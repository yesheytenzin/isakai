// SPDX-License-Identifier: GPL-3.0-or-later

//! JNI bridge between `org.isakai.db.IsakaiSQLite` and the system
//! libsqlite3. Thin handle translation: Java owns open/close pairing;
//! statements must be finalized before close. No ThrowNew here — errors
//! surface as 0/negative returns plus `nErr` text, and the Java wrapper
//! raises typed exceptions (code 5 → Locked).
//!
//! Open flags mirror sqlite3_open_v2 bits accepted from Java: READONLY=1,
//! READWRITE=2, CREATE=4. FULLMUTEX (0x10000) is always added and a 5s
//! busy timeout is set. Database files are created 0600 (umask-safe
//! fallback: chmod after open when the file is new).
const std = @import("std");

/// Raw system libsqlite3 bindings: hand-declared extern fns in the
/// xkb.zig style (Zig 0.17 has no translate-c import). Linked via
/// `linkSystemLibrary("sqlite3")` in build.zig. Signatures match sqlite3.h.
const c = struct {
    pub const sqlite3 = opaque {};
    pub const sqlite3_stmt = opaque {};
    pub const SQLITE_VERSION = "3";
    pub const SQLITE_OK = 0;
    pub const SQLITE_ROW = 100;
    pub const SQLITE_DONE = 101;
    pub const SQLITE_BUSY = 5;
    pub const SQLITE_OPEN_READONLY: c_int = 0x00000001;
    pub const SQLITE_OPEN_READWRITE: c_int = 0x00000002;
    pub const SQLITE_OPEN_CREATE: c_int = 0x00000004;
    pub const SQLITE_OPEN_FULLMUTEX: c_int = 0x00010000;
    pub extern fn sqlite3_open_v2(filename: [*:0]const u8, ppDb: *?*sqlite3, flags: c_int, zVfs: ?[*:0]const u8) c_int;
    pub extern fn isakai_register_unicode_collation(db: ?*sqlite3) c_int;
    pub extern fn sqlite3_clear_bindings(stmt: ?*sqlite3_stmt) c_int;
    pub extern fn sqlite3_close(db: ?*sqlite3) c_int;
    pub extern fn sqlite3_errmsg(db: ?*sqlite3) ?[*:0]const u8;
    pub extern fn sqlite3_exec(db: ?*sqlite3, sql: [*:0]const u8, callback: ?*const anyopaque, arg: ?*anyopaque, errmsg: *?[*:0]u8) c_int;
    pub extern fn sqlite3_prepare_v2(db: ?*sqlite3, zSql: [*:0]const u8, nByte: c_int, ppStmt: *?*sqlite3_stmt, pzTail: ?*const anyopaque) c_int;
    pub extern fn sqlite3_finalize(stmt: ?*sqlite3_stmt) c_int;
    pub extern fn sqlite3_reset(stmt: ?*sqlite3_stmt) c_int;
    pub extern fn sqlite3_bind_null(stmt: ?*sqlite3_stmt, index: c_int) c_int;
    pub extern fn sqlite3_bind_int64(stmt: ?*sqlite3_stmt, index: c_int, value: i64) c_int;
    pub extern fn sqlite3_bind_double(stmt: ?*sqlite3_stmt, index: c_int, value: f64) c_int;
    pub extern fn sqlite3_bind_text(stmt: ?*sqlite3_stmt, index: c_int, value: [*:0]const u8, n: c_int, destructor: ?*const anyopaque) c_int;
    pub extern fn sqlite3_bind_blob(stmt: ?*sqlite3_stmt, index: c_int, value: ?*const anyopaque, n: c_int, destructor: ?*const anyopaque) c_int;
    pub extern fn sqlite3_step(stmt: ?*sqlite3_stmt) c_int;
    pub extern fn sqlite3_column_count(stmt: ?*sqlite3_stmt) c_int;
    pub extern fn sqlite3_column_name(stmt: ?*sqlite3_stmt, col: c_int) ?[*:0]const u8;
    pub extern fn sqlite3_column_type(stmt: ?*sqlite3_stmt, col: c_int) c_int;
    pub extern fn sqlite3_column_int64(stmt: ?*sqlite3_stmt, col: c_int) i64;
    pub extern fn sqlite3_column_double(stmt: ?*sqlite3_stmt, col: c_int) f64;
    pub extern fn sqlite3_column_text(stmt: ?*sqlite3_stmt, col: c_int) ?[*]const u8;
    pub extern fn sqlite3_column_blob(stmt: ?*sqlite3_stmt, col: c_int) ?*const anyopaque;
    pub extern fn sqlite3_column_bytes(stmt: ?*sqlite3_stmt, col: c_int) c_int;
    pub extern fn sqlite3_last_insert_rowid(db: ?*sqlite3) i64;
    pub extern fn sqlite3_changes(db: ?*sqlite3) c_int;
    pub extern fn sqlite3_busy_timeout(db: ?*sqlite3, ms: c_int) c_int;
    pub extern fn sqlite3_free(ptr: ?*anyopaque) void;
    pub extern fn chmod(path: [*:0]const u8, mode: c_uint) c_int;
};

