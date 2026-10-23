// SPDX-License-Identifier: GPL-3.0-or-later

//! JNI bridge between `org.isakai.bridge.IsakaiWindow` and the Wayland
//! client in the `window` module.
//!
//! The JVM resolves the natives by dlsym name
//! (`Java_org_isakai_bridge_IsakaiWindow_…`), so no `JNI_OnLoad` or
//! `RegisterNatives` table is needed. JNI entry points are read out of the
//! `JNIEnv` function table by fixed slot index; the slots below were verified
//! against JDK 27's `jni.h` with
//! `slot = offsetof(struct JNINativeInterface_, name) / sizeof(void *)`:
//! `NewStringUTF` = 167, `GetStringUTFChars` = 169,
//! `ReleaseStringUTFChars` = 170, `GetArrayLength` = 171,
//! `GetByteArrayRegion` = 200, `GetIntArrayRegion` = 203,
//! `SetIntArrayRegion` = 211, `NewDirectByteBuffer` = 229.
//!
//! Failure policy: `nOpen` returns 0 and `nRun` returns -1 on error; the Java
//! wrapper turns those into `IllegalStateException`, so no `ThrowNew` here.
//! `nPost` returns 0 on success, -1 on failure; `nCommit` returns 1 when
//! a mapped buffer was committed, 0 when stale (nothing outstanding).
//! `nPollInput` returns the event count (groups of 8 ints), 0 for none,
//! -1 on bad/closed handle or a batch whose length is not a positive
//! multiple of 8.

const std = @import("std");

// The host JVM owns signal handling. Zig's default 256 KiB TLS signal
// stack exceeds the JVM process-reaper thread stack when this DSO is loaded.
pub const std_options: std.Options = .{
    .enable_segfault_handler = false,
    .signal_stack_size = null,
};
const window_mod = @import("window");
const audio_mod = @import("audio");
const desktop_mod = @import("desktop");
const sqlite_mod = @import("sqlite");

const JniInt = i32;
const JniSize = i32;
const JniLong = i64;

const gpa = std.heap.c_allocator;

/// Slot count of JDK 27's `JNINativeInterface_` table.
const jni_table_len = 236;
const slot_new_string_utf: usize = 167;
const slot_get_string_utf_chars: usize = 169;
const slot_release_string_utf_chars: usize = 170;
const slot_get_array_length: usize = 171;
const slot_get_byte_array_region: usize = 200;
const slot_get_int_array_region: usize = 203;
const slot_set_int_array_region: usize = 211;
const slot_new_direct_byte_buffer: usize = 229;

const JniTable = [jni_table_len]?*const anyopaque;

/// Dereference `JNIEnv*` (pointer to the function-table pointer) and return
/// the table, or null when either level is null.
fn jniFunctions(env: ?*anyopaque) ?*const JniTable {
    const penv: *?*const anyopaque = @ptrCast(@alignCast(env orelse return null));
    const iface = penv.* orelse return null;
    return @ptrCast(@alignCast(iface));
}

/// Comptime JNI function-table dispatch: one wrapper replacing the six
/// per-slot wrappers. Export signatures are unchanged.
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

/// Payload of a JNI function-table return: strips one optional layer so
/// void/plain/optional returns all normalize to a nullable result.
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

fn jniNewStringUtf(env: ?*anyopaque, fns: *const JniTable, bytes: [*:0]const u8) ?*anyopaque {
    const utf16 = std.unicode.utf8ToUtf16LeAlloc(gpa, std.mem.span(bytes)) catch return null;
    defer gpa.free(utf16);
    const len = std.math.cast(JniSize, utf16.len) orelse return null;
    const Fn = fn (?*anyopaque, [*]const u16, JniSize) callconv(.c) ?*anyopaque;
    return jniCall(163, Fn, fns, env, .{ utf16.ptr, len });
}

fn jniArrayLen(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque) ?JniSize {
    const Fn = fn (?*anyopaque, ?*anyopaque) callconv(.c) JniSize;
    return jniCall(slot_get_array_length, Fn, fns, env, .{arr});
}

fn jniGetByteRegion(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque, start: JniSize, len: JniSize, buf: [*]u8) void {
    const Fn = fn (?*anyopaque, ?*anyopaque, JniSize, JniSize, [*]u8) callconv(.c) void;
    _ = jniCall(slot_get_byte_array_region, Fn, fns, env, .{ arr, start, len, buf });
}

