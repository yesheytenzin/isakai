//! M6a bionic shim: interposer library providing the small set of
//! Android-bionic-only symbols that NDK-built app `.so` files reference
//! when they only use libc-level facilities. Built as
//! `libisakai_bionic_shim.so`, LD_PRELOADed into the JVM before app libs
//! dlopen (see launch.zig). It only ADDS symbols glibc lacks; glibc's own
//! exports are never interposed, so glibc semantics stay untouched.
//!
//! Symbol set (bionic-only surface observed in real JNI libs):
//!   - android/log.h: __android_log_print/__android_log_write/
//!     __android_log_vprint/__android_log_assert/__android_log_is_loggable
//!   - sys/system_properties.h: property_get/property_set/property_list,
//!     __system_property_get/__system_property_set
//!   - android/dlext.h: android_dlopen_ext → dlopen (extinfo ignored)
//! Deliberately NOT provided: ashmem/ASharedMemory, hardware/*.so entry
//! points, media/NDK APIs. A lib needing those fails dlopen with a loud
//! undefined-symbol error — the honest failure mode.

const std = @import("std");

// The host JVM owns signal handling. Zig's default 256 KiB TLS signal
// stack exceeds the JVM process-reaper thread stack when this DSO is loaded.
pub const std_options: std.Options = .{
    .enable_segfault_handler = false,
    .signal_stack_size = null,
};

const prop_value_max = 92; // bionic PROP_VALUE_MAX

extern fn vsnprintf(str: [*]u8, size: usize, format: [*:0]const u8, ap: *anyopaque) c_int;
extern fn va_copy(dest: *anyopaque, src: *anyopaque) void;
extern fn va_end(ap: *anyopaque) void;

fn logLevel(priority: c_int) std.log.Level {
    // ANDROID_LOG_VERBOSE=2 .. ANDROID_LOG_FATAL=7
    return switch (priority) {
        0...3 => .debug,
        4 => .info,
        5 => .warn,
        else => .err,
    };
}

fn logScoped(priority: c_int, tag: [*:0]const u8, msg: []const u8) void {
    const tag_s = std.mem.sliceTo(tag, 0);
    // Runtime-level: the C variadic path can't pass a comptime level.
    switch (logLevel(priority)) {
        .debug => std.log.debug("[{s}] {s}", .{ tag_s, msg }),
        .info => std.log.info("[{s}] {s}", .{ tag_s, msg }),
        .warn => std.log.warn("[{s}] {s}", .{ tag_s, msg }),
        .err => std.log.err("[{s}] {s}", .{ tag_s, msg }),
    }
}

// --- android/log.h ---
// Variadic signatures use the C ABI directly: the caller passes a real
// va_list-formatted varargs tail, which we forward to vsnprintf. Zig can
// declare variadic exports; the va_list is captured via @cVaStart.

export fn __android_log_write(priority: c_int, tag: [*:0]const u8, text: [*:0]const u8) c_int {
    logScoped(priority, tag, std.mem.sliceTo(text, 0));
    return 1;
}

export fn __android_log_print(priority: c_int, tag: [*:0]const u8, fmt: [*:0]const u8, ...) c_int {
    var buf: [4096]u8 = undefined;
    var ap = @cVaStart();
    defer @cVaEnd(&ap);
    const n = vsnprintf(&buf, buf.len, fmt, @ptrCast(&ap));
    if (n < 0) return -1;
    const len: usize = @min(@as(usize, @intCast(n)), buf.len - 1);
    logScoped(priority, tag, buf[0..len]);
    return 1;
}

export fn __android_log_vprint(priority: c_int, tag: [*:0]const u8, fmt: [*:0]const u8, ap: *anyopaque) c_int {
    var buf: [4096]u8 = undefined;
    const n = vsnprintf(&buf, buf.len, fmt, ap);
    if (n < 0) return -1;
    const len: usize = @min(@as(usize, @intCast(n)), buf.len - 1);
    logScoped(priority, tag, buf[0..len]);
    return 1;
}

// AOSP android/log.h: void __android_log_assert(const char *cond,
// const char *tag, const char *fmt, ...). The condition is a string; the
// call always logs (including the failed condition) and aborts.
export fn __android_log_assert(cond: [*:0]const u8, tag: [*:0]const u8, fmt: [*:0]const u8, ...) void {
    var buf: [4096]u8 = undefined;
    var ap = @cVaStart();
    defer @cVaEnd(&ap);
    _ = vsnprintf(&buf, buf.len, fmt, @ptrCast(&ap));
    const tag_s = std.mem.sliceTo(tag, 0);
    const cond_s = std.mem.sliceTo(cond, 0);
    std.debug.print("{s}: assertion '{s}' failed: {s}\n", .{ tag_s, cond_s, std.mem.sliceTo(&buf, 0) });
    std.process.abort();
}

