// SPDX-License-Identifier: GPL-3.0-or-later

//! The package registry: the single source of truth for what is installed.
//! Writes go through a temporary file and a rename so a crash mid-write
//! cannot corrupt it. Ported from the Rust broker's registry.rs.

const std = @import("std");

pub const AppState = union(enum) {
    installed: void,
    starting: void,
    running: struct {
        pid: u32,
        started: u64 = 0,
    },
    exited: struct {
        code: ?i32,
    },
    removing: void,

    pub fn jsonStringify(self: AppState, jw: *std.json.Stringify) !void {
        try jw.beginObject();
        switch (self) {
            .installed => try jw.objectField("status"),
            .starting => try jw.objectField("status"),
            .removing => try jw.objectField("status"),
            .running, .exited => try jw.objectField("status"),
        }
        switch (self) {
            .installed => try jw.write("installed"),
            .starting => try jw.write("starting"),
            .removing => try jw.write("removing"),
            .running => |r| {
                try jw.write("running");
                try jw.objectField("pid");
                try jw.write(r.pid);
                try jw.objectField("started");
                try jw.write(r.started);
            },
            .exited => |e| {
                try jw.write("exited");
                try jw.objectField("code");
                try jw.write(e.code);
            },
        }
        try jw.endObject();
    }

    pub fn jsonParse(
        allocator: std.mem.Allocator,
        source: anytype,
        options: std.json.ParseOptions,
    ) !AppState {
        const Value = struct {
            status: []const u8,
            pid: u32 = 0,
            started: u64 = 0,
            code: ?i32 = null,
        };
        const v = try std.json.innerParse(Value, allocator, source, options);
        if (std.mem.eql(u8, v.status, "installed")) return .installed;
        if (std.mem.eql(u8, v.status, "starting")) return .starting;
        if (std.mem.eql(u8, v.status, "removing")) return .removing;
        if (std.mem.eql(u8, v.status, "running")) return .{ .running = .{ .pid = v.pid, .started = v.started } };
        if (std.mem.eql(u8, v.status, "exited")) return .{ .exited = .{ .code = v.code } };
        return error.UnexpectedToken;
    }
};

pub const Package = struct {
    name: []const u8,
    /// The app's own system UID.
    uid: u32,
    data_dir: []const u8,
    apk: []const u8,
    /// Previous code dir kept for one generation so `upgrade` can be
    /// rolled back. Null when there is no previous generation; absent in
    /// registries written before upgrades landed (defaults to null).
    prev_apk: ?[]const u8 = null,
    installed_at: u64,
    state: AppState = .installed,
    signer_sha256: ?[]const u8 = null,
    /// All verified v2 signer leaf-cert fingerprints (sorted, deduped).
    /// M6e multi-signer: the full identity set is pinned; upgrades require
    /// the new APK's set to match exactly. Absent/empty = single-signer
    /// registry (use `signer_sha256`) or pre-M6e entry.
    signer_sha256_set: []const []const u8 = &.{},
    launch_id: u64 = 0,
    /// Host machine arch recorded at install (uname -m, e.g. "x86_64").
    /// Defaults to "" for entries written before arch capture landed;
    /// install always fills it, so empty means pre-arch registry.
    arch: []const u8 = "",
    /// The native ABI slice selected at install ("x86_64"), or ""
    /// for a bytecode-only app with no native libraries. The installer keeps
    /// every ABI slice beside the retained base.apk; this records which one
    /// is selected for execution, so a reviewer can see it without re-reading
    /// the APK.
    native_abi: []const u8 = "",
    /// android:versionCode pinned at install; upgrades to an older build fail.
    version_code: u32 = 0,
};