fn jniGetIntRegion(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque, start: JniSize, len: JniSize, buf: [*]JniInt) void {
    const Fn = fn (?*anyopaque, ?*anyopaque, JniSize, JniSize, [*]JniInt) callconv(.c) void;
    _ = jniCall(slot_get_int_array_region, Fn, fns, env, .{ arr, start, len, buf });
}

fn jniSetIntRegion(env: ?*anyopaque, fns: *const JniTable, arr: ?*anyopaque, start: JniSize, len: JniSize, buf: [*]const JniInt) void {
    const Fn = fn (?*anyopaque, ?*anyopaque, JniSize, JniSize, [*]const JniInt) callconv(.c) void;
    _ = jniCall(slot_set_int_array_region, Fn, fns, env, .{ arr, start, len, buf });
}

fn jniNewDirectBuffer(env: ?*anyopaque, fns: *const JniTable, addr: *anyopaque, cap: JniLong) ?*anyopaque {
    const Fn = fn (?*anyopaque, *anyopaque, JniLong) callconv(.c) ?*anyopaque;
    return jniCall(slot_new_direct_byte_buffer, Fn, fns, env, .{ addr, cap });
}

fn getenvNonEmpty(name: [*:0]const u8) ?[]const u8 {
    const raw = std.c.getenv(name) orelse return null;
    const s = std.mem.span(raw);
    if (s.len == 0) return null;
    return s;
}

/// Zero-copy opt-out: ISAKAI_ZEROCOPY=0 forces the copy path (nPost).
/// Any other value (or unset) allows nMapBuffer.
fn zerocopyEnabled() bool {
    const raw = std.c.getenv("ISAKAI_ZEROCOPY") orelse return true;
    return !std.mem.eql(u8, std.mem.span(raw), "0");
}

/// Resolve the Wayland display socket path: absolute `$WAYLAND_DISPLAY` is
/// used as-is, otherwise `$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`. Returns null
/// when unset so `Window.open` falls back to its own default.
fn resolveDisplayPath() ?[]u8 {
    const display = getenvNonEmpty("WAYLAND_DISPLAY") orelse return null;
    if (std.fs.path.isAbsolute(display)) return gpa.dupe(u8, display) catch null;
    const runtime = getenvNonEmpty("XDG_RUNTIME_DIR") orelse return null;
    return std.fmt.allocPrint(gpa, "{s}/{s}", .{ runtime, display }) catch null;
}

fn asAudio(handle: JniLong) ?*audio_mod.Stream {
    if (handle == 0) return null;
    return @ptrFromInt(@as(usize, @intCast(handle)));
}

fn asWindow(handle: JniLong) ?*window_mod.Window {
    if (handle == 0) return null;
    return @ptrFromInt(@as(usize, @intCast(handle)));
}

fn checkedU31(v: JniInt) ?u31 {
    if (v <= 0) return null;
    if (v > std.math.maxInt(u31)) return null;
    return @intCast(v);
}

/// Checked width/height pair.
fn dims2(w: JniInt, h: JniInt) ?struct { w: u31, h: u31 } {
    return .{ .w = checkedU31(w) orelse return null, .h = checkedU31(h) orelse return null };
}

/// Packed (w << 32) | h size long shared by nPollSize and nSize.
fn packSize(w: u31, h: u31) JniLong {
    return (@as(JniLong, w) << 32) | @as(JniLong, h);
}

export fn Java_org_isakai_bridge_IsakaiWindow_nOpen(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    jtitle: ?*anyopaque,
    japp_id: ?*anyopaque,
    w: JniInt,
    h: JniInt,
) callconv(.c) JniLong {
    _ = clazz;
    const dims = dims2(w, h) orelse return 0;
    const width = dims.w;
    const height = dims.h;
    if (jtitle == null or japp_id == null) return 0;
    const fns = jniFunctions(env) orelse return 0;
    const title_chars = jstrDup(env, fns, jtitle) orelse return 0;
    defer gpa.free(title_chars);
    const app_chars = jstrDup(env, fns, japp_id) orelse return 0;
    defer gpa.free(app_chars);
    const title = std.fmt.allocPrintSentinel(gpa, "{s}", .{title_chars}, 0) catch return 0;
    defer gpa.free(title);
    const app_id = std.fmt.allocPrintSentinel(gpa, "{s}", .{app_chars}, 0) catch return 0;
    defer gpa.free(app_id);
    const display_path = resolveDisplayPath();
    defer if (display_path) |d| gpa.free(d);
    const win = window_mod.Window.open(gpa, display_path, title, app_id, width, height) catch |err| {
        // The Java side discards the error; the confined-app window path
        // needs the actual failure observed from the app's own stderr.
        std.debug.print("isakai-window: nOpen failed: {s}\n", .{@errorName(err)});
        return 0;
    };
    return @intCast(@intFromPtr(win));
}

