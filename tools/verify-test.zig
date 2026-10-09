const std = @import("std");
const apksig = @import("apksig");

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    if (argv.len < 2) {
        std.debug.print("usage: verify-test <apk>\n", .{});
        return 2;
    }
    const fp = apksig.verifyApk(io, arena, argv[1]) catch |err| {
        std.debug.print("VERIFY FAIL: {t}\n", .{err});
        return 1;
    };
    std.debug.print("fingerprint: {s}\n", .{fp});
    return 0;
}
