// SPDX-License-Identifier: GPL-3.0-or-later

//! DEX parser: header verification + section tables. Translation to JVM
//! classes lives in translate.zig (same module).

const std = @import("std");

pub const DexError = error{
    Truncated,
    BadMagic,
    BadVersion,
    BadFileSize,
    BadHeaderSize,
    BadEndian,
    BadChecksum,
    BadSignature,
    BadSection,
    TooManyIds,
    BadIndex,
    BadString,
    BadDescriptor,
    OdexInput,
    OutOfMemory,
};

pub const header_size: usize = 0x70;
pub const max_ids: usize = 1 << 20;

pub const Header = struct {
    magic: [8]u8,
    checksum: u32,
    signature: [20]u8,
    file_size: u32,
    header_size: u32,
    endian_tag: u32,
    link_size: u32,
    link_off: u32,
    map_off: u32,
    string_ids_size: u32,
    string_ids_off: u32,
    type_ids_size: u32,
    type_ids_off: u32,
    proto_ids_size: u32,
    proto_ids_off: u32,
    field_ids_size: u32,
    field_ids_off: u32,
    method_ids_size: u32,
    method_ids_off: u32,
    class_defs_size: u32,
    class_defs_off: u32,
    data_size: u32,
    data_off: u32,
};

fn u16At(data: []const u8, at: usize) DexError!u16 {
    if (at + 2 > data.len) return error.Truncated;
    return std.mem.readInt(u16, data[at..][0..2], .little);
}

fn u32At(data: []const u8, at: usize) DexError!u32 {
    if (at + 4 > data.len) return error.Truncated;
    return std.mem.readInt(u32, data[at..][0..4], .little);
}

pub fn parseHeader(data: []const u8) DexError!Header {
    if (data.len < header_size) return error.Truncated;
    // odex/oat detection: same magic family, different prefix.
    if (std.mem.startsWith(u8, data, "dey\n") or std.mem.startsWith(u8, data, "oat\n")) {
        return error.OdexInput;
    }
    var h: Header = undefined;
    @memcpy(&h.magic, data[0..8]);
    if (!std.mem.eql(u8, h.magic[0..4], "dex\n") or h.magic[7] != 0) return error.BadMagic;
    const version = std.fmt.parseInt(u32, h.magic[4..7], 10) catch return error.BadVersion;
    if (version < 35 or version > 40) return error.BadVersion;
    h.checksum = try u32At(data, 8);
    @memcpy(&h.signature, data[12..32]);
    h.file_size = try u32At(data, 32);
    h.header_size = try u32At(data, 36);
    h.endian_tag = try u32At(data, 40);
    h.link_size = try u32At(data, 44);
    h.link_off = try u32At(data, 48);
    h.map_off = try u32At(data, 52);
    h.string_ids_size = try u32At(data, 56);
    h.string_ids_off = try u32At(data, 60);
    h.type_ids_size = try u32At(data, 64);
    h.type_ids_off = try u32At(data, 68);
    h.proto_ids_size = try u32At(data, 72);
    h.proto_ids_off = try u32At(data, 76);
    h.field_ids_size = try u32At(data, 80);
    h.field_ids_off = try u32At(data, 84);
    h.method_ids_size = try u32At(data, 88);
    h.method_ids_off = try u32At(data, 92);
    h.class_defs_size = try u32At(data, 96);
    h.class_defs_off = try u32At(data, 100);
    h.data_size = try u32At(data, 104);
    h.data_off = try u32At(data, 108);

    if (h.file_size != data.len) return error.BadFileSize;
    if (h.header_size != header_size) return error.BadHeaderSize;
    if (h.endian_tag != 0x12345678) return error.BadEndian;

    // Adler-32 over bytes 12..file_size.
    if (std.hash.Adler32.hash(data[12..]) != h.checksum) return error.BadChecksum;
    // SHA-1 over bytes 32..file_size.
    var digest: [20]u8 = undefined;
    std.crypto.hash.Sha1.hash(data[32..], &digest, .{});
    if (!std.mem.eql(u8, &digest, &h.signature)) return error.BadSignature;

    // Section bounds + id count caps.
    const sections = [_]struct { size: u32, off: u32, item: usize }{
        .{ .size = h.string_ids_size, .off = h.string_ids_off, .item = 4 },
        .{ .size = h.type_ids_size, .off = h.type_ids_off, .item = 4 },
        .{ .size = h.proto_ids_size, .off = h.proto_ids_off, .item = 12 },
        .{ .size = h.field_ids_size, .off = h.field_ids_off, .item = 8 },
        .{ .size = h.method_ids_size, .off = h.method_ids_off, .item = 8 },
        .{ .size = h.class_defs_size, .off = h.class_defs_off, .item = 32 },
    };
    for (sections) |s| {
        if (s.size > max_ids) return error.TooManyIds;
        if (s.size == 0) continue;
        const bytes = @as(usize, s.size) * s.item;
        const end = @as(usize, s.off) + bytes;
        if (s.off < header_size or end > data.len) return error.BadSection;
    }
    return h;
}

