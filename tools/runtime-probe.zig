//! Unprivileged regression launcher for the production Landlock/seccomp policy.
//! Does not claim to test UID drops or mount namespaces (privileged E2E does).
//! `ISAKAI_BRIDGE` (when set) is the desktop-bridge endpoint the caller
//! prepared: it is granted read-only by the same rule the privileged helper
//! installs, and forwarded to the JVM so the probe can connect to
//! `<ISAKAI_BRIDGE>/bridge.sock` under confinement.
const std = @import("std");
const helper = @import("uid_helper");
pub fn main(init: std.process.Init) !void {
    const args = try init.minimal.args.toSlice(init.arena.allocator());
    if (args.len < 6) return error.UsageExpectedWorkPackageFrameworkJavaAndArgs;
    const work = args[1];
    const package = args[2];
    const data = try std.fmt.allocPrint(init.arena.allocator(), "{s}/data/{s}", .{ work, package });
    const bridge_dir = init.environ_map.get("ISAKAI_BRIDGE");
    const linux = std.os.linux;
    if (linux.errno(linux.prctl(38, 1, 0, 0, 0)) != .SUCCESS) return error.NoNewPrivsFailed;
    const data_z = try init.arena.allocator().dupeSentinel(u8, data, 0);
    if (linux.errno(linux.chdir(data_z)) != .SUCCESS) return error.ChdirFailed;
    try helper.applyRuntimeConfinement(data, work, package, bridge_dir, args[4], args[3], false);
    var argv: [64]?[*:0]const u8 = @splat(null);
    if (args.len - 4 >= argv.len) return error.TooManyArgs;
    for (args[4..], 0..) |arg, i| argv[i] = arg.ptr;
    const preload = try std.fmt.allocPrintSentinel(init.arena.allocator(), "LD_PRELOAD={s}/lib/libisakai_bionic_shim.so", .{args[3]}, 0);
    const app_home = try std.fmt.allocPrintSentinel(init.arena.allocator(), "HOME={s}", .{data}, 0);
    const bridge = try std.fmt.allocPrintSentinel(init.arena.allocator(), "ISAKAI_BRIDGE={s}", .{bridge_dir orelse ""}, 0);
    var env_buf: [4]?[*:0]const u8 = .{ preload.ptr, app_home.ptr, bridge.ptr, null };
    const env: [*:null]const ?[*:0]const u8 = @ptrCast(&env_buf);
    const rc = linux.execve(argv[0].?, @ptrCast(&argv), env);
    std.debug.print("runtime probe exec: {t}\n", .{linux.errno(rc)});
    linux.exit(127);
}
