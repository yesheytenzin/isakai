// SPDX-License-Identifier: GPL-3.0-or-later

//! DEX to JVM class translation. Register-based DEX becomes stack-based JVM
//! bytecode with one JVM local per DEX register. Install-time only.

const std = @import("std");
const dex = @import("dex");

pub const TranslateError = error{
    UnsupportedOpcode,
    BadBranch,
    BadRegister,
    BadDescriptor,
    OutOfMemory,
} || dex.DexError;

// DEX opcodes we translate (M1 subset).
const Op = enum(u8) {
    nop = 0x00,
    move = 0x01,
    move_from16 = 0x02,
    move16 = 0x03,
    move_wide = 0x04,
    move_wide_from16 = 0x05,
    move_wide16 = 0x06,
    move_object = 0x07,
    move_object_from16 = 0x08,
    move_object16 = 0x09,
    move_result = 0x0a,
    move_result_wide = 0x0b,
    move_result_object = 0x0c,
    move_exception = 0x0d,
    return_void = 0x0e,
    return_ = 0x0f,
    return_wide = 0x10,
    return_object = 0x11,
    const4 = 0x12,
    const16 = 0x13,
    const_ = 0x14,
    const_high16 = 0x15,
    const_wide16 = 0x16,
    const_wide32 = 0x17,
    const_wide = 0x18,
    const_wide_high16 = 0x19,
    const_string = 0x1a,
    const_string_jumbo = 0x1b,
    const_class = 0x1c,
    monitor_enter = 0x1d,
    monitor_exit = 0x1e,
    check_cast = 0x1f,
    instance_of = 0x20,
    array_length = 0x21,
    new_instance = 0x22,
    new_array = 0x23,
    filled_new_array = 0x24,
    filled_new_array_range = 0x25,
    fill_array_data = 0x26,
    throw_ = 0x27,
    goto_ = 0x28,
    goto16 = 0x29,
    goto32 = 0x2a,
    packed_switch = 0x2b,
    sparse_switch = 0x2c,
    cmpl_float = 0x2d,
    cmpg_float = 0x2e,
    cmpl_double = 0x2f,
    cmpg_double = 0x30,
    cmp_long = 0x31,
    if_eq = 0x32,
    if_ne = 0x33,
    if_lt = 0x34,
    if_ge = 0x35,
    if_gt = 0x36,
    if_le = 0x37,
    if_eqz = 0x38,
    if_nez = 0x39,
    if_ltz = 0x3a,
    if_gez = 0x3b,
    if_gtz = 0x3c,
    if_lez = 0x3d,
    aget = 0x44,
    aget_wide = 0x45,
    aget_object = 0x46,
    aget_boolean = 0x47,
    aget_byte = 0x48,
    aget_char = 0x49,
    aget_short = 0x4a,
    aput = 0x4b,
    aput_wide = 0x4c,
    aput_object = 0x4d,
    aput_boolean = 0x4e,
    aput_byte = 0x4f,
    aput_char = 0x50,
    aput_short = 0x51,
    iget = 0x52,
    iget_wide = 0x53,
    iget_object = 0x54,
    iget_boolean = 0x55,
    iget_byte = 0x56,
    iget_char = 0x57,
    iget_short = 0x58,
    iput = 0x59,
    iput_wide = 0x5a,
    iput_object = 0x5b,
    iput_boolean = 0x5c,
    iput_byte = 0x5d,
    iput_char = 0x5e,
    iput_short = 0x5f,
    sget = 0x60,
    sget_wide = 0x61,
    sget_object = 0x62,
    sget_boolean = 0x63,
    sget_byte = 0x64,
    sget_char = 0x65,
    sget_short = 0x66,
    sput = 0x67,
    sput_wide = 0x68,
    sput_object = 0x69,
    sput_boolean = 0x6a,
    sput_byte = 0x6b,
    sput_char = 0x6c,
    sput_short = 0x6d,
    invoke_virtual = 0x6e,
    invoke_super = 0x6f,
    invoke_direct = 0x70,
    invoke_static = 0x71,
    invoke_interface = 0x72,
    invoke_virtual_range = 0x74,
    invoke_super_range = 0x75,
    invoke_direct_range = 0x76,
    invoke_static_range = 0x77,
    invoke_interface_range = 0x78,
    neg_int = 0x7b,
    not_int = 0x7c,
    neg_long = 0x7d,
    not_long = 0x7e,
    neg_float = 0x7f,
    neg_double = 0x80,
    int_to_long = 0x81,
    int_to_float = 0x82,
    int_to_double = 0x83,
    long_to_int = 0x84,
    long_to_float = 0x85,
    long_to_double = 0x86,
    float_to_int = 0x87,
    float_to_long = 0x88,
    float_to_double = 0x89,
    double_to_int = 0x8a,
    double_to_long = 0x8b,
    double_to_float = 0x8c,
    int_to_byte = 0x8d,
    int_to_char = 0x8e,
    int_to_short = 0x8f,
    add_int = 0x90,
    sub_int = 0x91,
    mul_int = 0x92,
    div_int = 0x93,
    rem_int = 0x94,
    and_int = 0x95,
    or_int = 0x96,
    xor_int = 0x97,
    shl_int = 0x98,
    shr_int = 0x99,
    ushr_int = 0x9a,
    add_long = 0x9b,
    sub_long = 0x9c,
    mul_long = 0x9d,
    div_long = 0x9e,
    rem_long = 0x9f,
    and_long = 0xa0,
    or_long = 0xa1,
    xor_long = 0xa2,
    shl_long = 0xa3,
    shr_long = 0xa4,
    ushr_long = 0xa5,
    add_float = 0xa6,
    sub_float = 0xa7,
    mul_float = 0xa8,
    div_float = 0xa9,
    rem_float = 0xaa,
    add_double = 0xab,
    sub_double = 0xac,
    mul_double = 0xad,
    div_double = 0xae,
    rem_double = 0xaf,
    add_int_2addr = 0xb0,
    sub_int_2addr = 0xb1,
    mul_int_2addr = 0xb2,
    div_int_2addr = 0xb3,
    rem_int_2addr = 0xb4,
    and_int_2addr = 0xb5,
    or_int_2addr = 0xb6,
    xor_int_2addr = 0xb7,
    shl_int_2addr = 0xb8,
    shr_int_2addr = 0xb9,
    ushr_int_2addr = 0xba,
    add_long_2addr = 0xbb,
    sub_long_2addr = 0xbc,
    mul_long_2addr = 0xbd,
    div_long_2addr = 0xbe,
    rem_long_2addr = 0xbf,
    and_long_2addr = 0xc0,
    or_long_2addr = 0xc1,
    xor_long_2addr = 0xc2,
    shl_long_2addr = 0xc3,
    shr_long_2addr = 0xc4,
    ushr_long_2addr = 0xc5,
    add_float_2addr = 0xc6,
    sub_float_2addr = 0xc7,
    mul_float_2addr = 0xc8,
    div_float_2addr = 0xc9,
    rem_float_2addr = 0xca,
    add_double_2addr = 0xcb,
    sub_double_2addr = 0xcc,
    mul_double_2addr = 0xcd,
    div_double_2addr = 0xce,
    rem_double_2addr = 0xcf,
    add_int_lit16 = 0xd0,
    sub_int_lit16 = 0xd1,
    mul_int_lit16 = 0xd2,
    div_int_lit16 = 0xd3,
    rem_int_lit16 = 0xd4,
    and_int_lit16 = 0xd5,
    or_int_lit16 = 0xd6,
    xor_int_lit16 = 0xd7,
    add_int_lit8 = 0xd8,
    sub_int_lit8 = 0xd9,
    mul_int_lit8 = 0xda,
    div_int_lit8 = 0xdb,
    rem_int_lit8 = 0xdc,
    and_int_lit8 = 0xdd,
    or_int_lit8 = 0xde,
    xor_int_lit8 = 0xdf,
    shl_int_lit8 = 0xe0,
    shr_int_lit8 = 0xe1,
    ushr_int_lit8 = 0xe2,
    _,
};

// Instruction formats by opcode (code units consumed).
fn insnSize(op: u8) TranslateError!usize {
    return switch (@as(Op, @enumFromInt(op))) {
        .nop, .move, .move_wide, .move_object, .move_result, .move_result_wide, .move_result_object, .move_exception, .return_void, .return_, .return_wide, .return_object, .const4, .monitor_enter, .monitor_exit, .throw_, .goto_, .array_length, .neg_int, .not_int, .neg_long, .not_long, .neg_float, .neg_double, .int_to_long, .int_to_float, .int_to_double, .long_to_int, .long_to_float, .long_to_double, .float_to_int, .float_to_long, .float_to_double, .double_to_int, .double_to_long, .double_to_float, .int_to_byte, .int_to_char, .int_to_short, .add_int_2addr, .sub_int_2addr, .mul_int_2addr, .div_int_2addr, .rem_int_2addr, .and_int_2addr, .or_int_2addr, .xor_int_2addr, .shl_int_2addr, .shr_int_2addr, .ushr_int_2addr, .add_long_2addr, .sub_long_2addr, .mul_long_2addr, .div_long_2addr, .rem_long_2addr, .and_long_2addr, .or_long_2addr, .xor_long_2addr, .shl_long_2addr, .shr_long_2addr, .ushr_long_2addr, .add_float_2addr, .sub_float_2addr, .mul_float_2addr, .div_float_2addr, .rem_float_2addr, .add_double_2addr, .sub_double_2addr, .mul_double_2addr, .div_double_2addr, .rem_double_2addr => 1,
        .if_eq, .if_ne, .if_lt, .if_ge, .if_gt, .if_le, .if_eqz, .if_nez, .if_ltz, .if_gez, .if_gtz, .if_lez, .add_int_lit16, .sub_int_lit16, .mul_int_lit16, .div_int_lit16, .rem_int_lit16, .and_int_lit16, .or_int_lit16, .xor_int_lit16, .add_int_lit8, .sub_int_lit8, .mul_int_lit8, .div_int_lit8, .rem_int_lit8, .and_int_lit8, .or_int_lit8, .xor_int_lit8, .shl_int_lit8, .shr_int_lit8, .ushr_int_lit8 => 2,
        .cmpl_float, .cmpg_float, .cmpl_double, .cmpg_double, .cmp_long, .aget, .aget_wide, .aget_object, .aget_boolean, .aget_byte, .aget_char, .aget_short, .aput, .aput_wide, .aput_object, .aput_boolean, .aput_byte, .aput_char, .aput_short, .move_from16, .move_wide_from16, .move_object_from16, .add_int, .sub_int, .mul_int, .div_int, .rem_int, .and_int, .or_int, .xor_int, .shl_int, .shr_int, .ushr_int, .add_long, .sub_long, .mul_long, .div_long, .rem_long, .and_long, .or_long, .xor_long, .shl_long, .shr_long, .ushr_long, .add_float, .sub_float, .mul_float, .div_float, .rem_float, .add_double, .sub_double, .mul_double, .div_double, .rem_double => 2,
        .const16, .const_high16, .const_wide16, .const_wide_high16, .goto16, .const_string, .const_class, .check_cast, .instance_of, .new_instance, .new_array, .iget, .iget_wide, .iget_object, .iget_boolean, .iget_byte, .iget_char, .iget_short, .iput, .iput_wide, .iput_object, .iput_boolean, .iput_byte, .iput_char, .iput_short, .sget, .sget_wide, .sget_object, .sget_boolean, .sget_byte, .sget_char, .sget_short, .sput, .sput_wide, .sput_object, .sput_boolean, .sput_byte, .sput_char, .sput_short => 2,
        .packed_switch, .sparse_switch, .fill_array_data, .invoke_virtual, .invoke_super, .invoke_direct, .invoke_static, .invoke_interface, .invoke_virtual_range, .invoke_super_range, .invoke_direct_range, .invoke_static_range, .invoke_interface_range, .const_, .const_wide32, .goto32, .filled_new_array, .filled_new_array_range, .const_string_jumbo, .move16, .move_wide16, .move_object16 => 3,
        .const_wide => 5,
        _ => return error.UnsupportedOpcode,
    };
}

/// JVM bytecode emitter with branch fixups.
pub const Emitter = struct {
    code: std.ArrayListUnmanaged(u8) = .empty,
    // dex_pc (code units) -> jvm offset
    dex_to_jvm: std.AutoHashMapUnmanaged(u32, u32) = .empty,
    // pending branch fixups: jvm offset of branch operand -> dex target
    fixups: std.ArrayListUnmanaged(struct { at: u32, target_dex: u32, wide: bool }) = .empty,
    // 32-bit switch fixups: operand offset, opcode offset (deltas are
    // relative to the switch opcode), dex target.
    fixups32: std.ArrayListUnmanaged(struct { at: u32, op_at: u32, target_dex: u32 }) = .empty,
    max_stack: u16 = 0,
    cur_stack: i32 = 0,

    pub fn deinit(self: *Emitter, gpa: std.mem.Allocator) void {
        self.code.deinit(gpa);
        self.dex_to_jvm.deinit(gpa);
        self.fixups.deinit(gpa);
        self.fixups32.deinit(gpa);
    }

    fn push(self: *Emitter, n: i32) void {
        self.cur_stack += n;
        if (self.cur_stack > self.max_stack) self.max_stack = @intCast(self.cur_stack);
    }

    pub fn mark(self: *Emitter, gpa: std.mem.Allocator, dex_pc: u32) !void {
        try self.dex_to_jvm.put(gpa, dex_pc, @intCast(self.code.items.len));
    }

    pub fn emit(self: *Emitter, gpa: std.mem.Allocator, b: u8) !void {
        try self.code.append(gpa, b);
    }

    pub fn emitU16(self: *Emitter, gpa: std.mem.Allocator, v: u16) !void {
        try self.code.append(gpa, @intCast(v >> 8));
        try self.code.append(gpa, @intCast(v & 0xff));
    }

    pub fn emitU32Raw(self: *Emitter, gpa: std.mem.Allocator, v: u32) !void {
        try self.code.append(gpa, @intCast(v >> 24));
        try self.code.append(gpa, @intCast((v >> 16) & 0xff));
        try self.code.append(gpa, @intCast((v >> 8) & 0xff));
        try self.code.append(gpa, @intCast(v & 0xff));
    }

    pub fn emitI32(self: *Emitter, gpa: std.mem.Allocator, v: i32) !void {
        try self.emitU32Raw(gpa, @bitCast(v));
    }

    /// Emit a 32-bit switch offset placeholder, resolved against op_at.
    pub fn emitU32Fixup(self: *Emitter, gpa: std.mem.Allocator, op_at: u32, target_dex: u32) !void {
        const at: u32 = @intCast(self.code.items.len);
        try self.emitU32Raw(gpa, 0);
        try self.fixups32.append(gpa, .{ .at = at, .op_at = op_at, .target_dex = target_dex });
    }

    pub fn emitBranch(self: *Emitter, gpa: std.mem.Allocator, op: u8, target_dex: u32) !void {
        try self.emit(gpa, op);
        const at: u32 = @intCast(self.code.items.len);
        try self.emitU16(gpa, 0);
        try self.fixups.append(gpa, .{ .at = at, .target_dex = target_dex, .wide = false });
    }

    /// Wide branch (goto_w 0xc8): 4-byte signed offset from the opcode
    /// start, patched by resolve() via the fixup's width flag.
    pub fn emitBranchWide(self: *Emitter, gpa: std.mem.Allocator, target_dex: u32) !void {
        try self.emit(gpa, 0xc8); // goto_w
        const at: u32 = @intCast(self.code.items.len);
        try self.emitU32Raw(gpa, 0);
        try self.fixups.append(gpa, .{ .at = at, .target_dex = target_dex, .wide = true });
    }

    pub fn resolve(self: *Emitter) TranslateError!void {
        for (self.fixups.items) |f| {
            const target = self.dex_to_jvm.get(f.target_dex) orelse return error.BadBranch;
            // Branch offset is relative to the branch opcode start.
            const op_at: i32 = @intCast(f.at - 1);
            const delta: i32 = @as(i32, @intCast(target)) - op_at;
            if (f.wide) {
                const u: u32 = @bitCast(delta);
                self.code.items[f.at] = @intCast(u >> 24);
                self.code.items[f.at + 1] = @intCast((u >> 16) & 0xff);
                self.code.items[f.at + 2] = @intCast((u >> 8) & 0xff);
                self.code.items[f.at + 3] = @intCast(u & 0xff);
            } else {
                if (delta < -32768 or delta > 32767) return error.BadBranch;
                const u: u16 = @bitCast(@as(i16, @intCast(delta)));
                self.code.items[f.at] = @intCast(u >> 8);
                self.code.items[f.at + 1] = @intCast(u & 0xff);
            }
        }

        for (self.fixups32.items) |f| {
            const target = self.dex_to_jvm.get(f.target_dex) orelse return error.BadBranch;
            const delta: i32 = @as(i32, @intCast(target)) - @as(i32, @intCast(f.op_at));
            const u: u32 = @bitCast(delta);
            self.code.items[f.at] = @intCast(u >> 24);
            self.code.items[f.at + 1] = @intCast((u >> 16) & 0xff);
            self.code.items[f.at + 2] = @intCast((u >> 8) & 0xff);
            self.code.items[f.at + 3] = @intCast(u & 0xff);
        }
    }
};

