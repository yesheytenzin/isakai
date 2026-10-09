// SPDX-License-Identifier: GPL-3.0-or-later

//! Privileged helper: allocate/remove per-app system users + data dirs.
//! Runs as root via pkexec (or sudo -n), does one thing, exits. The CLI and
//! registry stay unprivileged. Port of the Rust uid_helper's allocate/remove
//! paths (launch path arrives with P1.8's spawnAsUser).

const std = @import("std");
const registry = @import("registry");

pub const HelperError = error{
    BadPackage,
    BadUid,
    BadDataDir,
    SymlinkEscape,
    OwnerMismatch,
    UseraddFailed,
    UserdelFailed,
    GroupaddFailed,
    GroupdelFailed,
    UidTaken,
    CredentialMismatch,
    NoPkexecOrSudo,
    HelperFailed,
    OutOfMemory,
};

/// Account name: isakai-<first 24 hex of sha256(package)>.
/// Hash avoids truncation collisions; no package text in the username.
pub fn userName(gpa: std.mem.Allocator, package: []const u8) ![]u8 {
    var digest: [32]u8 = undefined;
    std.crypto.hash.sha2.Sha256.hash(package, &digest, .{});
    const hex = std.fmt.bytesToHex(digest[0..12], .lower);
    return std.fmt.allocPrint(gpa, "isakai-{s}", .{hex});
}

/// Shared strict package-name rules (mirrors install.validPackage; kept
/// local so the privileged helper has no heavy imports). Each dot-separated
/// segment must start with an ASCII letter; the rest is alphanumeric or
/// underscore. Requires at least one dot, max 200 bytes.
pub fn validPackage(package: []const u8) bool {
    if (package.len == 0 or package.len > 200) return false;
    if (std.mem.indexOfScalar(u8, package, '.') == null) return false;
    var parts = std.mem.splitScalar(u8, package, '.');
    while (parts.next()) |part| {
        if (part.len == 0) return false;
        if (!std.ascii.isAlphabetic(part[0])) return false;
        for (part[1..]) |c| {
            if (!std.ascii.isAlphanumeric(c) and c != '_') return false;
        }
    }
    return true;
}

/// Copy a path into a NUL-terminated stack buffer. Null when it won't fit.
fn toZ(path: []const u8, buf: []u8) ?[*:0]const u8 {
    if (path.len + 1 > buf.len) return null;
    @memcpy(buf[0..path.len], path);
    buf[path.len] = 0;
    return @ptrCast(&buf[0]);
}

/// Open a directory without following a trailing symlink. Null on failure.
fn openDirNoFollow(dirfd: std.os.linux.fd_t, name_z: [*:0]const u8) ?std.os.linux.fd_t {
    const linux = std.os.linux;
    const rc = linux.openat(dirfd, name_z, .{ .DIRECTORY = true, .NOFOLLOW = true, .CLOEXEC = true }, 0);
    if (linux.errno(rc) != .SUCCESS) return null;
    return @bitCast(@as(u32, @truncate(rc)));
}

/// Data dir must be exactly work/data/<package>: absolute, no `..`, no
/// symlink components (NOFOLLOW statx per level; only the leaf may be
/// missing, which allocate() creates via mkdirat below).
fn validateDataDir(io: std.Io, gpa: std.mem.Allocator, work: []const u8, package: []const u8, data_dir: []const u8) HelperError!void {
    _ = io;
    const linux = std.os.linux;
    if (!validPackage(package)) return error.BadPackage;
    if (data_dir.len == 0 or data_dir[0] != '/') return error.BadDataDir;
    if (work.len == 0 or work[0] != '/') return error.BadDataDir;
    const expect = registry.expectDataDir(gpa, work, package) catch return error.OutOfMemory;
    defer gpa.free(expect);
    if (!std.mem.eql(u8, data_dir, expect)) return error.BadDataDir;
    if (std.mem.indexOf(u8, data_dir, "..") != null) return error.BadDataDir;
    var zbuf: [4096]u8 = undefined;
    var i: usize = 1;
    while (i <= data_dir.len) : (i += 1) {
        if (i == data_dir.len or data_dir[i] == '/') {
            const prefix = data_dir[0..i];
            const z = toZ(prefix, &zbuf) orelse return error.BadDataDir;
            var stx: linux.Statx = undefined;
            const rc = linux.statx(linux.AT.FDCWD, z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true }, &stx);
            const err = linux.errno(rc);
            if (err != .SUCCESS) {
                // Only the leaf may be missing (allocate creates it).
                if (err == .NOENT and i == data_dir.len) break else return error.BadDataDir;
            } else {
                if (stx.mode & linux.S.IFMT == linux.S.IFLNK) return error.SymlinkEscape;
                if (i != data_dir.len and stx.mode & linux.S.IFMT != linux.S.IFDIR) return error.BadDataDir;
            }
        }
    }
}

