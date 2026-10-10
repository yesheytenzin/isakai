// SPDX-License-Identifier: GPL-3.0-or-later

//! Bounded APK inspection and staging. Names come from binary XML, never from
//! filenames. Rules ported from the Rust broker's apk.rs.

const std = @import("std");
const registry = @import("registry");
const apksig = @import("apksig");
const arsc = @import("arsc");
const uid_helper = @import("uid_helper");

pub const max_entry: u64 = 256 * 1024 * 1024;
pub const max_total: u64 = 1024 * 1024 * 1024;
pub const max_entries: usize = 100_000;
pub const max_manifest: usize = 4 * 1024 * 1024;

const android_ns = "http://schemas.android.com/apk/res/android";

pub const Manifest = struct {
    package: []const u8,
    application: ?[]const u8 = null,
    launcher: ?[]const u8 = null,
    /// Resolved labels when android:label is a literal string. When the
    /// label is a `@…` resource reference, the raw id is kept in the
    /// matching `*_label_id` and resolved later against resources.arsc
    /// (install/launch), with the package as the final fallback.
    app_label: ?[]const u8 = null,
    launcher_label: ?[]const u8 = null,
    app_label_id: ?u32 = null,
    launcher_label_id: ?u32 = null,
    app_theme: u32 = 0,
    activity_theme: u32 = 0,
    services: std.ArrayListUnmanaged([]const u8) = .empty,
    min_sdk: u32 = 1,
    target_sdk: u32 = 1,
    permissions: std.ArrayListUnmanaged([]const u8) = .empty,
};

/// Literal label filter: returns the value unless it looks like a
/// `@…` resource reference. References are not lost: the manifest parser
/// captures their ids (`*_label_id`) for later arsc resolution.
pub fn literalLabel(v: []const u8) ?[]const u8 {
    if (v.len == 0 or v[0] == '@') return null;
    return v;
}

/// Resolved window title: launcher label, else app label, else package.
pub fn manifestTitle(m: *const Manifest) []const u8 {
    if (m.launcher_label) |l| return l;
    if (m.app_label) |l| return l;
    return m.package;
}

test "literalLabel ignores @string refs; manifestTitle prefers launcher" {
    try std.testing.expect(literalLabel("@string/app_name") == null);
    try std.testing.expect(literalLabel("@123") == null);
    try std.testing.expectEqualStrings("Fixture", literalLabel("Fixture").?);
    var m = Manifest{ .package = "com.foo", .app_label = "App", .launcher_label = "Launch" };
    try std.testing.expectEqualStrings("Launch", manifestTitle(&m));
    m.launcher_label = null;
    try std.testing.expectEqualStrings("App", manifestTitle(&m));
    m.app_label = null;
    try std.testing.expectEqualStrings("com.foo", manifestTitle(&m));
}

/// Strict package-name rules (mirrored in uid_helper.validPackage, kept
/// in sync by the "must match" unit tests in both files).

pub fn validPackage(name: []const u8) bool {
    if (name.len == 0 or name.len > 200) return false;
    if (std.mem.indexOfScalar(u8, name, '.') == null) return false;
    var parts = std.mem.splitScalar(u8, name, '.');
    while (parts.next()) |part| {
        if (part.len == 0) return false;
        if (!std.ascii.isAlphabetic(part[0])) return false;
        for (part[1..]) |c| {
            if (!std.ascii.isAlphanumeric(c) and c != '_') return false;
        }
    }
    return true;
}

fn className(gpa: std.mem.Allocator, package: []const u8, name: []const u8) ![]u8 {
    if (name.len > 0 and name[0] == '.') {
        return std.fmt.allocPrint(gpa, "{s}{s}", .{ package, name });
    } else if (std.mem.indexOfScalar(u8, name, '.') != null) {
        return gpa.dupe(u8, name);
    } else {
        return std.fmt.allocPrint(gpa, "{s}.{s}", .{ package, name });
    }
}

pub const ApkError = error{
    ApkTooLarge,
    TooManyEntries,
    UnsafeEntry,
    DuplicateEntry,
    SizeOverflow,
    EntryTooLarge,
    NoClassesDex,
    NoX86_64,
    NativeUnsupported,
    NoManifest,
    ManifestTooLarge,
    Truncated,
    InvalidStringPool,
    InvalidStringIndex,
    InvalidBinaryXml,
    DuplicateStringPool,
    InvalidElement,
    CrcMismatch,
    InvalidAttributes,
    DuplicateAttribute,
    ResourceRefUnsupported,
    InvalidManifestRoot,
    NoPackage,
    InvalidPackageName,
    SplitUnsupported,
    BadSdkVersion,
    PermissionNoName,
    ServiceNoName,
    ActivityNoName,
    NestingTooDeep,
    UnbalancedElements,
    IncompleteManifest,
    SdkTooNew,
    EntryNotFound,
    SizeMismatch,
    StagingExists,
    OutOfMemory,
};

fn validateEntry(name: []const u8) ApkError!void {
    if (name.len == 0 or name.len > 1024) return error.UnsafeEntry;
    if (name[0] == '/') return error.UnsafeEntry;
    if (std.mem.indexOf(u8, name, "//") != null) return error.UnsafeEntry;
    var depth: usize = 0;
    var parts = std.mem.splitScalar(u8, name, '/');
    while (parts.next()) |part| {
        depth += 1;
        if (depth > 64) return error.UnsafeEntry;
        if (part.len == 0) continue;
        if (std.mem.eql(u8, part, ".") or std.mem.eql(u8, part, "..")) return error.UnsafeEntry;
        if (std.mem.indexOfAny(u8, part, "\\\x00") != null) return error.UnsafeEntry;
    }
}

pub const ZipEntry = struct {
    name: []const u8,
    size: u64,
};