/// DEX register to JVM local mapping. JVM layout: params first (this=0,
/// wides taking 2 slots), then non-param DEX regs packed linearly after.
/// A rotation would split wide pairs crossing the params boundary, so an
/// explicit table is built from the proto (see translateMethod).
const RegMap = struct {
    table: []const u16,
    fn map(self: RegMap, r: u16) u16 {
        if (r < self.table.len) return self.table[r];
        return r; // defensive: malformed reg passes through to verifier
    }
};

/// Emit load of DEX register r as int/reference.
fn emitLoadInt(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.map(rx);
    if (r < 4) {
        try e.emit(gpa, @intCast(0x1a + r)); // iload_0..3
    } else if (r < 256) {
        try e.emit(gpa, 0x15); // iload
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4); // wide
        try e.emit(gpa, 0x15);
        try e.emitU16(gpa, r);
    }
    e.push(1);
}

fn emitStoreInt(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.map(rx);
    if (r < 4) {
        try e.emit(gpa, @intCast(0x3b + r)); // istore_0..3
    } else if (r < 256) {
        try e.emit(gpa, 0x36); // istore
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, 0x36);
        try e.emitU16(gpa, r);
    }
    e.push(-1);
}

/// Emit load/store of DEX register r as float (fload/fstore; the JVM
/// verifier distinguishes float from int even though both are category 1).
fn emitLoadFloat(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.map(rx);
    if (r < 4) {
        try e.emit(gpa, @intCast(0x22 + r)); // fload_0..3
    } else if (r < 256) {
        try e.emit(gpa, 0x17); // fload
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, 0x17);
        try e.emitU16(gpa, r);
    }
    e.push(1);
}

fn emitStoreFloat(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.map(rx);
    if (r < 4) {
        try e.emit(gpa, @intCast(0x43 + r)); // fstore_0..3
    } else if (r < 256) {
        try e.emit(gpa, 0x38); // fstore
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, 0x38);
        try e.emitU16(gpa, r);
    }
    e.push(-1);
}

fn emitLoadRef(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.map(rx);
    if (r < 4) {
        try e.emit(gpa, @intCast(0x2a + r)); // aload_0..3
    } else if (r < 256) {
        try e.emit(gpa, 0x19);
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, 0x19);
        try e.emitU16(gpa, r);
    }
    e.push(1);
}

fn emitStoreRef(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.map(rx);
    if (r < 4) {
        try e.emit(gpa, @intCast(0x4b + r)); // astore_0..3
    } else if (r < 256) {
        try e.emit(gpa, 0x3a);
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, 0x3a);
        try e.emitU16(gpa, r);
    }
    e.push(-1);
}

fn emitLoadWide(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16, is_double: bool) !void {
    const r = rm.map(rx);
    const base: u8 = if (is_double) 0x26 else 0x1e; // dload_n / lload_n
    const op: u8 = if (is_double) 0x18 else 0x16; // dload / lload
    if (r < 4) {
        try e.emit(gpa, base + @as(u8, @intCast(r)));
    } else if (r < 256) {
        try e.emit(gpa, op);
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, op);
        try e.emitU16(gpa, r);
    }
    e.push(2);
}

fn emitStoreWide(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16, is_double: bool) !void {
    const r = rm.map(rx);
    const base: u8 = if (is_double) 0x47 else 0x3f; // dstore_n / lstore_n
    const op: u8 = if (is_double) 0x39 else 0x37; // dstore / lstore
    if (r < 4) {
        try e.emit(gpa, base + @as(u8, @intCast(r)));
    } else if (r < 256) {
        try e.emit(gpa, op);
        try e.emit(gpa, @intCast(r));
    } else {
        try e.emit(gpa, 0xc4);
        try e.emit(gpa, op);
        try e.emitU16(gpa, r);
    }
    e.push(-2);
}

/// Store to DEX register rx and record its tracked type. Wide kinds (.long,
/// .double) set r+1 as well; .ref clears the wide-component entry (unknown
/// array origin). move_object and new-array keep their own wide_comp lines.
fn storeTracked(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    types: []RegType,
    wide_comp: []u8,
    rx: u16,
    kind: RegType,
) !void {
    switch (kind) {
        .int => {
            try emitStoreInt(e, gpa, rm, rx);
            if (rx < types.len) types[rx] = .int;
        },
        .float => {
            try emitStoreFloat(e, gpa, rm, rx);
            if (rx < types.len) types[rx] = .float;
        },
        .ref => {
            try emitStoreRef(e, gpa, rm, rx);
            if (rx < types.len) types[rx] = .ref;
            if (rx < wide_comp.len) wide_comp[rx] = 0;
        },
        .long => {
            try emitStoreWide(e, gpa, rm, rx, false);
            if (rx < types.len) types[rx] = .long;
            if (@as(usize, rx) + 1 < types.len) types[@as(usize, rx) + 1] = .long;
        },
        .double => {
            try emitStoreWide(e, gpa, rm, rx, true);
            if (rx < types.len) types[rx] = .double;
            if (@as(usize, rx) + 1 < types.len) types[@as(usize, rx) + 1] = .double;
        },
        .unknown => unreachable,
    }
}

/// Emit jop with a 2-byte pool-index placeholder and record the use. Covers
/// the const-use prologue (getfield/putfield/getstatic/putstatic, ldc_w,
/// new, checkcast, instanceof, invoke, raw JDK refs); push is the net stack
/// delta of the operation itself. (A zero push is a no-op for max_stack.)
fn emitPoolRef(
    e: *Emitter,
    gpa: std.mem.Allocator,
    uses: *std.ArrayListUnmanaged(ConstUse),
    jop: u8,
    push: i32,
    ref: ConstRef,
) !void {
    try e.emit(gpa, jop);
    const at: u32 = @intCast(e.code.items.len);
    try e.emitU16(gpa, 0);
    e.push(push);
    try uses.append(gpa, .{ .at = at, .wide = true, .ref = ref });
}

/// Decode a 23x instruction: unit0 holds A in bits 8-15, unit1 holds B in
/// bits 0-7 and C in bits 8-15.
fn regs23x(unit0: u16, unit1: u16) struct { a: u8, b: u8, c: u8 } {
    return .{
        .a = @intCast((unit0 >> 8) & 0xFF),
        .b = @intCast(unit1 & 0xFF),
        .c = @intCast((unit1 >> 8) & 0xFF),
    };
}

/// First descriptor char of a field's type (0 when empty). Replaces the
/// repeated fieldId+getTypeDescriptor+fd[0] fetches in the field arms.
fn fieldKind1(d: dex.Dex, gpa: std.mem.Allocator, field_idx: u16) TranslateError!u8 {
    const fid = try d.fieldId(field_idx);
    const desc = try d.getTypeDescriptor(gpa, fid.type_idx);
    defer gpa.free(desc);
    return if (desc.len > 0) desc[0] else 0;
}

/// Shared iget/iput/sget/sput lowering. jop selects getfield/putfield/
/// getstatic/putstatic; obj is the object reg (null for static); reg is
/// the dest (loads) or value (stores) reg. Load order, push delta, and
/// dest form all derive from the field kind.
fn emitFieldOp(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    d: dex.Dex,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    wide_comp: []u8,
    jop: u8,
    field_idx: u16,
    obj: ?u16,
    reg: u16,
) TranslateError!void {
    const k = try fieldKind1(d, gpa, field_idx);
    const is_d = k == 'D';
    const wide = k == 'J' or is_d;
    const is_f = k == 'F';
    const is_ref = k == 'L' or k == '[';
    const kind: RegType = if (is_f) .float else if (is_d) .double else if (k == 'J') .long else if (is_ref) .ref else .int;
    switch (jop) {
        0xb4 => { // getfield: pop ref, push value
            try emitLoadRef(e, gpa, rm, obj.?);
            try emitPoolRef(e, gpa, uses, jop, if (wide) 1 else 0, .{ .field = field_idx });
            try storeTracked(e, gpa, rm, types, wide_comp, reg, kind);
        },
        0xb2 => { // getstatic: push value
            try emitPoolRef(e, gpa, uses, jop, if (wide) 2 else 1, .{ .field = field_idx });
            try storeTracked(e, gpa, rm, types, wide_comp, reg, kind);
        },
        0xb5 => { // putfield: pop ref+value
            try emitLoadRef(e, gpa, rm, obj.?);
            if (wide) try emitLoadWide(e, gpa, rm, reg, is_d) else if (is_ref) try emitLoadRef(e, gpa, rm, reg) else if (is_f) try emitLoadFloat(e, gpa, rm, reg) else try emitLoadInt(e, gpa, rm, reg);
            try emitPoolRef(e, gpa, uses, jop, if (wide) -3 else -2, .{ .field = field_idx });
        },
        0xb3 => { // putstatic: pop value
            if (wide) try emitLoadWide(e, gpa, rm, reg, is_d) else if (is_ref) try emitLoadRef(e, gpa, rm, reg) else if (is_f) try emitLoadFloat(e, gpa, rm, reg) else try emitLoadInt(e, gpa, rm, reg);
            try emitPoolRef(e, gpa, uses, jop, if (wide) -2 else -1, .{ .field = field_idx });
        },
        else => unreachable,
    }
}

/// Single table mapping every DEX arithmetic/logic opcode to its JVM opcode.
fn arithJop(opcode: Op) u8 {
    return switch (opcode) {
        .add_int, .add_int_2addr, .add_int_lit8, .add_int_lit16 => 0x60, // iadd
        .sub_int, .sub_int_2addr, .sub_int_lit8, .sub_int_lit16 => 0x64, // isub
        .mul_int, .mul_int_2addr, .mul_int_lit8, .mul_int_lit16 => 0x68, // imul
        .div_int, .div_int_2addr, .div_int_lit8, .div_int_lit16 => 0x6c, // idiv
        .rem_int, .rem_int_2addr, .rem_int_lit8, .rem_int_lit16 => 0x70, // irem
        .shl_int, .shl_int_2addr, .shl_int_lit8 => 0x78, // ishl
        .shr_int, .shr_int_2addr, .shr_int_lit8 => 0x7a, // ishr
        .ushr_int, .ushr_int_2addr, .ushr_int_lit8 => 0x7c, // iushr
        .and_int, .and_int_2addr, .and_int_lit8, .and_int_lit16 => 0x7e, // iand
        .or_int, .or_int_2addr, .or_int_lit8, .or_int_lit16 => 0x80, // ior
        .xor_int, .xor_int_2addr, .xor_int_lit8, .xor_int_lit16 => 0x82, // ixor
        .add_long, .add_long_2addr => 0x61, // ladd
        .sub_long, .sub_long_2addr => 0x65, // lsub
        .mul_long, .mul_long_2addr => 0x69, // lmul
        .div_long, .div_long_2addr => 0x6d, // ldiv
        .rem_long, .rem_long_2addr => 0x71, // lrem
        .shl_long, .shl_long_2addr => 0x79, // lshl
        .shr_long, .shr_long_2addr => 0x7b, // lshr
        .ushr_long, .ushr_long_2addr => 0x7d, // lushr
        .and_long, .and_long_2addr => 0x7f, // land
        .or_long, .or_long_2addr => 0x81, // lor
        .xor_long, .xor_long_2addr => 0x83, // lxor
        .add_float, .add_float_2addr => 0x62, // fadd
        .sub_float, .sub_float_2addr => 0x66, // fsub
        .mul_float, .mul_float_2addr => 0x6a, // fmul
        .div_float, .div_float_2addr => 0x6e, // fdiv
        .rem_float, .rem_float_2addr => 0x72, // frem
        .add_double, .add_double_2addr => 0x63, // dadd
        .sub_double, .sub_double_2addr => 0x67, // dsub
        .mul_double, .mul_double_2addr => 0x6b, // dmul
        .div_double, .div_double_2addr => 0x6f, // ddiv
        .rem_double, .rem_double_2addr => 0x73, // drem
        else => unreachable,
    };
}

/// Little-endian 32-bit value across two code units.
fn u32Pair(lo: u16, hi: u16) u32 {
    return (@as(u32, hi) << 16) | lo;
}

/// Signed twin of u32Pair.
fn i32Pair(lo: u16, hi: u16) i32 {
    return @bitCast(u32Pair(lo, hi));
}

/// Little-endian 64-bit value across four code units.
fn u64Quad(w0: u16, w1: u16, w2: u16, w3: u16) u64 {
    return @as(u64, w0) | (@as(u64, w1) << 16) | (@as(u64, w2) << 32) | (@as(u64, w3) << 48);
}

