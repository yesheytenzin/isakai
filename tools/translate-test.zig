const std = @import("std");
const dex = @import("dex");
const translate = @import("translate");

pub fn main(init: std.process.Init) !u8 {
    const io = init.io;
    const arena = init.arena.allocator();
    const argv = try init.minimal.args.toSlice(arena);
    var dump_lines = false;
    var keep_going = false;
    var positional: [2][]const u8 = undefined;
    var npos: usize = 0;
    for (argv[1..]) |a| {
        if (std.mem.eql(u8, a, "--dump-lines")) {
            dump_lines = true;
        } else if (std.mem.eql(u8, a, "--keep-going")) {
            keep_going = true;
        } else if (npos < 2) {
            positional[npos] = a;
            npos += 1;
        } else {
            std.debug.print("usage: translate-test [--dump-lines] [--keep-going] <classes.dex> <outdir>\n", .{});
            return 2;
        }
    }
    if (npos < 2) {
        std.debug.print("usage: translate-test [--dump-lines] [--keep-going] <classes.dex> <outdir>\n", .{});
        return 2;
    }
    const bytes = try std.Io.Dir.cwd().readFileAlloc(io, positional[0], arena, .limited(256 * 1024 * 1024));
    const header = dex.parseHeader(bytes) catch |err| {
        std.debug.print("PARSE FAIL: {t}\n", .{err});
        return 1;
    };
    const d = dex.Dex{ .data = bytes, .header = header };
    std.debug.print("dex: {d} strings {d} types {d} methods {d} classes\n", .{
        header.string_ids_size, header.type_ids_size, header.method_ids_size, header.class_defs_size,
    });

    var stdout_buf: [4096]u8 = undefined;
    var stdout_writer = std.Io.File.stdout().writer(io, &stdout_buf);
    const out = &stdout_writer.interface;
    defer out.flush() catch {};

    var ok: usize = 0;
    var failed: usize = 0;
    for (0..header.class_defs_size) |i| {
        const tc = translate.translateClass(arena, d, i) catch |err| {
            const def = d.classDef(i) catch {
                std.debug.print("TRANSLATE FAIL class {d}: {t} (classDef unreadable)\n", .{ i, err });
                if (!keep_going) return 1;
                failed += 1;
                continue;
            };
            const desc = d.getTypeDescriptor(arena, def.class_idx) catch "?";
            std.debug.print("TRANSLATE FAIL class {d} ({s}): {t}\n", .{ i, desc, err });
            if (!keep_going) return 1;
            failed += 1;
            continue;
        };
        const out_path = try std.fmt.allocPrint(arena, "{s}/{s}.class", .{ positional[1], tc.internal_name });
        if (std.fs.path.dirname(out_path)) |dir| {
            try std.Io.Dir.cwd().createDirPath(io, dir);
        }
        var f = try std.Io.Dir.cwd().createFile(io, out_path, .{});
        defer f.close(io);
        var buf: [8192]u8 = undefined;
        var fw = f.writer(io, &buf);
        try fw.interface.writeAll(tc.bytes);
        try fw.interface.flush();
        if (!keep_going) std.debug.print("wrote {s} ({d} bytes)\n", .{ out_path, tc.bytes.len });
        ok += 1;
        if (dump_lines) {
            try dumpLines(arena, out, d, i, tc.internal_name);
        }
    }
    if (keep_going) std.debug.print("keep-going: {d} ok, {d} failed of {d}\n", .{ ok, failed, header.class_defs_size });
    return if (failed > 0) 1 else 0;
}

/// Print "<class> <method> <start_pc> <line>" triples, one per LineNumberTable
/// entry, by re-translating each method body.
///
/// CONTRACT: traversal order MUST match translateClass exactly (direct
/// methods then virtual methods, skipping code_off == 0). oracle/lines.sh
/// diffs these triples against javap output; drift breaks the gate silently.
fn dumpLines(gpa: std.mem.Allocator, out: *std.Io.Writer, d: dex.Dex, def_idx: usize, internal: []const u8) !void {
    const def = try d.classDef(def_idx);
    var cdata = try dex.readClassData(gpa, d, def.class_data_off);
    defer cdata.deinit(gpa);
    const groups = [_][]dex.EncodedMethod{ cdata.direct_methods, cdata.virtual_methods };
    for (groups) |group| {
        for (group) |m| {
            if (m.code_off == 0) continue;
            const mid = try d.methodId(m.method_idx);
            const mname = try d.getString(gpa, mid.name_idx);
            defer gpa.free(mname);
            var c = try dex.readCode(gpa, d, m.code_off);
            defer c.deinit(gpa);
            var tm = try translate.translateMethod(gpa, d, c, mid.proto_idx, m.access_flags & 0x8 != 0);
            defer tm.deinit(gpa);
            for (tm.lines) |le| {
                try out.print("{s} {s} {d} {d}\n", .{ internal, mname, le.start_pc, le.line });
            }
        }
    }
}