/// Line iterator over a small colon-separated system file (/etc/passwd,
/// /etc/group). Returns null when unreadable; both callers treat that as
/// "not found" (passwdLookup) / "absent" (groupExists).
fn readLines(gpa: std.mem.Allocator, io: std.Io, path: []const u8) ?[]u8 {
    return std.Io.Dir.cwd().readFileAlloc(io, path, gpa, .limited(4 * 1024 * 1024)) catch null;
}

/// Parse a decimal id field, null on garbage (callers skip the line).
fn parseU32(s: []const u8) ?u32 {
    return std.fmt.parseInt(u32, s, 10) catch null;
}

const PasswdEntry = struct {
    name: []const u8,
    uid: u32,
    gid: u32,
};

/// Parse /etc/passwd (NSS-free; our range is file-backed by useradd).
fn passwdLookup(gpa: std.mem.Allocator, io: std.Io, want_name: ?[]const u8, want_uid: ?u32) !?PasswdEntry {
    const bytes = readLines(gpa, io, "/etc/passwd") orelse return null;
    defer gpa.free(bytes);
    var lines = std.mem.splitScalar(u8, bytes, '\n');
    while (lines.next()) |line| {
        if (line.len == 0 or line[0] == '#') continue;
        var f = std.mem.splitScalar(u8, line, ':');
        const name = f.next() orelse continue;
        _ = f.next(); // passwd
        const uid_s = f.next() orelse continue;
        const gid_s = f.next() orelse continue;
        const uid = parseU32(uid_s) orelse continue;
        const gid = parseU32(gid_s) orelse continue;
        if (want_name) |n| {
            if (std.mem.eql(u8, name, n)) {
                return .{ .name = try gpa.dupe(u8, name), .uid = uid, .gid = gid };
            }
        } else if (want_uid) |u| {
            if (uid == u) {
                return .{ .name = try gpa.dupe(u8, name), .uid = uid, .gid = gid };
            }
        }
    }
    return null;
}

fn groupExists(gpa: std.mem.Allocator, io: std.Io, name: []const u8, gid: u32) !bool {
    const bytes = readLines(gpa, io, "/etc/group") orelse return false;
    defer gpa.free(bytes);
    var lines = std.mem.splitScalar(u8, bytes, '\n');
    while (lines.next()) |line| {
        if (line.len == 0 or line[0] == '#') continue;
        var f = std.mem.splitScalar(u8, line, ':');
        const n = f.next() orelse continue;
        _ = f.next();
        const g = f.next() orelse continue;
        if (std.mem.eql(u8, n, name)) {
            const got = parseU32(g) orelse return false;
            return got == gid;
        }
    }
    return false;
}

fn runCmd(io: std.Io, argv: []const []const u8) !void {
    var child = try std.process.spawn(io, .{
        .argv = argv,
        .stdin = .ignore,
        .stdout = .ignore,
        .stderr = .ignore,
    });
    registry.waitOk(&child, io) catch return error.HelperFailed;
}

