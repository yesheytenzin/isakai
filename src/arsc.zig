// SPDX-License-Identifier: GPL-3.0-or-later

//! Minimal resources.arsc reader for install-time lookups (manifest labels,
//! smoke checks). Covers the real table shape as shipped by aapt2: string
//! pools (UTF-8 and UTF-16), table header, packages, type specs (skipped),
//! types with config blobs, simple entries, bag detection, default-config
//! preference, and `@ref` chains with a depth cap.
//!
//! The app-runtime (JVM) lookup path lives in
//! framework/android/content/res/Resources.java; this module only answers
//! "what string/color is id X" while inspecting/staging on the host.
//! Anything unexpected fails closed (null / error), never a guess.
//!
//! Config matching is deliberately narrow: the default-config chunk wins,
//! else the first chunk with the entry present. Install-time callers have
//! no device configuration to match against; locale/density-qualified
//! values resolve to their default, which is also what the label and
//! smoke-check paths need.

const std = @import("std");

pub const max_arsc: usize = 16 * 1024 * 1024;
pub const max_strings: usize = 200_000;
pub const max_packages: usize = 16;
pub const max_chunks: usize = 100_000;

pub const ArscError = error{
    Truncated,
    InvalidTable,
    InvalidStringPool,
    InvalidPackage,
    InvalidType,
    TooBig,
    OutOfMemory,
};

const chunk_table: u16 = 0x0002;
const chunk_string_pool: u16 = 0x0001;
const chunk_package: u16 = 0x0200;
const chunk_type_spec: u16 = 0x0202;
const chunk_type: u16 = 0x0201;

fn u16At(data: []const u8, at: usize) ArscError!u16 {
    if (at + 2 > data.len) return error.Truncated;
    return std.mem.readInt(u16, data[at..][0..2], .little);
}

fn u32At(data: []const u8, at: usize) ArscError!u32 {
    if (at + 4 > data.len) return error.Truncated;
    return std.mem.readInt(u32, data[at..][0..4], .little);
}

const Chunk = struct {
    kind: u16,
    header: usize,
    size: usize,
};

fn chunkAt(data: []const u8, at: usize) ArscError!Chunk {
    const kind = try u16At(data, at);
    const header: usize = try u16At(data, at + 2);
    const size: usize = try u32At(data, at + 4);
    if (header < 8 or size < header) return error.InvalidTable;
    const end = std.math.add(usize, at, size) catch return error.InvalidTable;
    if (end > data.len) return error.Truncated;
    return .{ .kind = kind, .header = header, .size = size };
}

fn utf8Len(data: []const u8, pos: *usize) ArscError!usize {
    if (pos.* >= data.len) return error.Truncated;
    const first = data[pos.*];
    pos.* += 1;
    if (first & 0x80 == 0) return first;
    if (pos.* >= data.len) return error.Truncated;
    const second = data[pos.*];
    pos.* += 1;
    return (@as(usize, first & 0x7f) << 8) | second;
}

fn utf16Len(data: []const u8, pos: *usize) ArscError!usize {
    const first = try u16At(data, pos.*);
    pos.* += 2;
    if (first & 0x8000 == 0) return first;
    const second = try u16At(data, pos.*);
    pos.* += 2;
    return (@as(usize, first & 0x7fff) << 16) | second;
}