pub const Registry = struct {
    packages: std.StringHashMapUnmanaged(Package) = .empty,
    arena: std.heap.ArenaAllocator,

    const Self = @This();

    pub fn init(gpa: std.mem.Allocator) Self {
        return .{ .arena = std.heap.ArenaAllocator.init(gpa) };
    }

    pub fn deinit(self: *Self) void {
        self.arena.deinit();
    }

    fn allocator(self: *Self) std.mem.Allocator {
        return self.arena.allocator();
    }

    fn registryPath(gpa: std.mem.Allocator, work: []const u8) ![]u8 {
        return std.fmt.allocPrint(gpa, "{s}/registry.json", .{work});
    }
    /// Host machine arch (uname -m, e.g. "x86_64"). Caller owns the result.
    pub fn hostArch(gpa: std.mem.Allocator) ![]u8 {
        const uts = std.posix.uname();
        const m = std.mem.sliceTo(&uts.machine, 0);
        return gpa.dupe(u8, m);
    }

    /// Write `data` to `path` atomically: O_CREAT|O_EXCL|O_NOFOLLOW tmp
    /// sibling + fsync + rename + parent-dir fsync. The tmp name carries the
    /// pid so two writers never share it; O_EXCL makes a stale tmp fail
    /// loudly, so a clash unlinks it and retries once instead of jamming
    /// every later save. Fail-closed: any error leaves the destination
    /// untouched.
    pub fn writeAtomic(io: std.Io, gpa: std.mem.Allocator, path: []const u8, data: []const u8, mode: u32) !void {
        const tmp_path = try tmpSiblingPath(gpa, path);
        defer gpa.free(tmp_path);
        const fd = openTmpExclusive(tmp_path, mode) catch |err| blk: {
            if (err != error.TmpExists) return err;
            try std.Io.Dir.deleteFileAbsolute(io, tmp_path);
            break :blk try openTmpExclusive(tmp_path, mode);
        };
        errdefer std.Io.Dir.deleteFileAbsolute(io, tmp_path) catch {};
        var off: usize = 0;
        while (off < data.len) {
            const n = std.os.linux.write(fd, data.ptr + off, data.len - off);
            const err = std.os.linux.errno(n);
            if (err != .SUCCESS) {
                _ = std.os.linux.close(fd);
                return error.WriteFailed;
            }
            const wrote: usize = @intCast(n);
            if (wrote == 0) {
                _ = std.os.linux.close(fd);
                return error.WriteFailed;
            }
            off += wrote;
        }
        if (std.os.linux.errno(std.os.linux.fsync(fd)) != .SUCCESS) {
            _ = std.os.linux.close(fd);
            return error.SyncFailed;
        }
        _ = std.os.linux.close(fd);
        try std.Io.Dir.renameAbsolute(tmp_path, path, io);
        // The file fsync above orders the data; fsync the parent dir so
        // the rename itself survives a crash.
        try fsyncParentDir(path);
    }

    /// Tmp sibling for an atomic write: "<path>.tmp.<pid>". Caller owns.
    pub fn tmpSiblingPath(gpa: std.mem.Allocator, path: []const u8) ![]u8 {
        return std.fmt.allocPrint(gpa, "{s}.tmp.{d}", .{ path, std.os.linux.getpid() });
    }

    /// Open a tmp sibling O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC.
    /// O_EXCL: a stale sibling fails loudly instead of being truncated.
    fn openTmpExclusive(path: []const u8, mode: u32) !std.os.linux.fd_t {
        const linux = std.os.linux;
        var zbuf: [4096]u8 = undefined;
        if (path.len + 1 > zbuf.len) return error.NameTooLong;
        @memcpy(zbuf[0..path.len], path);
        zbuf[path.len] = 0;
        const z: [*:0]const u8 = @ptrCast(&zbuf);
        const rc = linux.openat(linux.AT.FDCWD, z, .{ .ACCMODE = .WRONLY, .CREAT = true, .EXCL = true, .NOFOLLOW = true, .CLOEXEC = true }, @truncate(mode));
        const err = linux.errno(rc);
        if (err != .SUCCESS) {
            if (err == .EXIST) return error.TmpExists;
            return error.OpenFailed;
        }
        return @bitCast(@as(u32, @truncate(rc)));
    }

    /// Validate a staged symlink: readlink must resolve inside `anchor_dir`
    /// (exact dir or dir + "/..."), with no `..` escape in the joined path.
    /// Pure string check on the link target; callers re-verify post-rename.
    pub fn symlinkTargetAnchored(link_target: []const u8, anchor_dir: []const u8) bool {
        if (link_target.len == 0) return false;
        if (std.mem.indexOf(u8, link_target, "..") != null) return false;
        if (link_target[0] == '/') {
            if (std.mem.eql(u8, link_target, anchor_dir)) return true;
            if (link_target.len <= anchor_dir.len) return false;
            if (!std.mem.startsWith(u8, link_target, anchor_dir)) return false;
            return link_target[anchor_dir.len] == '/';
        }
        var buf: [4096]u8 = undefined;
        const joined = std.fmt.bufPrint(&buf, "{s}/{s}", .{ anchor_dir, link_target }) catch return false;
        return std.mem.indexOf(u8, joined, "..") == null;
    }

    /// Read the symlink at `link_path` and check its target stays anchored
    /// under `anchor_dir`. Returns false when the path is not a symlink,
    /// is unreadable, or escapes. Buffer cap 4 KiB (Linux PATH_MAX).
    pub fn validateStagedSymlink(io: std.Io, link_path: []const u8, anchor_dir: []const u8) bool {
        var buf: [4096]u8 = undefined;
        const n = std.Io.Dir.readLinkAbsolute(io, link_path, &buf) catch return false;
        return symlinkTargetAnchored(buf[0..n], anchor_dir);
    }

    /// Advisory inter-process lock guarding check-then-act sequences
    /// (load → mutate → save). Crash-safe: the OS releases flock on exit.
    pub const Lock = struct {
        file: std.Io.File,
        io: std.Io,

        /// Open (creating) the sidecar and take the lock. Blocking by
        /// default; pass exclusive=false for a shared read lock.
        pub fn acquire(io: std.Io, gpa: std.mem.Allocator, work: []const u8, exclusive: bool) !Lock {
            try std.Io.Dir.cwd().createDirPath(io, work);
            const path = try std.fmt.allocPrint(gpa, "{s}/isakai.lock", .{work});
            defer gpa.free(path);
            const file = try std.Io.Dir.cwd().createFile(io, path, .{});
            errdefer file.close(io);
            try file.lock(io, if (exclusive) .exclusive else .shared);
            return .{ .file = file, .io = io };
        }

        pub fn release(self: *Lock) void {
            self.file.unlock(self.io);
            self.file.close(self.io);
        }
    };

    /// Load, failing on corrupt content. Missing file means empty.
    pub fn loadChecked(io: std.Io, gpa: std.mem.Allocator, work: []const u8) !Self {
        var self = Self.init(gpa);
        errdefer self.deinit();
        const path = try registryPath(gpa, work);
        defer gpa.free(path);
        const bytes = std.Io.Dir.cwd().readFileAlloc(io, path, gpa, .limited(16 * 1024 * 1024)) catch |err| switch (err) {
            error.FileNotFound => return self,
            else => return err,
        };
        defer gpa.free(bytes);
        const parsed = try std.json.parseFromSlice(
            std.json.ArrayHashMap(Package),
            gpa,
            bytes,
            .{},
        );
        defer parsed.deinit();
        const alloc = self.allocator();
        var it = parsed.value.map.iterator();
        while (it.next()) |entry| {
            const name = try alloc.dupe(u8, entry.key_ptr.*);
            var pkg = entry.value_ptr.*;
            pkg.name = name;
            pkg.data_dir = try alloc.dupe(u8, pkg.data_dir);
            pkg.apk = try alloc.dupe(u8, pkg.apk);
            if (pkg.signer_sha256) |s| pkg.signer_sha256 = try alloc.dupe(u8, s);
            if (pkg.signer_sha256_set.len > 0) {
                const set = try alloc.alloc([]const u8, pkg.signer_sha256_set.len);
                for (pkg.signer_sha256_set, 0..) |s, i| set[i] = try alloc.dupe(u8, s);
                pkg.signer_sha256_set = set;
            }
            if (pkg.prev_apk) |p| pkg.prev_apk = try alloc.dupe(u8, p);
            pkg.arch = try alloc.dupe(u8, pkg.arch);
            pkg.native_abi = try alloc.dupe(u8, pkg.native_abi);
            try self.packages.put(alloc, name, pkg);
        }
        return self;
    }

    /// Load, treating unreadable content as empty.
    pub fn load(io: std.Io, gpa: std.mem.Allocator, work: []const u8) Self {
        return loadChecked(io, gpa, work) catch Self.init(gpa);
    }

    pub fn save(self: *Self, io: std.Io, gpa: std.mem.Allocator, work: []const u8) !void {
        try std.Io.Dir.cwd().createDirPath(io, work);
        const path = try registryPath(gpa, work);
        defer gpa.free(path);
        // Serialize into memory first so the atomic write is one shot:
        // writeAtomic (O_EXCL tmp + fsync + rename) leaves any existing
        // registry untouched on failure.
        var aw: std.Io.Writer.Allocating = .init(gpa);
        defer aw.deinit();
        const w = &aw.writer;
        var jw: std.json.Stringify = .{ .writer = w, .options = .{ .whitespace = .indent_2 } };
        try jw.beginObject();
        // Sorted for stable output.
        var names: std.ArrayListUnmanaged([]const u8) = .empty;
        defer names.deinit(gpa);
        var it = self.packages.iterator();
        while (it.next()) |entry| try names.append(gpa, entry.key_ptr.*);
        std.mem.sort([]const u8, names.items, {}, struct {
            fn lt(_: void, a: []const u8, b: []const u8) bool {
                return std.mem.order(u8, a, b) == .lt;
            }
        }.lt);
        for (names.items) |name| {
            const pkg = self.packages.get(name).?;
            try jw.objectField(name);
            try jw.write(pkg);
        }
        try jw.endObject();
        try writeAtomic(io, gpa, path, aw.written(), 0o644);
    }

    pub fn add(self: *Self, package: Package) !void {
        const alloc = self.allocator();
        const name = try alloc.dupe(u8, package.name);
        var pkg = package;
        pkg.name = name;
        pkg.data_dir = try alloc.dupe(u8, package.data_dir);
        pkg.apk = try alloc.dupe(u8, package.apk);
        if (pkg.signer_sha256) |s| pkg.signer_sha256 = try alloc.dupe(u8, s);
        if (pkg.signer_sha256_set.len > 0) {
            const set = try alloc.alloc([]const u8, pkg.signer_sha256_set.len);
            for (pkg.signer_sha256_set, 0..) |s, i| set[i] = try alloc.dupe(u8, s);
            pkg.signer_sha256_set = set;
        }
        if (pkg.prev_apk) |p| pkg.prev_apk = try alloc.dupe(u8, p);
        pkg.arch = try alloc.dupe(u8, package.arch);
        pkg.native_abi = try alloc.dupe(u8, package.native_abi);
        try self.packages.put(alloc, name, pkg);
    }

    pub fn remove(self: *Self, name: []const u8) ?Package {
        // Key/value memory stays in the arena; fine for a registry lifetime.
        const kv = self.packages.fetchRemove(name) orelse return null;
        return kv.value;
    }

    /// Replace the stored package's state (or insert it) and persist.
    /// Collapses the remove/add/save idiom; distinct caller errors are
    /// preserved because add/save still surface OutOfMemory/IO failures.
    pub fn updateState(self: *Self, io: std.Io, gpa: std.mem.Allocator, work: []const u8, pkg: Package) !void {
        _ = self.remove(pkg.name);
        try self.add(pkg);
        try self.save(io, gpa, work);
    }

    pub fn get(self: *const Self, name: []const u8) ?Package {
        return self.packages.get(name);
    }

    pub const ResolveError = error{
        Ambiguous,
        NotFound,
        OutOfMemory,
    };

    /// The package a person means by `selector`: exact name wins, then a
    /// unique case-insensitive prefix, then a unique substring.
    pub fn resolve(self: *const Self, gpa: std.mem.Allocator, selector: []const u8) ResolveError!Package {
        if (self.packages.get(selector)) |pkg| return pkg;
        const folded = try std.ascii.allocLowerString(gpa, selector);
        defer gpa.free(folded);
        for ([_]bool{ true, false }) |prefixes_only| {
            var matches: std.ArrayListUnmanaged(Package) = .empty;
            defer matches.deinit(gpa);
            var it = self.packages.iterator();
            while (it.next()) |entry| {
                const lname = try std.ascii.allocLowerString(gpa, entry.key_ptr.*);
                defer gpa.free(lname);
                const hit = if (prefixes_only)
                    std.mem.startsWith(u8, lname, folded)
                else
                    std.mem.indexOf(u8, lname, folded) != null;
                if (hit) try matches.append(gpa, entry.value_ptr.*);
            }
            std.mem.sort(Package, matches.items, {}, struct {
                fn lt(_: void, a: Package, b: Package) bool {
                    return std.mem.order(u8, a.name, b.name) == .lt;
                }
            }.lt);
            switch (matches.items.len) {
                0 => continue,
                1 => return matches.items[0],
                else => return error.Ambiguous,
            }
        }
        return error.NotFound;
    }

    /// Next free UID in the reserved range, ignoring ones already taken.
    pub fn nextUid(self: *const Self, start: u32, end: u32) ?u32 {
        var uid: u32 = start;
        while (uid <= end) : (uid += 1) {
            var taken = false;
            var it = self.packages.iterator();
            while (it.next()) |entry| {
                if (entry.value_ptr.uid == uid) {
                    taken = true;
                    break;
                }
            }
            if (!taken) return uid;
            if (uid == std.math.maxInt(u32)) break;
        }
        return null;
    }
};
/// True when env var `key` equals "1". Shared by install + launch for the
/// ISAKAI_* flag checks (ISAKAI_NO_PRIVILEGE, ISAKAI_NONINTERACTIVE,
/// ISAKAI_WINDOW, ISAKAI_ALLOW_UNSIGNED).
pub fn envFlag(env: *const std.process.Environ.Map, key: []const u8) bool {
    const v = env.get(key) orelse return false;
    return std.mem.eql(u8, v, "1");
}
/// Wait and require exit code 0; any signal/stop/nonzero is HelperFailed.
/// Shared by uid_helper.runCmd's tail; the registry test spawn keeps its
/// own inline wait. Lives here (not launch) so uid_helper can use it
/// without a module cycle.
pub fn waitOk(child: *std.process.Child, io: std.Io) !void {
    const term = try child.wait(io);
    switch (term) {
        .exited => |c| if (c != 0) return error.HelperFailed,
        else => return error.HelperFailed,
    }
}
/// Canonical data-dir anchor work/data/<package>. Shared by install (which
/// creates it) and uid_helper.validateDataDir + runAs (which require an
/// exact match). Caller owns the result.
pub fn expectDataDir(gpa: std.mem.Allocator, work: []const u8, package: []const u8) ![]u8 {
    return std.fmt.allocPrint(gpa, "{s}/data/{s}", .{ work, package });
}

