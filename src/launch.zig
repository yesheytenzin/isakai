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
        // Per-dex arena: translation does millions of small allocs;
        // a bump allocator is an order of magnitude faster than the
        // shared GPA here (all per-class state dies with the dex).
        var arena_state = std.heap.ArenaAllocator.init(gpa);
        defer arena_state.deinit();
        const aa = arena_state.allocator();
        const dex_path = try std.fmt.allocPrint(aa, "{s}/{s}", .{ code_dir, dn });
        const bytes = try std.Io.Dir.cwd().readFileAlloc(io, dex_path, aa, .limited(install.max_entry));
        const header = try dex.parseHeader(bytes);
        const d = dex.Dex{ .data = bytes, .header = header };
        var fwd = translate.ForwardNeeds{};
        defer fwd.deinit(aa);
        for (0..header.class_defs_size) |i| {
            var tc = try translate.translateClass(aa, d, i, &fwd, &.{});
            defer tc.deinit(aa);
            const gop = try seen.getOrPut(gpa, tc.internal_name);
            if (gop.found_existing) {
                std.log.err("duplicate class {s} in {s} (already translated)", .{ tc.internal_name, dn });
                return error.DuplicateClass;
            }
            gop.key_ptr.* = try gpa.dupe(u8, tc.internal_name);
            const out_path = try std.fmt.allocPrint(aa, "{s}/{s}.class", .{ classes_dir, tc.internal_name });
            defer aa.free(out_path);
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
        // Second pass: inherited-ctor forwards (JVM resolves <init>
        // without a superclass search, so children reached via new+init
        // must declare every invoked proto). Re-translate affected
        // classes with synthesized forwards and overwrite.
        if (fwd.needs.items.len > 0) {
            var plan = try translate.planInheritedForwards(aa, d, &fwd);
            defer {
                var pit = plan.iterator();
                while (pit.next()) |e| {
                    for (e.value_ptr.items) |ed| aa.free(ed);
                    e.value_ptr.deinit(aa);
                    aa.free(e.key_ptr.*);
                }
                plan.deinit(aa);
            }
            var pit = plan.iterator();
            while (pit.next()) |e| {
                const found = translate.dexDefIndexByInternal(d, aa, e.key_ptr.*) orelse continue;
                var tc = try translate.translateClass(aa, d, found, &fwd, e.value_ptr.items);
                defer tc.deinit(aa);
                const out_path = try std.fmt.allocPrint(aa, "{s}/{s}.class", .{ classes_dir, tc.internal_name });
                defer aa.free(out_path);
                var f = try std.Io.Dir.cwd().createFile(io, out_path, .{});
                defer f.close(io);
                var buf: [8192]u8 = undefined;
                var fw = f.writer(io, &buf);
                try fw.interface.writeAll(tc.bytes);
                try fw.interface.flush();
                try std.Io.Dir.cwd().setFilePermissions(io, out_path, .fromMode(0o644), .{});
            }
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
    code_dir: []const u8,
    app_theme: []const u8,
    act_theme: []const u8,
) ![][]const u8 {
    const argv = try gpa.alloc([]const u8, 15);
    argv[0] = java;
    argv[1] = "--enable-native-access=ALL-UNNAMED";
    argv[2] = lib_path;
    argv[3] = windowed_prop;
    argv[4] = try std.fmt.allocPrint(gpa, "-Disakai.codeDir={s}", .{code_dir});
    argv[5] = "-cp";
    argv[6] = cp;
    argv[7] = "org.isakai.runtime.IsakaiMain";
    argv[8] = data_dir;
    argv[9] = app_arg;
    argv[10] = act_arg;
    argv[11] = pkg_name;
    argv[12] = title;
    argv[13] = app_theme;
    argv[14] = act_theme;
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

/// Bridge/stage dir anchors: work/run/<package> + work/stage/<package>.
/// Caller owns both results.
pub fn bridgeDirs(gpa: std.mem.Allocator, work: []const u8, package: []const u8) !struct { bridge: []u8, stage: []u8 } {
    const bridge = try std.fmt.allocPrint(gpa, "{s}/run/{s}", .{ work, package });
    errdefer gpa.free(bridge);
    const stage = try std.fmt.allocPrint(gpa, "{s}/stage/{s}", .{ work, package });
    return .{ .bridge = bridge, .stage = stage };
}

/// Pre-bind the bridge + stage dirs. Creates work/run + work/stage parents.
/// run/<pkg> is launcher-owned 0711: the daemon binds there, and the
/// sandboxed app uid traverses (x-only, no listing) to reach the socket
/// (SO_PEERCRED gates clients). stage/<pkg> is 0700; the privileged
/// helper chowns it to the app uid at install (app writes transfers).
pub fn prebindBridgeDirs(io: std.Io, bridge_dir: []const u8, stage_dir: []const u8) void {
    if (std.fs.path.dirname(bridge_dir)) |parent| {
        std.Io.Dir.cwd().createDirPath(io, parent) catch {};
    }
    std.Io.Dir.cwd().createDirPath(io, bridge_dir) catch {};
    std.Io.Dir.cwd().setFilePermissions(io, bridge_dir, .fromMode(0o711), .{}) catch {};
    if (std.fs.path.dirname(stage_dir)) |parent| {
        std.Io.Dir.cwd().createDirPath(io, parent) catch {};
    }
    std.Io.Dir.cwd().createDirPath(io, stage_dir) catch {};
    std.Io.Dir.cwd().setFilePermissions(io, stage_dir, .fromMode(0o700), .{}) catch {};
}

/// Resolve the bridge executable: sibling of `exe` when exe contains a
/// slash, else PATH lookup name. Caller owns the result.
pub fn bridgeExe(gpa: std.mem.Allocator, exe: []const u8) ![]u8 {
    if (std.fs.path.dirname(exe)) |dir| {
        return std.fmt.allocPrint(gpa, "{s}/isakai-dbus-bridge", .{dir});
    }
    return gpa.dupe(u8, "isakai-dbus-bridge");
}

/// Spawn the per-app desktop bridge as ourselves (it runs as the user,
/// not the app uid; SO_PEERCRED gates clients). Stdout is ignored;
/// stderr inherits for diagnostics. Caller reaps via wait().
pub fn spawnBridge(
    io: std.Io,
    gpa: std.mem.Allocator,
    bridge_exe: []const u8,
    bridge_dir: []const u8,
    stage_dir: []const u8,
    uid: u32,
) !std.process.Child {
    const uid_s = try std.fmt.allocPrint(gpa, "{d}", .{uid});
    defer gpa.free(uid_s);
    return std.process.spawn(io, .{
        .argv = &.{ bridge_exe, "--bridge-dir", bridge_dir, "--stage-dir", stage_dir, "--uid", uid_s },
        .stdin = .ignore,
        .stdout = .ignore,
        .stderr = .inherit,
    });
}

/// Wait for the bridge socket to appear (up to ~5s). False on timeout.
pub fn waitBridgeSocket(io: std.Io, gpa: std.mem.Allocator, bridge_dir: []const u8) bool {
    const sock = std.fmt.allocPrint(gpa, "{s}/bridge.sock", .{bridge_dir}) catch return false;
    defer gpa.free(sock);
    var i: usize = 0;
    while (i < 50) : (i += 1) {
        std.Io.Dir.cwd().access(io, sock, .{}) catch {
            {
                const ts = std.os.linux.timespec{ .sec = 0, .nsec = 100 * 1000 * 1000 };
                _ = std.os.linux.nanosleep(&ts, null);
            }
            continue;
        };
        return true;
    }
    return false;
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

/// Resolve the compositor socket for brokering into a privileged windowed
/// run: absolute `$WAYLAND_DISPLAY` is used as-is, otherwise
/// `$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY` (matches the JNI resolver). The
/// path must be absolute without `..`, fit a unix `sockaddr_un` (107
/// bytes + NUL), and stat as a unix socket; anything else is a fail-closed
/// error. Caller owns the result. A `null` return means "no compositor
/// socket configured" (neither var set) — the caller warns, never brokers.
pub fn resolveWaylandSocket(gpa: std.mem.Allocator, env: *const std.process.Environ.Map) !?[]u8 {
    const display = env.get("WAYLAND_DISPLAY") orelse return null;
    if (display.len == 0) return null;
    var path: []u8 = undefined;
    if (display[0] == '/') {
        path = try gpa.dupe(u8, display);
    } else {
        const runtime = env.get("XDG_RUNTIME_DIR") orelse return null;
        if (runtime.len == 0) return null;
        path = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ runtime, display });
    }
    if (!validBridgeDir(path)) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    // Fit check: sockaddr_un path field is 108 bytes incl. NUL.
    if (path.len + 1 > 108) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    // Must stat as a unix-domain socket (fail closed: no broker of
    // regular files, dirs, or missing paths into the app sandbox).
    var zbuf: [4096]u8 = undefined;
    if (path.len >= zbuf.len) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    @memcpy(zbuf[0..path.len], path);
    zbuf[path.len] = 0;
    var stx: std.os.linux.Statx = undefined;
    const rc = std.os.linux.statx(std.os.linux.AT.FDCWD, @ptrCast(&zbuf), 0, .{ .TYPE = true, .MODE = true }, &stx);
    if (std.os.linux.errno(rc) != .SUCCESS) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    if (stx.mode & std.os.linux.S.IFMT != std.os.linux.S.IFSOCK) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    return path;
}

/// Build the `uid-helper run` argv head (everything through `--`):
/// `exe uid-helper run <pkg> <uid> <data> <work> [--wayland-sock S] --`.
/// The flag appears only when `wayland_sock` is non-null (privileged
/// windowed with a resolved compositor socket); unprivileged and headless
/// runs keep the historical argv byte-identical. All items borrowed.
pub fn helperRunHead(
    gpa: std.mem.Allocator,
    exe: []const u8,
    pkg_name: []const u8,
    uid_s: []const u8,
    data_dir: []const u8,
    work: []const u8,
    wayland_sock: ?[]const u8,
) !std.ArrayListUnmanaged([]const u8) {
    var argv: std.ArrayListUnmanaged([]const u8) = .empty;
    errdefer argv.deinit(gpa);
    try argv.appendSlice(gpa, &.{ exe, "uid-helper", "run", pkg_name, uid_s, data_dir, work });
    if (wayland_sock) |s| {
        try argv.appendSlice(gpa, &.{ "--wayland-sock", s });
    }
    try argv.append(gpa, "--");
    return argv;
}

/// True when a bridge/stage dir override is safe: absolute, no `..`.
pub fn validBridgeDir(s: []const u8) bool {
    if (s.len == 0 or s.len > 4000) return false;
    if (s[0] != '/') return false;
    if (std.mem.indexOf(u8, s, "..") != null) return false;
    return true;
}

/// Remove session-bus vars from our own environ so no-priv children
/// cannot reach the bus directly (they must go through the bridge).
/// Privileged children get a minimal env from runAs anyway.
/// Clone the parent env for the sandboxed JVM: add ISAKAI_BRIDGE /
/// ISAKAI_STAGE, drop every DBUS_* var so the app cannot reach the
/// session bus directly (all bus traffic goes through the bridge).
pub fn cloneSandboxEnv(
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    bridge_dir: []const u8,
    stage_dir: []const u8,
) !std.process.Environ.Map {
    var m = try env.clone(gpa);
    errdefer m.deinit();
    try m.put("ISAKAI_BRIDGE", bridge_dir);
    try m.put("ISAKAI_STAGE", stage_dir);
    var drop: std.ArrayListUnmanaged([]const u8) = .empty;
    defer drop.deinit(gpa);
    var it = m.iterator();
    while (it.next()) |e| {
        if (std.mem.startsWith(u8, e.key_ptr.*, "DBUS_")) {
            drop.append(gpa, e.key_ptr.*) catch break;
        }
    }
    for (drop.items) |k| _ = m.swapRemove(k);
    return m;
}

/// spawnInherit with an explicit env map.
pub fn spawnWithEnv(io: std.Io, argv: []const []const u8, env: *const std.process.Environ.Map) !std.process.Child {
    return std.process.spawn(io, .{
        .argv = argv,
        .stdin = .inherit,
        .stdout = .inherit,
        .stderr = .inherit,
        .environ_map = env,
    });
}

fn linuxKill(pid: std.posix.pid_t, sig: u8) void {
    const linux = std.os.linux;
    const SIG = struct {
        fn term() linux.SIG {
            return .TERM;
        }
    };
    _ = sig;
    _ = linux.kill(pid, SIG.term());
}

/// Spawn the JVM as the app user: elevate `isakai uid-helper run ...`
/// via pkexec (or sudo -n when noninteractive). Stdio inherits so app
/// output streams to the caller's terminal. `wayland_sock` is the resolved
/// compositor socket (privileged windowed only); it becomes a discrete
/// `--wayland-sock <abs>` flag before `--` so the helper can bind-mount it
/// into the app runtime dir. Null keeps the historical argv byte-identical.
fn spawnViaHelper(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    exe: []const u8,
    pkg: *const registry.Package,
    work: []const u8,
    jvm_argv: []const []const u8,
    wayland_sock: ?[]const u8,
) !std.process.Child {
    const uid_s = try std.fmt.allocPrint(gpa, "{d}", .{pkg.uid});
    defer gpa.free(uid_s);
    const noninteractive = registry.envFlag(env, "ISAKAI_NONINTERACTIVE");
    var argv: std.ArrayListUnmanaged([]const u8) = .empty;
    defer argv.deinit(gpa);
    if (!noninteractive) {
        // pkexec if present, else fall back to sudo -n on spawn failure.
        try argv.append(gpa, "/usr/bin/pkexec");
        var head = try helperRunHead(gpa, exe, pkg.name, uid_s, pkg.data_dir, work, wayland_sock);
        defer head.deinit(gpa);
        try argv.appendSlice(gpa, head.items);
        try argv.appendSlice(gpa, jvm_argv);
        return spawnInherit(io, argv.items) catch |err| switch (err) {
            error.FileNotFound => {
                argv.clearRetainingCapacity();
                try argv.append(gpa, "/usr/bin/sudo");
                try argv.append(gpa, "-n");
                var head2 = try helperRunHead(gpa, exe, pkg.name, uid_s, pkg.data_dir, work, wayland_sock);
                defer head2.deinit(gpa);
                try argv.appendSlice(gpa, head2.items);
                try argv.appendSlice(gpa, jvm_argv);
                return spawnInherit(io, argv.items);
            },
            else => return err,
        };
    }
    try argv.append(gpa, "/usr/bin/sudo");
    try argv.append(gpa, "-n");
    var head = try helperRunHead(gpa, exe, pkg.name, uid_s, pkg.data_dir, work, wayland_sock);
    defer head.deinit(gpa);
    try argv.appendSlice(gpa, head.items);
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
    // Real apps carry `@string` label refs: resolve them from the staged
    // resources.arsc so the toplevel titles correctly; literals (fixture)
    // and missing tables fall through to manifestTitle unchanged.
    _ = install.resolveStagedLabels(io, gpa, pkg.apk, &m) catch false;
    const title_arg = install.manifestTitle(&m);
    const app_theme_arg = try std.fmt.allocPrint(gpa, "{d}", .{m.app_theme});
    defer gpa.free(app_theme_arg);
    const act_theme_arg = try std.fmt.allocPrint(gpa, "{d}", .{m.activity_theme});
    defer gpa.free(act_theme_arg);
    const windowed: bool = registry.envFlag(env, "ISAKAI_WINDOW");
    // Privileged windowed runs need the compositor socket brokered into the
    // app sandbox (the helper bind-mounts it into the per-app runtime dir):
    // resolve + validate here, fail closed when unusable. The diagnostic
    // stays when no socket is configured at all. Unprivileged runs inherit
    // our own Wayland env untouched (byte-identical path, no resolution).
    var wayland_sock: ?[]u8 = null;
    defer if (wayland_sock) |s| gpa.free(s);
    if (windowed and !no_priv) {
        wayland_sock = try resolveWaylandSocket(gpa, env);
        if (wayland_sock == null) {
            std.log.err("windowed launch: no compositor socket (set WAYLAND_DISPLAY+XDG_RUNTIME_DIR); run with ISAKAI_NO_PRIVILEGE=1 for now, e.g. ISAKAI_WINDOW=1 ISAKAI_NO_PRIVILEGE=1 isakai launch <pkg>", .{});
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return error.NoWaylandSocket;
        }
    }
    const lib_path = try std.fmt.allocPrint(gpa, "-Djava.library.path={s}/lib", .{framework_dir});
    defer gpa.free(lib_path);
    const windowed_prop = if (windowed) "-Disakai.windowed=true" else "-Disakai.windowed=false";
    const jvm_argv = try buildJvmArgv(gpa, java, lib_path, windowed_prop, cp, pkg.data_dir, app_arg, act_arg, pkg.name, title_arg, pkg.apk, app_theme_arg, act_theme_arg);
    defer gpa.free(jvm_argv);
    defer gpa.free(jvm_argv[4]);
    // Desktop bridge: validated --bridge-dir/--stage-dir under
    // work/run/<pkg> + work/stage/<pkg>, pre-bound before the spawn.
    // ISAKAI_BRIDGE_DIR/ISAKAI_STAGE_DIR env overrides (validated) let
    // tests redirect; the sandbox gets ISAKAI_BRIDGE/ISAKAI_STAGE.
    const dirs = try bridgeDirs(gpa, work, pkg.name);
    defer gpa.free(dirs.bridge);
    defer gpa.free(dirs.stage);
    const bridge_dir = env.get("ISAKAI_BRIDGE_DIR") orelse dirs.bridge;
    const stage_dir = env.get("ISAKAI_STAGE_DIR") orelse dirs.stage;
    if (!validBridgeDir(bridge_dir) or !validBridgeDir(stage_dir)) {
        std.log.err("bridge/stage dir must be absolute without '..'", .{});
        rollbackToInstalled(&reg, io, gpa, work, pkg);
        lock.release();
        return error.BadBridgeDir;
    }
    prebindBridgeDirs(io, bridge_dir, stage_dir);
    // Spawn the bridge before the JVM so the socket is ready; it runs
    // as the user (not the app uid) with --uid for SO_PEERCRED gating.
    // ISAKAI_NO_BRIDGE=1 skips it (headless/bridge-less runs).
    const no_bridge = registry.envFlag(env, "ISAKAI_NO_BRIDGE");
    const bridge_path = try bridgeExe(gpa, exe);
    defer gpa.free(bridge_path);
    var bridge_child: ?std.process.Child = null;
    if (!no_bridge) {
        if (spawnBridge(io, gpa, bridge_path, bridge_dir, stage_dir, pkg.uid)) |bc| {
            bridge_child = bc;
        } else |err| {
            std.log.warn("bridge spawn failed ({t}); continuing without desktop integration", .{err});
        }
        if (bridge_child != null) {
            if (!waitBridgeSocket(io, gpa, bridge_dir)) {
                std.log.warn("bridge socket never appeared; continuing without desktop integration", .{});
                var bc = bridge_child.?;
                _ = bc.wait(io) catch {};
                bridge_child = null;
            }
        }
    }
    // Sandbox env: ISAKAI_BRIDGE/ISAKAI_STAGE point at the pre-bound
    // dirs; DBUS_* is never propagated (the bridge owns the bus).
    // Privileged children get a minimal env from runAs anyway.
    var child_env = try cloneSandboxEnv(gpa, env, bridge_dir, stage_dir);
    defer child_env.deinit();
    // Privileged path: run the JVM as the app user via the helper.
    // ISAKAI_NO_PRIVILEGE=1 runs it directly as ourselves (dev/test).
    var child: std.process.Child = undefined;
    if (no_priv) {
        child = spawnWithEnv(io, jvm_argv, &child_env) catch |err| {
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return err;
        };
    } else {
        child = spawnViaHelper(io, gpa, &child_env, exe, &pkg, work, jvm_argv, wayland_sock) catch |err| {
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
    // Reap the bridge after the app exits (TERM, then wait).
    if (bridge_child) |*bc| {
        const id = bc.id;
        if (id) |pid_v| {
            _ = linuxKill(pid_v, 15);
        }
        _ = bc.wait(io) catch {};
    }
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

test "buildJvmArgv shape: native-access first, codeDir 5th, 5th IsakaiMain arg is title" {
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
        "/code/com.foo/abc",
        "0",
        "0",
    );
    defer {
        std.testing.allocator.free(argv[4]);
        std.testing.allocator.free(argv);
    }
    try std.testing.expectEqual(@as(usize, 15), argv.len);
    try std.testing.expectEqualStrings("--enable-native-access=ALL-UNNAMED", argv[1]);
    try std.testing.expectEqualStrings("-Disakai.codeDir=/code/com.foo/abc", argv[4]);
    try std.testing.expectEqualStrings("org.isakai.runtime.IsakaiMain", argv[7]);
    try std.testing.expectEqualStrings("com.foo", argv[11]);
    try std.testing.expectEqualStrings("Fixture", argv[12]);
}

test "resolveWaylandSocket: null when unconfigured, resolves runtime/display" {
    const gpa = std.testing.allocator;
    const io = std.testing.io;
    // No vars: null (caller warns, never brokers).
    {
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try std.testing.expect(try resolveWaylandSocket(gpa, &env) == null);
    }
    // Empty display: null.
    {
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "");
        try env.put("XDG_RUNTIME_DIR", "/run/user/1000");
        try std.testing.expect(try resolveWaylandSocket(gpa, &env) == null);
    }
    // Relative display without runtime: null.
    {
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "wayland-1");
        try std.testing.expect(try resolveWaylandSocket(gpa, &env) == null);
    }
    // Relative display + runtime pointing at a real dir, but no socket
    // file: fail-closed BadWaylandSocket (not null).
    {
        var tmp = std.testing.tmpDir(.{});
        defer tmp.cleanup();
        const cwd_path = try std.process.currentPathAlloc(io, gpa);
        defer gpa.free(cwd_path);
        const rt = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}", .{ cwd_path, &tmp.sub_path });
        defer gpa.free(rt);
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "wayland-9");
        try env.put("XDG_RUNTIME_DIR", rt);
        try std.testing.expectError(error.BadWaylandSocket, resolveWaylandSocket(gpa, &env));
    }
    // Traversal in the composed path: fail closed.
    {
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "../evil");
        try env.put("XDG_RUNTIME_DIR", "/run/user/1000");
        try std.testing.expectError(error.BadWaylandSocket, resolveWaylandSocket(gpa, &env));
    }
    // A bound unix socket resolves to its absolute path.
    {
        var tmp = std.testing.tmpDir(.{});
        defer tmp.cleanup();
        const cwd_path = try std.process.currentPathAlloc(io, gpa);
        defer gpa.free(cwd_path);
        const rt = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}", .{ cwd_path, &tmp.sub_path });
        defer gpa.free(rt);
        const sock_path = try std.fmt.allocPrint(gpa, "{s}/wayland-7", .{rt});
        defer gpa.free(sock_path);
        const linux = std.os.linux;
        const fd_rc = linux.socket(@as(u32, linux.AF.UNIX), @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC), 0);
        try std.testing.expect(linux.errno(fd_rc) == .SUCCESS);
        const fd: std.posix.fd_t = @bitCast(@as(u32, @truncate(fd_rc)));
        defer _ = linux.close(fd);
        var arr: [108]u8 = undefined;
        @memset(&arr, 0);
        @memcpy(arr[0..sock_path.len], sock_path);
        var addr = linux.sockaddr.un{ .path = arr };
        const addrlen: linux.socklen_t = @intCast(2 + sock_path.len + 1);
        try std.testing.expect(linux.errno(linux.bind(fd, @ptrCast(&addr), addrlen)) == .SUCCESS);
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "wayland-7");
        try env.put("XDG_RUNTIME_DIR", rt);
        const got = try resolveWaylandSocket(gpa, &env);
        defer if (got) |s| gpa.free(s);
        try std.testing.expect(got != null);
        try std.testing.expectEqualStrings(sock_path, got.?);
    }
    // Same socket via absolute WAYLAND_DISPLAY (no runtime needed).
    {
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "/run/user/1000/wayland-1");
        // Only assert when that exact fixture exists on this machine;
        // otherwise the fail-closed branch is equally acceptable.
        const got = resolveWaylandSocket(gpa, &env) catch |err| blk: {
            try std.testing.expectEqual(error.BadWaylandSocket, err);
            break :blk @as(?[]u8, null);
        };
        defer if (got) |s| gpa.free(s);
        if (got) |s| try std.testing.expectEqualStrings("/run/user/1000/wayland-1", s);
    }
    // Regular file at the composed path: fail closed, never brokered.
    {
        var tmp = std.testing.tmpDir(.{});
        defer tmp.cleanup();
        const cwd_path = try std.process.currentPathAlloc(io, gpa);
        defer gpa.free(cwd_path);
        const rt = try std.fmt.allocPrint(gpa, "{s}/.zig-cache/tmp/{s}", .{ cwd_path, &tmp.sub_path });
        defer gpa.free(rt);
        const fpath = try std.fmt.allocPrint(gpa, "{s}/wayland-0", .{rt});
        defer gpa.free(fpath);
        var z: [4096]u8 = undefined;
        @memcpy(z[0..fpath.len], fpath);
        z[fpath.len] = 0;
        const fz: [*:0]const u8 = @ptrCast(&z);
        const frc = std.os.linux.openat(std.os.linux.AT.FDCWD, fz, .{ .ACCMODE = .WRONLY, .CREAT = true, .TRUNC = true }, 0o600);
        try std.testing.expect(std.os.linux.errno(frc) == .SUCCESS);
        _ = std.os.linux.close(@bitCast(@as(u32, @truncate(frc))));
        var env = std.process.Environ.Map.init(gpa);
        defer env.deinit();
        try env.put("WAYLAND_DISPLAY", "wayland-0");
        try env.put("XDG_RUNTIME_DIR", rt);
        try std.testing.expectError(error.BadWaylandSocket, resolveWaylandSocket(gpa, &env));
    }
}

test "helperRunHead: no flag without socket, discrete flag with socket" {
    const gpa = std.testing.allocator;
    // Headless/unprivileged shape: byte-identical to the historical argv.
    {
        var head = try helperRunHead(gpa, "isakai", "com.foo", "200001", "/data", "/work", null);
        defer head.deinit(gpa);
        const want = [_][]const u8{ "isakai", "uid-helper", "run", "com.foo", "200001", "/data", "/work", "--" };
        try std.testing.expectEqual(want.len, head.items.len);
        for (want, head.items) |w, h| try std.testing.expectEqualStrings(w, h);
    }
    // Privileged windowed: one discrete flag pair before `--`, nothing else.
    {
        var head = try helperRunHead(gpa, "isakai", "com.foo", "200001", "/data", "/work", "/run/user/1000/wayland-1");
        defer head.deinit(gpa);
        const want = [_][]const u8{ "isakai", "uid-helper", "run", "com.foo", "200001", "/data", "/work", "--wayland-sock", "/run/user/1000/wayland-1", "--" };
        try std.testing.expectEqual(want.len, head.items.len);
        for (want, head.items) |w, h| try std.testing.expectEqualStrings(w, h);
    }
}
