// SPDX-License-Identifier: GPL-3.0-or-later

//! Isakai: run Android apps as native Linux processes.
//! CLI entry: parse, dispatch a verb.

const std = @import("std");
const registry = @import("registry");
const install = @import("install");
const launch = @import("launch");
const upgrade = @import("upgrade");
const uid_helper = @import("uid_helper");
const version = "0.0.1";

const Verb = enum {
    install,
    run,
    upgrade,
    rollback,
    uninstall,
    launch,
    query,
    list,
    uid_helper,
    help,
    version,
};

fn parseVerb(arg: []const u8) ?Verb {
    if (std.mem.eql(u8, arg, "install")) return .install;
    if (std.mem.eql(u8, arg, "run")) return .run;
    if (std.mem.eql(u8, arg, "upgrade")) return .upgrade;
    if (std.mem.eql(u8, arg, "rollback")) return .rollback;
    if (std.mem.eql(u8, arg, "uninstall")) return .uninstall;
    if (std.mem.eql(u8, arg, "launch")) return .launch;
    if (std.mem.eql(u8, arg, "query")) return .query;
    if (std.mem.eql(u8, arg, "list")) return .list;
    if (std.mem.eql(u8, arg, "uid-helper")) return .uid_helper;
    if (std.mem.eql(u8, arg, "help")) return .help;
    if (std.mem.eql(u8, arg, "--help") or std.mem.eql(u8, arg, "-h")) return .help;
    if (std.mem.eql(u8, arg, "--version") or std.mem.eql(u8, arg, "-V")) return .version;
    return null;
}

fn printHelp(out: *std.Io.Writer) !void {
    try out.writeAll(
        \\isakai: run Android apps as native Linux processes
        \\
        \\usage: isakai [--work DIR] [-v|--verbose] [-q|--quiet] <verb> [args]
        \\
        \\verbs:
        \\  install <apk>        verify, stage and register an app
        \\  run <apk>            install an app and launch it
        \\  upgrade <apk>        verify and switch an installed app to a new build
        \\  rollback <package>   revert an upgrade to the previous build
        \\  uninstall <package>  remove an app and its data
        \\  launch <package>     start an app
        \\  query <package>      show an app's state
        \\  list                 list installed apps
        \\  help                 show this help
        \\
    );
}

fn defaultWork(env: *const std.process.Environ.Map, gpa: std.mem.Allocator) ![]u8 {
    if (env.get("ISAKAI_WORK")) |w| return try gpa.dupe(u8, w);
    const home = env.get("HOME") orelse return error.MissingHome;
    return std.fmt.allocPrint(gpa, "{s}/.local/share/isakai", .{home});
}

fn cmdList(io: std.Io, gpa: std.mem.Allocator, out: *std.Io.Writer, work: []const u8) !u8 {
    var reg = registry.Registry.load(io, gpa, work);
    defer reg.deinit();
    var names: std.ArrayListUnmanaged([]const u8) = .empty;
    defer names.deinit(gpa);
    var it = reg.packages.iterator();
    while (it.next()) |entry| try names.append(gpa, entry.key_ptr.*);
    std.mem.sort([]const u8, names.items, {}, struct {
        fn lt(_: void, a: []const u8, b: []const u8) bool {
            return std.mem.order(u8, a, b) == .lt;
        }
    }.lt);
    for (names.items) |name| {
        const pkg = reg.packages.get(name).?;
        const state: []const u8 = switch (pkg.state) {
            .installed => "installed",
            .starting => "starting",
            .running => "running",
            .exited => "exited",
            .removing => "removing",
        };
        try out.print("{s} uid={d} {s}\n", .{ name, pkg.uid, state });
    }
    return 0;
}

