//! Kernel-level sandboxing for sandboxed app JVMs: Landlock filesystem
//! confinement + seccomp-BPF syscall allowlist, applied in the
//! `uid-helper run` child before privilege drop and exec.
//!
//! Namespace setup and credential drop happen first. Landlock is installed
//! before seccomp, then both restrictions survive the JVM exec and descendants.
//! Landlock ABI probing follows the kernel UAPI (null/zero + VERSION); creation
//! uses attributes + flags=0. REFER/TRUNCATE rights are negotiated on ABI 2/3.
//! Missing kernel support warns; other failures abort launch. The debug-only
//! ISAKAI_CONFINEMENT=0 opt-out skips both layers. Filesystem grants are built
//! by uid_helper.applyRuntimeConfinement; no / or whole-home grants are used.
//! The seccomp allowlist is x86_64-only and denies other syscalls with EPERM.
//! These entry points are allocation-free for use after fork.

const std = @import("std");
const linux = std.os.linux;

// ---------------------------------------------------------------------------
// seccomp-BPF (classic cBPF via sock_filter, attached with prctl)
// ---------------------------------------------------------------------------

/// One classic-BPF filter block (uabi `struct sock_filter`).
pub const SockFilter = extern struct {
    code: u16,
    jt: u8,
    jf: u8,
    k: u32,
};

/// AUDIT_ARCH_X86_64 = EM_X86_64|__AUDIT_ARCH_64BIT|__AUDIT_ARCH_LE
/// = 0x3e | 0x80000000 | 0x40000000 (linux/audit.h).
pub const AUDIT_ARCH_X86_64: u32 = 0x3e | 0x80000000 | 0x40000000;

// BPF class/size/op constants (classic BPF, from linux/filter.h).
const BPF_LD: u16 = 0x00;
const BPF_JMP: u16 = 0x05;
const BPF_RET: u16 = 0x06;
const BPF_ABS: u16 = 0x20;
const BPF_JEQ: u16 = 0x10;
const BPF_JGE: u16 = 0x30;
const BPF_K: u16 = 0x00;
const BPF_W: u16 = 0x00;

const BPF_JEQ_K: u16 = BPF_JMP | BPF_JEQ | BPF_K;
const BPF_JGE_K: u16 = BPF_JMP | BPF_JGE | BPF_K;
const BPF_LD_W_ABS: u16 = BPF_LD | BPF_W | BPF_ABS;
const BPF_RET_K: u16 = BPF_RET | BPF_K;
const BPF_JA_K: u16 = BPF_JMP | 0x00 | BPF_K; // unconditional jump

// Offsets within `struct seccomp_data` (see std.os.linux.seccomp.data):
// nr: c_int @0, arch: u32 @4, instruction_pointer: u64 @8, args: u64 @16.
const OFF_NR: u32 = 0;
const OFF_ARCH: u32 = 4;
const OFF_ARGS: u32 = 16;

/// seccomp RET actions (std.os.linux.seccomp.RET).
const RET_ERRNO_EPERM: u32 = linux.SECCOMP.RET.ERRNO | 1; // EPERM=1
const RET_ALLOW: u32 = linux.SECCOMP.RET.ALLOW;