/// List and validate all entry names + sizes. Caller owns names.
pub fn listEntries(
    io: std.Io,
    gpa: std.mem.Allocator,
    apk_path: []const u8,
    entries: *std.ArrayListUnmanaged(ZipEntry),
) (ApkError || std.Io.File.OpenError || std.Io.Dir.StatFileError || error{OutOfMemory})!void {
    const stat = try std.Io.Dir.cwd().statFile(io, apk_path, .{});
    if (stat.size > max_entry) return error.ApkTooLarge;
    var file = try std.Io.Dir.cwd().openFile(io, apk_path, .{ .mode = .read_only });
    defer file.close(io);

    var read_buf: [8192]u8 = undefined;
    var stream = file.reader(io, &read_buf);
    var iter = std.zip.Iterator.init(&stream) catch return error.Truncated;

    var seen: std.StringHashMapUnmanaged(void) = .empty;
    defer seen.deinit(gpa);
    var total: u64 = 0;
    var count: usize = 0;
    var name_buf: [2048]u8 = undefined;

    while (true) {
        const entry = (iter.next() catch return error.Truncated) orelse break;
        count += 1;
        if (count > max_entries) return error.TooManyEntries;
        const raw_name = entry.getFilename(&stream, &name_buf, .{}) catch return error.UnsafeEntry;
        try validateEntry(raw_name);
        const gop = try seen.getOrPut(gpa, raw_name);
        if (gop.found_existing) return error.DuplicateEntry;
        gop.key_ptr.* = try gpa.dupe(u8, raw_name);
        total = std.math.add(u64, total, entry.uncompressed_size) catch return error.SizeOverflow;
        if (entry.uncompressed_size > max_entry or total > max_total) return error.EntryTooLarge;
        try entries.append(gpa, .{ .name = gop.key_ptr.*, .size = entry.uncompressed_size });
    }
}

/// Writer adapter over an ArrayList with a hard cap.
const ArrayListWriter = struct {
    gpa: std.mem.Allocator,
    list: *std.ArrayListUnmanaged(u8),
    cap: usize,
    buf: [8192]u8 = undefined,
    interface: std.Io.Writer = .{
        .vtable = &.{ .drain = drain },
        .buffer = &.{},
    },

    fn drain(w: *std.Io.Writer, data: []const []const u8, splat: usize) std.Io.Writer.Error!usize {
        const self: *@This() = @fieldParentPtr("interface", w);
        // buffer[0..end] first.
        if (w.end > 0) {
            if (self.list.items.len + w.end > self.cap) return error.WriteFailed;
            self.list.appendSlice(self.gpa, w.buffer[0..w.end]) catch return error.WriteFailed;
            w.end = 0;
        }
        var total: usize = 0;
        for (data[0 .. data.len - 1]) |chunk| {
            if (self.list.items.len + chunk.len > self.cap) return error.WriteFailed;
            self.list.appendSlice(self.gpa, chunk) catch return error.WriteFailed;
            total += chunk.len;
        }
        const pattern = data[data.len - 1];
        if (splat > 0) {
            if (pattern.len == 0) return total;
            const need = pattern.len * splat;
            if (self.list.items.len + need > self.cap) return error.WriteFailed;
            for (0..splat) |_| {
                self.list.appendSlice(self.gpa, pattern) catch return error.WriteFailed;
            }
            total += need;
        } else {
            if (self.list.items.len + pattern.len > self.cap) return error.WriteFailed;
            self.list.appendSlice(self.gpa, pattern) catch return error.WriteFailed;
            total += pattern.len;
        }
        return total;
    }

    fn finish(self: *@This()) ApkError!void {
        // Flush any buffered tail.
        if (self.interface.end > 0) {
            if (self.list.items.len + self.interface.end > self.cap) return error.EntryTooLarge;
            self.list.appendSlice(self.gpa, self.interface.buffer[0..self.interface.end]) catch return error.OutOfMemory;
            self.interface.end = 0;
        }
    }
};

/// Read one entry's bytes by name. Caller owns the result.
pub fn readEntry(
    io: std.Io,
    gpa: std.mem.Allocator,
    apk_path: []const u8,
    want: []const u8,
    limit: usize,
) (ApkError || std.Io.File.OpenError || error{OutOfMemory})![]u8 {
    var file = try std.Io.Dir.cwd().openFile(io, apk_path, .{ .mode = .read_only });
    defer file.close(io);
    var read_buf: [8192]u8 = undefined;
    var stream = file.reader(io, &read_buf);
    var iter = std.zip.Iterator.init(&stream) catch return error.Truncated;
    var name_buf: [2048]u8 = undefined;
    while (true) {
        const entry = (iter.next() catch return error.Truncated) orelse return error.EntryNotFound;
        const raw_name = entry.getFilename(&stream, &name_buf, .{}) catch return error.UnsafeEntry;
        if (!std.mem.eql(u8, raw_name, want)) continue;
        if (entry.uncompressed_size > limit) return error.EntryTooLarge;
        var out: std.ArrayListUnmanaged(u8) = .empty;
        errdefer out.deinit(gpa);
        var aw: ArrayListWriter = .{ .gpa = gpa, .list = &out, .cap = limit };
        aw.interface.buffer = &aw.buf;
        entry.extractTo(&stream, &aw.interface) catch return error.Truncated;
        try aw.finish();
        if (out.items.len != entry.uncompressed_size) return error.SizeMismatch;
        // std.zip.extractTo never verifies the CRC32 (verify_checksums is
        // a TODO); do it here so corrupt APK data fails install/upgrade
        // instead of staging silently-corrupted bytes.
        const crc = std.hash.Crc32.hash(out.items);
        if (crc != entry.crc32) return error.CrcMismatch;
        return out.toOwnedSlice(gpa) catch return error.OutOfMemory;
    }
}

// --- Binary XML ---

fn u16At(data: []const u8, at: usize) ApkError!u16 {
    if (at + 2 > data.len) return error.Truncated;
    return std.mem.readInt(u16, data[at..][0..2], .little);
}

fn u32At(data: []const u8, at: usize) ApkError!u32 {
    if (at + 4 > data.len) return error.Truncated;
    return std.mem.readInt(u32, data[at..][0..4], .little);
}

fn strLen(data: []const u8, pos: *usize, utf8: bool) ApkError!usize {
    if (utf8) {
        if (pos.* >= data.len) return error.Truncated;
        const first = data[pos.*];
        pos.* += 1;
        if (first & 0x80 == 0) return first;
        if (pos.* >= data.len) return error.Truncated;
        const second = data[pos.*];
        pos.* += 1;
        return (@as(usize, first & 0x7f) << 8) | second;
    } else {
        const first = try u16At(data, pos.*);
        pos.* += 2;
        if (first & 0x8000 == 0) return first;
        const second = try u16At(data, pos.*);
        pos.* += 2;
        return (@as(usize, first & 0x7fff) << 16) | second;
    }
}