/// True when the pid names a live process. ESRCH = dead; success/EPERM =
/// alive. Local copy so terminatePid (shared by launch's stale-reaper and
/// install's uninstall guard) lives in the one module both already import.
pub fn isAlive(pid: u32) bool {
    if (pid == 0) return false;
    std.posix.kill(@intCast(pid), @enumFromInt(0)) catch |err| switch (err) {
        error.ProcessNotFound => return false,
        else => return true,
    };
    return true;
}

/// Default SIGTERM grace period for terminatePid, in milliseconds.
pub const default_terminate_timeout_ms: u64 = 5000;

/// Gracefully stop `pid`, escalating to SIGKILL on timeout.
///
/// Shared process-kill helper for the guarded uninstall path:
/// uninstall must not delete a live app's code+data. State view:
/// running --terminate--> (process dead, registry still .running) -->
/// caller marks .removing --> gone; kill failure keeps the old state and
/// returns an error so the caller aborts instead of deleting.
///
/// Never kills pid 0/1/self: returns error.Refused. An already-dead pid
/// returns success immediately. `.starting` entries carry no pid, so
/// callers skip the call entirely when there is nothing to kill.
///
/// Flow: SIGTERM, poll isAlive every ~50ms up to `timeout_ms`, then
/// SIGKILL + a brief re-poll; error.Timeout if still alive. A pid that
/// died but was never reaped (a zombie child of the caller) still reads
/// alive and therefore times out: pass only pids you do not own.
pub const TerminateError = error{
    Refused,
    Timeout,
} || std.posix.KillError || std.Io.Cancelable;