fn ensureUser(gpa: std.mem.Allocator, io: std.Io, user: []const u8, uid: u32) HelperError!void {
    if (try passwdLookup(gpa, io, user, null)) |existing| {
        defer gpa.free(existing.name);
        if (existing.uid != uid or existing.gid != uid) return error.CredentialMismatch;
        return;
    }
    if (try passwdLookup(gpa, io, null, uid)) |other| {
        defer gpa.free(other.name);
        return error.UidTaken;
    }
    if (!(try groupExists(gpa, io, user, uid))) {
        const gid_s = std.fmt.allocPrint(gpa, "{d}", .{uid}) catch return error.OutOfMemory;
        defer gpa.free(gid_s);
        runCmd(io, &.{ "/usr/bin/groupadd", "--system", "--gid", gid_s, user }) catch return error.GroupaddFailed;
    }
    const uid_s = std.fmt.allocPrint(gpa, "{d}", .{uid}) catch return error.OutOfMemory;
    defer gpa.free(uid_s);
    runCmd(io, &.{
        "/usr/bin/useradd", "--system", "--no-create-home", "--gid", uid_s,
        "--shell", "/usr/bin/nologin", "--uid", uid_s, user,
    }) catch return error.UseraddFailed;
    const created = (try passwdLookup(gpa, io, user, null)) orelse return error.UseraddFailed;
    defer gpa.free(created.name);
    if (created.uid != uid or created.gid != uid) return error.CredentialMismatch;
}

/// Create data dir (0700, uid:uid) anchored on dirfds: opens the parent
/// NOFOLLOW, then delegates the leaf mkdir/chown/chmod to mkdirOwnedAt.
/// Ownership is never set by path, so a swapped symlink cannot redirect it.
fn ensureDataDir(io: std.Io, data_dir: []const u8, uid: u32) HelperError!void {
    _ = io;
    const linux = std.os.linux;
    const parent = std.fs.path.dirname(data_dir) orelse return error.BadDataDir;
    const leaf = std.fs.path.basename(data_dir);
    if (leaf.len == 0 or leaf.len >= 256) return error.BadDataDir;
    var pbuf: [4096]u8 = undefined;
    const parent_z = toZ(parent, &pbuf) orelse return error.BadDataDir;
    const parent_fd = openDirNoFollow(linux.AT.FDCWD, parent_z) orelse return error.BadDataDir;
    defer _ = linux.close(parent_fd);
    try mkdirOwnedAt(parent_fd, leaf, 0o700, uid);
}

/// mkdirat + fchown + fchmod for one path component under a dirfd.
/// Verifies the result is a directory via the opened fd.
fn mkdirOwnedAt(dirfd: std.os.linux.fd_t, name: []const u8, mode: u32, uid: u32) HelperError!void {
    const linux = std.os.linux;
    var nbuf: [256]u8 = undefined;
    const name_z = toZ(name, &nbuf) orelse return error.BadDataDir;
    {
        const rc = linux.mkdirat(dirfd, name_z, mode);
        const err = linux.errno(rc);
        if (err != .SUCCESS and err != .EXIST) return error.BadDataDir;
    }
    const fd = openDirNoFollow(dirfd, name_z) orelse return error.BadDataDir;
    defer _ = linux.close(fd);
    if (linux.errno(linux.fchown(fd, uid, uid)) != .SUCCESS) return error.BadDataDir;
    if (linux.errno(linux.fchmod(fd, mode)) != .SUCCESS) return error.BadDataDir;
}

pub const AllocateArgs = struct {
    package: []const u8,
    uid: u32,
    data_dir: []const u8,
    work: []const u8,
};

pub fn allocate(gpa: std.mem.Allocator, io: std.Io, args: AllocateArgs) HelperError!void {
    if (args.uid < 200000 or args.uid > 299999) return error.BadUid;
    try validateDataDir(io, gpa, args.work, args.package, args.data_dir);
    const user = try userName(gpa, args.package);
    defer gpa.free(user);
    try ensureUser(gpa, io, user, args.uid);
    try ensureDataDir(io, args.data_dir, args.uid);
    // Per-app runtime subdirs the dropped JVM expects (0700, app-owned).
    // Anchored on the leaf fd; every subdir gets chmod 0700 after chown
    // so a pre-existing world-readable dir cannot survive a reinstall.
    {
        const linux = std.os.linux;
        var dbuf: [4096]u8 = undefined;
        const data_z = toZ(args.data_dir, &dbuf) orelse return error.BadDataDir;
        const data_fd = openDirNoFollow(linux.AT.FDCWD, data_z) orelse return error.BadDataDir;
        defer _ = linux.close(data_fd);
        try mkdirOwnedAt(data_fd, "cache", 0o700, args.uid);
        const cache_z: [*:0]const u8 = @ptrCast("cache\x00");
        const cache_fd = openDirNoFollow(data_fd, cache_z) orelse return error.BadDataDir;
        defer _ = linux.close(cache_fd);
        try mkdirOwnedAt(cache_fd, "tmp", 0o700, args.uid);
        try mkdirOwnedAt(data_fd, "run", 0o700, args.uid);
    }
}