const gpa = std.heap.c_allocator;

const JniInt = i32;
const JniLong = i64;
const JniDouble = f64;

const jni_table_len = 236;
const slot_new_string_utf: usize = 167;
const slot_get_string_utf_length: usize = 168;
const slot_get_string_utf_chars: usize = 169;
const slot_release_string_utf_chars: usize = 170;
const slot_get_array_length: usize = 171;
const slot_new_byte_array: usize = 176;
const slot_get_byte_array_region: usize = 200;
const slot_set_byte_array_region: usize = 208;
const slot_new_direct_byte_buffer: usize = 229;
const slot_throw_new: usize = 14;

const JniTable = [jni_table_len]?*const anyopaque;

fn jniFunctions(env: ?*anyopaque) ?*const JniTable {
    const penv: *?*const anyopaque = @ptrCast(@alignCast(env orelse return null));
    const iface = penv.* orelse return null;
    return @ptrCast(@alignCast(iface));
}

fn jniCall(comptime slot: usize, comptime Fn: type, fns: *const JniTable, env: ?*anyopaque, args: anytype) ?FnPayload(Fn) {
    const f: *const Fn = @ptrCast(fns[slot] orelse return null);
    const Ret = @typeInfo(Fn).@"fn".return_type.?;
    if (Ret == void) {
        @call(.auto, f, .{env} ++ args);
        return null;
    }
    const r: Ret = @call(.auto, f, .{env} ++ args);
    return r;
}

fn FnPayload(comptime Fn: type) type {
    const Ret = @typeInfo(Fn).@"fn".return_type.?;
    return switch (@typeInfo(Ret)) {
        .optional => |o| o.child,
        else => Ret,
    };
}

fn jniGetUtf(env: ?*anyopaque, fns: *const JniTable, s: ?*anyopaque) ?[*:0]const u8 {
    const Fn = fn (?*anyopaque, ?*anyopaque, ?*u8) callconv(.c) ?[*:0]const u8;
    return jniCall(slot_get_string_utf_chars, Fn, fns, env, .{ s, @as(?*u8, null) });
}

fn jniReleaseUtf(env: ?*anyopaque, fns: *const JniTable, s: ?*anyopaque, chars: [*:0]const u8) void {
    const Fn = fn (?*anyopaque, ?*anyopaque, [*:0]const u8) callconv(.c) void;
    _ = jniCall(slot_release_string_utf_chars, Fn, fns, env, .{ s, chars });
}

fn jniUtfLen(env: ?*anyopaque, fns: *const JniTable, s: ?*anyopaque) ?usize {
    const Fn = fn (?*anyopaque, ?*anyopaque) callconv(.c) usize;
    return jniCall(slot_get_string_utf_length, Fn, fns, env, .{s});
}

fn jniNewString(env: ?*anyopaque, fns: *const JniTable, bytes: []const u8) ?*anyopaque {
    const utf16 = std.unicode.utf8ToUtf16LeAlloc(gpa, bytes) catch return null;
    defer gpa.free(utf16);
    const len = std.math.cast(i32, utf16.len) orelse return null;
    const Fn = fn (?*anyopaque, [*]const u16, i32) callconv(.c) ?*anyopaque;
    return jniCall(163, Fn, fns, env, .{ utf16.ptr, len });
}