fn readStrings(
    gpa: std.mem.Allocator,
    chunk: []const u8,
    header: usize,
) ApkError!std.ArrayListUnmanaged([]const u8) {
    if (header < 28) return error.InvalidStringPool;
    const count: usize = try u32At(chunk, 8);
    const styles: usize = try u32At(chunk, 12);
    const flags = try u32At(chunk, 16);
    const utf8 = flags & 0x100 != 0;
    const start: usize = try u32At(chunk, 20);
    if (count > 100_000 or start < header + (count + styles) * 4 or start > chunk.len) {
        return error.InvalidStringPool;
    }
    var pool: std.ArrayListUnmanaged([]const u8) = .empty;
    errdefer {
        for (pool.items) |s| gpa.free(s);
        pool.deinit(gpa);
    }
    for (0..count) |i| {
        const offset: usize = try u32At(chunk, header + i * 4);
        var pos = std.math.add(usize, start, offset) catch return error.InvalidStringPool;
        var size = try strLen(chunk, &pos, utf8);
        if (utf8) size = try strLen(chunk, &pos, true);
        if (utf8) {
            const end = std.math.add(usize, pos, size) catch return error.InvalidStringPool;
            if (end >= chunk.len) return error.Truncated;
            if (chunk[end] != 0) return error.InvalidStringPool;
            const bytes = chunk[pos..end];
            if (!std.unicode.utf8ValidateSlice(bytes)) return error.InvalidStringPool;
            try pool.append(gpa, gpa.dupe(u8, bytes) catch return error.OutOfMemory);
        } else {
            const bytes_len = std.math.mul(usize, size, 2) catch return error.InvalidStringPool;
            const end = std.math.add(usize, pos, bytes_len) catch return error.InvalidStringPool;
            if (end + 2 > chunk.len) return error.Truncated;
            if (try u16At(chunk, end) != 0) return error.InvalidStringPool;
            const words = chunk[pos..end];
            var out: std.ArrayListUnmanaged(u8) = .empty;
            errdefer out.deinit(gpa);
            var wi: usize = 0;
            while (wi < words.len) {
                const w1 = std.mem.readInt(u16, words[wi..][0..2], .little);
                wi += 2;
                var cp: u21 = w1;
                if (w1 >= 0xD800 and w1 < 0xDC00) {
                    if (wi + 2 > words.len) {
                        out.deinit(gpa);
                        return error.InvalidStringPool;
                    }
                    const w2 = std.mem.readInt(u16, words[wi..][0..2], .little);
                    wi += 2;
                    if (w2 < 0xDC00 or w2 >= 0xE000) {
                        out.deinit(gpa);
                        return error.InvalidStringPool;
                    }
                    cp = 0x10000 + (@as(u21, w1 - 0xD800) << 10) + (w2 - 0xDC00);
                } else if (w1 >= 0xDC00 and w1 < 0xE000) {
                    out.deinit(gpa);
                    return error.InvalidStringPool;
                }
                var enc: [4]u8 = undefined;
                const n = std.unicode.utf8Encode(cp, &enc) catch {
                    out.deinit(gpa);
                    return error.InvalidStringPool;
                };
                out.appendSlice(gpa, enc[0..n]) catch {
                    out.deinit(gpa);
                    return error.OutOfMemory;
                };
            }
            pool.append(gpa, out.toOwnedSlice(gpa) catch return error.OutOfMemory) catch return error.OutOfMemory;
        }
    }
    return pool;
}

fn poolStr(pool: []const []const u8, index: u32) ApkError![]const u8 {
    if (index >= pool.len) return error.InvalidStringIndex;
    return pool[index];
}