export fn Java_org_isakai_bridge_IsakaiWindow_nSetTitle(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    jtitle: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const win = asWindow(handle) orelse return -1;
    const fns = jniFunctions(env) orelse return -1;
    const chars = jstrDup(env, fns, jtitle) orelse return -1;
    defer gpa.free(chars);
    win.setTitle(chars) catch return -1;
    return 0;
}

export fn Java_org_isakai_bridge_IsakaiWindow_nPost(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    jpixels: ?*anyopaque,
    w: JniInt,
    h: JniInt,
    stride: JniInt,
) callconv(.c) JniInt {
    _ = clazz;
    const win = asWindow(handle) orelse return -1;
    const dims = dims2(w, h) orelse return -1;
    const width = dims.w;
    const height = dims.h;
    const row_stride = checkedU31(stride) orelse return -1;
    if (jpixels == null) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const count_i64: i64 = @as(i64, height) * @as(i64, row_stride);
    if (count_i64 > std.math.maxInt(JniSize)) return -1;
    const count: usize = @intCast(count_i64);
    const have = jniArrayLen(env, fns, jpixels) orelse return -1;
    if (@as(i64, have) < count_i64) return -1;
    const buf = gpa.alloc(JniInt, count) catch return -1;
    defer gpa.free(buf);
    jniGetIntRegion(env, fns, jpixels, 0, @intCast(count_i64), buf.ptr);
    const pixels: []u32 = @ptrCast(buf);
    for (pixels) |*px| px.* = 0xFF00_0000 | (px.* & 0x00FF_FFFF);
    win.post(pixels, width, height, row_stride) catch |err| {
        std.debug.print("isakai: frame post failed: {s}\n", .{@errorName(err)});
        return -1;
    };
    return 0;
}

/// Reserve a free SHM slot and wrap it in a NewDirectByteBuffer of
/// w*h*4 bytes for zero-copy drawing. `out_slot[0]` receives the slot
/// index for nCommit. Returns null when zero-copy is disabled via
/// ISAKAI_ZEROCOPY=0, no slot is free, or the pool remake fails. The
/// buffer is only valid until nCommit/nPost; a resize remakes the pool
/// and any previously mapped buffer is stale and must be re-mapped.
export fn Java_org_isakai_bridge_IsakaiWindow_nMapBuffer(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    out_slot: ?*anyopaque,
    w: JniInt,
    h: JniInt,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    if (!zerocopyEnabled()) return null;
    const win = asWindow(handle) orelse return null;
    const dims = dims2(w, h) orelse return null;
    const width = dims.w;
    const height = dims.h;
    if (out_slot == null) return null;
    const fns = jniFunctions(env) orelse return null;
    const pix_len: usize = @as(usize, width) * @as(usize, height);
    const slot = win.freeBuffer(width, height, width, pix_len) catch return null;
    const bytes = win.tryMap(slot, width, height) orelse return null;
    const out: [*]JniInt = @ptrCast(@alignCast(out_slot));
    out[0] = @intCast(slot);
    return jniNewDirectBuffer(env, fns, bytes.ptr, @intCast(bytes.len));
}

/// Commit the buffer previously handed out by nMapBuffer. Returns 1 when
/// a mapped buffer was committed, 0 when stale (nothing outstanding).
export fn Java_org_isakai_bridge_IsakaiWindow_nCommit(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    w: JniInt,
    h: JniInt,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const win = asWindow(handle) orelse return 0;
    const dims = dims2(w, h) orelse return 0;
    const width = dims.w;
    const height = dims.h;
    const ok = win.commit(width, height) catch return 0;
    return if (ok) 1 else 0;
}

/// Poll the compositor (short dispatch) and report a pending resize as a
/// packed jlong: 0 = no resize, else (w << 32) | h. Pure return, no slots.
export fn Java_org_isakai_bridge_IsakaiWindow_nPollSize(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    const win = asWindow(handle) orelse return 0;
    win.pump(0) catch return 0;
    const r = win.takeResize() orelse return 0;
    return packSize(r.w, r.h);
}

