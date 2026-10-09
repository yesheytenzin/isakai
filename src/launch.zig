// SPDX-License-Identifier: GPL-3.0-or-later

//! App launch: translate DEX at install time, spawn the host JVM, reap it,
//! record the outcome in the registry state machine.

const std = @import("std");
const dex = @import("dex");
const translate = @import("translate");
const registry = @import("registry");
const install = @import("install");

/// Translate all classes.dex/classesN.dex in the staged code dir to JVM
/// classes. Writes into <code_dir>/classes/. Idempotent: skips if the
/// marker (combined digest list) matches.
///
/// Enumeration follows Android: `classes.dex` first, then N=2,3,… ascending,
/// stopping at the first gap. Each dex is validated by magic (not name) and
/// parsed with its own ID namespace. Duplicate class names across dexes are
/// a loud error (first-wins would hide split-brain input).
pub fn translateApp(
    io: std.Io,
    gpa: std.mem.Allocator,
    code_dir: []const u8,
) anyerror!void {
    const classes_dir = try std.fmt.allocPrint(gpa, "{s}/classes", .{code_dir});
    defer gpa.free(classes_dir);
    const marker_path = try std.fmt.allocPrint(gpa, "{s}/.translated", .{classes_dir});
    defer gpa.free(marker_path);

    // Enumerate dex files: classes.dex, classes2.dex, ... (stop at gap).
    var dex_names: std.ArrayListUnmanaged([]const u8) = .empty;
    defer {
        for (dex_names.items) |n| gpa.free(n);
        dex_names.deinit(gpa);
    }
    try dex_names.append(gpa, try gpa.dupe(u8, "classes.dex"));
    var n: u32 = 2;
    while (true) : (n += 1) {
        const cand = try std.fmt.allocPrint(gpa, "classes{d}.dex", .{n});
        defer gpa.free(cand);
        const full = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ code_dir, cand });
        defer gpa.free(full);
        if (std.Io.Dir.cwd().statFile(io, full, .{})) |_| {
            try dex_names.append(gpa, try gpa.dupe(u8, cand));
        } else |_| break;
        if (n > 1000) break; // sanity cap
    }

    // Marker: newline-joined per-dex digests; skip when unchanged.
    var marker: std.ArrayListUnmanaged(u8) = .empty;
    defer marker.deinit(gpa);
    for (dex_names.items) |dn| {
        const full = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ code_dir, dn });
        defer gpa.free(full);
        const dg = try install.sha256File(io, gpa, full);
        defer gpa.free(dg);
        try marker.appendSlice(gpa, dg);
        try marker.append(gpa, '\n');
    }
    if (std.Io.Dir.cwd().readFileAlloc(io, marker_path, gpa, .limited(65536))) |old| {
        defer gpa.free(old);
        if (std.mem.eql(u8, old, marker.items)) return;
        try std.Io.Dir.cwd().deleteTree(io, classes_dir);
    } else |_| {}
    try std.Io.Dir.cwd().createDirPath(io, classes_dir);

    var seen: std.StringHashMapUnmanaged(void) = .empty;
    defer {
        var sit = seen.iterator();
        while (sit.next()) |e| gpa.free(e.key_ptr.*);
        seen.deinit(gpa);
    }
    for (dex_names.items) |dn| {
        const dex_path = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ code_dir, dn });
        defer gpa.free(dex_path);
        const bytes = try std.Io.Dir.cwd().readFileAlloc(io, dex_path, gpa, .limited(install.max_entry));
        defer gpa.free(bytes);
        const header = try dex.parseHeader(bytes);
        const d = dex.Dex{ .data = bytes, .header = header };
        for (0..header.class_defs_size) |i| {
            var tc = try translate.translateClass(gpa, d, i);
            defer tc.deinit(gpa);
            const gop = try seen.getOrPut(gpa, tc.internal_name);
            if (gop.found_existing) {
                std.log.err("duplicate class {s} in {s} (already translated)", .{ tc.internal_name, dn });
                return error.DuplicateClass;
            }
            gop.key_ptr.* = try gpa.dupe(u8, tc.internal_name);
            const out_path = try std.fmt.allocPrint(gpa, "{s}/{s}.class", .{ classes_dir, tc.internal_name });
            defer gpa.free(out_path);
            if (std.fs.path.dirname(out_path)) |dir| {
                try std.Io.Dir.cwd().createDirPath(io, dir);
                // Translated dirs must stay traversable by the dropped app
                // user (privileged runs); harmless headless no-privilege.
                try std.Io.Dir.cwd().setFilePermissions(io, dir, .fromMode(0o755), .{});
            }
            var f = try std.Io.Dir.cwd().createFile(io, out_path, .{});
            defer f.close(io);
            var buf: [8192]u8 = undefined;
            var fw = f.writer(io, &buf);
            try fw.interface.writeAll(tc.bytes);
            try fw.interface.flush();
            // Translated classes are read-only inputs to the JVM: 0644 so
            // the app user can read them under a privileged launch.
            try std.Io.Dir.cwd().setFilePermissions(io, out_path, .fromMode(0o644), .{});
        }
    }
    var mf = try std.Io.Dir.cwd().createFile(io, marker_path, .{});
    defer mf.close(io);
    var mbuf: [8192]u8 = undefined;
    var mfw = mf.writer(io, &mbuf);
    try mfw.interface.writeAll(marker.items);
    try mfw.interface.flush();
}