pub const StringId = struct { off: u32 };
pub const TypeId = struct { descriptor_idx: u32 };
pub const ProtoId = struct {
    shorty_idx: u32,
    return_type_idx: u32,
    parameters_off: u32,
};
pub const FieldId = struct {
    class_idx: u16,
    type_idx: u16,
    name_idx: u32,
};
pub const MethodId = struct {
    class_idx: u16,
    proto_idx: u16,
    name_idx: u32,
};
pub const ClassDef = struct {
    class_idx: u32,
    access_flags: u32,
    superclass_idx: u32, // NO_INDEX = 0xFFFFFFFF
    interfaces_off: u32,
    source_file_idx: u32,
    annotations_off: u32,
    class_data_off: u32,
    static_values_off: u32,
};

pub const no_index: u32 = 0xFFFFFFFF;

pub const Dex = struct {
    data: []const u8,
    header: Header,

    pub fn stringId(self: Dex, i: usize) DexError!StringId {
        const at = try self.idAt(self.header.string_ids_off, self.header.string_ids_size, i, 4);
        return .{ .off = try u32At(self.data, at) };
    }

    /// Decode a MUTF-8 string data item. Caller owns the result (WTF-8/UTF-8 bytes).
    pub fn getString(self: Dex, gpa: std.mem.Allocator, idx: u32) DexError![]u8 {
        const sid = try self.stringId(idx);
        var pos: usize = sid.off;
        // uleb128 utf16 length (ignored; we decode to end by bytes).
        _ = try readUleb128(self.data, &pos);
        const start = pos;
        // MUTF-8: NUL encoded as C0 80, surrogate pairs for non-BMP.
        var out: std.ArrayListUnmanaged(u8) = .empty;
        errdefer out.deinit(gpa);
        while (true) {
            if (pos >= self.data.len) return error.BadString;
            const b = self.data[pos];
            pos += 1;
            if (b == 0) break;
            if (b < 0x80) {
                out.append(gpa, b) catch return error.OutOfMemory;
            } else if (b >> 5 == 0b110) {
                if (pos >= self.data.len) return error.BadString;
                const b2 = self.data[pos];
                pos += 1;
                if (b2 >> 6 != 0b10) return error.BadString;
                const cp: u21 = (@as(u21, b & 0x1F) << 6) | (b2 & 0x3F);
                if (cp == 0) {
                    // MUTF-8 NUL: keep as real NUL byte.
                    out.append(gpa, 0) catch return error.OutOfMemory;
                } else {
                    var enc: [4]u8 = undefined;
                    const n = std.unicode.utf8Encode(cp, &enc) catch return error.BadString;
                    out.appendSlice(gpa, enc[0..n]) catch return error.OutOfMemory;
                }
            } else if (b >> 4 == 0b1110) {
                if (pos + 1 >= self.data.len) return error.BadString;
                const b2 = self.data[pos];
                const b3 = self.data[pos + 1];
                pos += 2;
                if (b2 >> 6 != 0b10 or b3 >> 6 != 0b10) return error.BadString;
                var cp: u21 = (@as(u21, b & 0x0F) << 12) | (@as(u21, b2 & 0x3F) << 6) | (b3 & 0x3F);
                // CESU-8: lead surrogate followed by trail surrogate
                // encodes one non-BMP codepoint; combine before encoding.
                if (cp >= 0xD800 and cp <= 0xDBFF and pos + 2 < self.data.len) {
                    const c2 = self.data[pos];
                    if (c2 >> 4 == 0b1110) {
                        const d2 = self.data[pos + 1];
                        const d3 = self.data[pos + 2];
                        if (d2 >> 6 == 0b10 and d3 >> 6 == 0b10) {
                            const lo: u21 = (@as(u21, c2 & 0x0F) << 12) | (@as(u21, d2 & 0x3F) << 6) | (d3 & 0x3F);
                            if (lo >= 0xDC00 and lo <= 0xDFFF) {
                                pos += 3;
                                cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                            }
                        }
                    }
                }
                var enc: [4]u8 = undefined;
                const n = std.unicode.utf8Encode(cp, &enc) catch {
                    // Lone surrogate: pass through as WTF-8 (3-byte form).
                    const wtf: [3]u8 = .{ b, b2, b3 };
                    out.appendSlice(gpa, &wtf) catch return error.OutOfMemory;
                    continue;
                };
                out.appendSlice(gpa, enc[0..n]) catch return error.OutOfMemory;
            } else {
                return error.BadString;
            }
        }
        _ = start;
        return out.toOwnedSlice(gpa) catch return error.OutOfMemory;
    }

    pub fn typeId(self: Dex, i: usize) DexError!TypeId {
        const at = try self.idAt(self.header.type_ids_off, self.header.type_ids_size, i, 4);
        return .{ .descriptor_idx = try u32At(self.data, at) };
    }

    pub fn getTypeDescriptor(self: Dex, gpa: std.mem.Allocator, idx: u32) DexError![]u8 {
        const t = try self.typeId(idx);
        return self.getString(gpa, t.descriptor_idx);
    }

    pub fn protoId(self: Dex, i: usize) DexError!ProtoId {
        const at = try self.idAt(self.header.proto_ids_off, self.header.proto_ids_size, i, 12);
        return .{
            .shorty_idx = try u32At(self.data, at),
            .return_type_idx = try u32At(self.data, at + 4),
            .parameters_off = try u32At(self.data, at + 8),
        };
    }

    pub fn fieldId(self: Dex, i: usize) DexError!FieldId {
        const at = try self.idAt(self.header.field_ids_off, self.header.field_ids_size, i, 8);
        return .{
            .class_idx = try u16At(self.data, at),
            .type_idx = try u16At(self.data, at + 2),
            .name_idx = try u32At(self.data, at + 4),
        };
    }

    pub fn methodId(self: Dex, i: usize) DexError!MethodId {
        const at = try self.idAt(self.header.method_ids_off, self.header.method_ids_size, i, 8);
        return .{
            .class_idx = try u16At(self.data, at),
            .proto_idx = try u16At(self.data, at + 2),
            .name_idx = try u32At(self.data, at + 4),
        };
    }

    pub fn classDef(self: Dex, i: usize) DexError!ClassDef {
        const at = try self.idAt(self.header.class_defs_off, self.header.class_defs_size, i, 32);
        return .{
            .class_idx = try u32At(self.data, at),
            .access_flags = try u32At(self.data, at + 4),
            .superclass_idx = try u32At(self.data, at + 8),
            .interfaces_off = try u32At(self.data, at + 12),
            .source_file_idx = try u32At(self.data, at + 16),
            .annotations_off = try u32At(self.data, at + 20),
            .class_data_off = try u32At(self.data, at + 24),
            .static_values_off = try u32At(self.data, at + 28),
        };
    }
    /// Absolute offset of index `i` in an id table (bounds-checked).
    /// Shared by the 6 accessors (stringId/typeId/protoId/fieldId/methodId/classDef).
    pub fn idAt(self: Dex, off: u32, size: u32, i: usize, item: usize) DexError!usize {
        if (i >= size) return error.BadIndex;
        const at: usize = off + i * item;
        if (at + item > self.data.len) return error.Truncated;
        return at;
    }
};
/// Max entries in one type_list (method params, interface lists).
/// Unifies the 255/256 caps that disagreed across translate.zig's three
/// param readers: 255 is the class-file ceiling (one slot is `this` or
/// the return), so every reader uses it.
pub const max_type_list: u32 = 255;