/// Current window size as a packed jlong (w << 32) | h. 0 on bad handle.
export fn Java_org_isakai_bridge_IsakaiWindow_nSize(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    const win = asWindow(handle) orelse return 0;
    const s = win.size();
    return packSize(s.w, s.h);
}

export fn Java_org_isakai_bridge_IsakaiWindow_nRun(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    timeout_ms: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const win = asWindow(handle) orelse return -1;
    const reason = win.run(@intCast(@max(timeout_ms, 0)));
    return switch (reason) {
        .closed => 0,
        .timeout => 1,
    };
}

/// Drain queued input events into `batch` as groups of 8 ints, kind first
/// (M3 contract: 1=KEY, 2=MOTION, 5=KBD_FOCUS, 6=PTR_FOCUS; M4 adds 7/8
/// touch headers/rows). Returns the event count, 0 for none, -1 on
/// bad/closed handle or a batch whose length is not a positive multiple
/// of 8. Pure drain: no pump here, the Java loop calls run()/pollSize
/// for I/O. The Java wrapper turns negatives into IllegalStateException
/// (no ThrowNew here).
export fn Java_org_isakai_bridge_IsakaiWindow_nPollInput(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    batch: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const win = asWindow(handle) orelse return -1;
    if (win.isClosed()) return -1;
    if (batch == null) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const len = jniArrayLen(env, fns, batch) orelse return -1;
    if (len <= 0 or @rem(len, 8) != 0) return -1;
    const cap: usize = @intCast(len);
    const max_events = cap / 8;
    // Cap the stack buffer at 32 events (256 ints); the rest drains on
    // the next call. Java allocates 8*32.
    var stack: [256]JniInt = undefined;
    const want: usize = @min(max_events, 32);
    const n = win.takeInput(stack[0 .. want * 8]);
    if (n > 0) jniSetIntRegion(env, fns, batch, 0, @intCast(n * 8), stack[0..].ptr);
    return @intCast(n);
}

/// Poll the compositor (short dispatch) and report a pending
/// DisplayMetrics snapshot into `out` (int[8]: w,h,densityDpi,
/// density*1000,scaled*1000,xdpi*1000,ydpi*1000,scale). Returns 1 when a
/// fresh snapshot was written, 0 when nothing new arrived, -1 on
/// bad/closed handle or an out array shorter than 8. Touch rides the
/// existing nPollInput kinds 7/8; this native carries metrics only.
export fn Java_org_isakai_bridge_IsakaiWindow_nPollMetrics(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    out: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const win = asWindow(handle) orelse return -1;
    if (win.isClosed()) return -1;
    if (out == null) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const len = jniArrayLen(env, fns, out) orelse return -1;
    if (len < 8) return -1;
    win.pump(0) catch return -1;
    var snap: [8]JniInt = undefined;
    if (win.takeMetrics(&snap) == null) return 0;
    jniSetIntRegion(env, fns, out, 0, 8, snap[0..].ptr);
    return 1;
}

/// Open a PulseAudio playback stream (S16LE stereo 44100). Returns a
/// nonzero handle, 0 on failure. The Java wrapper turns 0 into
/// IllegalStateException (no ThrowNew here).
export fn Java_org_isakai_bridge_IsakaiAudio_nAudioOpen(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    const st = audio_mod.open() orelse return 0;
    const boxed = gpa.create(audio_mod.Stream) catch return 0;
    boxed.* = st;
    return @intCast(@intFromPtr(boxed));
}

/// Open a PulseAudio playback stream at `rate`/`channels` (S16LE).
/// Returns a nonzero handle, 0 on failure or unsupported format.
export fn Java_org_isakai_bridge_IsakaiAudio_nAudioOpenFormat(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    rate: JniInt,
    channels: JniInt,
) callconv(.c) JniLong {
    _ = env;
    _ = clazz;
    if (rate <= 0 or channels <= 0 or channels > 127) return 0;
    const st = audio_mod.openFormat(@intCast(rate), @intCast(channels)) orelse return 0;
    const boxed = gpa.create(audio_mod.Stream) catch return 0;
    boxed.* = st;
    return @intCast(@intFromPtr(boxed));
}