pub fn remove(gpa: std.mem.Allocator, io: std.Io, args: AllocateArgs) HelperError!void {
    if (args.uid < 200000 or args.uid > 299999) return error.BadUid;
    try validateDataDir(io, gpa, args.work, args.package, args.data_dir);
    // Refuse unless the leaf exists, is a real directory (not a symlink),
    // and is owned by the allocation UID. NOFOLLOW throughout.
    {
        const linux = std.os.linux;
        var dbuf: [4096]u8 = undefined;
        const data_z = toZ(args.data_dir, &dbuf) orelse return error.BadDataDir;
        var stx: linux.Statx = undefined;
        const rc = linux.statx(linux.AT.FDCWD, data_z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true, .UID = true }, &stx);
        if (linux.errno(rc) == .SUCCESS) {
            if (stx.mode & linux.S.IFMT == linux.S.IFLNK) return error.SymlinkEscape;
            if (stx.mode & linux.S.IFMT != linux.S.IFDIR) return error.BadDataDir;
            if (stx.uid != args.uid) return error.OwnerMismatch;
            try unlinkTreeAnchored(args.data_dir);
        } else if (linux.errno(rc) != .NOENT) {
            return error.BadDataDir;
        }
    }
    const user = try userName(gpa, args.package);
    defer gpa.free(user);
    if (try passwdLookup(gpa, io, user, null)) |existing| {
        gpa.free(existing.name);
        runCmd(io, &.{ "/usr/bin/userdel", user }) catch return error.UserdelFailed;
    }
    // groupdel best-effort when the group matches our gid.
    if (try groupExists(gpa, io, user, args.uid)) {
        runCmd(io, &.{ "/usr/bin/groupdel", user }) catch return error.GroupdelFailed;
    }
}

/// Recursively delete a directory tree using only dirfd-anchored syscalls:
/// openat(NOFOLLOW|DIRECTORY) -> getdents64 -> unlinkat per entry (never
/// by-path). Symlinks are unlinked, never followed.
fn unlinkTreeAnchored(root_path: []const u8) HelperError!void {
    const linux = std.os.linux;
    var rbuf: [4096]u8 = undefined;
    const root_z = toZ(root_path, &rbuf) orelse return error.BadDataDir;
    const root_fd = openDirNoFollow(linux.AT.FDCWD, root_z) orelse return error.BadDataDir;
    defer _ = linux.close(root_fd);
    unlinkTreeFd(root_fd) catch return error.BadDataDir;
    // Root is now empty: remove the leaf itself via its parent.
    const parent = std.fs.path.dirname(root_path) orelse return error.BadDataDir;
    const leaf = std.fs.path.basename(root_path);
    var pbuf: [4096]u8 = undefined;
    var lbuf: [256]u8 = undefined;
    const parent_z = toZ(parent, &pbuf) orelse return error.BadDataDir;
    const leaf_z = toZ(leaf, &lbuf) orelse return error.BadDataDir;
    const parent_fd = openDirNoFollow(linux.AT.FDCWD, parent_z) orelse return error.BadDataDir;
    defer _ = linux.close(parent_fd);
    if (linux.errno(linux.unlinkat(parent_fd, leaf_z, linux.AT.REMOVEDIR)) != .SUCCESS) return error.BadDataDir;
}