/// Parse a JEP 223 / legacy version string to its FEATURE number.
/// Handles `17`, `17.0.1`, `27+35`, `1.8.0_422` (legacy 1.MAJOR → 8).
pub fn parseJavaFeature(version_output: []const u8) ?u32 {
    // Find the first quoted version: openjdk version "17.0.1" ...
    const q1 = std.mem.indexOfScalar(u8, version_output, '"') orelse return null;
    const rest = version_output[q1 + 1 ..];
    const q2 = std.mem.indexOfScalar(u8, rest, '"') orelse return null;
    const v = rest[0..q2];
    if (std.mem.startsWith(u8, v, "1.")) {
        // Legacy: 1.8.x → 8.
        var parts = std.mem.splitScalar(u8, v[2..], '.');
        const minor = parts.next() orelse return null;
        return std.fmt.parseInt(u32, minor, 10) catch null;
    }
    var parts = std.mem.splitAny(u8, v, ".-+_");
    const feature = parts.next() orelse return null;
    return std.fmt.parseInt(u32, feature, 10) catch null;
}

/// Run `<java> -version`, return its FEATURE number, or null when the
/// candidate is missing/broken/unparseable. Never fails hard.
fn javaFeature(io: std.Io, gpa: std.mem.Allocator, java: []const u8) ?u32 {
    var child = std.process.spawn(io, .{
        .argv = &.{ java, "-version" },
        .stdin = .ignore,
        .stdout = .pipe,
        .stderr = .pipe,
    }) catch return null;
    // -version prints to stderr; read it fully (small, bounded).
    const text = captureBounded(&child, io, gpa);
    defer if (text.len > 0) gpa.free(text);
    _ = child.wait(io) catch return null;
    return parseJavaFeature(text);
}

pub const min_java_feature: u32 = 17;

/// True when every component of an absolute path grants other-execute
/// (dirs) / other-read+execute (file), so a dropped-privilege child can
/// traverse+read it. Checks MODE BITS, not our own access(): we can
/// traverse our own 0700 home, but the app user cannot. Relative paths
/// return false — callers must resolve them first.
fn worldTraversable(path: []const u8) bool {
    const linux = std.os.linux;
    if (path.len == 0 or path[0] != '/') return false;
    var i: usize = 1;
    while (i <= path.len) : (i += 1) {
        if (i == path.len or path[i] == '/') {
            const prefix = path[0..i];
            var zbuf: [4096]u8 = undefined;
            if (prefix.len >= zbuf.len) return false;
            @memcpy(zbuf[0..prefix.len], prefix);
            zbuf[prefix.len] = 0;
            var stx: std.os.linux.Statx = undefined;
            const rc = linux.statx(linux.AT.FDCWD, @ptrCast(&zbuf), 0, .{ .TYPE = true, .MODE = true }, &stx);
            if (linux.errno(rc) != .SUCCESS) return false;
            const is_last = i == path.len;
            const is_dir = stx.mode & linux.S.IFMT == linux.S.IFDIR;
            // Dirs (including intermediate): need o+x. Final file: o+r.
            // Final executable (java binary itself): needs o+x too — the
            // caller checks execute separately via a direct exec probe.
            const need: u16 = if (!is_last or is_dir) linux.S.IXOTH else linux.S.IROTH;
            if (stx.mode & need != need) return false;
        }
    }
    return true;
}