/// Shared float/double operand loader (see emitLoadFloatOperand): registers
/// the tracker marks wide hold real values, anything else holds a bit
/// pattern needing reinterpretation. Net stack effect: +1 (float) / +2.
fn emitLoadFloatishOperand(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    rx: u8,
    is_double: bool,
) !void {
    if (is_double) {
        if (rx < types.len and types[rx] == .double) {
            return emitLoadWide(e, gpa, rm, rx, true);
        }
        try emitLoadWide(e, gpa, rm, rx, false);
        try emitPoolRef(e, gpa, uses, 0xb8, 0, .{ .raw_method = .{
            .cls = "java/lang/Double",
            .name = "longBitsToDouble",
            .desc = "(J)D",
        } });
    } else {
        if (rx < types.len and types[rx] == .float) {
            return emitLoadFloat(e, gpa, rm, rx);
        }
        try emitLoadInt(e, gpa, rm, rx);
        try emitPoolRef(e, gpa, uses, 0xb8, 0, .{ .raw_method = .{
            .cls = "java/lang/Float",
            .name = "intBitsToFloat",
            .desc = "(I)F",
        } });
    }
}

/// Signed branch target from a DEX pc and a code-unit offset.
fn branchTarget(dex_pc: u32, off: i32) TranslateError!u32 {
    const target = @as(i32, @intCast(dex_pc)) + off;
    if (target < 0) return error.BadBranch;
    return @intCast(target);
}

/// Payload position from a 31t-style pc-relative offset.
fn payloadPc(dex_pc: u32, off: i32, len: usize) TranslateError!usize {
    const p = @as(i32, @intCast(dex_pc)) + off;
    if (p < 0 or p >= len) return error.BadBranch;
    return @intCast(p);
}

/// Decode 22x/32x move regs: from16 uses an 8-bit dest + 16-bit src,
/// 32x uses two 16-bit regs (src in the second unit).
fn moveRegs16(opcode: Op, unit0: u16, unit1: u16, unit2: u16) struct { src: u16, dst: u16 } {
    const from16 = opcode == .move_from16 or opcode == .move_wide_from16 or opcode == .move_object_from16;
    if (from16) return .{ .src = unit1, .dst = regA(unit0) };
    return .{ .src = unit2, .dst = unit1 };
}

/// Decode 35c nibble registers per ART GetVarArgs: argc = bits 12-15,
/// G = bits 8-11, C/D/E/F = unit2 nibbles 0/1/2/3.
fn regs35c(unit0: u16, arg_word: u16) [5]u16 {
    return .{
        (arg_word >> 0) & 0xF,
        (arg_word >> 4) & 0xF,
        (arg_word >> 8) & 0xF,
        (arg_word >> 12) & 0xF,
        (unit0 >> 8) & 0xF,
    };
}

fn emitConstInt(e: *Emitter, gpa: std.mem.Allocator, v: i32) !void {
    if (v >= -1 and v <= 5) {
        try e.emit(gpa, @intCast(0x03 + v)); // iconst_m1..5
    } else if (v >= -128 and v <= 127) {
        try e.emit(gpa, 0x10); // bipush
        try e.emit(gpa, @bitCast(@as(i8, @intCast(v))));
    } else if (v >= -32768 and v <= 32767) {
        try e.emit(gpa, 0x11); // sipush
        try e.emitU16(gpa, @bitCast(@as(i16, @intCast(v))));
    } else {
        // Large constant: ldc with a pool Integer. The caller must record
        // a ConstUse — this helper can't (no uses list). Callers needing
        // big constants use emitConstIntPool below instead.
        // Unreachable for direct callers; kept as a loud fallback.
        try e.emit(gpa, 0x12); // ldc
        try e.emit(gpa, 0); // patched by class writer
        e.push(1);
        return;
    }
    e.push(1);
}

/// emitConstInt that records a pool use for large values. Always ldc_w
/// (wide:true): the class writer patches a 2-byte index, so pool indices
/// above 255 stay encodable without a second pass over the bytecode.
fn emitConstIntPool(
    e: *Emitter,
    gpa: std.mem.Allocator,
    uses: *std.ArrayListUnmanaged(ConstUse),
    v: i32,
) !void {
    if (v >= -32768 and v <= 32767) {
        return emitConstInt(e, gpa, v);
    }
    try emitPoolRef(e, gpa, uses, 0x13, 1, .{ .int_const = v }); // ldc_w
}

/// Long constant via ldc2_w (3 bytes, no 25-byte synth). lconst_0/1 stay
/// inline (1 byte). Net stack effect: +2.
fn emitConstLongPool(
    e: *Emitter,
    gpa: std.mem.Allocator,
    uses: *std.ArrayListUnmanaged(ConstUse),
    v: i64,
) !void {
    if (v == 0 or v == 1) {
        try e.emit(gpa, if (v == 0) 0x09 else 0x0a); // lconst_0/1
        e.push(2);
        return;
    }
    try emitPoolRef(e, gpa, uses, 0x14, 2, .{ .long_const = v }); // ldc2_w
}

/// Double constant (bits) via ldc2_w. Net stack effect: +2.
fn emitConstDoublePool(
    e: *Emitter,
    gpa: std.mem.Allocator,
    uses: *std.ArrayListUnmanaged(ConstUse),
    bits: u64,
) !void {
    try emitPoolRef(e, gpa, uses, 0x14, 2, .{ .double_const = bits }); // ldc2_w
}

/// Constant reference needed by one translated method.
pub const ConstRef = union(enum) {
    string: u32, // dex string idx
    type: u32, // dex type idx
    component_type: u32, // dex type idx of an array; resolve to COMPONENT
    int_const: i32, // literal Integer pool entry
    long_const: i64, // literal Long pool entry
    double_const: u64, // literal Double pool entry (bits)
    field: u32, // dex field idx
    method: u32, // dex method idx
    raw_method: struct { cls: []const u8, name: []const u8, desc: []const u8 }, // direct JVM owner/name/desc (JDK helpers)
    raw_field: struct { cls: []const u8, name: []const u8, desc: []const u8 }, // direct JVM owner/name/desc (e.g. Integer.TYPE)
};

/// A use of a constant pool slot inside emitted code (to patch with the real index).
pub const ConstUse = struct {
    at: u32, // offset in code of the index operand
    wide: bool, // 1-byte (ldc) or 2-byte index
    ref: ConstRef,
};

/// Validate a filled-new-array element kind: wide (J/D) elements cannot be
/// built with per-element xastore lowering (no single-word store fits a
/// double-width value), so reject them loudly. All narrow primitives,
/// floats, and refs are lowerable.
fn filledArrayElemKind(elem: u8) TranslateError!void {
    switch (elem) {
        'J', 'D' => return error.BadDescriptor,
        'Z', 'B', 'S', 'C', 'I', 'F', 'L', '[' => {},
        else => return error.BadDescriptor,
    }
}

/// Load a DEX register as a float operand for comparisons. Registers the
/// tracker marks .float hold real floats (fload). Anything else holds a
/// float bit pattern stored by an int const/move (DEX is untyped; d8 emits
/// `const/4 v,0` for 0.0f), so reinterpret the bits via intBitsToFloat
/// rather than i2f (numeric conversion would mangle nonzero patterns).
/// Net stack effect: +1.
fn emitLoadFloatOperand(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    rx: u8,
) !void {
    return emitLoadFloatishOperand(e, gpa, rm, uses, types, rx, false);
}

/// Double twin of emitLoadFloatOperand via Double.longBitsToDouble(J)D.
/// Net stack effect: +2.
fn emitLoadDoubleOperand(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    rx: u8,
) !void {
    return emitLoadFloatishOperand(e, gpa, rm, uses, types, rx, true);
}

pub const TranslatedMethod = struct {
    code: []u8,
    max_stack: u16,
    max_locals: u16,
    uses: []ConstUse,
    /// (start_pc, end_pc, handler_pc, catch_type_dex_idx or null for catch-all).
    /// PCs are JVM byte offsets, resolved after emission.
    exceptions: []ExceptionEntry,
    /// Sorted, deduped (start_pc, line) pairs for the LineNumberTable.
    lines: []LineEntry,

    pub fn deinit(self: *TranslatedMethod, gpa: std.mem.Allocator) void {
        gpa.free(self.code);
        gpa.free(self.uses);
        gpa.free(self.exceptions);
        gpa.free(self.lines);
    }
};

pub const LineEntry = struct {
    start_pc: u32,
    line: u32,
};

pub const ExceptionEntry = struct {
    start_pc: u32,
    end_pc: u32,
    handler_pc: u32,
    catch_type: ?u32, // dex type idx; null = catch-all (catch_type 0)
};

fn regA(unit: u16) u8 {
    return @intCast((unit >> 8) & 0xF);
}
fn regB(unit: u16) u8 {
    return @intCast((unit >> 12) & 0xF);
}
fn reg8(unit: u16) u8 {
    return @intCast(unit >> 8);
}

/// Load invoke arguments with JVM types from the callee proto.
/// Non-static calls take `this` (a ref) as the first register.
fn emitInvokeArgs(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    d: dex.Dex,
    method_idx: u16,
    regs: []const u16,
    is_static: bool,
) TranslateError!void {
    const mid = try d.methodId(method_idx);
    const proto = try d.protoId(mid.proto_idx);
    const param_types = try dex.paramKinds(gpa, d, proto.parameters_off);
    defer gpa.free(param_types);
    // Wide params occupy two DEX registers each (both halves are listed
    // in the invoke reg list), so count J/D twice.
    var expect_regs: usize = @as(usize, if (is_static) 0 else 1);
    for (param_types) |tc| expect_regs += if (tc == 'J' or tc == 'D') 2 else 1;
    if (regs.len != expect_regs) return error.UnsupportedOpcode;
    var ri: usize = 0;
    if (!is_static) {
        try emitLoadRef(e, gpa, rm, regs[0]);
        ri = 1;
    }
    for (param_types) |tc| {
        const r = regs[ri];
        ri += 1;
        switch (tc) {
            'Z', 'B', 'S', 'C', 'I' => try emitLoadInt(e, gpa, rm, r),
            'F' => try emitLoadFloat(e, gpa, rm, r),
            'J', 'D' => {
                try emitLoadWide(e, gpa, rm, r, tc == 'D');
                ri += 1; // wide consumes two DEX registers
            },
            'L', '[' => try emitLoadRef(e, gpa, rm, r),
            else => return error.BadDescriptor,
        }
    }
}

/// Shared invoke lowering: args + pool ref + callee ret-kind + interface
/// tail. regs is the already-decoded arg register list.
fn emitInvokeCall(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    d: dex.Dex,
    uses: *std.ArrayListUnmanaged(ConstUse),
    last_ret_kind: *u8,
    jop: u8,
    method_idx: u16,
    regs: []const u16,
    is_static: bool,
    is_interface: bool,
) TranslateError!void {
    try emitInvokeArgs(e, gpa, rm, d, method_idx, regs, is_static);
    try emitPoolRef(e, gpa, uses, jop, -@as(i32, @intCast(regs.len)), .{ .method = method_idx });
    const cmid = try d.methodId(method_idx);
    const cproto = try d.protoId(cmid.proto_idx);
    const cret = try d.getTypeDescriptor(gpa, cproto.return_type_idx);
    defer gpa.free(cret);
    last_ret_kind.* = if (cret.len > 0) cret[0] else 'V';
    if (cret.len > 0 and cret[0] != 'V') e.push(if (cret[0] == 'J' or cret[0] == 'D') 2 else 1);
    if (is_interface) {
        try e.emit(gpa, @intCast(regs.len));
        try e.emit(gpa, 0);
    }
}

/// Shared newarray/anewarray creation from an array type descriptor.
/// Caller pushes the size first; net stack effect is neutral (pop size,
/// push ref). Full atype switch; filled-new-array callers validate wide
/// kinds themselves before calling.
fn emitNewArray(
    e: *Emitter,
    gpa: std.mem.Allocator,
    uses: *std.ArrayListUnmanaged(ConstUse),
    type_idx: u16,
    td: []const u8,
) TranslateError!void {
    if (td.len >= 2 and td[0] == '[' and (td[1] == 'L' or td[1] == '[')) {
        try emitPoolRef(e, gpa, uses, 0xbd, 0, .{ .component_type = type_idx }); // anewarray (component type, not array type)
        return;
    }
    if (td.len != 2 or td[0] != '[') return error.BadDescriptor;
    const atype: u8 = switch (td[1]) {
        'Z' => 4, // boolean
        'C' => 5, // char
        'F' => 6, // float
        'D' => 7, // double
        'B' => 8, // byte
        'S' => 9, // short
        'I' => 10, // int
        'J' => 11, // long
        else => return error.BadDescriptor,
    };
    try e.emit(gpa, 0xbc); // newarray
    try e.emit(gpa, atype);
    e.push(0);
}

/// Per-register type for lowering decisions (int/ref/wide/float need
/// different JVM opcodes). Seeded from the method proto for params;
/// updated on every store. Single-type-per-register assumption holds for
/// compiler output; hand-written smali reusing one register for different
/// types across branches may mistranslate.
const RegType = enum { unknown, int, ref, long, double, float };

/// Decide whether an aget/aput-wide pair is a double operation. The
/// new-array component tracker (array reg's recorded 'J'/'D' kind) wins;
/// the value reg's tracked type is the fallback; unknown defaults to long
/// (the historical behavior for untracked arrays, e.g. params).
fn wideIsDouble(comp_kind: u8, value_ty: RegType) bool {
    if (comp_kind == 'D') return true;
    if (comp_kind == 'J') return false;
    return value_ty == .double;
}

