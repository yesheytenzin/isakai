// SPDX-License-Identifier: GPL-3.0-or-later

//! Host implementation of the android/log.h entry points. Exported by the
//! bionic shim (NDK app libraries) and linked into the libandroidfw
//! resource engine, whose AOSP sources log through the same functions.

const std = @import("std");

extern fn vsnprintf(str: [*]u8, size: usize, format: [*:0]const u8, ap: *anyopaque) c_int;

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

export fn __android_log_print(priority: c_int, tag: [*:0]const u8, fmt: ?[*:0]const u8, ...) c_int {
    var buf: [4096]u8 = undefined;
    var ap = @cVaStart();
    defer @cVaEnd(&ap);
    const f = fmt orelse {
        logScoped(priority, tag, "");
        return 1;
    };
    const n = vsnprintf(&buf, buf.len, f, @ptrCast(&ap));
    if (n < 0) return -1;
    const len: usize = @min(@as(usize, @intCast(n)), buf.len - 1);
    logScoped(priority, tag, buf[0..len]);
    return 1;
}

export fn __android_log_vprint(priority: c_int, tag: [*:0]const u8, fmt: ?[*:0]const u8, ap: *anyopaque) c_int {
    var buf: [4096]u8 = undefined;
    const f = fmt orelse {
        logScoped(priority, tag, "");
        return 1;
    };
    const n = vsnprintf(&buf, buf.len, f, ap);
    if (n < 0) return -1;
    const len: usize = @min(@as(usize, @intCast(n)), buf.len - 1);
    logScoped(priority, tag, buf[0..len]);
    return 1;
}

// AOSP android/log.h: void __android_log_assert(const char *cond,
// const char *tag, const char *fmt, ...). Both cond and fmt may be null:
// LOG_ALWAYS_FATAL passes a null cond, LOG_ALWAYS_FATAL_IF without a message
// passes a null fmt. The call always logs the failure and aborts.
export fn __android_log_assert(cond: ?[*:0]const u8, tag: [*:0]const u8, fmt: ?[*:0]const u8, ...) void {
    var buf: [4096]u8 = undefined;
    var ap = @cVaStart();
    defer @cVaEnd(&ap);
    const tag_s = std.mem.sliceTo(tag, 0);
    const cond_s = if (cond) |c| std.mem.sliceTo(c, 0) else "";
    const msg: []const u8 = if (fmt) |f| blk: {
        _ = vsnprintf(&buf, buf.len, f, @ptrCast(&ap));
        break :blk std.mem.sliceTo(&buf, 0);
    } else "";
    std.debug.print("{s}: assertion '{s}' failed: {s}\n", .{ tag_s, cond_s, msg });
    std.process.abort();
}

// AOSP liblog: int __android_log_buf_print(int bufID, int prio,
// const char *tag, const char *fmt, ...). Host logging has one buffer.
export fn __android_log_buf_print(buf_id: c_int, priority: c_int, tag: [*:0]const u8, fmt: ?[*:0]const u8, ...) c_int {
    _ = buf_id;
    var buf: [4096]u8 = undefined;
    var ap = @cVaStart();
    defer @cVaEnd(&ap);
    const f = fmt orelse {
        logScoped(priority, tag, "");
        return 1;
    };
    const n = vsnprintf(&buf, buf.len, f, @ptrCast(&ap));
    if (n < 0) return -1;
    const len: usize = @min(@as(usize, @intCast(n)), buf.len - 1);
    logScoped(priority, tag, buf[0..len]);
    return 1;
}