/// Ordered JVM discovery: $JAVA_HOME/bin/java → PATH `java` →
/// /usr/lib/jvm/*/bin/java (highest valid feature wins). Every candidate
/// is validated by executing `-version`; the first with feature >= 17 wins,
/// except the /usr/lib/jvm scan which prefers the highest version.
/// When `privileged` is true, candidates under non-traversable paths
/// (e.g. a 0700 home) are skipped: the dropped child couldn't exec them.
pub fn findJava(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    privileged: bool,
) !?[]u8 {
    // Privileged runs need world-traversable java binaries (o+x on every
    // component including the file itself).
    const worldExec = struct {
        fn f(path: []const u8) bool {
            const linux = std.os.linux;
            if (!worldTraversable(path)) return false;
            var zbuf: [4096]u8 = undefined;
            if (path.len >= zbuf.len) return false;
            @memcpy(zbuf[0..path.len], path);
            zbuf[path.len] = 0;
            var stx: linux.Statx = undefined;
            const rc = linux.statx(linux.AT.FDCWD, @ptrCast(&zbuf), 0, .{ .MODE = true }, &stx);
            if (linux.errno(rc) != .SUCCESS) return false;
            return stx.mode & linux.S.IXOTH != 0;
        }
    }.f;
    if (env.get("JAVA_HOME")) |jh| {
        const p = try std.fmt.allocPrint(gpa, "{s}/bin/java", .{jh});
        errdefer gpa.free(p);
        const ok = !privileged or worldExec(p);
        if (ok) {
            if (javaFeature(io, gpa, p)) |f| {
                if (f >= min_java_feature) return p;
            }
        }
        gpa.free(p);
    }
    if (!privileged) {
        // Bare PATH lookup only for unprivileged runs: the dropped child
        // gets a minimal PATH and couldn't resolve this anyway.
        const p = try gpa.dupe(u8, "java");
        errdefer gpa.free(p);
        if (javaFeature(io, gpa, p)) |f| {
            if (f >= min_java_feature) return p;
        }
        gpa.free(p);
    }
    // Scan /usr/lib/jvm for the highest valid version.
    var jvm_dir = std.Io.Dir.cwd().openDir(io, "/usr/lib/jvm", .{ .iterate = true }) catch return null;
    defer jvm_dir.close(io);
    var best: ?[]u8 = null;
    var best_feature: u32 = 0;
    var it = jvm_dir.iterate();
    while (it.next(io) catch null) |entry| {
        if (entry.kind != .directory) continue;
        const cand = std.fmt.allocPrint(gpa, "/usr/lib/jvm/{s}/bin/java", .{entry.name}) catch continue;
        defer gpa.free(cand);
        if (privileged and !worldExec(cand)) continue;
        const f = javaFeature(io, gpa, cand) orelse continue;
        if (f >= min_java_feature and f > best_feature) {
            if (best) |b| gpa.free(b);
            best = gpa.dupe(u8, cand) catch continue;
            best_feature = f;
        }
    }
    return best;
}
/// Assemble the JVM argv for an app launch. Owns the returned slice (not
/// the strings, which are borrowed from the caller). Add --enable-native-
/// access unconditionally: JEP 412 (strong encapsulation of JDK internals,
/// 17) and JEP 472 (restricted native access warnings, 23+) mean the JNI
/// bridge's direct-buffer path needs explicit native permission on modern
/// JDKs; passing it always keeps one argv shape across JDK 17..27.
pub fn buildJvmArgv(
    gpa: std.mem.Allocator,
    java: []const u8,
    lib_path: []const u8,
    windowed_prop: []const u8,
    cp: []const u8,
    data_dir: []const u8,
    app_arg: []const u8,
    act_arg: []const u8,
    pkg_name: []const u8,
    title: []const u8,
) ![][]const u8 {
    const argv = try gpa.alloc([]const u8, 12);
    argv[0] = java;
    argv[1] = "--enable-native-access=ALL-UNNAMED";
    argv[2] = lib_path;
    argv[3] = windowed_prop;
    argv[4] = "-cp";
    argv[5] = cp;
    argv[6] = "org.isakai.runtime.IsakaiMain";
    argv[7] = data_dir;
    argv[8] = app_arg;
    argv[9] = act_arg;
    argv[10] = pkg_name;
    argv[11] = title;
    return argv;
}