fn cmdQuery(
    io: std.Io,
    gpa: std.mem.Allocator,
    out: *std.Io.Writer,
    err_writer: *std.Io.Writer,
    work: []const u8,
    selector: []const u8,
) !u8 {
    var reg = registry.Registry.load(io, gpa, work);
    defer reg.deinit();
    const pkg = reg.resolve(gpa, selector) catch |err| switch (err) {
        error.Ambiguous => {
            try err_writer.print("error: '{s}' matches several packages\n", .{selector});
            return 1;
        },
        error.NotFound => {
            try err_writer.print("error: '{s}' is not installed\n", .{selector});
            return 1;
        },
        else => return err,
    };
    try out.print("name: {s}\nuid: {d}\ndata: {s}\napk: {s}\nstate: {t}\n", .{
        pkg.name, pkg.uid, pkg.data_dir, pkg.apk, pkg.state,
    });
    return 0;
}

/// Our own executable path for re-exec via the privileged helper,
/// falling back to "isakai" (PATH lookup) when unresolvable.
fn resolveExe(init: std.process.Init) struct { owned: ?[:0]u8, exe: []const u8 } {
    const exe_owned = std.process.executablePathAlloc(init.io, init.gpa) catch null;
    return .{ .owned = exe_owned, .exe = if (exe_owned) |e| e else "isakai" };
}

/// Refuse unless root (uid-helper entry points run only via pkexec/sudo).
/// Returns 1 after printing, else null to continue.
fn requireRoot(err_writer: *std.Io.Writer) !?u8 {
    if (std.os.linux.geteuid() != 0) {
        try err_writer.writeAll("uid-helper must run as root (via pkexec or sudo -n)\n");
        return 1;
    }
    return null;
}

fn cmdInstall(
    init: std.process.Init,
    out: *std.Io.Writer,
    err_writer: *std.Io.Writer,
    work: []const u8,
    apk_path: []const u8,
    launch_after_install: bool,
) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    const resolved = resolveExe(init);
    defer if (resolved.owned) |e| gpa.free(e);
    const exe = resolved.exe;
    const result = install.install(io, gpa, init.environ_map, exe, work, apk_path) catch |err| {
        try err_writer.print("install failed: {t}\n", .{err});
        return 1;
    };
    defer gpa.free(result.package);
    defer gpa.free(result.code_dir);
    defer gpa.free(result.data_dir);
    try out.print("installed {s} uid={d}\n  code: {s}\n  data: {s}\n", .{
        result.package, result.uid, result.code_dir, result.data_dir,
    });
    if (launch_after_install) {
        try out.flush();
        return cmdLaunch(init, out, err_writer, work, result.package, "");
    }
    return 0;
}

fn cmdUpgrade(
    init: std.process.Init,
    out: *std.Io.Writer,
    err_writer: *std.Io.Writer,
    work: []const u8,
    apk_path: []const u8,
) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    const result = upgrade.upgrade(io, gpa, init.environ_map, work, apk_path) catch |err| {
        try err_writer.print("upgrade failed: {t}\n", .{err});
        return 1;
    };
    defer gpa.free(result.package);
    defer gpa.free(result.code_dir);
    defer gpa.free(result.prev_code_dir);
    try out.print("upgraded {s}\n  code: {s}\n  prev: {s}\n", .{
        result.package, result.code_dir, result.prev_code_dir,
    });
    return 0;
}

fn cmdRollback(
    init: std.process.Init,
    out: *std.Io.Writer,
    err_writer: *std.Io.Writer,
    work: []const u8,
    selector: []const u8,
) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    const result = upgrade.rollback(io, gpa, work, selector) catch |err| {
        try err_writer.print("rollback failed: {t}\n", .{err});
        return 1;
    };
    defer gpa.free(result.package);
    defer gpa.free(result.code_dir);
    defer gpa.free(result.prev_code_dir);
    try out.print("rolled back {s}\n  code: {s}\n  prev: {s}\n", .{
        result.package, result.code_dir, result.prev_code_dir,
    });
    return 0;
}