/// Read a type_list at file offset `off` (u32 size + u16 type idxs).
/// Zero offset = empty. Caller owns the result. Shared by translate's
/// emitInvokeArgs / prologue / protoDescriptor and the interfaces reader.
pub fn readTypeList(gpa: std.mem.Allocator, d: Dex, off: u32) DexError![]u16 {
    if (off == 0) return gpa.alloc(u16, 0) catch return error.OutOfMemory;
    if (off + 4 > d.data.len) return error.Truncated;
    const n = std.mem.readInt(u32, d.data[off..][0..4], .little);
    if (n > max_type_list) return error.BadSection;
    if (off + 4 + @as(usize, n) * 2 > d.data.len) return error.Truncated;
    const out = gpa.alloc(u16, n) catch return error.OutOfMemory;
    for (out, 0..) |*t, i| {
        t.* = std.mem.readInt(u16, d.data[off + 4 + i * 2 ..][0..2], .little);
    }
    return out;
}

/// First descriptor char per entry of the type_list at `off`
/// (e.g. "IJL[" kinds for params). Caller owns the result. Shared by
/// emitInvokeArgs and the translateMethod prologue.
pub fn paramKinds(gpa: std.mem.Allocator, d: Dex, off: u32) DexError![]u8 {
    const idxs = try readTypeList(gpa, d, off);
    defer gpa.free(idxs);
    const out = gpa.alloc(u8, idxs.len) catch return error.OutOfMemory;
    errdefer gpa.free(out);
    for (idxs, 0..) |t, i| {
        const desc = try d.getTypeDescriptor(gpa, t);
        defer gpa.free(desc);
        if (desc.len == 0) {
            gpa.free(out);
            return error.BadDescriptor;
        }
        out[i] = desc[0];
    }
    return out;
}