/// Decode one string pool chunk. Returns owned strings (arena) plus the
/// chunk's total size. Style spans are ignored.
fn decodePool(
    arena: std.mem.Allocator,
    bytes: []const u8,
    off: usize,
) ArscError!struct { strings: []const []const u8, size: usize } {
    const c = try chunkAt(bytes, off);
    if (c.kind != chunk_string_pool or c.header < 28) return error.InvalidStringPool;
    const count: usize = try u32At(bytes, off + 8);
    const styles: usize = try u32At(bytes, off + 12);
    const flags = try u32At(bytes, off + 16);
    const start: usize = try u32At(bytes, off + 20);
    if (count > max_strings) return error.InvalidStringPool;
    if (start < c.header + (count + styles) * 4 or off + start > bytes.len) {
        return error.InvalidStringPool;
    }
    const utf8 = flags & 0x100 != 0;
    var out: std.ArrayListUnmanaged([]const u8) = .empty;
    for (0..count) |i| {
        const rel: usize = try u32At(bytes, off + c.header + i * 4);
        var pos = std.math.add(usize, off + start, rel) catch return error.InvalidStringPool;
        if (utf8) {
            const ulen = try utf8Len(bytes, &pos);
            _ = ulen;
            const blen = try utf8Len(bytes, &pos);
            const end = std.math.add(usize, pos, blen) catch return error.InvalidStringPool;
            if (end >= bytes.len) return error.Truncated;
            if (bytes[end] != 0) return error.InvalidStringPool;
            const slice = bytes[pos..end];
            if (!std.unicode.utf8ValidateSlice(slice)) return error.InvalidStringPool;
            out.append(arena, try arena.dupe(u8, slice)) catch return error.OutOfMemory;
        } else {
            const nchars = try utf16Len(bytes, &pos);
            const blen = std.math.mul(usize, nchars, 2) catch return error.InvalidStringPool;
            const end = std.math.add(usize, pos, blen) catch return error.InvalidStringPool;
            if (end + 2 > bytes.len) return error.Truncated;
            if (try u16At(bytes, end) != 0) return error.InvalidStringPool;
            var tmp: std.ArrayListUnmanaged(u8) = .empty;
            defer tmp.deinit(arena);
            var wi: usize = pos;
            while (wi < end) {
                const w1 = std.mem.readInt(u16, bytes[wi..][0..2], .little);
                wi += 2;
                var cp: u21 = w1;
                if (w1 >= 0xD800 and w1 < 0xDC00) {
                    if (wi + 2 > end) return error.InvalidStringPool;
                    const w2 = std.mem.readInt(u16, bytes[wi..][0..2], .little);
                    wi += 2;
                    if (w2 < 0xDC00 or w2 >= 0xE000) return error.InvalidStringPool;
                    cp = 0x10000 + (@as(u21, w1 - 0xD800) << 10) + (w2 - 0xDC00);
                } else if (w1 >= 0xDC00 and w1 < 0xE000) {
                    return error.InvalidStringPool;
                }
                var enc: [4]u8 = undefined;
                const n = std.unicode.utf8Encode(cp, &enc) catch return error.InvalidStringPool;
                tmp.appendSlice(arena, enc[0..n]) catch return error.OutOfMemory;
            }
            out.append(arena, try arena.dupe(u8, tmp.items)) catch return error.OutOfMemory;
        }
    }
    return .{ .strings = try arena.dupe([]const u8, out.items), .size = c.size };
}

/// True when the type chunk's config blob is the default (no qualifiers):
/// first u32 is the config size, every byte after it is zero.
fn isDefaultConfig(bytes: []const u8, cfg_off: usize, cfg_len: usize) bool {
    if (cfg_len < 4 or cfg_off + cfg_len > bytes.len) return false;
    for (bytes[cfg_off + 4 .. cfg_off + cfg_len]) |b| {
        if (b != 0) return false;
    }
    return true;
}

pub const TypeChunk = struct {
    /// Offset of the type chunk header within the table bytes.
    off: usize,
    /// Header size (config blob lives at off+20 .. off+header).
    header: usize,
    id: u8,
    entry_count: u32,
    entries_start: usize,
    default_config: bool,
};

pub const Package = struct {
    id: u32,
    type_names: []const []const u8,
    key_names: []const []const u8,
    types: []const TypeChunk,
};

/// Resolved scalar value. `str` is set only for TYPE_STRING (borrowed from
/// the decoded global pool); all other types carry raw `data`.
pub const Resolved = struct {
    data_type: u8,
    data: u32,
    str: ?[]const u8 = null,
};