fn unlinkTreeFd(dirfd: std.os.linux.fd_t) !void {
    const linux = std.os.linux;
    var buf: [8192]u8 align(@alignOf(linux.dirent64)) = undefined;
    while (true) {
        const n = linux.getdents64(dirfd, &buf, buf.len);
        const err = linux.errno(n);
        if (err != .SUCCESS) return error.UnlinkFailed;
        if (n == 0) return;
        var off: usize = 0;
        while (off < n) {
            const d: *linux.dirent64 = @ptrCast(@alignCast(&buf[off]));
            const reclen: usize = d.reclen;
            if (reclen == 0) return error.UnlinkFailed;
            const name_ptr: [*:0]u8 = @ptrCast(&d.name);
            const name = std.mem.span(name_ptr);
            if (!std.mem.eql(u8, name, ".") and !std.mem.eql(u8, name, "..")) {
                var nbuf: [512]u8 = undefined;
                if (name.len + 1 > nbuf.len) return error.UnlinkFailed;
                @memcpy(nbuf[0..name.len], name);
                nbuf[name.len] = 0;
                const name_z: [*:0]const u8 = @ptrCast(&nbuf);
                if (d.type == linux.DT.DIR) {
                    const sub = openDirNoFollow(dirfd, name_z) orelse return error.UnlinkFailed;
                    unlinkTreeFd(sub) catch {
                        _ = linux.close(sub);
                        return error.UnlinkFailed;
                    };
                    _ = linux.close(sub);
                    if (linux.errno(linux.unlinkat(dirfd, name_z, linux.AT.REMOVEDIR)) != .SUCCESS) {
                        return error.UnlinkFailed;
                    }
                } else {
                    // Files, symlinks, and anything else: unlink, never follow.
                    if (linux.errno(linux.unlinkat(dirfd, name_z, 0)) != .SUCCESS) {
                        return error.UnlinkFailed;
                    }
                }
            }
            off += reclen;
        }
    }
}

fn ownerMatches(path: []const u8, uid: u32) bool {
    var buf: [4096]u8 = undefined;
    const cpath = toZ(path, &buf) orelse return false;
    var stx: std.os.linux.Statx = undefined;
    const rc = std.os.linux.statx(std.os.linux.AT.FDCWD, cpath, std.os.linux.AT.SYMLINK_NOFOLLOW, .{ .UID = true }, &stx);
    if (std.os.linux.errno(rc) != .SUCCESS) return false;
    return stx.uid == uid;
}

/// Unprivileged side: elevate `isakai uid-helper ...` via pkexec, else sudo -n.
pub fn invoke(
    io: std.Io,
    gpa: std.mem.Allocator,
    exe: []const u8,
    subcommand: []const u8,
    package: []const u8,
    uid: u32,
    data_dir: []const u8,
    work: []const u8,
    noninteractive: bool,
) HelperError!void {
    const uid_s = std.fmt.allocPrint(gpa, "{d}", .{uid}) catch return error.OutOfMemory;
    defer gpa.free(uid_s);
    const helper_argv: []const []const u8 = &.{
        exe, "uid-helper", subcommand, package, uid_s, data_dir, work,
    };
    if (!noninteractive) {
        // Prefer pkexec (desktop dialog); fall back to sudo -n on failure?
        // No fallback: pkexec missing means try sudo -n directly.
        if (hasExe(io, "/usr/bin/pkexec")) {
            var argv: std.ArrayListUnmanaged([]const u8) = .empty;
            defer argv.deinit(gpa);
            argv.append(gpa, "/usr/bin/pkexec") catch return error.OutOfMemory;
            argv.appendSlice(gpa, helper_argv) catch return error.OutOfMemory;
            runCmd(io, argv.items) catch return error.HelperFailed;
            return;
        }
    }
    if (hasExe(io, "/usr/bin/sudo")) {
        var argv: std.ArrayListUnmanaged([]const u8) = .empty;
        defer argv.deinit(gpa);
        argv.append(gpa, "/usr/bin/sudo") catch return error.OutOfMemory;
        argv.append(gpa, "-n") catch return error.OutOfMemory;
        argv.appendSlice(gpa, helper_argv) catch return error.OutOfMemory;
        runCmd(io, argv.items) catch return error.HelperFailed;
        return;
    }
    return error.NoPkexecOrSudo;
}

fn hasExe(io: std.Io, path: []const u8) bool {
    std.Io.Dir.cwd().access(io, path, .{ .execute = true }) catch return false;
    return true;
}