/// Spawn with fully inherited stdio. Collapses the 3 identical
/// spawnViaHelper/no-priv spawn sites.
pub fn spawnInherit(io: std.Io, argv: []const []const u8) !std.process.Child {
    return std.process.spawn(io, .{
        .argv = argv,
        .stdin = .inherit,
        .stdout = .inherit,
        .stderr = .inherit,
    });
}

/// empty slice on allocation failure. Caller frees nonempty results.
pub fn captureBounded(child: *std.process.Child, io: std.Io, gpa: std.mem.Allocator) []u8 {
    var err_buf: [4096]u8 = undefined;
    if (child.stderr) |*f| {
        var r = f.reader(io, &err_buf);
        return r.interface.allocRemaining(gpa, .limited(4096)) catch &.{};
    }
    return &.{};
}

pub const RunResult = struct {
    code: ?i32,
};

/// Spawn the JVM as the app user: elevate `isakai uid-helper run ...`
/// via pkexec (or sudo -n when noninteractive). Stdio inherits so app
/// output streams to the caller's terminal.
fn spawnViaHelper(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    exe: []const u8,
    pkg: *const registry.Package,
    work: []const u8,
    jvm_argv: []const []const u8,
) !std.process.Child {
    const uid_s = try std.fmt.allocPrint(gpa, "{d}", .{pkg.uid});
    defer gpa.free(uid_s);
    const noninteractive = registry.envFlag(env, "ISAKAI_NONINTERACTIVE");
    var argv: std.ArrayListUnmanaged([]const u8) = .empty;
    defer argv.deinit(gpa);
    if (!noninteractive) {
        // pkexec if present, else fall back to sudo -n on spawn failure.
        try argv.appendSlice(gpa, &.{ "/usr/bin/pkexec", exe, "uid-helper", "run", pkg.name, uid_s, pkg.data_dir, work, "--" });
        try argv.appendSlice(gpa, jvm_argv);
        return spawnInherit(io, argv.items) catch |err| switch (err) {
            error.FileNotFound => {
                argv.clearRetainingCapacity();
                try argv.appendSlice(gpa, &.{ "/usr/bin/sudo", "-n", exe, "uid-helper", "run", pkg.name, uid_s, pkg.data_dir, work, "--" });
                try argv.appendSlice(gpa, jvm_argv);
                return spawnInherit(io, argv.items);
            },
            else => return err,
        };
    }
    try argv.appendSlice(gpa, &.{ "/usr/bin/sudo", "-n", exe, "uid-helper", "run", pkg.name, uid_s, pkg.data_dir, work, "--" });
    try argv.appendSlice(gpa, jvm_argv);
    return spawnInherit(io, argv.items);
}

/// Restore .installed after a launch failure and persist, ignoring
/// persistence errors (the caller already has a real error to return).
/// Collapses the 4 best-effort remove/add/save rollback idioms.
pub fn rollbackToInstalled(reg: *registry.Registry, io: std.Io, gpa: std.mem.Allocator, work: []const u8, pkg: registry.Package) void {
    var p = pkg;
    p.state = .installed;
    _ = reg.remove(p.name);
    reg.add(p) catch {};
    reg.save(io, gpa, work) catch {};
}