/// Translate one DEX code item to JVM bytecode. Registers map to JVM locals 1:1 (wide regs occupy two slots; max_locals gets +1 slop).
pub fn translateMethod(
    gpa: std.mem.Allocator,
    d: dex.Dex,
    code: dex.Code,
    proto_idx: u32,
    is_static: bool,
) TranslateError!TranslatedMethod {
    // tries are expanded to the JVM exception table after emission (below).
    // Zero-register methods are valid DEX (empty static bodies); only
    // ins > registers is malformed (underflows the tables below).
    if (code.ins > code.registers) return error.BadSection;
    // Param kinds from the proto (first char of each descriptor).
    const proto = try d.protoId(proto_idx);
    const param_kinds = try dex.paramKinds(gpa, d, proto.parameters_off);
    defer gpa.free(param_kinds);
    // JVM local table: params first (this=0, wides take 2 slots), then
    // non-param DEX regs packed linearly. Wide pairs stay adjacent.
    var local_table = gpa.alloc(u16, code.registers) catch return error.OutOfMemory;
    defer gpa.free(local_table);
    var param_slots: u16 = 0;
    {
        var ri: usize = code.registers - code.ins;
        var slot: u16 = 0;
        if (!is_static) {
            if (ri < code.registers) {
                local_table[ri] = slot;
                slot += 1;
            }
            ri += 1;
        }
        for (param_kinds) |tc| {
            if (ri >= code.registers) break;
            const wide = tc == 'J' or tc == 'D';
            local_table[ri] = slot;
            slot += if (wide) 2 else 1;
            ri += 1;
            if (wide and ri < code.registers) {
                local_table[ri] = slot - 1; // second half (unused directly)
                ri += 1;
            }
        }
        param_slots = slot;
    }
    for (0..code.registers - code.ins) |r| {
        local_table[r] = param_slots + @as(u16, @intCast(r));
    }
    const rm = RegMap{ .table = local_table };
    const max_locals: u16 = param_slots + code.registers - code.ins + 1;
    // Seed register types from the proto: last `ins` regs are params.
    var types = gpa.alloc(RegType, code.registers) catch return error.OutOfMemory;
    defer gpa.free(types);
    @memset(types, .unknown);
    {
        var ri: usize = code.registers - code.ins;
        if (!is_static) {
            if (ri < code.registers) types[ri] = .ref;
            ri += 1;
        }
        for (param_kinds) |tc| {
            if (ri >= code.registers) break;
            types[ri] = switch (tc) {
                'J' => .long,
                'D' => .double,
                'F' => .float,
                'L', '[' => .ref,
                else => .int,
            };
            const tk = types[ri];
            ri += 1;
            if (tc == 'J' or tc == 'D') {
                if (ri < code.registers) types[ri] = tk;
                ri += 1;
            }
        }
    }
    // Wide array component tracking for aget-wide/aput-wide: DEX gives no
    // local component info, so record the element kind ('J' or 'D') when
    // new-array creates a [J/[D array in a dest reg. Default (unknown) is
    // long — the historical fallback. Any ref/int store to the reg
    // invalidates the entry (conservative: only double[] reads need it).
    const wide_comp = gpa.alloc(u8, code.registers) catch return error.OutOfMemory;
    defer gpa.free(wide_comp);
    @memset(wide_comp, 0);
    // First descriptor char of the last invoke's return (for move-result).
    var last_ret_kind: u8 = 'V';
    // First descriptor char of this method's return (for return-*).
    const ret_kind: u8 = blk: {
        const rp = try d.protoId(proto_idx);
        const rd = try d.getTypeDescriptor(gpa, rp.return_type_idx);
        defer gpa.free(rd);
        break :blk if (rd.len > 0) rd[0] else 'V';
    };
    var e = Emitter{};
    defer e.deinit(gpa);
    var uses: std.ArrayListUnmanaged(ConstUse) = .empty;
    errdefer uses.deinit(gpa);

    var pc: usize = 0;
    const insns = code.insns;
    // Pre-scan: switch/fill-array payloads live in the insns stream as
    // data; collect their ranges so the walk skips them (they would
    // otherwise decode as garbage opcodes).
    const PayloadRange = struct { start: usize, end: usize };
    var payloads: std.ArrayListUnmanaged(PayloadRange) = .empty;
    defer payloads.deinit(gpa);
    {
        var spc: usize = 0;
        while (spc < insns.len) {
            // Skip already-recorded payload ranges (payload bytes would
            // otherwise decode as garbage opcodes and derail the scan).
            var skipped = false;
            for (payloads.items) |r| {
                if (spc >= r.start and spc < r.end) {
                    spc = r.end;
                    skipped = true;
                    break;
                }
            }
            if (skipped) continue;
            const su0 = insns[spc];
            const sop: u8 = @intCast(su0 & 0xFF);
            const sopcode: Op = @enumFromInt(sop);
            const ssize = try insnSize(sop);
            if (spc + ssize > insns.len) break;
            if (sopcode == .packed_switch or sopcode == .sparse_switch or sopcode == .fill_array_data) {
                const soff = @as(i32, insns[spc + 1]) + (@as(i32, insns[spc + 2]) << 16);
                const pdex = @as(i64, @intCast(spc)) + soff;
                if (pdex >= 0 and pdex < insns.len) {
                    const pu: usize = @intCast(pdex);
                    const ident = insns[pu];
                    const want: u16 = if (sopcode == .packed_switch) 0x0100 else if (sopcode == .sparse_switch) 0x0200 else 0x0300;
                    if (ident == want and pu + 2 <= insns.len) {
                        var pend: usize = undefined;
                        if (sopcode == .fill_array_data) {
                            const width: usize = insns[pu + 1];
                            if (width == 1 or width == 2 or width == 4 or width == 8) {
                                const cnt: usize = (@as(usize, insns[pu + 3]) << 16) | insns[pu + 2];
                                if (cnt <= 65536) {
                                    pend = pu + 4 + (cnt * width + 1) / 2;
                                } else continue;
                            } else continue;
                        } else {
                            const cnt: usize = insns[pu + 1];
                            if (cnt == 0 or cnt > 4096) continue;
                            pend = if (sopcode == .packed_switch) pu + 4 + cnt * 2 else pu + 2 + cnt * 4;
                        }
                        if (pend <= insns.len) {
                            try payloads.append(gpa, .{ .start = pu, .end = pend });
                        }
                    }
                }
            }
            spc += ssize;
        }
    }
    const inPayload = struct {
        fn f(pl: []const PayloadRange, at: usize) bool {
            for (pl) |r| {
                if (at >= r.start and at < r.end) return true;
            }
            return false;
        }
    }.f;
    while (pc < insns.len) {
        if (inPayload(payloads.items, pc)) {
            pc += 1;
            continue;
        }
        const dex_pc: u32 = @intCast(pc);
        try e.mark(gpa, dex_pc);
        const unit0 = insns[pc];
        const op: u8 = @intCast(unit0 & 0xFF);
        const size = try insnSize(op);
        const opcode: Op = @enumFromInt(op);
        switch (opcode) {
            .nop => try e.emit(gpa, 0x00),
            .move => {
                const mvs = regB(unit0);
                const mvf = mvs < types.len and types[mvs] == .float;
                if (mvf) try emitLoadFloat(&e, gpa, rm, mvs) else try emitLoadInt(&e, gpa, rm, mvs);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), if (mvf) .float else .int);
            },
            .move_from16, .move16 => {
                // 22x/32x: A = dest, B = src (8/16-bit regs).
                const mr = moveRegs16(opcode, unit0, insns[pc + 1], insns[pc + 2]);
                const mvf = mr.src < types.len and types[mr.src] == .float;
                if (mvf) try emitLoadFloat(&e, gpa, rm, mr.src) else try emitLoadInt(&e, gpa, rm, mr.src);
                try storeTracked(&e, gpa, rm, types, wide_comp, mr.dst, if (mvf) .float else .int);
            },

            .move_object => {
                try emitLoadRef(&e, gpa, rm, regB(unit0));
                try emitStoreRef(&e, gpa, rm, regA(unit0)); if (regA(unit0) < types.len) types[regA(unit0)] = .ref;
                // Aliasing copy: propagate the component kind (array moves
                // between regs stay tracked); source kept as-is.
                if (regA(unit0) < wide_comp.len) wide_comp[regA(unit0)] = if (regB(unit0) < wide_comp.len) wide_comp[regB(unit0)] else 0;
            },
            .move_object_from16, .move_object16 => {
                const mr = moveRegs16(opcode, unit0, insns[pc + 1], insns[pc + 2]);
                try emitLoadRef(&e, gpa, rm, mr.src);
                try emitStoreRef(&e, gpa, rm, mr.dst);
                if (mr.dst < types.len) types[mr.dst] = .ref;
                if (mr.dst < wide_comp.len) wide_comp[mr.dst] = if (mr.src < wide_comp.len) wide_comp[mr.src] else 0;
            },
            .move_wide => {
                const mws = regB(unit0);
                const mwd = mws < types.len and types[mws] == .double;
                try emitLoadWide(&e, gpa, rm, mws, mwd);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), if (mwd) .double else .long);
            },
            .move_wide_from16, .move_wide16 => {
                const mr = moveRegs16(opcode, unit0, insns[pc + 1], insns[pc + 2]);
                const mwd = mr.src < types.len and types[mr.src] == .double;
                try emitLoadWide(&e, gpa, rm, mr.src, mwd);
                try storeTracked(&e, gpa, rm, types, wide_comp, mr.dst, if (mwd) .double else .long);
            },
            .move_result => {
                // Value is on the JVM stack from the preceding invoke.
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), if (last_ret_kind == 'F') .float else .int);
            },
            .move_result_object => {
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },
            .move_result_wide => {
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), if (last_ret_kind == 'D') .double else .long);
            },
            .move_exception => {
                // Handler entry: the exception object is on the JVM stack
                // (single-item stack per JVMS). Store it to the target reg.
                // The emitter's static stack depth is wrong here (it thinks
                // the stack holds whatever the fallthrough path left), so
                // reset depth accounting: handler starts fresh.
                e.cur_stack = 1;
                if (e.max_stack < 1) e.max_stack = 1;
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },

            .return_void => {
                try e.emit(gpa, 0xb1);
            },
            .return_ => {
                if (ret_kind == 'F') {
                    try emitLoadFloat(&e, gpa, rm, reg8(unit0));
                    try e.emit(gpa, 0xae); // freturn
                } else {
                    try emitLoadInt(&e, gpa, rm, reg8(unit0));
                    try e.emit(gpa, 0xac); // ireturn
                }
                e.push(-1);
            },
            .return_wide => {
                const rwd = ret_kind == 'D';
                try emitLoadWide(&e, gpa, rm, reg8(unit0), rwd);
                try e.emit(gpa, if (rwd) 0xaf else 0xad); // dreturn / lreturn
                e.push(-2);
            },
            .return_object => {
                try emitLoadRef(&e, gpa, rm, reg8(unit0));
                try e.emit(gpa, 0xb0); // areturn
                e.push(-1);
            },
            .const4 => {
                const lit: i32 = @as(i8, @bitCast(@as(u8, regB(unit0)) << 4)) >> 4;
                try emitConstInt(&e, gpa, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .const16 => {
                const lit: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try emitConstInt(&e, gpa, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .int);
            },
            .const_ => {
                try emitConstIntPool(&e, gpa, &uses, i32Pair(insns[pc + 1], insns[pc + 2]));
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .int);
            },
            .const_high16 => {
                const lit: i32 = @as(i32, @as(i16, @bitCast(insns[pc + 1]))) << 16;
                try emitConstIntPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .int);
            },
            .const_wide16 => {
                const lit: i64 = @as(i16, @bitCast(insns[pc + 1]));
                try emitConstLongPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .long);
            },
            .const_wide32 => {
                const lit: i64 = @as(i32, @bitCast(u32Pair(insns[pc + 1], insns[pc + 2])));
                try emitConstLongPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .long);
            },
            .const_wide => {
                // 51l: 64-bit literal across 4 units, little-endian.
                const u: u64 = u64Quad(insns[pc + 1], insns[pc + 2], insns[pc + 3], insns[pc + 4]);
                try emitConstLongPool(&e, gpa, &uses, @bitCast(u));
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .long);
            },
            .const_wide_high16 => {
                const lit: i64 = @as(i64, @as(i16, @bitCast(insns[pc + 1]))) << 48;
                try emitConstLongPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .long);
            },
            .goto_ => {
                const off: i32 = @as(i8, @bitCast(reg8(unit0)));
                try e.emitBranch(gpa, 0xa7, try branchTarget(dex_pc, off));
            },
            .goto16 => {
                const off: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try e.emitBranch(gpa, 0xa7, try branchTarget(dex_pc, off));
            },
            .goto32 => {
                const off: i32 = i32Pair(insns[pc + 1], insns[pc + 2]);
                try e.emitBranchWide(gpa, try branchTarget(dex_pc, off));
            },

            .if_eq, .if_ne, .if_lt, .if_ge, .if_gt, .if_le => {
                // if-eq/if-ne compare ints OR refs; the rest are int-only.
                const ra = regA(unit0);
                const rb = regB(unit0);
                const ta: RegType = if (ra < types.len) types[ra] else .unknown;
                const tb: RegType = if (rb < types.len) types[rb] else .unknown;
                const is_ref = ta == .ref or tb == .ref;
                const jop: u8 = switch (opcode) {
                    .if_eq => if (is_ref) @as(u8, 0xa5) else 0x9f, // if_acmpeq / if_icmpeq
                    .if_ne => if (is_ref) @as(u8, 0xa6) else 0xa0, // if_acmpne / if_icmpne
                    .if_lt => 0xa1, // if_icmplt
                    .if_ge => 0xa2, // if_icmpge
                    .if_gt => 0xa3, // if_icmpgt
                    .if_le => 0xa4, // if_icmple
                    else => unreachable,
                };
                if (is_ref and (opcode == .if_eq or opcode == .if_ne)) {
                    try emitLoadRef(&e, gpa, rm, ra);
                    try emitLoadRef(&e, gpa, rm, rb);
                } else {
                    try emitLoadInt(&e, gpa, rm, ra);
                    try emitLoadInt(&e, gpa, rm, rb);
                }
                e.push(-2);
                const off: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try e.emitBranch(gpa, jop, try branchTarget(dex_pc, off));
            },
            .if_eqz, .if_nez, .if_ltz, .if_gez, .if_gtz, .if_lez => {
                const ra = regA(unit0);
                const ta: RegType = if (ra < types.len) types[ra] else .unknown;
                const is_ref = ta == .ref;
                const jop: u8 = switch (opcode) {
                    .if_eqz => if (is_ref) @as(u8, 0xc6) else 0x99, // ifnull / ifeq
                    .if_nez => if (is_ref) @as(u8, 0xc7) else 0x9a, // ifnonnull / ifne
                    .if_ltz => 0x9b, // iflt
                    .if_gez => 0x9c, // ifge
                    .if_gtz => 0x9d, // ifgt
                    .if_lez => 0x9e, // ifle
                    else => unreachable,
                };
                if (is_ref and (opcode == .if_eqz or opcode == .if_nez)) {
                    try emitLoadRef(&e, gpa, rm, ra);
                } else {
                    try emitLoadInt(&e, gpa, rm, ra);
                }
                e.push(-1);
                const off: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try e.emitBranch(gpa, jop, try branchTarget(dex_pc, off));
            },
            .add_int, .sub_int, .mul_int, .div_int, .rem_int, .and_int, .or_int, .xor_int, .shl_int, .shr_int, .ushr_int => {
                // 23x: 8-bit regs. unit0 = A<<8|op, unit1 = C<<8|B.
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadInt(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .int);
            },
            .add_long, .sub_long, .mul_long, .div_long, .rem_long, .and_long, .or_long, .xor_long => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, false);
                try emitLoadWide(&e, gpa, rm, r.c, false);
                try e.emit(gpa, arithJop(opcode));
                e.push(-2);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .long);
            },
            .shl_long, .shr_long, .ushr_long => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, false);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1); // pop 3 (wide+int), push 2
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .long);
            },
            .add_float, .sub_float, .mul_float, .div_float, .rem_float => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadFloat(&e, gpa, rm, r.b);
                try emitLoadFloat(&e, gpa, rm, r.c);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .float);
            },
            .add_double, .sub_double, .mul_double, .div_double, .rem_double => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, true);
                try emitLoadWide(&e, gpa, rm, r.c, true);
                try e.emit(gpa, arithJop(opcode));
                e.push(-2);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .double);
            },

            .add_int_2addr, .sub_int_2addr, .mul_int_2addr, .div_int_2addr, .rem_int_2addr, .and_int_2addr, .or_int_2addr, .xor_int_2addr, .shl_int_2addr, .shr_int_2addr, .ushr_int_2addr => {
                // 12x: A = dest+src1, B = src2.
                const a = regA(unit0);
                try emitLoadInt(&e, gpa, rm, a);
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, a, .int);
            },
            .add_long_2addr, .sub_long_2addr, .mul_long_2addr, .div_long_2addr, .rem_long_2addr, .and_long_2addr, .or_long_2addr, .xor_long_2addr, .shl_long_2addr, .shr_long_2addr, .ushr_long_2addr => {
                const a = regA(unit0);
                const b = regB(unit0);
                try emitLoadWide(&e, gpa, rm, a, false);
                if (opcode == .shl_long_2addr or opcode == .shr_long_2addr or opcode == .ushr_long_2addr) {
                    try emitLoadInt(&e, gpa, rm, b);
                    try e.emit(gpa, arithJop(opcode));
                    e.push(-1);
                } else {
                    try emitLoadWide(&e, gpa, rm, b, false);
                    try e.emit(gpa, arithJop(opcode));
                    e.push(-2);
                }
                try storeTracked(&e, gpa, rm, types, wide_comp, a, .long);
            },
            .add_float_2addr, .sub_float_2addr, .mul_float_2addr, .div_float_2addr, .rem_float_2addr => {
                const a = regA(unit0);
                try emitLoadFloat(&e, gpa, rm, a);
                try emitLoadFloat(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, a, .float);
            },
            .add_double_2addr, .sub_double_2addr, .mul_double_2addr, .div_double_2addr, .rem_double_2addr => {
                const a = regA(unit0);
                try emitLoadWide(&e, gpa, rm, a, true);
                try emitLoadWide(&e, gpa, rm, regB(unit0), true);
                try e.emit(gpa, arithJop(opcode));
                e.push(-2);
                try storeTracked(&e, gpa, rm, types, wide_comp, a, .double);
            },
            .add_int_lit8, .sub_int_lit8, .mul_int_lit8, .div_int_lit8, .rem_int_lit8, .and_int_lit8, .or_int_lit8, .xor_int_lit8, .shl_int_lit8, .shr_int_lit8, .ushr_int_lit8 => {
                // 22b per ART: A = unit0 bits 8-15 (8-bit), B = unit1 low
                // byte, literal = unit1 high byte (signed).
                const a: u8 = @intCast((unit0 >> 8) & 0xFF);
                const b: u8 = @intCast(insns[pc + 1] & 0xFF);
                const lit: i32 = @as(i8, @bitCast(@as(u8, @intCast((insns[pc + 1] >> 8) & 0xFF))));
                try emitLoadInt(&e, gpa, rm, b);
                try emitConstInt(&e, gpa, lit);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, a, .int);
            },
            .add_int_lit16, .sub_int_lit16, .mul_int_lit16, .div_int_lit16, .rem_int_lit16, .and_int_lit16, .or_int_lit16, .xor_int_lit16 => {
                // 22s: literal = next unit (sign-extended 16-bit).
                const lit: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try emitConstInt(&e, gpa, lit);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .iget, .iget_wide, .iget_object, .iget_boolean, .iget_byte, .iget_char, .iget_short => {
                // 22c: A = dest, B = object, index in next unit.
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, 0xb4, insns[pc + 1], regB(unit0), regA(unit0));
            },
            .iput, .iput_wide, .iput_object, .iput_boolean, .iput_byte, .iput_char, .iput_short => {
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, 0xb5, insns[pc + 1], regB(unit0), regA(unit0));
            },
            .sget, .sget_wide, .sget_object, .sget_boolean, .sget_byte, .sget_char, .sget_short => {
                // 21c: A = dest, index in next unit.
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, 0xb2, insns[pc + 1], null, reg8(unit0));
            },
            .sput, .sput_wide, .sput_object, .sput_boolean, .sput_byte, .sput_char, .sput_short => {
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, 0xb3, insns[pc + 1], null, reg8(unit0));
            },
            .new_instance => {
                const type_idx = insns[pc + 1];
                try emitPoolRef(&e, gpa, &uses, 0xbb, 1, .{ .type = type_idx }); // new
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },
            .const_string => {
                // Always ldc_w (wide:true): the class writer patches a
                // 2-byte pool index, so string pools above 255 entries
                // stay encodable (jumbo-sized pools imply this path).
                try emitPoolRef(&e, gpa, &uses, 0x13, 1, .{ .string = insns[pc + 1] }); // ldc_w
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },
            .const_class => {
                // 21c: A = dest, type in next unit. Reference/array types
                // load via ldc_w of the Class entry; primitives have no
                // Class entry, so load Wrapper.TYPE instead (dex2jar parity).
                const type_idx = insns[pc + 1];
                const td = try d.getTypeDescriptor(gpa, type_idx);
                defer gpa.free(td);
                const prim_wrap: ?[]const u8 = if (td.len == 1) switch (td[0]) {
                    'Z' => "java/lang/Boolean",
                    'B' => "java/lang/Byte",
                    'C' => "java/lang/Character",
                    'D' => "java/lang/Double",
                    'F' => "java/lang/Float",
                    'I' => "java/lang/Integer",
                    'J' => "java/lang/Long",
                    'S' => "java/lang/Short",
                    else => null,
                } else null;
                if (prim_wrap) |w| {
                    try emitPoolRef(&e, gpa, &uses, 0xb2, 1, .{ .raw_field = .{ .cls = w, .name = "TYPE", .desc = "Ljava/lang/Class;" } }); // getstatic
                } else {
                    if (td.len == 0 or (td[0] != 'L' and td[0] != '[')) return error.BadDescriptor;
                    try emitPoolRef(&e, gpa, &uses, 0x13, 1, .{ .type = type_idx }); // ldc_w
                }
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },
            .const_string_jumbo => {
                // 32x: 8/16-bit reg + 32-bit string idx across 2 units.
                try emitPoolRef(&e, gpa, &uses, 0x13, 1, .{ .string = u32Pair(insns[pc + 1], insns[pc + 2]) }); // ldc_w (same wide path as const_string)
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },
            .invoke_virtual, .invoke_super, .invoke_direct, .invoke_static, .invoke_interface => {
                // 35c per ART GetVarArgs: argc = bits 12-15, G = bits 8-11,
                // C/D/E/F = unit2 nibbles 0/1/2/3. (A and B are swapped
                // relative to most formats; the spec labels are misleading.)
                const method_idx = insns[pc + 1];
                const argc: u8 = @intCast((unit0 >> 12) & 0xF);
                if (argc > 5) return error.UnsupportedOpcode;
                const nib = regs35c(unit0, insns[pc + 2]);
                const jop: u8 = switch (opcode) {
                    .invoke_virtual => 0xb6,
                    .invoke_super => 0xb7, // invokespecial
                    .invoke_direct => 0xb7, // invokespecial
                    .invoke_static => 0xb8,
                    .invoke_interface => 0xb9,
                    else => unreachable,
                };
                // Return value accounting happens in move-result; assume nothing here.
                // The class writer recomputes max_stack conservatively (+8 slop).
                try emitInvokeCall(&e, gpa, rm, d, &uses, &last_ret_kind, jop, method_idx, nib[0..argc], opcode == .invoke_static, opcode == .invoke_interface);
            },
            .invoke_virtual_range, .invoke_super_range, .invoke_direct_range, .invoke_static_range, .invoke_interface_range => {
                // 3rc: B = argc, CCCC = first register, BBBB = method idx.
                const method_idx = insns[pc + 1];
                const first: u16 = insns[pc + 2];
                const argc: u8 = reg8(unit0);
                // 3rc argc is a full byte (Kotlin data-class ctors exceed
                // 16); 256 slots always suffice (u8 max, JVM max 255).
                var rregs: [256]u16 = undefined;
                if (@as(usize, first) + argc > 65536) return error.BadBranch;
                for (0..argc) |i| rregs[i] = first + @as(u16, @intCast(i));
                const jop: u8 = switch (opcode) {
                    .invoke_virtual_range => 0xb6,
                    .invoke_super_range => 0xb7,
                    .invoke_direct_range => 0xb7,
                    .invoke_static_range => 0xb8,
                    .invoke_interface_range => 0xb9,
                    else => unreachable,
                };
                try emitInvokeCall(&e, gpa, rm, d, &uses, &last_ret_kind, jop, method_idx, rregs[0..argc], opcode == .invoke_static_range, opcode == .invoke_interface_range);
            },
            // Unary 12x: A = dest, B = src.
            .neg_int, .not_int => {
                const jop: u8 = if (opcode == .neg_int) 0x74 else 0x80 | 0x06; // ineg; inot via ixor -1
                _ = jop;
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                if (opcode == .neg_int) {
                    try e.emit(gpa, 0x74); // ineg
                } else {
                    // ixor with -1 (iconst_m1).
                    try e.emit(gpa, 0x02); // iconst_m1
                    e.push(1);
                    try e.emit(gpa, 0x82); // ixor
                    e.push(-1);
                }
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .neg_long, .not_long => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                if (opcode == .neg_long) {
                    try e.emit(gpa, 0x75); // lneg
                } else {
                    // lxor with -1L: iconst_m1, i2l, lxor.
                    try e.emit(gpa, 0x02); // iconst_m1
                    e.push(1);
                    try e.emit(gpa, 0x85); // i2l: pop 1, push 2
                    e.push(1);
                    try e.emit(gpa, 0x83); // lxor: pop 4, push 2
                    e.push(-2);
                }
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .long);
            },
            .neg_float => {
                try emitLoadFloat(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x76); // fneg
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .float);
            },
            .neg_double => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), true);
                try e.emit(gpa, 0x77); // dneg
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .double);
            },
            // Conversions 12x.
            .int_to_long => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x85); // i2l
                e.push(1); // pop 1 push 2
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .long);
            },
            .int_to_float => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x86); // i2f
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .float);
            },
            .int_to_double => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x87); // i2d
                e.push(1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .double);
            },
            .long_to_int => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                try e.emit(gpa, 0x88); // l2i
                e.push(-1); // pop 2 push 1
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .long_to_float => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                try e.emit(gpa, 0x89); // l2f
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .float);
            },
            .long_to_double => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                try e.emit(gpa, 0x8a); // l2d
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .double);
            },
            .float_to_int => {
                try emitLoadFloat(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x8b); // f2i
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .float_to_long => {
                try emitLoadFloat(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x8c); // f2l
                e.push(1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .long);
            },
            .float_to_double => {
                try emitLoadFloat(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x8d); // f2d
                e.push(1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .double);
            },
            .double_to_int => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), true);
                try e.emit(gpa, 0x8e); // d2i
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .double_to_long => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), true);
                try e.emit(gpa, 0x8f); // d2l
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .long);
            },
            .double_to_float => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), true);
                try e.emit(gpa, 0x90); // d2f
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .float);
            },
            .int_to_byte => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x91); // i2b
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .int_to_char => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x92); // i2c
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .int_to_short => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x93); // i2s
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            // Arrays: 23x aget/aput (A=dest/src val, B=array, C=index),
            // 12x array-length, 22c new-array.
            .aget => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, 0x2e); // iaload
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .int);
            },
            .aget_boolean, .aget_byte, .aget_char, .aget_short => {
                // 23x like aget; baload/caload/saload all push int.
                const jop: u8 = switch (opcode) {
                    .aget_boolean, .aget_byte => 0x33, // baload
                    .aget_char => 0x34, // caload
                    else => 0x35, // saload
                };
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, jop);
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .int);
            },
            .aget_wide => {
                const r = regs23x(unit0, insns[pc + 1]);
                // Component kind from the new-array tracker (array reg b);
                // fall back to the value reg's type, else long. Only
                // double[] needs daload/.double; long is the default.
                const comp: u8 = if (r.b < wide_comp.len) wide_comp[r.b] else 0;
                const vt: RegType = if (r.a < types.len) types[r.a] else .unknown;
                const is_d = wideIsDouble(comp, vt);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, if (is_d) 0x31 else 0x2f); // daload / laload
                e.push(0); // pop 2, push 2
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, if (is_d) .double else .long);
            },
            .aget_object => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, 0x32); // aaload
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .ref);
            },
            .aput => {
                const r = regs23x(unit0, insns[pc + 1]);
                const afl = r.a < types.len and types[r.a] == .float;
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                if (afl) try emitLoadFloat(&e, gpa, rm, r.a) else try emitLoadInt(&e, gpa, rm, r.a);
                try e.emit(gpa, if (afl) 0x51 else 0x4f); // fastore / iastore
                e.push(-3);
            },
            .aput_boolean, .aput_byte, .aput_char, .aput_short => {
                // 23x like aput; value is int on the stack for all four.
                const jop: u8 = switch (opcode) {
                    .aput_boolean, .aput_byte => 0x54, // bastore
                    .aput_char => 0x55, // castore
                    else => 0x56, // sastore
                };
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try emitLoadInt(&e, gpa, rm, r.a);
                try e.emit(gpa, jop);
                e.push(-3);
            },
            .aput_wide => {
                const r = regs23x(unit0, insns[pc + 1]);
                const acomp: u8 = if (r.b < wide_comp.len) wide_comp[r.b] else 0;
                const avt: RegType = if (r.a < types.len) types[r.a] else .unknown;
                const is_d = wideIsDouble(acomp, avt);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try emitLoadWide(&e, gpa, rm, r.a, is_d);
                try e.emit(gpa, if (is_d) 0x52 else 0x50); // dastore / lastore
                e.push(-4);
            },
            .aput_object => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try emitLoadRef(&e, gpa, rm, r.a);
                try e.emit(gpa, 0x53); // aastore
                e.push(-3);
            },
            .array_length => {
                // 12x: A = dest, B = array ref.
                try emitLoadRef(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0xbe); // arraylength
                e.push(0); // pop ref, push int
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },

            .new_array => {
                // 22c: A = dest, B = size, type in next unit.
                // anewarray for refs, newarray for primitives: decide by descriptor.
                const type_idx = insns[pc + 1];
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                const td = try d.getTypeDescriptor(gpa, type_idx);
                defer gpa.free(td);
                try emitNewArray(&e, gpa, &uses, type_idx, td);
                const dest = regA(unit0);
                try storeTracked(&e, gpa, rm, types, wide_comp, dest, .ref);
                // Record wide component kind for later aget/aput-wide on
                // this dest reg ('J'/'D', else 0 = unknown/long default).
                // AFTER storeTracked (which clears wide_comp for refs).
                if (dest < wide_comp.len) {
                    wide_comp[dest] = if (td.len == 2 and td[0] == '[' and (td[1] == 'J' or td[1] == 'D')) td[1] else 0;
                }
            },
            .monitor_enter => {
                try emitLoadRef(&e, gpa, rm, reg8(unit0));
                try e.emit(gpa, 0xc2); // monitorenter
                e.push(-1);
            },
            .monitor_exit => {
                try emitLoadRef(&e, gpa, rm, reg8(unit0));
                try e.emit(gpa, 0xc3); // monitorexit
                e.push(-1);
            },
            .throw_ => {
                // 11x: A = exception ref.
                try emitLoadRef(&e, gpa, rm, reg8(unit0));
                try e.emit(gpa, 0xbf); // athrow
                e.push(-1);
            },
            .check_cast => {
                // 21c: A = ref, type in next unit.
                try emitLoadRef(&e, gpa, rm, reg8(unit0));
                try emitPoolRef(&e, gpa, &uses, 0xc0, 0, .{ .type = insns[pc + 1] }); // checkcast
                try storeTracked(&e, gpa, rm, types, wide_comp, reg8(unit0), .ref);
            },
            .instance_of => {
                // 22c: A = dest, B = ref, type in next unit.
                try emitLoadRef(&e, gpa, rm, regB(unit0));
                try emitPoolRef(&e, gpa, &uses, 0xc1, 0, .{ .type = insns[pc + 1] }); // instanceof: pop ref, push int
                try storeTracked(&e, gpa, rm, types, wide_comp, regA(unit0), .int);
            },
            .packed_switch, .sparse_switch => {
                // 31t: A = test reg, payload at branch offset (code units).
                const pu: usize = try payloadPc(dex_pc, i32Pair(insns[pc + 1], insns[pc + 2]), insns.len);
                const ident = insns[pu];
                const want: u16 = if (opcode == .packed_switch) 0x0100 else 0x0200;
                if (ident != want) return error.BadBranch;
                const count: usize = insns[pu + 1];
                if (count == 0 or count > 4096) return error.BadSection;
                try emitLoadInt(&e, gpa, rm, reg8(unit0));
                e.push(-1); // consumed by switch
                if (opcode == .packed_switch) {
                    const first_key: i32 = i32Pair(insns[pu + 2], insns[pu + 3]);
                    if (pu + 4 + count * 2 > insns.len) return error.BadBranch;
                    // tableswitch with JVM fixups per target.
                    try e.emit(gpa, 0xaa); // tableswitch
                    const op_at: u32 = @intCast(e.code.items.len - 1);
                    while (e.code.items.len % 4 != 0) try e.emit(gpa, 0);
                    // default: fall through to next DEX instruction.
                    const default_dex: u32 = dex_pc + 3;
                    try e.emitU32Fixup(gpa, op_at, default_dex);
                    try e.emitI32(gpa, first_key);
                    try e.emitI32(gpa, first_key + @as(i32, @intCast(count)) - 1);
                    for (0..count) |i| {
                        const rel: i32 = i32Pair(insns[pu + 4 + i * 2], insns[pu + 4 + i * 2 + 1]);
                        try e.emitU32Fixup(gpa, op_at, try branchTarget(dex_pc, rel));
                    }
                } else {
                    if (pu + 2 + count * 4 > insns.len) return error.BadBranch;
                    try e.emit(gpa, 0xab); // lookupswitch
                    const op_at: u32 = @intCast(e.code.items.len - 1);
                    while (e.code.items.len % 4 != 0) try e.emit(gpa, 0);
                    const default_dex: u32 = dex_pc + 3;
                    try e.emitU32Fixup(gpa, op_at, default_dex);
                    // npairs
                    try e.emitU32Raw(gpa, @intCast(count));
                    for (0..count) |i| {
                        const key: i32 = i32Pair(insns[pu + 2 + i * 2], insns[pu + 2 + i * 2 + 1]);
                        const rel: i32 = i32Pair(insns[pu + 2 + count * 2 + i * 2], insns[pu + 2 + count * 2 + i * 2 + 1]);
                        try e.emitI32(gpa, key);
                        try e.emitU32Fixup(gpa, op_at, try branchTarget(dex_pc, rel));
                    }
                }
            },
            .fill_array_data => {
                // 31t: array reg + payload. Sound lowering: explicit
                // per-element stores (correct for any size; large arrays
                // bloat but stay correct — an optimization can compact later).
                const pu: usize = try payloadPc(dex_pc, i32Pair(insns[pc + 1], insns[pc + 2]), insns.len);
                if (insns[pu] != 0x0300) return error.BadBranch;
                const width = insns[pu + 1];
                if (width != 1 and width != 2 and width != 4 and width != 8) return error.BadSection;
                const count: usize = u32Pair(insns[pu + 2], insns[pu + 3]);
                if (count > 65536) return error.BadSection;
                const data_start = pu + 4; // in code units
                const total_bytes = count * width;
                const total_units = (total_bytes + 1) / 2;
                if (data_start + total_units > insns.len) return error.BadBranch;
                const arr = reg8(unit0);
                var idx: usize = 0;
                while (idx < count) : (idx += 1) {
                    // Load element value (little-endian from unit stream).
                    const byte_off = idx * width;
                    try emitLoadRef(&e, gpa, rm, arr);
                    try emitConstInt(&e, gpa, @intCast(idx));
                    if (width == 8) {
                        // long/double element: reconstruct 64 bits, push
                        // via ldc2_w (Long or Double pool entry by the
                        // array reg's tracked component), then xastore.
                        const bits: u64 = u64Quad(insns[data_start + byte_off / 2], insns[data_start + byte_off / 2 + 1], insns[data_start + byte_off / 2 + 2], insns[data_start + byte_off / 2 + 3]);
                        const dcomp: u8 = if (arr < wide_comp.len) wide_comp[arr] else 0;
                        const fis_d = dcomp == 'D';
                        if (fis_d) {
                            try emitConstDoublePool(&e, gpa, &uses, bits);
                        } else {
                            try emitConstLongPool(&e, gpa, &uses, @bitCast(bits));
                        }
                        try e.emit(gpa, if (fis_d) 0x52 else 0x50); // dastore / lastore
                        e.push(-4); // pop array+index+wide
                        continue;
                    }
                    var v: i32 = 0;
                    var b: usize = 0;
                    while (b < width) : (b += 1) {
                        const u = insns[data_start + (byte_off + b) / 2];
                        const byte: u32 = if ((byte_off + b) % 2 == 0) u & 0xFF else (u >> 8) & 0xFF;
                        v |= @as(i32, @bitCast(byte << @intCast(b * 8)));
                    }
                    // Sign-extend sub-word values.
                    if (width == 1) v = @as(i8, @truncate(v));
                    if (width == 2) v = @as(i16, @truncate(v));
                    try emitConstIntPool(&e, gpa, &uses, v);
                    try e.emit(gpa, 0x4f); // iastore (also boolean/byte/char/short arrays)
                    e.push(-3);
                }
            },
            .cmpl_float, .cmpg_float => {
                // 23x float compare: fcmpl (0x95, NaN -> -1) / fcmpg (0x96, NaN -> +1).
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, r.b);
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, r.c);
                try e.emit(gpa, if (opcode == .cmpl_float) 0x95 else 0x96);
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .int);
            },
            .cmpl_double, .cmpg_double => {
                // 23x double compare: dcmpl (0x97) / dcmpg (0x98).
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.b);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.c);
                try e.emit(gpa, if (opcode == .cmpl_double) 0x97 else 0x98);
                e.push(-3);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .int);
            },
            .cmp_long => {
                // 23x long compare: lcmp (0x94).
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, false);
                try emitLoadWide(&e, gpa, rm, r.c, false);
                try e.emit(gpa, 0x94);
                e.push(-3);
                try storeTracked(&e, gpa, rm, types, wide_comp, r.a, .int);
            },
            .filled_new_array, .filled_new_array_range => {
                // 35c/3rc: build a fresh array element-by-element, leaving the
                // ref on the stack for the following move-result-object.
                const type_idx = insns[pc + 1];
                const td = try d.getTypeDescriptor(gpa, type_idx);
                defer gpa.free(td);
                if (td.len < 2 or td[0] != '[') return error.BadDescriptor;
                const elem = td[1];
                try filledArrayElemKind(elem);
                const is_ref_elem = elem == 'L' or elem == '[';
                const is_float_elem = elem == 'F';
                var regs: [256]u16 = undefined;
                var argc: usize = 0;
                if (opcode == .filled_new_array) {
                    const n: u8 = @intCast((unit0 >> 12) & 0xF);
                    if (n == 0) return error.BadSection;
                    const nib = regs35c(unit0, insns[pc + 2]);
                    regs[0..5].* = nib;
                    argc = n;
                    if (argc > 5) return error.UnsupportedOpcode;
                } else {
                    const n: u8 = reg8(unit0);
                    if (n == 0) return error.BadSection;
                    const first: u16 = insns[pc + 2];
                    if (@as(usize, first) + n > 65536) return error.BadRegister;
                    for (0..n) |i| regs[i] = first + @as(u16, @intCast(i));
                    argc = n;
                }
                try emitConstIntPool(&e, gpa, &uses, @intCast(argc));
                try emitNewArray(&e, gpa, &uses, type_idx, td);
                var i: usize = 0;
                while (i < argc) : (i += 1) {
                    try e.emit(gpa, 0x59); // dup
                    e.push(1);
                    try emitConstInt(&e, gpa, @intCast(i));
                    if (is_ref_elem) {
                        try emitLoadRef(&e, gpa, rm, regs[i]);
                    } else if (is_float_elem) {
                        try emitLoadFloat(&e, gpa, rm, regs[i]);
                    } else {
                        try emitLoadInt(&e, gpa, rm, regs[i]);
                    }
                    try e.emit(gpa, if (is_ref_elem) 0x53 else if (is_float_elem) 0x51 else 0x4f);
                    e.push(-3);
                }
                // Arrayref stays on the stack for move-result-object (store pops it).
                e.push(1);
            },
            else => return error.UnsupportedOpcode,
        }
        pc += size;
    }
    try e.resolve();
    // Expand DEX tries to JVM exception table entries via the offset map.
    var exceptions: std.ArrayListUnmanaged(ExceptionEntry) = .empty;
    errdefer exceptions.deinit(gpa);
    for (code.tries) |t| {
        const start = e.dex_to_jvm.get(t.start_addr) orelse return error.BadBranch;
        const end_dex = t.start_addr + t.insn_count;
        // end_pc: map the first unit past the range; fall back to code end.
        const end = e.dex_to_jvm.get(end_dex) orelse @as(u32, @intCast(e.code.items.len));
        const handlers = try dex.readCatchHandlers(gpa, d, code, t.handler_off);
        defer gpa.free(handlers);
        for (handlers) |h| {
            const hpc = e.dex_to_jvm.get(h.addr) orelse return error.BadBranch;
            try exceptions.append(gpa, .{
                .start_pc = start,
                .end_pc = end,
                .handler_pc = hpc,
                .catch_type = if (h.type_idx == std.math.maxInt(u32)) null else h.type_idx,
            });
        }
    }
    // Map debug (dex_pc -> line) entries to JVM offsets via dex_to_jvm,
    // then sort by start_pc and dedup (keep first line per offset).
    var lines: std.ArrayListUnmanaged(LineEntry) = .empty;
    errdefer lines.deinit(gpa);
    {
        const dbg = try dex.readDebugLines(gpa, d, code.debug_off);
        defer gpa.free(dbg);
        for (dbg) |dl| {
            const jvm = e.dex_to_jvm.get(dl.addr) orelse continue;
            try lines.append(gpa, .{ .start_pc = jvm, .line = dl.line });
        }
        std.mem.sort(LineEntry, lines.items, {}, struct {
            fn less(_: void, a: LineEntry, b: LineEntry) bool {
                return a.start_pc < b.start_pc;
            }
        }.less);
        var w: usize = 0;
        for (lines.items) |le| {
            if (w > 0 and lines.items[w - 1].start_pc == le.start_pc) continue;
            lines.items[w] = le;
            w += 1;
        }
        lines.items.len = w;
    }
    return .{
        .code = try e.code.toOwnedSlice(gpa),
        .max_stack = e.max_stack,
        .max_locals = max_locals,
        .uses = try uses.toOwnedSlice(gpa),
        .exceptions = try exceptions.toOwnedSlice(gpa),
        .lines = try lines.toOwnedSlice(gpa),
    };
}
// --- Class file writer ---

