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
    installed_at: u64,
    state: AppState = .installed,
    signer_sha256: ?[]const u8 = null,
    launch_id: u64 = 0,
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
        const tmp_path = try std.fmt.allocPrint(gpa, "{s}.tmp", .{path});
        defer gpa.free(tmp_path);

        var file = try std.Io.Dir.cwd().createFile(io, tmp_path, .{});
        defer file.close(io);
        var buf: [4096]u8 = undefined;
        var file_writer = file.writer(io, &buf);
        const w = &file_writer.interface;
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
        try w.flush();
        try file.sync(io);
        try std.Io.Dir.renameAbsolute(tmp_path, path, io);
    }

    pub fn add(self: *Self, package: Package) !void {
        const alloc = self.allocator();
        const name = try alloc.dupe(u8, package.name);
        var pkg = package;
        pkg.name = name;
        pkg.data_dir = try alloc.dupe(u8, package.data_dir);
        pkg.apk = try alloc.dupe(u8, package.apk);
        if (pkg.signer_sha256) |s| pkg.signer_sha256 = try alloc.dupe(u8, s);
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
    const self: u32 = @intCast(std.posix.system.getpid());
    if (pid <= 1 or pid == self) return error.Refused;
    if (!isAlive(pid)) return;
    std.posix.kill(@intCast(pid), .TERM) catch |err| switch (err) {
        error.ProcessNotFound => return,
        else => return err,
    };
    if (try awaitGone(io, pid, timeout_ms)) return;
    std.posix.kill(@intCast(pid), .KILL) catch |err| switch (err) {
        error.ProcessNotFound => return,
        else => return err,
    };
    if (try awaitGone(io, pid, kill_recheck_ms)) return;
    return error.Timeout;
}

const terminate_poll_ms: u64 = 50;
const kill_recheck_ms: u64 = 500;

/// Poll isAlive every ~50ms up to budget_ms; true once the pid is gone.
fn awaitGone(io: std.Io, pid: u32, budget_ms: u64) TerminateError!bool {
    var waited: u64 = 0;
    while (waited < budget_ms) {
        const step: u64 = @min(terminate_poll_ms, budget_ms - waited);
        try std.Io.sleep(io, .fromMilliseconds(@as(i64, @intCast(step))), .awake);
        if (!isAlive(pid)) return true;
        waited += step;
    }
    return !isAlive(pid);
}

test "terminatePid: dead pid ok, pid 0/1/self refused" {
    const io = std.testing.io;
    try terminatePid(io, 1 << 30, 100);
    try std.testing.expectError(error.Refused, terminatePid(io, 0, 100));
    try std.testing.expectError(error.Refused, terminatePid(io, 1, 100));
    const self: u32 = @intCast(std.posix.system.getpid());
    try std.testing.expectError(error.Refused, terminatePid(io, self, 100));
}

test "terminatePid kills a live orphaned process" {
    // Orphan (reparented, not our child): pure kill(0) semantics, no
    // zombie-reaping skew. sh backgrounds sleep, prints its pid, exits.
    const io = std.testing.io;
    const gpa = std.testing.allocator;
    var child = try std.process.spawn(io, .{
        .argv = &.{ "/bin/sh", "-c", "sleep 30 & echo $!" },
        .stdin = .ignore,
        .stdout = .pipe,
        .stderr = .ignore,
    });
    var out_buf: [64]u8 = undefined;
    var pid: u32 = 0;
    if (child.stdout) |*f| {
        var r = f.reader(io, &out_buf);
        const text = try r.interface.allocRemaining(gpa, .limited(64));
        defer gpa.free(text);
        pid = try std.fmt.parseInt(u32, std.mem.trim(u8, text, " \t\r\n"), 10);
    } else return error.TestUnexpectedResult;
    _ = try child.wait(io);
    try std.testing.expect(pid > 1);
    try std.testing.expect(isAlive(pid));
    try terminatePid(io, pid, default_terminate_timeout_ms);
    try std.testing.expect(!isAlive(pid));
}