fn jniArrayLen(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque) ?JniInt {
    const Fn = fn (?*anyopaque, ?*anyopaque) callconv(.c) JniInt;
    return jniCall(slot_get_array_length, Fn, fns, env, .{arr});
}

fn jniNewByteArray(env: ?*anyopaque, fns: *const JniTable, len: JniInt) ?*anyopaque {
    const Fn = fn (?*anyopaque, JniInt) callconv(.c) ?*anyopaque;
    return jniCall(slot_new_byte_array, Fn, fns, env, .{len});
}

fn jniGetByteRegion(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque, start: JniInt, len: JniInt, buf: [*]u8) void {
    const Fn = fn (?*anyopaque, ?*anyopaque, JniInt, JniInt, [*]u8) callconv(.c) void;
    _ = jniCall(slot_get_byte_array_region, Fn, fns, env, .{ arr, start, len, buf });
}

fn jniSetByteRegion(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque, start: JniInt, len: JniInt, buf: [*]const u8) void {
    const Fn = fn (?*anyopaque, ?*anyopaque, JniInt, JniInt, [*]const u8) callconv(.c) void;
    _ = jniCall(slot_set_byte_array_region, Fn, fns, env, .{ arr, start, len, buf });
}

/// Copy a jstring to an owned NUL-terminated buffer. Null on null input
/// or allocation failure.
fn copyJString(env: ?*anyopaque, fns: *const JniTable, s: ?*anyopaque) ?[:0]u8 {
    if (s == null) return null;
    const GetLength = fn (?*anyopaque, ?*anyopaque) callconv(.c) i32;
    const len = jniCall(164, GetLength, fns, env, .{s}) orelse return null;
    if (len < 0) return null;
    const GetChars = fn (?*anyopaque, ?*anyopaque, ?*u8) callconv(.c) ?[*]const u16;
    const chars = jniCall(165, GetChars, fns, env, .{ s, @as(?*u8, null) }) orelse return null;
    defer {
        const Release = fn (?*anyopaque, ?*anyopaque, [*]const u16) callconv(.c) void;
        _ = jniCall(166, Release, fns, env, .{ s, chars });
    }
    const utf8 = std.unicode.utf16LeToUtf8Alloc(gpa, chars[0..@intCast(len)]) catch return null;
    defer gpa.free(utf8);
    return gpa.dupeSentinel(u8, utf8, 0) catch null;
}

/// Copy a jbyte[] to owned bytes. Null on null input or allocation failure.
fn copyJBytes(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque) ?[]u8 {
    if (arr == null) return null;
    const len = jniArrayLen(env, fns, arr) orelse return null;
    if (len < 0) return null;
    const n: usize = @intCast(len);
    const out = gpa.alloc(u8, n) catch return null;
    errdefer gpa.free(out);
    if (n > 0) jniGetByteRegion(env, fns, arr, 0, len, out.ptr);
    return out;
}

fn asDb(handle: JniLong) ?*c.sqlite3 {
    if (handle == 0) return null;
    return @ptrFromInt(@as(usize, @intCast(handle)));
}

fn asStmt(handle: JniLong) ?*c.sqlite3_stmt {
    if (handle == 0) return null;
    return @ptrFromInt(@as(usize, @intCast(handle)));
}

fn chmod0600(path: [:0]const u8) void {
    _ = c.chmod(path.ptr, 0o600);
}

export fn Java_org_isakai_db_IsakaiSQLite_nOpen(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    jpath: ?*anyopaque,
    jflags: JniInt,
) callconv(.c) JniLong {
    _ = clazz;
    if (jpath == null) return 0;
    const fns = jniFunctions(env) orelse return 0;
    const path = copyJString(env, fns, jpath) orelse return 0;
    defer gpa.free(path);
    // Probe existence first so we only chmod files we created.
    var existed: bool = true;
    {
        var stx: std.os.linux.Statx = undefined;
        const rc = std.os.linux.statx(std.os.linux.AT.FDCWD, path.ptr, 0, .{ .TYPE = true }, &stx);
        if (std.os.linux.errno(rc) == .NOENT) existed = false;
    }
    var flags: c_int = c.SQLITE_OPEN_FULLMUTEX;
    if ((jflags & 1) != 0) {
        flags |= c.SQLITE_OPEN_READONLY;
    } else {
        flags |= c.SQLITE_OPEN_READWRITE;
        if ((jflags & 4) != 0) flags |= c.SQLITE_OPEN_CREATE;
    }
    var db: ?*c.sqlite3 = null;
    const rc = c.sqlite3_open_v2(path.ptr, &db, flags, null);
    const opened = db orelse return 0;
    if (rc != c.SQLITE_OK) {
        _ = c.sqlite3_close(opened);
        return 0;
    }
    if (c.isakai_register_unicode_collation(opened) != c.SQLITE_OK) {
        _ = c.sqlite3_close(opened);
        return 0;
    }
    _ = c.sqlite3_busy_timeout(opened, 5000);
    if (!existed) chmod0600(path);
    return @intCast(@intFromPtr(opened));
}