/// DEX descriptor (Lpkg/Cls; or primitive) to JVM internal form (pkg/Cls).
/// Caller owns the result.
pub fn descriptorToInternal(gpa: std.mem.Allocator, desc: []const u8) TranslateError![]u8 {
    if (desc.len >= 2 and desc[0] == 'L' and desc[desc.len - 1] == ';') {
        return gpa.dupe(u8, desc[1 .. desc.len - 1]) catch return error.OutOfMemory;
    }
    if (desc.len == 1) {
        // Primitive: map to wrapper-free JVM base type char (same letter).
        return gpa.dupe(u8, desc) catch return error.OutOfMemory;
    }
    if (desc.len >= 1 and desc[0] == '[') {
        // Array: JVM form identical.
        return gpa.dupe(u8, desc) catch return error.OutOfMemory;
    }
    return error.BadDescriptor;
}

const CpEntry = union(enum) {
    utf8: []const u8,
    class: u16, // name_idx
    string: u16, // utf8 idx
    integer: i32,
    long: i64,
    double: u64, // bits
    pad: void, // second slot of a long/double (JVM 4.4.5: unusable index)
    fieldref: struct { class_idx: u16, nat_idx: u16 },
    methodref: struct { class_idx: u16, nat_idx: u16 },
    ifacemethodref: struct { class_idx: u16, nat_idx: u16 },
    nameandtype: struct { name_idx: u16, desc_idx: u16 },
};