pub const Table = struct {
    arena: std.heap.ArenaAllocator,
    bytes: []const u8,
    globals: []const []const u8,
    packages: []const Package,

    pub fn deinit(self: *Table) void {
        self.arena.deinit();
    }

    /// Look up one resource id, following `@ref` chains (depth cap 8).
    /// Returns null for missing entries, bags, and out-of-range data
    /// (fail-closed, never a guess).
    pub fn lookup(self: *const Table, id: u32) ?Resolved {
        return self.lookupDepth(id, 0);
    }

    fn lookupDepth(self: *const Table, id: u32, depth: u8) ?Resolved {
        if (depth > 8) return null;
        const pkg_id: u32 = (id >> 24) & 0xFF;
        const type_id: u8 = @truncate((id >> 16) & 0xFF);
        const entry_idx: usize = id & 0xFFFF;
        for (self.packages) |*pkg| {
            if (pkg.id != pkg_id) continue;
            var fallback: ?*const TypeChunk = null;
            for (pkg.types) |*tc| {
                if (tc.id != type_id) continue;
                if (entry_idx >= tc.entry_count) continue;
                const slot = std.math.add(usize, tc.off + tc.header, entry_idx * 4) catch return null;
                const rel = u32At(self.bytes, slot) catch return null;
                if (rel == std.math.maxInt(u32)) continue;
                if (tc.default_config) {
                    fallback = null;
                    return self.readEntry(pkg, tc, rel, id, depth);
                }
                if (fallback == null) fallback = tc;
            }
            if (fallback) |tc| {
                const slot = std.math.add(usize, tc.off + tc.header, entry_idx * 4) catch return null;
                const rel = u32At(self.bytes, slot) catch return null;
                if (rel == std.math.maxInt(u32)) return null;
                return self.readEntry(pkg, tc, rel, id, depth);
            }
            return null;
        }
        return null;
    }

    fn readEntry(
        self: *const Table,
        pkg: *const Package,
        tc: *const TypeChunk,
        rel: u32,
        id: u32,
        depth: u8,
    ) ?Resolved {
        _ = pkg;
        _ = id;
        const at = std.math.add(usize, tc.off + tc.entries_start, rel) catch return null;
        if (at + 16 > self.bytes.len) return null;
        const flags = std.mem.readInt(u16, self.bytes[at + 2 ..][0..2], .little);
        if (flags & 1 != 0) return null; // Complex (bag): no scalar value.
        // Simple value: size u16 @+8, res0 u8 @+10, dataType u8 @+11, data u32 @+12.
        const dtype: u8 = self.bytes[at + 11];
        const data = std.mem.readInt(u32, self.bytes[at + 12 ..][0..4], .little);
        if (dtype == 1) return self.lookupDepth(data, depth + 1); // TYPE_REFERENCE.
        if (dtype == 3) {
            if (data >= self.globals.len) return null;
            return .{ .data_type = dtype, .data = data, .str = self.globals[data] };
        }
        return .{ .data_type = dtype, .data = data };
    }

    /// String value of `id`, or null when absent / not a string.
    pub fn lookupString(self: *const Table, id: u32) ?[]const u8 {
        const r = self.lookup(id) orelse return null;
        return r.str;
    }

    /// Packed ARGB color of `id`, or null when absent / not a color int
    /// (types 0x1c..0x1f).
    pub fn lookupColor(self: *const Table, id: u32) ?u32 {
        const r = self.lookup(id) orelse return null;
        if (r.data_type < 0x1c or r.data_type > 0x1f) return null;
        return r.data;
    }

    /// Parse table bytes (borrowed; caller keeps them alive). Strings are
    /// decoded into an internal arena freed by `deinit`.
    pub fn parse(gpa: std.mem.Allocator, bytes: []const u8) ArscError!Table {
        if (bytes.len > max_arsc) return error.TooBig;
        const root = try chunkAt(bytes, 0);
        if (root.kind != chunk_table or root.header < 12) return error.InvalidTable;
        const pkg_count: usize = try u32At(bytes, 8);
        if (pkg_count > max_packages) return error.InvalidTable;

        var arena: std.heap.ArenaAllocator = .init(gpa);
        errdefer arena.deinit();
        const alloc = arena.allocator();

        var at: usize = root.header;
        const globals = try decodePool(alloc, bytes, at);
        at += globals.size;

        var pkgs: std.ArrayListUnmanaged(Package) = .empty;
        for (0..pkg_count) |_| {
            const pc = try chunkAt(bytes, at);
            if (pc.kind != chunk_package or pc.header < 288) return error.InvalidPackage;
            const pid = try u32At(bytes, at + 8);
            const type_rel: usize = try u32At(bytes, at + 268);
            const key_rel: usize = try u32At(bytes, at + 276);
            if (type_rel >= pc.size or key_rel >= pc.size) return error.InvalidPackage;
            const type_pool = try decodePool(alloc, bytes, at + type_rel);
            const key_pool = try decodePool(alloc, bytes, at + key_rel);

            var types: std.ArrayListUnmanaged(TypeChunk) = .empty;
            var co: usize = at + pc.header;
            const pend = at + pc.size;
            var seen: usize = 0;
            while (co < pend) {
                const tc = try chunkAt(bytes, co);
                if (tc.kind == chunk_type_spec or tc.kind == chunk_string_pool) {
                    // Config masks only; type/key name pools live at the
                    // type/key offsets inside this span and carry no entries.
                } else if (tc.kind == chunk_type) {
                    if (tc.header < 20) return error.InvalidType;
                    const tid = bytes[co + 8];
                    const ecount = try u32At(bytes, co + 12);
                    const estart: usize = try u32At(bytes, co + 16);
                    if (ecount > max_strings) return error.InvalidType;
                    const cfg_len = tc.header - 20;
                    const is_def = isDefaultConfig(bytes, co + 20, cfg_len);
                    const idx_end = std.math.add(usize, co + tc.header, ecount * 4) catch
                        return error.InvalidType;
                    if (idx_end > co + tc.size) return error.InvalidType;
                    if (estart > tc.size) return error.InvalidType;
                    types.append(alloc, .{
                        .off = co,
                        .header = tc.header,
                        .id = tid,
                        .entry_count = ecount,
                        .entries_start = estart,
                        .default_config = is_def,
                    }) catch return error.OutOfMemory;
                } else {
                    return error.InvalidPackage;
                }
                co += tc.size;
                seen += 1;
                if (seen > max_chunks) return error.InvalidPackage;
            }
            pkgs.append(alloc, .{
                .id = pid,
                .type_names = type_pool.strings,
                .key_names = key_pool.strings,
                .types = try alloc.dupe(TypeChunk, types.items),
            }) catch return error.OutOfMemory;
            at += pc.size;
        }
        return .{
            .arena = arena,
            .bytes = bytes,
            .globals = globals.strings,
            .packages = try alloc.dupe(Package, pkgs.items),
        };
    }
};