pub fn terminatePid(io: std.Io, pid: u32, timeout_ms: u64) TerminateError!void {
    return terminateInner(io, pid, null, timeout_ms);
}

/// Checked terminate for destructive paths (uninstall): `expected_started`
/// is the launch epoch recorded in the registry. Refuses when /proc shows
/// the pid started after that epoch (a recycled pid), or when the occupant
/// changes mid-kill; 0 skips the epoch check (pre-identity entries) but
/// still guards the kill sequence against recycling.
pub fn terminatePidChecked(io: std.Io, pid: u32, expected_started: u64, timeout_ms: u64) TerminateError!void {
    return terminateInner(io, pid, expected_started, timeout_ms);
}

/// Clock ticks per second for /proc starttime conversion (Linux USER_HZ).
const proc_clk_tck: u64 = 100;
/// Slack comparing a recorded launch epoch against the /proc-derived start
/// time: epoch truncation plus boot-time granularity.
const proc_start_slack_s: u64 = 120;

/// Start time of `pid` in clock ticks since boot (/proc/<pid>/stat field
/// 22). Null when the pid is gone or unreadable.
fn procStartTicks(io: std.Io, pid: u32) ?u64 {
    var pathbuf: [64]u8 = undefined;
    const path = std.fmt.bufPrint(&pathbuf, "/proc/{d}/stat", .{pid}) catch return null;
    var file = std.Io.Dir.openFileAbsolute(io, path, .{ .mode = .read_only }) catch return null;
    defer file.close(io);
    var rbuf: [512]u8 = undefined;
    var reader = file.reader(io, &rbuf);
    var sbuf: [1024]u8 = undefined;
    const n = reader.interface.readSliceShort(&sbuf) catch return null;
    const data = sbuf[0..n];
    // comm may contain spaces/parens: fields start after the last ')'.
    const end = std.mem.lastIndexOfScalar(u8, data, ')') orelse return null;
    var it = std.mem.splitScalar(u8, data[end + 1 ..], ' ');
    var i: usize = 0;
    while (it.next()) |tok| {
        if (tok.len == 0) continue;
        i += 1;
        if (i == 20) return std.fmt.parseInt(u64, tok, 10) catch null;
    }
    return null;
}