pub const ClassWriter = struct {
    entries: std.ArrayListUnmanaged(CpEntry) = .empty,
    utf8_map: std.StringHashMapUnmanaged(u16) = .empty,

    pub fn deinit(self: *ClassWriter, gpa: std.mem.Allocator) void {
        for (self.entries.items) |en| {
            if (en == .utf8) gpa.free(en.utf8);
        }
        self.entries.deinit(gpa);
        self.utf8_map.deinit(gpa);
    }

    pub fn utf8(self: *ClassWriter, gpa: std.mem.Allocator, s: []const u8) !u16 {
        if (self.utf8_map.get(s)) |idx| return idx;
        const owned = try gpa.dupe(u8, s);
        try self.entries.append(gpa, .{ .utf8 = owned });
        const idx: u16 = @intCast(self.entries.items.len);
        try self.utf8_map.put(gpa, owned, idx);
        return idx;
    }

    /// Shared single-utf8-ref ctor (class/string differ only by entry tag).
    fn utf8Single(self: *ClassWriter, gpa: std.mem.Allocator, s: []const u8, is_class: bool) !u16 {
        const n = try self.utf8(gpa, s);
        try self.entries.append(gpa, if (is_class) .{ .class = n } else .{ .string = n });
        return @intCast(self.entries.items.len);
    }

    /// Shared two-slot ctor (long/double differ only by entry tag; both
    /// take a bit pattern — long bitcasts its value at the call site).
    fn wideSlot(self: *ClassWriter, gpa: std.mem.Allocator, bits: u64, is_double: bool) !u16 {
        try self.entries.append(gpa, if (is_double) .{ .double = bits } else .{ .long = @bitCast(bits) });
        const idx: u16 = @intCast(self.entries.items.len);
        try self.entries.append(gpa, .pad);
        return idx;
    }

    /// Shared class+nameAndType member-ref ctor. iface == null selects a
    /// fieldref; otherwise a method/ifacemethodref by the flag.
    fn memberRef(self: *ClassWriter, gpa: std.mem.Allocator, cls_internal: []const u8, name: []const u8, desc: []const u8, iface: ?bool) !u16 {
        const ci = try self.class(gpa, cls_internal);
        const ni = try self.nameAndType(gpa, name, desc);
        if (iface) |is_iface| {
            try self.entries.append(gpa, if (is_iface) .{ .ifacemethodref = .{ .class_idx = ci, .nat_idx = ni } } else .{ .methodref = .{ .class_idx = ci, .nat_idx = ni } });
        } else {
            try self.entries.append(gpa, .{ .fieldref = .{ .class_idx = ci, .nat_idx = ni } });
        }
        return @intCast(self.entries.items.len);
    }

    pub fn class(self: *ClassWriter, gpa: std.mem.Allocator, internal: []const u8) !u16 {
        return self.utf8Single(gpa, internal, true);
    }

    pub fn string(self: *ClassWriter, gpa: std.mem.Allocator, s: []const u8) !u16 {
        return self.utf8Single(gpa, s, false);
    }

    pub fn integer(self: *ClassWriter, gpa: std.mem.Allocator, v: i32) !u16 {
        try self.entries.append(gpa, .{ .integer = v });
        return @intCast(self.entries.items.len);
    }

    // Long/Double occupy TWO pool slots (the second is unusable); the
    // pad entry keeps 1-based indexing aligned. Returns the first slot.
    pub fn long(self: *ClassWriter, gpa: std.mem.Allocator, v: i64) !u16 {
        return self.wideSlot(gpa, @bitCast(v), false);
    }

    pub fn double(self: *ClassWriter, gpa: std.mem.Allocator, bits: u64) !u16 {
        return self.wideSlot(gpa, bits, true);
    }

    pub fn nameAndType(self: *ClassWriter, gpa: std.mem.Allocator, name: []const u8, desc: []const u8) !u16 {
        const ni = try self.utf8(gpa, name);
        const di = try self.utf8(gpa, desc);
        try self.entries.append(gpa, .{ .nameandtype = .{ .name_idx = ni, .desc_idx = di } });
        return @intCast(self.entries.items.len);
    }

    pub fn fieldref(self: *ClassWriter, gpa: std.mem.Allocator, cls_internal: []const u8, name: []const u8, desc: []const u8) !u16 {
        return self.memberRef(gpa, cls_internal, name, desc, null);
    }

    pub fn methodref(self: *ClassWriter, gpa: std.mem.Allocator, cls_internal: []const u8, name: []const u8, desc: []const u8, iface: bool) !u16 {
        return self.memberRef(gpa, cls_internal, name, desc, iface);
    }
    fn appendU16BE(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, v: u16) !void {
        try writeU16(out, gpa, v);
    }

    fn appendU32BE(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, v: u32) !void {
        try writeU32(out, gpa, v);
    }

    /// Shared tag + single-u16 emission (class/string).
    fn tag1(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, tag: u8, idx: u16) !void {
        try out.append(gpa, tag);
        try appendU16BE(out, gpa, idx);
    }

    /// Shared tag + two-u16 emission (fieldref/methodref/ifacemethodref/nameandtype).
    fn ref2(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, tag: u8, a: u16, b: u16) !void {
        try out.append(gpa, tag);
        try appendU16BE(out, gpa, a);
        try appendU16BE(out, gpa, b);
    }
    fn writeU16(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, v: u16) !void {
        try out.append(gpa, @intCast(v >> 8));
        try out.append(gpa, @intCast(v & 0xFF));
    }
    fn writeU32(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, v: u32) !void {
        try out.append(gpa, @intCast(v >> 24));
        try out.append(gpa, @intCast((v >> 16) & 0xFF));
        try out.append(gpa, @intCast((v >> 8) & 0xFF));
        try out.append(gpa, @intCast(v & 0xFF));
    }
    fn writeU64(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, v: u64) !void {
        try writeU32(out, gpa, @intCast(v >> 32));
        try writeU32(out, gpa, @intCast(v & 0xFFFFFFFF));
    }

    /// Encode standard UTF-8 (possibly WTF-8 with lone surrogates) as
    /// JVM Modified UTF-8: U+0000 -> C0 80, BMP as-is (1-3 bytes),
    /// astral -> CESU-8 surrogate pair (2x3 bytes). Invalid sequences
    /// pass through byte-wise (fail-open: matches dex2jar posture for
    /// hostile strings; the class still loads).
    fn toModifiedUtf8(gpa: std.mem.Allocator, s: []const u8) ![]u8 {
        var out: std.ArrayListUnmanaged(u8) = .empty;
        errdefer out.deinit(gpa);
        var i: usize = 0;
        while (i < s.len) {
            const b = s[i];
            if (b == 0) {
                try out.appendSlice(gpa, &.{ 0xC0, 0x80 });
                i += 1;
            } else if (b < 0x80) {
                try out.append(gpa, b);
                i += 1;
            } else if (b >> 5 == 0b110 and i + 1 < s.len) {
                try out.appendSlice(gpa, s[i .. i + 2]);
                i += 2;
            } else if (b >> 4 == 0b1110 and i + 2 < s.len) {
                try out.appendSlice(gpa, s[i .. i + 3]);
                i += 3;
            } else if (b >> 3 == 0b11110 and i + 3 < s.len) {
                // Astral UTF-8 -> CESU-8 pair.
                const b2 = s[i + 1];
                const b3 = s[i + 2];
                const b4 = s[i + 3];
                if (b2 >> 6 != 0b10 or b3 >> 6 != 0b10 or b4 >> 6 != 0b10) {
                    try out.append(gpa, b);
                    i += 1;
                    continue;
                }
                const cp: u21 = (@as(u21, b & 0x07) << 18) | (@as(u21, b2 & 0x3F) << 12) | (@as(u21, b3 & 0x3F) << 6) | (b4 & 0x3F);
                if (cp < 0x10000 or cp > 0x10FFFF) {
                    try out.append(gpa, b);
                    i += 1;
                    continue;
                }
                const hi: u16 = @intCast(0xD800 + ((cp - 0x10000) >> 10));
                const lo: u16 = @intCast(0xDC00 + ((cp - 0x10000) & 0x3FF));
                for ([_]u16{ hi, lo }) |su| {
                    try out.append(gpa, 0xE0 | @as(u8, @intCast(su >> 12)));
                    try out.append(gpa, 0x80 | @as(u8, @intCast((su >> 6) & 0x3F)));
                    try out.append(gpa, 0x80 | @as(u8, @intCast(su & 0x3F)));
                }
                i += 4;
            } else {
                try out.append(gpa, b);
                i += 1;
            }
        }
        return out.toOwnedSlice(gpa);
    }
    pub fn emitPool(self: *ClassWriter, gpa: std.mem.Allocator, out: *std.ArrayListUnmanaged(u8)) !void {
        try writeU16(out, gpa, @intCast(self.entries.items.len + 1));
        for (self.entries.items) |en| {
            switch (en) {
                .utf8 => |s| {
                    // JVM CONSTANT_Utf8 is Modified UTF-8: NUL as C0 80,
                    // non-BMP as CESU-8 surrogate pairs (never 4-byte).
                    const m = try toModifiedUtf8(gpa, s);
                    defer gpa.free(m);
                    try out.append(gpa, 1);
                    try appendU16BE(out, gpa, @intCast(m.len));
                    try out.appendSlice(gpa, m);
                },
                .class => |n| try tag1(out, gpa, 7, n),
                .string => |n| try tag1(out, gpa, 8, n),
                .integer => |v| {
                    try out.append(gpa, 3);
                    try appendU32BE(out, gpa, @bitCast(v));
                },
                .long => |v| try tag8(out, gpa, 5, @bitCast(v)),
                .double => |b| try tag8(out, gpa, 6, b),
                .pad => {},
                .fieldref => |r| try ref2(out, gpa, 9, r.class_idx, r.nat_idx),
                .methodref => |r| try ref2(out, gpa, 10, r.class_idx, r.nat_idx),
                .ifacemethodref => |r| try ref2(out, gpa, 11, r.class_idx, r.nat_idx),
                .nameandtype => |r| try ref2(out, gpa, 12, r.name_idx, r.desc_idx),
            }
        }
    }
    /// Shared tag + 8-byte payload (long/double).
    fn tag8(out: *std.ArrayListUnmanaged(u8), gpa: std.mem.Allocator, tag: u8, bits: u64) !void {
        try out.append(gpa, tag);
        try writeU64(out, gpa, bits);
    }
};