/// Write `len` bytes of `data` to the stream in 64 KiB chunks
/// (GetByteArrayRegion chunked copy, mirroring nPost's int path).
/// Returns 0 on success, -1 on failure. 0-byte writes succeed; len
/// above 8 MiB fails.
export fn Java_org_isakai_bridge_IsakaiAudio_nAudioWrite(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    jdata: ?*anyopaque,
    len: JniInt,
) callconv(.c) JniInt {
    _ = clazz;
    const stream = asAudio(handle) orelse return -1;
    if (jdata == null) return -1;
    if (len < 0) return -1;
    if (len == 0) return 0;
    if (@as(i64, len) > audio_mod.max_write_len) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const have = jniArrayLen(env, fns, jdata) orelse return -1;
    if (have < len) return -1;
    var off: JniSize = 0;
    var chunk: [65536]u8 = undefined;
    while (off < len) {
        const n: JniSize = @min(len - off, 65536);
        jniGetByteRegion(env, fns, jdata, off, n, &chunk);
        const slice = chunk[0..@intCast(n)];
        if (!stream.write(slice)) return -1;
        off += n;
    }
    return 0;
}

/// Drain buffered audio (block until played). 0 on success, -1 on failure.
export fn Java_org_isakai_bridge_IsakaiAudio_nAudioDrain(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const stream = asAudio(handle) orelse return -1;
    return if (stream.drain()) 0 else -1;
}

export fn Java_org_isakai_bridge_IsakaiAudio_nAudioClose(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) void {
    _ = env;
    _ = clazz;
    const stream = asAudio(handle) orelse return;
    stream.close();
    gpa.destroy(stream);
}

export fn Java_org_isakai_bridge_IsakaiWindow_nClose(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) void {
    _ = env;
    _ = clazz;
    const win = asWindow(handle) orelse return;
    win.close();
}

/// Copy `text` into the Wayland selection (empty clears). Silent
/// local-only when no data device is bound; the Java side keeps its own
/// copy either way. 0 on success, -1 on bad handle or JNI failure.
export fn Java_org_isakai_bridge_IsakaiWindow_nClipboardSet(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
    jtext: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    const win = asWindow(handle) orelse return -1;
    if (win.isClosed()) return -1;
    if (jtext == null) {
        win.clipboardCopy("");
        return 0;
    }
    const fns = jniFunctions(env) orelse return -1;
    const chars = jstrDup(env, fns, jtext) orelse return -1;
    defer gpa.free(chars);
    win.clipboardCopy(chars);
    return 0;
}

/// Paste the current selection as a NewStringUTF jstring, or null when no
/// text is offered / the transfer fails. GetStringUTFChars slot verified
/// via offsetof (see header); NewStringUTF = 167.
export fn Java_org_isakai_bridge_IsakaiWindow_nClipboardGet(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    const win = asWindow(handle) orelse return null;
    if (win.isClosed()) return null;
    const fns = jniFunctions(env) orelse return null;
    const owned = win.clipboardPaste() orelse return null;
    defer gpa.free(owned);
    const z = gpa.allocSentinel(u8, owned.len, 0) catch return null;
    defer gpa.free(z);
    @memcpy(z[0..owned.len], owned);
    return jniNewStringUtf(env, fns, z);
}

/// 1 when the remote offer carries text, 0 otherwise (or bad handle).
/// Pure flag check: no I/O, so Java decides when to pump.
export fn Java_org_isakai_bridge_IsakaiWindow_nClipboardHasText(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    handle: JniLong,
) callconv(.c) JniInt {
    _ = env;
    _ = clazz;
    const win = asWindow(handle) orelse return 0;
    if (win.isClosed()) return 0;
    return if (win.clipboardHasText()) 1 else 0;
}
/// Desktop natives (IsakaiDesktop): thin JNI adapters over desktop.zig.
/// Failure policy matches the window bridge: 0/-1/null returns, the
/// Java wrapper turns them into IllegalStateException or fallbacks
/// (no ThrowNew here).
fn jstrDup(env: ?*anyopaque, fns: *const JniTable, j: ?*anyopaque) ?[]u8 {
    if (j == null) return null;
    const Len = fn (?*anyopaque, ?*anyopaque) callconv(.c) JniSize;
    const Get = fn (?*anyopaque, ?*anyopaque, ?*u8) callconv(.c) ?[*]const u16;
    const Release = fn (?*anyopaque, ?*anyopaque, [*]const u16) callconv(.c) void;
    const len = jniCall(164, Len, fns, env, .{j}) orelse return null;
    if (len < 0) return null;
    const chars = jniCall(165, Get, fns, env, .{ j, @as(?*u8, null) }) orelse return null;
    defer _ = jniCall(166, Release, fns, env, .{ j, chars });
    return std.unicode.utf16LeToUtf8Alloc(gpa, chars[0..@intCast(len)]) catch null;
}

