// SPDX-License-Identifier: GPL-3.0-or-later

//! JNI natives for `android.content.res.ResEngine`: the libandroidfw
//! resource backend (src/native/res_engine.cpp) below Resources,
//! AssetManager, Theme, TypedArray and XmlResourceParser.
//!
//! Handles are `long` pointers owned by the Java wrappers, each with an
//! explicit delete. Failure policy: lookups return false/null/-1/0 and the
//! Java side turns that into NotFoundException or IOException, so nothing
//! here throws. Value records are int[6]: cookie, type, data, resid,
//! changing configurations, density (cookie -1 means none).

const std = @import("std");

const Res = opaque {};
const Theme = opaque {};
const Xml = opaque {};
const Asset = opaque {};

const Value = extern struct {
    cookie: i32,
    type: u32,
    data: u32,
    resid: u32,
    flags: u32,
    density: u32,
};
const value_words = 6;

const c = struct {
    extern fn isakai_res_open(paths: [*]const [*:0]const u8, count: usize, system_count: usize) ?*Res;
    extern fn isakai_res_close(res: *Res) void;
    extern fn isakai_res_set_config(res: *Res, fields: [*]const i32, n: usize, locale: [*:0]const u8) void;
    extern fn isakai_res_get_value(res: *Res, resid: u32, resolve: bool, density: u16, out: *Value) bool;
    extern fn isakai_res_pool_string(res: *Res, cookie: i32, index: u32, len: *usize) ?[*]const u16;
    extern fn isakai_res_get_bag(res: *Res, resid: u32, keys: ?[*]u32, values: ?[*]Value, cap: usize) i32;
    extern fn isakai_res_name(res: *Res, resid: u32, buf: [*]u8, cap: usize) i32;
    extern fn isakai_res_identifier(res: *Res, name: [*:0]const u8, type: [*:0]const u8, package: [*:0]const u8) u32;
    extern fn isakai_theme_new(res: *Res) ?*Theme;
    extern fn isakai_theme_delete(theme: *Theme) void;
    extern fn isakai_theme_apply(theme: *Theme, style: u32, force: bool) bool;
    extern fn isakai_theme_set_to(dst: *Theme, src: *const Theme) bool;
    extern fn isakai_theme_attr(theme: *Theme, attr: u32, resolve: bool, out: *Value) bool;
    extern fn isakai_theme_changing_configurations(theme: *Theme) u32;
    extern fn isakai_apply_style(theme: *Theme, xml: ?*Xml, def_attr: u32, def_res: u32, attrs: [*]const u32, n: usize, out_values: [*]u32, out_indices: [*]u32) bool;
    extern fn isakai_retrieve_attributes(res: *Res, xml: *Xml, attrs: [*]u32, n: usize, out_values: [*]u32, out_indices: [*]u32) bool;
    extern fn isakai_xml_open(res: *Res, cookie: i32, path: [*:0]const u8) ?*Xml;
    extern fn isakai_xml_close(xml: *Xml) void;
    extern fn isakai_xml_next(xml: *Xml) i32;
    extern fn isakai_xml_current(xml: *Xml, out: ?[*]i32, cap: usize) usize;
    extern fn isakai_xml_string(xml: *Xml, index: u32, len: *usize) ?[*]const u16;
    extern fn isakai_asset_open(res: *Res, path: [*:0]const u8, mode: i32, out_cookie: *i32) ?*Asset;
    extern fn isakai_asset_read(asset: *Asset, buf: [*]u8, count: usize) i32;
    extern fn isakai_asset_seek(asset: *Asset, offset: i64, whence: i32) i64;
    extern fn isakai_asset_length(asset: *Asset) i64;
    extern fn isakai_asset_remaining(asset: *Asset) i64;
    extern fn isakai_asset_buffer(asset: *Asset) ?*const anyopaque;
    extern fn isakai_asset_is_allocated(asset: *Asset) bool;
    extern fn isakai_asset_fd(asset: *Asset, start: *i64, length: *i64) c_int;
    extern fn isakai_asset_range(asset: *Asset, start: *i64, length: *i64) bool;
    extern fn isakai_asset_close(asset: *Asset) void;
    extern fn isakai_asset_list(res: *Res, dir: [*:0]const u8, len: *usize) ?[*]u8;
    extern fn free(ptr: ?*anyopaque) void;
};