fn cmdUninstall(
    init: std.process.Init,
    out: *std.Io.Writer,
    err_writer: *std.Io.Writer,
    work: []const u8,
    selector: []const u8,
) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    const resolved = resolveExe(init);
    defer if (resolved.owned) |e| gpa.free(e);
    const exe = resolved.exe;
    const name = install.uninstall(io, gpa, init.environ_map, exe, work, selector) catch |err| {
        try err_writer.print("uninstall failed: {t}\n", .{err});
        return 1;
    };
    defer gpa.free(name);
    try out.print("uninstalled {s}\n", .{name});
    return 0;
}

fn cmdLaunch(
    init: std.process.Init,
    out: *std.Io.Writer,
    err_writer: *std.Io.Writer,
    work: []const u8,
    selector: []const u8,
    intent_data: []const u8,
) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    const resolved = resolveExe(init);
    defer if (resolved.owned) |e| gpa.free(e);
    const exe = resolved.exe;
    // Framework location: ISAKAI_FRAMEWORK, else <work>/../framework
    // (dev layout), else /usr/share/isakai/framework (distro install).
    var fw_buf: [4096]u8 = undefined;
    const fw_default = std.fmt.bufPrint(&fw_buf, "{s}/../framework", .{work}) catch work;
    var fw: []const u8 = fw_default;
    if (init.environ_map.get("ISAKAI_FRAMEWORK")) |env_fw| {
        fw = env_fw;
    } else {
        std.Io.Dir.cwd().access(io, fw_default, .{}) catch {
            fw = "/usr/share/isakai/framework";
        };
    }
    const result = launch.launch(io, gpa, init.environ_map, exe, work, selector, fw, intent_data) catch |err| {
        try err_writer.print("launch failed: {t}\n", .{err});
        return 1;
    };
    if (result.code) |c| {
        try out.print("exited with code {d}\n", .{c});
        return if (c == 0) 0 else 1;
    }
    try out.writeAll("terminated by signal\n");
    return 1;
}

fn cmdUidHelper(
    init: std.process.Init,
    err_writer: *std.Io.Writer,
    sub: []const u8,
    package: []const u8,
    uid_s: []const u8,
    data_dir: []const u8,
    work: []const u8,
) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    if (try requireRoot(err_writer)) |code| return code;
    const uid = std.fmt.parseInt(u32, uid_s, 10) catch {
        try err_writer.writeAll("uid-helper: bad uid\n");
        return 2;
    };
    const args = uid_helper.AllocateArgs{ .package = package, .uid = uid, .data_dir = data_dir, .work = work };
    if (std.mem.eql(u8, sub, "allocate")) {
        uid_helper.allocate(gpa, io, args) catch |err| {
            try err_writer.print("uid-helper allocate failed: {t}\n", .{err});
            return 1;
        };
        return 0;
    } else if (std.mem.eql(u8, sub, "remove")) {
        uid_helper.remove(gpa, io, args) catch |err| {
            try err_writer.print("uid-helper remove failed: {t}\n", .{err});
            return 1;
        };
        return 0;
    } else {
        try err_writer.writeAll("usage: isakai uid-helper <allocate|remove> <package> <uid> <data-dir> <work>\n");
        return 2;
    }
}