/// Minimal valid table for unit tests (also documents the fixture layout):
/// package 0x7f, string type (17) with a literal plus a `@ref` alias, color
/// type (6) with one packed color. The string type ships two config chunks
/// (default + a qualified twin) so the default-wins rule is covered.
pub fn buildSample(gpa: std.mem.Allocator) ![]u8 {
    var out: std.ArrayListUnmanaged(u8) = .empty;
    errdefer out.deinit(gpa);

    const w16 = struct {
        fn f(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u16) !void {
            var b: [2]u8 = undefined;
            std.mem.writeInt(u16, &b, v, .little);
            try l.appendSlice(g, &b);
        }
    }.f;
    const w32 = struct {
        fn f(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, v: u32) !void {
            var b: [4]u8 = undefined;
            std.mem.writeInt(u32, &b, v, .little);
            try l.appendSlice(g, &b);
        }
    }.f;
    const wPoolStr = struct {
        fn f(l: *std.ArrayListUnmanaged(u8), g: std.mem.Allocator, s: []const u8) !void {
            // Dual varint length prefix (utf16 len, byte len); ASCII: equal.
            for ([_]usize{ s.len, s.len }) |v| {
                if (v < 0x80) {
                    try l.append(g, @intCast(v));
                } else {
                    try l.append(g, @as(u8, @intCast(0x80 | (v >> 8))));
                    try l.append(g, @as(u8, @intCast(v & 0xFF)));
                }
            }
            try l.appendSlice(g, s);
            try l.append(g, 0);
        }
    }.f;
    const wPool = struct {
        fn f(
            l: *std.ArrayListUnmanaged(u8),
            g: std.mem.Allocator,
            w16f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u16) anyerror!void,
            w32f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u32) anyerror!void,
            wps: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, []const u8) anyerror!void,
            strs: []const []const u8,
        ) !void {
            try w16f(l, g, 1);
            try w16f(l, g, 28);
            const len_pos = l.items.len;
            try w32f(l, g, 0); // patched
            try w32f(l, g, @intCast(strs.len));
            try w32f(l, g, 0);
            try w32f(l, g, 0x100); // UTF-8
            const start_pos = l.items.len;
            try w32f(l, g, 0); // patched
            try w32f(l, g, 0);
            var data: std.ArrayListUnmanaged(u8) = .empty;
            defer data.deinit(g);
            const idx_base = l.items.len;
            for (strs) |_| try w32f(l, g, 0); // offset placeholders
            for (strs, 0..) |s, i| {
                std.mem.writeInt(u32, l.items[idx_base + i * 4 ..][0..4], @intCast(data.items.len), .little);
                try wps(&data, g, s);
            }
            while (data.items.len % 4 != 0) try data.append(g, 0);
            try l.appendSlice(g, data.items);
            const total: u32 = @intCast(l.items.len - len_pos + 4);
            std.mem.writeInt(u32, l.items[len_pos..][0..4], total, .little);
            const start_val: u32 = @intCast(28 + strs.len * 4);
            std.mem.writeInt(u32, l.items[start_pos..][0..4], start_val, .little);
        }
    }.f;

    // Table header.
    try w16(&out, gpa, chunk_table);
    try w16(&out, gpa, 12);
    const table_len_pos = out.items.len;
    try w32(&out, gpa, 0); // patched
    try w32(&out, gpa, 1); // one package

    try wPool(&out, gpa, &w16, &w32, &wPoolStr, &.{ "Sample", "Sample-hdpi" });

    // Package.
    const pkg_pos = out.items.len;
    try w16(&out, gpa, chunk_package);
    try w16(&out, gpa, 288);
    const pkg_len_pos = out.items.len;
    try w32(&out, gpa, 0); // patched
    try w32(&out, gpa, 0x7f);
    for (0..128) |i| {
        const w: u16 = if (i < "org.isakai.sample".len) "org.isakai.sample"[i] else 0;
        try w16(&out, gpa, w);
    }
    const type_rel_pos = out.items.len;
    try w32(&out, gpa, 0); // patched
    try w32(&out, gpa, 0);
    const key_rel_pos = out.items.len;
    try w32(&out, gpa, 0); // patched
    try w32(&out, gpa, 0);
    try w32(&out, gpa, 0); // typeIdOffset

    const type_pool_off = out.items.len - pkg_pos;
    try wPool(&out, gpa, &w16, &w32, &wPoolStr, &.{
        "anim", "animator", "array", "attr", "bool", "color", "dimen", "drawable",
        "id", "integer", "interpolator", "layout", "font", "menu", "mipmap",
        "plurals", "string", "style", "raw", "xml",
    });
    const key_pool_off = out.items.len - pkg_pos;
    try wPool(&out, gpa, &w16, &w32, &wPoolStr, &.{ "sample_a", "sample_b", "sample_color" });

    // TypeSpec + Type helpers.
    const wSpec = struct {
        fn f(
            l: *std.ArrayListUnmanaged(u8),
            g: std.mem.Allocator,
            w16f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u16) anyerror!void,
            w32f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u32) anyerror!void,
            tid: u8,
            count: u32,
        ) !void {
            try w16f(l, g, chunk_type_spec);
            try w16f(l, g, 16);
            try w32f(l, g, 16 + count * 4);
            try l.append(g, tid);
            try l.append(g, 0);
            try w16f(l, g, 0);
            try w32f(l, g, count);
            for (0..count) |_| try w32f(l, g, 0xFFFFFFFF);
        }
    }.f;
    // Emit one TYPE chunk (header 84 + config blob + index + entries).
    const wType = struct {
        fn f(
            l: *std.ArrayListUnmanaged(u8),
            g: std.mem.Allocator,
            w16f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u16) anyerror!void,
            w32f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u32) anyerror!void,
            tid: u8,
            qualified: bool,
            entry_offs: []const u32,
            entries: []const u8,
        ) !void {
            const count: u32 = @intCast(entry_offs.len);
            try w16f(l, g, chunk_type);
            try w16f(l, g, 84);
            const len_pos = l.items.len;
            try w32f(l, g, 0); // patched
            try l.append(g, tid);
            try l.append(g, 0);
            try w16f(l, g, 0);
            try w32f(l, g, count);
            try w32f(l, g, 84 + count * 4);
            try w32f(l, g, 64);
            var cfg: [60]u8 = std.mem.zeroes([60]u8);
            if (qualified) {
                cfg[10] = 240;
            }
            try l.appendSlice(g, &cfg);
            for (entry_offs) |o| try w32f(l, g, o);
            try l.appendSlice(g, entries);
            const total: u32 = @intCast(l.items.len - (len_pos - 4));
            std.mem.writeInt(u32, l.items[len_pos..][0..4], total, .little);
        }
    }.f;

    // Simple string entry: ResTable_entry + Res_value.
    const wStrEntry = struct {
        fn f(
            l: *std.ArrayListUnmanaged(u8),
            g: std.mem.Allocator,
            w16f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u16) anyerror!void,
            w32f: *const fn (*std.ArrayListUnmanaged(u8), std.mem.Allocator, u32) anyerror!void,
            key: u32,
            dtype: u8,
            data: u32,
        ) !void {
            try w16f(l, g, 16); // entry size
            try w16f(l, g, 0); // flags: simple
            try w32f(l, g, key);
            try w16f(l, g, 8); // value size
            try l.append(g, 0);
            try l.append(g, dtype);
            try w32f(l, g, data);
        }
    }.f;

    // String type, default config: [0] "Sample", [1] ref -> 0x7f110000.
    var e_def: std.ArrayListUnmanaged(u8) = .empty;
    defer e_def.deinit(gpa);
    try wStrEntry(&e_def, gpa, &w16, &w32, 0, 3, 0);
    try wStrEntry(&e_def, gpa, &w16, &w32, 1, 1, 0x7f110000);
    // Qualified twin: [0] "Sample-hdpi", [1] missing.
    var e_q: std.ArrayListUnmanaged(u8) = .empty;
    defer e_q.deinit(gpa);
    try wStrEntry(&e_q, gpa, &w16, &w32, 0, 3, 1);
    try wSpec(&out, gpa, &w16, &w32, 17, 2);
    try wType(&out, gpa, &w16, &w32, 17, false, &.{ 0, 16 }, e_def.items);
    try wType(&out, gpa, &w16, &w32, 17, true, &.{ 0, 0xFFFFFFFF }, e_q.items);
    // Color type: [0] packed color.
    var e_c: std.ArrayListUnmanaged(u8) = .empty;
    defer e_c.deinit(gpa);
    try wStrEntry(&e_c, gpa, &w16, &w32, 2, 0x1c, 0xFF112233);
    try wSpec(&out, gpa, &w16, &w32, 6, 1);
    try wType(&out, gpa, &w16, &w32, 6, false, &.{0}, e_c.items);

    std.mem.writeInt(u32, out.items[type_rel_pos..][0..4], @intCast(type_pool_off), .little);
    std.mem.writeInt(u32, out.items[key_rel_pos..][0..4], @intCast(key_pool_off), .little);
    std.mem.writeInt(u32, out.items[pkg_len_pos..][0..4], @intCast(out.items.len - pkg_pos), .little);
    std.mem.writeInt(u32, out.items[table_len_pos..][0..4], @intCast(out.items.len), .little);
    return out.toOwnedSlice(gpa);
}

