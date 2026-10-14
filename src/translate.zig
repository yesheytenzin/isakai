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
/// JDK interfaces with default methods callable via invoke-super (DEX) /
/// invokespecial (JVM InterfaceMethodref). External classes have no DEX
/// def to consult, so well-known collection interfaces are named here.
fn isJdkInterface(internal: []const u8) bool {
    const ifaces = [_][]const u8{
        "java/util/Map",
        "java/util/List",
        "java/util/Set",
        "java/util/Collection",
        "java/util/Iterator",
        "java/util/ListIterator",
        "java/util/concurrent/ConcurrentMap",
        "java/util/function/Function",
        "java/util/function/BiFunction",
        "java/util/function/Consumer",
        "java/util/function/BiConsumer",
        "java/util/function/Supplier",
        "java/util/function/Predicate",
        "java/lang/Iterable",
        "java/lang/CharSequence",
        "java/lang/Comparable",
        "java/io/Serializable",
        "java/lang/Cloneable",
    };
    for (ifaces) |name| {
        if (std.mem.eql(u8, internal, name)) return true;
    }
    return false;
}

/// Framework helper for an invoke-super to a JDK interface default, or null
/// when the callee is not a covered default. The helper is static with the
/// receiver as its first parameter, so already-emitted args fit as-is.
const SuperHelper = struct { cls: []const u8, name: []const u8, desc: []const u8 };
fn superInterfaceHelper(d: dex.Dex, method_idx: u16) ?SuperHelper {
    const gpa = std.heap.page_allocator;
    const mid = d.methodId(method_idx) catch return null;
    const cd = d.getTypeDescriptor(gpa, mid.class_idx) catch return null;
    defer gpa.free(cd);
    const internal = descriptorToInternal(gpa, cd) catch return null;
    defer gpa.free(internal);
    if (!isJdkInterface(internal)) return null;
    const name = d.getString(gpa, mid.name_idx) catch return null;
    defer gpa.free(name);
    // Only covered defaults get helpers; anything else stays a loud error.
    // Literals: pool uses reference them after this returns (no free).
    if (std.mem.eql(u8, internal, "java/util/Map") and std.mem.eql(u8, name, "getOrDefault")) {
        return SuperHelper{
            .cls = "org/isakai/runtime/InterfaceDefaults",
            .name = "mapGetOrDefault",
            .desc = "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        };
    }
    return null;
}
fn insnSize(op: u8) TranslateError!usize {
    return switch (@as(Op, @fromBackingInt(@intCast(op)))) {
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
    /// Conflict ref-slots: crefs[r] != 0 means DEX reg r is int/ref
    /// conflicted and its ref value lives in JVM local crefs[r] (the
    /// normal mapping holds the int value). Zero = unconflicted.
    crefs: []const u16,
    fn map(self: RegMap, r: u16) u16 {
        if (r < self.table.len) return self.table[r];
        return r; // defensive: malformed reg passes through to verifier
    }
    fn refSlot(self: RegMap, r: u16) u16 {
        if (r < self.crefs.len and self.crefs[r] != 0) return self.crefs[r];
        return self.map(r);
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
    const r = rm.refSlot(rx);
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

/// Load a register in a VALUE position expecting a reference (invoke arg,
/// field/array value, return). d8 encodes null as const/4 0 (tracked
/// .int); verified DEX passing int-as-ref means null at runtime, so
/// emit aconst_null instead of aload (dex2jar parity). Receivers and
/// array refs keep plain emitLoadRef (null there is a loud failure).
fn emitLoadRefOrNull(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, types: []const RegType, rx: u16) !void {
    const conflicted = rx < rm.crefs.len and rm.crefs[rx] != 0;
    if (!conflicted and rx < types.len and types[rx] == .int) {
        try e.emit(gpa, 0x01); // aconst_null
        e.push(1);
    } else try emitLoadRef(e, gpa, rm, rx);
}

fn emitStoreRef(e: *Emitter, gpa: std.mem.Allocator, rm: RegMap, rx: u16) !void {
    const r = rm.refSlot(rx);
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
    alloc_owner: []?[]u8,
    rx: u16,
    kind: RegType,
) !void {
    // NB: stores do NOT clear pending new-instance types. In valid DEX
    // an <init> call's object is always its dominating new (calling init
    // on anything else is a verify error), so the pairing survives reuse
    // stores on skipped paths; only the <init> call itself consumes it,
    // and a later new overwrites (see the new-instance arm).
    _ = alloc_owner;
    switch (kind) {
        .int => {
            try emitStoreInt(e, gpa, rm, rx);
            if (rx < types.len) types[rx] = .int;
        },
        .float => unreachable, // bits-always: producers use emitStoreFloatBits
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
        .double => unreachable, // bits-always: producers use emitStoreDoubleBits
        .unknown => unreachable,
    }
}

/// Kind delivered by a storing opcode's destination: int-ish (int
/// bits, floats-as-bits, wide producers) or ref.
const StoreKind = enum { int, ref };

/// Dest reg + kind of a storing opcode, or null for non-storing ops.
/// Dest extracts mirror the conflict pre-pass and the lowering arms;
/// float/wide producers count as int (bits-always locals).
fn storeDestKind(opcode: Op, unit0: u16, unit1: u16, unit2: u16) ?struct { dst: u16, kind: StoreKind } {
    return switch (opcode) {
        .move => .{ .dst = regA(unit0), .kind = .int },
        .move_from16, .move16 => .{ .dst = moveRegs16(opcode, unit0, unit1, unit2).dst, .kind = .int },
        .move_wide, .move_wide_from16, .move_wide16 => .{
            .dst = if (opcode == .move_wide) regA(unit0) else moveRegs16(opcode, unit0, unit1, unit2).dst,
            .kind = .int,
        },
        .move_object => .{ .dst = regA(unit0), .kind = .ref },
        .move_object_from16, .move_object16 => .{ .dst = moveRegs16(opcode, unit0, unit1, unit2).dst, .kind = .ref },
        .move_result => .{ .dst = reg8(unit0), .kind = .int },
        .move_result_wide => .{ .dst = reg8(unit0), .kind = .int },
        .move_result_object, .move_exception => .{ .dst = reg8(unit0), .kind = .ref },
        .const4 => .{ .dst = regA(unit0), .kind = .int },
        .const16, .const_, .const_high16 => .{ .dst = reg8(unit0), .kind = .int },
        .const_wide16, .const_wide32, .const_wide, .const_wide_high16 => .{ .dst = reg8(unit0), .kind = .int },
        .const_string, .const_string_jumbo, .const_class => .{ .dst = reg8(unit0), .kind = .ref },
        .check_cast, .new_instance => .{ .dst = reg8(unit0), .kind = .ref },
        .new_array => .{ .dst = regA(unit0), .kind = .ref },
        .instance_of, .array_length => .{ .dst = regA(unit0), .kind = .int },
        .aget_object => .{ .dst = regs23x(unit0, unit1).a, .kind = .ref },
        .aget, .aget_boolean, .aget_byte, .aget_char, .aget_short, .aget_wide => .{ .dst = regs23x(unit0, unit1).a, .kind = .int },
        .iget_object => .{ .dst = regA(unit0), .kind = .ref },
        .iget, .iget_boolean, .iget_byte, .iget_char, .iget_short, .iget_wide => .{ .dst = regA(unit0), .kind = .int },
        .sget_object => .{ .dst = reg8(unit0), .kind = .ref },
        .sget, .sget_boolean, .sget_byte, .sget_char, .sget_short, .sget_wide => .{ .dst = reg8(unit0), .kind = .int },
        .neg_int,
        .not_int,
        .long_to_int,
        .float_to_int,
        .double_to_int,
        .int_to_byte,
        .int_to_char,
        .int_to_short,
        .add_int_2addr,
        .sub_int_2addr,
        .mul_int_2addr,
        .div_int_2addr,
        .rem_int_2addr,
        .and_int_2addr,
        .or_int_2addr,
        .xor_int_2addr,
        .shl_int_2addr,
        .shr_int_2addr,
        .ushr_int_2addr,
        .add_int_lit16,
        .sub_int_lit16,
        .mul_int_lit16,
        .div_int_lit16,
        .rem_int_lit16,
        .and_int_lit16,
        .or_int_lit16,
        .xor_int_lit16,
        .add_int_lit8,
        .sub_int_lit8,
        .mul_int_lit8,
        .div_int_lit8,
        .rem_int_lit8,
        .and_int_lit8,
        .or_int_lit8,
        .xor_int_lit8,
        .shl_int_lit8,
        .shr_int_lit8,
        .ushr_int_lit8,
        => .{ .dst = regA(unit0), .kind = .int },
        .cmpl_float,
        .cmpg_float,
        .cmpl_double,
        .cmpg_double,
        .cmp_long,
        .add_int,
        .sub_int,
        .mul_int,
        .div_int,
        .rem_int,
        .and_int,
        .or_int,
        .xor_int,
        .shl_int,
        .shr_int,
        .ushr_int,
        .add_long,
        .sub_long,
        .mul_long,
        .div_long,
        .rem_long,
        .and_long,
        .or_long,
        .xor_long,
        .shl_long,
        .shr_long,
        .ushr_long,
        .add_float,
        .sub_float,
        .mul_float,
        .div_float,
        .rem_float,
        .add_double,
        .sub_double,
        .mul_double,
        .div_double,
        .rem_double,
        => .{ .dst = regs23x(unit0, unit1).a, .kind = .int },
        .neg_long,
        .not_long,
        .int_to_long,
        .float_to_long,
        .double_to_long,
        .add_long_2addr,
        .sub_long_2addr,
        .mul_long_2addr,
        .div_long_2addr,
        .rem_long_2addr,
        .and_long_2addr,
        .or_long_2addr,
        .xor_long_2addr,
        .shl_long_2addr,
        .shr_long_2addr,
        .ushr_long_2addr,
        => .{ .dst = regA(unit0), .kind = .int },
        .neg_float,
        .int_to_float,
        .long_to_float,
        .double_to_float,
        .add_float_2addr,
        .sub_float_2addr,
        .mul_float_2addr,
        .div_float_2addr,
        .rem_float_2addr,
        => .{ .dst = regA(unit0), .kind = .int },
        .neg_double,
        .int_to_double,
        .long_to_double,
        .float_to_double,
        .add_double_2addr,
        .sub_double_2addr,
        .mul_double_2addr,
        .div_double_2addr,
        .rem_double_2addr,
        => .{ .dst = regA(unit0), .kind = .int },
        else => null,
    };
}

/// Param kind of a reg, or null for non-param regs. Float/double params
/// convert to bits in the prologue, so they count as int. Mirrors the
/// prologue/local-table param layout.
fn paramKind(nregs: usize, nins: usize, is_static: bool, param_kinds: []const u8, rx: u16) ?StoreKind {
    var sri: usize = nregs -| nins;
    if (!is_static) {
        if (rx == sri) return .ref; // this
        sri += 1;
    }
    for (param_kinds) |tc| {
        if (sri >= nregs) break;
        const wide = tc == 'J' or tc == 'D';
        if (rx == sri or (wide and rx == sri + 1)) {
            return if (tc == 'L' or tc == '[') .ref else .int;
        }
        sri += 1;
        if (wide) sri += 1;
    }
    return null;
}

/// Param-list index of a reg, or null for `this`/non-param regs. Mirrors
/// paramKind's layout walk (wide halves map to the same index).
fn paramRegIndex(nregs: usize, nins: usize, is_static: bool,
    param_kinds: []const u8, rx: u16) ?usize {
    var sri: usize = nregs -| nins;
    if (!is_static) {
        if (rx == sri) return null; // this
        sri += 1;
    }
    for (param_kinds, 0..) |tc, pi| {
        if (sri >= nregs) break;
        const wide = tc == 'J' or tc == 'D';
        if (rx == sri or (wide and rx == sri + 1)) return pi;
        sri += 1;
        if (wide) sri += 1;
    }
    return null;
}

/// True when reg holds `this` (directly or via a move chain from it).
fn receiverIsThis(insns: []const u16, call_pc: usize, rx: u16,
    nregs: usize, nins: usize, is_static: bool) bool {
    if (is_static or nins == 0 or nregs < nins) return false;
    const this_reg: u16 = @intCast(nregs - nins);
    if (rx == this_reg) return true;
    return isThisAlias(insns, call_pc, rx, this_reg);
}

/// True when a param reg's descriptor names the current class (protected
/// calls through it must keep the subclass type; narrowing to a super
/// owner breaks verification).
fn paramIsThisType(gpa: std.mem.Allocator, d: dex.Dex, parameters_off: u32,
    param_index: usize, this_internal: []const u8) bool {
    const tids = dex.readTypeList(gpa, d, parameters_off) catch return false;
    defer if (tids.len > 0) gpa.free(tids);
    if (param_index >= tids.len) return false;
    const desc = d.getTypeDescriptor(gpa, tids[param_index]) catch return false;
    defer gpa.free(desc);
    if (desc.len < 3 or desc[0] != 'L' or desc[desc.len - 1] != ';') return false;
    return std.mem.eql(u8, desc[1 .. desc.len - 1], this_internal);
}

/// True when every backward path from a conflicted read of rx (over the
/// precomputed preds) reaches an int-ish store first: then every path
/// delivers int and the INT form verifies. Any ref store (or ref param)
/// first on any path means a {0,ref} merge in valid DEX, so the answer
/// is false and the caller uses the REF form (0/null agree on ifz).
/// Uninitialized paths (no store back to entry) wildcard: the prologue
/// zeroes both slots, so either form reads a consistent zero value.
/// Linear snapshots can't answer this: a single fallthrough pred may
/// itself be a join whose linear state ignores a jumping-in path.
/// Visited-set DFS: each unit scanned once, so O(units) per query and
/// always terminating (loops revisited contribute no new kinds).
fn reachingDefsAllInt(
    gpa: std.mem.Allocator,
    insns: []const u16,
    preds: []const std.ArrayListUnmanaged(u32),
    nregs: usize,
    nins: usize,
    is_static: bool,
    param_kinds: []const u8,
    use_pc: usize,
    rx: u16,
) bool {
    if (use_pc >= preds.len or rx >= nregs) return false;
    if (preds[use_pc].items.len == 0) {
        // No in-method preds: the value is the entry value (param or
        // prologue zero). INT iff an int param; uninit wildcards to REF
        // (prologue null in the ref slot).
        return paramKind(nregs, nins, is_static, param_kinds, rx) == .int;
    }
    const visited = gpa.alloc(bool, insns.len) catch return false;
    defer gpa.free(visited);
    @memset(visited, false);
    var stack = gpa.alloc(u32, insns.len) catch return false;
    defer gpa.free(stack);
    var top: usize = 0;
    for (preds[use_pc].items) |p| {
        if (p < insns.len and !visited[p]) {
            visited[p] = true;
            stack[top] = p;
            top += 1;
        }
    }
    var saw_int = false;
    while (top > 0) {
        top -= 1;
        const pc: usize = stack[top];
        const unit0 = insns[pc];
        const op: Op = @fromBackingInt(@intCast(@as(u8, @intCast(unit0 & 0xFF))));
        const w1: u16 = if (pc + 1 < insns.len) insns[pc + 1] else 0;
        const w2: u16 = if (pc + 2 < insns.len) insns[pc + 2] else 0;
        if (storeDestKind(op, unit0, w1, w2)) |st| {
            if (st.dst == rx) {
                if (st.kind == .ref) return false;
                saw_int = true;
                continue; // this path delivers int; stop it here
            }
        }
        const no_preds = pc >= preds.len or preds[pc].items.len == 0;
        if (!no_preds) {
            for (preds[pc].items) |p| {
                if (p < insns.len and !visited[p]) {
                    visited[p] = true;
                    stack[top] = p;
                    top += 1;
                }
            }
        } else if (pc == 0) {
            // True method entry reached: the entry value (param kind)
            // decides this path. Ref params veto INT (a {0,ref} merge
            // reads as ref); int params vote int; non-params wildcard
            // (prologue zeroes both slots).
            if (paramKind(nregs, nins, is_static, param_kinds, rx)) |pk| {
                if (pk == .ref) return false;
                saw_int = true;
            }
        }
        // Else: dead unit with no preds (after return/throw/goto):
        // contributes nothing (live paths decide; all-dead falls
        // through to the entry fallback below).
    }
    // Units with preds but no reaching store (e.g. straight-line code
    // from entry): fall back to the entry value.
    if (!saw_int) {
        if (paramKind(nregs, nins, is_static, param_kinds, rx)) |pk| {
            return pk == .int;
        }
        return false; // uninit: REF (prologue null)
    }
    return true;
}

/// Array component kind reaching an aget/aput use, or 0 when unknown or
/// disagreed. Backward DFS like reachingDefsAllInt: linear wide_comp is
/// clobbered by stores on paths that never reach the read (e.g. a
/// `new IllegalArgumentException` into the same reg on a throwing branch
/// before a float[] read). Sources: new-array (descriptor element),
/// move-object chains (recurse), check-cast to [X, param [X seeding.
/// move-result/iget/sget/aget-object/new-instance yield unknown (0);
/// callers fall back to the linear tracker, then int.
fn reachingArrayComponent(
    gpa: std.mem.Allocator,
    d: dex.Dex,
    insns: []const u16,
    preds: []const std.ArrayListUnmanaged(u32),
    nregs: usize,
    nins: usize,
    is_static: bool,
    param_kinds: []const u8,
    parameters_off: u32,
    use_pc: usize,
    rx: u16,
) u8 {
    return reachingArrayComponentDepth(gpa, d, insns, preds, nregs, nins,
        is_static, param_kinds, parameters_off, use_pc, rx, 0);
}

fn reachingArrayComponentDepth(
    gpa: std.mem.Allocator,
    d: dex.Dex,
    insns: []const u16,
    preds: []const std.ArrayListUnmanaged(u32),
    nregs: usize,
    nins: usize,
    is_static: bool,
    param_kinds: []const u8,
    parameters_off: u32,
    use_pc: usize,
    rx: u16,
    depth: u8,
) u8 {
    if (depth > 8) return 0;
    if (use_pc >= preds.len or rx >= nregs) return 0;
    const visited = gpa.alloc(bool, insns.len) catch return 0;
    defer gpa.free(visited);
    @memset(visited, false);
    const stack = gpa.alloc(u32, insns.len) catch return 0;
    defer gpa.free(stack);
    var top: usize = 0;
    for (preds[use_pc].items) |p| {
        if (p < insns.len and !visited[p]) {
            visited[p] = true;
            stack[top] = p;
            top += 1;
        }
    }
    var found: u8 = 0;
    var saw_unknown = false;
    while (top > 0) {
        top -= 1;
        const pc: usize = stack[top];
        const unit0 = insns[pc];
        const op: Op = @fromBackingInt(@intCast(@as(u8, @intCast(unit0 & 0xFF))));
        const w1: u16 = if (pc + 1 < insns.len) insns[pc + 1] else 0;
        const w2: u16 = if (pc + 2 < insns.len) insns[pc + 2] else 0;
        if (storeDestKind(op, unit0, w1, w2)) |st| {
            if (st.dst == rx) {
                const comp: u8 = switch (op) {
                    .new_array => blk: {
                        const td = d.getTypeDescriptor(gpa, w1) catch "";
                        defer if (td.len > 0) gpa.free(td);
                        break :blk if (td.len == 2 and td[0] == '[') td[1] else 0;
                    },
                    .check_cast => blk: {
                        const td = d.getTypeDescriptor(gpa, w1) catch "";
                        defer if (td.len > 0) gpa.free(td);
                        break :blk if (td.len == 2 and td[0] == '[') td[1] else 0;
                    },
                    .move_object, .move_object_from16, .move_object16 => {
                        // Recurse into the source on this path: handled by
                        // continuing the walk with rx := src below.
                        const src: u16 = if (op == .move_object) regB(unit0) else blk2: {
                            const mr = moveRegs16(op, unit0, w1, w2);
                            break :blk2 if (mr.src > 65535) rx else @as(u16, @intCast(mr.src));
                        };
                        if (src == rx) {
                            saw_unknown = true;
                            continue;
                        }
                        const sub = reachingArrayComponentDepth(gpa, d, insns, preds, nregs, nins,
                            is_static, param_kinds, parameters_off, pc, src, depth + 1);
                        if (sub == 0) {
                            saw_unknown = true;
                            continue;
                        }
                        if (found == 0) {
                            found = sub;
                        } else if (found != sub) {
                            return 0;
                        }
                        continue;
                    },
                    else => 0,
                };
                if (comp == 0) {
                    saw_unknown = true;
                    continue;
                }
                if (found == 0) {
                    found = comp;
                } else if (found != comp) {
                    return 0;
                }
                continue;
            }
        }
        const no_preds = pc >= preds.len or preds[pc].items.len == 0;
        if (!no_preds) {
            for (preds[pc].items) |p| {
                if (p < insns.len and !visited[p]) {
                    visited[p] = true;
                    stack[top] = p;
                    top += 1;
                }
            }
        } else if (pc == 0) {
            // Entry: param [X seeds the component.
            if (paramRegIndex(nregs, nins, is_static, param_kinds, rx)) |pi| {
                const tids = dex.readTypeList(gpa, d, parameters_off) catch &.{};
                defer if (tids.len > 0) gpa.free(tids);
                if (pi < tids.len) {
                    const pd = d.getTypeDescriptor(gpa, tids[pi]) catch null;
                    if (pd) |desc| {
                        defer gpa.free(desc);
                        if (desc.len >= 2 and desc[0] == '[') {
                            if (found == 0) {
                                found = desc[1];
                            } else if (found != desc[1]) {
                                return 0;
                            }
                            continue;
                        }
                    }
                }
            }
            saw_unknown = true;
        }
    }
    if (found != 0 and !saw_unknown) return found;
    if (found != 0) return found;
    return 0;
}

/// Store an int literal. Conflicted regs dual-write const-0 (0 to the
/// int slot, null to the ref slot): verified DEX passing int-as-ref can
/// only be null, and every use-kind then reads a consistent value from
/// its slot.
fn storeConstInt(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    types: []RegType,
    wide_comp: []u8,
    alloc_owner: []?[]u8,
    uses: *std.ArrayListUnmanaged(ConstUse),
    rx: u16,
    lit: i32,
    pool: bool,
) !void {
    const conflicted = rx < rm.crefs.len and rm.crefs[rx] != 0;
    if (pool) try emitConstIntPool(e, gpa, uses, lit) else try emitConstInt(e, gpa, lit);
    try storeTracked(e, gpa, rm, types, wide_comp, alloc_owner, rx, .int);
    if (lit == 0 and conflicted) {
        try e.emit(gpa, 0x01); // aconst_null
        e.push(1);
        try emitStoreRef(e, gpa, rm, rx);
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
    alloc_owner: []?[]u8,
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
            if (is_f) {
                try emitStoreFloatBits(e, gpa, rm, uses, types, wide_comp, alloc_owner, reg);
            } else if (is_d) {
                try emitStoreDoubleBits(e, gpa, rm, uses, types, wide_comp, alloc_owner, reg);
            } else {
                try storeTracked(e, gpa, rm, types, wide_comp, alloc_owner, reg, kind);
            }
            // Record array component for later aget/aput (AFTER the store,
            // which clears wide_comp for refs).
            if (is_ref and reg < wide_comp.len) {
                const fid = try d.fieldId(field_idx);
                const fd = try d.getTypeDescriptor(gpa, fid.type_idx);
                defer gpa.free(fd);
                if (fd.len == 2 and fd[0] == '[') wide_comp[reg] = fd[1];
            }
        },
        0xb2 => { // getstatic: push value
            try emitPoolRef(e, gpa, uses, jop, if (wide) 2 else 1, .{ .field = field_idx });
            if (is_f) {
                try emitStoreFloatBits(e, gpa, rm, uses, types, wide_comp, alloc_owner, reg);
            } else if (is_d) {
                try emitStoreDoubleBits(e, gpa, rm, uses, types, wide_comp, alloc_owner, reg);
            } else {
                try storeTracked(e, gpa, rm, types, wide_comp, alloc_owner, reg, kind);
            }
            if (is_ref and reg < wide_comp.len) {
                const fid = try d.fieldId(field_idx);
                const fd = try d.getTypeDescriptor(gpa, fid.type_idx);
                defer gpa.free(fd);
                if (fd.len == 2 and fd[0] == '[') wide_comp[reg] = fd[1];
            }
        },
        0xb5 => { // putfield: pop ref+value
            try emitLoadRef(e, gpa, rm, obj.?);
            if (wide and !is_d) try emitLoadWide(e, gpa, rm, reg, false) else if (wide) try emitLoadDoubleOperand(e, gpa, rm, uses, types, reg) else if (is_ref) try emitLoadRefOrNull(e, gpa, rm, types, reg) else if (is_f) try emitLoadFloatOperand(e, gpa, rm, uses, types, reg) else try emitLoadInt(e, gpa, rm, reg);
            try emitPoolRef(e, gpa, uses, jop, if (wide) -3 else -2, .{ .field = field_idx });
        },
        0xb3 => { // putstatic: pop value
            if (wide and !is_d) try emitLoadWide(e, gpa, rm, reg, false) else if (wide) try emitLoadDoubleOperand(e, gpa, rm, uses, types, reg) else if (is_ref) try emitLoadRefOrNull(e, gpa, rm, types, reg) else if (is_f) try emitLoadFloatOperand(e, gpa, rm, uses, types, reg) else try emitLoadInt(e, gpa, rm, reg);
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

/// Shared float/double operand loader: locals always hold int/long
/// bits (never real floats/doubles), so loads reinterpret via
/// intBitsToFloat/longBitsToDouble. Net stack effect: +1 (float) / +2.
fn emitLoadFloatishOperand(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    rx: u16,
    is_double: bool,
) !void {
    _ = types;
    if (is_double) {
        // Locals always hold long bits (never real doubles).
        try emitLoadWide(e, gpa, rm, rx, false);
        try emitPoolRef(e, gpa, uses, 0xb8, 0, .{ .raw_method = .{
            .cls = "java/lang/Double",
            .name = "longBitsToDouble",
            .desc = "(J)D",
        } });
    } else {
        // Locals always hold int bits (never real floats).
        try emitLoadInt(e, gpa, rm, rx);
        try emitPoolRef(e, gpa, uses, 0xb8, 0, .{ .raw_method = .{
            .cls = "java/lang/Float",
            .name = "intBitsToFloat",
            .desc = "(I)F",
        } });
    }
}

/// Store the float on stack as int bits (locals never hold real
/// floats: int/float slot conflicts vanish; loads reinterpret).
fn emitStoreFloatBits(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    wide_comp: []u8,
    alloc_owner: []?[]u8,
    rx: u16,
) !void {
    try emitPoolRef(e, gpa, uses, 0xb8, 0, .{ .raw_method = .{
        .cls = "java/lang/Float",
        .name = "floatToRawIntBits",
        .desc = "(F)I",
    } });
    try storeTracked(e, gpa, rm, types, wide_comp, alloc_owner, rx, .int);
    // Value-kind marker (slot stays int; guides aput/filled-array op choice).
    if (rx < types.len) types[rx] = .float;
}

/// Store the double on stack as long bits (locals never hold real
/// doubles; loads reinterpret).
fn emitStoreDoubleBits(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    uses: *std.ArrayListUnmanaged(ConstUse),
    types: []RegType,
    wide_comp: []u8,
    alloc_owner: []?[]u8,
    rx: u16,
) !void {
    try emitPoolRef(e, gpa, uses, 0xb8, 0, .{ .raw_method = .{
        .cls = "java/lang/Double",
        .name = "doubleToRawLongBits",
        .desc = "(D)J",
    } });
    try storeTracked(e, gpa, rm, types, wide_comp, alloc_owner, rx, .long);
    // Value-kind marker (slot stays long; guides aput-wide op choice).
    if (rx < types.len) types[rx] = .double;
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
    // 22x: A is a full byte (vAA), B is 16-bit. (NOT a 4-bit nibble.)
    if (from16) return .{ .src = unit1, .dst = reg8(unit0) };
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
    method: struct { idx: u32, iface: bool }, // dex method idx + force-interface (invoke-interface always true)
    init: struct { idx: u32, owner: []const u8 }, // dex method idx + JVM owner of the new-instance type (duped, freed with uses)
    super_init: struct { idx: u32, owner: []const u8 }, // dex method idx + DIRECT SUPER owner for this/super <init> (duped, freed with uses)
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
    rx: u16,
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
    rx: u16,
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
        for (self.uses) |u| {
            if (u.ref == .init) gpa.free(@constCast(u.ref.init.owner));
            if (u.ref == .super_init) gpa.free(@constCast(u.ref.super_init.owner));
        }
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
    types: []RegType,
    uses: *std.ArrayListUnmanaged(ConstUse),
    jop: u8,
    this_internal: []const u8,
    super_internal: ?[]const u8,
    nregs: usize,
    nins: usize,
    caller_param_kinds: []const u8,
    caller_parameters_off: u32,
    insns: []const u16,
    call_pc: usize,
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
        // JVM verification needs the receiver's static type assignable to
        // the callee owner; DEX carries no such constraint (a param typed
        // as an interface may hold the implementing class). Narrow with a
        // checkcast so e.g. an (La9/d;) param dispatches to Lu9/g;.D()V.
        // Skipped when narrowing is wrong or unneeded:
        // - invokespecial: non-<init> invokespecial requires a receiver
        //   assignable to the CURRENT class; narrowing to the super owner
        //   breaks that (and <init> receivers are uninitialized).
        // - `this`-family receivers: already the current class; narrowing
        //   would break protected member access from subclasses.
        // - callee owner is the current class or its direct super: the
        //   receiver is `this`-shaped in every working DEX path (a
        //   protected super call through an unrelated static type would
        //   fail on Android too), and narrowing a current-class receiver
        //   to the super breaks protected access verification.
        var skip = jop == 0xb7 or receiverIsThis(insns, call_pc, regs[0], nregs, nins, is_static);
        if (!skip) {
            const odesc = try d.getTypeDescriptor(gpa, mid.class_idx);
            defer gpa.free(odesc);
            const owner_internal = try descriptorToInternal(gpa, odesc);
            defer gpa.free(owner_internal);
            if (std.mem.eql(u8, owner_internal, this_internal)) {
                skip = true;
            } else if (super_internal) |sup| {
                if (std.mem.eql(u8, owner_internal, sup)) skip = true;
            } else {
                if (std.mem.eql(u8, owner_internal, "java/lang/Object")) skip = true;
            }
        }
        if (!skip) {
            // A param already typed as the current class must also keep its
            // type (protected access needs the subclass, not the owner).
            if (paramRegIndex(nregs, nins, is_static, caller_param_kinds, regs[0])) |pi| {
                skip = paramIsThisType(gpa, d, caller_parameters_off, pi, this_internal);
            }
        }
        if (!skip) {
            try emitPoolRef(e, gpa, uses, 0xc0, 0, .{ .type = mid.class_idx });
        }
        ri = 1;
    }
    for (param_types) |tc| {
        const r = regs[ri];
        ri += 1;
        switch (tc) {
            'Z', 'B', 'S', 'C', 'I' => try emitLoadInt(e, gpa, rm, r),
            'F' => try emitLoadFloatOperand(e, gpa, rm, uses, types, r),
            'J' => {
                try emitLoadWide(e, gpa, rm, r, false);
                ri += 1; // wide consumes two DEX registers
            },
            'D' => {
                try emitLoadDoubleOperand(e, gpa, rm, uses, types, r);
                ri += 1; // wide consumes two DEX registers
            },
            'L', '[' => try emitLoadRefOrNull(e, gpa, rm, types, r),
            else => return error.BadDescriptor,
        }
    }
}

/// An inherited ctor invoked via new+init on a child that may lack it:
/// the JVM requires invokespecial <init> to name the declaring class AND
/// requires new+init to name the allocation type, so the child (and any
/// intermediate ancestors up to the declarer) needs a synthesized forward
/// `ctor(params): aload_0, loads..., invokespecial super.<init>, return`.
pub const ForwardNeed = struct {
    child: []const u8, // internal name of the allocation type (duped)
    desc: []const u8, // JVM method descriptor, e.g. (I)V (duped)
};

/// Collected across a DEX translation run; drained by the forward pass.
pub const ForwardNeeds = struct {
    needs: std.ArrayListUnmanaged(ForwardNeed) = .empty,

    pub fn deinit(self: *ForwardNeeds, gpa: std.mem.Allocator) void {
        for (self.needs.items) |n| {
            gpa.free(n.child);
            gpa.free(n.desc);
        }
        self.needs.deinit(gpa);
    }

    pub fn add(self: *ForwardNeeds, gpa: std.mem.Allocator, child: []const u8, desc: []const u8) !void {
        for (self.needs.items) |n| {
            if (std.mem.eql(u8, n.child, child) and std.mem.eql(u8, n.desc, desc)) return;
        }
        try self.needs.append(gpa, .{
            .child = try gpa.dupe(u8, child),
            .desc = try gpa.dupe(u8, desc),
        });
    }
};

/// Shared invoke lowering: args + pool ref + callee ret-kind + interface
/// tail. regs is the already-decoded arg register list.
/// True when reg r at call pc holds `ctor_this` via a backward chain
/// of register moves (R8 copies this to append/preset ctor args, as in
/// i9/j.<init> calling super through v0). Bounded straight-line scan:
/// any branch opcode, branch payload, or non-move store to the tracked
/// reg ends the chain (conservative: unknown means not-this).
fn isThisAlias(insns: []const u16, call_pc: usize, r: u16, ctor: u16) bool {
    if (r == ctor) return true;
    var cur = r;
    var q = call_pc;
    var steps: usize = 0;
    while (q > 0 and steps < 32) {
        // Step to the previous instruction start by size-walking from a
        // bounded rewind (units are 1-3 wide; payloads excluded below).
        var start: ?usize = null;
        const k: usize = if (q >= 4) q - 4 else 0;
        // Walk sizes forward from k to find starts.
        var qs: [5]usize = undefined;
        var nq: usize = 0;
        var w: usize = k;
        while (w < q and nq < qs.len) {
            const wop: u8 = @intCast(insns[w] & 0xFF);
            const wsz = insnSize(wop) catch break;
            if (wsz == 0 or w + wsz > insns.len) break;
            qs[nq] = w;
            nq += 1;
            w += wsz;
        }
        if (w != q or nq == 0) return false;
        start = qs[nq - 1];
        const pc = start.?;
        const unit0 = insns[pc];
        const op: Op = @fromBackingInt(@intCast(@as(u8, @intCast(unit0 & 0xFF))));
        switch (op) {
            .move, .move_object, .move_wide => {
                if (regA(unit0) == cur) {
                    cur = regB(unit0);
                    if (cur == ctor) return true;
                } else if (regB(unit0) == cur) {
                    // Source clobbered without our dest: chain intact.
                } else {
                    // Unrelated move: keep scanning past it.
                }
            },
            .move_from16, .move_object_from16, .move_wide_from16, .move16, .move_wide16, .move_object16 => {
                if (pc + 2 >= insns.len) return false;
                const mr = moveRegs16(op, unit0, insns[pc + 1], insns[pc + 2]);
                if (mr.dst == cur) {
                    if (mr.src > 65535) return false;
                    cur = @intCast(mr.src);
                    if (cur == ctor) return true;
                }
            },
            .goto_,
            .goto16,
            .goto32,
            .if_eq,
            .if_ne,
            .if_lt,
            .if_ge,
            .if_gt,
            .if_le,
            .if_eqz,
            .if_nez,
            .if_ltz,
            .if_gez,
            .if_gtz,
            .if_lez,
            .return_void,
            .return_,
            .return_wide,
            .return_object,
            .throw_,
            .packed_switch,
            .sparse_switch,
            .fill_array_data,
            => return false,
            else => {
                // Any other store to cur breaks the chain; anything else
                // is transparent.
                if (storeDestKind(op, unit0, if (pc + 1 < insns.len) insns[pc + 1] else 0, if (pc + 2 < insns.len) insns[pc + 2] else 0)) |st| {
                    if (st.dst == cur) return false;
                }
            },
        }
        q = pc;
        steps += 1;
    }
    return false;
}

fn emitInvokeCall(
    e: *Emitter,
    gpa: std.mem.Allocator,
    rm: RegMap,
    d: dex.Dex,
    uses: *std.ArrayListUnmanaged(ConstUse),
    last_ret_kind: *u8,
    last_ret_comp: *u8,
    jop: u8,
    method_idx: u16,
    regs: []const u16,
    is_static: bool,
    is_interface: bool,
    discard_result: bool,
    types: []RegType,
    alloc_owner: []?[]u8,
    super_internal: ?[]const u8,
    this_internal: []const u8,
    ctor_this: ?u16,
    forward_needs: ?*ForwardNeeds,
    insns: []const u16,
    call_pc: usize,
    nregs: usize,
    nins: usize,
    caller_param_kinds: []const u8,
    caller_parameters_off: u32,
) TranslateError!void {
    try emitInvokeArgs(e, gpa, rm, d, method_idx, regs, is_static, types, uses, jop,
        this_internal, super_internal, nregs, nins, caller_param_kinds, caller_parameters_off, insns, call_pc);
    // Uninitialized-instance <init>: name the ALLOCATION type (R8 may
    // declare a supertype owner, which the JVM rejects). Any <init>
    // call consumes the pending allocation.
    const cmid0 = try d.methodId(method_idx);
    var init_ref: ?ConstRef = null;
    if (!is_static and regs.len > 0 and jop == 0xb7) {
        const oname = try d.getString(gpa, cmid0.name_idx);
        defer gpa.free(oname);
        if (std.mem.eql(u8, oname, "<init>")) {
            const obj = regs[0];
            if (obj < alloc_owner.len) {
                if (alloc_owner[obj]) |own| {
                    init_ref = .{ .init = .{ .idx = method_idx, .owner = try gpa.dupe(u8, own) } };
                    // The allocation type must DECLARE the ctor (JVM
                    // resolves <init> without a superclass search); when
                    // it only inherits it (or R8 stripped the trivial
                    // self-named one), a forward gets synthesized.
                    // Recorded unconditionally (even self-named): the
                    // planner skips declared protos.
                    if (forward_needs) |fn_| {
                        const cmid = try d.methodId(method_idx);
                        const mdesc = try protoDescriptor(gpa, d, cmid.proto_idx);
                        defer gpa.free(mdesc);
                        try fn_.add(gpa, own, mdesc);
                    }
                }
                if (alloc_owner[obj]) |old| gpa.free(old);
                alloc_owner[obj] = null;
            }
            // No pending allocation: this()/super() call inside a ctor
            // (obj is `this` or a this-alias: R8 copies this to append
            // args; anything else is a new+init sequence that lost alloc
            // tracking, which keeps its declared owner).
            // The JVM accepts only the current class or direct super as
            // owner; R8 may name a skipped (trivial) ancestor. Redirect
            // skipped ancestors to the direct super (same proto).
            // The EMITTED owner must DECLARE the ctor in every case, so
            // record unconditionally (planner skips declared protos).
            const is_this_call = if (ctor_this) |ct| isThisAlias(insns, call_pc, regs[0], ct) else false;
            if (init_ref == null and is_this_call) {
                const cdesc = try d.getTypeDescriptor(gpa, cmid0.class_idx);
                defer gpa.free(cdesc);
                const cown = try descriptorToInternal(gpa, cdesc);
                defer gpa.free(cown);
                const super_is_object = super_internal == null;
                const is_this = std.mem.eql(u8, cown, this_internal);
                const is_super = (!super_is_object and std.mem.eql(u8, cown, super_internal.?)) or
                    (super_is_object and std.mem.eql(u8, cown, "java/lang/Object"));
                var need_owner: ?[]const u8 = null;
                // Redirect only genuine this calls (lost-tracking new+init
                // keeps its declared owner; rewriting those would break
                // the uninitialized-instance owner match).
                if (!is_this and !is_super) {
                    const sup: []const u8 = if (super_internal) |si| si else "java/lang/Object";
                    init_ref = .{ .super_init = .{ .idx = method_idx, .owner = try gpa.dupe(u8, sup) } };
                    need_owner = sup;
                } else {
                    need_owner = cown;
                }
                if (forward_needs) |fn_| {
                    if (need_owner) |no| {
                        const cmid = try d.methodId(method_idx);
                        const mdesc = try protoDescriptor(gpa, d, cmid.proto_idx);
                        defer gpa.free(mdesc);
                        try fn_.add(gpa, no, mdesc);
                    }
                }
            }
        }
    }
    if (init_ref) |r| {
        try emitPoolRef(e, gpa, uses, jop, -@as(i32, @intCast(regs.len)), r);
    } else if (jop == 0xb7) {
        if (superInterfaceHelper(d, method_idx)) |helper| {
            // invoke-super to a JDK interface default: V49 cannot express
            // invokespecial+InterfaceMethodref, and invokeinterface would
            // dispatch virtually to the override. Route through a framework
            // static helper with identical semantics (args already on stack:
            // receiver first, exactly the helper's parameter order).
            try emitPoolRef(e, gpa, uses, 0xb8, -@as(i32, @intCast(regs.len)),
                .{ .raw_method = .{ .cls = helper.cls, .name = helper.name, .desc = helper.desc } });
        } else {
            try emitPoolRef(e, gpa, uses, jop, -@as(i32, @intCast(regs.len)), .{ .method = .{ .idx = method_idx, .iface = is_interface } });
        }
    } else {
        try emitPoolRef(e, gpa, uses, jop, -@as(i32, @intCast(regs.len)), .{ .method = .{ .idx = method_idx, .iface = is_interface } });
    }
    const cmid = try d.methodId(method_idx);
    const cproto = try d.protoId(cmid.proto_idx);
    const cret = try d.getTypeDescriptor(gpa, cproto.return_type_idx);
    defer gpa.free(cret);
    last_ret_kind.* = if (cret.len > 0) cret[0] else 'V';
    last_ret_comp.* = if (cret.len == 2 and cret[0] == '[') cret[1] else 0;
    if (is_interface) {
        try e.emit(gpa, @intCast(regs.len));
        try e.emit(gpa, 0);
    }
    if (cret.len > 0 and cret[0] != 'V') {
        const wide = cret[0] == 'J' or cret[0] == 'D';
        // DEX drops uncollected results silently; JVM needs pop/pop2.
        if (discard_result) {
            try e.emit(gpa, if (wide) 0x58 else 0x57); // pop2 / pop
        } else {
            e.push(if (wide) 2 else 1);
        }
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
    /// Direct superclass internal name (for super-<init> redirect), plus
    /// the current class internal name. Null supers mean java/lang/Object.
    super_internal: ?[]const u8,
    this_internal: []const u8,
    /// Some(this_reg) iff the current method is a non-static <init>
    /// (gates the super-<init> redirect to genuine this/super calls).
    ctor_this: ?u16,
    /// Collect inherited-ctor forward needs (null to skip; tests).
    forward_needs: ?*ForwardNeeds,
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
    // Conflict pre-pass: regs ever stored as int AND ever stored as ref
    // get dual JVM locals (int value in the normal slot, ref value in an
    // appended ref-slot). Seeded from param types so reused param regs
    // split too. Needs payload ranges: computed below, so this block
    // runs after the payload pre-scan (see conflictBlock).
    const ever_int = gpa.alloc(bool, code.registers) catch return error.OutOfMemory;
    defer gpa.free(ever_int);
    @memset(ever_int, false);
    const ever_ref = gpa.alloc(bool, code.registers) catch return error.OutOfMemory;
    defer gpa.free(ever_ref);
    @memset(ever_ref, false);
    const crefs = gpa.alloc(u16, code.registers) catch return error.OutOfMemory;
    defer gpa.free(crefs);
    @memset(crefs, 0);
    var nconflicted: u16 = 0;
    // Filled after the payload scan; rm/max_locals finalize there too.
    var rm = RegMap{ .table = local_table, .crefs = crefs };
    var max_locals: u16 = param_slots + code.registers - code.ins + 1;
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
    // Seed array components for array-typed params (aget/aput pick
    // iaload/faload by this; unseeded float[] params miscompile to
    // iaload and fail verification).
    {
        const tids = dex.readTypeList(gpa, d, proto.parameters_off) catch &.{};
        defer if (tids.len > 0) gpa.free(tids);
        var pri: usize = code.registers - code.ins;
        if (!is_static) pri += 1;
        for (param_kinds, 0..) |tc, pi| {
            if (pri >= code.registers or pi >= tids.len) break;
            if (tc == '[') {
                const pd = d.getTypeDescriptor(gpa, tids[pi]) catch null;
                if (pd) |desc| {
                    defer gpa.free(desc);
                    if (desc.len >= 2 and desc[0] == '[') wide_comp[pri] = desc[1];
                }
            }
            pri += 1;
            if ((tc == 'J' or tc == 'D') and pri < code.registers) pri += 1;
        }
    }
    // new-instance allocation types per reg (JVM owner, duped): the
    // invoke-direct <init> call on an uninitialized instance must name
    // the ALLOCATION type, but R8 may declare a supertype owner.
    // Cleared on any other store via trackAlloc below.
    const alloc_owner = gpa.alloc(?[]u8, code.registers) catch return error.OutOfMemory;
    defer {
        for (alloc_owner) |o| if (o) |s| gpa.free(s);
        gpa.free(alloc_owner);
    }
    @memset(alloc_owner, null);
    // Type snapshots after each unit (single-pred restore): flat
    // [nins][registers] for types + wide_comp. Methods are small.
    const snap_types = gpa.alloc(RegType, code.insns.len * @as(usize, code.registers)) catch return error.OutOfMemory;
    defer gpa.free(snap_types);
    const snap_wide = gpa.alloc(u8, code.insns.len * @as(usize, code.registers)) catch return error.OutOfMemory;
    defer gpa.free(snap_wide);
    // First descriptor char of the last invoke's return (for move-result).
    var last_ret_kind: u8 = 'V';
    var last_ret_comp: u8 = 0;
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
    errdefer {
        for (uses.items) |u| {
            if (u.ref == .init) gpa.free(@constCast(u.ref.init.owner));
            if (u.ref == .super_init) gpa.free(@constCast(u.ref.super_init.owner));
        }
        uses.deinit(gpa);
    }

    var pc: usize = 0;
    const insns = code.insns;
    // Pre-scan: switch/fill-array payloads live in the insns stream as
    // data; collect their ranges so the walk skips them (they would
    // otherwise decode as garbage opcodes).
    const PayloadRange = struct { start: usize, end: usize };
    var payloads: std.ArrayListUnmanaged(PayloadRange) = .empty;
    defer payloads.deinit(gpa);
    // (Pred lists are built in a later pre-pass once payloads are known.)
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
            const sopcode: Op = @fromBackingInt(@intCast(sop));
            const ssize = try insnSize(sop);
            if (spc + ssize > insns.len) break;
            // (Pred lists built later subsume branch targets.)
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
    // Predecessor lists per unit (fallthrough + jump + switch sources;
    // handler pcs flagged separately). Built once; drives single-pred
    // restore and the reaching-def walk at conflicted reads.
    const PredList = std.ArrayListUnmanaged(u32);
    var preds = gpa.alloc(PredList, insns.len) catch return error.OutOfMemory;
    defer {
        for (preds) |*l| l.deinit(gpa);
        gpa.free(preds);
    }
    @memset(preds, .empty);
    var is_handler_pc = gpa.alloc(bool, insns.len) catch return error.OutOfMemory;
    defer gpa.free(is_handler_pc);
    @memset(is_handler_pc, false);
    {
        for (code.tries) |t| {
            const hs = dex.readCatchHandlers(gpa, d, code, t.handler_off) catch continue;
            defer gpa.free(hs);
            for (hs) |h| {
                if (h.addr < insns.len) is_handler_pc[h.addr] = true;
            }
        }
        // Instruction starts (payload-aware).
        var starts = std.ArrayListUnmanaged(usize).empty;
        defer starts.deinit(gpa);
        {
            var q: usize = 0;
            while (q < insns.len) {
                var in_pl = false;
                for (payloads.items) |r| {
                    if (q >= r.start and q < r.end) {
                        q = r.end;
                        in_pl = true;
                        break;
                    }
                }
                if (in_pl) continue;
                const qsz = insnSize(@intCast(insns[q] & 0xFF)) catch break;
                if (qsz == 0 or q + qsz > insns.len) break;
                try starts.append(gpa, q);
                q += qsz;
            }
        }
        for (starts.items, 0..) |spc, si| {
            const su0 = insns[spc];
            const sop: Op = @fromBackingInt(@intCast(@as(u8, @intCast(su0 & 0xFF))));
            const ssize = insnSize(@as(u8, @intCast(su0 & 0xFF))) catch continue;
            // Fallthrough successor.
            const falls = switch (sop) {
                .goto_, .goto16, .goto32, .return_void, .return_, .return_wide, .return_object, .throw_ => false,
                else => true,
            };
            if (falls and si + 1 < starts.items.len and starts.items[si + 1] == spc + ssize) {
                try preds[spc + ssize].append(gpa, @intCast(spc));
            }
            // Jump successors.
            switch (sop) {
                .goto_ => {
                    const t = @as(i64, @intCast(spc)) + @as(i8, @bitCast(reg8(su0)));
                    if (t >= 0 and t < insns.len) try preds[@intCast(t)].append(gpa, @intCast(spc));
                },
                .goto16, .if_eq, .if_ne, .if_lt, .if_ge, .if_gt, .if_le, .if_eqz, .if_nez, .if_ltz, .if_gez, .if_gtz, .if_lez => {
                    const t = @as(i64, @intCast(spc)) + @as(i16, @bitCast(insns[spc + 1]));
                    if (t >= 0 and t < insns.len) try preds[@intCast(t)].append(gpa, @intCast(spc));
                },
                .goto32 => {
                    const t = @as(i64, @intCast(spc)) + i32Pair(insns[spc + 1], insns[spc + 2]);
                    if (t >= 0 and t < insns.len) try preds[@intCast(t)].append(gpa, @intCast(spc));
                },
                .packed_switch, .sparse_switch => {
                    const soff = @as(i32, insns[spc + 1]) + (@as(i32, insns[spc + 2]) << 16);
                    const pdex = @as(i64, @intCast(spc)) + soff;
                    if (pdex >= 0 and pdex < insns.len) {
                        const pu: usize = @intCast(pdex);
                        const want: u16 = if (sop == .packed_switch) 0x0100 else 0x0200;
                        if (insns[pu] == want) {
                            const cnt: usize = insns[pu + 1];
                            if (cnt > 0 and cnt <= 4096) {
                                const tbase = if (sop == .packed_switch) pu + 4 else pu + 2 + cnt * 2;
                                for (0..cnt) |i| {
                                    if (tbase + i * 2 + 1 >= insns.len) break;
                                    const rel: i32 = @as(i32, insns[tbase + i * 2]) + (@as(i32, insns[tbase + i * 2 + 1]) << 16);
                                    const t = @as(i64, @intCast(spc)) + rel;
                                    if (t >= 0 and t < insns.len) try preds[@intCast(t)].append(gpa, @intCast(spc));
                                }
                            }
                        }
                    }
                },
                else => {},
            }
        }
    }
    // seeded from param types, then dual-slot allocation for regs with
    // both. Payload ranges are known here.
    {
        // Seed from params (last `ins` regs): a reused param reg splits.
        var sri: usize = code.registers - code.ins;
        if (!is_static) {
            if (sri < code.registers) ever_ref[sri] = true; // this
            sri += 1;
        }
        for (param_kinds) |tc| {
            if (sri >= code.registers) break;
            if (tc == 'L' or tc == '[') {
                ever_ref[sri] = true;
            } else {
                // Bits-always: float/double params convert to int/long
                // bits in the prologue, so their slots count as int.
                ever_int[sri] = true;
            }
            sri += 1;
            if ((tc == 'J' or tc == 'D') and sri < code.registers) sri += 1;
        }
        var spc2: usize = 0;
        while (spc2 < insns.len) {
            var in_pl = false;
            for (payloads.items) |r| {
                if (spc2 >= r.start and spc2 < r.end) {
                    spc2 = r.end;
                    in_pl = true;
                    break;
                }
            }
            if (in_pl) continue;
            const su0 = insns[spc2];
            const sop: u8 = @intCast(su0 & 0xFF);
            const sopcode: Op = @fromBackingInt(@intCast(sop));
            const ssize = try insnSize(sop);
            if (spc2 + ssize > insns.len) break;
            const markInt = struct {
                fn f(tab: []bool, r: u16) void {
                    if (r < tab.len) tab[r] = true;
                }
            }.f;
            const markRef = markInt;
            switch (sopcode) {
                .move_object => markRef(ever_ref, regA(su0)),
                .move, .move_wide => markInt(ever_int, regA(su0)),
                .move_object_from16, .move_object16 => {
                    if (spc2 + 2 < insns.len) markRef(ever_ref, moveRegs16(sopcode, su0, insns[spc2 + 1], insns[spc2 + 2]).dst);
                },
                .move_from16, .move16, .move_wide_from16, .move_wide16 => {
                    if (spc2 + 2 < insns.len) markInt(ever_int, moveRegs16(sopcode, su0, insns[spc2 + 1], insns[spc2 + 2]).dst);
                },
                .move_result_object, .move_exception => markRef(ever_ref, reg8(su0)),
                .move_result => markInt(ever_int, reg8(su0)),
                .const_string, .const_string_jumbo, .const_class => markRef(ever_ref, reg8(su0)),
                .const4, .const16, .const_, .const_high16 => {
                    // const4 dest is 4-bit A; 21c/31i dests are 8-bit.
                    markInt(ever_int, if (sopcode == .const4) regA(su0) else reg8(su0));
                },
                .check_cast, .new_instance => markRef(ever_ref, reg8(su0)),
                .new_array => markRef(ever_ref, regA(su0)),
                .instance_of, .array_length => markInt(ever_int, regA(su0)),
                .aget_object => {
                    if (spc2 + 1 < insns.len) markRef(ever_ref, regs23x(su0, insns[spc2 + 1]).a);
                },
                .aget, .aget_boolean, .aget_byte, .aget_char, .aget_short => {
                    if (spc2 + 1 < insns.len) markInt(ever_int, regs23x(su0, insns[spc2 + 1]).a);
                },
                .iget_object => markRef(ever_ref, regA(su0)),
                .iget, .iget_boolean, .iget_byte, .iget_char, .iget_short => markInt(ever_int, regA(su0)),
                .sget_object => markRef(ever_ref, reg8(su0)),
                .sget, .sget_boolean, .sget_byte, .sget_char, .sget_short => markInt(ever_int, reg8(su0)),
                .add_int,
                .sub_int,
                .mul_int,
                .div_int,
                .rem_int,
                .and_int,
                .or_int,
                .xor_int,
                .shl_int,
                .shr_int,
                .ushr_int,
                .add_int_2addr,
                .sub_int_2addr,
                .mul_int_2addr,
                .div_int_2addr,
                .rem_int_2addr,
                .and_int_2addr,
                .or_int_2addr,
                .xor_int_2addr,
                .shl_int_2addr,
                .shr_int_2addr,
                .ushr_int_2addr,
                .add_int_lit16,
                .sub_int_lit16,
                .mul_int_lit16,
                .div_int_lit16,
                .rem_int_lit16,
                .and_int_lit16,
                .or_int_lit16,
                .xor_int_lit16,
                .add_int_lit8,
                .sub_int_lit8,
                .mul_int_lit8,
                .div_int_lit8,
                .rem_int_lit8,
                .and_int_lit8,
                .or_int_lit8,
                .xor_int_lit8,
                .shl_int_lit8,
                .shr_int_lit8,
                .ushr_int_lit8,
                .neg_int,
                .not_int,
                .long_to_int,
                .float_to_int,
                .double_to_int,
                .int_to_byte,
                .int_to_char,
                .int_to_short,
                .cmpl_float,
                .cmpg_float,
                .cmpl_double,
                .cmpg_double,
                .cmp_long,
                => {
                    // Dest: 23x A (8-bit), 12x/22b/22s A. Cover both extracts.
                    markInt(ever_int, regA(su0));
                    markInt(ever_int, reg8(su0));
                },
                else => {},
            }
            spc2 += ssize;
        }
        // Allocate ref-slots for conflicted regs after max_locals.
        for (0..code.registers) |r| {
            if (ever_int[r] and ever_ref[r] and nconflicted < 65535) {
                crefs[r] = max_locals + nconflicted;
                nconflicted += 1;
            }
        }
        max_locals += nconflicted;
        rm.crefs = crefs;
    }
    const inPayload = struct {
        fn f(pl: []const PayloadRange, at: usize) bool {
            for (pl) |r| {
                if (at >= r.start and at < r.end) return true;
            }
            return false;
        }
    }.f;
    // Prologue: initialize every non-param local. Unconflicted regs
    // get iconst_0/istore (or aconst_null/astore for ever-ref regs).
    // Conflicted regs init BOTH slots: int slot from the param when the
    // reg is an int param else 0, ref slot from the param when a ref
    // param else null. DEX permits reads of path-uninitialized regs in
    // shapes ART accepts (stale/zero); the JVM verifier rejects
    // uninitialized locals outright. Zero-init matches ART's observable
    // behavior on live paths (dead paths don't care).
    {
        const nlocals: usize = code.registers - code.ins;
        for (0..nlocals) |r| {
            const rx: u16 = @intCast(r);
            const conflicted = rx < rm.crefs.len and rm.crefs[rx] != 0;
            if (conflicted) {
                try e.emit(gpa, 0x03); // iconst_0
                e.push(1);
                try emitStoreInt(&e, gpa, rm, rx);
                try e.emit(gpa, 0x01); // aconst_null
                e.push(1);
                try emitStoreRef(&e, gpa, rm, rx);
            } else if (rx < ever_ref.len and ever_ref[rx]) {
                try e.emit(gpa, 0x01); // aconst_null
                e.push(1);
                try emitStoreRef(&e, gpa, rm, rx);
            } else {
                try e.emit(gpa, 0x03); // iconst_0
                e.push(1);
                try emitStoreInt(&e, gpa, rm, rx);
            }
        }
        // Conflicted PARAM regs: copy the entry value into the ref slot
        // (int slot keeps the param local itself for int params; for ref
        // params the int slot inits 0 below... handled per-kind).
        // Bits-always: float/double params arrive as real floats;
        // convert to int/long bits up front (second halves of wide
        // pairs are covered by the first half's store).
        {
            var pri: usize = code.registers - code.ins;
            if (!is_static) pri += 1;
            for (param_kinds) |tc| {
                if (pri >= code.registers) break;
                const prx: u16 = @intCast(pri);
                if (tc == 'F') {
                    const slot = rm.map(prx);
                    if (slot < 4) {
                        try e.emit(gpa, @intCast(0x22 + slot)); // fload_n
                    } else if (slot < 256) {
                        try e.emit(gpa, 0x17);
                        try e.emit(gpa, @intCast(slot));
                    } else {
                        try e.emit(gpa, 0xc4);
                        try e.emit(gpa, 0x17);
                        try e.emitU16(gpa, slot);
                    }
                    e.push(1);
                    try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, prx);
                    // Keep the .float value marker (slot holds bits).
                    if (prx < types.len) types[prx] = .float;
                    pri += 1;
                } else if (tc == 'D') {
                    const slot = rm.map(prx);
                    if (slot < 4) {
                        try e.emit(gpa, @intCast(0x26 + slot)); // dload_n
                    } else if (slot < 256) {
                        try e.emit(gpa, 0x18);
                        try e.emit(gpa, @intCast(slot));
                    } else {
                        try e.emit(gpa, 0xc4);
                        try e.emit(gpa, 0x18);
                        try e.emitU16(gpa, slot);
                    }
                    e.push(2);
                    try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, prx);
                    if (prx < types.len) types[prx] = .double;
                    if (prx + 1 < types.len) types[prx + 1] = .double;
                    pri += 2;
                } else if (tc == 'J') {
                    pri += 2;
                } else {
                    pri += 1;
                }
            }
        }
        for (code.registers - code.ins..code.registers) |r| {
            const rx: u16 = @intCast(r);
            if (!(rx < rm.crefs.len and rm.crefs[rx] != 0)) continue;
            const pk: RegType = if (rx < types.len) types[rx] else .unknown;
            if (pk == .ref) {
                // Ref param: normal slot already holds it; int slot needs
                // a local of its own... the normal mapping IS the int
                // slot. Copy param aside first? No: normal slot keeps the
                // param (int reads of it are DEX-impossible); init only
                // the ref slot with the param value.
                try emitLoadRef(&e, gpa, .{ .table = rm.table, .crefs = &.{} }, rx);
                try emitStoreRef(&e, gpa, rm, rx);
            } else if (pk == .int) {
                // Int param: normal slot holds it; ref slot inits null.
                try e.emit(gpa, 0x01); // aconst_null
                e.push(1);
                try emitStoreRef(&e, gpa, rm, rx);
            }
            // Wide/float/unknown param kinds: ref slot inits null (reads
            // there are DEX-impossible or null-tolerant).
            if (pk != .ref and pk != .int) {
                try e.emit(gpa, 0x01); // aconst_null
                e.push(1);
                try emitStoreRef(&e, gpa, rm, rx);
            }
        }
    }
    while (pc < insns.len) {
        if (inPayload(payloads.items, pc)) {
            pc += 1;
            continue;
        }
        const dex_pc: u32 = @intCast(pc);
        // Single-predecessor restore: at a unit with exactly one
        // predecessor, the linear tracker (which reflects the linear
        // predecessor) is wrong when the pred is a jump; restore the
        // pred's exit snapshot. Exact, not heuristic. Skipped for
        // handler pcs (exception edges) and multi-pred joins (linear
        // status quo). No-op when the pred is the fallthrough.
        if (pc < preds.len and preds[pc].items.len == 1 and !is_handler_pc[pc] and pc > 0) {
            const pred = preds[pc].items[0];
            // Snapshot exists only for already-processed preds (loop
            // back-edges to earlier targets have none yet).
            if (pred < pc) {
                @memcpy(types, snap_types[@as(usize, pred) * code.registers ..][0..code.registers]);
                @memcpy(wide_comp, snap_wide[@as(usize, pred) * code.registers ..][0..code.registers]);
            }
        }
        try e.mark(gpa, dex_pc);
        const unit0 = insns[pc];
        const op: u8 = @intCast(unit0 & 0xFF);
        const size = try insnSize(op);
        const opcode: Op = @fromBackingInt(@intCast(op));
        switch (opcode) {
            .nop => try e.emit(gpa, 0x00),
            .move => {
                // Bits move as ints (locals never hold real floats).
                const mvs = regB(unit0);
                try emitLoadInt(&e, gpa, rm, mvs);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .move_from16, .move16 => {
                // 22x/32x: A = dest, B = src (8/16-bit regs).
                const mr = moveRegs16(opcode, unit0, insns[pc + 1], insns[pc + 2]);
                try emitLoadInt(&e, gpa, rm, mr.src);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, mr.dst, .int);
            },

            .move_object => {
                const msrc = regB(unit0);
                const mdst = regA(unit0);
                const sconf = msrc < rm.crefs.len and rm.crefs[msrc] != 0;
                if (!sconf and msrc < types.len and (types[msrc] == .int or types[msrc] == .unknown)) {
                    // Null idiom: an int-held (or uninitialized-held)
                    // null copies as aconst_null (verified DEX passing
                    // int-as-ref is null; uninitialized can only flow to
                    // a null-test-guarded use).
                    try e.emit(gpa, 0x01); // aconst_null
                    e.push(1);
                } else {
                    try emitLoadRef(&e, gpa, rm, msrc);
                }
                try emitStoreRef(&e, gpa, rm, mdst);
                if (mdst < types.len) types[mdst] = .ref;
                // Aliasing copy: propagate the component kind (array moves
                // between regs stay tracked); source kept as-is.
                if (mdst < wide_comp.len) wide_comp[mdst] = if (msrc < wide_comp.len) wide_comp[msrc] else 0;
                // Propagate a pending allocation (init-via-alias keeps pairing).
                if (mdst < alloc_owner.len and msrc < alloc_owner.len) {
                    if (alloc_owner[mdst]) |old| gpa.free(old);
                    alloc_owner[mdst] = if (alloc_owner[msrc]) |own| try gpa.dupe(u8, own) else null;
                }
            },
            .move_object_from16, .move_object16 => {
                const mr = moveRegs16(opcode, unit0, insns[pc + 1], insns[pc + 2]);
                // Same null idiom as move_object (int/unknown-held null).
                const mconf = mr.src < rm.crefs.len and rm.crefs[mr.src] != 0;
                if (!mconf and mr.src < types.len and (types[mr.src] == .int or types[mr.src] == .unknown)) {
                    try e.emit(gpa, 0x01); // aconst_null
                    e.push(1);
                } else {
                    try emitLoadRef(&e, gpa, rm, mr.src);
                }
                try emitStoreRef(&e, gpa, rm, mr.dst);
                if (mr.dst < types.len) types[mr.dst] = .ref;
                if (mr.dst < wide_comp.len) wide_comp[mr.dst] = if (mr.src < wide_comp.len) wide_comp[mr.src] else 0;
                if (mr.dst < alloc_owner.len and mr.src < alloc_owner.len) {
                    if (alloc_owner[mr.dst]) |old| gpa.free(old);
                    alloc_owner[mr.dst] = if (alloc_owner[mr.src]) |own| try gpa.dupe(u8, own) else null;
                }
            },
            .move_wide => {
                // Bits move as longs (locals never hold real doubles).
                const mws = regB(unit0);
                try emitLoadWide(&e, gpa, rm, mws, false);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .long);
            },
            .move_wide_from16, .move_wide16 => {
                const mr = moveRegs16(opcode, unit0, insns[pc + 1], insns[pc + 2]);
                try emitLoadWide(&e, gpa, rm, mr.src, false);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, mr.dst, .long);
            },
            .move_result => {
                // Value is on the JVM stack from the preceding invoke.
                if (last_ret_kind == 'F') {
                    try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, reg8(unit0));
                } else {
                    try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .int);
                }
            },
            .move_result_object => {
                const mrr = reg8(unit0);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, mrr, .ref);
                if (mrr < wide_comp.len and last_ret_comp != 0) wide_comp[mrr] = last_ret_comp;
            },
            .move_result_wide => {
                if (last_ret_kind == 'D') {
                    try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, reg8(unit0));
                } else {
                    try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .long);
                }
            },
            .move_exception => {
                // Dead in straight-line flow: handlers route through
                // trampolines (see the tries expansion below) that store
                // the exception and jump past this unit. Only the type
                // tracker updates (fallthrough never reaches here).
                const rx = reg8(unit0);
                if (rx < types.len) types[rx] = .ref;
            },

            .return_void => {
                try e.emit(gpa, 0xb1);
            },
            .return_ => {
                if (ret_kind == 'F') {
                    try emitLoadFloatOperand(&e, gpa, rm, &uses, types, reg8(unit0));
                    try e.emit(gpa, 0xae); // freturn
                } else {
                    try emitLoadInt(&e, gpa, rm, reg8(unit0));
                    try e.emit(gpa, 0xac); // ireturn
                }
                e.push(-1);
            },
            .return_wide => {
                const rwd = ret_kind == 'D';
                if (rwd) {
                    try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, reg8(unit0));
                } else {
                    try emitLoadWide(&e, gpa, rm, reg8(unit0), false);
                }
                try e.emit(gpa, if (rwd) 0xaf else 0xad); // dreturn / lreturn
                e.push(-2);
            },
            .return_object => {
                try emitLoadRefOrNull(&e, gpa, rm, types, reg8(unit0));
                try e.emit(gpa, 0xb0); // areturn
                e.push(-1);
            },
            .const4 => {
                const lit: i32 = @as(i8, @bitCast(@as(u8, regB(unit0)) << 4)) >> 4;
                try storeConstInt(&e, gpa, rm, types, wide_comp, alloc_owner, &uses, regA(unit0), lit, false);
            },
            .const16 => {
                const lit: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try storeConstInt(&e, gpa, rm, types, wide_comp, alloc_owner, &uses, reg8(unit0), lit, false);
            },
            .const_ => {
                try storeConstInt(&e, gpa, rm, types, wide_comp, alloc_owner, &uses, reg8(unit0), i32Pair(insns[pc + 1], insns[pc + 2]), true);
            },
            .const_high16 => {
                const lit: i32 = @as(i32, @as(i16, @bitCast(insns[pc + 1]))) << 16;
                try storeConstInt(&e, gpa, rm, types, wide_comp, alloc_owner, &uses, reg8(unit0), lit, true);
            },
            .const_wide16 => {
                const lit: i64 = @as(i16, @bitCast(insns[pc + 1]));
                try emitConstLongPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .long);
            },
            .const_wide32 => {
                const lit: i64 = @as(i32, @bitCast(u32Pair(insns[pc + 1], insns[pc + 2])));
                try emitConstLongPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .long);
            },
            .const_wide => {
                // 51l: 64-bit literal across 4 units, little-endian.
                const u: u64 = u64Quad(insns[pc + 1], insns[pc + 2], insns[pc + 3], insns[pc + 4]);
                try emitConstLongPool(&e, gpa, &uses, @bitCast(u));
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .long);
            },
            .const_wide_high16 => {
                const lit: i64 = @as(i64, @as(i16, @bitCast(insns[pc + 1]))) << 48;
                try emitConstLongPool(&e, gpa, &uses, lit);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .long);
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
                // Forced kinds: DEX rejects mixed int/ref at if-eq, so
                // a conflicted operand MUST match a definite other side
                // (conflicted+int -> INT, conflicted+ref -> REF). Both
                // conflicted (or conflicted+unknown) uses backward
                // reaching-def agreement over both operands (INT iff all
                // paths deliver int).
                const conf_a = ra < rm.crefs.len and rm.crefs[ra] != 0;
                const conf_b = rb < rm.crefs.len and rm.crefs[rb] != 0;
                const ka_ref = ta == .ref or (!conf_a and ta == .unknown and ra < ever_ref.len and ever_ref[ra]);
                const kb_ref = tb == .ref or (!conf_b and tb == .unknown and rb < ever_ref.len and ever_ref[rb]);
                const ka_int = !conf_a and !ka_ref;
                const kb_int = !conf_b and !kb_ref;
                // Linear tracker state can be left as REF by a terminated
                // throwing block that reuses an integer parameter. At a join,
                // definite reaching integer stores outrank that stale state.
                const both_int = (opcode == .if_eq or opcode == .if_ne) and
                    reachingDefsAllInt(gpa, insns, preds, code.registers, code.ins, is_static, param_kinds, pc, ra) and
                    reachingDefsAllInt(gpa, insns, preds, code.registers, code.ins, is_static, param_kinds, pc, rb);
                const is_ref = if (both_int) false else if (conf_a and kb_int) false else if (conf_b and ka_int) false else if (conf_a and kb_ref) true else if (conf_b and ka_ref) true else if (conf_a or conf_b) !(reachingDefsAllInt(gpa, insns, preds, code.registers, code.ins, is_static, param_kinds, pc, ra) and reachingDefsAllInt(gpa, insns, preds, code.registers, code.ins, is_static, param_kinds, pc, rb)) else ka_ref or kb_ref;
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
                // 21t: vAA is a full byte (nibble extract misreads regs >= 16).
                const ra = reg8(unit0);
                const ta: RegType = if (ra < types.len) types[ra] else .unknown;
                // Conflicted regs: INT form when every backward path
                // reaches an int store first (then every path delivers
                // int); else REF form (0/null agree on ifz; int/ref
                // conflicts at ifz are always {0,ref} in verified DEX).
                // Uninitialized paths wildcard (prologue zeroes both
                // slots); all-uninit reads as ref (ifnull).
                const conflicted = ra < rm.crefs.len and rm.crefs[ra] != 0;
                const all_int = conflicted and reachingDefsAllInt(gpa, insns, preds, code.registers, code.ins, is_static, param_kinds, pc, ra);
                const is_ref = if (all_int) false else conflicted or ta == .ref or ta == .unknown;
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
                    if (ta == .unknown and !conflicted) {
                        try e.emit(gpa, 0x01); // aconst_null
                        e.push(1);
                    } else {
                        try emitLoadRef(&e, gpa, rm, ra);
                    }
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .int);
            },
            .add_long, .sub_long, .mul_long, .div_long, .rem_long, .and_long, .or_long, .xor_long => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, false);
                try emitLoadWide(&e, gpa, rm, r.c, false);
                try e.emit(gpa, arithJop(opcode));
                e.push(-2);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .long);
            },
            .shl_long, .shr_long, .ushr_long => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, false);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1); // pop 3 (wide+int), push 2
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .long);
            },
            .add_float, .sub_float, .mul_float, .div_float, .rem_float => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, r.b);
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, r.c);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, r.a);
            },
            .add_double, .sub_double, .mul_double, .div_double, .rem_double => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.b);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.c);
                try e.emit(gpa, arithJop(opcode));
                e.push(-2);
                try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, r.a);
            },

            .add_int_2addr, .sub_int_2addr, .mul_int_2addr, .div_int_2addr, .rem_int_2addr, .and_int_2addr, .or_int_2addr, .xor_int_2addr, .shl_int_2addr, .shr_int_2addr, .ushr_int_2addr => {
                // 12x: A = dest+src1, B = src2.
                const a = regA(unit0);
                try emitLoadInt(&e, gpa, rm, a);
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, a, .int);
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, a, .long);
            },
            .add_float_2addr, .sub_float_2addr, .mul_float_2addr, .div_float_2addr, .rem_float_2addr => {
                const a = regA(unit0);
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, a);
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, a);
            },
            .add_double_2addr, .sub_double_2addr, .mul_double_2addr, .div_double_2addr, .rem_double_2addr => {
                const a = regA(unit0);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, a);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, arithJop(opcode));
                e.push(-2);
                try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, a);
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, a, .int);
            },
            .add_int_lit16, .sub_int_lit16, .mul_int_lit16, .div_int_lit16, .rem_int_lit16, .and_int_lit16, .or_int_lit16, .xor_int_lit16 => {
                // 22s: literal = next unit (sign-extended 16-bit).
                const lit: i32 = @as(i16, @bitCast(insns[pc + 1]));
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try emitConstInt(&e, gpa, lit);
                try e.emit(gpa, arithJop(opcode));
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .iget, .iget_wide, .iget_object, .iget_boolean, .iget_byte, .iget_char, .iget_short => {
                // 22c: A = dest, B = object, index in next unit.
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, alloc_owner, 0xb4, insns[pc + 1], regB(unit0), regA(unit0));
            },
            .iput, .iput_wide, .iput_object, .iput_boolean, .iput_byte, .iput_char, .iput_short => {
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, alloc_owner, 0xb5, insns[pc + 1], regB(unit0), regA(unit0));
            },
            .sget, .sget_wide, .sget_object, .sget_boolean, .sget_byte, .sget_char, .sget_short => {
                // 21c: A = dest, index in next unit.
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, alloc_owner, 0xb2, insns[pc + 1], null, reg8(unit0));
            },
            .sput, .sput_wide, .sput_object, .sput_boolean, .sput_byte, .sput_char, .sput_short => {
                try emitFieldOp(&e, gpa, rm, d, &uses, types, wide_comp, alloc_owner, 0xb3, insns[pc + 1], null, reg8(unit0));
            },
            .new_instance => {
                const type_idx = insns[pc + 1];
                try emitPoolRef(&e, gpa, &uses, 0xbb, 1, .{ .type = type_idx }); // new
                const new_reg = reg8(unit0);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, new_reg, .ref);
                // Record the allocation type for the <init> call rewrite.
                if (new_reg < alloc_owner.len) {
                    if (alloc_owner[new_reg]) |old| gpa.free(old);
                    const ndesc = try d.getTypeDescriptor(gpa, type_idx);
                    defer gpa.free(ndesc);
                    alloc_owner[new_reg] = try descriptorToInternal(gpa, ndesc);
                }
            },
            .const_string => {
                // Always ldc_w (wide:true): the class writer patches a
                // 2-byte pool index, so string pools above 255 entries
                // stay encodable (jumbo-sized pools imply this path).
                try emitPoolRef(&e, gpa, &uses, 0x13, 1, .{ .string = insns[pc + 1] }); // ldc_w
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .ref);
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .ref);
            },
            .const_string_jumbo => {
                // 32x: 8/16-bit reg + 32-bit string idx across 2 units.
                try emitPoolRef(&e, gpa, &uses, 0x13, 1, .{ .string = u32Pair(insns[pc + 1], insns[pc + 2]) }); // ldc_w (same wide path as const_string)
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, reg8(unit0), .ref);
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
                // Discard the result when no move-result follows (DEX drops
                // it silently; JVM would leak stack).
                const nxt: u8 = if (pc + size < insns.len) @intCast(insns[pc + size] & 0xFF) else 0;
                const keep = nxt == 0x0a or nxt == 0x0b or nxt == 0x0c;
                try emitInvokeCall(&e, gpa, rm, d, &uses, &last_ret_kind, &last_ret_comp, jop, method_idx, nib[0..argc], opcode == .invoke_static, opcode == .invoke_interface, !keep, types, alloc_owner, super_internal, this_internal, ctor_this, forward_needs, insns, pc, code.registers, code.ins, param_kinds, proto.parameters_off);
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
                const nxt2: u8 = if (pc + size < insns.len) @intCast(insns[pc + size] & 0xFF) else 0;
                const keep2 = nxt2 == 0x0a or nxt2 == 0x0b or nxt2 == 0x0c;
                try emitInvokeCall(&e, gpa, rm, d, &uses, &last_ret_kind, &last_ret_comp, jop, method_idx, rregs[0..argc], opcode == .invoke_static_range, opcode == .invoke_interface_range, !keep2, types, alloc_owner, super_internal, this_internal, ctor_this, forward_needs, insns, pc, code.registers, code.ins, param_kinds, proto.parameters_off);
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .long);
            },
            .neg_float => {
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x76); // fneg
                try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .neg_double => {
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x77); // dneg
                try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            // Conversions 12x.
            .int_to_long => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x85); // i2l
                e.push(1); // pop 1 push 2
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .long);
            },
            .int_to_float => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x86); // i2f
                try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .int_to_double => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x87); // i2d
                e.push(1);
                try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .long_to_int => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                try e.emit(gpa, 0x88); // l2i
                e.push(-1); // pop 2 push 1
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .long_to_float => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                try e.emit(gpa, 0x89); // l2f
                e.push(-1);
                try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .long_to_double => {
                try emitLoadWide(&e, gpa, rm, regB(unit0), false);
                try e.emit(gpa, 0x8a); // l2d
                try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .float_to_int => {
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x8b); // f2i
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .float_to_long => {
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x8c); // f2l
                e.push(1);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .long);
            },
            .float_to_double => {
                try emitLoadFloatOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x8d); // f2d
                e.push(1);
                try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .double_to_int => {
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x8e); // d2i
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .double_to_long => {
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x8f); // d2l
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .long);
            },
            .double_to_float => {
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, regB(unit0));
                try e.emit(gpa, 0x90); // d2f
                e.push(-1);
                try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, regA(unit0));
            },
            .int_to_byte => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x91); // i2b
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .int_to_char => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x92); // i2c
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            .int_to_short => {
                try emitLoadInt(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0x93); // i2s
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
            },
            // Arrays: 23x aget/aput (A=dest/src val, B=array, C=index),
            // 12x array-length, 22c new-array.
            .aget => {
                // DEX aget covers int[] and float[] (both 32-bit): pick by
                // the array reg's recorded component ('F' => faload/bits).
                // The linear tracker is clobbered by stores on dead paths,
                // so confirm via reaching defs when it disagrees or is blank.
                const r = regs23x(unit0, insns[pc + 1]);
                var acomp: u8 = if (r.b < wide_comp.len) wide_comp[r.b] else 0;
                if (acomp != 'F') {
                    const reach = reachingArrayComponent(gpa, d, insns, preds,
                        code.registers, code.ins, is_static, param_kinds, proto.parameters_off, pc, r.b);
                    if (reach != 0) acomp = reach;
                }
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                if (acomp == 'F') {
                    try e.emit(gpa, 0x30); // faload
                    e.push(-1);
                    try emitStoreFloatBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, r.a);
                } else {
                    try e.emit(gpa, 0x2e); // iaload
                    e.push(-1);
                    try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .int);
                }
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .int);
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
                if (is_d) {
                    try emitStoreDoubleBits(&e, gpa, rm, &uses, types, wide_comp, alloc_owner, r.a);
                } else {
                    try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .long);
                }
            },
            .aget_object => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try e.emit(gpa, 0x32); // aaload
                e.push(-1);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .ref);
            },
            .aput => {
                const r = regs23x(unit0, insns[pc + 1]);
                var acomp: u8 = if (r.b < wide_comp.len) wide_comp[r.b] else 0;
                if (acomp != 'F') {
                    const reach = reachingArrayComponent(gpa, d, insns, preds,
                        code.registers, code.ins, is_static, param_kinds, proto.parameters_off, pc, r.b);
                    if (reach != 0) acomp = reach;
                }
                const afl = acomp == 'F' or (acomp != 'I' and acomp != 'J' and acomp != 'D' and r.a < types.len and types[r.a] == .float);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                if (afl) try emitLoadFloatOperand(&e, gpa, rm, &uses, types, r.a) else try emitLoadInt(&e, gpa, rm, r.a);
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
                if (is_d) {
                    try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.a);
                } else {
                    try emitLoadWide(&e, gpa, rm, r.a, false);
                }
                try e.emit(gpa, if (is_d) 0x52 else 0x50); // dastore / lastore
                e.push(-4);
            },
            .aput_object => {
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadRef(&e, gpa, rm, r.b);
                try emitLoadInt(&e, gpa, rm, r.c);
                try emitLoadRefOrNull(&e, gpa, rm, types, r.a);
                try e.emit(gpa, 0x53); // aastore
                e.push(-3);
            },
            .array_length => {
                // 12x: A = dest, B = array ref.
                try emitLoadRef(&e, gpa, rm, regB(unit0));
                try e.emit(gpa, 0xbe); // arraylength
                e.push(0); // pop ref, push int
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, dest, .ref);
                // Record component kind for later aget/aput on this dest
                // reg ('J'/'D'/'F', else 0 = unknown/int default).
                // AFTER storeTracked (which clears wide_comp for refs).
                if (dest < wide_comp.len) {
                    wide_comp[dest] = if (td.len == 2 and td[0] == '[') td[1] else 0;
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
                // 11x: A = exception ref. Null idiom: int-held 0 is null
                // (athrow null throws NPE; int-throw is DEX-unverifiable
                // except via the null idiom, and often dead code after a
                // check helper that always throws).
                try emitLoadRefOrNull(&e, gpa, rm, types, reg8(unit0));
                try e.emit(gpa, 0xbf); // athrow
                e.push(-1);
            },
            .check_cast => {
                // 21c: A = ref, type in next unit.
                const ccd = reg8(unit0);
                try emitLoadRef(&e, gpa, rm, ccd);
                try emitPoolRef(&e, gpa, &uses, 0xc0, 0, .{ .type = insns[pc + 1] }); // checkcast
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, ccd, .ref);
                // Record array component (AFTER the clearing store).
                if (ccd < wide_comp.len) {
                    const ctd = try d.getTypeDescriptor(gpa, insns[pc + 1]);
                    defer gpa.free(ctd);
                    if (ctd.len == 2 and ctd[0] == '[') wide_comp[ccd] = ctd[1];
                }
            },
            .instance_of => {
                // 22c: A = dest, B = ref, type in next unit.
                try emitLoadRef(&e, gpa, rm, regB(unit0));
                try emitPoolRef(&e, gpa, &uses, 0xc1, 0, .{ .type = insns[pc + 1] }); // instanceof: pop ref, push int
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, regA(unit0), .int);
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
                    // Component-aware store: width alone is ambiguous
                    // (1 = byte/boolean share bastore; 2 = short/char need
                    // sastore/castore; 4 + 'F' needs fastore). Unknown comp
                    // defaults to the int-family opcode (loud if wrong).
                    const fcomp: u8 = if (arr < wide_comp.len) wide_comp[arr] else 0;
                    if (width == 1) v = @as(i8, @truncate(v));
                    if (width == 2 and fcomp != 'C') v = @as(i16, @truncate(v));
                    try emitConstIntPool(&e, gpa, &uses, v);
                    if (width == 8) unreachable; // handled above
                    if (width == 4 and fcomp == 'F') {
                        try emitPoolRef(&e, gpa, &uses, 0xb8, 0, .{ .raw_method = .{
                            .cls = "java/lang/Float",
                            .name = "intBitsToFloat",
                            .desc = "(I)F",
                        } });
                        try e.emit(gpa, 0x51); // fastore
                    } else if (width == 1) {
                        try e.emit(gpa, 0x54); // bastore (byte[] and boolean[])
                    } else if (width == 2) {
                        try e.emit(gpa, if (fcomp == 'C') 0x55 else 0x56); // castore / sastore
                    } else {
                        try e.emit(gpa, 0x4f); // iastore
                    }
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
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .int);
            },
            .cmpl_double, .cmpg_double => {
                // 23x double compare: dcmpl (0x97) / dcmpg (0x98).
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.b);
                try emitLoadDoubleOperand(&e, gpa, rm, &uses, types, r.c);
                try e.emit(gpa, if (opcode == .cmpl_double) 0x97 else 0x98);
                e.push(-3);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .int);
            },
            .cmp_long => {
                // 23x long compare: lcmp (0x94).
                const r = regs23x(unit0, insns[pc + 1]);
                try emitLoadWide(&e, gpa, rm, r.b, false);
                try emitLoadWide(&e, gpa, rm, r.c, false);
                try e.emit(gpa, 0x94);
                e.push(-3);
                try storeTracked(&e, gpa, rm, types, wide_comp, alloc_owner, r.a, .int);
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
                        try emitLoadFloatOperand(&e, gpa, rm, &uses, types, regs[i]);
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
        // Snapshot tracker state for single-pred restore.
        @memcpy(snap_types[pc * code.registers ..][0..code.registers], types);
        @memcpy(snap_wide[pc * code.registers ..][0..code.registers], wide_comp);
        pc += size;
    }
    try e.resolve();
    // Handler-body infallible prefixes: straight-line runs of
    // non-throwing opcodes starting at each handler pc. Try ranges are
    // split around them (below): handler code covered by its own try
    // feeds handler-entry state back into the try, and the JVM verifier
    // merges it with normal flow (ART tolerates this shape; HotSpot
    // rejects the over-merged locals). Excluding infallible prefixes is
    // semantics-preserving (they cannot throw).
    const ExclRange = struct { s: u32, e: u32 };
    var excl: std.ArrayListUnmanaged(ExclRange) = .empty;
    defer excl.deinit(gpa);
    {
        var seen_h: std.AutoHashMapUnmanaged(u32, void) = .empty;
        defer seen_h.deinit(gpa);
        for (code.tries) |t| {
            const hs = dex.readCatchHandlers(gpa, d, code, t.handler_off) catch continue;
            defer gpa.free(hs);
            for (hs) |h| {
                if (seen_h.contains(h.addr)) continue;
                seen_h.put(gpa, h.addr, {}) catch continue;
                var q: usize = h.addr;
                // Walk straight infallible units: nop, moves, consts,
                // move-result/exception. Anything else (branches,
                // invokes, field/array access, throw, arithmetic that
                // can trap) ends the prefix.
                while (q < insns.len) {
                    const qu = insns[q];
                    const qop: Op = @fromBackingInt(@intCast(@as(u8, @intCast(qu & 0xFF))));
                    // Terminal exits: a goto/return ends the run (included:
                    // both are infallible, and excluding the exit keeps the
                    // handler body's outgoing merge out of the try).
                    const terminal = switch (qop) {
                        .goto_, .goto16, .goto32, .return_void, .return_, .return_wide, .return_object => true,
                        else => false,
                    };
                    const infallible = switch (qop) {
                        .nop,
                        .move,
                        .move_from16,
                        .move16,
                        .move_wide,
                        .move_wide_from16,
                        .move_wide16,
                        .move_object,
                        .move_object_from16,
                        .move_object16,
                        .move_result,
                        .move_result_wide,
                        .move_result_object,
                        .move_exception,
                        .const4,
                        .const16,
                        .const_,
                        .const_high16,
                        .const_wide16,
                        .const_wide32,
                        .const_wide,
                        .const_wide_high16,
                        .const_string,
                        .const_string_jumbo,
                        .const_class,
                        => true,
                        else => false,
                    };
                    if (!infallible and !terminal) break;
                    const qsz = insnSize(@as(u8, @intCast(qu & 0xFF))) catch break;
                    if (qsz == 0 or q + qsz > insns.len) break;
                    q += qsz;
                    if (terminal) break;
                }
                if (q > h.addr) {
                    try excl.append(gpa, .{ .s = h.addr, .e = @intCast(q) });
                }
            }
        }
    }
    // Expand DEX tries to JVM exception table entries via the offset map.
    var exceptions: std.ArrayListUnmanaged(ExceptionEntry) = .empty;
    errdefer exceptions.deinit(gpa);
    for (code.tries) |t| {
        const try_start: u32 = t.start_addr;
        var try_end: u32 = t.start_addr + t.insn_count;
        // Dalvik handlers observe try-START register state, but JVM
        // handlers observe the join over the whole try body. D8 reuses
        // registers across that boundary (e.g. a param reg holding a
        // temp inside the try), so the join can be wider than what the
        // handler's uses accept (Gson TimeTypeAdapter: k1/a + Time ->
        // Object at a k1/a.h call). Shrink the JVM try end backward
        // over provably-pure tails (no sync throw possible, so the
        // handler never triggers for them): the join then excludes
        // the dead temp stores. Never empties (keeps one unit).
        {
            // Instruction starts in [try_start, try_end).
            var starts: [512]u32 = undefined;
            var nstarts: usize = 0;
            var q: usize = try_start;
            while (q < try_end and q < insns.len and nstarts < starts.len) {
                if (inPayload(payloads.items, q)) break;
                const qsz = insnSize(@as(u8, @intCast(insns[q] & 0xFF))) catch break;
                if (qsz == 0 or q + qsz > insns.len) break;
                starts[nstarts] = @intCast(q);
                nstarts += 1;
                q += qsz;
            }
            const pure_tail = struct {
                fn f(opcode: Op) bool {
                    return switch (opcode) {
                        .nop,
                        .move,
                        .move_from16,
                        .move16,
                        .move_wide,
                        .move_wide_from16,
                        .move_wide16,
                        .move_object,
                        .move_object_from16,
                        .move_object16,
                        .move_result,
                        .move_result_wide,
                        .move_result_object,
                        .move_exception,
                        .const4,
                        .const16,
                        .const_,
                        .const_high16,
                        .const_wide16,
                        .const_wide32,
                        .const_wide,
                        .const_wide_high16,
                        .const_string,
                        .const_string_jumbo,
                        .goto_,
                        .goto16,
                        .goto32,
                        .return_void,
                        .return_,
                        .return_wide,
                        .return_object,
                        .if_eq,
                        .if_ne,
                        .if_lt,
                        .if_ge,
                        .if_gt,
                        .if_le,
                        .if_eqz,
                        .if_nez,
                        .if_ltz,
                        .if_gez,
                        .if_gtz,
                        .if_lez,
                        .neg_int,
                        .not_int,
                        .neg_long,
                        .not_long,
                        .neg_float,
                        .neg_double,
                        .int_to_long,
                        .int_to_float,
                        .int_to_double,
                        .long_to_int,
                        .long_to_float,
                        .long_to_double,
                        .float_to_int,
                        .float_to_long,
                        .float_to_double,
                        .double_to_int,
                        .double_to_long,
                        .double_to_float,
                        .int_to_byte,
                        .int_to_char,
                        .int_to_short,
                        .cmpl_float,
                        .cmpg_float,
                        .cmpl_double,
                        .cmpg_double,
                        .cmp_long,
                        .instance_of,
                        .add_int,
                        .sub_int,
                        .mul_int,
                        .and_int,
                        .or_int,
                        .xor_int,
                        .shl_int,
                        .shr_int,
                        .ushr_int,
                        .add_long,
                        .sub_long,
                        .mul_long,
                        .and_long,
                        .or_long,
                        .xor_long,
                        .shl_long,
                        .shr_long,
                        .ushr_long,
                        .add_float,
                        .sub_float,
                        .mul_float,
                        .div_float,
                        .rem_float,
                        .add_double,
                        .sub_double,
                        .mul_double,
                        .div_double,
                        .rem_double,
                        .add_int_2addr,
                        .sub_int_2addr,
                        .mul_int_2addr,
                        .and_int_2addr,
                        .or_int_2addr,
                        .xor_int_2addr,
                        .shl_int_2addr,
                        .shr_int_2addr,
                        .ushr_int_2addr,
                        .add_long_2addr,
                        .sub_long_2addr,
                        .mul_long_2addr,
                        .and_long_2addr,
                        .or_long_2addr,
                        .xor_long_2addr,
                        .shl_long_2addr,
                        .shr_long_2addr,
                        .ushr_long_2addr,
                        .add_float_2addr,
                        .sub_float_2addr,
                        .mul_float_2addr,
                        .div_float_2addr,
                        .rem_float_2addr,
                        .add_double_2addr,
                        .sub_double_2addr,
                        .mul_double_2addr,
                        .div_double_2addr,
                        .rem_double_2addr,
                        .add_int_lit16,
                        .sub_int_lit16,
                        .mul_int_lit16,
                        .and_int_lit16,
                        .or_int_lit16,
                        .xor_int_lit16,
                        .add_int_lit8,
                        .sub_int_lit8,
                        .mul_int_lit8,
                        .and_int_lit8,
                        .or_int_lit8,
                        .xor_int_lit8,
                        .shl_int_lit8,
                        .shr_int_lit8,
                        .ushr_int_lit8,
                        .packed_switch,
                        .sparse_switch,
                        => true,
                        // Throws (sync): div/rem-int/long, monitor, check-cast,
                        // new/fill, field/array/invoke/throw, array-length,
                        // const-class (loads), filled-new-array.
                        else => false,
                    };
                }
            }.f;
            var si: usize = nstarts;
            while (si > 1) {
                const spc: usize = starts[si - 1];
                const sop: Op = @fromBackingInt(@intCast(@as(u8, @intCast(insns[spc] & 0xFF))));
                if (pure_tail(sop)) {
                    try_end = @intCast(spc);
                    si -= 1;
                    continue;
                }
                // Trailing monitor-exit/move-exception also shrink:
                // IllegalMonitorState is only catchable pathologically
                // (re-entry loop when a handler covers its own exit),
                // and excluding the exit keeps try-body temps out of the
                // handler-entry join (Dalvik observes try-start state).
                if (sop != .monitor_exit and sop != .move_exception) break;
                try_end = @intCast(spc);
                si -= 1;
            }
        }
        // Split [try_start, try_end) around handler prefixes inside it.
        // Segments stay in DEX units; mapped to JVM below.
        const Seg = struct { s: u32, e: u32 };
        var segs: [17]Seg = undefined;
        var nsegs: usize = 1;
        segs[0] = .{ .s = try_start, .e = try_end };
        for (excl.items) |x| {
            var out_i: usize = 0;
            var tmp: [17]Seg = undefined;
            for (segs[0..nsegs]) |sg| {
                if (x.e <= sg.s or x.s >= sg.e or nsegs >= 16) {
                    if (out_i < tmp.len) {
                        tmp[out_i] = sg;
                        out_i += 1;
                    }
                    continue;
                }
                if (x.s > sg.s and out_i < tmp.len) {
                    tmp[out_i] = .{ .s = sg.s, .e = x.s };
                    out_i += 1;
                }
                if (x.e < sg.e and out_i < tmp.len) {
                    tmp[out_i] = .{ .s = x.e, .e = sg.e };
                    out_i += 1;
                }
            }
            @memcpy(segs[0..out_i], tmp[0..out_i]);
            nsegs = out_i;
            if (nsegs == 0) break;
        }
        if (nsegs == 0) continue;
        const handlers = try dex.readCatchHandlers(gpa, d, code, t.handler_off);
        defer gpa.free(handlers);
        for (handlers) |h| {
            // Every handler routes through a trampoline appended after
            // the code. JVMS delivers the exception object on the stack
            // with EMPTY registers-as-locals expectations, but our
            // linear register tracking reflects the fallthrough path;
            // a handler pc inside straight-line code would merge the
            // exception-entry state with fallthrough locals and fail
            // verification (e.g. int/ref conflicts on shared regs).
            // With move-exception: pop the duplicate into the target
            // local via a dedicated store (stack is [exc], handler code
            // expects it consumed). Without (R8-dropped): pop, goto.
            const hdex: usize = h.addr;
            const hat_move_exc = hdex < insns.len and insns[hdex] & 0xFF == @backingInt(Op.move_exception);
            if (e.max_stack < 1) e.max_stack = 1;
            const tramp: u32 = @intCast(e.code.items.len);
            if (hat_move_exc) {
                // Stack [exc]: dup, store to the move-exception dest,
                // pop the original, then fall into the handler body
                // AFTER the move-exception unit.
                const dest = reg8(insns[hdex]);
                try e.emit(gpa, 0x59); // dup
                e.push(1);
                // Conflict-aware: ref values land in the ref slot.
                try emitStoreRef(&e, gpa, rm, dest);
                try e.emit(gpa, 0x57); // pop
                // Fall through to the unit after move-exception.
                const next_dex: u32 = h.addr + 1;
                if (e.dex_to_jvm.get(next_dex)) |_| {
                    try e.emitBranchWide(gpa, next_dex);
                } else {
                    try e.emit(gpa, 0xb1); // return (handler is just move-exception)
                }
                try e.resolve();
            } else {
                try e.emit(gpa, 0x57); // pop
                try e.emitBranchWide(gpa, h.addr);
                try e.resolve();
            }
            const hpc = tramp;
            const ct: ?u32 = if (h.type_idx == std.math.maxInt(u32)) null else h.type_idx;
            for (segs[0..nsegs]) |sg| {
                const start = e.dex_to_jvm.get(sg.s) orelse return error.BadBranch;
                // end_pc: map the first unit past the segment; fall back
                // to code end.
                const end = e.dex_to_jvm.get(sg.e) orelse @as(u32, @intCast(e.code.items.len));
                if (end <= start) continue;
                try exceptions.append(gpa, .{
                    .start_pc = start,
                    .end_pc = end,
                    .handler_pc = hpc,
                    .catch_type = ct,
                });
            }
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
    if (dex_flags & 0x40 != 0) out |= 0x0040; // volatile/bridge bit
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

/// Values from a DEX encoded_array_item that can be represented by the JVM
/// ConstantValue attribute.  The translator previously dropped the entire
/// static_values_off section, which left initialized static ints at zero.
fn readStaticIntValues(gpa: std.mem.Allocator, d: dex.Dex, off: u32) TranslateError![]?i32 {
    if (off == 0) return &.{};
    var pos: usize = off;
    const count = try dex.readUleb128(d.data, &pos);
    if (count > 1 << 20) return error.BadDescriptor;
    const out = try gpa.alloc(?i32, count);
    errdefer gpa.free(out);
    for (out) |*slot| {
        if (pos >= d.data.len) return error.BadDescriptor;
        const header = d.data[pos];
        pos += 1;
        const kind = header & 0x1f;
        const arg = header >> 5;
        slot.* = null;
        if (kind == 0x1f) { // boolean: value is encoded in value_arg
            slot.* = if (arg == 0) 0 else 1;
            continue;
        }
        const width: usize = @as(usize, arg) + 1;
        if (pos + width > d.data.len) return error.BadDescriptor;
        var bits: u32 = 0;
        if (width <= 4) {
            for (0..width) |i| bits |= @as(u32, d.data[pos + i]) << @intCast(i * 8);
        }
        pos += width;
        switch (kind) {
            0x00 => slot.* = @as(i8, @bitCast(@as(u8, @intCast(bits & 0xff)))), // byte
            0x02 => slot.* = @as(i16, @bitCast(@as(u16, @intCast(bits & 0xffff)))), // short
            0x03, 0x04 => slot.* = @bitCast(bits), // char/int
            else => {}, // long/float/double/string/object: no int ConstantValue
        }
    }
    return out;
}

fn supportsIntConstantValue(desc: []const u8) bool {
    return desc.len == 1 and switch (desc[0]) {
        'Z', 'B', 'S', 'C', 'I' => true,
        else => false,
    };
}

/// Param slot classes of a JVM method descriptor's parameter section.
/// Returns the load opcode per param (aload/iload/fload/lload/dload) plus total slots.
/// Forward ctors pass JVM-typed params straight through (real floats/doubles),
/// unlike translated DEX bodies whose locals hold int/long bits.
fn descParamLoads(desc: []const u8, out: []u8) struct { n: usize, slots: u16 } {
    // desc = (params)ret; out[i] = load op for param i.
    var n: usize = 0;
    var slots: u16 = 1; // slot 0 = this
    var i: usize = 1; // skip '('
    while (i < desc.len and desc[i] != ')') {
        if (n >= out.len) break;
        const c = desc[i];
        if (c == 'L') {
            while (i < desc.len and desc[i] != ';') i += 1;
            i += 1;
            out[n] = 0x19; // aload
            n += 1;
            slots += 1;
        } else if (c == '[') {
            while (i < desc.len and desc[i] == '[') i += 1;
            if (i < desc.len and desc[i] == 'L') {
                while (i < desc.len and desc[i] != ';') i += 1;
                i += 1;
            } else {
                i += 1;
            }
            out[n] = 0x19; // aload
            n += 1;
            slots += 1;
        } else {
            i += 1;
            if (c == 'J') {
                out[n] = 0x16; // lload
                n += 1;
                slots += 2;
            } else if (c == 'D') {
                out[n] = 0x18; // dload
                n += 1;
                slots += 2;
            } else if (c == 'F') {
                out[n] = 0x17; // fload (forward passes the real float through)
                n += 1;
                slots += 1;
            } else {
                out[n] = 0x15; // iload (I,Z,B,S,C)
                n += 1;
                slots += 1;
            }
        }
    }
    return .{ .n = n, .slots = slots };
}

/// True when the DEX class declares <init> with this JVM descriptor.
fn dexDeclaresInit(gpa: std.mem.Allocator, d: dex.Dex, def: dex.ClassDef, desc: []const u8) bool {
    var cdata = dex.readClassData(gpa, d, def.class_data_off) catch return false;
    defer cdata.deinit(gpa);
    for (cdata.direct_methods) |m| {
        const mid = d.methodId(m.method_idx) catch continue;
        const mname = d.getString(gpa, mid.name_idx) catch continue;
        defer gpa.free(mname);
        if (!std.mem.eql(u8, mname, "<init>")) continue;
        const mdesc = protoDescriptor(gpa, d, mid.proto_idx) catch continue;
        defer gpa.free(mdesc);
        if (std.mem.eql(u8, mdesc, desc)) return true;
    }
    return false;
}

/// Internal name of a class_def's superclass, or null for none/Object.
/// Caller owns the result.
fn dexSuperInternal(gpa: std.mem.Allocator, d: dex.Dex, def: dex.ClassDef) TranslateError!?[]u8 {
    if (def.superclass_idx == dex.no_index) return null;
    const sdesc = try d.getTypeDescriptor(gpa, def.superclass_idx);
    defer gpa.free(sdesc);
    return try descriptorToInternal(gpa, sdesc);
}

/// class_def index by internal name, for the driver's forward pass.
pub fn dexDefIndexByInternal(d: dex.Dex, gpa: std.mem.Allocator, internal: []const u8) ?usize {
    const found = dexDefByInternal(d, gpa, internal) orelse return null;
    return found.idx;
}

/// Find a class_def by internal name (linear; classes are few per DEX
/// relative to methods, and forwards are rare).
fn dexDefByInternal(d: dex.Dex, gpa: std.mem.Allocator, internal: []const u8) ?struct { idx: usize, def: dex.ClassDef } {
    for (0..d.header.class_defs_size) |i| {
        const def = d.classDef(i) catch continue;
        const desc = d.getTypeDescriptor(gpa, def.class_idx) catch continue;
        defer gpa.free(desc);
        const want_pre = if (std.mem.eql(u8, internal, "java/lang/Object")) "Ljava/lang/Object;" else null;
        if (want_pre) |wp| {
            if (std.mem.eql(u8, desc, wp)) return .{ .idx = i, .def = def };
            continue;
        }
        // Descriptor from internal: L + internal + ; (no array class_defs).
        if (desc.len == internal.len + 2 and desc[0] == 'L' and desc[desc.len - 1] == ';' and
            std.mem.eql(u8, desc[1 .. desc.len - 1], internal))
        {
            return .{ .idx = i, .def = def };
        }
    }
    return null;
}

/// Expand collected forward needs into per-class extra-ctor descriptor
/// lists, propagating up DEX hierarchies: a forward in C calls C's
/// direct super, so when the super (in-DEX) also lacks the desc it
/// needs its own forward, up to the declarer. Needs whose declarer is
/// external (framework/JDK) terminate at the external boundary (the
/// external class links as-is). Result maps internal class name ->
/// desc list; caller owns (deep). Stops ascending past 64 levels
/// (malformed hierarchy: the runtime error stays loud, as today).
pub fn planInheritedForwards(
    gpa: std.mem.Allocator,
    d: dex.Dex,
    needs: *const ForwardNeeds,
) TranslateError!std.StringHashMapUnmanaged(std.ArrayListUnmanaged([]const u8)) {
    var plan = std.StringHashMapUnmanaged(std.ArrayListUnmanaged([]const u8)){};
    errdefer {
        var it = plan.iterator();
        while (it.next()) |e| {
            for (e.value_ptr.items) |ed| gpa.free(ed);
            e.value_ptr.deinit(gpa);
            gpa.free(e.key_ptr.*);
        }
        plan.deinit(gpa);
    }
    // Index defs by internal name once (linear scan per need is
    // quadratic on big DEXes with unconditional recording).
    var def_by_name = std.StringHashMapUnmanaged(dex.ClassDef){};
    defer {
        var it = def_by_name.iterator();
        while (it.next()) |e| gpa.free(e.key_ptr.*);
        def_by_name.deinit(gpa);
    }
    for (0..d.header.class_defs_size) |i| {
        const def = d.classDef(i) catch continue;
        const desc = d.getTypeDescriptor(gpa, def.class_idx) catch continue;
        defer gpa.free(desc);
        if (desc.len < 3 or desc[0] != 'L' or desc[desc.len - 1] != ';') continue;
        const internal = gpa.dupe(u8, desc[1 .. desc.len - 1]) catch continue;
        const gop = def_by_name.getOrPut(gpa, internal) catch {
            gpa.free(internal);
            continue;
        };
        if (gop.found_existing) {
            gpa.free(internal);
        } else {
            gop.key_ptr.* = internal;
            gop.value_ptr.* = def;
        }
    }
    // Declared-<init> descriptor sets per def index (parsed once).
    const init_sets = gpa.alloc(?std.StringHashMapUnmanaged(void), d.header.class_defs_size) catch return error.OutOfMemory;
    defer {
        for (init_sets) |*slot| {
            if (slot.*) |*m| {
                var it = m.iterator();
                while (it.next()) |e| gpa.free(e.key_ptr.*);
                m.deinit(gpa);
            }
        }
        gpa.free(init_sets);
    }
    @memset(init_sets, null);
    const declaresInit = struct {
        fn f(
            alloc: std.mem.Allocator,
            dd: dex.Dex,
            defs: std.StringHashMapUnmanaged(dex.ClassDef),
            sets: []?std.StringHashMapUnmanaged(void),
            internal: []const u8,
            desc: []const u8,
        ) bool {
            const def = defs.get(internal) orelse return false;
            // Find the def index by identity (class_idx unique).
            var def_idx: ?usize = null;
            for (0..dd.header.class_defs_size) |i| {
                const cand = dd.classDef(i) catch continue;
                if (cand.class_idx == def.class_idx) {
                    def_idx = i;
                    break;
                }
            }
            const di = def_idx orelse return false;
            if (sets[di] == null) {
                var m = std.StringHashMapUnmanaged(void){};
                var cdata = dex.readClassData(alloc, dd, def.class_data_off) catch return false;
                defer cdata.deinit(alloc);
                for (cdata.direct_methods) |mth| {
                    const mid = dd.methodId(mth.method_idx) catch continue;
                    const mname = dd.getString(alloc, mid.name_idx) catch continue;
                    defer alloc.free(mname);
                    if (!std.mem.eql(u8, mname, "<init>")) continue;
                    const mdesc = protoDescriptor(alloc, dd, mid.proto_idx) catch continue;
                    errdefer alloc.free(mdesc);
                    const gop = m.getOrPut(alloc, mdesc) catch {
                        alloc.free(mdesc);
                        continue;
                    };
                    if (gop.found_existing) alloc.free(mdesc);
                }
                sets[di] = m;
            }
            return sets[di].?.contains(desc);
        }
    }.f;
    for (needs.needs.items) |n| {
        // Walk child + ancestors (bounded; malformed cycles keep
        // today's loud runtime error), planning a forward in each
        // in-DEX class up to (excluding) the declarer. External
        // classes link as-is (their ctors are explicit).
        var cur_buf: ?[]u8 = null;
        defer if (cur_buf) |b| gpa.free(b);
        var cur_name: []const u8 = n.child;
        var depth: usize = 0;
        while (depth < 64) : (depth += 1) {
            const cdef = def_by_name.get(cur_name) orelse break;
            if (declaresInit(gpa, d, def_by_name, init_sets, cur_name, n.desc)) break;
            const gop = try plan.getOrPut(gpa, cur_name);
            if (!gop.found_existing) {
                gop.key_ptr.* = try gpa.dupe(u8, cur_name);
                gop.value_ptr.* = .empty;
            }
            var dup = false;
            for (gop.value_ptr.items) |ed| {
                if (std.mem.eql(u8, ed, n.desc)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) try gop.value_ptr.append(gpa, try gpa.dupe(u8, n.desc));
            const sup = try dexSuperInternal(gpa, d, cdef);
            defer if (sup) |sb| gpa.free(sb);
            if (sup == null) break;
            if (cur_buf) |b| gpa.free(b);
            cur_buf = try gpa.dupe(u8, sup.?);
            cur_name = cur_buf.?;
        }
    }
    return plan;
}

/// Translate one DEX class_def to a JVM class file (major v49, pure inference verification).
/// Caller owns the result. `class_name` used in errors only.
/// `forward_needs` collects inherited-ctor forward needs (null to skip);
/// `extra_ctors` synthesizes forwarding <init>s for these JVM descriptors
/// (from a prior collection pass; see planInheritedForwards).
pub fn translateClass(
    gpa: std.mem.Allocator,
    d: dex.Dex,
    def_idx: usize,
    forward_needs: ?*ForwardNeeds,
    extra_ctors: []const []const u8,
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
    var super_internal_opt: ?[]const u8 = null;
    var super_internal_buf: ?[]u8 = null;
    defer if (super_internal_buf) |b| gpa.free(b);
    if (def.superclass_idx != dex.no_index) {
        const sdesc = try d.getTypeDescriptor(gpa, def.superclass_idx);
        defer gpa.free(sdesc);
        const sinternal = try descriptorToInternal(gpa, sdesc);
        defer gpa.free(sinternal);
        super_cls = try cw.class(gpa, sinternal);
        super_internal_buf = try gpa.dupe(u8, sinternal);
        super_internal_opt = super_internal_buf;
    } else {
        super_cls = try cw.class(gpa, "java/lang/Object");
    }

    // Fields: instance + static.
    const FieldOut = struct { name: []const u8, desc: []const u8, flags: u16, constant: ?i32 };
    const static_values = try readStaticIntValues(gpa, d, def.static_values_off);
    defer if (static_values.len > 0) gpa.free(static_values);
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
            .constant = null,
        });
    }
    for (cdata.static_fields, 0..) |f, fi| {
        const fid = try d.fieldId(f.field_idx);
        const fname = try d.getString(gpa, fid.name_idx);
        errdefer gpa.free(fname);
        const fdesc = try d.getTypeDescriptor(gpa, fid.type_idx);
        errdefer gpa.free(fdesc);
        try fields.append(gpa, .{
            .name = fname,
            .desc = fdesc,
            .flags = dexFlagsToJvm(f.access_flags) | 0x0008,
            .constant = if (fi < static_values.len and supportsIntConstantValue(fdesc)) static_values[fi] else null,
        });
    }

    // Methods: translate bodies, collect const uses.
    var methods: std.ArrayListUnmanaged(MethodOut) = .empty;
    // Parallel to methods: injected ctors (default/enum/forwards) own a
    // duped raw_method cls; real method bodies use literal clses.
    var injected: std.ArrayListUnmanaged(bool) = .empty;
    defer {
        for (methods.items, 0..) |*m, mi| {
            if (mi < injected.items.len and injected.items[mi]) for (m.tm.uses) |u| {
                if (u.ref == .raw_method) gpa.free(@constCast(u.ref.raw_method.cls));
            };
            gpa.free(m.name);
            gpa.free(m.desc);
            m.tm.deinit(gpa);
        }
        methods.deinit(gpa);
        injected.deinit(gpa);
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
                const is_ctor = std.mem.eql(u8, mname, "<init>") and m.access_flags & 0x8 == 0;
                const ctor_this: ?u16 = if (is_ctor and c.ins <= c.registers)
                    @intCast(c.registers - c.ins)
                else
                    null;
                tm = translateMethod(gpa, d, c, mid.proto_idx, m.access_flags & 0x8 != 0, super_internal_opt, internal, ctor_this, forward_needs) catch |err| {
                    std.debug.print("translation failed: {s}.{s}{s}: {t}\n", .{ internal, mname, mdesc, err });
                    return err;
                };
                has_body = true;
            } else {
                tm = .{ .code = &.{}, .max_stack = 0, .max_locals = 0, .uses = &.{}, .exceptions = &.{}, .lines = &.{} };
            }
            errdefer if (has_body) tm.deinit(gpa);
            try methods.append(gpa, .{
                .name = mname,
                .desc = mdesc,
                .flags = dexFlagsToJvm(m.access_flags),
                .tm = tm,
            });
            try injected.append(gpa, false);
        }
    }

    // R8 drops trivial constructors; JVM allocation still needs one.
    // Inject `ctor()V: aload_0; invokespecial super.<init>; return`
    // when no <init> exists (never for interfaces or java/lang/Object).
    {
        var has_init = false;
        for (methods.items) |m| {
            if (std.mem.eql(u8, m.name, "<init>")) {
                has_init = true;
                break;
            }
        }
        const is_iface = def.access_flags & 0x200 != 0;
        const is_object = std.mem.eql(u8, internal, "java/lang/Object");
        // Enum without <init>: R8 strips the (String,int) ctor. Inject
        // `ctor(String,I)V: aload_0, aload_1, iload_2, invokespecial
        // super.<init>(String,I), return` so <clinit>'s allocations link.
        const is_enum = def.access_flags & 0x4000 != 0;
        if (!has_init and !is_iface and !is_object and is_enum) {
            const sdesc = try d.getTypeDescriptor(gpa, def.superclass_idx);
            defer gpa.free(sdesc);
            const sinternal = try descriptorToInternal(gpa, sdesc);
            defer gpa.free(sinternal);
            const code = try gpa.alloc(u8, 7);
            errdefer gpa.free(code);
            code[0] = 0x2a; // aload_0
            code[1] = 0x2b; // aload_1
            code[2] = 0x1c; // iload_2
            code[3] = 0xb7; // invokespecial
            code[4] = 0;
            code[5] = 0;
            code[6] = 0xb1; // return
            const uses = try gpa.alloc(ConstUse, 1);
            errdefer gpa.free(uses);
            uses[0] = .{ .at = 4, .wide = true, .ref = .{ .raw_method = .{ .cls = sinternal, .name = "<init>", .desc = "(Ljava/lang/String;I)V" } } };
            const cls_dupe = try gpa.dupe(u8, sinternal);
            uses[0].ref.raw_method.cls = cls_dupe;
            const cname = try gpa.dupe(u8, "<init>");
            errdefer gpa.free(cname);
            const cdesc = try gpa.dupe(u8, "(Ljava/lang/String;I)V");
            errdefer gpa.free(cdesc);
            const ctm = TranslatedMethod{ .code = code, .max_stack = 2, .max_locals = 3, .uses = uses, .exceptions = &.{}, .lines = &.{} };
            errdefer {
                gpa.free(cls_dupe);
                gpa.free(code);
                gpa.free(uses);
            }
            try methods.append(gpa, .{ .name = cname, .desc = cdesc, .flags = 0x0002, .tm = ctm });
            try injected.append(gpa, true);
            has_init = true;
        }
        // Default ()V only when the super declares it (in-DEX scan) or
        // is external (framework/JDK ctors are explicit): otherwise the
        // injected super-call would not link, and the forward pass owns
        // any genuinely needed proto.
        if (!has_init and !is_iface and !is_object) super_check: {
            if (def.superclass_idx != dex.no_index) {
                const sdesc0 = try d.getTypeDescriptor(gpa, def.superclass_idx);
                defer gpa.free(sdesc0);
                const sinternal0 = try descriptorToInternal(gpa, sdesc0);
                defer gpa.free(sinternal0);
                if (dexDefByInternal(d, gpa, sinternal0)) |sdef| {
                    if (!dexDeclaresInit(gpa, d, sdef.def, "()V")) break :super_check;
                }
            }
            const sdesc = try d.getTypeDescriptor(gpa, def.superclass_idx);
            defer gpa.free(sdesc);
            const sinternal = try descriptorToInternal(gpa, sdesc);
            defer gpa.free(sinternal);
            const code = try gpa.alloc(u8, 5);
            errdefer gpa.free(code);
            code[0] = 0x2a; // aload_0
            code[1] = 0xb7; // invokespecial
            code[2] = 0; // pool index patched below
            code[3] = 0;
            code[4] = 0xb1; // return
            const uses = try gpa.alloc(ConstUse, 1);
            errdefer gpa.free(uses);
            uses[0] = .{ .at = 2, .wide = true, .ref = .{ .raw_method = .{ .cls = sinternal, .name = "<init>", .desc = "()V" } } };
            // raw_method borrows sinternal; dupe for the use lifetime.
            const cls_dupe = try gpa.dupe(u8, sinternal);
            uses[0].ref.raw_method.cls = cls_dupe;
            const cname = try gpa.dupe(u8, "<init>");
            errdefer gpa.free(cname);
            const cdesc = try gpa.dupe(u8, "()V");
            errdefer gpa.free(cdesc);
            const ctm = TranslatedMethod{ .code = code, .max_stack = 1, .max_locals = 1, .uses = uses, .exceptions = &.{}, .lines = &.{} };
            errdefer {
                gpa.free(cls_dupe);
                gpa.free(code);
                gpa.free(uses);
            }
            try methods.append(gpa, .{ .name = cname, .desc = cdesc, .flags = 0x0001, .tm = ctm });
            try injected.append(gpa, true);
        }
    }

    // Inherited-ctor forwards (extra_ctors from planInheritedForwards):
    // `ctor(params): aload_0, loads..., invokespecial super.<init>,
    // return`. Skipped when the class already declares the desc.
    for (extra_ctors) |edesc| {
        var have = false;
        for (methods.items) |m| {
            if (std.mem.eql(u8, m.name, "<init>") and std.mem.eql(u8, m.desc, edesc)) {
                have = true;
                break;
            }
        }
        if (have) continue;
        if (def.superclass_idx == dex.no_index) continue;
        const sdesc = try d.getTypeDescriptor(gpa, def.superclass_idx);
        defer gpa.free(sdesc);
        const sinternal = try descriptorToInternal(gpa, sdesc);
        defer gpa.free(sinternal);
        var loads: [256]u8 = undefined;
        const pl = descParamLoads(edesc, &loads);
        // Code: aload_0 + per-param load (1-3 bytes: short forms for
        // slots 0-3, wide prefix past 255) + invokespecial + return.
        // Param slots start at 1; slot count bounded by 256 params.
        var code = std.ArrayListUnmanaged(u8).empty;
        errdefer code.deinit(gpa);
        try code.append(gpa, 0x2a); // aload_0
        var slot: u16 = 1;
        for (loads[0..pl.n]) |lop| {
            const wide_op = lop == 0x16 or lop == 0x18;
            if (slot < 4) {
                // iload/fload/aload/lload/dload short forms for slots 1..3.
                const base: u8 = if (lop == 0x19) 0x2a // aload_n
                    else if (lop == 0x16) 0x1e // lload_n
                    else if (lop == 0x18) 0x26 // dload_n
                    else if (lop == 0x17) 0x22 // fload_n
                    else 0x1a; // iload_n
                try code.append(gpa, base + @as(u8, @intCast(slot)));
            } else if (slot < 256) {
                try code.append(gpa, lop);
                try code.append(gpa, @intCast(slot));
            } else {
                try code.append(gpa, 0xc4); // wide
                try code.append(gpa, lop);
                try code.append(gpa, @intCast(slot >> 8));
                try code.append(gpa, @intCast(slot & 0xFF));
            }
            slot += if (wide_op) 2 else 1;
        }
        const at: u32 = @intCast(code.items.len + 1);
        try code.append(gpa, 0xb7); // invokespecial
        try code.append(gpa, 0);
        try code.append(gpa, 0);
        try code.append(gpa, 0xb1); // return
        const uses = try gpa.alloc(ConstUse, 1);
        errdefer gpa.free(uses);
        uses[0] = .{ .at = at, .wide = true, .ref = .{ .raw_method = .{ .cls = "", .name = "<init>", .desc = edesc } } };
        uses[0].ref.raw_method.cls = try gpa.dupe(u8, sinternal);
        errdefer gpa.free(@constCast(uses[0].ref.raw_method.cls));
        const cname = try gpa.dupe(u8, "<init>");
        errdefer gpa.free(cname);
        const cdesc = try gpa.dupe(u8, edesc);
        errdefer gpa.free(cdesc);
        const ctm = TranslatedMethod{
            .code = try code.toOwnedSlice(gpa),
            .max_stack = pl.slots,
            .max_locals = pl.slots,
            .uses = uses,
            .exceptions = &.{},
            .lines = &.{},
        };
        errdefer {
            gpa.free(@constCast(uses[0].ref.raw_method.cls));
            gpa.free(ctm.code);
            gpa.free(uses);
        }
        try methods.append(gpa, .{ .name = cname, .desc = cdesc, .flags = 0x0001, .tm = ctm });
        try injected.append(gpa, true);
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
                .method => |mr| blk: {
                    const mid = try d.methodId(mr.idx);
                    const cd = try d.getTypeDescriptor(gpa, mid.class_idx);
                    defer gpa.free(cd);
                    const ci = try descriptorToInternal(gpa, cd);
                    defer gpa.free(ci);
                    const mn = try d.getString(gpa, mid.name_idx);
                    defer gpa.free(mn);
                    const md = try protoDescriptor(gpa, d, mid.proto_idx);
                    defer gpa.free(md);
                    // Interface-ness: forced by invoke-interface (DEX-level
                    // truth for external classes); else look at the class
                    // def, default class ref. JDK interfaces called via
                    // invoke-super (default methods like Map.getOrDefault)
                    // need InterfaceMethodref even under invokespecial.
                    var is_iface = mr.iface;
                    for (0..d.header.class_defs_size) |ci2| {
                        const dd = try d.classDef(ci2);
                        if (dd.class_idx == mid.class_idx) {
                            is_iface = is_iface or (dd.access_flags & 0x200 != 0);
                            break;
                        }
                    }
                    if (!is_iface) {
                        is_iface = isJdkInterface(ci);
                    }
                    break :blk try cw.methodref(gpa, ci, mn, md, is_iface);
                },
                .init => |ir| blk: {
                    // Allocation-site owner: the new-instance type, NOT
                    // the DEX-declared (possibly supertype) owner.
                    const mid = try d.methodId(ir.idx);
                    const mn = try d.getString(gpa, mid.name_idx);
                    defer gpa.free(mn);
                    const md = try protoDescriptor(gpa, d, mid.proto_idx);
                    defer gpa.free(md);
                    break :blk try cw.methodref(gpa, ir.owner, mn, md, false);
                },
                .super_init => |ir| blk: {
                    // Direct-super owner for this/super <init> calls R8
                    // rewrote to a skipped ancestor (JVM requires the
                    // current class or direct super; skipped ancestors
                    // are provably trivial).
                    const mid = try d.methodId(ir.idx);
                    const mn = try d.getString(gpa, mid.name_idx);
                    defer gpa.free(mn);
                    const md = try protoDescriptor(gpa, d, mid.proto_idx);
                    defer gpa.free(md);
                    break :blk try cw.methodref(gpa, ir.owner, mn, md, false);
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
    const constant_value_attr = try cw.utf8(gpa, "ConstantValue");
    var field_name_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer field_name_idx.deinit(gpa);
    var field_desc_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer field_desc_idx.deinit(gpa);
    for (fields.items) |f| {
        try field_name_idx.append(gpa, try cw.utf8(gpa, f.name));
        try field_desc_idx.append(gpa, try cw.utf8(gpa, f.desc));
    }
    var field_constant_idx: std.ArrayListUnmanaged(u16) = .empty;
    defer field_constant_idx.deinit(gpa);
    for (fields.items) |f| {
        try field_constant_idx.append(gpa, if (f.constant) |v| try cw.integer(gpa, v) else 0);
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
        if (f.constant) |value| {
            _ = value;
            try ClassWriter.writeU16(&out, gpa, 1);
            try ClassWriter.writeU16(&out, gpa, constant_value_attr);
            try ClassWriter.writeU32(&out, gpa, 2);
            try ClassWriter.writeU16(&out, gpa, field_constant_idx.items[i]);
        } else {
            try ClassWriter.writeU16(&out, gpa, 0);
        }
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

