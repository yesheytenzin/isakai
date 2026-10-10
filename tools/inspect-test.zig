const std = @import("std");
const install = @import("install");

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    const m = install.inspect(io, arena, argv[1]) catch |err| {
        std.debug.print("INSPECT FAIL: {t}\n", .{err});
        return 1;
    };
    std.debug.print("package={s} min={d} target={d} app={?s} launcher={?s} perms={d} services={d} apptheme=0x{x} acttheme=0x{x}\n", .{ m.package, m.min_sdk, m.target_sdk, m.application, m.launcher, m.permissions.items.len, m.services.items.len, m.app_theme, m.activity_theme });
    return 0;
}
