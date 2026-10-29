// SPDX-License-Identifier: GPL-3.0-or-later

//! Privileged helper: allocate/remove per-app system users + data dirs.
//! Runs as root via pkexec (or sudo -n), does one thing, exits. The CLI and
//! registry stay unprivileged. Port of the Rust uid_helper's allocate/remove
//! paths (launch path arrives with P1.8's spawnAsUser).

const std = @import("std");
const registry = @import("registry");
const pathmod = @import("bridge_path");

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
        "/usr/bin/useradd", "--system",         "--no-create-home", "--gid", uid_s,
        "--shell",          "/usr/bin/nologin", "--uid",            uid_s,   user,
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
    // Stage dir: work/stage/<pkg>, 0700 then chown to the app uid via
    // dirfd (a swapped symlink cannot redirect ownership). The app
    // writes transfer files here directly. Best-effort parents.
    // NOTE: work/run/<pkg> is deliberately NOT chowned: the bridge
    // daemon (launcher user) must bind its socket there; the socket
    // itself is fchown'd to the app uid at bind time instead.
    ensureOneBridgeDir(args.work, args.package, "stage", args.uid);
}

/// Path must be exactly work/run/<package> or work/stage/<package>:
/// absolute, anchored, no `..`. Returns the owned path or null.
fn expectBridgeDir(gpa: std.mem.Allocator, work: []const u8, package: []const u8, kind: []const u8) ?[]u8 {
    if (!validPackage(package)) return null;
    if (work.len == 0 or work[0] != '/') return null;
    if (std.mem.indexOf(u8, package, "..") != null) return null;
    if (std.mem.indexOf(u8, work, "..") != null) return null;
    return std.fmt.allocPrint(gpa, "{s}/{s}/{s}", .{ work, kind, package }) catch null;
}