/// One diff-coded index step (class_data fields/methods): idx += diff.
/// Shared by readFields/readMethods.
pub fn readDiffCoded(data: []const u8, pos: *usize, acc: *u32) DexError!void {
    const diff = try readUleb128(data, pos);
    acc.* = std.math.add(u32, acc.*, diff) catch return error.BadSection;
}

pub fn readUleb128(data: []const u8, pos: *usize) DexError!u32 {
    var result: u32 = 0;
    var shift: u5 = 0;
    for (0..5) |_| {
        if (pos.* >= data.len) return error.Truncated;
        const b = data[pos.*];
        pos.* += 1;
        result |= @as(u32, b & 0x7F) << shift;
        if (b & 0x80 == 0) return result;
        shift += 7;
    }
    return error.BadString;
}

pub fn readSleb128(data: []const u8, pos: *usize) DexError!i32 {
    var result: i32 = 0;
    var shift: u5 = 0;
    var b: u8 = 0;
    for (0..5) |_| {
        if (pos.* >= data.len) return error.Truncated;
        b = data[pos.*];
        pos.* += 1;
        result |= @as(i32, b & 0x7F) << shift;
        shift += 7;
        if (b & 0x80 == 0) break;
    } else return error.Truncated;
    if (shift < 32 and (b & 0x40) != 0) {
        result |= @as(i32, -1) << shift;
    }
    return result;
}

pub const EncodedField = struct {
    field_idx: u32, // diff-decoded to absolute
    access_flags: u32,
};

pub const EncodedMethod = struct {
    method_idx: u32, // diff-decoded to absolute
    access_flags: u32,
    code_off: u32,
};

pub const ClassData = struct {
    static_fields: []EncodedField,
    instance_fields: []EncodedField,
    direct_methods: []EncodedMethod,
    virtual_methods: []EncodedMethod,

    pub fn deinit(self: *ClassData, gpa: std.mem.Allocator) void {
        gpa.free(self.static_fields);
        gpa.free(self.instance_fields);
        gpa.free(self.direct_methods);
        gpa.free(self.virtual_methods);
    }
};

fn readFields(gpa: std.mem.Allocator, data: []const u8, pos: *usize, count: u32) DexError![]EncodedField {
    if (count > max_ids) return error.TooManyIds;
    const out = gpa.alloc(EncodedField, count) catch return error.OutOfMemory;
    errdefer gpa.free(out);
    var idx: u32 = 0;
    for (out) |*f| {
        try readDiffCoded(data, pos, &idx);
        f.field_idx = idx;
        f.access_flags = try readUleb128(data, pos);
    }
    return out;
}

fn readMethods(gpa: std.mem.Allocator, data: []const u8, pos: *usize, count: u32) DexError![]EncodedMethod {
    if (count > max_ids) return error.TooManyIds;
    const out = gpa.alloc(EncodedMethod, count) catch return error.OutOfMemory;
    errdefer gpa.free(out);
    var idx: u32 = 0;
    for (out) |*m| {
        try readDiffCoded(data, pos, &idx);
        m.method_idx = idx;
        m.access_flags = try readUleb128(data, pos);
        m.code_off = try readUleb128(data, pos);
    }
    return out;
}

