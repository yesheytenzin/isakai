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

comptime {
    _ = @import("android_log");
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
    const n = @min(v.len, prop_value_max - 1);
    @memcpy(buf[0..n], v[0..n]);
    buf[n] = 0;
    return @intCast(n);
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
    // Callers may read or change properties from their callback. Retain a
    // bounded snapshot, then release the lock before entering caller code.
    var snapshot: [prop_max]PropEntry = undefined;
    propLock();
    const count = props_len;
    @memcpy(snapshot[0..count], props[0..count]);
    propUnlock();
    for (snapshot[0..count]) |*kv| {
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

export fn android_dlopen_ext(file_name: ?[*:0]const u8, flags: c_int, extinfo: ?*anyopaque) ?*anyopaque {
    _ = extinfo;
    return dlopen(file_name, flags);
}

// --- N6: route app .so loading through ATL's bionic loader ---
//
// HotSpot's System.loadLibrary ends in a host glibc dlopen, which cannot
// resolve an Android DSO's libc.so/libm.so/libdl.so (@LIBC versions, sonames
// that are linker scripts or absent on the host). Interpose dlopen/dlsym so
// any library glibc refuses is retried with bionic_dlopen from the pinned
// ATL bionic_translation build; the returned handle is recognised by the
// interposed dlsym, so HotSpot still resolves and calls JNI_OnLoad itself.
// Normal host libraries keep going through glibc untouched.

const DlInfo = extern struct {
    fname: ?[*:0]const u8,
    fbase: ?*anyopaque,
    sname: ?[*:0]const u8,
    saddr: ?*anyopaque,
};

extern fn dlvsym(handle: ?*anyopaque, symbol: [*:0]const u8, version: [*:0]const u8) ?*anyopaque;
extern fn dladdr(addr: ?*const anyopaque, info: *DlInfo) c_int;
extern fn setenv(name: [*:0]const u8, value: [*:0]const u8, overwrite: c_int) c_int;
extern fn getenv(name: [*:0]const u8) ?[*:0]u8;
// ELF link.h `struct r_debug` (64-bit): version, map, brk, state, ldbase.
// Declared at its true size: the Android loader reads the whole struct
// through _r_debug_ptr, and a narrower declaration corrupts neighbors.
const RDebug = extern struct {
    r_version: c_int,
    r_map: ?*anyopaque,
    r_brk: usize,
    r_state: c_int,
    r_ldbase: usize,
};
extern var _r_debug: RDebug;
extern fn atan2(y: f64, x: f64) f64;

const rtld_next: ?*anyopaque = @ptrFromInt(~@as(usize, 0));

const DlopenFn = *const fn (?[*:0]const u8, c_int) callconv(.c) ?*anyopaque;
const DlsymFn = *const fn (?*anyopaque, [*:0]const u8) callconv(.c) ?*anyopaque;
const DlcloseFn = *const fn (?*anyopaque) callconv(.c) c_int;
const DlerrorFn = *const fn () callconv(.c) ?[*:0]u8;

var real_state = std.atomic.Value(u8).init(0); // 0 new, 1 busy, 2 ok, 3 failed
var real_dlopen: ?DlopenFn = null;
var real_dlsym: ?DlsymFn = null;
var real_dlclose: ?DlcloseFn = null;
var real_dlerror: ?DlerrorFn = null;

fn resolveReal() void {
    const observed = real_state.cmpxchgStrong(0, 1, .acq_rel, .acquire);
    if (observed) |_| {
        while (true) {
            const state = real_state.load(.acquire);
            if (state == 2 or state == 3) return;
            std.atomic.spinLoopHint();
        }
    }
    // RTLD_NEXT skips this DSO, and dlvsym avoids binding back to the
    // interposed dlopen/dlsym exports in this library.
    const versions = [_][*:0]const u8{ "GLIBC_2.34", "GLIBC_2.2.5" };
    for (versions) |version| {
        if (dlvsym(rtld_next, "dlopen", version)) |p| {
            real_dlopen = @ptrCast(@alignCast(p));
            break;
        }
    }
    for (versions) |version| {
        if (dlvsym(rtld_next, "dlsym", version)) |p| {
            real_dlsym = @ptrCast(@alignCast(p));
            break;
        }
    }
    for (versions) |version| {
        if (dlvsym(rtld_next, "dlclose", version)) |p| {
            real_dlclose = @ptrCast(@alignCast(p));
            break;
        }
    }
    for (versions) |version| {
        if (dlvsym(rtld_next, "dlerror", version)) |p| {
            real_dlerror = @ptrCast(@alignCast(p));
            break;
        }
    }
    const ok = real_dlopen != null and real_dlsym != null and
        real_dlclose != null and real_dlerror != null;
    real_state.store(if (ok) 2 else 3, .release);
}

const BionicDlopenFn = *const fn ([*:0]const u8, c_int) callconv(.c) ?*anyopaque;
const BionicDlsymFn = *const fn (?*anyopaque, [*:0]const u8) callconv(.c) ?*anyopaque;
const BionicDlcloseFn = *const fn (?*anyopaque) callconv(.c) c_int;
const BionicDlerrorFn = *const fn () callconv(.c) ?[*:0]const u8;

var loader_state = std.atomic.Value(u8).init(0); // 0 new, 1 busy, 2 ok, 3 failed
var bionic_dlopen: ?BionicDlopenFn = null;
var bionic_dlsym: ?BionicDlsymFn = null;
var bionic_dlclose: ?BionicDlcloseFn = null;
var bionic_dlerror: ?BionicDlerrorFn = null;

var bionic_handles: std.ArrayListUnmanaged(?*anyopaque) = .empty;
var handles_lock = std.atomic.Value(bool).init(false);

fn handlesLock() void {
    while (handles_lock.swap(true, .acquire)) std.atomic.spinLoopHint();
}

fn handlesUnlock() void {
    handles_lock.store(false, .release);
}

fn trackHandle(handle: *anyopaque) bool {
    handlesLock();
    defer handlesUnlock();
    for (bionic_handles.items) |*slot| {
        if (slot.* == null) {
            slot.* = handle;
            return true;
        }
    }
    // No cap: grow the table instead of closing a good library and
    // failing the load once the initial slots fill.
    bionic_handles.append(std.heap.c_allocator, handle) catch return false;
    return true;
}

fn isTracked(handle: *anyopaque) bool {
    handlesLock();
    defer handlesUnlock();
    for (bionic_handles.items) |slot| {
        if (slot == handle) return true;
    }
    return false;
}

fn untrackHandle(handle: *anyopaque) bool {
    handlesLock();
    defer handlesUnlock();
    for (bionic_handles.items) |*slot| {
        if (slot.* == handle) {
            slot.* = null;
            return true;
        }
    }
    return false;
}

threadlocal var err_buf: [512]u8 = undefined;
threadlocal var err_len: usize = 0;
// Set while inside bionic_dlopen on this thread. ATL's own
// missing-dependency fallback calls the host dlopen, which lands back
// here through the interposer; retrying bionic for that nested call
// would recurse into the loader, so stay on the host path instead.
threadlocal var in_bionic: bool = false;

fn clearError() void {
    err_len = 0;
}

fn setError(comptime fmt: []const u8, args: anytype) void {
    const text = std.mem.printSentinel(&err_buf, fmt, args, 0) catch {
        err_buf[0] = 0;
        err_len = 1;
        return;
    };
    err_len = text.len;
}

/// Append `a` (and optional `b`) to an inherited colon-separated path.
fn appendEnv(name: [*:0]const u8, a: []const u8, b: ?[]const u8) void {
    const old = if (getenv(name)) |v| std.mem.sliceTo(v, 0) else "";
    var buf: [8192]u8 = undefined;
    const value = if (b) |bb|
        (if (old.len > 0)
            std.mem.printSentinel(&buf, "{s}:{s}:{s}", .{ old, a, bb }, 0) catch return
        else
            std.mem.printSentinel(&buf, "{s}:{s}", .{ a, bb }, 0) catch return)
    else if (old.len > 0)
        std.mem.printSentinel(&buf, "{s}:{s}", .{ old, a }, 0) catch return
    else
        std.mem.printSentinel(&buf, "{s}", .{a}, 0) catch return;
    _ = setenv(name, value.ptr, 1);
}

fn initLoader() bool {
    var info: DlInfo = undefined;
    if (dladdr(@ptrCast(&dlopen), &info) == 0) {
        setError("isakai: cannot locate the bionic shim path", .{});
        return false;
    }
    const self_path = std.mem.sliceTo(info.fname orelse {
        setError("isakai: bionic shim has no path", .{});
        return false;
    }, 0);
    const lib_dir = std.fs.path.dirname(self_path) orelse return false;

    var path_buf: [4096]u8 = undefined;
    // ATL reads XDG_DATA_DIRS and BIONIC_LD_LIBRARY_PATH in libdl_bio's
    // constructor, so both must be in place before the host dlopen below.
    const cfg_root = std.fmt.bufPrint(&path_buf, "{s}/bionic/xdg", .{lib_dir}) catch return false;
    appendEnv("XDG_DATA_DIRS", cfg_root, null);
    var libm_dir: ?[]const u8 = null;
    var libm_info: DlInfo = undefined;
    if (dladdr(@ptrCast(&atan2), &libm_info) != 0) {
        if (libm_info.fname) |p| libm_dir = std.fs.path.dirname(std.mem.sliceTo(p, 0));
    }
    const bionic_lib_dir = std.fmt.bufPrint(&path_buf, "{s}/bionic", .{lib_dir}) catch return false;
    appendEnv("BIONIC_LD_LIBRARY_PATH", bionic_lib_dir, libm_dir);

    const loader_path = std.mem.printSentinel(&path_buf, "{s}/bionic/libdl_bio.so.0", .{lib_dir}, 0) catch return false;
    const loader = real_dlopen.?(loader_path.ptr, 0x102) orelse { // RTLD_NOW|RTLD_GLOBAL
        setError("isakai: cannot load {s}: {s}", .{ loader_path, real_dlerror.?() orelse "unknown error" });
        return false;
    };
    // The wrapper libraries must be visible in RTLD_DEFAULT: the shim
    // linker resolves libc symbols as bionic_<name> through dlsym.
    const globals = [_][]const u8{ "libc_bio.so.0", "libpthread_bio.so.0", "libstdc++_bio.so.0" };
    for (globals) |name| {
        const p = std.mem.printSentinel(&path_buf, "{s}/bionic/{s}", .{ lib_dir, name }, 0) catch return false;
        if (real_dlopen.?(p.ptr, 0x102) == null) {
            setError("isakai: cannot load {s}: {s}", .{ p, real_dlerror.?() orelse "unknown error" });
            return false;
        }
    }
    // ATL expects the main executable to initialise this (bionic_compat.c).
    const rdp = real_dlsym.?(loader, "_r_debug_ptr") orelse {
        setError("isakai: libdl_bio has no _r_debug_ptr", .{});
        return false;
    };
    const debug_ptr: *?*anyopaque = @ptrCast(@alignCast(rdp));
    debug_ptr.* = @ptrCast(&_r_debug);

    bionic_dlopen = @ptrCast(@alignCast(real_dlsym.?(loader, "bionic_dlopen") orelse return false));
    bionic_dlsym = @ptrCast(@alignCast(real_dlsym.?(loader, "bionic_dlsym") orelse return false));
    bionic_dlclose = @ptrCast(@alignCast(real_dlsym.?(loader, "bionic_dlclose") orelse return false));
    bionic_dlerror = @ptrCast(@alignCast(real_dlsym.?(loader, "bionic_dlerror") orelse return false));
    return true;
}

fn ensureLoader() bool {
    const observed = loader_state.cmpxchgStrong(0, 1, .acq_rel, .acquire);
    if (observed) |_| {
        while (true) {
            const state = loader_state.load(.acquire);
            if (state == 2 or state == 3) return state == 2;
            std.atomic.spinLoopHint();
        }
    }
    const ok = initLoader();
    loader_state.store(if (ok) 2 else 3, .release);
    return ok;
}

export fn dlopen(file_name: ?[*:0]const u8, flags: c_int) ?*anyopaque {
    resolveReal();
    if (real_dlopen == null) return null;
    // Nested ATL fallback (see in_bionic): host path only, no bionic retry.
    if (in_bionic) return real_dlopen.?(file_name, flags);
    // Host libraries (libjvm's own dependencies, JNI libs built for glibc)
    // keep the normal path. Only a library glibc rejects is retried with the
    // Android loader.
    if (real_dlopen.?(file_name, flags)) |handle| {
        clearError();
        return handle;
    }
    if (file_name == null) return null;
    if (!ensureLoader()) return null;
    in_bionic = true;
    const maybe_handle = bionic_dlopen.?(file_name.?, flags);
    in_bionic = false;
    const handle = maybe_handle orelse {
        if (bionic_dlerror) |f| {
            if (f()) |message| setError("{s}", .{std.mem.sliceTo(message, 0)});
        }
        return null;
    };
    if (!trackHandle(handle)) {
        _ = bionic_dlclose.?(handle);
        setError("isakai: out of memory tracking bionic library", .{});
        return null;
    }
    clearError();
    return handle;
}

export fn dlsym(handle: ?*anyopaque, symbol: [*:0]const u8) ?*anyopaque {
    resolveReal();
    if (handle) |h| {
        if (isTracked(h)) {
            const sym = bionic_dlsym.?(h, symbol) orelse {
                if (bionic_dlerror) |f| {
                    if (f()) |message| setError("{s}", .{std.mem.sliceTo(message, 0)});
                }
                return null;
            };
            clearError();
            return sym;
        }
    }
    const sym = if (real_dlsym) |f| f(handle, symbol) else null;
    if (sym != null) clearError();
    return sym;
}

export fn dlclose(handle: ?*anyopaque) c_int {
    resolveReal();
    if (handle) |h| {
        if (untrackHandle(h)) {
            const rc = if (bionic_dlclose) |f| f(h) else 0;
            if (rc == 0) clearError();
            return rc;
        }
    }
    const rc = if (real_dlclose) |f| f(handle) else -1;
    if (rc == 0) clearError();
    return rc;
}

export fn dlerror() ?[*:0]u8 {
    resolveReal();
    if (err_len != 0) {
        err_len = 0;
        return @ptrCast(&err_buf);
    }
    return if (real_dlerror) |f| f() else null;
}