const gpa = std.heap.c_allocator;

const JniInt = i32;
const JniLong = i64;
const JniBool = u8;
const Env = ?*anyopaque;
const Obj = ?*anyopaque;

const jni_table_len = 236;
const JniTable = [jni_table_len]?*const anyopaque;

const slot_delete_local_ref: usize = 23;
const slot_get_object_class: usize = 31;
const slot_get_method_id: usize = 33;
const slot_call_long_method: usize = 52;
const slot_new_string: usize = 163;
const slot_get_string_length: usize = 164;
const slot_get_string_chars: usize = 165;
const slot_release_string_chars: usize = 166;
const slot_get_array_length: usize = 171;
const slot_get_object_array_element: usize = 173;
const slot_new_int_array: usize = 179;
const slot_get_int_array_region: usize = 203;
const slot_set_byte_array_region: usize = 208;
const slot_set_int_array_region: usize = 211;
const slot_set_long_array_region: usize = 212;

fn jniFunctions(env: Env) ?*const JniTable {
    const penv: *?*const anyopaque = @ptrCast(@alignCast(env orelse return null));
    const iface = penv.* orelse return null;
    return @ptrCast(@alignCast(iface));
}

fn jniCall(comptime slot: usize, comptime Fn: type, env: Env, args: anytype) ?FnPayload(Fn) {
    const fns = jniFunctions(env) orelse return null;
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

fn arrayLen(env: Env, arr: Obj) ?usize {
    if (arr == null) return null;
    const n = jniCall(slot_get_array_length, fn (Env, Obj) callconv(.c) JniInt, env, .{arr}) orelse return null;
    return std.math.cast(usize, n);
}

fn deleteLocal(env: Env, obj: Obj) void {
    _ = jniCall(slot_delete_local_ref, fn (Env, Obj) callconv(.c) void, env, .{obj});
}

fn getInts(env: Env, arr: Obj, out: []i32) void {
    if (out.len == 0) return;
    _ = jniCall(slot_get_int_array_region, fn (Env, Obj, JniInt, JniInt, [*]i32) callconv(.c) void, env, .{ arr, 0, @as(JniInt, @intCast(out.len)), out.ptr });
}

fn setInts(env: Env, arr: Obj, src: []const i32) void {
    if (src.len == 0) return;
    _ = jniCall(slot_set_int_array_region, fn (Env, Obj, JniInt, JniInt, [*]const i32) callconv(.c) void, env, .{ arr, 0, @as(JniInt, @intCast(src.len)), src.ptr });
}

fn newInts(env: Env, src: []const i32) Obj {
    const len = std.math.cast(JniInt, src.len) orelse return null;
    const arr = jniCall(slot_new_int_array, fn (Env, JniInt) callconv(.c) Obj, env, .{len}) orelse return null;
    setInts(env, arr, src);
    return arr;
}

fn newString16(env: Env, chars: [*]const u16, len: usize) Obj {
    const n = std.math.cast(JniInt, len) orelse return null;
    return jniCall(slot_new_string, fn (Env, [*]const u16, JniInt) callconv(.c) Obj, env, .{ chars, n }) orelse null;
}

fn newStringUtf8(env: Env, bytes: []const u8) Obj {
    const utf16 = std.unicode.utf8ToUtf16LeAlloc(gpa, bytes) catch return null;
    defer gpa.free(utf16);
    return newString16(env, utf16.ptr, utf16.len);
}

/// Copy a jstring to an owned NUL-terminated UTF-8 buffer; null for a null
/// string or allocation failure.
fn copyString(env: Env, s: Obj) ?[:0]u8 {
    if (s == null) return null;
    const len = jniCall(slot_get_string_length, fn (Env, Obj) callconv(.c) JniInt, env, .{s}) orelse return null;
    if (len < 0) return null;
    const chars = jniCall(slot_get_string_chars, fn (Env, Obj, ?*u8) callconv(.c) ?[*]const u16, env, .{ s, @as(?*u8, null) }) orelse return null;
    defer _ = jniCall(slot_release_string_chars, fn (Env, Obj, [*]const u16) callconv(.c) void, env, .{ s, chars });
    const utf8 = std.unicode.utf16LeToUtf8Alloc(gpa, chars[0..@intCast(len)]) catch return null;
    defer gpa.free(utf8);
    return gpa.dupeSentinel(u8, utf8, 0) catch null;
}

fn handle(comptime T: type, h: JniLong) ?*T {
    if (h == 0) return null;
    return @ptrFromInt(@as(usize, @bitCast(h)));
}

fn toHandle(p: anytype) JniLong {
    return @bitCast(@as(usize, @intFromPtr(p)));
}

fn writeValue(env: Env, out: Obj, v: Value) void {
    setInts(env, out, &.{ v.cookie, @bitCast(v.type), @bitCast(v.data), @bitCast(v.resid), @bitCast(v.flags), @bitCast(v.density) });
}

fn outValueArray(env: Env, out: Obj) bool {
    return (arrayLen(env, out) orelse 0) >= value_words;
}

const Ints = struct {
    words: []i32,
    fn copy(env: Env, arr: Obj) ?Ints {
        const n = arrayLen(env, arr) orelse return null;
        const words = gpa.alloc(i32, n) catch return null;
        getInts(env, arr, words);
        return .{ .words = words };
    }
    fn alloc(n: usize) ?Ints {
        const words = gpa.alloc(i32, n) catch return null;
        @memset(words, 0);
        return .{ .words = words };
    }
    fn deinit(self: Ints) void {
        gpa.free(self.words);
    }
    fn u32s(self: Ints) [*]u32 {
        return @ptrCast(self.words.ptr);
    }
};

const style_words = 7;

/// Archives in cookie order; the first `system_count` are framework archives.
export fn Java_android_content_res_ResEngine_nOpen(env: Env, _: Obj, jpaths: Obj, system_count: JniInt) callconv(.c) JniLong {
    const count = arrayLen(env, jpaths) orelse return 0;
    const systems = std.math.cast(usize, system_count) orelse return 0;
    if (count == 0 or systems > count) return 0;
    const paths = gpa.alloc([:0]u8, count) catch return 0;
    var filled: usize = 0;
    defer {
        for (paths[0..filled]) |p| gpa.free(p);
        gpa.free(paths);
    }
    const ptrs = gpa.alloc([*:0]const u8, count) catch return 0;
    defer gpa.free(ptrs);
    for (0..count) |i| {
        const elem = jniCall(slot_get_object_array_element, fn (Env, Obj, JniInt) callconv(.c) Obj, env, .{ jpaths, @as(JniInt, @intCast(i)) }) orelse return 0;
        defer deleteLocal(env, elem);
        paths[i] = copyString(env, elem) orelse return 0;
        filled += 1;
        ptrs[i] = paths[i].ptr;
    }
    const res = c.isakai_res_open(ptrs.ptr, count, systems) orelse return 0;
    return toHandle(res);
}

export fn Java_android_content_res_ResEngine_nClose(_: Env, _: Obj, h: JniLong) callconv(.c) void {
    if (handle(Res, h)) |res| c.isakai_res_close(res);
}

export fn Java_android_content_res_ResEngine_nSetConfiguration(env: Env, _: Obj, h: JniLong, jfields: Obj, jlocale: Obj) callconv(.c) void {
    const res = handle(Res, h) orelse return;
    const fields = Ints.copy(env, jfields) orelse return;
    defer fields.deinit();
    const locale = copyString(env, jlocale);
    defer if (locale) |l| gpa.free(l);
    c.isakai_res_set_config(res, fields.words.ptr, fields.words.len, if (locale) |l| l.ptr else "");
}

export fn Java_android_content_res_ResEngine_nGetValue(env: Env, _: Obj, h: JniLong, resid: JniInt, resolve: JniBool, density: JniInt, out: Obj) callconv(.c) JniBool {
    const res = handle(Res, h) orelse return 0;
    if (!outValueArray(env, out)) return 0;
    var v: Value = undefined;
    if (!c.isakai_res_get_value(res, @bitCast(resid), resolve != 0, std.math.cast(u16, density) orelse 0, &v)) return 0;
    writeValue(env, out, v);
    return 1;
}

export fn Java_android_content_res_ResEngine_nPoolString(env: Env, _: Obj, h: JniLong, cookie: JniInt, index: JniInt) callconv(.c) Obj {
    const res = handle(Res, h) orelse return null;
    if (index < 0) return null;
    var len: usize = 0;
    const chars = c.isakai_res_pool_string(res, cookie, @intCast(index), &len) orelse return null;
    return newString16(env, chars, len);
}

/// Bag entries as int[7 * n]: key, then the resolved value record.
export fn Java_android_content_res_ResEngine_nGetBag(env: Env, _: Obj, h: JniLong, resid: JniInt) callconv(.c) Obj {
    const res = handle(Res, h) orelse return null;
    const count = c.isakai_res_get_bag(res, @bitCast(resid), null, null, 0);
    if (count < 0) return null;
    const n: usize = @intCast(count);
    const keys = gpa.alloc(u32, n) catch return null;
    defer gpa.free(keys);
    const values = gpa.alloc(Value, n) catch return null;
    defer gpa.free(values);
    if (c.isakai_res_get_bag(res, @bitCast(resid), keys.ptr, values.ptr, n) != count) return null;
    const flat = gpa.alloc(i32, n * 7) catch return null;
    defer gpa.free(flat);
    for (keys, values, 0..) |k, v, i| {
        flat[i * 7 ..][0..7].* = .{ @bitCast(k), v.cookie, @bitCast(v.type), @bitCast(v.data), @bitCast(v.resid), @bitCast(v.flags), @bitCast(v.density) };
    }
    return newInts(env, flat);
}

export fn Java_android_content_res_ResEngine_nResourceName(env: Env, _: Obj, h: JniLong, resid: JniInt) callconv(.c) Obj {
    const res = handle(Res, h) orelse return null;
    var buf: [1024]u8 = undefined;
    const n = c.isakai_res_name(res, @bitCast(resid), &buf, buf.len);
    if (n < 0) return null;
    return newStringUtf8(env, buf[0..@intCast(n)]);
}

export fn Java_android_content_res_ResEngine_nIdentifier(env: Env, _: Obj, h: JniLong, jname: Obj, jtype: Obj, jpkg: Obj) callconv(.c) JniInt {
    const res = handle(Res, h) orelse return 0;
    const name = copyString(env, jname) orelse return 0;
    defer gpa.free(name);
    const type_s = copyString(env, jtype);
    defer if (type_s) |s| gpa.free(s);
    const pkg = copyString(env, jpkg);
    defer if (pkg) |s| gpa.free(s);
    return @bitCast(c.isakai_res_identifier(res, name.ptr, if (type_s) |s| s.ptr else "", if (pkg) |s| s.ptr else ""));
}

export fn Java_android_content_res_ResEngine_nThemeNew(_: Env, _: Obj, h: JniLong) callconv(.c) JniLong {
    const res = handle(Res, h) orelse return 0;
    const theme = c.isakai_theme_new(res) orelse return 0;
    return toHandle(theme);
}

export fn Java_android_content_res_ResEngine_nThemeDelete(_: Env, _: Obj, t: JniLong) callconv(.c) void {
    if (handle(Theme, t)) |theme| c.isakai_theme_delete(theme);
}

export fn Java_android_content_res_ResEngine_nThemeApply(_: Env, _: Obj, t: JniLong, style: JniInt, force: JniBool) callconv(.c) JniBool {
    const theme = handle(Theme, t) orelse return 0;
    return @intFromBool(c.isakai_theme_apply(theme, @bitCast(style), force != 0));
}

export fn Java_android_content_res_ResEngine_nThemeSetTo(_: Env, _: Obj, dst: JniLong, src: JniLong) callconv(.c) JniBool {
    const d = handle(Theme, dst) orelse return 0;
    const s = handle(Theme, src) orelse return 0;
    return @intFromBool(c.isakai_theme_set_to(d, s));
}

export fn Java_android_content_res_ResEngine_nThemeAttr(env: Env, _: Obj, t: JniLong, attr: JniInt, resolve: JniBool, out: Obj) callconv(.c) JniBool {
    const theme = handle(Theme, t) orelse return 0;
    if (!outValueArray(env, out)) return 0;
    var v: Value = undefined;
    if (!c.isakai_theme_attr(theme, @bitCast(attr), resolve != 0, &v)) return 0;
    writeValue(env, out, v);
    return 1;
}

export fn Java_android_content_res_ResEngine_nThemeChangingConfigurations(_: Env, _: Obj, t: JniLong) callconv(.c) JniInt {
    const theme = handle(Theme, t) orelse return 0;
    return @bitCast(c.isakai_theme_changing_configurations(theme));
}

/// Styled-attribute output buffers checked against the attr count:
/// out_values 7 * n words, out_indices n + 1 words.
const StyleBuffers = struct {
    attrs: Ints,
    values: Ints,
    indices: Ints,
    fn init(env: Env, jattrs: Obj, jvalues: Obj, jindices: Obj) ?StyleBuffers {
        const attrs = Ints.copy(env, jattrs) orelse return null;
        const n = attrs.words.len;
        if ((arrayLen(env, jvalues) orelse 0) < n * style_words or (arrayLen(env, jindices) orelse 0) < n + 1) {
            attrs.deinit();
            return null;
        }
        const values = Ints.alloc(n * style_words) orelse {
            attrs.deinit();
            return null;
        };
        const indices = Ints.alloc(n + 1) orelse {
            attrs.deinit();
            values.deinit();
            return null;
        };
        return .{ .attrs = attrs, .values = values, .indices = indices };
    }
    fn publish(self: StyleBuffers, env: Env, jvalues: Obj, jindices: Obj) void {
        setInts(env, jvalues, self.values.words);
        setInts(env, jindices, self.indices.words);
    }
    fn deinit(self: StyleBuffers) void {
        self.attrs.deinit();
        self.values.deinit();
        self.indices.deinit();
    }
};

export fn Java_android_content_res_ResEngine_nApplyStyle(env: Env, _: Obj, t: JniLong, x: JniLong, def_attr: JniInt, def_res: JniInt, jattrs: Obj, jvalues: Obj, jindices: Obj) callconv(.c) JniBool {
    const theme = handle(Theme, t) orelse return 0;
    const bufs = StyleBuffers.init(env, jattrs, jvalues, jindices) orelse return 0;
    defer bufs.deinit();
    if (!c.isakai_apply_style(theme, handle(Xml, x), @bitCast(def_attr), @bitCast(def_res), bufs.attrs.u32s(), bufs.attrs.words.len, bufs.values.u32s(), bufs.indices.u32s())) return 0;
    bufs.publish(env, jvalues, jindices);
    return 1;
}

export fn Java_android_content_res_ResEngine_nRetrieveAttributes(env: Env, _: Obj, h: JniLong, x: JniLong, jattrs: Obj, jvalues: Obj, jindices: Obj) callconv(.c) JniBool {
    const res = handle(Res, h) orelse return 0;
    const xml = handle(Xml, x) orelse return 0;
    const bufs = StyleBuffers.init(env, jattrs, jvalues, jindices) orelse return 0;
    defer bufs.deinit();
    if (!c.isakai_retrieve_attributes(res, xml, bufs.attrs.u32s(), bufs.attrs.words.len, bufs.values.u32s(), bufs.indices.u32s())) return 0;
    bufs.publish(env, jvalues, jindices);
    return 1;
}

export fn Java_android_content_res_ResEngine_nXmlOpen(env: Env, _: Obj, h: JniLong, cookie: JniInt, jpath: Obj) callconv(.c) JniLong {
    const res = handle(Res, h) orelse return 0;
    const path = copyString(env, jpath) orelse return 0;
    defer gpa.free(path);
    const xml = c.isakai_xml_open(res, cookie, path.ptr) orelse return 0;
    return toHandle(xml);
}

export fn Java_android_content_res_ResEngine_nXmlClose(_: Env, _: Obj, x: JniLong) callconv(.c) void {
    if (handle(Xml, x)) |xml| c.isakai_xml_close(xml);
}

export fn Java_android_content_res_ResEngine_nXmlNext(_: Env, _: Obj, x: JniLong) callconv(.c) JniInt {
    const xml = handle(Xml, x) orelse return -1;
    return c.isakai_xml_next(xml);
}

/// Current event words (see isakai_xml_current).
export fn Java_android_content_res_ResEngine_nXmlCurrent(env: Env, _: Obj, x: JniLong) callconv(.c) Obj {
    const xml = handle(Xml, x) orelse return null;
    const need = c.isakai_xml_current(xml, null, 0);
    const words = gpa.alloc(i32, need) catch return null;
    defer gpa.free(words);
    if (c.isakai_xml_current(xml, words.ptr, need) != need) return null;
    return newInts(env, words);
}

export fn Java_android_content_res_ResEngine_nXmlString(env: Env, _: Obj, x: JniLong, index: JniInt) callconv(.c) Obj {
    const xml = handle(Xml, x) orelse return null;
    if (index < 0) return null;
    var len: usize = 0;
    const chars = c.isakai_xml_string(xml, @intCast(index), &len) orelse return null;
    return newString16(env, chars, len);
}

/// Opens an archive path; writes the Java cookie (native + 1) to out[0].
export fn Java_android_content_res_ResEngine_nAssetOpen(env: Env, _: Obj, h: JniLong, jpath: Obj, mode: JniInt, out_cookie: Obj) callconv(.c) JniLong {
    const res = handle(Res, h) orelse return 0;
    if ((arrayLen(env, out_cookie) orelse 0) < 1) return 0;
    const path = copyString(env, jpath) orelse return 0;
    defer gpa.free(path);
    var cookie: i32 = -1;
    const asset = c.isakai_asset_open(res, path.ptr, mode, &cookie) orelse return 0;
    setInts(env, out_cookie, &.{cookie + 1});
    return toHandle(asset);
}

export fn Java_android_content_res_ResEngine_nAssetRead(env: Env, _: Obj, a: JniLong, jbuf: Obj, off: JniInt, len: JniInt) callconv(.c) JniInt {
    const asset = handle(Asset, a) orelse return -1;
    const cap = arrayLen(env, jbuf) orelse return -1;
    const start = std.math.cast(usize, off) orelse return -1;
    const want = std.math.cast(usize, len) orelse return -1;
    if (start > cap or want > cap - start) return -1;
    if (want == 0) return 0;
    var chunk: [64 * 1024]u8 = undefined;
    const n = c.isakai_asset_read(asset, &chunk, @min(want, chunk.len));
    if (n <= 0) return n;
    _ = jniCall(slot_set_byte_array_region, fn (Env, Obj, JniInt, JniInt, [*]const u8) callconv(.c) void, env, .{ jbuf, off, n, &chunk });
    return n;
}

export fn Java_android_content_res_ResEngine_nAssetSeek(_: Env, _: Obj, a: JniLong, offset: JniLong, whence: JniInt) callconv(.c) JniLong {
    const asset = handle(Asset, a) orelse return -1;
    return c.isakai_asset_seek(asset, offset, whence);
}

export fn Java_android_content_res_ResEngine_nAssetLength(_: Env, _: Obj, a: JniLong) callconv(.c) JniLong {
    const asset = handle(Asset, a) orelse return -1;
    return c.isakai_asset_length(asset);
}

export fn Java_android_content_res_ResEngine_nAssetRemaining(_: Env, _: Obj, a: JniLong) callconv(.c) JniLong {
    const asset = handle(Asset, a) orelse return -1;
    return c.isakai_asset_remaining(asset);
}

/// {start, length} of an uncompressed asset inside its archive into out
/// (long[2]); false for compressed entries or bad handles.
export fn Java_android_content_res_ResEngine_nAssetRange(env: Env, _: Obj, a: JniLong, out: Obj) callconv(.c) JniBool {
    const asset = handle(Asset, a) orelse return 0;
    if ((arrayLen(env, out) orelse 0) < 2) return 0;
    var start: i64 = 0;
    var length: i64 = 0;
    if (!c.isakai_asset_range(asset, &start, &length)) return 0;
    const words = [2]i64{ start, length };
    _ = jniCall(slot_set_long_array_region, fn (Env, Obj, JniInt, JniInt, [*]const i64) callconv(.c) void, env, .{ out, 0, 2, &words });
    return 1;
}

export fn Java_android_content_res_ResEngine_nAssetClose(_: Env, _: Obj, a: JniLong) callconv(.c) void {
    if (handle(Asset, a)) |asset| c.isakai_asset_close(asset);
}

/// Names under assets/<dir> joined by NUL, or null when absent.
export fn Java_android_content_res_ResEngine_nAssetList(env: Env, _: Obj, h: JniLong, jdir: Obj) callconv(.c) Obj {
    const res = handle(Res, h) orelse return null;
    const dir = copyString(env, jdir) orelse return null;
    defer gpa.free(dir);
    var len: usize = 0;
    const joined = c.isakai_asset_list(res, dir.ptr, &len) orelse return null;
    defer c.free(joined);
    return newStringUtf8(env, joined[0..len]);
}

const AAssetDir = struct {
    joined: [*]u8,
    len: usize,
    cursor: usize,
};

/// NDK bridge: Java owns the manager; native callers borrow its AssetManager2.
export fn AAssetManager_fromJava(env: Env, manager: Obj) callconv(.c) ?*Res {
    if (manager == null) return null;
    const class = jniCall(slot_get_object_class, fn (Env, Obj) callconv(.c) Obj, env, .{manager}) orelse return null;
    defer _ = jniCall(slot_delete_local_ref, fn (Env, Obj) callconv(.c) void, env, .{class});
    const method = jniCall(slot_get_method_id, fn (Env, Obj, [*:0]const u8, [*:0]const u8) callconv(.c) ?*anyopaque, env, .{ class, "nativePtr", "()J" }) orelse return null;
    const value = jniCall(slot_call_long_method, fn (Env, Obj, *anyopaque) callconv(.c) JniLong, env, .{ manager, method }) orelse return null;
    return handle(Res, value);
}

export fn AAssetManager_open(manager: ?*Res, filename: ?[*:0]const u8, mode: c_int) callconv(.c) ?*Asset {
    const res = manager orelse return null;
    const name = filename orelse return null;
    const path = std.fmt.allocPrintSentinel(gpa, "assets/{s}", .{std.mem.span(name)}, 0) catch return null;
    defer gpa.free(path);
    var cookie: i32 = -1;
    return c.isakai_asset_open(res, path.ptr, mode, &cookie);
}

export fn AAsset_read(asset: ?*Asset, buf: ?*anyopaque, count: usize) callconv(.c) c_int {
    const a = asset orelse return -1;
    const out: [*]u8 = @ptrCast(buf orelse return if (count == 0) 0 else -1);
    return c.isakai_asset_read(a, out, count);
}

export fn AAsset_seek64(asset: ?*Asset, offset: i64, whence: c_int) callconv(.c) i64 {
    return c.isakai_asset_seek(asset orelse return -1, offset, whence);
}

export fn AAsset_seek(asset: ?*Asset, offset: isize, whence: c_int) callconv(.c) isize {
    return @intCast(AAsset_seek64(asset, offset, whence));
}

export fn AAsset_getLength64(asset: ?*Asset) callconv(.c) i64 {
    return c.isakai_asset_length(asset orelse return 0);
}

export fn AAsset_getLength(asset: ?*Asset) callconv(.c) isize {
    return @intCast(AAsset_getLength64(asset));
}

export fn AAsset_getRemainingLength64(asset: ?*Asset) callconv(.c) i64 {
    return c.isakai_asset_remaining(asset orelse return 0);
}

export fn AAsset_getRemainingLength(asset: ?*Asset) callconv(.c) isize {
    return @intCast(AAsset_getRemainingLength64(asset));
}

export fn AAsset_getBuffer(asset: ?*Asset) callconv(.c) ?*const anyopaque {
    return c.isakai_asset_buffer(asset orelse return null);
}

export fn AAsset_isAllocated(asset: ?*Asset) callconv(.c) c_int {
    return @intFromBool(c.isakai_asset_is_allocated(asset orelse return 0));
}

export fn AAsset_openFileDescriptor64(asset: ?*Asset, start: ?*i64, length: ?*i64) callconv(.c) c_int {
    const a = asset orelse return -1;
    const s = start orelse return -1;
    const l = length orelse return -1;
    return c.isakai_asset_fd(a, s, l);
}

export fn AAsset_openFileDescriptor(asset: ?*Asset, start: ?*isize, length: ?*isize) callconv(.c) c_int {
    const s_out = start orelse return -1;
    const l_out = length orelse return -1;
    var s: i64 = 0;
    var l: i64 = 0;
    const fd = AAsset_openFileDescriptor64(asset, &s, &l);
    if (fd < 0) return fd;
    s_out.* = @intCast(s);
    l_out.* = @intCast(l);
    return fd;
}

export fn AAsset_close(asset: ?*Asset) callconv(.c) void {
    if (asset) |a| c.isakai_asset_close(a);
}

export fn AAssetManager_openDir(manager: ?*Res, dirname: ?[*:0]const u8) callconv(.c) ?*AAssetDir {
    const res = manager orelse return null;
    const dir = if (dirname) |d| d else "";
    var len: usize = 0;
    const joined = c.isakai_asset_list(res, dir, &len) orelse return null;
    const listing = gpa.create(AAssetDir) catch {
        c.free(joined);
        return null;
    };
    listing.* = .{ .joined = joined, .len = len, .cursor = 0 };
    return listing;
}

export fn AAssetDir_getNextFileName(dir: ?*AAssetDir) callconv(.c) ?[*:0]const u8 {
    const listing = dir orelse return null;
    if (listing.cursor >= listing.len) return null;
    const start = listing.cursor;
    while (listing.cursor < listing.len and listing.joined[listing.cursor] != 0) listing.cursor += 1;
    listing.cursor += 1;
    return @ptrCast(listing.joined + start);
}

export fn AAssetDir_rewind(dir: ?*AAssetDir) callconv(.c) void {
    if (dir) |listing| listing.cursor = 0;
}

export fn AAssetDir_close(dir: ?*AAssetDir) callconv(.c) void {
    if (dir) |listing| {
        c.free(listing.joined);
        gpa.destroy(listing);
    }
}