pub fn readClassData(gpa: std.mem.Allocator, dex: Dex, off: u32) DexError!ClassData {
    if (off == 0) return .{
        .static_fields = &.{},
        .instance_fields = &.{},
        .direct_methods = &.{},
        .virtual_methods = &.{},
    };
    var pos: usize = off;
    if (pos >= dex.data.len) return error.Truncated;
    const n_static = try readUleb128(dex.data, &pos);
    const n_instance = try readUleb128(dex.data, &pos);
    const n_direct = try readUleb128(dex.data, &pos);
    const n_virtual = try readUleb128(dex.data, &pos);
    var out = ClassData{
        .static_fields = try readFields(gpa, dex.data, &pos, n_static),
        .instance_fields = try readFields(gpa, dex.data, &pos, n_instance),
        .direct_methods = try readMethods(gpa, dex.data, &pos, n_direct),
        .virtual_methods = try readMethods(gpa, dex.data, &pos, n_virtual),
    };
    errdefer out.deinit(gpa);
    return out;
}

pub const TryItem = struct {
    start_addr: u32,
    insn_count: u16,
    handler_off: u16,
};

pub const Code = struct {
    registers: u16,
    ins: u16,
    outs: u16,
    tries: []TryItem,
    insns: []const u16, // code units (owned)
    debug_off: u32, // debug_info_item file offset (0 = none)
    handlers_base: u32, // file offset of encoded_catch_handler_list

    pub fn deinit(self: *Code, gpa: std.mem.Allocator) void {
        gpa.free(self.tries);
        gpa.free(self.insns);
    }
};

pub const CatchHandler = struct {
    type_idx: u32, // NO_INDEX (maxInt) = catch-all
    addr: u32, // code-unit offset of handler
};

/// Parse the encoded_catch_handler at handlers_base + handler_off.
/// Caller owns the returned slice.
pub fn readCatchHandlers(gpa: std.mem.Allocator, dex: Dex, code: Code, handler_off: u32) DexError![]CatchHandler {
    var pos: usize = std.math.add(u32, code.handlers_base, handler_off) catch return error.BadSection;
    const size = try readSleb128(dex.data, &pos);
    const n: usize = @intCast(if (size < 0) -size else size);
    const has_catchall = size <= 0;
    if (n > 1024) return error.BadSection;
    const total = n + (if (has_catchall) @as(usize, 1) else 0);
    const out = gpa.alloc(CatchHandler, total) catch return error.OutOfMemory;
    errdefer gpa.free(out);
    for (out[0..n]) |*h| {
        h.type_idx = try readUleb128(dex.data, &pos);
        h.addr = try readUleb128(dex.data, &pos);
    }
    if (has_catchall) {
        out[n] = .{ .type_idx = std.math.maxInt(u32), .addr = try readUleb128(dex.data, &pos) };
    }
    return out;
}

pub fn readCode(gpa: std.mem.Allocator, dex: Dex, off: u32) DexError!Code {
    var pos: usize = off;
    if (pos + 16 > dex.data.len) return error.Truncated;
    const registers = try u16At(dex.data, pos);
    const ins = try u16At(dex.data, pos + 2);
    const outs = try u16At(dex.data, pos + 4);
    const tries_size = try u16At(dex.data, pos + 6);
    const debug_off = try u32At(dex.data, pos + 8);
    const insns_size = try u32At(dex.data, pos + 12);
    if (registers > 65536 or insns_size > (1 << 24)) return error.BadSection;
    pos += 16;
    const insns_bytes = @as(usize, insns_size) * 2;
    if (pos + insns_bytes > dex.data.len) return error.Truncated;
    // Copy to an aligned buffer: the DEX slice itself may be odd-aligned.
    const insns = gpa.alloc(u16, insns_size) catch return error.OutOfMemory;
    errdefer gpa.free(insns);
    for (insns, 0..) |*u, i| {
        u.* = std.mem.readInt(u16, dex.data[pos + i * 2 ..][0..2], .little);
    }
    pos += insns_bytes;
    // Padding to 4 bytes if tries exist and insns_size is odd.
    if (tries_size > 0 and insns_size % 2 == 1) {
        if (pos + 2 > dex.data.len) return error.Truncated;
        pos += 2;
    }
    if (tries_size > 4096) return error.BadSection;
    const tries = gpa.alloc(TryItem, tries_size) catch return error.OutOfMemory;
    errdefer gpa.free(tries);
    for (tries) |*t| {
        if (pos + 8 > dex.data.len) {
            gpa.free(tries);
            return error.Truncated;
        }
        t.start_addr = try u32At(dex.data, pos);
        t.insn_count = try u16At(dex.data, pos + 4);
        t.handler_off = try u16At(dex.data, pos + 6);
        pos += 8;
    }
    return .{
        .registers = registers,
        .ins = ins,
        .outs = outs,
        .tries = tries,
        .insns = insns,
        .debug_off = debug_off,
        .handlers_base = @intCast(pos),
    };
}