export fn Java_org_isakai_bridge_IsakaiDesktop_nNotify(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    japp: ?*anyopaque,
    id: JniInt,
    jtitle: ?*anyopaque,
    jbody: ?*anyopaque,
    jchannel: ?*anyopaque,
    urgency: JniInt,
) callconv(.c) JniLong {
    _ = clazz;
    const fns = jniFunctions(env) orelse return 0;
    const app = jstrDup(env, fns, japp) orelse return 0;
    defer gpa.free(app);
    const title = jstrDup(env, fns, jtitle) orelse return 0;
    defer gpa.free(title);
    const body = jstrDup(env, fns, jbody) orelse return 0;
    defer gpa.free(body);
    const channel = jstrDup(env, fns, jchannel) orelse return 0;
    defer gpa.free(channel);
    const plat = desktop_mod.notify(app, id, title, body, channel, urgency);
    if (plat == 0) return 0;
    return @intCast(plat);
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nCancel(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    plat: JniLong,
) callconv(.c) void {
    _ = env;
    _ = clazz;
    if (plat <= 0) return;
    desktop_mod.cancel(@intCast(plat));
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nOpenUri(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    juri: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    if (juri == null) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const uri = jstrDup(env, fns, juri) orelse return -1;
    defer gpa.free(uri);
    return if (desktop_mod.openUri(uri)) 0 else -1;
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nOpenFileStart(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    jtitle: ?*anyopaque,
) callconv(.c) JniLong {
    _ = clazz;
    if (jtitle == null) return 0;
    const fns = jniFunctions(env) orelse return 0;
    const title = jstrDup(env, fns, jtitle) orelse return 0;
    defer gpa.free(title);
    const req = desktop_mod.openFileStart(title);
    if (req == 0) return 0;
    return @intCast(req);
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nGetFileResult(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    req: JniLong,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    if (req <= 0) return null;
    const fns = jniFunctions(env) orelse return null;
    const r = desktop_mod.fileResult(@intCast(req)) orelse return null;
    switch (r.status) {
        .pending => {
            gpa.free(r.path);
            return null;
        },
        .cancel => {
            gpa.free(r.path);
            const z = gpa.allocSentinel(u8, 0, 0) catch return null;
            return jniNewStringUtf(env, fns, z);
        },
        .done => {
            defer gpa.free(r.path);
            const z = gpa.allocSentinel(u8, r.path.len, 0) catch return null;
            defer gpa.free(z);
            @memcpy(z[0..r.path.len], r.path);
            return jniNewStringUtf(env, fns, z);
        },
    }
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nCloseReq(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    req: JniLong,
) callconv(.c) void {
    _ = env;
    _ = clazz;
    if (req <= 0) return;
    desktop_mod.closeReq(@intCast(req));
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nPollDesktop(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    batch: ?*anyopaque,
) callconv(.c) JniInt {
    _ = clazz;
    if (batch == null) return -1;
    const fns = jniFunctions(env) orelse return -1;
    const len = jniArrayLen(env, fns, batch) orelse return -1;
    if (len <= 0 or @rem(len, 8) != 0) return -1;
    const cap: usize = @intCast(len);
    const want: usize = @min(cap / 8, 32);
    var stack: [256]JniInt = undefined;
    const buf: []i32 = @ptrCast(stack[0 .. want * 8]);
    const n = desktop_mod.poll(buf);
    if (n < 0) return -1;
    if (n > 0) jniSetIntRegion(env, fns, batch, 0, @intCast(n * 8), stack[0..].ptr);
    return n;
}
export fn Java_org_isakai_bridge_IsakaiDesktop_nGetActionKey(
    env: ?*anyopaque,
    clazz: ?*anyopaque,
    slot: JniInt,
) callconv(.c) ?*anyopaque {
    _ = clazz;
    if (slot < 0 or slot >= 16) return null;
    const fns = jniFunctions(env) orelse return null;
    const key = desktop_mod.actionKey(@intCast(slot)) orelse return null;
    const z = gpa.allocSentinel(u8, key.len, 0) catch return null;
    defer gpa.free(z);
    @memcpy(z[0..key.len], key);
    return jniNewStringUtf(env, fns, z);
}

// Storage force-link: reference the sqlite bridge so its IsakaiSQLite
// natives land in libisakai_jni.so (no cross-calls; pure symbol anchoring).
comptime {
    _ = sqlite_mod.sqlite_linked;
}