/// Launch an installed app headless: translate, spawn JVM, wait, record state.
/// `framework_dir` holds compiled android.* + IsakaiMain classes.
/// `app_class`/`activity_class` override manifest values (empty = manifest).
pub fn launch(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    exe: []const u8,
    work: []const u8,
    selector: []const u8,
    framework_dir: []const u8,
) anyerror!RunResult {
    // Check-and-mark-starting under the exclusive lock; the lock is NOT
    // held across the child run (that would serialize launches and queries).
    var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
    var reg = try registry.Registry.loadChecked(io, gpa, work);
    defer reg.deinit();
    var pkg = try reg.resolve(gpa, selector);
    switch (pkg.state) {
        .running => |r| {
            // Reap stale `running`: a dead pid means the launcher crashed
            // without recording the exit. Demote to installed under the
            // same lock and continue; a live pid still blocks relaunch.
            if (!registry.isAlive(r.pid)) {
                std.log.warn("reaping stale running state for {s} (pid {d} dead)", .{ pkg.name, r.pid });
                pkg.state = .installed;
                try reg.updateState(io, gpa, work, pkg);
            } else {
                lock.release();
                return error.AlreadyRunning;
            }
        },
        else => {},
    }
    // Manifest gives app/activity classes.
    const manifest_path = try std.fmt.allocPrint(gpa, "{s}/AndroidManifest.xml", .{pkg.apk});
    defer gpa.free(manifest_path);
    const mbytes = try std.Io.Dir.cwd().readFileAlloc(io, manifest_path, gpa, .limited(install.max_manifest));
    defer gpa.free(mbytes);
    var m = try install.parseManifest(gpa, mbytes);
    defer install.freeManifest(gpa, &m);

    try translateApp(io, gpa, pkg.apk);

    // Mark starting.
    pkg.state = .starting;
    pkg.launch_id += 1;
    try reg.updateState(io, gpa, work, pkg);

    const no_priv = registry.envFlag(env, "ISAKAI_NO_PRIVILEGE");
    const java = (try findJava(io, gpa, env, !no_priv)) orelse return error.NoJava;
    defer gpa.free(java);
    const classes_dir = try std.fmt.allocPrint(gpa, "{s}/classes", .{pkg.apk});
    defer gpa.free(classes_dir);
    const cp = try std.fmt.allocPrint(gpa, "{s}:{s}", .{ classes_dir, framework_dir });
    defer gpa.free(cp);
    if (!no_priv) {
        // The dropped child must traverse the framework + staged classes.
        // Fail here with a clear error instead of a JVM classpath failure.
        if (!worldTraversable(framework_dir)) {
            std.log.err("framework dir not traversable by app user: {s}", .{framework_dir});
            std.log.err("hint: install the framework outside any 0700 home, or set ISAKAI_FRAMEWORK", .{});
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return error.FrameworkUnreachable;
        }
        if (!worldTraversable(classes_dir)) {
            std.log.err("staged classes not traversable by app user: {s}", .{classes_dir});
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return error.FrameworkUnreachable;
        }
    }
    const app_arg = m.application orelse "-";
    const act_arg = m.launcher orelse "-";
    const title_arg = install.manifestTitle(&m);
    const windowed: bool = registry.envFlag(env, "ISAKAI_WINDOW");
    if (windowed and !no_priv) {
        // Privileged windowed launches cannot reach the user's Wayland
        // socket yet (per-app XDG_RUNTIME_DIR is fresh/empty): run with
        // ISAKAI_NO_PRIVILEGE=1 for now, e.g.
        // ISAKAI_WINDOW=1 ISAKAI_NO_PRIVILEGE=1 isakai launch <pkg>.
        std.log.err("windowed launch needs ISAKAI_NO_PRIVILEGE=1 for now (no Wayland socket in the app sandbox)", .{});
    }
    const lib_path = try std.fmt.allocPrint(gpa, "-Djava.library.path={s}/lib", .{framework_dir});
    defer gpa.free(lib_path);
    const windowed_prop = if (windowed) "-Disakai.windowed=true" else "-Disakai.windowed=false";
    const jvm_argv = try buildJvmArgv(gpa, java, lib_path, windowed_prop, cp, pkg.data_dir, app_arg, act_arg, pkg.name, title_arg);
    defer gpa.free(jvm_argv);
    // Privileged path: run the JVM as the app user via the helper.
    // ISAKAI_NO_PRIVILEGE=1 runs it directly as ourselves (dev/test).
    var child: std.process.Child = undefined;
    if (no_priv) {
        child = spawnInherit(io, jvm_argv) catch |err| {
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return err;
        };
    } else {
        child = spawnViaHelper(io, gpa, env, exe, &pkg, work, jvm_argv) catch |err| {
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return err;
        };
    }
    // P0.2: record the real child PID.
    const pid: u32 = if (child.id) |id| @intCast(id) else 0;

    // Mark running (still under the starting lock).
    pkg.state = .{ .running = .{ .pid = pid, .started = install.nowSeconds(io) } };
    try reg.updateState(io, gpa, work, pkg);
    lock.release();

    const term = try child.wait(io);
    const code: ?i32 = switch (term) {
        .exited => |c| @intCast(c),
        .signal => null,
        .stopped => null,
        .unknown => null,
    };
    // Re-acquire to record the exit (state may have changed underneath).
    var lock2 = try registry.Registry.Lock.acquire(io, gpa, work, true);
    defer lock2.release();
    var reg2 = try registry.Registry.loadChecked(io, gpa, work);
    defer reg2.deinit();
    if (reg2.get(pkg.name)) |current| {
        var updated = current;
        // Only overwrite running/starting states we own (same launch_id).
        const ours = switch (updated.state) {
            .running, .starting => updated.launch_id == pkg.launch_id,
            else => false,
        };
        if (ours) {
            updated.state = .{ .exited = .{ .code = code } };
            try reg2.updateState(io, gpa, work, updated);
        }
    }
    return .{ .code = code };
}