test "resolve prefers exact, then prefix, then substring" {
    const gpa = std.testing.allocator;
    var reg = Registry.init(gpa);
    defer reg.deinit();
    try reg.add(.{ .name = "org.example.notes", .uid = 5000, .data_dir = "/tmp/a", .apk = "/tmp/a.apk", .installed_at = 1 });
    try reg.add(.{ .name = "org.example.notes.pro", .uid = 5001, .data_dir = "/tmp/b", .apk = "/tmp/b.apk", .installed_at = 2 });
    try reg.add(.{ .name = "com.other.app", .uid = 5002, .data_dir = "/tmp/c", .apk = "/tmp/c.apk", .installed_at = 3 });

    // Exact wins even when it is also a prefix of another.
    try std.testing.expectEqualStrings("org.example.notes", (try reg.resolve(gpa, "org.example.notes")).name);
    // Unique prefix.
    try std.testing.expectEqualStrings("com.other.app", (try reg.resolve(gpa, "com.other")).name);
    // Ambiguous prefix.
    try std.testing.expectError(error.Ambiguous, reg.resolve(gpa, "org.example"));
    // Unique substring.
    try std.testing.expectEqualStrings("com.other.app", (try reg.resolve(gpa, "other")).name);
    // Case-insensitive.
    try std.testing.expectEqualStrings("com.other.app", (try reg.resolve(gpa, "COM.OTHER")).name);
    // Missing.
    try std.testing.expectError(error.NotFound, reg.resolve(gpa, "nope"));
}

test "nextUid skips taken uids" {
    const gpa = std.testing.allocator;
    var reg = Registry.init(gpa);
    defer reg.deinit();
    try reg.add(.{ .name = "a", .uid = 5000, .data_dir = "", .apk = "", .installed_at = 0 });
    try reg.add(.{ .name = "b", .uid = 5001, .data_dir = "", .apk = "", .installed_at = 0 });
    try std.testing.expectEqual(@as(?u32, 5002), reg.nextUid(5000, 5999));
    try std.testing.expectEqual(@as(?u32, null), reg.nextUid(5000, 5001));
}

fn tmpWorkDir(io: std.Io, gpa: std.mem.Allocator) !struct {
    tmp: std.testing.TmpDir,
    work: []u8,
} {
    var tmp = std.testing.tmpDir(.{});
    errdefer tmp.cleanup();
    // TmpDir lives under .zig-cache/tmp/<sub>; resolve the absolute path.
    const cwd_path = try std.process.currentPathAlloc(io, gpa);
    defer gpa.free(cwd_path);
    const work = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}", .{ cwd_path, &tmp.sub_path });
    return .{ .tmp = tmp, .work = work };
}

test "save and load round-trip" {
    const gpa = std.testing.allocator;
    const io = std.testing.io;
    var t = try tmpWorkDir(io, gpa);
    defer t.tmp.cleanup();
    defer gpa.free(t.work);

    var reg = Registry.init(gpa);
    defer reg.deinit();
    try reg.add(.{
        .name = "org.example.notes",
        .uid = 5000,
        .data_dir = "/tmp/notes",
        .apk = "/tmp/notes.apk",
        .installed_at = 42,
        .state = .{ .running = .{ .pid = 123, .started = 7 } },
        .signer_sha256 = "ab12",
        .launch_id = 3,
    });
    try reg.save(io, gpa, t.work);

    var loaded = try Registry.loadChecked(io, gpa, t.work);
    defer loaded.deinit();
    try std.testing.expectEqual(@as(usize, 1), loaded.packages.count());
    const pkg = loaded.get("org.example.notes").?;
    try std.testing.expectEqual(@as(u32, 5000), pkg.uid);
    try std.testing.expectEqual(@as(u64, 3), pkg.launch_id);
    try std.testing.expectEqualStrings("ab12", pkg.signer_sha256.?);
    switch (pkg.state) {
        .running => |r| {
            try std.testing.expectEqual(@as(u32, 123), r.pid);
            try std.testing.expectEqual(@as(u64, 7), r.started);
        },
        else => return error.TestUnexpectedResult,
    }
}

test "corrupt registry fails checked load" {
    const gpa = std.testing.allocator;
    const io = std.testing.io;
    var t = try tmpWorkDir(io, gpa);
    defer t.tmp.cleanup();
    defer gpa.free(t.work);
    try std.Io.Dir.cwd().createDirPath(io, t.work);
    const path = try std.fmt.allocPrint(gpa, "{s}/registry.json", .{t.work});
    defer gpa.free(path);
    var f = try std.Io.Dir.cwd().createFile(io, path, .{});
    defer f.close(io);
    var buf: [64]u8 = undefined;
    var fw = f.writer(io, &buf);
    try fw.interface.writeAll("not json{{{");
    try fw.interface.flush();

    if (Registry.loadChecked(io, gpa, t.work)) |*loaded| {
        var owned = loaded.*;
        owned.deinit();
        return error.TestUnexpectedResult;
    } else |_| {
        // Any parse error is the expected outcome.
    }
}