export fn Java_org_isakai_db_IsakaiSQLite_nClose(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const db = asDb(handle) orelse return -1;
    const rc = c.sqlite3_close(db);
    return rc;
}

export fn Java_org_isakai_db_IsakaiSQLite_nErr(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    code: JniInt,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    const fns = jniFunctions(env) orelse return null;
    var msg: []const u8 = "";
    if (asDb(handle)) |db| {
        if (c.sqlite3_errmsg(db)) |r| msg = std.mem.span(r);
    }
    var buf: [256]u8 = undefined;
    const text = std.fmt.bufPrint(&buf, "{s} (code {d})", .{ msg, code }) catch "sqlite error";
    return jniNewString(env, fns, text);
}

export fn Java_org_isakai_db_IsakaiSQLite_nExec(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    jsql: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const db = asDb(handle) orelse return -1;
    if (jsql == null) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const sql = copyJString(env, fns, jsql) orelse return -1;
    defer gpa.free(sql);
    var errmsg: ?[*:0]u8 = null;
    // sqlite3_exec wants char* (mutable in the Zig decl); the call does not mutate.
    const rc = c.sqlite3_exec(db, sql.ptr, null, null, &errmsg);
    if (errmsg) |e| c.sqlite3_free(e);
    if (rc != c.SQLITE_OK) return -@as(JniInt, @intCast(rc));
    return 0;
}

export fn Java_org_isakai_db_IsakaiSQLite_nPrepare(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    jsql: ?*anyopaque,
) callconv(.c) JniLong {
    _ = clazz;
    const db = asDb(handle) orelse return 0;
    if (jsql == null) return 0;
    const fns = jniFunctions(env) orelse return 0;
    const sql = copyJString(env, fns, jsql) orelse return 0;
    defer gpa.free(sql);
    const nbyte: c_int = @intCast(sql.len + 1);
    var stmt: ?*c.sqlite3_stmt = null;
    const rc = c.sqlite3_prepare_v2(db, sql.ptr, nbyte, &stmt, null);
    const out = stmt orelse return 0;
    if (rc != c.SQLITE_OK) {
        _ = c.sqlite3_finalize(out);
        return 0;
    }
    return @intCast(@intFromPtr(out));
}

export fn Java_org_isakai_db_IsakaiSQLite_nFinalize(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_finalize(stmt);
}

export fn Java_org_isakai_db_IsakaiSQLite_nReset(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_reset(stmt);
}

export fn Java_org_isakai_db_IsakaiSQLite_nBindNull(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    index: JniInt,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_bind_null(stmt, index);
}

export fn Java_org_isakai_db_IsakaiSQLite_nBindLong(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    index: JniInt,
    value: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_bind_int64(stmt, index, value);
}

export fn Java_org_isakai_db_IsakaiSQLite_nBindDouble(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    index: JniInt,
    value: JniDouble,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_bind_double(stmt, index, value);
}

export fn Java_org_isakai_db_IsakaiSQLite_nBindText(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    index: JniInt,
    jvalue: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    if (jvalue == null) return c.sqlite3_bind_null(stmt, index);
    const fns = jniFunctions(env) orelse return -1;
    const text = copyJString(env, fns, jvalue) orelse return -1;
    defer gpa.free(text);
    const n: c_int = @intCast(text.len);
    // SQLITE_TRANSIENT (-1): sqlite copies the bytes before we free.
    const rc = c.sqlite3_bind_text(stmt, index, text.ptr, n, @ptrFromInt(@as(usize, @bitCast(@as(isize, -1)))));
    return rc;
}