test "parseJavaFeature handles JEP 223 and legacy" {
    try std.testing.expectEqual(@as(?u32, 17), parseJavaFeature("openjdk version \"17.0.1\" 2021-10-19\n"));
    try std.testing.expectEqual(@as(?u32, 27), parseJavaFeature("openjdk version \"27\" 2026-09-15\n"));
    try std.testing.expectEqual(@as(?u32, 21), parseJavaFeature("openjdk version \"21.0.3+9\" 2024-04-16\n"));
    try std.testing.expectEqual(@as(?u32, 8), parseJavaFeature("java version \"1.8.0_422\"\n"));
    try std.testing.expectEqual(@as(?u32, 11), parseJavaFeature("openjdk version \"11.0.24\" 2024-07-16\n"));
    try std.testing.expectEqual(@as(?u32, null), parseJavaFeature("garbage with no quotes"));
    try std.testing.expectEqual(@as(?u32, null), parseJavaFeature("openjdk version \"\" 2024-01-01\n"));
}

test "buildJvmArgv shape: native-access first, 5th IsakaiMain arg is title" {
    const argv = try buildJvmArgv(
        std.testing.allocator,
        "java",
        "-Djava.library.path=/fw/lib",
        "-Disakai.windowed=true",
        "/code/classes:/fw",
        "/data",
        "com.Foo.App",
        "com.Foo.Main",
        "com.foo",
        "Fixture",
    );
    defer std.testing.allocator.free(argv);
    try std.testing.expectEqual(@as(usize, 12), argv.len);
    try std.testing.expectEqualStrings("--enable-native-access=ALL-UNNAMED", argv[1]);
    try std.testing.expectEqualStrings("org.isakai.runtime.IsakaiMain", argv[6]);
    try std.testing.expectEqualStrings("com.foo", argv[10]);
    try std.testing.expectEqualStrings("Fixture", argv[11]);
}