/// (dex_pc code units, source line) decoded from a debug_info_item.
pub const DebugLine = struct {
    addr: u32,
    line: u32,
};

/// Decode the debug_info_item state machine into (address, line) entries.
/// Only line-relevant opcodes are tracked; position/advance opcodes update
/// the (address, line) registers, prologue/epilogue/file opcodes ignored.
/// Entries are in emission order (caller sorts/dedups). Malformed/truncated
/// items return an error. Caller owns the result.
pub fn readDebugLines(gpa: std.mem.Allocator, d: Dex, debug_off: u32) DexError![]DebugLine {
    var out: std.ArrayListUnmanaged(DebugLine) = .empty;
    errdefer out.deinit(gpa);
    if (debug_off == 0) return out.toOwnedSlice(gpa) catch return error.OutOfMemory;
    var pos: usize = debug_off;
    if (pos >= d.data.len) return error.Truncated;
    var line: u32 = try readUleb128(d.data, &pos);
    const param_count = try readUleb128(d.data, &pos);
    if (param_count > 65536) return error.BadSection;
    var pi: u32 = 0;
    while (pi < param_count) : (pi += 1) {
        _ = try readUleb128(d.data, &pos);
    }
    var addr: u32 = 0;
    while (true) {
        if (pos >= d.data.len) return error.Truncated;
        const op = d.data[pos];
        pos += 1;
        switch (op) {
            0x00 => break,
            0x01 => {
                const delta = try readUleb128(d.data, &pos);
                addr = std.math.add(u32, addr, delta) catch return error.BadSection;
            },
            0x02 => {
                const delta = try readSleb128(d.data, &pos);
                const nl: i64 = @as(i64, line) + delta;
                // Line 0 is legal DEX ("no line info", e.g. synthetic
                // lambdas with line_start=0); clamp negatives, keep 0
                // internally, and skip emitting it below.
                if (nl > 0xFFFFFF) return error.BadSection;
                line = if (nl < 0) 0 else @intCast(nl);
            },
            0x03, 0x04 => {
                _ = try readUleb128(d.data, &pos);
                _ = try readUleb128(d.data, &pos);
                _ = try readUleb128(d.data, &pos);
                if (op == 0x04) _ = try readUleb128(d.data, &pos);
            },
            0x05 => {
                _ = try readUleb128(d.data, &pos);
            },
            0x06 => {
                _ = try readUleb128(d.data, &pos);
            },
            0x07 => {},
            0x08 => {},
            0x09 => {
                _ = try readUleb128(d.data, &pos);
            },
            else => {
                const adj = @as(u32, op) - 0x0a;
                const addr_delta: u32 = adj / 15;
                const line_delta: i32 = @as(i32, @intCast(adj % 15)) - 4;
                addr = std.math.add(u32, addr, addr_delta) catch return error.BadSection;
                const nl: i64 = @as(i64, line) + line_delta;
                if (nl > 0xFFFFFF) return error.BadSection;
                line = if (nl < 0) 0 else @intCast(nl);
                // Skip line-0 entries (JVM line numbers start at 1).
                if (line > 0) try out.append(gpa, .{ .addr = addr, .line = line });
            },
        }
        if (out.items.len > 1 << 20) return error.BadSection;
    }
    return out.toOwnedSlice(gpa) catch return error.OutOfMemory;
}

test "header rejects junk" {
    try std.testing.expectError(error.Truncated, parseHeader(&[_]u8{ 1, 2, 3 }));
    var bad: [0x70]u8 = undefined;
    @memset(&bad, 0);
    try std.testing.expectError(error.BadMagic, parseHeader(&bad));
    @memcpy(bad[0..4], "dey\n");
    try std.testing.expectError(error.OdexInput, parseHeader(&bad));
}

test "uleb128 decodes" {
    const data = [_]u8{ 0xE5, 0x8E, 0x26, 0x7F, 0x80, 0x01 };
    var pos: usize = 0;
    try std.testing.expectEqual(@as(u32, 624485), try readUleb128(&data, &pos));
    try std.testing.expectEqual(@as(usize, 3), pos);
    try std.testing.expectEqual(@as(u32, 127), try readUleb128(&data, &pos));
    try std.testing.expectEqual(@as(u32, 128), try readUleb128(&data, &pos));
}
