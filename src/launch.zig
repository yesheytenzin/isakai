// SPDX-License-Identifier: GPL-3.0-or-later

//! App launch: translate DEX at install time, spawn the host JVM, reap it,
//! record the outcome in the registry state machine.

const std = @import("std");
const dex = @import("dex");
const translate = @import("translate");
const registry = @import("registry");
const install = @import("install");
const pathmod = @import("bridge_path");
const runtime_model = @import("runtime");

/// Translate all classes.dex/classesN.dex in the staged code dir to JVM
/// classes. Writes into <code_dir>/classes/. Idempotent: skips if the
/// marker (combined digest list) matches.
///
/// Enumeration follows Android: `classes.dex` first, then N=2,3,… ascending,
/// stopping at the first gap. Each dex is validated by magic (not name) and
/// parsed with its own ID namespace. Duplicate class names across dexes are
/// a loud error (first-wins would hide split-brain input).
pub fn translateApp(io: std.Io, gpa: std.mem.Allocator, code_dir: []const u8) anyerror!void {
    return translateAppWithRuntime(io, gpa, code_dir, "unselected", "unselected");
}

pub fn translateAppWithRuntime(
    io: std.Io,
    gpa: std.mem.Allocator,
    code_dir: []const u8,
    java: []const u8,
    framework_dir: []const u8,
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

    // A DEX-only marker would keep executing old bytecode after translator
    // fixes. Include source and toolchain identity, not just APK identity.
    var marker: std.ArrayListUnmanaged(u8) = .empty;
    defer marker.deinit(gpa);
    const compiler = translatorFingerprint();
    try marker.appendSlice(gpa, runtime_model.cache_version);
    try marker.append(gpa, '\n');
    const runtime_header = try std.fmt.allocPrint(gpa, "backend:{d}:jvm_v1\njava:{d}:{s}\nframework:{d}:{s}\n", .{
        "jvm_v1".len, java.len, java, framework_dir.len, framework_dir,
    });
    defer gpa.free(runtime_header);
    try marker.appendSlice(gpa, runtime_header);
    const java_digest = try install.sha256File(io, gpa, java);
    defer gpa.free(java_digest);
    try marker.appendSlice(gpa, "java-digest:");
    try marker.appendSlice(gpa, java_digest);
    try marker.append(gpa, '\n');
    try marker.appendSlice(gpa, "translator:");
    try marker.appendSlice(gpa, &compiler);
    try marker.append(gpa, '\n');
    for (dex_names.items) |dn| {
        const full = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ code_dir, dn });
        defer gpa.free(full);
        const dg = try install.sha256File(io, gpa, full);
        defer gpa.free(dg);
        try marker.appendSlice(gpa, dg);
        try marker.append(gpa, '\n');
    }
    for ([_][]const u8{
        "org/isakai/runtime/IsakaiMain.class",
        "lib/libisakai_jni.so",
        "lib/libisakai_bionic_shim.so",
    }) |relative| {
        const full = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ framework_dir, relative });
        defer gpa.free(full);
        if (install.sha256File(io, gpa, full)) |digest| {
            defer gpa.free(digest);
            const line = try std.fmt.allocPrint(gpa, "runtime-artifact:{d}:{s}:{s}\n", .{ relative.len, relative, digest });
            defer gpa.free(line);
            try marker.appendSlice(gpa, line);
        } else |_| {
            const line = try std.fmt.allocPrint(gpa, "runtime-artifact:{d}:{s}:missing\n", .{ relative.len, relative });
            defer gpa.free(line);
            try marker.appendSlice(gpa, line);
        }
    }
    if (std.Io.Dir.cwd().readFileAlloc(io, marker_path, gpa, .limited(65536))) |old| {
        defer gpa.free(old);
        if (std.mem.eql(u8, old, marker.items)) return;
        try std.Io.Dir.cwd().deleteTree(io, classes_dir);
    } else |_| {}
    try std.Io.Dir.cwd().createDirPath(io, classes_dir);

    // Mirror staged ServiceLoader configurations into the translated
    // classes root. Stock ServiceLoader (used by the obfuscated coroutine
    // dispatcher factories the pinned apps ship under META-INF/services/)
    // reads the JVM classpath, never the staged tree, so without this
    // mirror Dispatchers.Main has no factory and Compose never schedules
    // its recomposer. Placed after the marker decision so a retranslate
    // (which wipes classes/) cannot remove the mirror; idempotent, so
    // every full translation ends with current service bytes.
    {
        const svc_src = try std.fmt.allocPrint(gpa, "{s}/META-INF/services", .{code_dir});
        defer gpa.free(svc_src);
        if (std.Io.Dir.cwd().openDir(io, svc_src, .{ .iterate = true })) |*svc_dir| {
            defer svc_dir.close(io);
            const svc_dst = try std.fmt.allocPrint(gpa, "{s}/META-INF/services", .{classes_dir});
            defer gpa.free(svc_dst);
            std.Io.Dir.cwd().createDirPath(io, svc_dst) catch {};
            var it = svc_dir.iterate();
            while (it.next(io) catch null) |entry| {
                if (entry.kind != .file) continue;
                const src_path = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ svc_src, entry.name });
                defer gpa.free(src_path);
                const bytes = std.Io.Dir.cwd().readFileAlloc(io, src_path, gpa, .limited(65536)) catch continue;
                defer gpa.free(bytes);
                const dst_path = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ svc_dst, entry.name });
                defer gpa.free(dst_path);
                var f = std.Io.Dir.cwd().createFile(io, dst_path, .{}) catch continue;
                defer f.close(io);
                var wbuf: [8192]u8 = undefined;
                var fw = f.writer(io, &wbuf);
                fw.interface.writeAll(bytes) catch continue;
                fw.interface.flush() catch continue;
                std.Io.Dir.cwd().setFilePermissions(io, dst_path, .fromMode(0o644), .{}) catch {};
            }
        } else |_| {}
    }

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
            // Platform classes ship in the framework, not the app: stub DEXes
            // embed android.* throw-stubs (e.g. AutofillCallback) that would
            // shadow the real framework classes on the app-first classpath.
            // Bundled libraries (androidx, kotlin, kotlinx, android.support)
            // still translate: only the android/ platform tree is skipped.
            if (std.mem.startsWith(u8, tc.internal_name, "android/") and
                !std.mem.startsWith(u8, tc.internal_name, "android/support/"))
            {
                continue;
            }
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
                // Platform classes are never written (see the first-pass
                // skip above); a forward naming one has no directory and
                // must not be created.
                if (std.mem.startsWith(u8, e.key_ptr.*, "android/") and
                    !std.mem.startsWith(u8, e.key_ptr.*, "android/support/"))
                {
                    continue;
                }
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