pub fn parseManifest(gpa: std.mem.Allocator, data: []const u8) ApkError!Manifest {
    if (try u16At(data, 0) != 3 or try u16At(data, 2) != 8 or try u32At(data, 4) != data.len) {
        return error.InvalidBinaryXml;
    }
    var pool: std.ArrayListUnmanaged([]const u8) = .empty;
    var pool_owned = false;
    defer if (pool_owned) {
        for (pool.items) |s| gpa.free(s);
        pool.deinit(gpa);
    };
    var stack: std.ArrayListUnmanaged([]const u8) = .empty;
    defer stack.deinit(gpa);
    var manifest = Manifest{ .package = "" };
    errdefer freeManifest(gpa, &manifest);
    var activity: ?[]u8 = null;
    defer if (activity) |a| gpa.free(a);
    var activity_label: ?[]u8 = null;
    var activity_label_id: ?u32 = null;
    var activity_target: ?[]u8 = null;
    defer if (activity_target) |a| gpa.free(a);
    var activity_themes: std.StringHashMapUnmanaged(u32) = .empty;
    defer {
        var it = activity_themes.iterator();
        while (it.next()) |e| gpa.free(e.key_ptr.*);
        activity_themes.deinit(gpa);
    }
    var in_filter = false;
    var seen_main = false;
    var seen_launcher = false;
    var manifest_seen = false;

    var at: usize = 8;
    while (at < data.len) {
        const kind = try u16At(data, at);
        const header: usize = try u16At(data, at + 2);
        const size: usize = try u32At(data, at + 4);
        if (header < 8 or size < header) return error.InvalidBinaryXml;
        const end = std.math.add(usize, at, size) catch return error.InvalidBinaryXml;
        if (end > data.len) return error.Truncated;
        const chunk = data[at..end];
        switch (kind) {
            1 => {
                if (pool_owned) return error.DuplicateStringPool;
                pool = try readStrings(gpa, chunk, header);
                pool_owned = true;
            },
            0x102 => {
                if (header != 16) return error.InvalidElement;
                if (!pool_owned) return error.InvalidBinaryXml;
                const name = try poolStr(pool.items, try u32At(chunk, 20));
                const start: usize = 16 + try u16At(chunk, 24);
                const stride: usize = try u16At(chunk, 26);
                const count: usize = try u16At(chunk, 28);
                if (stride < 20 or start < 36 or start + stride * count > size) {
                    return error.InvalidAttributes;
                }
                var attrs: std.StringHashMapUnmanaged([]const u8) = .empty;
                defer attrs.deinit(gpa);
                var key_arena: std.heap.ArenaAllocator = .init(gpa);
                defer key_arena.deinit();
                const ka = key_arena.allocator();
                var label_ids: std.StringHashMapUnmanaged(u32) = .empty;
                defer label_ids.deinit(gpa);
                for (0..count) |i| {
                    const a = start + i * stride;
                    const ns_idx = try u32At(chunk, a);
                    const ns: []const u8 = if (ns_idx == std.math.maxInt(u32))
                        ""
                    else
                        try poolStr(pool.items, ns_idx);
                    const key = try poolStr(pool.items, try u32At(chunk, a + 4));
                    const raw = try u32At(chunk, a + 8);
                    if (a + 15 >= chunk.len) return error.Truncated;
                    const typ = chunk[a + 15];
                    const value = try u32At(chunk, a + 16);
                    // Resource references (TYPE_REFERENCE with no raw
                    // string) carry the id for later arsc resolution.
                    // OpenCalc labels arrive exactly this way; so do themes.
                    if (typ == 1 and raw == std.math.maxInt(u32) and std.mem.eql(u8, ns, android_ns) and
                        (std.mem.eql(u8, key, "label") or std.mem.eql(u8, key, "theme")))
                    {
                        const lck = std.fmt.allocPrint(ka, "{s}\x00{s}\x00id", .{ ns, key }) catch return error.OutOfMemory;
                        const gop = label_ids.getOrPut(gpa, lck) catch return error.OutOfMemory;
                        if (gop.found_existing) return error.DuplicateAttribute;
                        gop.value_ptr.* = value;
                    }
                    const text: []const u8 = if (raw != std.math.maxInt(u32))
                        try poolStr(pool.items, raw)
                    else if (typ == 3)
                        try poolStr(pool.items, value)
                    else if (typ >= 0x10 and typ <= 0x12)
                        std.fmt.allocPrint(ka, "{d}", .{value}) catch return error.OutOfMemory
                    else {
                        if (std.mem.eql(u8, key, "package") or
                            std.mem.eql(u8, key, "name") or
                            std.mem.eql(u8, key, "minSdkVersion") or
                            std.mem.eql(u8, key, "targetSdkVersion") or
                            std.mem.eql(u8, key, "split") or
                            std.mem.eql(u8, key, "sharedUserId"))
                        {
                            return error.ResourceRefUnsupported;
                        }
                        continue;
                    };
                    const ck = std.fmt.allocPrint(ka, "{s}\x00{s}", .{ ns, key }) catch return error.OutOfMemory;
                    const gop = attrs.getOrPut(gpa, ck) catch return error.OutOfMemory;
                    if (gop.found_existing) return error.DuplicateAttribute;
                    gop.value_ptr.* = text;
                }
                const lookup = struct {
                    fn plain(m: *const @TypeOf(attrs), key: []const u8, kaa: std.mem.Allocator) ?[]const u8 {
                        const ck = std.fmt.allocPrint(kaa, "\x00{s}", .{key}) catch return null;
                        return m.get(ck);
                    }
                    fn android(m: *const @TypeOf(attrs), key: []const u8, kaa: std.mem.Allocator) ?[]const u8 {
                        const ck = std.fmt.allocPrint(kaa, "{s}\x00{s}", .{ android_ns, key }) catch return null;
                        return m.get(ck);
                    }
                    fn androidId(m: *const @TypeOf(label_ids), key: []const u8, kaa: std.mem.Allocator) ?u32 {
                        const ck = std.fmt.allocPrint(kaa, "{s}\x00{s}\x00id", .{ android_ns, key }) catch return null;
                        return m.get(ck);
                    }
                };
                const parent: ?[]const u8 = if (stack.items.len > 0) stack.items[stack.items.len - 1] else null;

                if (std.mem.eql(u8, name, "manifest")) {
                    if (manifest_seen or stack.items.len != 0) return error.InvalidManifestRoot;
                    manifest_seen = true;
                    const pkg = lookup.plain(&attrs, "package", ka) orelse return error.NoPackage;
                    if (!validPackage(pkg)) return error.InvalidPackageName;
                    manifest.package = gpa.dupe(u8, pkg) catch return error.OutOfMemory;
                    if (lookup.plain(&attrs, "split", ka) != null or
                        lookup.android(&attrs, "isFeatureSplit", ka) != null or
                        lookup.android(&attrs, "sharedUserId", ka) != null)
                    {
                        return error.SplitUnsupported;
                    }
                } else if (std.mem.eql(u8, name, "uses-sdk") and parent != null and std.mem.eql(u8, parent.?, "manifest")) {
                    if (lookup.android(&attrs, "minSdkVersion", ka)) |v| {
                        manifest.min_sdk = std.fmt.parseInt(u32, v, 10) catch return error.BadSdkVersion;
                    }
                    if (lookup.android(&attrs, "targetSdkVersion", ka)) |v| {
                        manifest.target_sdk = std.fmt.parseInt(u32, v, 10) catch return error.BadSdkVersion;
                    } else {
                        manifest.target_sdk = manifest.min_sdk;
                    }
                } else if (std.mem.eql(u8, name, "uses-permission") and parent != null and std.mem.eql(u8, parent.?, "manifest")) {
                    const perm = lookup.android(&attrs, "name", ka) orelse return error.PermissionNoName;
                    manifest.permissions.append(gpa, gpa.dupe(u8, perm) catch return error.OutOfMemory) catch return error.OutOfMemory;
                } else if (std.mem.eql(u8, name, "application") and parent != null and std.mem.eql(u8, parent.?, "manifest")) {
                    if (lookup.android(&attrs, "appComponentFactory", ka) != null) {
                        // API-28 instantiation hook (AndroidX injects
                        // CoreComponentFactory by default). Default behavior
                        // is direct instantiation, which is what IsakaiMain
                        // does — safe to ignore with a warning.
                        std.log.warn("ignoring android:appComponentFactory (direct instantiation)", .{});
                    }
                    if (lookup.android(&attrs, "name", ka)) |v| {
                        manifest.application = className(gpa, manifest.package, v) catch return error.OutOfMemory;
                    }
                    if (lookup.android(&attrs, "label", ka)) |v| {
                        if (literalLabel(v)) |lit| {
                            if (manifest.app_label) |old| gpa.free(old);
                            manifest.app_label = gpa.dupe(u8, lit) catch return error.OutOfMemory;
                        }
                    } else if (lookup.androidId(&label_ids, "label", ka)) |id| {
                        manifest.app_label_id = id;
                    }
                    if (lookup.androidId(&label_ids, "theme", ka)) |tid| {
                        manifest.app_theme = tid;
                    }
                } else if (std.mem.eql(u8, name, "service") and parent != null and std.mem.eql(u8, parent.?, "application")) {
                    const svc = lookup.android(&attrs, "name", ka) orelse return error.ServiceNoName;
                    manifest.services.append(gpa, className(gpa, manifest.package, svc) catch return error.OutOfMemory) catch return error.OutOfMemory;
                } else if ((std.mem.eql(u8, name, "activity") or std.mem.eql(u8, name, "activity-alias")) and
                    parent != null and std.mem.eql(u8, parent.?, "application"))
                {
                    const act = lookup.android(&attrs, "name", ka) orelse return error.ActivityNoName;
                    if (activity) |a| gpa.free(a);
                    activity = className(gpa, manifest.package, act) catch return error.OutOfMemory;
                    if (activity_label) |old| gpa.free(old);
                    activity_label = null;
                    activity_label_id = null;
                    if (activity_target) |old| gpa.free(old);
                    activity_target = null;
                    if (std.mem.eql(u8, name, "activity-alias")) {
                        if (lookup.android(&attrs, "targetActivity", ka)) |t| {
                            activity_target = className(gpa, manifest.package, t) catch return error.OutOfMemory;
                        }
                    }
                    if (lookup.android(&attrs, "label", ka)) |v| {
                        if (literalLabel(v)) |lit| {
                            activity_label = gpa.dupe(u8, lit) catch return error.OutOfMemory;
                        }
                    } else if (lookup.androidId(&label_ids, "label", ka)) |id| {
                        activity_label_id = id;
                    }
                    if (lookup.androidId(&label_ids, "theme", ka)) |tid| {
                        if (activity) |a| {
                            const key = gpa.dupe(u8, a) catch return error.OutOfMemory;
                            errdefer gpa.free(key);
                            const gop = activity_themes.getOrPut(gpa, key) catch return error.OutOfMemory;
                            if (gop.found_existing) {
                                gpa.free(key);
                            } else {
                                gop.key_ptr.* = key;
                            }
                            gop.value_ptr.* = tid;
                        }
                    }
                } else if (std.mem.eql(u8, name, "intent-filter") and parent != null and
                    (std.mem.eql(u8, parent.?, "activity") or std.mem.eql(u8, parent.?, "activity-alias")))
                {
                    in_filter = true;
                    seen_main = false;
                    seen_launcher = false;
                } else if (std.mem.eql(u8, name, "action") and in_filter and parent != null and
                    std.mem.eql(u8, parent.?, "intent-filter"))
                {
                    if (lookup.android(&attrs, "name", ka)) |v| {
                        seen_main = seen_main or std.mem.eql(u8, v, "android.intent.action.MAIN");
                    }
                } else if (std.mem.eql(u8, name, "category") and in_filter and parent != null and
                    std.mem.eql(u8, parent.?, "intent-filter"))
                {
                    if (lookup.android(&attrs, "name", ka)) |v| {
                        seen_launcher = seen_launcher or std.mem.eql(u8, v, "android.intent.category.LAUNCHER");
                    }
                }
                if (stack.items.len >= 64) return error.NestingTooDeep;
                stack.append(gpa, name) catch return error.OutOfMemory;
            },
            0x103 => {
                if (!pool_owned) return error.InvalidBinaryXml;
                const name = try poolStr(pool.items, try u32At(chunk, 20));
                const popped = stack.pop() orelse return error.UnbalancedElements;
                if (!std.mem.eql(u8, popped, name)) return error.UnbalancedElements;
                if (std.mem.eql(u8, name, "intent-filter") and in_filter) {
                    if (seen_main and seen_launcher and manifest.launcher == null) {
                        // Launcher aliases resolve to their targetActivity.
                        if (activity_target) |t| {
                            manifest.launcher = gpa.dupe(u8, t) catch return error.OutOfMemory;
                        } else if (activity) |a| {
                            manifest.launcher = gpa.dupe(u8, a) catch return error.OutOfMemory;
                        }
                        if (manifest.launcher) |l| {
                            if (activity_themes.get(l)) |tid| manifest.activity_theme = tid;
                        }
                        if (activity_label) |l| manifest.launcher_label = gpa.dupe(u8, l) catch return error.OutOfMemory;
                        if (activity_label_id) |id| manifest.launcher_label_id = id;
                    }
                    in_filter = false;
                }
                if (std.mem.eql(u8, name, "activity") or std.mem.eql(u8, name, "activity-alias")) {
                    if (activity) |a| {
                        gpa.free(a);
                        activity = null;
                    }
                    if (activity_label) |l| {
                        gpa.free(l);
                        activity_label = null;
                    }
                    activity_label_id = null;
                    if (activity_target) |t| {
                        gpa.free(t);
                        activity_target = null;
                    }
                }
            },
            0x100, 0x101, 0x180 => {},
            else => return error.InvalidBinaryXml,
        }
        at = end;
    }
    if (!manifest_seen or stack.items.len != 0) return error.IncompleteManifest;
    if (manifest.min_sdk > 33) return error.SdkTooNew;
    return manifest;
}