/// Boot time (epoch seconds) from /proc/stat. Null when unreadable.
fn procBootTime(io: std.Io) ?u64 {
    var file = std.Io.Dir.openFileAbsolute(io, "/proc/stat", .{ .mode = .read_only }) catch return null;
    defer file.close(io);
    var rbuf: [256]u8 = undefined;
    var reader = file.reader(io, &rbuf);
    var line: [64]u8 = undefined;
    var len: usize = 0;
    var overflowed = false;
    while (true) {
        const byte = reader.interface.takeByte() catch return null;
        if (byte == '\n') {
            if (!overflowed and std.mem.startsWith(u8, line[0..len], "btime ")) {
                return std.fmt.parseInt(u64, line[0..len]["btime ".len..], 10) catch null;
            }
            len = 0;
            overflowed = false;
        } else if (!overflowed) {
            if (len < line.len) {
                line[len] = byte;
                len += 1;
            } else overflowed = true;
        }
    }
}

/// True while `pid` still names the process that started at `ticks`.
fn sameProc(io: std.Io, pid: u32, ticks: u64) bool {
    const cur = procStartTicks(io, pid) orelse return false;
    return cur == ticks;
}

/// fsync the directory containing `path` so a just-completed rename is
/// durable before returning.
fn fsyncParentDir(path: []const u8) !void {
    const dir = std.fs.path.dirname(path) orelse ".";
    const linux = std.os.linux;
    var zbuf: [4096]u8 = undefined;
    if (dir.len + 1 > zbuf.len) return error.NameTooLong;
    @memcpy(zbuf[0..dir.len], dir);
    zbuf[dir.len] = 0;
    const z: [*:0]const u8 = @ptrCast(&zbuf);
    const rc = linux.openat(linux.AT.FDCWD, z, .{ .DIRECTORY = true, .CLOEXEC = true }, 0);
    if (linux.errno(rc) != .SUCCESS) return error.OpenFailed;
    const fd: linux.fd_t = @bitCast(@as(u32, @truncate(rc)));
    defer _ = linux.close(fd);
    if (linux.errno(linux.fsync(fd)) != .SUCCESS) return error.SyncFailed;
}