/// The syscall surface the runtime needs on x86_64. Ordered roughly by
/// group; the filter is a linear allowlist so order is free. Numbers are
/// the x86_64 ABI values (checked at comptime against std's SYS table).
pub const allowed_syscalls = [_]linux.SYS{
    // --- exec + process basics (helper tail runs AFTER the filter) ---
    linux.SYS.execve,
    linux.SYS.execveat,
    linux.SYS.exit,
    linux.SYS.exit_group,
    linux.SYS.set_tid_address,
    linux.SYS.rt_sigreturn,
    linux.SYS.getpid,
    linux.SYS.getppid,
    linux.SYS.gettid,
    linux.SYS.getuid,
    linux.SYS.geteuid,
    linux.SYS.getgid,
    linux.SYS.getegid,
    linux.SYS.getgroups,
    linux.SYS.getrlimit,
    // --- privilege drop tail (helper still runs these post-filter) ---
    linux.SYS.setgroups,
    linux.SYS.setresgid,
    linux.SYS.setresuid,
    linux.SYS.chdir,
    linux.SYS.umask,
    // --- signals ---
    linux.SYS.rt_sigaction,
    linux.SYS.rt_sigprocmask,
    linux.SYS.rt_sigtimedwait,
    linux.SYS.kill,
    linux.SYS.tgkill,
    linux.SYS.sigaltstack,
    // --- memory (JVM heap, code cache, SHM mapping) ---
    linux.SYS.mmap,
    linux.SYS.munmap,
    linux.SYS.mprotect,
    linux.SYS.mremap,
    linux.SYS.madvise,
    linux.SYS.brk,
    linux.SYS.msync,
    linux.SYS.mincore,
    linux.SYS.pkey_mprotect, // JVM guard pages on newer kernels
    // --- threads (JVM GC + JIT compilers) ---
    linux.SYS.clone,
    linux.SYS.clone3,
    linux.SYS.fork,
    linux.SYS.vfork,
    linux.SYS.wait4,
    linux.SYS.waitid,
    linux.SYS.futex,
    linux.SYS.set_robust_list,
    linux.SYS.get_robust_list,
    linux.SYS.rseq,
    linux.SYS.sched_getaffinity,
    linux.SYS.sched_setaffinity,
    linux.SYS.sched_yield,
    linux.SYS.sched_getparam,
    linux.SYS.sched_getscheduler,
    linux.SYS.sched_setscheduler,
    linux.SYS.getcpu,
    // --- file IO (code, data, /proc, /sys, memfd, io_uring-free) ---
    linux.SYS.open,
    linux.SYS.openat,
    linux.SYS.openat2,
    linux.SYS.close,
    linux.SYS.close_range,
    linux.SYS.read,
    linux.SYS.write,
    linux.SYS.readv,
    linux.SYS.writev,
    linux.SYS.pread64,
    linux.SYS.pwrite64,
    linux.SYS.lseek,
    linux.SYS.fstat,
    linux.SYS.stat,
    linux.SYS.lstat,
    linux.SYS.newfstatat,
    linux.SYS.access,
    linux.SYS.fstatfs,
    linux.SYS.statfs,
    linux.SYS.statx,
    linux.SYS.faccessat,
    linux.SYS.faccessat2,
    linux.SYS.fcntl,
    linux.SYS.ioctl,
    linux.SYS.getdents64,
    linux.SYS.mkdir,
    linux.SYS.mkdirat,
    linux.SYS.rmdir,
    linux.SYS.unlink,
    linux.SYS.unlinkat,
    linux.SYS.rename,
    linux.SYS.renameat,
    linux.SYS.renameat2,
    linux.SYS.ftruncate,
    linux.SYS.fchmod,
    linux.SYS.fchown,
    linux.SYS.dup,
    linux.SYS.dup2,
    linux.SYS.dup3,
    linux.SYS.pipe,
    linux.SYS.pipe2,
    linux.SYS.memfd_create,
    linux.SYS.flock,
    linux.SYS.getcwd,
    linux.SYS.readlink,
    linux.SYS.readlinkat,
    linux.SYS.utimensat,
    linux.SYS.fsync,
    linux.SYS.fdatasync,
    linux.SYS.sync_file_range,
    linux.SYS.copy_file_range,
    linux.SYS.sendfile,
    linux.SYS.splice,
    linux.SYS.vmsplice,
    linux.SYS.getrandom,
    // --- polling / timers (event loop + JIT watchdogs) ---
    linux.SYS.epoll_create1,
    linux.SYS.epoll_ctl,
    linux.SYS.epoll_wait,
    linux.SYS.epoll_pwait,
    linux.SYS.poll,
    linux.SYS.ppoll,
    linux.SYS.select,
    linux.SYS.timerfd_create,
    linux.SYS.timerfd_settime,
    linux.SYS.eventfd2,
    linux.SYS.clock_gettime,
    linux.SYS.clock_getres,
    linux.SYS.clock_nanosleep,
    linux.SYS.nanosleep,
    linux.SYS.gettimeofday,
    // --- unix sockets: wayland (bind-mounted socket), bridge, pulse, dbus ---
    linux.SYS.socket,
    linux.SYS.socketpair,
    linux.SYS.connect,
    linux.SYS.bind,
    linux.SYS.listen,
    linux.SYS.accept,
    linux.SYS.accept4,
    linux.SYS.sendto,
    linux.SYS.recvfrom,
    linux.SYS.sendmsg,
    linux.SYS.recvmsg,
    linux.SYS.sendmmsg,
    linux.SYS.recvmmsg,
    linux.SYS.shutdown,
    linux.SYS.getsockname,
    linux.SYS.getpeername,
    linux.SYS.setsockopt,
    linux.SYS.getsockopt,
    // --- shm / posix ipc (fontconfig caches etc. best-effort) ---
    linux.SYS.shmget,
    linux.SYS.shmat,
    linux.SYS.shmdt,
    linux.SYS.shmctl,
    // --- sysinfo / uname / misc introspection ---
    linux.SYS.uname,
    linux.SYS.sysinfo,
    linux.SYS.prctl,
    linux.SYS.arch_prctl,
    linux.SYS.capget,
    linux.SYS.prlimit64,
    linux.SYS.getrusage,
};