pub fn freeManifest(gpa: std.mem.Allocator, m: *Manifest) void {
    if (m.package.len > 0) gpa.free(m.package);
    if (m.application) |a| gpa.free(a);
    if (m.launcher) |l| gpa.free(l);
    if (m.app_label) |l| gpa.free(l);
    if (m.launcher_label) |l| gpa.free(l);
    for (m.services.items) |s| gpa.free(s);
    m.services.deinit(gpa);
    for (m.permissions.items) |p| gpa.free(p);
    m.permissions.deinit(gpa);
    m.* = .{ .package = "" };
}

/// Resolve any `@…` label ids against a parsed resources table. Literal
/// labels (already in `app_label`/`launcher_label`) win; ids fill the
/// gaps; the package stays the final fallback via `manifestTitle`.
/// Missing table entries are not an error — the id fields simply stay
/// set and `manifestTitle` falls back to the package.
pub fn resolveLabels(gpa: std.mem.Allocator, m: *Manifest, t: *const arsc.Table) !void {
    if (m.app_label == null) {
        if (m.app_label_id) |id| {
            if (t.lookupString(id)) |s| {
                m.app_label = try gpa.dupe(u8, s);
            }
        }
    }
    if (m.launcher_label == null) {
        if (m.launcher_label_id) |id| {
            if (t.lookupString(id)) |s| {
                m.launcher_label = try gpa.dupe(u8, s);
            }
        }
    }
}

/// Load `<code_dir>/resources.arsc` and resolve manifest label ids in
/// place. Returns false (not an error) when the table is absent or
/// unparsable: real apps ship one, but a missing/corrupt table must
/// never break install/launch — the package fallback still titles.
pub fn resolveStagedLabels(io: std.Io, gpa: std.mem.Allocator, code_dir: []const u8, m: *Manifest) !bool {
    const path = try std.fmt.allocPrint(gpa, "{s}/resources.arsc", .{code_dir});
    defer gpa.free(path);
    const bytes = std.Io.Dir.cwd().readFileAlloc(io, path, gpa, .limited(arsc.max_arsc)) catch return false;
    defer gpa.free(bytes);
    var t = arsc.Table.parse(gpa, bytes) catch return false;
    defer t.deinit();
    try resolveLabels(gpa, m, &t);
    return true;
}