fn ensureOneBridgeDir(work: []const u8, package: []const u8, kind: []const u8, uid: u32) void {
    const linux = std.os.linux;
    var arena: [8192]u8 = undefined;
    var fba: std.heap.FixedBufferAllocator = .init(&arena);
    const gpa = fba.allocator();
    const dir = expectBridgeDir(gpa, work, package, kind) orelse return;
    // Mkdir parents by path (work/run, work/stage): root-owned 0755 is
    // fine for parents; the leaf gets 0700 + chown below.
    var pbuf: [4096]u8 = undefined;
    const parent = std.fmt.bufPrint(&pbuf, "{s}/{s}", .{ work, kind }) catch return;
    var pz: [4096]u8 = undefined;
    if (parent.len + 1 > pz.len) return;
    @memcpy(pz[0..parent.len], parent);
    pz[parent.len] = 0;
    _ = linux.mkdirat(linux.AT.FDCWD, @as([*:0]const u8, @ptrCast(&pz)), 0o755);
    var dz: [4096]u8 = undefined;
    if (dir.len + 1 > dz.len) return;
    @memcpy(dz[0..dir.len], dir);
    dz[dir.len] = 0;
    const dir_z: [*:0]const u8 = @ptrCast(&dz);
    _ = linux.mkdirat(linux.AT.FDCWD, dir_z, 0o700);
    // chown + chmod by fd (NOFOLLOW open): symlink-safe.
    const fd = openDirNoFollow(linux.AT.FDCWD, dir_z) orelse return;
    defer _ = linux.close(fd);
    _ = linux.fchown(fd, uid, uid);
    _ = linux.fchmod(fd, 0o700);
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
            // M6f: prefer the per-operation wrapper (matches the split
            // policy actions' exec.path → per-operation dialog text).
            const op_wrapper = if (std.mem.eql(u8, subcommand, "allocate"))
                "/usr/bin/isakai-allocate-uid"
            else
                "/usr/bin/isakai-remove-uid";
            if (hasExe(io, op_wrapper)) {
                argv.append(gpa, op_wrapper) catch return error.OutOfMemory;
                // Wrapper re-inserts the subcommand; skip it here.
                argv.append(gpa, package) catch return error.OutOfMemory;
                argv.append(gpa, uid_s) catch return error.OutOfMemory;
                argv.append(gpa, data_dir) catch return error.OutOfMemory;
                argv.append(gpa, work) catch return error.OutOfMemory;
                runCmd(io, argv.items) catch return error.HelperFailed;
                return;
            }
            argv.append(gpa, helper_argv[0]) catch return error.OutOfMemory;
            argv.append(gpa, helper_argv[1]) catch return error.OutOfMemory;
            argv.appendSlice(gpa, helper_argv[2..]) catch return error.OutOfMemory;
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

pub const JvmRequest = struct {
    package: []const u8,
    uid: u32,
    data_dir: []const u8,
    work: []const u8,
    code_dir: []const u8,
    java: [:0]const u8,
    framework: []const u8,
    windowed: bool,
    app_class: [:0]const u8,
    activity_class: [:0]const u8,
    title: [:0]const u8,
    app_theme: [:0]const u8,
    activity_theme: [:0]const u8,
    target_sdk: [:0]const u8,
    min_sdk: [:0]const u8,
    intent_data: [:0]const u8,
    wayland_sock: ?[]const u8,
    bridge_dir: ?[]const u8,
};

pub fn runJvm(gpa: std.mem.Allocator, request: JvmRequest, confinement_disabled: bool, err_writer: *std.Io.Writer) !u8 {
    for ([_][]const u8{ request.app_class, request.activity_class, request.title }) |value| {
        if (value.len == 0 or value.len > 4096) {
            try err_writer.writeAll("uid-helper run-jvm: invalid text field\n");
            return 2;
        }
    }
    for ([_][]const u8{ request.app_theme, request.activity_theme, request.target_sdk, request.min_sdk }) |value| {
        _ = std.fmt.parseInt(u32, value, 10) catch {
            try err_writer.writeAll("uid-helper run-jvm: invalid numeric field\n");
            return 2;
        };
    }
    if (request.intent_data.len > 4096 or std.mem.indexOfScalar(u8, request.intent_data, 0) != null) {
        try err_writer.writeAll("uid-helper run-jvm: invalid intent data\n");
        return 2;
    }
    if (!std.mem.startsWith(u8, request.java, "/usr/lib/jvm/")) {
        try err_writer.writeAll("uid-helper run-jvm: java is outside the trusted runtime roots\n");
        return 2;
    }
    if (!validWaylandSockPath(request.framework) or std.mem.eql(u8, request.framework, "/")) {
        try err_writer.writeAll("uid-helper run-jvm: bad framework path\n");
        return 2;
    }
    if (!trustedFrameworkPath(request.framework)) {
        try err_writer.writeAll("uid-helper run-jvm: framework is outside the trusted runtime profile\n");
        return 2;
    }
    if (!trustedCodeDir(request.work, request.package, request.code_dir)) {
        try err_writer.writeAll("uid-helper run-jvm: code directory is not anchored to the installed package\n");
        return 2;
    }
    const classes = try std.fmt.allocPrintSentinel(gpa, "{s}/classes", .{request.code_dir}, 0);
    defer gpa.free(classes);
    const cp = try std.fmt.allocPrintSentinel(gpa, "{s}:{s}", .{ classes, request.framework }, 0);
    defer gpa.free(cp);
    const lib_path = try std.fmt.allocPrintSentinel(gpa, "-Djava.library.path={s}/lib:{s}/lib/x86_64:{s}/lib", .{ request.framework, request.code_dir, request.code_dir }, 0);
    defer gpa.free(lib_path);
    const code_prop = try std.fmt.allocPrintSentinel(gpa, "-Disakai.codeDir={s}", .{request.code_dir}, 0);
    defer gpa.free(code_prop);
    const temp_prop = try std.fmt.allocPrintSentinel(gpa, "-Djava.io.tmpdir={s}/cache/tmp", .{request.data_dir}, 0);
    defer gpa.free(temp_prop);
    const data_z = try gpa.dupeSentinel(u8, request.data_dir, 0);
    defer gpa.free(data_z);
    const package_z = try gpa.dupeSentinel(u8, request.package, 0);
    defer gpa.free(package_z);
    const argv: [19][:0]const u8 = .{
        request.java,                                                                  "--enable-native-access=ALL-UNNAMED", lib_path,
        if (request.windowed) "-Disakai.windowed=true" else "-Disakai.windowed=false", code_prop,                            temp_prop,
        "-cp",                                                                         cp,                                   "org.isakai.runtime.IsakaiMain",
        data_z,                                                                        request.app_class,                    request.activity_class,
        package_z,                                                                     request.title,                        request.app_theme,
        request.activity_theme,                                                        request.target_sdk,                   request.min_sdk,
        request.intent_data,
    };
    return runPrepared(request.package, request.uid, request.data_dir, request.work, request.code_dir, &argv, request.wayland_sock, request.bridge_dir, request.framework, confinement_disabled, err_writer);
}

fn trustedCodeDir(work: []const u8, package: []const u8, path: []const u8) bool {
    if (!validWaylandSockPath(path)) return false;
    var prefix_buf: [4096]u8 = undefined;
    const prefix = std.fmt.bufPrint(&prefix_buf, "{s}/code/{s}/", .{ work, package }) catch return false;
    if (!std.mem.startsWith(u8, path, prefix)) return false;
    const digest = path[prefix.len..];
    if (digest.len != 64) return false;
    for (digest) |byte| {
        if (!std.ascii.isDigit(byte) and !(byte >= 'a' and byte <= 'f')) return false;
    }
    return true;
}

fn trustedFrameworkPath(path: []const u8) bool {
    if (std.mem.eql(u8, path, "/usr/share/isakai/framework")) return true;
    const linux = std.os.linux;
    var exe_buf: [4096]u8 = undefined;
    const n = linux.readlink("/proc/self/exe", &exe_buf, exe_buf.len);
    if (linux.errno(n) != .SUCCESS or n == exe_buf.len) return false;
    const bin = std.fs.path.dirname(exe_buf[0..n]) orelse return false;
    const prefix = std.fs.path.dirname(bin) orelse return false;
    var expected_buf: [4096]u8 = undefined;
    const expected = std.fmt.bufPrint(&expected_buf, "{s}/framework", .{prefix}) catch return false;
    if (std.mem.eql(u8, path, expected)) return true;
    var sibling_buf: [4096]u8 = undefined;
    const sibling = std.fmt.bufPrint(&sibling_buf, "{s}/framework", .{bin}) catch return false;
    return std.mem.eql(u8, path, sibling);
}

/// Fork, drop to the app user, set the per-app environment, exec the
/// command. Parent waits and returns the child's exit code (or 128+signal).
/// Must be root. `wayland_sock` (from the launcher's --wayland-sock flag)
/// is validated pre-fork (absolute, no `..`, SOCK_STREAM) and, when
/// present, bind-mounted into data_dir/run pre-drop with WAYLAND_DISPLAY
/// set in the child env. Drop order in the child: NO_NEW_PRIVS +
/// CLONE_NEWNS mount-ns confinement, then setgroups(0) → setresgid →
/// setresuid, every return checked, _exit(127) on failure. Never returns
/// the child.
fn runPrepared(
    package: []const u8,
    uid: u32,
    data_dir: []const u8,
    work: []const u8,
    code_dir: []const u8,
    cmd: []const [:0]const u8,
    wayland_sock: ?[]const u8,
    bridge_dir: ?[]const u8,
    framework_dir: ?[]const u8,
    confinement_disabled: bool,
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
    if (!validWaylandSockPath(work) or std.mem.eql(u8, work, "/")) {
        try err_writer.writeAll("uid-helper run: bad work path\n");
        return 2;
    }
    // Anchor work level by level: every prefix must be a real directory.
    {
        var wbuf: [4096]u8 = undefined;
        var i: usize = 1;
        while (i <= work.len) : (i += 1) {
            if (i == work.len or work[i] == '/') {
                const wz = toZ(work[0..i], &wbuf) orelse {
                    try err_writer.writeAll("uid-helper run: bad work path\n");
                    return 2;
                };
                var wstx: linux.Statx = undefined;
                const wrc = linux.statx(linux.AT.FDCWD, wz, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true }, &wstx);
                if (linux.errno(wrc) != .SUCCESS or wstx.mode & linux.S.IFMT != linux.S.IFDIR) {
                    try err_writer.writeAll("uid-helper run: work dir not anchored\n");
                    return 2;
                }
            }
        }
    }
    if (framework_dir) |path| {
        if (!validWaylandSockPath(path) or std.mem.eql(u8, path, "/")) {
            try err_writer.writeAll("uid-helper run: bad framework path\n");
            return 2;
        }
    }
    if (cmd.len != 19) unreachable;
    // Fixed positions from runJvm above: flags, classpath, entry class.
    if (!std.mem.eql(u8, cmd[6], "-cp") or !std.mem.eql(u8, cmd[8], "org.isakai.runtime.IsakaiMain")) {
        try err_writer.writeAll("uid-helper run: command shape unexpected\n");
        return 2;
    }
    // argv[0] must be absolute (no PATH search: PATH is attacker-influenced
    // when invoked oddly) and name the java launcher.
    if (!validJavaCmd(cmd[0])) {
        try err_writer.writeAll("uid-helper run: command not allowlisted (absolute path to java required)\n");
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
    // --wayland-sock, when given, must be absolute with no `..` and must
    // name a live SOCK_STREAM unix socket. Fail-closed (exit 2): never
    // launch unbrokered when the flag was given, never broker a bad path.
    if (wayland_sock) |sock| {
        if (!validWaylandSockPath(sock)) {
            try err_writer.writeAll("uid-helper run: bad wayland socket path (absolute, no `..` required)\n");
            return 2;
        }
        if (!isStreamSocket(sock)) {
            try err_writer.writeAll("uid-helper run: wayland socket is not a live stream socket\n");
            return 2;
        }
    }
    // --bridge-dir, when given, is the launcher-created desktop-bridge
    // endpoint: absolute, no `..`, short enough for sun_path, named inside
    // /tmp/isakai-*, a real directory owned by a *different* uid than the
    // app (the app must not be able to replace the socket) and not
    // group/other writable. Fail-closed: the sandbox env and the Landlock
    // grant must name a directory the launcher actually prepared.
    if (bridge_dir) |dir| {
        if (!validBridgeEndpointDir(dir, uid)) {
            try err_writer.writeAll("uid-helper run: bad bridge dir (want /tmp/isakai-<uid>-<digest>, owned by another uid, not group/other writable)\n");
            return 2;
        }
    }
    const pid_usize = linux.fork();
    const errno_fork = linux.errno(pid_usize);
    if (errno_fork != .SUCCESS) {
        try err_writer.print("uid-helper run: fork failed: {t}\n", .{errno_fork});
        return 1;
    }
    const pid: std.posix.pid_t = @bitCast(@as(u32, @truncate(pid_usize)));
    if (pid == 0) {
        // Child: mount setup, drop privileges, confine, set env, exec. Async-signal-safe
        // only; on any failure _exit(127) — never return to the caller.
        childMain(uid, data_dir, work, package, code_dir, cmd, wayland_sock, bridge_dir, framework_dir, confinement_disabled);
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
        if (linux.W.IFSIGNALED(st)) return @intCast(128 + @backingInt(linux.W.TERMSIG(st)));
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

/// --wayland-sock path shape: absolute, non-empty, no `..` component.
/// Socket liveness is checked separately by isStreamSocket.
pub fn validWaylandSockPath(sock: []const u8) bool {
    if (sock.len == 0 or sock.len >= 4096) return false;
    if (sock[0] != '/') return false;
    if (std.mem.indexOf(u8, sock, "..") != null) return false;
    if (std.mem.indexOfScalar(u8, sock, 0) != null) return false;
    return true;
}

/// True when `path` names a live SOCK_STREAM unix socket without
/// following a symlinked final component (NOFOLLOW reports the link
/// itself, never the target). A SOCK_STREAM connect must also succeed,
/// so datagram sockets fail here. Pre-fork probe: a negative here is
/// fail-closed (exit 2), never a fallback to unbrokered.
pub fn isStreamSocket(path: []const u8) bool {
    const linux = std.os.linux;
    var zbuf: [4096]u8 = undefined;
    const z = toZ(path, &zbuf) orelse return false;
    var stx: linux.Statx = undefined;
    const rc = linux.statx(linux.AT.FDCWD, z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true }, &stx);
    if (linux.errno(rc) != .SUCCESS) return false;
    if (stx.mode & linux.S.IFMT != linux.S.IFSOCK) return false;
    const sfd = linux.socket(@as(u32, linux.AF.UNIX), @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC), 0);
    if (linux.errno(sfd) != .SUCCESS) return false;
    const s: linux.fd_t = @bitCast(@as(u32, @truncate(sfd)));
    defer _ = linux.close(s);
    var addr_arr: [pathmod.max_path]u8 = undefined;
    @memset(&addr_arr, 0);
    if (path.len >= addr_arr.len) return false;
    @memcpy(addr_arr[0..path.len], path);
    var addr = linux.sockaddr.un{ .path = addr_arr };
    const addrlen: linux.socklen_t = @intCast(2 + path.len + 1);
    return linux.errno(linux.connect(s, @ptrCast(&addr), addrlen)) == .SUCCESS;
}

/// True when `dir` is a desktop-bridge endpoint the helper may export to
/// the sandbox and grant to Landlock: shape-checked by the shared module
/// (absolute, inside /tmp/isakai-*, short enough for `<dir>/bridge.sock`),
/// an existing directory (no symlink), owned by a uid other than the app's
/// (so the app cannot swap the socket node), and not group/other writable
/// (so no third party can). Allocation-free: runs pre-fork.
pub fn validBridgeEndpointDir(dir: []const u8, app_uid: u32) bool {
    const linux = std.os.linux;
    if (!pathmod.validDir(dir)) return false;
    if (!pathmod.fitsSocketPath(dir)) return false;
    var zbuf: [pathmod.max_dir + 1]u8 = undefined;
    @memcpy(zbuf[0..dir.len], dir);
    zbuf[dir.len] = 0;
    const z: [*:0]const u8 = @ptrCast(&zbuf);
    var stx: linux.Statx = undefined;
    const rc = linux.statx(linux.AT.FDCWD, z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true, .MODE = true, .UID = true }, &stx);
    if (linux.errno(rc) != .SUCCESS) return false;
    if (stx.mode & linux.S.IFMT != linux.S.IFDIR) return false;
    if (stx.uid == app_uid) return false;
    // Name-bound owner: the directory name carries its launcher uid, so
    // only that uid may own it on disk.
    {
        const base = std.fs.path.basename(dir);
        if (!std.mem.startsWith(u8, base, "isakai-")) return false;
        const tail = base["isakai-".len..];
        const dash = std.mem.indexOfScalar(u8, tail, '-') orelse return false;
        const owner = std.fmt.parseInt(u32, tail[0..dash], 10) catch return false;
        if (stx.uid != owner) return false;
    }
    if (stx.mode & 0o022 != 0) return false;
    return true;
}