comptime {
    // Every allowed name must exist in the x86_64 SYS table (compile error
    // otherwise — protects against typos and std renames).
    for (allowed_syscalls) |n| _ = n;
}

/// Emit the cBPF program: arch pin → allowlist linear scan → EPERM.
/// Canonical seccomp_filter layout (seccomp_filter.rst):
///
///   [0]     ld  [OFF_ARCH]                ; A = seccomp_data.arch
///   [1]     jne ARCH → +2                 ; wrong ABI: land on KILL ret
///   [2]     ld  [OFF_NR]                  ; A = seccomp_data.nr
///   [3..3+N) jeq sys_i → +1               ; match: land on ALLOW ret
///   [3+N]   ret ERRNO|EPERM               ; default deny (graceful)
///   [4+N]   ret KILL_PROCESS              ; arch-mismatch target
///   [5+N]   ret ALLOW                     ; matched allows land here
///
/// Jump semantics: cBPF jt/jf are relative to the NEXT instruction
/// (pc' = pc + 1 + offset).
///   - arch mismatch: pc=1, jf=3 → pc' = 4 (KILL ret); jt=0 falls to [2].
///   - allow at index 3+j: jt = N-j+1 → pc' = 3+j+1+N-j+1 = 5+N (ALLOW).
pub fn buildFilter(buf: []SockFilter, syscalls: []const linux.SYS) []const SockFilter {
    const n = syscalls.len;
    // [0] load arch
    buf[0] = .{ .code = BPF_LD_W_ABS, .jt = 0, .jf = 0, .k = OFF_ARCH };
    // [1] wrong arch → KILL ret at [4+n]: jf = (4+n) - 2 = n + 2.
    // (n is runtime; fine.) Match falls through to [2].
    buf[1] = .{ .code = BPF_JEQ_K, .jt = 0, .jf = @intCast(n + 2), .k = AUDIT_ARCH_X86_64 };
    // [2] load nr
    buf[2] = .{ .code = BPF_LD_W_ABS, .jt = 0, .jf = 0, .k = OFF_NR };
    // [3..3+N) allowlist compares; match jumps to the ALLOW ret at [5+N]:
    // jt = (5+N) - (3+j+1) = N - j + 2.
    for (syscalls, 0..) |n_sys, j| {
        buf[3 + j] = .{ .code = BPF_JEQ_K, .jt = @intCast(n - j + 1), .jf = 0, .k = @truncate(@backingInt(n_sys)) };
    }
    // [3+N] default deny with EPERM (graceful degrade like old kernels).
    buf[3 + n] = .{ .code = BPF_RET_K, .jt = 0, .jf = 0, .k = RET_ERRNO_EPERM };
    // [4+N] KILL for wrong-arch (defensive; prctl form never fires it).
    buf[4 + n] = .{ .code = BPF_RET_K, .jt = 0, .jf = 0, .k = linux.SECCOMP.RET.KILL_PROCESS };
    // [5+N] ALLOW ret: the allowlist jumps land here.
    buf[5 + n] = .{ .code = BPF_RET_K, .jt = 0, .jf = 0, .k = RET_ALLOW };
    return buf[0 .. 6 + n];
}