/// Fork, drop to the app user, set the per-app environment, exec the
/// command. Parent waits and returns the child's exit code (or 128+signal).
/// Must be root. Drop order: setgroups(0) → setresgid → setresuid, every
/// return checked, _exit(127) on failure. Never returns the child.
pub fn runAs(
    package: []const u8,
    uid: u32,
    data_dir: []const u8,
    work: []const u8,
    cmd: []const [:0]const u8,
    err_writer: *std.Io.Writer,
) !u8 {
    const linux = std.os.linux;
    if (uid < 200000 or uid > 299999) {
        try err_writer.writeAll("uid-helper run: uid out of range\n");
        return 2;
    }
    if (!validPackage(package)) {
        try err_writer.writeAll("uid-helper run: bad package\n");
        return 2;
    }
    if (cmd.len == 0) {
        try err_writer.writeAll("uid-helper run: no command\n");
        return 2;
    }
    // argv[0] must be absolute (no PATH search: PATH is attacker-influenced
    // when invoked oddly) and name the java launcher.
    if (!validJavaCmd(cmd[0])) {
        try err_writer.writeAll("uid-helper run: command not allowlisted (absolute path to java required)\n");
        return 2;
    }
    // The launcher always execs the IsakaiMain entry point; require its
    // marker so the helper cannot be repurposed as a generic root exec.
    if (!hasIsakaiMainMarker(cmd)) {
        try err_writer.writeAll("uid-helper run: missing org.isakai.runtime.IsakaiMain marker\n");
        return 2;
    }
    // data_dir must be exactly work/data/<package> (anchor check without Io;
    // same string as registry.expectDataDir, inlined here because runAs is allocation-free pre-fork).
    {
        var expect_buf: [4096]u8 = undefined;
        const expect = std.fmt.bufPrint(&expect_buf, "{s}/data/{s}", .{ work, package }) catch {
            try err_writer.writeAll("uid-helper run: path too long\n");
            return 2;
        };
        if (!std.mem.eql(u8, data_dir, expect)) {
            try err_writer.writeAll("uid-helper run: data dir not anchored\n");
            return 2;
        }
    }
    // argv[0] must additionally be world-executable: after the UID drop the
    // child can only traverse o+x components. Checked pre-fork (TOCTOU is
    // acceptable: worst case is an exec failure in the child).
    if (!worldExecutable(cmd[0])) {
        try err_writer.writeAll("uid-helper run: java binary not world-executable\n");
        return 2;
    }
    const pid_usize = linux.fork();
    const errno_fork = linux.errno(pid_usize);
    if (errno_fork != .SUCCESS) {
        try err_writer.print("uid-helper run: fork failed: {t}\n", .{errno_fork});
        return 1;
    }
    const pid: std.posix.pid_t = @bitCast(@as(u32, @truncate(pid_usize)));
    if (pid == 0) {
        // Child: drop privileges, set env, exec. Async-signal-safe only;
        // on any failure _exit(127) — never return to the caller.
        childMain(uid, data_dir, cmd);
    }
    // Parent: wait and translate status.
    while (true) {
        var status: i32 = 0;
        const w = linux.waitpid(pid, &status, 0);
        if (linux.errno(w) != .SUCCESS) {
            // EINTR: retry; anything else is fatal.
            if (linux.errno(w) == .INTR) continue;
            try err_writer.writeAll("uid-helper run: waitpid failed\n");
            return 1;
        }
        const st: u32 = @bitCast(status);
        if (linux.W.IFEXITED(st)) return linux.W.EXITSTATUS(st);
        if (linux.W.IFSIGNALED(st)) return @intCast(128 + @intFromEnum(linux.W.TERMSIG(st)));
        // Stopped/continued: keep waiting.
    }
}

/// argv[0] allowlist: absolute path whose basename is `java`.
pub fn validJavaCmd(exe: [:0]const u8) bool {
    const s: []const u8 = exe[0..exe.len :0];
    if (s.len == 0 or s[0] != '/') return false;
    if (std.mem.indexOf(u8, s, "..") != null) return false;
    return std.mem.eql(u8, std.fs.path.basename(s), "java");
}

/// The launcher always execs `org.isakai.runtime.IsakaiMain`; require the
/// marker anywhere in argv so the helper cannot be repurposed as root exec.
pub fn hasIsakaiMainMarker(cmd: []const [:0]const u8) bool {
    for (cmd) |a| {
        const s: []const u8 = a[0..a.len :0];
        if (std.mem.eql(u8, s, "org.isakai.runtime.IsakaiMain")) return true;
    }
    return false;
}