fn terminateInner(io: std.Io, pid: u32, expected_started: ?u64, timeout_ms: u64) TerminateError!void {
    const self: u32 = @intCast(std.posix.system.getpid());
    if (pid <= 1 or pid == self) return error.Refused;
    // Confirm identity before signaling: sample the /proc start time and
    // require the same occupant for the whole sequence.
    const start = procStartTicks(io, pid);
    if (start == null and !isAlive(pid)) return;
    const t0 = start orelse return error.Refused;
    if (expected_started) |exp| {
        if (exp != 0) {
            const btime = procBootTime(io) orelse return error.Refused;
            if (btime +| t0 / proc_clk_tck > exp +| proc_start_slack_s) return error.Refused;
        }
    }
    if (!isAlive(pid)) return;
    std.posix.kill(@intCast(pid), .TERM) catch |err| switch (err) {
        error.ProcessNotFound => return,
        else => return err,
    };
    if (try awaitGone(io, pid, t0, timeout_ms)) return;
    // Re-confirm before escalating: never SIGKILL a recycled occupant.
    if (!sameProc(io, pid, t0)) {
        if (!isAlive(pid)) return;
        return error.Refused;
    }
    std.posix.kill(@intCast(pid), .KILL) catch |err| switch (err) {
        error.ProcessNotFound => return,
        else => return err,
    };
    if (try awaitGone(io, pid, t0, kill_recheck_ms)) return;
    return error.Timeout;
}

const terminate_poll_ms: u64 = 50;
const kill_recheck_ms: u64 = 500;

/// Poll isAlive every ~50ms up to budget_ms; true once the pid is gone.
/// Refuses when the occupant changes mid-wait (pid recycled under us).
fn awaitGone(io: std.Io, pid: u32, ticks: u64, budget_ms: u64) TerminateError!bool {
    var waited: u64 = 0;
    while (waited < budget_ms) {
        const step: u64 = @min(terminate_poll_ms, budget_ms - waited);
        try std.Io.sleep(io, .fromMilliseconds(@as(i64, @intCast(step))), .awake);
        if (!isAlive(pid)) return true;
        if (!sameProc(io, pid, ticks)) return error.Refused;
        waited += step;
    }
    return !isAlive(pid);
}

