const std = @import("std");
const dex = @import("dex");
pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    const bytes = try std.Io.Dir.cwd().readFileAlloc(io, argv[1], arena, .limited(64 * 1024 * 1024));
    const header = try dex.parseHeader(bytes);
    const d = dex.Dex{ .data = bytes, .header = header };
    for (0..d.header.class_defs_size) |i| {
        const def = try d.classDef(i);
        const desc = try d.getTypeDescriptor(arena, def.class_idx);
        if (!std.mem.eql(u8, desc, "LF/e;")) continue;
        const cdata = try dex.readClassData(arena, d, def.class_data_off);
        const groups = [_][]dex.EncodedMethod{ cdata.direct_methods, cdata.virtual_methods };
        for (groups) |g| for (g) |m| {
            const mid = try d.methodId(m.method_idx);
            const mn = try d.getString(arena, mid.name_idx);
            if (std.mem.eql(u8, mn, "A")) {
                std.debug.print("FOUND A: idx={d} code_off={x} flags={x}\n", .{ m.method_idx, m.code_off, m.access_flags });
                return 0;
            }
        };
        std.debug.print("F/e has {d}+{d} methods, no A\n", .{ cdata.direct_methods.len, cdata.virtual_methods.len });
        return 0;
    }
    return 1;
}