fn translatorFingerprint() [64]u8 {
    var hash = std.crypto.hash.sha2.Sha256.init(.{});
    hash.update(@embedFile("translate.zig"));
    hash.update(@embedFile("dex.zig"));
    hash.update(@embedFile("launch.zig"));
    hash.update(@import("builtin").zig_version_string);
    return std.fmt.bytesToHex(hash.finalResult(), .lower);
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
/// Privileged candidates must be under /usr/lib/jvm/ to match runJvm's
/// trusted runtime roots, and world-traversable for the dropped child.
pub fn findJava(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    privileged: bool,
) !?[]u8 {
    const worldExec = struct {
        fn f(path: []const u8) bool {
            const linux = std.os.linux;
            if (!std.mem.startsWith(u8, path, "/usr/lib/jvm/")) return false;
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
        // Resolve PATH before spawning: process launch requires a path, and
        // mise supplies Java through a user-owned PATH entry.
        var paths = std.mem.splitScalar(u8, env.get("PATH") orelse "", ':');
        while (paths.next()) |dir| {
            const p = try std.fmt.allocPrint(gpa, "{s}/java", .{if (dir.len == 0) "." else dir});
            if (javaFeature(io, gpa, p)) |f| {
                if (f >= min_java_feature) return p;
            }
            gpa.free(p);
        }
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
    target_sdk: []const u8,
    min_sdk: []const u8,
    intent_data: []const u8,
) ![][]const u8 {
    const argv = try gpa.alloc([]const u8, 19);
    argv[0] = java;
    argv[1] = "--enable-native-access=ALL-UNNAMED";
    argv[2] = lib_path;
    argv[3] = windowed_prop;
    argv[4] = try std.fmt.allocPrint(gpa, "-Disakai.codeDir={s}", .{code_dir});
    argv[5] = try std.fmt.allocPrint(gpa, "-Djava.io.tmpdir={s}/cache/tmp", .{data_dir});
    argv[6] = "-cp";
    argv[7] = cp;
    argv[8] = "org.isakai.runtime.IsakaiMain";
    argv[9] = data_dir;
    argv[10] = app_arg;
    argv[11] = act_arg;
    argv[12] = pkg_name;
    argv[13] = title;
    argv[14] = app_theme;
    argv[15] = act_theme;
    argv[16] = target_sdk;
    argv[17] = min_sdk;
    argv[18] = intent_data;
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

/// Bridge/stage dir anchors: `<tmp>/isakai-<uid>-<digest>` (the desktop
/// bridge socket endpoint; see src/bridge/path.zig for why it cannot live
/// under the work dir) + work/stage/<package>. Caller owns both results.
pub fn bridgeDirs(gpa: std.mem.Allocator, work: []const u8, package: []const u8, uid: u32) !struct { bridge: []u8, stage: []u8 } {
    const bridge = try pathmod.dirFor(gpa, work, package, uid);
    errdefer gpa.free(bridge);
    const stage = try std.fmt.allocPrint(gpa, "{s}/stage/{s}", .{ work, package });
    return .{ .bridge = bridge, .stage = stage };
}

/// Pre-bind the bridge + stage dirs, fail-closed. The bridge endpoint is
/// created (never reused) when owned by us: launcher-owned 0711 so the
/// sandboxed app uid can traverse to the socket without listing the
/// directory, and no other local user can create or replace the socket
/// node inside it. A pre-existing directory owned by someone else is a
/// hijack attempt (or an unrelated collision) and refuses the launch.
/// stage/<pkg> is 0700; the privileged helper chowns it to the app uid at
/// install (app writes transfers).
pub fn prebindBridgeDirs(io: std.Io, bridge_dir: []const u8, stage_dir: []const u8) !void {
    const linux = std.os.linux;
    if (!pathmod.validDir(bridge_dir)) return error.BadBridgeDir;
    const cwd = std.Io.Dir.cwd();
    cwd.createDirPath(io, bridge_dir) catch |err| switch (err) {
        // Another process (concurrent launch, retry after crash, or a
        // foreign user) got there first: fine only if we own the result.
        error.PathAlreadyExists => {},
        else => return err,
    };
    {
        var zbuf: [pathmod.max_dir + 1]u8 = undefined;
        @memcpy(zbuf[0..bridge_dir.len], bridge_dir);
        zbuf[bridge_dir.len] = 0;
        const z: [*:0]const u8 = @ptrCast(&zbuf);
        var stx: linux.Statx = undefined;
        const rc = linux.statx(linux.AT.FDCWD, z, linux.AT.SYMLINK_NOFOLLOW, .{ .TYPE = true, .MODE = true, .UID = true }, &stx);
        if (linux.errno(rc) != .SUCCESS) return error.BadBridgeDir;
        if (stx.mode & linux.S.IFMT != linux.S.IFDIR) return error.BadBridgeDir;
        // A directory owned by another uid cannot be trusted: that uid
        // could replace bridge.sock with its own listener.
        if (stx.uid != linux.geteuid()) return error.BadBridgeDirOwner;
        if (stx.mode & 0o022 != 0) return error.BadBridgeDirMode;
    }
    cwd.setFilePermissions(io, bridge_dir, .fromMode(0o711), .{}) catch {};
    if (std.fs.path.dirname(stage_dir)) |parent| {
        cwd.createDirPath(io, parent) catch {};
    }
    cwd.createDirPath(io, stage_dir) catch {};
    cwd.setFilePermissions(io, stage_dir, .fromMode(0o700), .{}) catch {};
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

/// Wait for the bridge socket to appear (up to ~5s). False on timeout or
/// when the endpoints disagree about the socket path (a length/shape
/// mismatch is reported by the daemon's own diagnostic).
pub fn waitBridgeSocket(io: std.Io, gpa: std.mem.Allocator, bridge_dir: []const u8) bool {
    if (!pathmod.validDir(bridge_dir)) return false;
    const sock = pathmod.socketPath(gpa, bridge_dir) catch return false;
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
pub fn resolveWaylandSocket(io: std.Io, gpa: std.mem.Allocator, env: *const std.process.Environ.Map) !?[]u8 {
    const display = env.get("WAYLAND_DISPLAY") orelse return null;
    if (display.len == 0) return null;
    const runtime = env.get("XDG_RUNTIME_DIR") orelse return null;
    if (runtime.len == 0) return null;
    var path: []u8 = undefined;
    if (display[0] == '/') {
        path = try gpa.dupe(u8, display);
    } else {
        path = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ runtime, display });
    }
    // Anchor under the caller's own runtime dir: absolute displays cannot
    // point elsewhere, relative ones already resolve inside it.
    if (!std.mem.startsWith(u8, path, runtime) or path.len == runtime.len or path[runtime.len] != '/') {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    if (!validAbsPath(path)) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    // Fit check: sockaddr_un path field is 108 bytes incl. NUL.
    if (path.len + 1 > pathmod.max_path) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    // O_NOFOLLOW-style via Zig std: a symlinked final component reports
    // .sym_link and is rejected, never followed (fail closed: no broker
    // of regular files, dirs, or missing paths into the app sandbox).
    const st = std.Io.Dir.cwd().statFile(io, path, .{ .follow_symlinks = false }) catch {
        gpa.free(path);
        return error.BadWaylandSocket;
    };
    if (st.kind != .unix_domain_socket) {
        gpa.free(path);
        return error.BadWaylandSocket;
    }
    // Stream probe: only a SOCK_STREAM listener accepts this connect;
    // datagram sockets fail here instead of inside the sandbox.
    {
        const linux = std.os.linux;
        const sfd = linux.socket(@as(u32, linux.AF.UNIX), @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC), 0);
        if (linux.errno(sfd) != .SUCCESS) {
            gpa.free(path);
            return error.BadWaylandSocket;
        }
        const s: linux.fd_t = @bitCast(@as(u32, @truncate(sfd)));
        defer _ = linux.close(s);
        var addr_arr: [pathmod.max_path]u8 = undefined;
        @memset(&addr_arr, 0);
        @memcpy(addr_arr[0..path.len], path);
        var addr = linux.sockaddr.un{ .path = addr_arr };
        const addrlen: linux.socklen_t = @intCast(2 + path.len + 1);
        if (linux.errno(linux.connect(s, @ptrCast(&addr), addrlen)) != .SUCCESS) {
            gpa.free(path);
            return error.BadWaylandSocket;
        }
    }
    return path;
}

const HelperJvmFields = struct {
    java: []const u8,
    windowed: bool,
    app_class: []const u8,
    activity_class: []const u8,
    title: []const u8,
    app_theme: []const u8,
    activity_theme: []const u8,
    target_sdk: []const u8,
    min_sdk: []const u8,
    /// Optional VIEW intent data URI (empty = plain launcher intent).
    intent_data: []const u8,
};

pub fn helperRunHead(
    gpa: std.mem.Allocator,
    exe: []const u8,
    pkg_name: []const u8,
    uid_s: []const u8,
    data_dir: []const u8,
    code_dir: []const u8,
    work: []const u8,
    wayland_sock: ?[]const u8,
    bridge_dir: ?[]const u8,
    framework_dir: []const u8,
    fields: HelperJvmFields,
) !std.ArrayListUnmanaged([]const u8) {
    var argv: std.ArrayListUnmanaged([]const u8) = .empty;
    errdefer argv.deinit(gpa);
    try argv.appendSlice(gpa, &.{ exe, "uid-helper", "run-jvm", pkg_name, uid_s, data_dir, work });
    if (wayland_sock) |s| {
        try argv.appendSlice(gpa, &.{ "--wayland-sock", s });
    }
    if (bridge_dir) |b| {
        try argv.appendSlice(gpa, &.{ "--bridge-dir", b });
    }
    try argv.appendSlice(gpa, &.{
        "--code-dir",       code_dir,
        "--java",           fields.java,
        "--framework",      framework_dir,
        "--windowed",       if (fields.windowed) "1" else "0",
        "--app-class",      fields.app_class,
        "--activity-class", fields.activity_class,
        "--title",          fields.title,
        "--app-theme",      fields.app_theme,
        "--activity-theme", fields.activity_theme,
        "--target-sdk",     fields.target_sdk,
        "--min-sdk",        fields.min_sdk,
        "--intent-data",    fields.intent_data,
    });
    return argv;
}

/// True when an override path is safe to hand to the privileged helper:
/// absolute, no `..`, bounded length. The bridge endpoint has its own
/// stricter rule in src/bridge/path.zig.
pub fn validAbsPath(s: []const u8) bool {
    if (s.len == 0 or s.len > 4000) return false;
    if (s[0] != '/') return false;
    if (std.mem.indexOf(u8, s, "..") != null) return false;
    if (std.mem.indexOfScalar(u8, s, 0) != null) return false;
    return true;
}

/// True when `work` is anchored level by level: absolute, every prefix a
/// real directory, no symlink component (O_NOFOLLOW-style via Zig std).
fn anchorWork(io: std.Io, work: []const u8) bool {
    if (work.len == 0 or work.len > 4000 or work[0] != '/') return false;
    var i: usize = 1;
    while (i <= work.len) : (i += 1) {
        if (i == work.len or work[i] == '/') {
            const st = std.Io.Dir.cwd().statFile(io, work[0..i], .{ .follow_symlinks = false }) catch return false;
            if (st.kind != .directory) return false;
        }
    }
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

/// M6f: per-operation pkexec wrapper installed by `make install`; matches
/// the id.isakai.run-app policy action's exec.path. When absent (dev
/// relocatable installs with INSTALL_POLICY=0) launch falls back to the
/// direct `pkexec /usr/bin/isakai uid-helper run-jvm ...` argv.
const run_wrapper = "/usr/bin/isakai-run-app";

/// Fixed installed launcher: pkexec/sudo must never elevate argv[0],
/// which in a dev tree points at a writable checkout binary.
const installed_exe = "/usr/bin/isakai";

/// /proc/self/exe when it is the installed binary, else the fixed path.
fn privilegedExe(gpa: std.mem.Allocator) []const u8 {
    const linux = std.os.linux;
    var buf: [4096]u8 = undefined;
    const n = linux.readlink("/proc/self/exe", &buf, buf.len);
    if (linux.errno(n) == .SUCCESS and n < buf.len and std.mem.startsWith(u8, buf[0..n], "/usr/bin/")) {
        return gpa.dupe(u8, buf[0..n]) catch installed_exe;
    }
    return installed_exe;
}

fn hasRunWrapper(io: std.Io) bool {
    std.Io.Dir.cwd().access(io, run_wrapper, .{ .execute = true }) catch return false;
    return true;
}

fn spawnViaHelper(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    pkg: *const registry.Package,
    work: []const u8,
    bridge_dir: []const u8,
    fields: HelperJvmFields,
    wayland_sock: ?[]const u8,
    framework_dir: []const u8,
) !std.process.Child {
    const uid_s = try std.fmt.allocPrint(gpa, "{d}", .{pkg.uid});
    defer gpa.free(uid_s);
    const helper_exe = privilegedExe(gpa);
    defer {
        if (helper_exe.ptr != installed_exe.ptr) gpa.free(helper_exe);
    }
    const noninteractive = registry.envFlag(env, "ISAKAI_NONINTERACTIVE");
    var argv: std.ArrayListUnmanaged([]const u8) = .empty;
    defer argv.deinit(gpa);
    if (!noninteractive) {
        // pkexec if present, else fall back to sudo -n on spawn failure.
        try argv.append(gpa, "/usr/bin/pkexec");
        var head = try helperRunHead(gpa, helper_exe, pkg.name, uid_s, pkg.data_dir, pkg.apk, work, wayland_sock, bridge_dir, framework_dir, fields);
        defer head.deinit(gpa);
        // M6f: prefer the per-operation run wrapper (matches the
        // id.isakai.run-app policy action's exec.path → its own dialog
        // text). Wrapper re-inserts "uid-helper run-jvm" before the request.
        if (hasRunWrapper(io)) {
            try argv.append(gpa, run_wrapper);
            try argv.appendSlice(gpa, head.items[3..]);
            return spawnInherit(io, argv.items);
        }
        try argv.appendSlice(gpa, head.items);
        return spawnInherit(io, argv.items) catch |err| switch (err) {
            error.FileNotFound => {
                argv.clearRetainingCapacity();
                try argv.append(gpa, "/usr/bin/sudo");
                try argv.append(gpa, "-n");
                var head2 = try helperRunHead(gpa, helper_exe, pkg.name, uid_s, pkg.data_dir, pkg.apk, work, wayland_sock, bridge_dir, framework_dir, fields);
                defer head2.deinit(gpa);
                try argv.appendSlice(gpa, head2.items);
                return spawnInherit(io, argv.items);
            },
            else => return err,
        };
    }
    try argv.append(gpa, "/usr/bin/sudo");
    try argv.append(gpa, "-n");
    var head = try helperRunHead(gpa, helper_exe, pkg.name, uid_s, pkg.data_dir, pkg.apk, work, wayland_sock, bridge_dir, framework_dir, fields);
    defer head.deinit(gpa);
    try argv.appendSlice(gpa, head.items);
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
    intent_data: []const u8,
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
    // N2: cap --data once here so both the privileged helper path and the
    // no-privilege JVM path below share the same bound.
    if (intent_data.len > 4096 or std.mem.indexOfScalar(u8, intent_data, 0) != null) {
        std.log.err("intent data exceeds 4096 bytes; refusing launch", .{});
        lock.release();
        return error.BadIntentData;
    }
    // Manifest gives app/activity classes.
    const manifest_path = try std.fmt.allocPrint(gpa, "{s}/AndroidManifest.xml", .{pkg.apk});
    defer gpa.free(manifest_path);
    const mbytes = try std.Io.Dir.cwd().readFileAlloc(io, manifest_path, gpa, .limited(install.max_manifest));
    defer gpa.free(mbytes);
    var m = try install.parseManifest(gpa, mbytes);
    defer install.freeManifest(gpa, &m);

    const no_priv = registry.envFlag(env, "ISAKAI_NO_PRIVILEGE");
    var selected = try runtime_model.select(io, gpa, pkg, framework_dir, !no_priv);
    defer runtime_model.deinit(&selected, gpa);
    const java = (try findJava(io, gpa, env, !no_priv)) orelse return error.NoJava;
    defer gpa.free(java);

    try translateAppWithRuntime(io, gpa, pkg.apk, java, framework_dir);

    // Mark starting.
    pkg.state = .starting;
    pkg.launch_id += 1;
    try reg.updateState(io, gpa, work, pkg);

    if (no_priv) {
        const temp_dir = try std.fmt.allocPrint(gpa, "{s}/cache/tmp", .{pkg.data_dir});
        defer gpa.free(temp_dir);
        try std.Io.Dir.cwd().createDirPath(io, temp_dir);
    }
    const classes_dir = selected.jvm_v1.artifacts.classes;
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
    var title_buf: [16]u8 = undefined;
    const title_arg = install.manifestTitle(&title_buf, &m);
    const app_theme_arg = try std.fmt.allocPrint(gpa, "{d}", .{m.app_theme});
    defer gpa.free(app_theme_arg);
    const act_theme_arg = try std.fmt.allocPrint(gpa, "{d}", .{m.activity_theme});
    defer gpa.free(act_theme_arg);
    const target_sdk_arg = try std.fmt.allocPrint(gpa, "{d}", .{m.target_sdk});
    defer gpa.free(target_sdk_arg);
    const min_sdk_arg = try std.fmt.allocPrint(gpa, "{d}", .{m.min_sdk});
    defer gpa.free(min_sdk_arg);
    const windowed: bool = registry.envFlag(env, "ISAKAI_WINDOW");
    // Privileged windowed runs need the compositor socket brokered into the
    // app sandbox (the helper bind-mounts it into the per-app runtime dir):
    // resolve + validate here, fail closed when unusable. The diagnostic
    // stays when no socket is configured at all. Unprivileged runs inherit
    // our own Wayland env untouched (byte-identical path, no resolution).
    var wayland_sock: ?[]u8 = null;
    defer if (wayland_sock) |s| gpa.free(s);
    if (windowed and !no_priv) {
        wayland_sock = try resolveWaylandSocket(io, gpa, env);
        if (wayland_sock == null) {
            std.log.err("windowed launch: no compositor socket (set WAYLAND_DISPLAY+XDG_RUNTIME_DIR); run with ISAKAI_NO_PRIVILEGE=1 for now, e.g. ISAKAI_WINDOW=1 ISAKAI_NO_PRIVILEGE=1 isakai launch <pkg>", .{});
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return error.NoWaylandSocket;
        }
    }
    // M6a: app-native libs. The JVM's own dlopen does the loading.
    const lib_path = try std.fmt.allocPrint(
        gpa,
        "-Djava.library.path={s}/lib:{s}/lib/x86_64:{s}/lib",
        .{ framework_dir, pkg.apk, pkg.apk },
    );
    defer gpa.free(lib_path);
    const windowed_prop = if (windowed) "-Disakai.windowed=true" else "-Disakai.windowed=false";
    const jvm_argv = try buildJvmArgv(gpa, java, lib_path, windowed_prop, cp, pkg.data_dir, app_arg, act_arg, pkg.name, title_arg, pkg.apk, app_theme_arg, act_theme_arg, target_sdk_arg, min_sdk_arg, intent_data);
    defer gpa.free(jvm_argv);
    defer gpa.free(jvm_argv[4]);
    defer gpa.free(jvm_argv[5]);
    // Desktop bridge: validated --bridge-dir/--stage-dir under
    // work/run/<pkg> + work/stage/<pkg>, pre-bound before the spawn.
    // ISAKAI_BRIDGE_DIR/ISAKAI_STAGE_DIR env overrides (validated) let
    // tests redirect; the sandbox gets ISAKAI_BRIDGE/ISAKAI_STAGE.
    const dirs = try bridgeDirs(gpa, work, pkg.name, std.os.linux.getuid());
    defer gpa.free(dirs.bridge);
    defer gpa.free(dirs.stage);
    const bridge_dir = env.get("ISAKAI_BRIDGE_DIR") orelse dirs.bridge;
    const stage_dir = env.get("ISAKAI_STAGE_DIR") orelse dirs.stage;
    if (!anchorWork(io, work)) {
        std.log.err("work dir is not an anchored directory: {s}", .{work});
        rollbackToInstalled(&reg, io, gpa, work, pkg);
        lock.release();
        return error.BadWorkDir;
    }
    if (!pathmod.validDir(bridge_dir) or !validAbsPath(stage_dir)) {
        std.log.err("bridge dir must be absolute {s}/{s}<uid>-<digest>; stage dir absolute without '..'", .{ pathmod.tmp_base, pathmod.dir_prefix });
        rollbackToInstalled(&reg, io, gpa, work, pkg);
        lock.release();
        return error.BadBridgeDir;
    }
    prebindBridgeDirs(io, bridge_dir, stage_dir) catch |err| {
        std.log.err("bridge dir unusable: {s} ({t})", .{ bridge_dir, err });
        rollbackToInstalled(&reg, io, gpa, work, pkg);
        lock.release();
        return error.BadBridgeDir;
    };
    // Spawn the bridge before the JVM so the socket is ready; it runs
    // as the user (not the app uid) with --uid for SO_PEERCRED gating.
    // ISAKAI_NO_BRIDGE=1 skips it (headless/bridge-less runs).
    const no_bridge = registry.envFlag(env, "ISAKAI_NO_BRIDGE");
    const bridge_path = try bridgeExe(gpa, exe);
    defer gpa.free(bridge_path);
    var bridge_child: ?std.process.Child = null;
    if (!no_bridge) {
        if (spawnBridge(io, gpa, bridge_path, bridge_dir, stage_dir, if (no_priv) std.os.linux.getuid() else pkg.uid)) |bc| {
            bridge_child = bc;
        } else |err| {
            std.log.warn("bridge spawn failed ({t}); continuing without desktop integration", .{err});
        }
        if (bridge_child != null) {
            if (!waitBridgeSocket(io, gpa, bridge_dir)) {
                std.log.warn("bridge socket {s}/bridge.sock never appeared (see bridge stderr above); continuing without desktop integration", .{bridge_dir});
                var bc = bridge_child.?;
                if (bc.id) |pid_v| linuxKill(pid_v, 15);
                _ = bc.wait(io) catch {};
                bridge_child = null;
            } else {
                std.log.info("desktop bridge ready at {s}/{s}", .{ bridge_dir, pathmod.sock_name });
            }
        }
    }
    // Sandbox env: ISAKAI_BRIDGE/ISAKAI_STAGE point at the pre-bound
    // dirs; DBUS_* is never propagated (the bridge owns the bus).
    // Privileged children get a minimal env from runAs anyway.
    var child_env = try cloneSandboxEnv(gpa, env, bridge_dir, stage_dir);
    defer child_env.deinit();
    // N6: the ATL loader resolves an app's own dependencies from
    // BIONIC_LD_LIBRARY_PATH; add the staged ABI slice (the shim appends
    // its own bionic/glibc dirs at first use).
    {
        const app_lib_dir = try std.fmt.allocPrint(gpa, "{s}/lib/x86_64", .{pkg.apk});
        defer gpa.free(app_lib_dir);
        if (child_env.get("BIONIC_LD_LIBRARY_PATH")) |prev| {
            const merged = try std.fmt.allocPrint(gpa, "{s}:{s}", .{ prev, app_lib_dir });
            defer gpa.free(merged);
            try child_env.put("BIONIC_LD_LIBRARY_PATH", merged);
        } else {
            try child_env.put("BIONIC_LD_LIBRARY_PATH", app_lib_dir);
        }
    }
    // Native exec: advertise the bionic runner so the shim's execve
    // interposer can run Android PIE subprocesses (an app's generator
    // binary) under the bionic loader instead of failing with ENOENT.
    // Sibling of exe like the bridge; skipped when absent. The path
    // outlives child_env (freed with the other launch-owned strings).
    const bionic_helper = if (std.fs.path.dirname(exe)) |dir|
        try std.fmt.allocPrint(gpa, "{s}/isakai-bionic-exec", .{dir})
    else
        try gpa.dupe(u8, "isakai-bionic-exec");
    defer gpa.free(bionic_helper);
    if (std.Io.Dir.cwd().access(io, bionic_helper, .{ .execute = true })) |_| {
        try child_env.put("ISAKAI_BIONIC_EXEC", bionic_helper);
    } else |_| {}
    // M6a: LD_PRELOAD the bionic shim so NDK app .so files that call
    // bionic-only symbols (__android_log_*, property_*) resolve. The
    // shim lives in framework/lib; only inject when it exists (dev
    // installs without a built framework skip it harmlessly).
    {
        const shim = try std.fmt.allocPrint(gpa, "{s}/lib/libisakai_bionic_shim.so", .{framework_dir});
        defer gpa.free(shim);
        const exists = blk: {
            std.Io.Dir.cwd().access(io, shim, .{}) catch break :blk false;
            break :blk true;
        };
        if (exists) {
            if (child_env.get("LD_PRELOAD")) |prev| {
                const merged = try std.fmt.allocPrint(gpa, "{s}:{s}", .{ shim, prev });
                defer gpa.free(merged);
                try child_env.put("LD_PRELOAD", merged);
            } else {
                try child_env.put("LD_PRELOAD", shim);
            }
        }
    }
    var child: std.process.Child = undefined;
    if (no_priv) {
        child = spawnWithEnv(io, jvm_argv, &child_env) catch |err| {
            rollbackToInstalled(&reg, io, gpa, work, pkg);
            lock.release();
            return err;
        };
    } else {
        child = spawnViaHelper(io, gpa, &child_env, &pkg, work, bridge_dir, .{
            .java = java,
            .windowed = windowed,
            .app_class = app_arg,
            .activity_class = act_arg,
            .title = title_arg,
            .app_theme = app_theme_arg,
            .activity_theme = act_theme_arg,
            .target_sdk = target_sdk_arg,
            .min_sdk = min_sdk_arg,
            .intent_data = intent_data,
        }, wayland_sock, framework_dir) catch |err| {
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