fn childMain(uid: u32, data_dir: []const u8, work: []const u8, package: []const u8, code_dir: []const u8, cmd: []const [:0]const u8, wayland_sock: ?[]const u8, bridge_dir: ?[]const u8, framework_dir: ?[]const u8, confinement_disabled: bool) noreturn {
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
    // -1. Confinement, still root, before any drop: NO_NEW_PRIVS (exec
    // gains no privilege afterwards) + a private mount namespace whose
    // propagation is slave/private so later bind mounts never escape to
    // the host. Fail-closed with a stderr diagnostic; _exit(127).
    confineChild();
    // 0b. Wayland brokering: bind-mount the compositor socket onto
    // <data_dir>/run/<basename> (regular-file 0600 target) inside the
    // private mount ns, still root pre-drop. Fail-closed: _exit(127).
    var wayland_base: ?[]const u8 = null;
    var wayland_disp_buf: [108]u8 = undefined;
    if (wayland_sock) |sock| {
        wayland_base = brokerWaylandSock(sock, data_dir, &wayland_disp_buf) orelse linux.exit(127);
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
    // All privileged filesystem setup is complete. Rules refer to this app's
    // code tree, not its package-name string; stage is writable, bridge is RO.
    applyRuntimeConfinement(data_dir, work, package, bridge_dir, cmd[0], framework_dir, confinement_disabled) catch |err| {
        const prefix = "uid-helper run: confinement failed: ";
        _ = linux.write(linux.STDERR_FILENO, prefix, prefix.len);
        const name = @errorName(err);
        _ = linux.write(linux.STDERR_FILENO, name.ptr, name.len);
        _ = linux.write(linux.STDERR_FILENO, "\n", 1);
        linux.exit(127);
    };
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
    // Bridge/stage paths: the launcher passes the desktop-bridge endpoint
    // explicitly (short /tmp socket dir, mirrored into the Landlock grant
    // above); legacy callers without --bridge-dir keep the work/run/<pkg>
    // anchor. No DBUS_* vars are set: the sandbox reaches the bus only via
    // the bridge socket.
    var bridge_buf: [4352]u8 = undefined;
    var stage_buf: [4352]u8 = undefined;
    var legacy_endpoint_buf: [4096]u8 = undefined;
    const endpoint: []const u8 = bridge_dir orelse blk: {
        break :blk std.fmt.bufPrint(&legacy_endpoint_buf, "{s}/run/{s}", .{ work, package }) catch linux.exit(127);
    };
    const bridge = std.fmt.bufPrint(&bridge_buf, "ISAKAI_BRIDGE={s}\x00", .{endpoint}) catch linux.exit(127);
    const stage = std.fmt.bufPrint(&stage_buf, "ISAKAI_STAGE={s}/stage/{s}\x00", .{ work, package }) catch linux.exit(127);
    _ = bridge;
    _ = stage;
    // WAYLAND_DISPLAY=<basename>: points at the bind-mounted socket under
    // XDG_RUNTIME_DIR (<data_dir>/run). Only present when brokered.
    var wdisplay_buf: [128]u8 = undefined;
    if (wayland_base) |base| {
        _ = std.fmt.bufPrint(&wdisplay_buf, "WAYLAND_DISPLAY={s}\x00", .{base}) catch linux.exit(127);
    }
    // M6a: LD_PRELOAD the bionic shim so NDK app .so files that call
    // bionic-only symbols (__android_log_*, property_*) resolve. The shim
    // lives in framework/lib — only inject when the framework was passed
    // (privileged runs with a real framework dir).
    var preload_buf: [4352]u8 = undefined;
    var bionic_path_buf: [4352]u8 = undefined;
    var has_preload = false;
    if (framework_dir) |f| {
        _ = std.fmt.bufPrint(&preload_buf, "LD_PRELOAD={s}/lib/libisakai_bionic_shim.so\x00", .{f}) catch linux.exit(127);
        has_preload = true;
    }
    _ = std.fmt.bufPrint(&bionic_path_buf, "BIONIC_LD_LIBRARY_PATH={s}/lib/x86_64\x00", .{code_dir}) catch linux.exit(127);
    const env_base: [6]?[*:0]const u8 = .{
        @ptrCast(&tmpdir_buf),
        @ptrCast(&rundir_buf),
        @ptrCast(&home_buf),
        @ptrCast("PATH=/usr/bin:/bin\x00"),
        @ptrCast(&bridge_buf),
        @ptrCast(&stage_buf),
    };
    var envp_buf: [11]?[*:0]const u8 = .{ null, null, null, null, null, null, null, null, null, null, null };
    for (env_base, 0..) |e, i| envp_buf[i] = e;
    var nenv: usize = env_base.len;
    // Native exec runner: sibling of this helper (installed tree only),
    // so confined apps can run Android PIE subprocesses under the bionic
    // loader. Absent in dev layouts: the shim then fails closed as before.
    var bionicexec_buf: [4352]u8 = undefined;
    {
        var exe_buf: [4096]u8 = undefined;
        const n = linux.readlink("/proc/self/exe", &exe_buf, exe_buf.len);
        if (linux.errno(n) == .SUCCESS and n < exe_buf.len) {
            if (std.fs.path.dirname(exe_buf[0..n])) |bin| {
                const runner = std.fmt.bufPrint(&bionicexec_buf, "ISAKAI_BIONIC_EXEC={s}/isakai-bionic-exec\x00", .{bin}) catch null;
                if (runner) |r| {
                    var check_buf: [4096]u8 = undefined;
                    const cpath = std.fmt.bufPrint(&check_buf, "{s}/isakai-bionic-exec", .{bin}) catch null;
                    if (cpath) |cp| {
                        var zbuf: [4096]u8 = undefined;
                        if (cp.len + 1 <= zbuf.len) {
                            @memcpy(zbuf[0..cp.len], cp);
                            zbuf[cp.len] = 0;
                            const zpath: [*:0]const u8 = @ptrCast(&zbuf);
                            const fd = linux.openat(linux.AT.FDCWD, zpath, .{}, 0);
                            if (linux.errno(fd) == .SUCCESS) {
                                _ = linux.close(@bitCast(@as(u32, @truncate(fd))));
                                envp_buf[nenv] = @ptrCast(r.ptr);
                                nenv += 1;
                            }
                        }
                    }
                }
            }
        }
    }
    envp_buf[nenv] = @ptrCast(&bionic_path_buf);
    nenv += 1;
    if (wayland_base != null) {
        envp_buf[nenv] = @ptrCast(&wdisplay_buf);
        nenv += 1;
    }
    if (has_preload) {
        envp_buf[nenv] = @ptrCast(&preload_buf);
        nenv += 1;
    }
    envp_buf[nenv] = null;
    const envp: [*:null]const ?[*:0]const u8 = @ptrCast(&envp_buf);
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

/// Build an allocation-free policy from anchored app paths and the canonical
/// Java executable. Shared with the live runtime regression launcher.
/// `bridge_dir` is the launcher-created desktop-bridge endpoint
/// (`/tmp/isakai-<uid>-<digest>`); null keeps the legacy
/// `<work>/run/<package>` anchor and tolerates its absence.
pub fn applyRuntimeConfinement(data_dir: []const u8, work: []const u8, package: []const u8, bridge_dir: ?[]const u8, java: []const u8, framework_dir: ?[]const u8, disabled: bool) !void {
    const confine = @import("confine");
    const linux = std.os.linux;
    var code_buf: [4096]u8 = undefined;
    var stage_buf: [4096]u8 = undefined;
    var bridge_buf: [4096]u8 = undefined;
    const code = try std.fmt.bufPrint(&code_buf, "{s}/code/{s}", .{ work, package });
    const stage = try std.fmt.bufPrint(&stage_buf, "{s}/stage/{s}", .{ work, package });
    // The endpoint holds the bridge socket: read-only (connect only).
    const bridge = bridge_dir orelse try std.fmt.bufPrint(&bridge_buf, "{s}/run/{s}", .{ work, package });
    // Endpoints outside the work tree may legitimately be absent on a
    // bridge-less run; an explicitly passed endpoint must exist.
    const bridge_optional = bridge_dir == null;
    // Resolve symlinked /usr/bin/java before choosing its JDK home.
    var java_z_buf: [4096]u8 = undefined;
    const java_z = toZ(java, &java_z_buf) orelse return error.BadJavaPath;
    const opened = linux.openat(linux.AT.FDCWD, java_z, .{ .PATH = true, .CLOEXEC = true }, 0);
    if (linux.errno(opened) != .SUCCESS) return error.BadJavaPath;
    const fd: i32 = @intCast(opened);
    defer _ = linux.close(fd);
    var link_buf: [64]u8 = undefined;
    _ = try std.fmt.bufPrint(&link_buf, "/proc/self/fd/{d}\x00", .{fd});
    const link: [*:0]const u8 = @ptrCast(&link_buf);
    var resolved_buf: [4096]u8 = undefined;
    const n = linux.readlink(link, &resolved_buf, resolved_buf.len);
    if (linux.errno(n) != .SUCCESS or n == resolved_buf.len) return error.BadJavaPath;
    const bin = std.fs.path.dirname(resolved_buf[0..n]) orelse return error.BadJavaPath;
    if (!std.mem.eql(u8, std.fs.path.basename(bin), "bin")) return error.BadJavaPath;
    const java_home = std.fs.path.dirname(bin) orelse return error.BadJavaPath;
    if (std.mem.eql(u8, java_home, "/")) return error.BadJavaPath;
    // Opening conf separately follows distro symlinks outside JAVA_HOME.
    var java_conf_buf: [4096]u8 = undefined;
    const java_conf = try std.fmt.bufPrint(&java_conf_buf, "{s}/conf", .{java_home});
    var rules: [64]confine.Rule = undefined;
    var count: usize = 0;
    for ([_][]const u8{ java_home, code }) |path| {
        rules[count] = .{ .path = path };
        count += 1;
    }
    rules[count] = .{ .path = bridge, .optional = bridge_optional };
    count += 1;
    rules[count] = .{ .path = java_conf, .optional = true };
    count += 1;
    if (framework_dir) |path| {
        rules[count] = .{ .path = path };
        count += 1;
    }
    for ([_][]const u8{ data_dir, stage }) |path| {
        rules[count] = .{ .path = path, .access = confine.read_write };
        count += 1;
    }
    // Distro runtime libraries/configuration, not /usr or /etc wholesale.
    // /proc/self supports JVM introspection; another app's /proc tree is not
    // granted. Optional entries differ between Arch and Debian installations.
    for ([_][]const u8{
        "/lib",                  "/lib64",              "/usr/lib",                       "/usr/lib64",
        "/etc/ld.so.cache",      "/etc/ld.so.conf",     "/etc/ld.so.conf.d",              "/etc/ssl/certs",
        "/etc/pki",              "/etc/resolv.conf",    "/etc/hosts",                     "/etc/nsswitch.conf",
        "/etc/localtime",        "/etc/fonts",          "/etc/pulse",                     "/usr/share/fonts",
        "/usr/share/fontconfig", "/usr/share/zoneinfo", "/proc/self",                     "/proc/cpuinfo",
        "/proc/meminfo",         "/proc/stat",          "/proc/sys/vm/overcommit_memory", "/sys/devices/system/cpu",
        "/sys/fs/cgroup",        "/usr/bin/ffmpeg",     "/usr/bin/ffprobe",
        "/usr/bin/isakai-bionic-exec",
    }) |path| {
        rules[count] = .{ .path = path, .optional = true };
        count += 1;
    }
    for ([_][]const u8{ "/dev/null", "/dev/zero", "/dev/random", "/dev/urandom" }) |path| {
        rules[count] = .{ .path = path, .access = confine.read_write };
        count += 1;
    }
    try confine.confineAndRestrict(rules[0..count], disabled);
}

/// NO_NEW_PRIVS + private mount namespace, still root, pre-drop.
/// Fail-closed: stderr diagnostic + _exit(127) on any failure.
/// Async-signal-safe only (raw syscalls + linux.write). Runs as root
/// (helper entry requires root), where unshare(CLONE_NEWNS) is permitted;
/// without root the unshare fails and the launch aborts — by design.
fn confineChild() void {
    const linux = std.os.linux;
    // PR_SET_NO_NEW_PRIVS=38, arg 1: exec transitions gain no privilege.
    const nnp = linux.prctl(38, 1, 0, 0, 0);
    if (linux.errno(nnp) != .SUCCESS) {
        const msg = "uid-helper run: prctl(NO_NEW_PRIVS) failed\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        linux.exit(127);
    }
    // CLONE_NEWNS: private mount namespace so the socket bind-mount (and
    // any app mounts) never escape to the host. Entrance mounts keep host
    // propagation otherwise; mark the tree slave then private.
    if (linux.errno(linux.unshare(linux.CLONE.NEWNS)) != .SUCCESS) {
        const msg = "uid-helper run: unshare(CLONE_NEWNS) failed\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        linux.exit(127);
    }
    const none: [*:0]const u8 = @ptrCast("none\x00");
    const root: [*:0]const u8 = @ptrCast("/\x00");
    // mount("none", "/", null, MS_SLAVE|MS_REC, null), then MS_PRIVATE|MS_REC.
    if (linux.errno(linux.mount(none, root, null, linux.MS.SLAVE | linux.MS.REC, 0)) != .SUCCESS) {
        const msg = "uid-helper run: mount(/,SLAVE|REC) failed\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        linux.exit(127);
    }
    if (linux.errno(linux.mount(none, root, null, linux.MS.PRIVATE | linux.MS.REC, 0)) != .SUCCESS) {
        const msg = "uid-helper run: mount(/,PRIVATE|REC) failed\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        linux.exit(127);
    }
}

/// Bind-mount the validated compositor socket onto
/// <data_dir>/run/<basename>. Target is created as a 0600 regular file
/// (O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC) so a swapped symlink cannot
/// redirect the mount. Returns the basename slice (into name_buf) for the
/// WAYLAND_DISPLAY value, or null on any failure (caller _exit(127)s).
/// Async-signal-safe: raw syscalls only, fixed stack buffers.
fn brokerWaylandSock(sock: []const u8, data_dir: []const u8, name_buf: *[108]u8) ?[]const u8 {
    const linux = std.os.linux;
    if (!validWaylandSockPath(sock)) return null;
    const base = std.fs.path.basename(sock);
    if (base.len == 0 or base.len >= name_buf.len) return null;
    if (std.mem.indexOfScalar(u8, base, '/') != null) return null;
    @memcpy(name_buf[0..base.len], base);
    name_buf[base.len] = 0;
    const base_z: [*:0]const u8 = @ptrCast(name_buf);
    _ = base_z;
    // Re-validate the source is still a socket (TOCTOU: worst case the
    // bind below fails and the child _exit(127)s — fail-closed).
    {
        var sbuf: [4096]u8 = undefined;
        if (sock.len + 1 > sbuf.len) return null;
        @memcpy(sbuf[0..sock.len], sock);
        sbuf[sock.len] = 0;
        const z: [*:0]const u8 = @ptrCast(&sbuf);
        var stx: linux.Statx = undefined;
        const rc = linux.statx(linux.AT.FDCWD, z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true }, &stx);
        if (linux.errno(rc) != .SUCCESS) {
            const msg = "uid-helper run: wayland socket vanished\n";
            _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
            return null;
        }
        if (stx.mode & linux.S.IFMT != linux.S.IFSOCK) {
            const msg = "uid-helper run: wayland socket replaced\n";
            _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
            return null;
        }
    }
    // Target <data_dir>/run/<base>: open data_dir NOFOLLOW, then run dir,
    // then O_CREAT|O_EXCL|O_NOFOLLOW the target file 0600.
    var dbuf: [4096]u8 = undefined;
    var tbuf: [4096]u8 = undefined;
    var sbuf2: [4096]u8 = undefined;
    if (data_dir.len + 1 > dbuf.len) return null;
    @memcpy(dbuf[0..data_dir.len], data_dir);
    dbuf[data_dir.len] = 0;
    const data_z: [*:0]const u8 = @ptrCast(&dbuf);
    const data_fd = openDirNoFollow(linux.AT.FDCWD, data_z) orelse {
        const msg = "uid-helper run: data dir not openable\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        return null;
    };
    defer _ = linux.close(data_fd);
    const run_z: [*:0]const u8 = @ptrCast("run\x00");
    const run_fd = openDirNoFollow(data_fd, run_z) orelse {
        const msg = "uid-helper run: data run dir missing\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        return null;
    };
    defer _ = linux.close(run_fd);
    if (base.len + 1 > tbuf.len) return null;
    @memcpy(tbuf[0..base.len], base);
    tbuf[base.len] = 0;
    const tgt_z: [*:0]const u8 = @ptrCast(&tbuf);
    {
        const rc = linux.openat(run_fd, tgt_z, .{ .ACCMODE = .RDWR, .CREAT = true, .EXCL = true, .NOFOLLOW = true, .CLOEXEC = true }, 0o600);
        const err = linux.errno(rc);
        if (err != .SUCCESS) {
            // Target exists: only reuse a regular 0600-or-tighter file we
            // own the dirfd to (never follow); else fail closed.
            if (err != .EXIST) {
                const msg = "uid-helper run: wayland target create failed\n";
                _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
                return null;
            }
            const probe = linux.openat(run_fd, tgt_z, .{ .ACCMODE = .RDWR, .NOFOLLOW = true, .CLOEXEC = true }, 0);
            if (linux.errno(probe) != .SUCCESS) {
                const msg = "uid-helper run: wayland target not openable\n";
                _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
                return null;
            }
            const probe_fd: i32 = @bitCast(@as(u32, @truncate(probe)));
            {
                var stx: linux.Statx = undefined;
                const src = linux.statx(probe_fd, "", linux.AT.EMPTY_PATH, .{ .TYPE = true, .MODE = true }, &stx);
                _ = linux.close(probe_fd);
                if (linux.errno(src) != .SUCCESS) {
                    const msg = "uid-helper run: wayland target not statable\n";
                    _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
                    return null;
                }
                if (stx.mode & linux.S.IFMT != linux.S.IFREG) {
                    const msg = "uid-helper run: wayland target not a regular file\n";
                    _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
                    return null;
                }
                if (stx.mode & 0o077 != 0) {
                    const msg = "uid-helper run: wayland target too permissive\n";
                    _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
                    return null;
                }
            }
        } else {
            _ = linux.close(@bitCast(@as(u32, @truncate(rc))));
        }
    }
    if (sock.len + 1 > sbuf2.len) return null;
    @memcpy(sbuf2[0..sock.len], sock);
    sbuf2[sock.len] = 0;
    const src_z: [*:0]const u8 = @ptrCast(&sbuf2);
    // Bind-mount via /proc/self/fd: mount(src, /proc/self/fd/<run_fd>/<base>,
    // null, MS_BIND, null). Resolves the dirfd-anchored target to a path
    // without re-walking it by name (symlink-safe: fds were NOFOLLOW).
    var dstbuf: [64]u8 = undefined;
    const dst = std.fmt.bufPrint(&dstbuf, "/proc/self/fd/{d}/{s}\x00", .{ run_fd, base }) catch return null;
    _ = dst;
    const dst_z: [*:0]const u8 = @ptrCast(&dstbuf);
    if (linux.errno(linux.mount(src_z, dst_z, null, linux.MS.BIND, 0)) != .SUCCESS) {
        const msg = "uid-helper run: wayland bind mount failed\n";
        _ = linux.write(linux.STDERR_FILENO, msg, msg.len);
        return null;
    }
    return name_buf[0..base.len];
}