test "ref labels stay as ids until the table resolves them" {
    const gpa = std.testing.allocator;
    var m = Manifest{ .package = try gpa.dupe(u8, "com.darkempire78.opencalculator"), .app_label_id = 0x7f11002c, .launcher_label_id = 0x7f110129 };
    defer freeManifest(gpa, &m);
    try std.testing.expectEqualStrings("com.darkempire78.opencalculator", manifestTitle(&m));
    const bytes = try arsc.buildSample(gpa);
    defer gpa.free(bytes);
    var t = try arsc.Table.parse(gpa, bytes);
    defer t.deinit();
    m.app_label_id = 0x7f110000;
    m.launcher_label_id = 0x7f110001;
    try resolveLabels(gpa, &m, &t);
    try std.testing.expectEqualStrings("Sample", m.app_label.?);
    try std.testing.expectEqualStrings("Sample", manifestTitle(&m));
    var m2 = Manifest{ .package = try gpa.dupe(u8, "com.foo"), .app_label_id = 0x7f1100FF };
    defer freeManifest(gpa, &m2);
    try resolveLabels(gpa, &m2, &t);
    try std.testing.expect(m2.app_label == null);
    try std.testing.expectEqualStrings("com.foo", manifestTitle(&m2));
}

/// Full inspection: bounds, required entries, ABI gate, manifest parse.
pub fn inspect(
    io: std.Io,
    gpa: std.mem.Allocator,
    apk_path: []const u8,
) (ApkError || std.Io.File.OpenError || std.Io.Dir.StatFileError || error{OutOfMemory})!Manifest {
    var entries: std.ArrayListUnmanaged(ZipEntry) = .empty;
    defer {
        for (entries.items) |e| gpa.free(e.name);
        entries.deinit(gpa);
    }
    try listEntries(io, gpa, apk_path, &entries);
    var has_dex = false;
    var native = false;
    var x86 = false;
    for (entries.items) |e| {
        if (std.mem.eql(u8, e.name, "classes.dex")) has_dex = true;
        if (std.mem.startsWith(u8, e.name, "lib/") and std.mem.endsWith(u8, e.name, ".so")) {
            native = true;
            x86 = x86 or std.mem.startsWith(u8, e.name, "lib/x86_64/");
        }
    }
    if (!has_dex) return error.NoClassesDex;
    // M6a: native libs for the host ABI (lib/x86_64) are supported via the
    // JVM's own dlopen + the bionic shim (src/native.zig). Any other ABI
    // (arm64-v8a, armeabi-v7a, x86, mips...) still has no execution path:
    // reject loudly rather than installing an app that dies at launch
    // (ARM execution is M7, see docs/M7-ARM-PLAN.md).
    if (native and !x86) return error.NativeUnsupported;
    const bytes = try readEntry(io, gpa, apk_path, "AndroidManifest.xml", max_manifest);
    defer gpa.free(bytes);
    return parseManifest(gpa, bytes);
}

pub fn sha256File(io: std.Io, gpa: std.mem.Allocator, path: []const u8) ![]u8 {
    const bytes = try std.Io.Dir.cwd().readFileAlloc(io, path, gpa, .limited(max_entry));
    defer gpa.free(bytes);
    var out: [32]u8 = undefined;
    std.crypto.hash.sha2.Sha256.hash(bytes, &out, .{});
    const hex = std.fmt.bytesToHex(out, .lower);
    return gpa.dupe(u8, &hex);
}

pub fn nowSeconds(io: std.Io) u64 {
    const ts = std.Io.Timestamp.now(io, .real);
    const secs = ts.toSeconds();
    return if (secs < 0) 0 else @intCast(secs);
}

// --- Staging ---

fn wantEntry(name: []const u8) bool {
    if (std.mem.eql(u8, name, "resources.arsc")) return true;
    if (std.mem.eql(u8, name, "AndroidManifest.xml")) return true;
    if (std.mem.startsWith(u8, name, "res/")) return true;
    if (std.mem.startsWith(u8, name, "assets/")) return true;
    if (std.mem.startsWith(u8, name, "lib/x86_64/")) return true;
    if (std.mem.startsWith(u8, name, "classes") and std.mem.endsWith(u8, name, ".dex") and
        std.mem.indexOfScalar(u8, name, '/') == null) return true;
    return false;
}

/// Extract wanted entries into a fresh staging directory. Refuses if the
/// destination exists; removes it again on failure.
pub fn stage(
    io: std.Io,
    gpa: std.mem.Allocator,
    apk_path: []const u8,
    dest: []const u8,
) anyerror!void {
    // Re-inspect for bounds before touching the filesystem.
    var m = try inspect(io, gpa, apk_path);
    defer freeManifest(gpa, &m);

    std.Io.Dir.cwd().createDir(io, dest, .fromMode(0o755)) catch |err| switch (err) {
        error.PathAlreadyExists => return error.StagingExists,
        else => return err,
    };
    errdefer std.Io.Dir.cwd().deleteTree(io, dest) catch {};

    var entries: std.ArrayListUnmanaged(ZipEntry) = .empty;
    defer {
        for (entries.items) |e| gpa.free(e.name);
        entries.deinit(gpa);
    }
    try listEntries(io, gpa, apk_path, &entries);
    for (entries.items) |e| {
        if (std.mem.endsWith(u8, e.name, "/")) continue;
        if (!wantEntry(e.name)) continue;
        const bytes = try readEntry(io, gpa, apk_path, e.name, max_entry);
        defer gpa.free(bytes);
        const out_path = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ dest, e.name });
        defer gpa.free(out_path);
        if (std.fs.path.dirname(out_path)) |dir| {
            try std.Io.Dir.cwd().createDirPath(io, dir);
        }
        var f = try std.Io.Dir.cwd().createFile(io, out_path, .{ .exclusive = true });
        defer f.close(io);
        var wbuf: [8192]u8 = undefined;
        var fw = f.writer(io, &wbuf);
        try fw.interface.writeAll(bytes);
        try fw.interface.flush();
        try f.sync(io);
        try std.Io.Dir.cwd().setFilePermissions(io, out_path, .fromMode(0o644), .{});
    }
}