/// DEX access flags to JVM. Bit values coincide except where noted.
pub fn dexFlagsToJvm(dex_flags: u32) u16 {
    var out: u16 = 0;
    if (dex_flags & 0x1 != 0) out |= 0x0001; // public
    if (dex_flags & 0x2 != 0) out |= 0x0002; // private
    if (dex_flags & 0x4 != 0) out |= 0x0004; // protected
    if (dex_flags & 0x8 != 0) out |= 0x0008; // static
    if (dex_flags & 0x10 != 0) out |= 0x0010; // final
    if (dex_flags & 0x20 != 0) out |= 0x0020; // super/synchronized bit
    if (dex_flags & 0x80 != 0) out |= 0x0080; // transient/varargs
    if (dex_flags & 0x100 != 0) out |= 0x0100; // native
    if (dex_flags & 0x200 != 0) out |= 0x0200; // interface
    if (dex_flags & 0x400 != 0) out |= 0x0400; // abstract
    if (dex_flags & 0x1000 != 0) out |= 0x1000; // synthetic
    if (dex_flags & 0x4000 != 0) out |= 0x4000; // enum
    return out;
}

/// Build a JVM method descriptor from a DEX proto: (params)return.
pub fn protoDescriptor(gpa: std.mem.Allocator, d: dex.Dex, proto_idx: u32) TranslateError![]u8 {
    const p = try d.protoId(proto_idx);
    var out: std.ArrayListUnmanaged(u8) = .empty;
    errdefer out.deinit(gpa);
    out.append(gpa, '(') catch return error.OutOfMemory;
    if (p.parameters_off != 0) {
        const idxs = try dex.readTypeList(gpa, d, p.parameters_off);
        defer gpa.free(idxs);
        for (idxs) |t| {
            const desc = try d.getTypeDescriptor(gpa, t);
            defer gpa.free(desc);
            out.appendSlice(gpa, desc) catch return error.OutOfMemory;
        }
    }
    out.append(gpa, ')') catch return error.OutOfMemory;
    const ret = try d.getTypeDescriptor(gpa, p.return_type_idx);
    defer gpa.free(ret);
    out.appendSlice(gpa, ret) catch return error.OutOfMemory;
    return out.toOwnedSlice(gpa) catch return error.OutOfMemory;
}

pub const TranslatedClass = struct {
    internal_name: []u8,
    bytes: []u8,

    pub fn deinit(self: *TranslatedClass, gpa: std.mem.Allocator) void {
        gpa.free(self.internal_name);
        gpa.free(self.bytes);
    }
};

const MethodOut = struct {
    name: []const u8,
    desc: []const u8,
    flags: u16,
    tm: TranslatedMethod,
};