/// Filter length: 3 header + N allows + 3 rets (EPERM/KILL/ALLOW).
pub const filter_capacity = 6 + allowed_syscalls.len;

/// PR_SET_SECCOMP=22, MODE_FILTER=2 (linux/seccomp.h). prctl form avoids
/// the `struct sock_fprog` SO_ATTACH layout entirely and needs NNP —
/// which childMain already set.
const PR_SET_SECCOMP: i32 = 22;
const SECCOMP_MODE_FILTER: usize = 2;

/// Attach the allowlist. Fail-closed: any error returns an error the
/// caller turns into _exit(127). `buf` must be filter_capacity long.
pub fn applySeccomp(buf: []SockFilter) !void {
    // Build into the caller's buffer, then hand to the kernel as
    // (len, ptr). prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &fprog):
    // the `struct sock_fprog` pointer is arg3 — arg5 stays 0 (verified
    // on the real kernel: arg5 gives EFAULT).
    const prog = buildFilter(buf, &allowed_syscalls);
    // Kernel ABI: `struct sock_fprog { u16 len; ptr filter }` — 16 bytes
    // on x86_64 (2B len, 6B pad, 8B ptr).
    const Fprog = extern struct {
        len: u16 align(8),
        filter: [*]const SockFilter,
    };
    var fprog = Fprog{ .len = @intCast(prog.len), .filter = prog.ptr };
    const rc = linux.prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, @intFromPtr(&fprog), 0, 0);
    switch (linux.errno(rc)) {
        .SUCCESS => {},
        .ACCES => return error.NoNewPrivsMissing,
        .NOSYS => return error.SeccompUnsupported,
        .INVAL => return error.BadFilter,
        else => return error.SeccompFailed,
    }
}

// ---------------------------------------------------------------------------
// Landlock (raw syscalls 444–446; no typed wrapper in std 0.17)
// ---------------------------------------------------------------------------

// linux/landlock.h ABI v1.
const LANDLOCK_ACCESS_FS_EXECUTE: u64 = 1 << 0;
const LANDLOCK_ACCESS_FS_WRITE_FILE: u64 = 1 << 1;
const LANDLOCK_ACCESS_FS_READ_FILE: u64 = 1 << 2;
const LANDLOCK_ACCESS_FS_READ_DIR: u64 = 1 << 3;
const LANDLOCK_ACCESS_FS_REMOVE_DIR: u64 = 1 << 4;
const LANDLOCK_ACCESS_FS_REMOVE_FILE: u64 = 1 << 5;
const LANDLOCK_ACCESS_FS_MAKE_CHAR: u64 = 1 << 6;
const LANDLOCK_ACCESS_FS_MAKE_DIR: u64 = 1 << 7;
const LANDLOCK_ACCESS_FS_MAKE_REG: u64 = 1 << 8;
const LANDLOCK_ACCESS_FS_MAKE_SOCK: u64 = 1 << 9;
const LANDLOCK_ACCESS_FS_MAKE_FIFO: u64 = 1 << 10;
const LANDLOCK_ACCESS_FS_MAKE_BLOCK: u64 = 1 << 11;
const LANDLOCK_ACCESS_FS_MAKE_SYM: u64 = 1 << 12;

// ABI 2/3 rights are negotiated rather than silently leaving rename/truncate
// outside the policy. See docs.kernel.org/userspace-api/landlock.html.
const LANDLOCK_ACCESS_FS_REFER: u64 = 1 << 13;
const LANDLOCK_ACCESS_FS_TRUNCATE: u64 = 1 << 14;
const handled_v1: u64 = (1 << 13) - 1;
pub const read_only: u64 = LANDLOCK_ACCESS_FS_EXECUTE | LANDLOCK_ACCESS_FS_READ_FILE | LANDLOCK_ACCESS_FS_READ_DIR;
pub const read_write: u64 = (handled_v1 | LANDLOCK_ACCESS_FS_REFER | LANDLOCK_ACCESS_FS_TRUNCATE) &
    ~(LANDLOCK_ACCESS_FS_EXECUTE | LANDLOCK_ACCESS_FS_MAKE_CHAR | LANDLOCK_ACCESS_FS_MAKE_BLOCK);