/// True when every component of an absolute path grants o+x (dirs) and the
/// file itself grants o+r+x, so the dropped child can exec it. statx with
/// SYMLINK_NOFOLLOW on the final component: a symlink java is rejected
/// (use the real path instead).
fn worldExecutable(path: [:0]const u8) bool {
    const linux = std.os.linux;
    const s: []const u8 = path[0..path.len :0];
    if (s.len == 0 or s[0] != '/') return false;
    var zbuf: [4096]u8 = undefined;
    var i: usize = 1;
    while (i <= s.len) : (i += 1) {
        if (i == s.len or s[i] == '/') {
            const prefix = s[0..i];
            const z = toZ(prefix, &zbuf) orelse return false;
            var stx: linux.Statx = undefined;
            const rc = linux.statx(linux.AT.FDCWD, z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true, .MODE = true }, &stx);
            if (linux.errno(rc) != .SUCCESS) return false;
            if (stx.mode & linux.S.IFMT == linux.S.IFLNK) return false;
            const is_last = i == s.len;
            const is_dir = stx.mode & linux.S.IFMT == linux.S.IFDIR;
            const need: u16 = if (!is_last or is_dir) linux.S.IXOTH else linux.S.IXOTH | linux.S.IROTH;
            if (stx.mode & need != need) return false;
        }
    }
    return true;
}

fn childMain(uid: u32, data_dir: []const u8, cmd: []const [:0]const u8) noreturn {
    const linux = std.os.linux;
    // 0. Close every fd >= 3 (no descriptor leaks into the app). Prefer
    // close_range(3, ~0, 0); fall back to a bounded loop when ENOSYS.
    {
        const rc = linux.close_range(3, std.math.maxInt(linux.fd_t), .{ ._0 = 0, .UNSHARE = false, .CLOEXEC = false, ._ = 0 });
        if (linux.errno(rc) != .SUCCESS and linux.errno(rc) != .INVAL) {
            var fd: i32 = 3;
            while (fd < 1024) : (fd += 1) _ = linux.close(fd);
        }
    }
    // 1. Supplementary groups first (else they leak across exec).
    if (linux.errno(linux.setgroups(0, &[_]linux.gid_t{})) != .SUCCESS) linux.exit(127);
    // 2. GID (before UID: dropping UID loses CAP_SETGID).
    if (linux.errno(linux.setresgid(uid, uid, uid)) != .SUCCESS) linux.exit(127);
    // 3. UID (all three; root unrecoverable after).
    if (linux.errno(linux.setresuid(uid, uid, uid)) != .SUCCESS) linux.exit(127);
    // 4. chdir into the app data dir: relative paths (core dumps, JVM
    // outputs) land inside the sandbox. _exit(127) on failure.
    {
        var dbuf: [4096]u8 = undefined;
        if (data_dir.len + 1 > dbuf.len) linux.exit(127);
        @memcpy(dbuf[0..data_dir.len], data_dir);
        dbuf[data_dir.len] = 0;
        const z: [*:0]const u8 = @ptrCast(&dbuf);
        if (linux.errno(linux.chdir(z)) != .SUCCESS) linux.exit(127);
    }
    // 5. Tight umask: app-created files default 0600 / dirs 0700.
    _ = linux.syscall1(.umask, 0o077);
    // 6. Per-app environment. Fixed-size stack buffers (no allocation
    // post-fork). Minimal set: TMPDIR, XDG_RUNTIME_DIR, HOME, PATH.
    // java.io.tmpdir is passed as a JVM flag by the launcher, not here.
    var tmpdir_buf: [4352]u8 = undefined;
    var rundir_buf: [4352]u8 = undefined;
    var home_buf: [4352]u8 = undefined;
    const tmpdir = std.fmt.bufPrint(&tmpdir_buf, "TMPDIR={s}/cache/tmp\x00", .{data_dir}) catch linux.exit(127);
    const rundir = std.fmt.bufPrint(&rundir_buf, "XDG_RUNTIME_DIR={s}/run\x00", .{data_dir}) catch linux.exit(127);
    const home = std.fmt.bufPrint(&home_buf, "HOME={s}\x00", .{data_dir}) catch linux.exit(127);
    _ = tmpdir;
    _ = rundir;
    _ = home;
    const envp: [*:null]const ?[*:0]const u8 = @ptrCast(&[_]?[*:0]const u8{
        @ptrCast(&tmpdir_buf),
        @ptrCast(&rundir_buf),
        @ptrCast(&home_buf),
        @ptrCast("PATH=/usr/bin:/bin\x00"),
        null,
    });
    // argv (already NUL-terminated slices).
    var argv_buf: [64]?[*:0]const u8 = @splat(null);
    if (cmd.len >= argv_buf.len) linux.exit(127);
    for (cmd, 0..) |a, i| argv_buf[i] = @ptrCast(a.ptr);
    argv_buf[cmd.len] = null;
    const rc = linux.execve(argv_buf[0].?, @ptrCast(&argv_buf), envp);
    // execve returned: report errno + path to stderr (async-signal-safe).
    const err = linux.errno(rc);
    const prefix = "uid-helper run: exec failed: ";
    _ = linux.write(linux.STDERR_FILENO, prefix, prefix.len);
    var ebuf: [32]u8 = undefined;
    const ename = @tagName(err);
    const elen = @min(ename.len, ebuf.len);
    @memcpy(ebuf[0..elen], ename[0..elen]);
    _ = linux.write(linux.STDERR_FILENO, &ebuf, elen);
    _ = linux.write(linux.STDERR_FILENO, " ", 1);
    const exe0 = cmd[0];
    _ = linux.write(linux.STDERR_FILENO, exe0.ptr, exe0.len);
    _ = linux.write(linux.STDERR_FILENO, "\n", 1);
    linux.exit(127);
}