test "sample table: strings, ref alias, colors, default-config wins" {
    const gpa = std.testing.allocator;
    const bytes = try buildSample(gpa);
    defer gpa.free(bytes);
    var t = try Table.parse(gpa, bytes);
    defer t.deinit();
    try std.testing.expectEqual(@as(usize, 1), t.packages.len);
    try std.testing.expectEqual(@as(u32, 0x7f), t.packages[0].id);
    try std.testing.expectEqualStrings("Sample", t.lookupString(0x7f110000).?);
    try std.testing.expectEqualStrings("Sample", t.lookupString(0x7f110001).?);
    try std.testing.expect(t.lookupString(0x7f110002) == null);
    try std.testing.expect(t.lookupString(0x7e110000) == null);
    try std.testing.expectEqual(@as(u32, 0xFF112233), t.lookupColor(0x7f060000).?);
    try std.testing.expect(t.lookupColor(0x7f110000) == null);
    const v = t.lookup(0x7f110000).?;
    try std.testing.expectEqual(@as(u8, 3), v.data_type);
}

test "table rejects truncated and wrong-magic input" {
    const gpa = std.testing.allocator;
    try std.testing.expectError(error.Truncated, Table.parse(gpa, &[_]u8{ 2, 0 }));
    var bad = [_]u8{ 0xFF, 0xFF, 8, 0, 8, 0, 0, 0, 0, 0, 0, 0 };
    try std.testing.expectError(error.InvalidTable, Table.parse(gpa, &bad));
    const bytes = try buildSample(gpa);
    defer gpa.free(bytes);
    try std.testing.expectError(error.Truncated, Table.parse(gpa, bytes[0 .. bytes.len - 10]));
}