const file_rights: u64 = LANDLOCK_ACCESS_FS_EXECUTE | LANDLOCK_ACCESS_FS_READ_FILE | LANDLOCK_ACCESS_FS_WRITE_FILE | LANDLOCK_ACCESS_FS_TRUNCATE;

const LandlockPathBeneath = extern struct {
    allowed_access: u64,
    parent_fd: i32,
};

pub const Rule = struct {
    path: []const u8,
    access: u64 = read_only,
    // Only missing paths may be skipped (distro-specific runtime files).
    optional: bool = false,
};

pub fn landlockVersion() usize {
    return linux.syscall3(.landlock_create_ruleset, 0, 0, 1);
}

pub fn applyLandlock(rules: []const Rule, pathbuf: []u8) !void {
    const version = landlockVersion();
    switch (linux.errno(version)) {
        .SUCCESS => {},
        .NOSYS, .OPNOTSUPP => return error.LandlockUnsupported,
        else => return error.LandlockProbeFailed,
    }
    // VERSION returns an ABI number, never a file descriptor.
    var handled = handled_v1;
    if (version >= 2) handled |= LANDLOCK_ACCESS_FS_REFER;
    if (version >= 3) handled |= LANDLOCK_ACCESS_FS_TRUNCATE;
    const rc = linux.syscall3(.landlock_create_ruleset, @intFromPtr(&handled), @sizeOf(u64), 0);
    if (linux.errno(rc) != .SUCCESS) return error.LandlockRulesetFailed;
    const ruleset_fd: i32 = @intCast(rc);
    defer _ = linux.close(ruleset_fd);
    for (rules) |r| {
        if (r.path.len == 0 or r.path[0] != '/' or r.path.len + 1 > pathbuf.len) return error.BadLandlockPath;
        @memcpy(pathbuf[0..r.path.len], r.path);
        pathbuf[r.path.len] = 0;
        const z: [*:0]const u8 = @ptrCast(pathbuf.ptr);
        const opened = linux.openat(linux.AT.FDCWD, z, .{ .PATH = true, .CLOEXEC = true }, 0);
        if (linux.errno(opened) != .SUCCESS) {
            if (r.optional and linux.errno(opened) == .NOENT) continue;
            return error.LandlockOpenPathFailed;
        }
        const fd: i32 = @intCast(opened);
        defer _ = linux.close(fd);
        var st: linux.Statx = undefined;
        if (linux.errno(linux.statx(fd, "", linux.AT.EMPTY_PATH, .{ .TYPE = true }, &st)) != .SUCCESS) return error.LandlockStatFailed;
        var access = r.access & handled;
        if (st.mode & linux.S.IFMT != linux.S.IFDIR) access &= file_rights;
        if (access == 0) continue;
        var rule = LandlockPathBeneath{ .allowed_access = access, .parent_fd = fd };
        if (linux.errno(linux.syscall4(.landlock_add_rule, @intCast(ruleset_fd), 1, @intFromPtr(&rule), 0)) != .SUCCESS) return error.LandlockAddRuleFailed;
    }
    if (linux.errno(linux.syscall2(.landlock_restrict_self, @intCast(ruleset_fd), 0)) != .SUCCESS) return error.LandlockRestrictFailed;
}

/// Called after privileged namespace/mount setup and credential drop. Install
/// Landlock BEFORE seccomp: Landlock syscalls are not part of the app allowlist.
pub fn confineAndRestrict(rules: []const Rule, disabled: bool) !void {
    if (disabled) {
        warn("uid-helper run: confinement disabled by ISAKAI_CONFINEMENT=0\n");
        return;
    }
    var pathbuf: [4096]u8 = undefined;
    applyLandlock(rules, &pathbuf) catch |e| switch (e) {
        error.LandlockUnsupported => warn("uid-helper run: Landlock unavailable; continuing with UID isolation\n"),
        else => return e,
    };
    var filter_buf: [filter_capacity]SockFilter = undefined;
    applySeccomp(&filter_buf) catch |e| switch (e) {
        error.SeccompUnsupported => warn("uid-helper run: seccomp unavailable; continuing with UID isolation\n"),
        else => return e,
    };
}

fn warn(msg: []const u8) void {
    _ = linux.write(linux.STDERR_FILENO, msg.ptr, msg.len);
}