/// Result of a successful install.
pub const InstallResult = struct {
    package: []const u8,
    uid: u32,
    data_dir: []const u8,
    code_dir: []const u8,
};

/// App UID range: dedicated subrange of the documented-unused 65536–524287
/// space (systemd.io/UIDS-GIDS). NOT the dynamic or homed ranges.
pub const uid_start: u32 = 200000;
pub const uid_end: u32 = 299999;

/// Install an APK: inspect, verify v2 signature, stage code, create data
/// dir, commit registry. Unsigned APKs are rejected unless
/// ISAKAI_ALLOW_UNSIGNED=1 (dev fixtures only).
pub fn install(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    exe: []const u8,
    work: []const u8,
    apk_path: []const u8,
) anyerror!InstallResult {
    var m = try inspect(io, gpa, apk_path);
    defer freeManifest(gpa, &m);
    const fingerprints = apksig.verifyApk(io, gpa, apk_path) catch |err| {
        if (allowUnsigned(env)) {
            std.log.warn("installing unsigned APK (ISAKAI_ALLOW_UNSIGNED=1)", .{});
            return installInner(io, gpa, env, exe, work, apk_path, &m, &.{});
        }
        return err;
    };
    defer {
        for (fingerprints) |f| gpa.free(f);
        gpa.free(fingerprints);
    }
    return installInner(io, gpa, env, exe, work, apk_path, &m, fingerprints);
}

fn allowUnsigned(env: *const std.process.Environ.Map) bool {
    // Dev-only escape hatch for unsigned fixtures.
    return registry.envFlag(env, "ISAKAI_ALLOW_UNSIGNED");
}

fn installInner(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    exe: []const u8,
    work: []const u8,
    apk_path: []const u8,
    m: *Manifest,
    fingerprints: []const []const u8,
) anyerror!InstallResult {
    var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
    defer lock.release();
    var reg = try registry.Registry.loadChecked(io, gpa, work);
    defer reg.deinit();
    if (reg.get(m.package) != null) return error.AlreadyInstalled;
    const uid = reg.nextUid(uid_start, uid_end) orelse return error.NoFreeUid;

    const digest = try sha256File(io, gpa, apk_path);
    defer gpa.free(digest);
    const code_dir = try std.fmt.allocPrint(gpa, "{s}/code/{s}/{s}", .{ work, m.package, digest });
    errdefer gpa.free(code_dir);
    const data_dir = try registry.expectDataDir(gpa, work, m.package);
    errdefer gpa.free(data_dir);
    const code_parent = try std.fmt.allocPrint(gpa, "{s}/code/{s}", .{ work, m.package });
    defer gpa.free(code_parent);
    const data_parent = try std.fmt.allocPrint(gpa, "{s}/data", .{work});
    defer gpa.free(data_parent);
    try std.Io.Dir.cwd().createDirPath(io, code_parent);
    try std.Io.Dir.cwd().createDirPath(io, data_parent);

    try stage(io, gpa, apk_path, code_dir);
    errdefer std.Io.Dir.cwd().deleteTree(io, code_dir) catch {};
    // Post-stage re-hash: the staged code dir names the APK digest; verify
    // the source APK still hashes to it (catches a swapped/replaced APK
    // between inspect and stage, and a partial stage write).
    {
        const rehash = try sha256File(io, gpa, apk_path);
        defer gpa.free(rehash);
        if (!std.mem.eql(u8, rehash, digest)) {
            std.Io.Dir.cwd().deleteTree(io, code_dir) catch {};
            return error.SizeMismatch;
        }
    }

    // Privileged step: create the app user + chown the data dir.
    // Skipped when ISAKAI_NO_PRIVILEGE=1 (dev/test without auth).
    const no_priv = registry.envFlag(env, "ISAKAI_NO_PRIVILEGE");
    if (no_priv) {
        std.Io.Dir.cwd().createDir(io, data_dir, .fromMode(0o700)) catch |err| switch (err) {
            error.PathAlreadyExists => {},
            else => return err,
        };
    } else {
        const noninteractive = registry.envFlag(env, "ISAKAI_NONINTERACTIVE");
        uid_helper.invoke(io, gpa, exe, "allocate", m.package, uid, data_dir, work, noninteractive) catch |err| {
            std.Io.Dir.cwd().deleteTree(io, code_dir) catch {};
            std.Io.Dir.cwd().deleteDir(io, code_parent) catch {};
            return err;
        };
    }

    try reg.add(.{
        .name = m.package,
        .uid = uid,
        .data_dir = data_dir,
        .apk = code_dir,
        .installed_at = nowSeconds(io),
        .signer_sha256 = if (fingerprints.len > 0) fingerprints[0] else null, // reg.add dupes into its arena
        .signer_sha256_set = fingerprints, // ditto (dupes every element)
    });
    try reg.save(io, gpa, work);

    return .{
        .package = try gpa.dupe(u8, m.package),
        .uid = uid,
        .data_dir = data_dir,
        .code_dir = code_dir,
    };
}

/// Uninstall: remove code + data + registry entry. The privileged helper
/// removes the app user and (root-owned) data dir.
///
/// A live app is terminated first, under the same exclusive lock: `.running`
/// with a live pid gets SIGTERM (escalating to SIGKILL) before anything is
/// deleted. A stale `.running` pid (already dead) proceeds straight to
/// removal; `.starting` carries no pid so there is nothing to kill.
/// Kill failure (error.Refused/error.Timeout/...) aborts the uninstall:
/// the registry entry and all files are left untouched.
pub fn uninstall(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    exe: []const u8,
    work: []const u8,
    selector: []const u8,
) anyerror![]u8 {
    var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
    defer lock.release();
    var reg = try registry.Registry.loadChecked(io, gpa, work);
    defer reg.deinit();
    const pkg = reg.resolve(gpa, selector) catch return error.NotInstalled;
    const name = try gpa.dupe(u8, pkg.name);
    errdefer gpa.free(name);
    // Never delete a live app's code+data: terminate first, abort on
    // failure and leave the registry entry + files untouched.
    switch (pkg.state) {
        .running => |r| {
            if (registry.isAlive(r.pid)) {
                std.log.info("uninstall: terminating live {s} (pid {d})", .{ pkg.name, r.pid });
                registry.terminatePid(io, r.pid, registry.default_terminate_timeout_ms) catch |err| {
                    std.log.warn("uninstall: refusing to remove live {s} (pid {d}): {t}", .{ pkg.name, r.pid, err });
                    return err;
                };
            }
        },
        else => {},
    }
    // Mark removing first so a crash mid-uninstall is retryable, not silent.
    var removing = pkg;
    removing.state = .removing;
    try reg.updateState(io, gpa, work, removing);

    if (std.Io.Dir.cwd().statFile(io, pkg.apk, .{}) catch null) |_| {
        try std.Io.Dir.cwd().deleteTree(io, pkg.apk);
        // Remove the per-package parent if now empty; ignore if not.
        if (std.fs.path.dirname(pkg.apk)) |parent| {
            std.Io.Dir.cwd().deleteDir(io, parent) catch {};
        }
    }
    const no_priv = registry.envFlag(env, "ISAKAI_NO_PRIVILEGE");
    if (no_priv) {
        if (std.Io.Dir.cwd().statFile(io, pkg.data_dir, .{}) catch null) |_| {
            try std.Io.Dir.cwd().deleteTree(io, pkg.data_dir);
        }
    } else {
        const noninteractive = registry.envFlag(env, "ISAKAI_NONINTERACTIVE");
        try uid_helper.invoke(io, gpa, exe, "remove", pkg.name, pkg.uid, pkg.data_dir, work, noninteractive);
    }
    _ = reg.remove(pkg.name);
    try reg.save(io, gpa, work);
    return name;
}

