//! isakai-bionic-exec: run an Android NDK PIE executable (e.g. an app's
//! generator binary) under ATL's bionic_translation loader. The host
//! kernel cannot exec these directly: PT_INTERP names /system/bin/linker64
//! (absent on the host), so plain execve fails with ENOENT. The bionic
//! shim's execve interposer redirects such execs here with argv
//! [helper, target, args...]; the target runs with argv[1..] and inherits
//! stdio, so the redirect is transparent to the spawning app.
//!
//! Mechanism: host-dlopen libdl_bio (ISAKAI_BIONIC_LOADER) plus the three
//! wrapper libs RTLD_GLOBAL (mirrors the shim's initLoader, which is what
//! makes bionic_<name> symbols resolvable), point the loader's
//! _r_debug_ptr at the real glibc _r_debug, bionic_dlopen the target, and
//! jump to its entry with a kernel-shaped initial stack. The loader
//! resolves the target's __libc_init to bionic___libc_init; this binary
//! overrides that symbol to drop the garbage fourth (structors) argument
//! _start passes and forward to the real one with an empty table
//! (constructors already ran at dlopen; running fini at exit would walk
//! garbage). Diagnostics go to stderr; stdout stays pristine for the app.

const std = @import("std");

extern var environ: [*:null]?[*:0]u8;

extern fn dlopen(path: ?[*:0]const u8, flags: c_int) callconv(.c) ?*anyopaque;
extern fn dlsym(handle: ?*anyopaque, symbol: [*:0]const u8) callconv(.c) ?*anyopaque;

// dlfcn.h values (glibc: RTLD_DEFAULT is NULL).
const rtld_now: c_int = 2;
const rtld_global: c_int = 0x100;
const rtld_default: ?*anyopaque = null;

// First fields of ElfW(Phdr_info) (link.h); the loader owns the record,
// this view only reads the address and name.
const DlPhdrInfo = extern struct {
    addr: usize,
    name: ?[*:0]const u8,
};

const BionicDlopenFn = *const fn ([*:0]const u8, c_int) callconv(.c) ?*anyopaque;
const BionicIterateFn = *const fn (
    *const fn (*DlPhdrInfo, usize, ?*anyopaque) callconv(.c) c_int,
    ?*anyopaque,
) callconv(.c) c_int;
const RealInitFn = *const fn (
    ?*anyopaque,
    ?*const fn () callconv(.c) void,
    ?*const fn (c_int, [*c][*c]u8, [*c][*c]u8) callconv(.c) c_int,
    ?*const anyopaque,
) callconv(.c) noreturn;

var real_init: ?RealInitFn = null;

// Empty structors table: the loader already ran constructors at dlopen,
// so fini-at-exit must be skipped, and _start's fourth argument register
// holds garbage anyway.
const empty_structors: [3]?*anyopaque = .{ null, null, null };

/// Swallow the loader's debug chatter. The loader resolves
/// __android_log_vprint through the host namespace; letting it through
/// would fill the spawner's stderr pipe (64KB) while the spawner reads
/// stdout, deadlocking both sides. Real load failures still surface: the
/// loader reports them via bionic_dlerror, which fail() prints.
export fn __android_log_vprint(
    prio: c_int,
    tag: ?[*:0]const u8,
    fmt: ?[*:0]const u8,
    ...
) callconv(.c) c_int {
    _ = prio;
    _ = tag;
    _ = fmt;
    return 0;
}

/// Override the loader's wrap-branch lookup for the target's __libc_init
/// reference (the executable links it; the loader first tries
/// bionic___libc_init in the host namespace, which finds this
/// definition before libc_bio's own).
export fn bionic___libc_init(
    raw_args: ?*anyopaque,
    onexit: ?*const fn () callconv(.c) void,
    slingshot: ?*const fn (c_int, [*c][*c]u8, [*c][*c]u8) callconv(.c) c_int,
    structors: ?*const anyopaque,
) callconv(.c) noreturn {
    _ = structors;
    const f = real_init orelse std.os.linux.exit(127);
    f(raw_args, onexit, slingshot, &empty_structors);
    unreachable;
}

const IterCtx = struct {
    want_base: []const u8,
    want_full: []const u8,
    bias: usize = 0,
    found: bool = false,
};