test "userName is stable and hashed" {
    const gpa = std.testing.allocator;
    const a = try userName(gpa, "org.isakai.fixture");
    defer gpa.free(a);
    const b = try userName(gpa, "org.isakai.fixture");
    defer gpa.free(b);
    try std.testing.expectEqualStrings(a, b);
    try std.testing.expect(std.mem.startsWith(u8, a, "isakai-"));
    try std.testing.expectEqual(@as(usize, 7 + 24), a.len);
    const c = try userName(gpa, "org.isakai.other");
    defer gpa.free(c);
    try std.testing.expect(!std.mem.eql(u8, a, c));
}

test "validPackage strict rules mirror install" {
    try std.testing.expect(validPackage("org.isakai.fixture"));
    try std.testing.expect(validPackage("com.example.a1_B2"));
    try std.testing.expect(!validPackage(""));
    try std.testing.expect(!validPackage("no-dot"));
    try std.testing.expect(!validPackage("../evil"));
    try std.testing.expect(!validPackage("org/isakai"));
    try std.testing.expect(!validPackage("org isakai"));
    try std.testing.expect(!validPackage("org.1bad.start"));
    try std.testing.expect(!validPackage("org..empty"));
    try std.testing.expect(!validPackage(".leading.dot"));
    try std.testing.expect(!validPackage("org.isakai.bad-char!"));
}

test "validJavaCmd requires absolute path to java" {
    try std.testing.expect(validJavaCmd("/usr/lib/jvm/java-17/bin/java"));
    try std.testing.expect(validJavaCmd("/usr/bin/java"));
    try std.testing.expect(!validJavaCmd("java"));
    try std.testing.expect(!validJavaCmd("./java"));
    try std.testing.expect(!validJavaCmd("/usr/bin/javac"));
    try std.testing.expect(!validJavaCmd("/usr/bin/java-evil"));
    try std.testing.expect(!validJavaCmd("/tmp/../usr/bin/java"));
    try std.testing.expect(!validJavaCmd(""));
}

test "hasIsakaiMainMarker requires the entry point" {
    const good: []const [:0]const u8 = &.{ "/usr/bin/java", "-cp", "x", "org.isakai.runtime.IsakaiMain" };
    try std.testing.expect(hasIsakaiMainMarker(good));
    const bad: []const [:0]const u8 = &.{ "/usr/bin/java", "-cp", "x", "org.evil.Main" };
    try std.testing.expect(!hasIsakaiMainMarker(bad));
    const empty: []const [:0]const u8 = &.{};
    try std.testing.expect(!hasIsakaiMainMarker(empty));
}