// ---------------------------------------------------------------------------
// Tests (pure, offline)
// ---------------------------------------------------------------------------

const testing = std.testing;

test "filter shape: header, allows, tails" {
    const sys = [_]linux.SYS{ .exit, .read, .write };
    var buf: [6 + sys.len]SockFilter = undefined;
    const prog = buildFilter(&buf, &sys);
    try testing.expectEqual(@as(usize, 6 + sys.len), prog.len);
    // [0] load arch; [1] arch compare
    try testing.expectEqual(BPF_LD_W_ABS, prog[0].code);
    try testing.expectEqual(OFF_ARCH, prog[0].k);
    try testing.expectEqual(BPF_JEQ_K, prog[1].code);
    try testing.expectEqual(AUDIT_ARCH_X86_64, prog[1].k);
    // [2] load nr; [3] first allow
    try testing.expectEqual(BPF_LD_W_ABS, prog[2].code);
    try testing.expectEqual(OFF_NR, prog[2].k);
    try testing.expectEqual(BPF_JEQ_K, prog[3].code);
    try testing.expectEqual(@backingInt(linux.SYS.exit), prog[3].k);
    // [3+N]=EPERM, [4+N]=KILL, [5+N]=ALLOW
    try testing.expectEqual(RET_ERRNO_EPERM, prog[3 + sys.len].k);
    try testing.expectEqual(linux.SECCOMP.RET.KILL_PROCESS, prog[4 + sys.len].k);
    try testing.expectEqual(RET_ALLOW, prog[5 + sys.len].k);
}

test "allowed syscalls are comptime-valid" {
    // Enum members are compile-checked by construction; sanity: filter
    // build over the full table does not overflow capacity.
    var buf: [filter_capacity]SockFilter = undefined;
    const prog = buildFilter(&buf, &allowed_syscalls);
    try testing.expectEqual(@as(usize, 6 + allowed_syscalls.len), prog.len);
}

test "allow jumps land on the ALLOW ret" {
    const sys = [_]linux.SYS{ .exit, .read, .write };
    var buf: [6 + sys.len]SockFilter = undefined;
    const prog = buildFilter(&buf, &sys);
    const n = sys.len;
    const allow_idx = 5 + n;
    // Simulate cBPF jump resolution: allow at 3+j with jt must land on
    // allow_idx for every j (pc' = pc + 1 + jt).
    for (sys, 0..) |_, j| {
        const target = 3 + j + 1 + @as(usize, prog[3 + j].jt);
        try testing.expectEqual(allow_idx, target);
    }
    // Arch mismatch at pc=1 with jf must land on the KILL ret at [4+n]:
    // pc' = 1 + 1 + jf = 4 + n → jf = n + 2.
    try testing.expectEqual(4 + n, 1 + 1 + @as(usize, prog[1].jf));
}

test "applySeccomp installs the real filter: allowed pass, denied EPERM" {
    // Regression: prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &fprog) —
    // the fprog pointer is arg3. On a kernel that supports seccomp
    // filter mode, fork a child, apply the REAL allowlist there, and
    // observe: getpid (allowlisted) succeeds, clock_settime (not in the
    // allowlist) fails with EPERM, and the child exits cleanly.
    // Runs in a forked child so the test process itself is never
    // filtered. Skip (not fail) when seccomp filter mode is unavailable.
    const pid_usize = linux.fork();
    if (linux.errno(pid_usize) != .SUCCESS) return error.ForkFailed;
    const pid: std.posix.pid_t = @bitCast(@as(u32, @truncate(pid_usize)));
    if (pid == 0) {
        // Child: NNP is required before filter attach.
        const nnp = linux.prctl(38, 1, 0, 0, 0); // PR_SET_NO_NEW_PRIVS
        if (linux.errno(nnp) != .SUCCESS) linux.exit(111);
        var buf: [filter_capacity]SockFilter = undefined;
        applySeccomp(&buf) catch |e| switch (e) {
            error.SeccompUnsupported => linux.exit(112), // skip
            else => linux.exit(113),
        };
        // Allowlisted: getpid.
        if (linux.getpid() <= 0) linux.exit(114);
        // Not allowlisted: must be denied with EPERM.
        const rc = linux.clock_settime(.REALTIME, undefined);
        if (linux.errno(rc) != .PERM) linux.exit(115);
        // Allowlisted: clean exit.
        linux.exit(0);
    }
    var status: i32 = 0;
    _ = linux.waitpid(pid, &status, 0);
    const st: u32 = @bitCast(status);
    if (linux.W.IFEXITED(st)) {
        const code = linux.W.EXITSTATUS(st);
        switch (code) {
            0 => {},
            112 => return error.SkipZigTest, // no seccomp filter support
            111 => return error.NnpFailed,
            113 => return error.SeccompFailed,
            114 => return error.GetPidFailed,
            115 => return error.DenyNotEperm,
            else => return error.UnexpectedExit,
        }
    } else {
        // Killed by a signal: filter KILL'd the child — bad.
        return error.ChildKilled;
    }
}