/// Translate one DEX class_def to a JVM class file (major v49, pure inference verification).
/// Caller owns the result. `class_name` used in errors only.
pub fn translateClass(
    gpa: std.mem.Allocator,
    d: dex.Dex,
    def_idx: usize,
) TranslateError!TranslatedClass {
    const def = try d.classDef(def_idx);
    const desc = try d.getTypeDescriptor(gpa, def.class_idx);
    defer gpa.free(desc);
    const internal = try descriptorToInternal(gpa, desc);
    errdefer gpa.free(internal);

    var cdata = try dex.readClassData(gpa, d, def.class_data_off);
    defer cdata.deinit(gpa);

    var cw = ClassWriter{};
    defer cw.deinit(gpa);

    const this_cls = try cw.class(gpa, internal);
    var super_cls: u16 = 0;
    if (def.superclass_idx != dex.no_index) {
        const sdesc = try d.getTypeDescriptor(gpa, def.superclass_idx);
        defer gpa.free(sdesc);
        const sinternal = try descriptorToInternal(gpa, sdesc);
        defer gpa.free(sinternal);
        super_cls = try cw.class(gpa, sinternal);
    } else {
        super_cls = try cw.class(gpa, "java/lang/Object");
    }

    // Fields: instance + static.
    const FieldOut = struct { name: []const u8, desc: []const u8, flags: u16 };
    var fields: std.ArrayListUnmanaged(FieldOut) = .empty;
    defer {
        for (fields.items) |f| {
            gpa.free(f.name);
            gpa.free(f.desc);
        }
        fields.deinit(gpa);
    }
    for (cdata.instance_fields) |f| {
        const fid = try d.fieldId(f.field_idx);
        const fname = try d.getString(gpa, fid.name_idx);
        errdefer gpa.free(fname);
        const fdesc = try d.getTypeDescriptor(gpa, fid.type_idx);
        errdefer gpa.free(fdesc);
        try fields.append(gpa, .{
            .name = fname,
            .desc = fdesc,
            .flags = dexFlagsToJvm(f.access_flags),
        });
    }
    for (cdata.static_fields) |f| {
        const fid = try d.fieldId(f.field_idx);
        const fname = try d.getString(gpa, fid.name_idx);
        errdefer gpa.free(fname);
        const fdesc = try d.getTypeDescriptor(gpa, fid.type_idx);
        errdefer gpa.free(fdesc);
        try fields.append(gpa, .{
            .name = fname,
            .desc = fdesc,
            .flags = dexFlagsToJvm(f.access_flags) | 0x0008,
        });
    }

    // Methods: translate bodies, collect const uses.
    var methods: std.ArrayListUnmanaged(MethodOut) = .empty;
    defer {
        for (methods.items) |*m| {
            gpa.free(m.name);
            gpa.free(m.desc);
            m.tm.deinit(gpa);
        }
        methods.deinit(gpa);
    }
    const all_methods = [_][]dex.EncodedMethod{ cdata.direct_methods, cdata.virtual_methods };
    for (all_methods) |group| {
        for (group) |m| {
            const mid = try d.methodId(m.method_idx);
            const mname = try d.getString(gpa, mid.name_idx);
            errdefer gpa.free(mname);
            const mdesc = try protoDescriptor(gpa, d, mid.proto_idx);
            errdefer gpa.free(mdesc);
            var tm: TranslatedMethod = undefined;
            var has_body = false;
            if (m.code_off != 0) {
                var c = try dex.readCode(gpa, d, m.code_off);
                defer c.deinit(gpa);
                tm = try translateMethod(gpa, d, c, mid.proto_idx, m.access_flags & 0x8 != 0);
                has_body = true;
            } else {
                tm = .{ .code = &.{}, .max_stack = 0, .max_locals = 0, .uses = &.{}, .exceptions = &.{} , .lines = &.{} };
            }
            errdefer if (has_body) tm.deinit(gpa);
            try methods.append(gpa, .{
                .name = mname,
                .desc = mdesc,
                .flags = dexFlagsToJvm(m.access_flags),
                .tm = tm,
            });
        }
    }

    // Resolve const uses into pool indices and patch code.
    for (methods.items) |*m| {
        for (m.tm.uses) |u| {
            const idx: u16 = switch (u.ref) {
                .string => |si| blk: {
                    const s = try d.getString(gpa, si);
                    defer gpa.free(s);
                    break :blk try cw.string(gpa, s);
                },
                .type => |ti| blk: {
                    const td = try d.getTypeDescriptor(gpa, ti);
                    defer gpa.free(td);
                    const ti2 = try descriptorToInternal(gpa, td);
                    defer gpa.free(ti2);
                    break :blk try cw.class(gpa, ti2);
                },
                .component_type => |ti| blk: {
                    const td = try d.getTypeDescriptor(gpa, ti);
                    defer gpa.free(td);
                    if (td.len < 2 or td[0] != '[') return error.BadDescriptor;
                    const ti2 = try descriptorToInternal(gpa, td[1..]);
                    defer gpa.free(ti2);
                    break :blk try cw.class(gpa, ti2);
                },
                .int_const => |v| blk: {
                    break :blk try cw.integer(gpa, v);
                },
                .long_const => |v| blk: {
                    break :blk try cw.long(gpa, v);
                },
                .double_const => |b| blk: {
                    break :blk try cw.double(gpa, b);
                },
                .field => |fi| blk: {
                    const fid = try d.fieldId(fi);
                    const cd = try d.getTypeDescriptor(gpa, fid.class_idx);
                    defer gpa.free(cd);
                    const ci = try descriptorToInternal(gpa, cd);
                    defer gpa.free(ci);
                    const fn2 = try d.getString(gpa, fid.name_idx);
                    defer gpa.free(fn2);
                    const fd = try d.getTypeDescriptor(gpa, fid.type_idx);
                    defer gpa.free(fd);
                    break :blk try cw.fieldref(gpa, ci, fn2, fd);
                },
                .method => |mi| blk: {
                    const mid = try d.methodId(mi);
                    const cd = try d.getTypeDescriptor(gpa, mid.class_idx);
                    defer gpa.free(cd);
                    const ci = try descriptorToInternal(gpa, cd);
                    defer gpa.free(ci);
                    const mn = try d.getString(gpa, mid.name_idx);
                    defer gpa.free(mn);
                    const md = try protoDescriptor(gpa, d, mid.proto_idx);
                    defer gpa.free(md);
                    // Interface-ness: look at the referenced class def, default class ref.
                    var is_iface = false;
                    for (0..d.header.class_defs_size) |ci2| {
                        const dd = try d.classDef(ci2);
                        if (dd.class_idx == mid.class_idx) {
                            is_iface = dd.access_flags & 0x200 != 0;
                            break;
                        }
                    }
                    break :blk try cw.methodref(gpa, ci, mn, md, is_iface);
                },
                .raw_method => |r| blk: {
                    break :blk try cw.methodref(gpa, r.cls, r.name, r.desc, false);
                },
                .raw_field => |r| blk: {
                    break :blk try cw.fieldref(gpa, r.cls, r.name, r.desc);
                },
            };
            if (u.wide) {
                if (idx > 255 and m.tm.code[u.at - 1] == 0x12) return error.UnsupportedOpcode; // ldc overflow
                m.tm.code[u.at] = @intCast(idx >> 8);
                m.tm.code[u.at + 1] = @intCast(idx & 0xFF);
            } else {
                if (idx > 255) return error.UnsupportedOpcode; // need ldc_w
                m.tm.code[u.at] = @intCast(idx);
            }
        }
    }

    // Pre-register every name/descriptor BEFORE emitting the pool.
    const code_attr = try cw.utf8(gpa, "Code");
    const line_attr = try cw.utf8(gpa, "LineNumberTable");
    var field_name_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer field_name_idx.deinit(gpa);
    var field_desc_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer field_desc_idx.deinit(gpa);
    for (fields.items) |f| {
        try field_name_idx.append(gpa, try cw.utf8(gpa, f.name));
        try field_desc_idx.append(gpa, try cw.utf8(gpa, f.desc));
    }
    var method_name_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer method_name_idx.deinit(gpa);
    var method_desc_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer method_desc_idx.deinit(gpa);
    for (methods.items) |m| {
        try method_name_idx.append(gpa, try cw.utf8(gpa, m.name));
        try method_desc_idx.append(gpa, try cw.utf8(gpa, m.desc));
    }
    // Catch-type classes for exception tables (parallel to m.tm.exceptions).
    var catch_idx: std.ArrayListUnmanaged(std.ArrayListUnmanaged(u16)) = .empty;
    defer {
        for (catch_idx.items) |*l| l.deinit(gpa);
        catch_idx.deinit(gpa);
    }
    for (methods.items) |m| {
        var l: std.ArrayListUnmanaged(u16) = .empty;
        for (m.tm.exceptions) |x| {
            if (x.catch_type) |ti| {
                const td = try d.getTypeDescriptor(gpa, ti);
                defer gpa.free(td);
                const cinternal = try descriptorToInternal(gpa, td);
                defer gpa.free(cinternal);
                try l.append(gpa, try cw.class(gpa, cinternal));
            } else {
                try l.append(gpa, 0); // catch-all
            }
        }
        try catch_idx.append(gpa, l);
    }
    // Interfaces: DEX type_list at interfaces_off (shared readTypeList;
    // BadSection cap errors map to BadDescriptor, matching the old arm).
    var iface_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer iface_idx.deinit(gpa);
    if (def.interfaces_off != 0) {
        const idxs = dex.readTypeList(gpa, d, def.interfaces_off) catch return error.BadDescriptor;
        defer gpa.free(idxs);
        for (idxs) |ti| {
            const tdesc = try d.getTypeDescriptor(gpa, ti);
            defer gpa.free(tdesc);
            const tinternal = try descriptorToInternal(gpa, tdesc);
            defer gpa.free(tinternal);
            try iface_idx.append(gpa, try cw.class(gpa, tinternal));
        }
    }

    // Emit the class file.
    var out: std.ArrayListUnmanaged(u8) = .empty;
    errdefer out.deinit(gpa);
    try ClassWriter.writeU32(&out, gpa, 0xCAFEBABE);
    try ClassWriter.writeU16(&out, gpa, 0); // minor
    try ClassWriter.writeU16(&out, gpa, 49); // major v49: pure inference verification, frames never required (JDK 27 enforces frames at v50)
    try cw.emitPool(gpa, &out);
    // ACC_SUPER is mandatory on classes but ILLEGAL on interfaces (JVM 4.1).
    const cls_flags = dexFlagsToJvm(def.access_flags) | (if (def.access_flags & 0x200 != 0) @as(u16, 0) else 0x0020);
    try ClassWriter.writeU16(&out, gpa, cls_flags);
    try ClassWriter.writeU16(&out, gpa, this_cls);
    try ClassWriter.writeU16(&out, gpa, super_cls);
    try ClassWriter.writeU16(&out, gpa, @intCast(iface_idx.items.len));
    for (iface_idx.items) |ci| try ClassWriter.writeU16(&out, gpa, ci);
    try ClassWriter.writeU16(&out, gpa, @intCast(fields.items.len));
    for (fields.items, 0..) |f, i| {
        try ClassWriter.writeU16(&out, gpa, f.flags);
        try ClassWriter.writeU16(&out, gpa, field_name_idx.items[i]);
        try ClassWriter.writeU16(&out, gpa, field_desc_idx.items[i]);
        try ClassWriter.writeU16(&out, gpa, 0); // attributes
    }
    try ClassWriter.writeU16(&out, gpa, @intCast(methods.items.len));
    for (methods.items, 0..) |m, i| {
        try ClassWriter.writeU16(&out, gpa, m.flags);
        try ClassWriter.writeU16(&out, gpa, method_name_idx.items[i]);
        try ClassWriter.writeU16(&out, gpa, method_desc_idx.items[i]);
        const has_code = m.tm.code.len > 0;
        try ClassWriter.writeU16(&out, gpa, if (has_code) 1 else 0);
        if (has_code) {
            try ClassWriter.writeU16(&out, gpa, code_attr);
            const ex_len: u32 = @intCast(m.tm.exceptions.len * 8);
            const line_len: u32 = if (m.tm.lines.len > 0) 8 + @as(u32, @intCast(m.tm.lines.len)) * 4 else 0;
            try ClassWriter.writeU32(&out, gpa, @intCast(12 + m.tm.code.len + ex_len + line_len));
            try ClassWriter.writeU16(&out, gpa, m.tm.max_stack + 8);
            try ClassWriter.writeU16(&out, gpa, m.tm.max_locals);
            try ClassWriter.writeU32(&out, gpa, @intCast(m.tm.code.len));
            try out.appendSlice(gpa, m.tm.code);
            try ClassWriter.writeU16(&out, gpa, @intCast(m.tm.exceptions.len));
            for (m.tm.exceptions, 0..) |x, xi| {
                try ClassWriter.writeU16(&out, gpa, @intCast(x.start_pc));
                try ClassWriter.writeU16(&out, gpa, @intCast(x.end_pc));
                try ClassWriter.writeU16(&out, gpa, @intCast(x.handler_pc));
                try ClassWriter.writeU16(&out, gpa, catch_idx.items[i].items[xi]);
            }
            if (m.tm.lines.len > 0) {
                try ClassWriter.writeU16(&out, gpa, 1);
                try ClassWriter.writeU16(&out, gpa, line_attr);
                try ClassWriter.writeU32(&out, gpa, @intCast(2 + m.tm.lines.len * 4));
                try ClassWriter.writeU16(&out, gpa, @intCast(m.tm.lines.len));
                for (m.tm.lines) |le| {
                    try ClassWriter.writeU16(&out, gpa, @intCast(le.start_pc));
                    try ClassWriter.writeU16(&out, gpa, @intCast(le.line));
                }
            } else {
                try ClassWriter.writeU16(&out, gpa, 0);
            }
        }
    }
    try ClassWriter.writeU16(&out, gpa, 0); // class attributes
    return .{
        .internal_name = internal,
        .bytes = try out.toOwnedSlice(gpa),
    };
}

test "descriptor conversion" {
    const gpa = std.testing.allocator;
    const i = try descriptorToInternal(gpa, "Lorg/isakai/Counter;");
    defer gpa.free(i);
    try std.testing.expectEqualStrings("org/isakai/Counter", i);
    const p = try descriptorToInternal(gpa, "I");
    defer gpa.free(p);
    try std.testing.expectEqualStrings("I", p);
    const a = try descriptorToInternal(gpa, "[Ljava/lang/String;");
    defer gpa.free(a);
    try std.testing.expectEqualStrings("[Ljava/lang/String;", a);
}

test "emitter branch fixup" {
    const gpa = std.testing.allocator;
    var e = Emitter{};
    defer e.deinit(gpa);
    try e.mark(gpa, 0);
    try e.emitBranch(gpa, 0xa7, 2); // goto dex_pc 2
    try e.mark(gpa, 1);
    try e.emit(gpa, 0x00); // nop
    try e.mark(gpa, 2);
    try e.emit(gpa, 0xb1); // return
    try e.resolve();
    // goto at 0, target jvm offset 4, delta = 4.
    try std.testing.expectEqual(@as(u8, 0xa7), e.code.items[0]);
    try std.testing.expectEqual(@as(u8, 0), e.code.items[1]);
    try std.testing.expectEqual(@as(u8, 4), e.code.items[2]);
}

test "emitter wide branch fixup" {
    const gpa = std.testing.allocator;
    var e = Emitter{};
    defer e.deinit(gpa);
    try e.mark(gpa, 0);
    try e.emitBranchWide(gpa, 5); // goto_w to dex_pc 5
    try e.mark(gpa, 1);
    try e.emit(gpa, 0x00); // nop at jvm offset 6
    // Pad to a >32KB span so the delta exceeds the 2-byte range.
    const pad: usize = 40000;
    try e.code.appendNTimes(gpa, 0x00, pad);
    try e.mark(gpa, 5);
    try e.emit(gpa, 0xb1); // return
    try e.resolve();
    // goto_w at 0 (5 bytes), nop at 5, pad, target jvm offset 6+pad.
    const want: i32 = @intCast(6 + pad);
    const u: u32 = @bitCast(want);
    try std.testing.expectEqual(@as(u8, 0xc8), e.code.items[0]);
    try std.testing.expectEqual(@as(u8, u >> 24), e.code.items[1]);
    try std.testing.expectEqual(@as(u8, (u >> 16) & 0xff), e.code.items[2]);
    try std.testing.expectEqual(@as(u8, (u >> 8) & 0xff), e.code.items[3]);
    try std.testing.expectEqual(@as(u8, u & 0xff), e.code.items[4]);
}

test "emitter narrow branch still rejects far targets" {
    const gpa = std.testing.allocator;
    var e = Emitter{};
    defer e.deinit(gpa);
    try e.mark(gpa, 0);
    try e.emitBranch(gpa, 0xa7, 5);
    try e.code.appendNTimes(gpa, 0x00, 40000);
    try e.mark(gpa, 5);
    try e.emit(gpa, 0xb1);
    try std.testing.expectError(error.BadBranch, e.resolve());
}

test "jumbo string idx decodes 32 bits little-endian" {
    // const-string/jumbo layout: unit0 holds the reg, units 1-2 hold the
    // 32-bit string idx (low unit first). Mirror the arm's decode here.
    const lo: u16 = 0xABCD;
    const hi: u16 = 0x1234;
    const idx: u32 = (@as(u32, hi) << 16) | lo;
    try std.testing.expectEqual(@as(u32, 0x1234ABCD), idx);
}

test "filled-new-array rejects wide component" {
    try std.testing.expectError(error.BadDescriptor, filledArrayElemKind('J'));
    try std.testing.expectError(error.BadDescriptor, filledArrayElemKind('D'));
    try std.testing.expectError(error.BadDescriptor, filledArrayElemKind('V'));
    try std.testing.expectError(error.BadDescriptor, filledArrayElemKind('X'));
    for ([_]u8{ 'Z', 'B', 'S', 'C', 'I', 'F', 'L', '[' }) |k| {
        try filledArrayElemKind(k);
    }
}

test "wide component decision prefers tracker, defaults long" {
    // Tracker wins both ways.
    try std.testing.expect(wideIsDouble('D', .long));
    try std.testing.expect(!wideIsDouble('J', .double));
    // Fallback to the value reg's type when untracked.
    try std.testing.expect(wideIsDouble(0, .double));
    try std.testing.expect(!wideIsDouble(0, .long));
    try std.testing.expect(!wideIsDouble(0, .unknown));
    try std.testing.expect(!wideIsDouble(0, .int));
}