fn iterateCb(info: *DlPhdrInfo, size: usize, data: ?*anyopaque) callconv(.c) c_int {
    _ = size;
    const ctx: *IterCtx = @ptrCast(@alignCast(data));
    const raw = info.name orelse return 0;
    const name = std.mem.span(raw);
    if (std.mem.eql(u8, name, ctx.want_full) or std.mem.eql(u8, name, ctx.want_base)) {
        ctx.bias = info.addr;
        ctx.found = true;
    }
    return 0;
}

fn fail(io: std.Io, comptime fmt: []const u8, args: anytype) noreturn {
    var buf: [1024]u8 = undefined;
    var w = std.Io.File.stderr().writer(io, &buf);
    w.interface.print("isakai-bionic-exec: " ++ fmt ++ "\n", args) catch {};
    w.interface.flush() catch {};
    std.process.exit(127);
}

pub fn main(init: std.process.Init) !void {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    if (argv.len < 2) fail(io, "usage: isakai-bionic-exec <target> [args...]", .{});
    const target = argv[1];

    const loader_opt = init.environ_map.get("ISAKAI_BIONIC_LOADER");
    const loader_path = loader_opt orelse fail(io, "ISAKAI_BIONIC_LOADER is not set", .{});
    const loader_dir = std.fs.path.dirname(loader_path) orelse fail(io, "bad loader path", .{});

    // Mirror the shim's initLoader: wrapper libs must be visible in
    // RTLD_DEFAULT so the loader's bionic_<name> wrap branch resolves
    // (e.g. bionic___strlen_chk).
    const globals = [_][]const u8{ "libc_bio.so.0", "libpthread_bio.so.0", "libstdc++_bio.so.0" };
    var libc_bio: ?*anyopaque = null;
    for (globals) |name| {
        const p = try std.fmt.allocPrintSentinel(arena, "{s}/{s}", .{ loader_dir, name }, 0);
        const h = dlopen(p, rtld_now | rtld_global) orelse fail(io, "cannot load {s}", .{name});
        if (libc_bio == null) libc_bio = h;
    }
    const loader_path_z = try std.fmt.allocPrintSentinel(arena, "{s}", .{loader_path}, 0);
    const loader = dlopen(loader_path_z, rtld_now | rtld_global) orelse
        fail(io, "cannot load bionic loader", .{});

    // The loader notifies gdb through _r_debug_ptr; it must point at a
    // real link_map chain, i.e. glibc's own _r_debug.
    const r_debug_ptr: ?**anyopaque = @ptrCast(@alignCast(dlsym(loader, "_r_debug_ptr")));
    const rdp = r_debug_ptr orelse fail(io, "loader has no _r_debug_ptr", .{});
    rdp.* = dlsym(rtld_default, "_r_debug") orelse fail(io, "no host _r_debug", .{});

    const bionic_dlopen: BionicDlopenFn = @ptrCast(@alignCast(
        dlsym(loader, "bionic_dlopen") orelse fail(io, "loader has no bionic_dlopen", .{}),
    ));
    const bionic_iterate: BionicIterateFn = @ptrCast(@alignCast(
        dlsym(loader, "bionic_dl_iterate_phdr") orelse fail(io, "loader has no bionic_dl_iterate_phdr", .{}),
    ));
    real_init = @ptrCast(@alignCast(
        dlsym(libc_bio, "bionic___libc_init") orelse fail(io, "libc_bio has no bionic___libc_init", .{}),
    ));

    const target_z = try std.fmt.allocPrintSentinel(arena, "{s}", .{target}, 0);
    _ = bionic_dlopen(target_z, rtld_now) orelse {
        const be: ?*const fn () callconv(.c) ?[*:0]const u8 = @ptrCast(@alignCast(dlsym(loader, "bionic_dlerror")));
        const msg = if (be) |f| f() else null;
        fail(io, "cannot load {s}: {s}", .{ target, if (msg) |m| std.mem.span(m) else "unknown error" });
    };

    var ctx = IterCtx{
        .want_base = std.fs.path.basename(target),
        .want_full = target,
    };
    _ = bionic_iterate(iterateCb, &ctx);
    if (!ctx.found) fail(io, "target not in loader phdr list", .{});

    // Entry address from the file's own ELF header.
    var file = std.Io.Dir.cwd().openFile(io, target, .{ .mode = .read_only }) catch
        fail(io, "cannot open {s}", .{target});
    defer file.close(io);
    var hdr_buf: [@sizeOf(std.elf.Elf64_Ehdr)]u8 align(8) = undefined;
    var read_buf: [8192]u8 = undefined;
    var reader = file.reader(io, &read_buf);
    const n = reader.interface.readSliceShort(&hdr_buf) catch fail(io, "cannot read {s}", .{target});
    if (n < hdr_buf.len) fail(io, "{s}: short ELF header", .{target});
    const ehdr: *const std.elf.Elf64_Ehdr = @ptrCast(@alignCast(&hdr_buf));
    if (!std.mem.eql(u8, ehdr.e_ident[0..4], std.elf.MAGIC)) fail(io, "{s}: not an ELF file", .{target});
    const entry: usize = ctx.bias +% @as(usize, @intCast(ehdr.e_entry));

    // Kernel-shaped initial stack: [argc][&argv0][argv][0][envp][0][auxv].
    // Strings stay where they are (helper argv/environ memory outlives the
    // jump); only the pointer block, auxv and AT_RANDOM bytes are built.
    var stack: [65536]u8 = undefined;
    const sp_top: usize = @intFromPtr(&stack) + stack.len;
    const rand_ptr: usize = sp_top - 16;
    const t_argv = argv[1..];
    var t_env_len: usize = 0;
    while (environ[t_env_len] != null) : (t_env_len += 1) {}
    const naux: usize = 12;
    const block_words: usize = 1 + 1 + (t_argv.len + 1) + (t_env_len + 1) + 2 * naux;
    const sp: usize = (sp_top - 16 - block_words * 8) & ~@as(usize, 15);
    const blk: [*]usize = @ptrFromInt(sp);
    blk[0] = t_argv.len;
    blk[1] = sp + 16; // &argv[0]
    for (t_argv, 0..) |a, i| blk[2 + i] = @intFromPtr(a.ptr);
    blk[2 + t_argv.len] = 0;
    for (0..t_env_len) |i| blk[2 + t_argv.len + 1 + i] = @intFromPtr(environ[i].?);
    blk[2 + t_argv.len + 1 + t_env_len] = 0;
    const aux: [*]usize = @ptrFromInt(sp + (2 + t_argv.len + 1 + t_env_len + 1) * 8);
    const pairs = [_][2]usize{
        .{ 3, ctx.bias +% @as(usize, @intCast(ehdr.e_phoff)) }, // AT_PHDR
        .{ 4, @as(usize, @intCast(ehdr.e_phentsize)) }, // AT_PHENT
        .{ 5, @as(usize, @intCast(ehdr.e_phnum)) }, // AT_PHNUM
        .{ 9, entry }, // AT_ENTRY
        .{ 11, @as(usize, @intCast(std.os.linux.getuid())) }, // AT_UID
        .{ 12, @as(usize, @intCast(std.os.linux.geteuid())) }, // AT_EUID
        .{ 13, @as(usize, @intCast(std.os.linux.getgid())) }, // AT_GID
        .{ 14, @as(usize, @intCast(std.os.linux.getegid())) }, // AT_EGID
        .{ 23, 0 }, // AT_SECURE
        .{ 6, 4096 }, // AT_PAGESZ
        .{ 17, 100 }, // AT_CLKTCK
        .{ 25, rand_ptr }, // AT_RANDOM
    };
    for (pairs, 0..) |p, i| {
        aux[2 * i] = p[0];
        aux[2 * i + 1] = p[1];
    }

    // "memory" clobber: the pointer block above is written through an
    // integer-derived pointer the compiler cannot see aliasing, so order
    // the stores explicitly before the stack switch.
    asm volatile (
        \\mov %[sp], %%rsp
        \\xor %%edx, %%edx
        \\jmp *%[entry]
        :
        : [sp] "r" (sp),
          [entry] "r" (entry),
        // The xor zeroes rdx (atexit slot for _start); keep the entry
        // input out of it.
        : .{ .memory = true, .rdx = true }
    );
    unreachable;
}