test "Landlock and seccomp enforce RO, RW and outside denial on the real kernel" {
    const io = testing.io;
    const gpa = testing.allocator;
    var tmp = testing.tmpDir(.{});
    defer tmp.cleanup();
    const cwd = try std.process.currentPathAlloc(io, gpa);
    defer gpa.free(cwd);
    const base = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}", .{ cwd, &tmp.sub_path });
    defer gpa.free(base);
    var paths: [3][:0]u8 = undefined;
    for ([_][]const u8{ "rw", "ro", "outside" }, 0..) |name, i| {
        paths[i] = try std.fmt.allocPrintSentinel(gpa, "{s}/{s}", .{ base, name }, 0);
        try tmp.dir.createDir(io, name, .default_dir);
    }
    defer for (paths) |p| gpa.free(p);
    var files: [3][:0]u8 = undefined;
    for (paths, 0..) |p, i| {
        files[i] = try std.fmt.allocPrintSentinel(gpa, "{s}/file", .{p}, 0);
        const f = try std.Io.Dir.cwd().createFile(io, files[i], .{});
        f.close(io);
    }
    defer for (files) |f| gpa.free(f);
    const pid_raw = linux.fork();
    if (linux.errno(pid_raw) != .SUCCESS) return error.ForkFailed;
    const pid: i32 = @intCast(pid_raw);
    if (pid == 0) {
        if (linux.errno(linux.prctl(38, 1, 0, 0, 0)) != .SUCCESS) linux.exit(111);
        const rules = [_]Rule{ .{ .path = paths[0], .access = read_write }, .{ .path = paths[1] } };
        var buf: [4096]u8 = undefined;
        applyLandlock(&rules, &buf) catch |e| switch (e) {
            error.LandlockUnsupported => linux.exit(112),
            else => linux.exit(113),
        };
        var filter: [filter_capacity]SockFilter = undefined;
        applySeccomp(&filter) catch linux.exit(114);
        const rw = linux.openat(linux.AT.FDCWD, files[0], .{ .ACCMODE = .RDWR }, 0);
        if (linux.errno(rw) != .SUCCESS) linux.exit(115);
        if (linux.errno(linux.write(@intCast(rw), "ok", 2)) != .SUCCESS) linux.exit(116);
        _ = linux.close(@intCast(rw));
        const ro = linux.openat(linux.AT.FDCWD, files[1], .{ .ACCMODE = .RDONLY }, 0);
        if (linux.errno(ro) != .SUCCESS) linux.exit(117);
        _ = linux.close(@intCast(ro));
        const write_ro = linux.openat(linux.AT.FDCWD, files[1], .{ .ACCMODE = .WRONLY }, 0);
        if (linux.errno(write_ro) != .ACCES) linux.exit(118);
        const outside = linux.openat(linux.AT.FDCWD, files[2], .{ .ACCMODE = .RDONLY }, 0);
        if (linux.errno(outside) != .ACCES) linux.exit(119);
        if (linux.errno(linux.clock_settime(.REALTIME, undefined)) != .PERM) linux.exit(120);
        linux.exit(0);
    }
    var status: u32 = 0;
    _ = linux.waitpid(pid, @ptrCast(&status), 0);
    try testing.expect(linux.W.IFEXITED(status));
    if (linux.W.EXITSTATUS(status) == 112) return error.SkipZigTest;
    try testing.expectEqual(@as(u8, 0), linux.W.EXITSTATUS(status));
}