export fn Java_org_isakai_db_IsakaiSQLite_nBindBlob(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    index: JniInt,
    jvalue: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    if (jvalue == null) return c.sqlite3_bind_null(stmt, index);
    const fns = jniFunctions(env) orelse return -1;
    const bytes = copyJBytes(env, fns, jvalue) orelse return -1;
    defer gpa.free(bytes);
    const n: c_int = @intCast(bytes.len);
    const ptr: ?*const anyopaque = if (bytes.len > 0) bytes.ptr else null;
    const rc = c.sqlite3_bind_blob(stmt, index, ptr, n, @ptrFromInt(@as(usize, @bitCast(@as(isize, -1)))));
    return rc;
}

export fn Java_org_isakai_db_IsakaiSQLite_nStep(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    const rc = c.sqlite3_step(stmt);
    if (rc == c.SQLITE_ROW) return 100;
    if (rc == c.SQLITE_DONE) return 101;
    return -@as(JniInt, @intCast(rc));
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnCount(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_column_count(stmt);
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnName(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    col: JniInt,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    const stmt = asStmt(handle) orelse return null;
    const fns = jniFunctions(env) orelse return null;
    const raw = c.sqlite3_column_name(stmt, col) orelse return null;
    return jniNewString(env, fns, std.mem.span(raw));
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnType(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    col: JniInt,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return -1;
    return c.sqlite3_column_type(stmt, col);
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnLong(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    col: JniInt,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return 0;
    return c.sqlite3_column_int64(stmt, col);
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnDouble(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    col: JniInt,
) callconv(.c) JniDouble {
    _ = env;
    _ = clazz;
    const stmt = asStmt(handle) orelse return 0;
    return c.sqlite3_column_double(stmt, col);
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnText(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    col: JniInt,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    const stmt = asStmt(handle) orelse return null;
    const fns = jniFunctions(env) orelse return null;
    const raw = c.sqlite3_column_text(stmt, col);
    if (raw == null) return null;
    const n: usize = @intCast(c.sqlite3_column_bytes(stmt, col));
    const bytes: [*]const u8 = @ptrCast(raw);
    return jniNewString(env, fns, bytes[0..n]);
}

export fn Java_org_isakai_db_IsakaiSQLite_nColumnBlob(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    col: JniInt,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    const stmt = asStmt(handle) orelse return null;
    const fns = jniFunctions(env) orelse return null;
    const n: usize = @intCast(c.sqlite3_column_bytes(stmt, col));
    if (n > std.math.maxInt(JniInt)) return null;
    const arr = jniNewByteArray(env, fns, @intCast(n)) orelse return null;
    if (n == 0) return arr;
    const raw = c.sqlite3_column_blob(stmt, col);
    if (raw == null) return arr;
    const bytes: [*]const u8 = @ptrCast(raw);
    jniSetByteRegion(env, fns, arr, 0, @intCast(n), bytes);
    return arr;
}

export fn Java_org_isakai_db_IsakaiSQLite_nLastRowId(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    const db = asDb(handle) orelse return 0;
    return c.sqlite3_last_insert_rowid(db);
}

export fn Java_org_isakai_db_IsakaiSQLite_nChanges(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    const db = asDb(handle) orelse return 0;
    return c.sqlite3_changes(db);
}

// Force-link: jni.zig imports this module so the sqlite natives land in
// libisakai_jni.so even though nothing calls back into Zig.
pub const sqlite_linked: bool = true;

test "sqlite bridge links against system libsqlite3" {
    try std.testing.expectEqualStrings("3", c.SQLITE_VERSION[0..1]);
    try std.testing.expect(c.SQLITE_ROW == 100);
    try std.testing.expect(c.SQLITE_DONE == 101);
    try std.testing.expect(c.SQLITE_BUSY == 5);
    try std.testing.expect(c.SQLITE_OPEN_FULLMUTEX == 0x00010000);
}

export fn Java_org_isakai_db_IsakaiSQLite_nClearBindings(env: ?*anyopaque, clazz: ?*anyopaque, handle: JniLong) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    return c.sqlite3_clear_bindings(asStmt(handle) orelse return -1);
}