// AOSP android/log.h: int __android_log_is_loggable(int prio,
// const char *tag, int def). Honours the in-process "log.tag.<TAG>"
// property (single level letter or name, as bionic does) and otherwise
// defers to the default threshold.
export fn __android_log_is_loggable(prio: c_int, tag: [*:0]const u8, default_prio: c_int) c_int {
    propLock();
    defer propUnlock();
    const tag_s = std.mem.sliceTo(tag, 0);
    var key_buf: [8 + prop_value_max]u8 = undefined; // "log.tag." + name
    const prefix = "log.tag.";
    if (tag_s.len + prefix.len < key_buf.len) {
        @memcpy(key_buf[0..prefix.len], prefix);
        @memcpy(key_buf[prefix.len..][0..tag_s.len], tag_s);
        const key = key_buf[0 .. prefix.len + tag_s.len];
        if (findPropLocked(key)) |v| {
            if (v.len > 0) {
                const level: c_int = switch (std.ascii.toUpper(v[0])) {
                    'V' => 2,
                    'D' => 3,
                    'I' => 4,
                    'W' => 5,
                    'E' => 6,
                    'F' => 7,
                    'S' => 100, // silent: nothing is loggable
                    else => default_prio,
                };
                return @intFromBool(prio >= level);
            }
        }
    }
    return @intFromBool(prio >= default_prio);
}

// --- system properties: in-process table (no /dev/__properties__) ---

// C-convention library: no Io instance available; a tiny spinlock is the
// honest concurrency primitive for an in-process property table (real
// bionic uses read-mostly mapped files with similar atomic access).
var prop_lock = std.atomic.Value(bool).init(false);

fn propLock() void {
    while (prop_lock.swap(true, .acquire)) std.atomic.spinLoopHint();
}

fn propUnlock() void {
    prop_lock.store(false, .release);
}
const prop_max = 64;
const PropEntry = struct {
    name: [prop_value_max]u8,
    name_len: usize,
    value: [prop_value_max]u8,
    value_len: usize,
};
var props: [prop_max]PropEntry = undefined;
var props_len: usize = 0;

fn findPropLocked(name: []const u8) ?[]const u8 {
    for (props[0..props_len]) |*kv| {
        if (std.mem.eql(u8, kv.name[0..kv.name_len], name)) {
            return kv.value[0..kv.value_len];
        }
    }
    return null;
}

fn storePropLocked(name: []const u8, value: []const u8) void {
    for (props[0..props_len]) |*kv| {
        if (std.mem.eql(u8, kv.name[0..kv.name_len], name)) {
            const n = @min(value.len, prop_value_max - 1);
            @memcpy(kv.value[0..n], value[0..n]);
            kv.value_len = n;
            return;
        }
    }
    if (props_len == prop_max) {
        // Evict the first entry (round-robin-ish; tables this small make
        // policy irrelevant).
        std.mem.copyForwards(PropEntry, props[0 .. prop_max - 1], props[1..prop_max]);
        props_len -= 1;
    }
    var e: PropEntry = undefined;
    const nn = @min(name.len, prop_value_max - 1);
    @memcpy(e.name[0..nn], name[0..nn]);
    e.name_len = nn;
    const vn = @min(value.len, prop_value_max - 1);
    @memcpy(e.value[0..vn], value[0..vn]);
    e.value_len = vn;
    props[props_len] = e;
    props_len += 1;
}

fn writePropValue(buf: [*]u8, v: []const u8) c_int {
    @memcpy(buf[0..v.len], v);
    buf[v.len] = 0;
    return @intCast(v.len);
}

/// Returns the value length (like bionic), or 0 when unset.
export fn property_get(name: [*:0]const u8, value_buf: [*]u8, default_value: ?[*:0]const u8) c_int {
    propLock();
    defer propUnlock();
    const name_s = std.mem.sliceTo(name, 0);
    if (findPropLocked(name_s)) |v| {
        return writePropValue(value_buf, v);
    }
    if (default_value) |d| {
        return writePropValue(value_buf, std.mem.sliceTo(d, 0));
    }
    value_buf[0] = 0;
    return 0;
}

export fn property_set(name: [*:0]const u8, value: [*:0]const u8) c_int {
    propLock();
    defer propUnlock();
    storePropLocked(std.mem.sliceTo(name, 0), std.mem.sliceTo(value, 0));
    return 0;
}

// AOSP cutils/properties.h: the callback receives separate name, value
// and cookie (not a single "name=value" string).
const PropCallback = *const fn (name: [*:0]const u8, value: [*:0]const u8, arg: ?*anyopaque) callconv(.c) void;

export fn property_list(cb: PropCallback, cookie: ?*anyopaque) c_int {
    propLock();
    defer propUnlock();
    for (props[0..props_len]) |*kv| {
        var name_buf: [prop_value_max]u8 = undefined;
        var value_buf: [prop_value_max]u8 = undefined;
        @memcpy(name_buf[0..kv.name_len], kv.name[0..kv.name_len]);
        name_buf[kv.name_len] = 0;
        @memcpy(value_buf[0..kv.value_len], kv.value[0..kv.value_len]);
        value_buf[kv.value_len] = 0;
        cb(name_buf[0..kv.name_len :0].ptr, value_buf[0..kv.value_len :0].ptr, cookie);
    }
    return 0;
}

// --- __system_property_* compatibility (NDK code uses these directly) ---

export fn __system_property_get(name: [*:0]const u8, value: [*]u8) c_int {
    return property_get(name, value, null);
}

// AOSP sys/system_properties.h: int __system_property_set(...).
export fn __system_property_set(name: [*:0]const u8, value: [*:0]const u8) c_int {
    return property_set(name, value);
}

// --- dlext: NDK-adjacent code sometimes calls android_dlopen_ext ---

extern fn dlopen(file_name: ?[*:0]const u8, flags: c_int) ?*anyopaque;

export fn android_dlopen_ext(file_name: ?[*:0]const u8, flags: c_int, extinfo: ?*anyopaque) ?*anyopaque {
    _ = extinfo;
    return dlopen(file_name, flags);
}