fn cmdUidHelperRun(
    init: std.process.Init,
    err_writer: *std.Io.Writer,
    rest: []const [:0]const u8,
) !u8 {
    if (try requireRoot(err_writer)) |code| return code;
    var wayland_sock: ?[]const u8 = null;
    var bridge_dir: ?[]const u8 = null;
    var cursor: usize = 5;
    var confinement_disabled = false;
    if (rest.len > cursor + 1 and std.mem.eql(u8, rest[cursor], "--wayland-sock")) {
        wayland_sock = rest[cursor + 1];
        cursor += 2;
    }
    if (rest.len > cursor + 1 and std.mem.eql(u8, rest[cursor], "--bridge-dir")) {
        bridge_dir = rest[cursor + 1];
        cursor += 2;
    }
    if (rest.len > cursor and std.mem.eql(u8, rest[cursor], "--insecure-no-confinement")) {
        confinement_disabled = true;
        cursor += 1;
    }
    const names = [_][]const u8{
        "--code-dir", "--java",      "--framework",      "--windowed",   "--app-class", "--activity-class",
        "--title",    "--app-theme", "--activity-theme", "--target-sdk", "--min-sdk",   "--intent-data",
    };
    if (rest.len != cursor + names.len * 2) {
        try err_writer.writeAll("usage: isakai uid-helper run-jvm <package> <uid> <data-dir> <work> [socket flags] [--insecure-no-confinement] --code-dir D --java J --framework F --windowed 0|1 --app-class C --activity-class C --title T --app-theme N --activity-theme N --target-sdk N --min-sdk N --intent-data URI\n");
        return 2;
    }
    var values: [names.len][:0]const u8 = undefined;
    for (names, 0..) |name, i| {
        if (!std.mem.eql(u8, rest[cursor], name)) {
            try err_writer.print("uid-helper run-jvm: expected {s}\n", .{name});
            return 2;
        }
        values[i] = rest[cursor + 1];
        cursor += 2;
    }
    const uid = std.fmt.parseInt(u32, rest[2], 10) catch {
        try err_writer.writeAll("uid-helper run-jvm: bad uid\n");
        return 2;
    };
    const windowed = if (std.mem.eql(u8, values[3], "1")) true else if (std.mem.eql(u8, values[3], "0")) false else {
        try err_writer.writeAll("uid-helper run-jvm: windowed must be 0 or 1\n");
        return 2;
    };
    // ISAKAI_CONFINEMENT=0 no longer disables anything; only the explicit flag above does.
    return uid_helper.runJvm(init.gpa, .{
        .package = rest[1],
        .uid = uid,
        .data_dir = rest[3],
        .work = rest[4],
        .code_dir = values[0],
        .java = values[1],
        .framework = values[2],
        .windowed = windowed,
        .app_class = values[4],
        .activity_class = values[5],
        .title = values[6],
        .app_theme = values[7],
        .activity_theme = values[8],
        .target_sdk = values[9],
        .min_sdk = values[10],
        .intent_data = values[11],
        .wayland_sock = wayland_sock,
        .bridge_dir = bridge_dir,
    }, confinement_disabled, err_writer);
}

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const gpa = init.gpa;
    const arena = init.arena.allocator();

    var stdout_buf: [4096]u8 = undefined;
    var stdout_writer = std.Io.File.stdout().writer(io, &stdout_buf);
    const out = &stdout_writer.interface;
    defer out.flush() catch {};

    var stderr_buf: [1024]u8 = undefined;
    var stderr_writer = std.Io.File.stderr().writer(io, &stderr_buf);
    const err_writer = &stderr_writer.interface;
    defer err_writer.flush() catch {};

    const argv = try init.minimal.args.toSlice(arena);

    // Global flags before the verb.
    var work: ?[]const u8 = null;
    var idx: usize = 1;
    while (idx < argv.len) : (idx += 1) {
        const arg: []const u8 = argv[idx];
        if (std.mem.eql(u8, arg, "--work")) {
            idx += 1;
            if (idx >= argv.len) {
                try err_writer.writeAll("error: --work needs a directory\n");
                return 2;
            }
            work = argv[idx];
        } else if (std.mem.startsWith(u8, arg, "--work=")) {
            work = arg["--work=".len..];
        } else if (std.mem.eql(u8, arg, "-v") or std.mem.eql(u8, arg, "--verbose")) {
            // Reserved for M5 (log-level wiring); parsed so scripts don't break.
        } else if (std.mem.eql(u8, arg, "-q") or std.mem.eql(u8, arg, "--quiet")) {
            // Reserved for M5 (log-level wiring); parsed so scripts don't break.
        } else if (parseVerb(arg) != null) {
            break; // `--help`/`--version` are verbs, not flags.
        } else if (std.mem.eql(u8, arg, "--") or !std.mem.startsWith(u8, arg, "-")) {
            if (std.mem.eql(u8, arg, "--")) idx += 1;
            break;
        } else {
            try err_writer.print("error: unknown flag '{s}'\n", .{arg});
            return 2;
        }
    }

    // Registry writes rename by absolute path, and the privileged helper
    // anchors data dirs as <work>/data/<pkg>: a relative --work must be
    // resolved against the current directory here, once, instead of
    // panicking later inside the registry.
    const work_dir = blk: {
        const raw = if (work) |w| try arena.dupe(u8, w) else try defaultWork(init.environ_map, arena);
        if (std.fs.path.isAbsolute(raw)) break :blk raw;
        const cwd = try std.process.currentPathAlloc(io, arena);
        break :blk try std.fs.path.join(arena, &.{ cwd, raw });
    };

    if (idx >= argv.len) {
        try printHelp(out);
        return 0;
    }
    const verb_arg: []const u8 = argv[idx];
    const verb = parseVerb(verb_arg) orelse {
        try err_writer.print("error: unknown verb '{s}'\n", .{verb_arg});
        try printHelp(err_writer);
        return 2;
    };
    const rest = argv[idx + 1 ..];

    switch (verb) {
        .help => {
            try printHelp(out);
            return 0;
        },
        .version => {
            try out.print("isakai {s}\n", .{version});
            return 0;
        },
        .list => return cmdList(io, gpa, out, work_dir),
        .query => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai query <package>\n");
                return 2;
            }
            return cmdQuery(io, gpa, out, err_writer, work_dir, rest[0]);
        },
        .install => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai install <apk>\n");
                return 2;
            }
            return cmdInstall(init, out, err_writer, work_dir, rest[0], false);
        },
        .run => {
            if (rest.len != 1) {
                try err_writer.writeAll("usage: isakai run <apk>\n");
                return 2;
            }
            if (init.environ_map.get("ISAKAI_WINDOW") == null and init.environ_map.get("WAYLAND_DISPLAY") != null) {
                try init.environ_map.put("ISAKAI_WINDOW", "1");
            }
            return cmdInstall(init, out, err_writer, work_dir, rest[0], true);
        },
        .upgrade => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai upgrade <apk>\n");
                return 2;
            }
            return cmdUpgrade(init, out, err_writer, work_dir, rest[0]);
        },
        .rollback => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai rollback <package>\n");
                return 2;
            }
            return cmdRollback(init, out, err_writer, work_dir, rest[0]);
        },
        .uninstall => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai uninstall <package>\n");
                return 2;
            }
            return cmdUninstall(init, out, err_writer, work_dir, rest[0]);
        },
        .launch => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai launch <package> [--data URI]\n");
                return 2;
            }
            var intent_data: []const u8 = "";
            var arg_index: usize = 1;
            while (arg_index < rest.len) {
                if (std.mem.eql(u8, rest[arg_index], "--data")) {
                    if (arg_index + 1 >= rest.len) {
                        try err_writer.writeAll("usage: isakai launch <package> [--data URI]\n");
                        return 2;
                    }
                    intent_data = rest[arg_index + 1];
                    arg_index += 2;
                } else {
                    try err_writer.print("error: unknown launch argument '{s}'\n", .{rest[arg_index]});
                    try err_writer.writeAll("usage: isakai launch <package> [--data URI]\n");
                    return 2;
                }
            }
            return cmdLaunch(init, out, err_writer, work_dir, rest[0], intent_data);
        },
        .uid_helper => {
            if (rest.len < 1) {
                try err_writer.writeAll("usage: isakai uid-helper <allocate|remove|run> ...\n");
                return 2;
            }
            if (std.mem.eql(u8, rest[0], "run-jvm")) {
                return cmdUidHelperRun(init, err_writer, rest);
            }
            // isakai uid-helper <allocate|remove> <package> <uid> <data-dir> <work>
            if (rest.len < 5) {
                try err_writer.writeAll("usage: isakai uid-helper <allocate|remove> <package> <uid> <data-dir> <work>\n");
                return 2;
            }
            return cmdUidHelper(init, err_writer, rest[0], rest[1], rest[2], rest[3], rest[4]);
        },
    }
}