test "uninstall aborts on unkillable live pid, keeps entry and files" {
    const gpa = std.testing.allocator;
    const io = std.testing.io;
    var tmp = std.testing.tmpDir(.{});
    defer tmp.cleanup();
    const cwd_path = try std.process.currentPathAlloc(io, gpa);
    defer gpa.free(cwd_path);
    const work = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}", .{ cwd_path, &tmp.sub_path });
    defer gpa.free(work);
    const pkg_apk = try std.fmt.allocPrint(gpa, "{s}/code/org.example.live", .{work});
    defer gpa.free(pkg_apk);
    const data_dir = try std.fmt.allocPrint(gpa, "{s}/data/org.example.live", .{work});
    defer gpa.free(data_dir);
    try std.Io.Dir.cwd().createDirPath(io, pkg_apk);
    try std.Io.Dir.cwd().createDirPath(io, data_dir);
    const keep_path = try std.fmt.allocPrint(gpa, "{s}/keep.txt", .{pkg_apk});
    defer gpa.free(keep_path);
    {
        var f = try std.Io.Dir.cwd().createFile(io, keep_path, .{});
        defer f.close(io);
        var buf: [64]u8 = undefined;
        var fw = f.writer(io, &buf);
        try fw.interface.writeAll("live");
        try fw.interface.flush();
    }
    {
        var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
        defer lock.release();
        var reg = try registry.Registry.loadChecked(io, gpa, work);
        defer reg.deinit();
        // Unkillable by design: pid 1 (init) is always Refused.
        try reg.add(.{
            .name = "org.example.live",
            .uid = 6000,
            .data_dir = data_dir,
            .apk = pkg_apk,
            .installed_at = 1,
            .state = .{ .running = .{ .pid = 1, .started = 1 } },
        });
        try reg.save(io, gpa, work);
    }
    var env = std.process.Environ.Map.init(gpa);
    defer env.deinit();
    try env.put("ISAKAI_NO_PRIVILEGE", "1");
    // Registry isAlive(1) may be false in a container without init as
    // pid 1: only assert when the guard actually engages.
    if (!registry.isAlive(1)) return error.SkipZigTest;
    try std.testing.expectError(error.Refused, uninstall(io, gpa, &env, "isakai", work, "org.example.live"));
    var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
    defer lock.release();
    var reg = try registry.Registry.loadChecked(io, gpa, work);
    defer reg.deinit();
    const pkg = reg.get("org.example.live") orelse return error.TestUnexpectedResult;
    try std.testing.expect(pkg.state == .running);
    const stat = try std.Io.Dir.cwd().statFile(io, keep_path, .{});
    try std.testing.expect(stat.size == 4);
}

test "validPackage accepts dotted identifiers, rejects the rest" {
    try std.testing.expect(validPackage("org.isakai.fixture"));
    try std.testing.expect(validPackage("com.example.a1_B2"));
    try std.testing.expect(!validPackage("no-dot"));
    try std.testing.expect(!validPackage(""));
    try std.testing.expect(!validPackage("org.1bad.start"));
    try std.testing.expect(!validPackage("org..empty"));
    try std.testing.expect(!validPackage(".leading.dot"));
    try std.testing.expect(!validPackage("org.isakai.bad-char!"));
}

test "validateEntry rejects traversal and absolute paths" {
    try validateEntry("classes.dex");
    try validateEntry("res/layout/main.xml");
    try validateEntry("lib/x86_64/libfoo.so");
    try std.testing.expectError(error.UnsafeEntry, validateEntry("../evil"));
    try std.testing.expectError(error.UnsafeEntry, validateEntry("/abs"));
    try std.testing.expectError(error.UnsafeEntry, validateEntry("a//b"));
    try std.testing.expectError(error.UnsafeEntry, validateEntry("a/./b"));
    try std.testing.expectError(error.UnsafeEntry, validateEntry(""));
    try std.testing.expectError(error.UnsafeEntry, validateEntry("back\\slash"));
}

test "inspect rejects a non-ZIP file" {
    const gpa = std.testing.allocator;
    const io = std.testing.io;
    var tmp = std.testing.tmpDir(.{});
    defer tmp.cleanup();
    const cwd_path = try std.process.currentPathAlloc(io, gpa);
    defer gpa.free(cwd_path);
    const path = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}/junk.apk", .{ cwd_path, &tmp.sub_path });
    defer gpa.free(path);
    var f = try std.Io.Dir.cwd().createFile(io, path, .{});
    defer f.close(io);
    var buf: [64]u8 = undefined;
    var fw = f.writer(io, &buf);
    try fw.interface.writeAll("this is not a zip file at all, just text");
    try fw.interface.flush();
    const err = inspect(io, gpa, path) catch |e| e;
    // Stat may succeed; the ZIP parse must fail as truncated/corrupt.
    try std.testing.expect(err == error.Truncated);
}

test "parseManifest rejects plain XML text" {
    const gpa = std.testing.allocator;
    const text = "<?xml version=\"1.0\"?><manifest package=\"x.y\"/>";
    try std.testing.expectError(error.InvalidBinaryXml, parseManifest(gpa, text));
}

test "parseManifest rejects truncated input" {
    const gpa = std.testing.allocator;
    try std.testing.expectError(error.Truncated, parseManifest(gpa, &[_]u8{ 3, 0 }));
}
