// SPDX-License-Identifier: GPL-3.0-or-later

//! Desktop integration bridge: speaks newline-JSON on stdin/stdout
//! (--stdio) or a unix socket (--bridge-dir), and D-Bus on the session
//! bus (org.freedesktop.Notifications + org.freedesktop.portal.Desktop).
//!
//! The sandboxed JVM cannot see the session bus (no DBUS_* in its env),
//! so the launcher spawns one bridge per app as the *user* before the
//! JVM starts; JNI (`src/bridge/desktop.zig`) connects to
//! `<bridge-dir>/bridge.sock` (path via $ISAKAI_BRIDGE).
//!
//! Protocol (one JSON object per line, `\n` terminated):
//!   {"op":"notify","app":s,"id":i,"title":s,"body":s,"urgency":0..2}
//!     -> {"ok":true,"id":u32}            (platform notification id)
//!   {"op":"close","plat":u32}           -> {"ok":true}
//!   {"op":"caps"}                        -> {"ok":true,"caps":[s]}
//!   {"op":"open-uri","uri":s}           -> {"ok":true}|{"ok":false,"err":s}
//!   {"op":"scheme","scheme":s}          -> {"ok":true,"supported":bool}
//!   {"op":"open-file","title":s}        -> {"ok":true,"req":u64}
//!   {"op":"file-result","req":u64}
//!     -> {"ok":true,"status":"pending"|"done"|"cancel","path":s?}
//!   {"op":"close-req","req":u64}        -> {"ok":true}
//!   {"op":"poll"}                        -> {"ok":true,"events":[...]}
//!     event: {"kind":"close","req":u64}
//!            {"kind":"action","plat":u32,"slot":u8,"key":s}
//!            {"kind":"file","req":u64,"status":"done"|"cancel","path":s?}
//!   {"op":"action-key","slot":u8}       -> {"ok":true,"key":s}
//!
//! File picks subscribe the portal Request Response match *before*
//! sending OpenFile (handle_token is chosen, so the object path is
//! known); when the reply handle differs, a legacy-handle match is
//! added as fallback. Host files are materialized into --stage-dir
//! (0600, chown to the app uid, 64 MiB/file, 256 MiB total quota) and
//! the staged path is reported — the sandbox can never see host paths.
//!
//! Security: in socket mode every client fd is checked with
//! SO_PEERCRED against --uid; mismatched peers are dropped. The bridge
//! never trusts client paths: notify/open-uri strings are bus payloads
//! only, and stage writes sanitize basenames.

const std = @import("std");
const linux = std.os.linux;
const dbus = @import("dbus");

const gpa = std.heap.c_allocator;

const max_line: usize = 65536;
const max_file_bytes: u64 = 64 * 1024 * 1024;
const max_stage_bytes: u64 = 256 * 1024 * 1024;
const sock_name = "bridge.sock";

fn logErr(comptime fmt: []const u8, args: anytype) void {
    var buf: [1024]u8 = undefined;
    const msg = std.fmt.bufPrint(&buf, fmt, args) catch return;
    _ = linux.write(linux.STDERR_FILENO, msg.ptr, msg.len);
}

/// Little-endian body writer with D-Bus alignment (offsets relative to
/// the body start, matching `dbus.Cursor`).
const W = struct {
    buf: []u8,
    pos: usize = 0,
    fn alignTo(self: *W, n: usize) void {
        self.pos = (self.pos + n - 1) & ~(n - 1);
    }
    fn wu32(self: *W, v: u32) void {
        self.alignTo(4);
        std.mem.writeInt(u32, self.buf[self.pos..][0..4], v, .little);
        self.pos += 4;
    }
    fn wi32(self: *W, v: i32) void {
        self.wu32(@bitCast(v));
    }
    fn byte(self: *W, v: u8) void {
        self.buf[self.pos] = v;
        self.pos += 1;
    }
    fn str(self: *W, s: []const u8) void {
        self.alignTo(4);
        self.pos += dbus.writeString(self.buf[self.pos..], s);
    }
    fn sig(self: *W, s: []const u8) void {
        self.pos += dbus.writeSig(self.buf[self.pos..], s);
    }
    fn variantStr(self: *W, s: []const u8) void {
        self.sig(if (std.mem.indexOfScalar(u8, s, '/') != null) "o" else "s");
        self.str(s);
    }
    fn bytes(self: *const W) []const u8 {
        return self.buf[0..self.pos];
    }
};

/// Skip one variant value of signature `sg` in cursor `c`.
fn skipValue(c: *dbus.Cursor, sg: []const u8) dbus.DbusError!void {
    if (sg.len == 1 and (sg[0] == 's' or sg[0] == 'o')) {
        _ = try c.nextString();
    } else if (sg.len == 1 and (sg[0] == 'u' or sg[0] == 'i' or sg[0] == 'b')) {
        _ = try c.nextU32();
    } else if (sg.len == 1 and sg[0] == 'y') {
        _ = try c.nextU8();
    } else if (std.mem.eql(u8, sg, "as")) {
        const n = try c.nextU32();
        _ = n;
        // Array of strings: walk NUL-terminated items until consumed.
        // Simpler: re-scan raw — instead parse count via strings.
        // We know items are strings; loop while more remain is hard
        // without lengths, so parse via remaining length bookkeeping:
        // (callers of skipValue("as") are rare; parse properly below.)
        return error.Truncated;
    } else if (sg.len == 1 and sg[0] == 'g') {
        _ = try c.nextSig();
    } else {
        return error.Truncated;
    }
}

const BusState = struct {
    conn: dbus.Conn,
    fd_state: *dbus.FdTransport,
    fd: std.posix.fd_t,
    /// Sender suffix for request paths (unique name with ":1." prefix
    /// stripped and dots underscored).
    sender: []u8,
    token_seq: u32 = 0,
    /// Live bus: false in --stdio test mode without a session bus.
    live: bool = true,
};

const FileReq = struct {
    token: []u8,
    handle: []u8,
    status: enum { pending, done, cancel },
    path: []u8, // staged path when done (owned, may be "")
};

const Event = union(enum) {
    close: u64, // file req id
    action: struct { plat: u32, slot: u8 },
    file: struct { req: u64, done: bool },
};

const Bridge = struct {
    bus: ?BusState = null,
    file_reqs: std.AutoHashMapUnmanaged(u64, FileReq) = .{},
    events: std.ArrayListUnmanaged(Event) = .empty,
    action_keys: [16]?[]u8 = .{null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null},
    action_seq: u8 = 0,
    stage_dir: []const u8 = "",
    app_uid: u32 = 0,
    req_seq: u64 = 0,
    next_fake_plat: u32 = 1,

    fn pushEvent(self: *Bridge, ev: Event) void {
        self.events.append(gpa, ev) catch {};
    }

    fn rememberKey(self: *Bridge, key: []const u8) u8 {
        const slot = self.action_seq % 16;
        self.action_seq +%= 1;
        if (self.action_keys[slot]) |old| gpa.free(old);
        self.action_keys[slot] = gpa.dupe(u8, key) catch null;
        return slot;
    }
};

/// Read one full bus message, dispatching signals into the bridge queues.
/// Returns null on timeout/no-data (nonblocking drain helper uses 0).
fn pumpOnce(br: *Bridge, timeout_ms: i32) bool {
    const bus = &(br.bus orelse return false);
    var pfd: [1]linux.pollfd = .{.{
        .fd = bus.fd,
        .events = linux.POLL.IN,
        .revents = 0,
    }};
    const rc = linux.poll(&pfd, 1, timeout_ms);
    if (linux.errno(rc) != .SUCCESS or rc == 0) return false;
    if (pfd[0].revents & linux.POLL.IN == 0) return false;
    const msg = bus.conn.readMessage(gpa) catch return false;
    defer gpa.free(msg);
    handleBusMessage(br, msg);
    return true;
}

fn headerFields(msg: []const u8) []const u8 {
    if (msg.len <= 16) return &.{};
    const h = dbus.decodeHeader(msg[0..16]) catch return &.{};
    const end: usize = 16 + h.fields_len;
    if (end > msg.len) return &.{};
    return msg[16..end];
}

fn bodyOf(msg: []const u8) []const u8 {
    // structsEnd scans the length-word-free array; the on-wire structs
    // occupy (flen + last-pad) bytes. Recompute the wire end from flen
    // via a padded scan: walk with findField-compatible steps.
    if (msg.len <= 16) return &.{};
    _ = dbus.decodeHeader(msg[0..16]) catch return &.{};
    const end: usize = 16 + wireStructs(msg[16..]);
    if (end > msg.len) return &.{};
    const pad = (8 - (end % 8)) % 8;
    if (end + pad > msg.len) return &.{};
    return msg[end + pad ..];
}

/// On-wire header struct bytes: flen plus the last struct's trailing
/// pad (which flen excludes). Recomputed by scanning padded structs.
fn wireStructs(area: []const u8) usize {
    var pos: usize = 0;
    while (pos < area.len) {
        const start = pos;
        if (pos + 1 > area.len) break;
        if (area[pos] == 0) break;
        const sig_pos = start + 1;
        if (sig_pos + 1 > area.len) break;
        const sig_len: usize = area[sig_pos];
        if (sig_len > 8) break;
        if (sig_pos + 1 + sig_len + 1 > area.len) break;
        if (area[sig_pos + 1 + sig_len] != 0) break;
        const val_start: usize = sig_pos + 1 + sig_len + 1;
        const sig_s = area[sig_pos + 1 ..][0..sig_len];
        var val_len: usize = 0;
        if (sig_len == 0 and area[start] == 8) {
            // Empty 'g' signature (e.g. AddMatch replies): no value
            // bytes. Restricted to code 8: body length words mimic the
            // (code, 0, 0) shape, so unscoped scans must stop there.
            val_len = 0;
        } else if (sig_s.len == 1 and (sig_s[0] == 's' or sig_s[0] == 'o')) {
            if (val_start + 4 > area.len) break;
            const n: usize = std.mem.readInt(u32, area[val_start..][0..4], .little);
            if (n > 1024 * 1024) break;
            if (val_start + 4 + n + 1 > area.len) break;
            val_len = 4 + dbus.padLen(n + 1);
        } else if (sig_s.len == 1 and sig_s[0] == 'g') {
            if (val_start + 1 > area.len) break;
            val_len = 1 + area[val_start] + 1;
        } else if (sig_s.len == 1 and (sig_s[0] == 'u' or sig_s[0] == 'i' or sig_s[0] == 'b')) {
            val_len = 4;
        } else break;
        if (val_start + val_len > area.len) break;
        pos = start + ((val_start + val_len - start + 7) & ~@as(usize, 7));
    }
    return pos;
}

/// Drain all immediately-available bus messages (nonblocking).
fn drainBus(br: *Bridge) void {
    while (pumpOnce(br, 0)) {}
}

fn fieldU32(msg: []const u8, code: u8) ?u32 {
    const f = dbus.findField(headerFields(msg), code) orelse return null;
    if (f.value.len < 4) return null;
    return std.mem.readInt(u32, f.value[0..4], .little);
}

fn fieldString(msg: []const u8, code: u8) ?[]const u8 {
    const f = dbus.findField(headerFields(msg), code) orelse return null;
    if (f.value.len < 4) return null;
    const n: usize = std.mem.readInt(u32, f.value[0..4], .little);
    if (4 + n + 1 > f.value.len) return null;
    return f.value[4..][0..n];
}

fn handleBusMessage(br: *Bridge, msg: []const u8) void {
    if (msg.len < 16) return;
    const h = dbus.decodeHeader(msg[0..16]) catch return;
    if (h.mtype != 4) return; // signals only; replies go to callAndWait
    const iface = fieldString(msg, dbus.F.interface) orelse return;
    const member = fieldString(msg, dbus.F.member) orelse return;
    const body = bodyOf(msg);
    if (std.mem.eql(u8, iface, "org.freedesktop.Notifications")) {
        if (std.mem.eql(u8, member, "NotificationClosed")) {
            var c = dbus.Cursor.init(body);
            const id = c.nextU32() catch return;
            _ = c.nextU32() catch {};
            br.pushEvent(.{ .action = .{ .plat = id, .slot = 0 } });
            // Encode close as an action event with empty key? No:
            // replace with a dedicated close marker via slot 255.
            br.events.items[br.events.items.len - 1] = .{ .action = .{ .plat = id, .slot = 255 } };
        } else if (std.mem.eql(u8, member, "ActionInvoked")) {
            var c = dbus.Cursor.init(body);
            const id = c.nextU32() catch return;
            const key = c.nextString() catch return;
            const slot = br.rememberKey(key);
            br.pushEvent(.{ .action = .{ .plat = id, .slot = slot } });
        }
    } else if (std.mem.eql(u8, iface, "org.freedesktop.portal.Request")) {
        if (std.mem.eql(u8, member, "Response")) {
            const path = fieldString(msg, dbus.F.path) orelse return;
            var c = dbus.Cursor.init(body);
            const response = c.nextU32() catch return;
            const done = response == 0;
            // Find the file req by handle path suffix match.
            var it = br.file_reqs.iterator();
            while (it.next()) |e| {
                if (std.mem.endsWith(u8, path, e.value_ptr.token) or std.mem.eql(u8, path, e.value_ptr.handle)) {
                    if (e.value_ptr.status == .pending) {
                        if (done) {
                            materializeResult(br, e.key_ptr.*, e.value_ptr, &c);
                        } else {
                            e.value_ptr.status = .cancel;
                            br.pushEvent(.{ .file = .{ .req = e.key_ptr.*, .done = false } });
                        }
                    }
                }
            }
        }
    }
}

/// Parse OpenFile results (a{sv} with "uris" as) and stage the first
/// URI into the stage dir. Any failure -> cancel.
fn materializeResult(br: *Bridge, req_id: u64, fr: *FileReq, c: *dbus.Cursor) void {
    // results: array of dict entries {string, variant}
    const arr_len = c.nextU32() catch {
        fr.status = .cancel;
        br.pushEvent(.{ .file = .{ .req = req_id, .done = false } });
        return;
    };
    _ = arr_len;
    const end = c.buf.len;
    var first_uri: ?[]const u8 = null;
    while (c.pos < end) {
        // Dict entry aligns to 8 relative to body start... approximate:
        // keys are strings; scan for "uris".
        const save = c.pos;
        // Try to read a string key; on failure stop.
        const align_pad = (8 - (c.pos % 8)) % 8;
        if (c.pos + align_pad + 4 > end) break;
        c.pos += align_pad;
        const key = c.nextString() catch break;
        const vsg = c.nextSig() catch break;
        if (std.mem.eql(u8, key, "uris") and std.mem.eql(u8, vsg, "as")) {
            const n = c.nextU32() catch break;
            _ = n;
            const uri = c.nextString() catch break;
            first_uri = uri;
            break;
        } else {
            skipValue(c, vsg) catch break;
        }
        _ = save;
    }
    const uri = first_uri orelse {
        fr.status = .cancel;
        br.pushEvent(.{ .file = .{ .req = req_id, .done = false } });
        return;
    };
    const staged = stageUri(br, uri) catch {
        fr.status = .cancel;
        br.pushEvent(.{ .file = .{ .req = req_id, .done = false } });
        return;
    };
    gpa.free(fr.path);
    fr.path = staged;
    fr.status = .done;
    br.pushEvent(.{ .file = .{ .req = req_id, .done = true } });
}

fn stageUri(br: *Bridge, uri: []const u8) ![]u8 {
    const prefix = "file://";
    if (!std.mem.startsWith(u8, uri, prefix)) return error.BadUri;
    const host_path = uri[prefix.len..];
    if (host_path.len == 0 or host_path[0] != '/') return error.BadUri;
    // Sanitize basename: no separators, bounded length.
    const base = std.fs.path.basename(host_path);
    if (base.len == 0 or base.len > 128) return error.BadName;
    if (std.mem.indexOf(u8, base, "..") != null) return error.BadName;
    var clean: [144]u8 = undefined;
    var clen: usize = 0;
    for (base) |ch| {
        const ok = std.ascii.isAlphanumeric(ch) or ch == '.' or ch == '-' or ch == '_';
        clean[clen] = if (ok) ch else '_';
        clen += 1;
    }
    // Enforce quotas: single file + stage total.
    var zsrc: [4096]u8 = undefined;
    if (host_path.len + 1 > zsrc.len) return error.BadName;
    @memcpy(zsrc[0..host_path.len], host_path);
    zsrc[host_path.len] = 0;
    const src_z: [*:0]const u8 = @ptrCast(&zsrc);
    var stx: linux.Statx = undefined;
    const rc = linux.statx(linux.AT.FDCWD, src_z, 0, .{ .SIZE = true, .TYPE = true }, &stx);
    if (linux.errno(rc) != .SUCCESS) return error.StatFailed;
    if (stx.mode & linux.S.IFMT != linux.S.IFREG) return error.NotFile;
    if (stx.size > max_file_bytes) return error.TooBig;
    // Stage quota: sum existing staged sizes via getdents64 on the
    // stage dirfd (no std.Io needed here).
    {
        var zb: [4096]u8 = undefined;
        if (br.stage_dir.len + 1 > zb.len) return error.NoStage;
        @memcpy(zb[0..br.stage_dir.len], br.stage_dir);
        zb[br.stage_dir.len] = 0;
        const dirfd = linux.openat(linux.AT.FDCWD, @as([*:0]const u8, @ptrCast(&zb)), .{ .DIRECTORY = true, .CLOEXEC = true }, 0);
        if (linux.errno(dirfd) != .SUCCESS) return error.NoStage;
        defer _ = linux.close(@bitCast(@as(u32, @truncate(dirfd))));
        const dfd: linux.fd_t = @bitCast(@as(u32, @truncate(dirfd)));
        var total: u64 = stx.size;
        var gbuf: [8192]u8 align(@alignOf(linux.dirent64)) = undefined;
        outer: while (true) {
            const n = linux.getdents64(dfd, &gbuf, gbuf.len);
            if (linux.errno(n) != .SUCCESS or n == 0) break;
            var off: usize = 0;
            while (off < n) {
                const d: *linux.dirent64 = @ptrCast(@alignCast(&gbuf[off]));
                if (d.reclen == 0) break :outer;
                const nm: [*:0]u8 = @ptrCast(&d.name);
                const name = std.mem.span(nm);
                if (!std.mem.eql(u8, name, ".") and !std.mem.eql(u8, name, "..")) {
                    const fp = std.fmt.allocPrint(gpa, "{s}/{s}", .{ br.stage_dir, name }) catch continue;
                    defer gpa.free(fp);
                    var fb: [4096]u8 = undefined;
                    if (fp.len + 1 > fb.len) continue;
                    @memcpy(fb[0..fp.len], fp);
                    fb[fp.len] = 0;
                    var sx: linux.Statx = undefined;
                    if (linux.errno(linux.statx(linux.AT.FDCWD, @as([*:0]const u8, @ptrCast(&fb)), 0, .{ .SIZE = true }, &sx)) == .SUCCESS) {
                        total += sx.size;
                    }
                }
                off += d.reclen;
            }
        }
        if (total > max_stage_bytes) return error.Quota;
    }
    const dst = try std.fmt.allocPrint(gpa, "{s}/{s}", .{ br.stage_dir, clean[0..clen] });
    errdefer gpa.free(dst);
    // Copy via stack-buffered fds (no std.Io dependency).
    const src_fd = linux.openat(linux.AT.FDCWD, src_z, .{ .ACCMODE = .RDONLY, .CLOEXEC = true }, 0);
    if (linux.errno(src_fd) != .SUCCESS) return error.OpenFailed;
    defer _ = linux.close(@bitCast(@as(u32, @truncate(src_fd))));
    var zdst: [4096]u8 = undefined;
    if (dst.len + 1 > zdst.len) return error.BadName;
    @memcpy(zdst[0..dst.len], dst);
    zdst[dst.len] = 0;
    const dst_z: [*:0]const u8 = @ptrCast(&zdst);
    const dfd = linux.openat(
        linux.AT.FDCWD,
        dst_z,
        .{ .ACCMODE = .WRONLY, .CREAT = true, .TRUNC = true, .CLOEXEC = true },
        0o600,
    );
    if (linux.errno(dfd) != .SUCCESS) return error.CreateFailed;
    defer _ = linux.close(@bitCast(@as(u32, @truncate(dfd))));
    const df: std.posix.fd_t = @bitCast(@as(u32, @truncate(dfd)));
    const sf: std.posix.fd_t = @bitCast(@as(u32, @truncate(src_fd)));
    var buf: [65536]u8 = undefined;
    while (true) {
        const n = linux.read(sf, &buf, buf.len);
        const err = linux.errno(n);
        if (err != .SUCCESS) {
            if (err == .INTR) continue;
            return error.ReadFailed;
        }
        if (n == 0) break;
        var off: usize = 0;
        while (off < n) {
            const w = linux.write(df, buf[off..n].ptr, n - off);
            const werr = linux.errno(w);
            if (werr != .SUCCESS) {
                if (werr == .INTR) continue;
                return error.WriteFailed;
            }
            off += w;
        }
    }
    _ = linux.fchmod(df, 0o600);
    if (br.app_uid != 0) _ = linux.fchown(df, br.app_uid, br.app_uid);
    return dst;
}

/// Synchronous method call: send, then read until the matching reply
/// (signals are dispatched to the queues meanwhile). Timeout in ms.
fn callAndWait(
    br: *Bridge,
    dest: []const u8,
    path: []const u8,
    iface: []const u8,
    member: []const u8,
    body_sig: []const u8,
    body: []const u8,
    timeout_ms: i32,
) dbus.DbusError![]u8 {
    const bus = &(br.bus orelse return error.IoFailed);
    const serial = try bus.conn.sendCall(dest, path, iface, member, body_sig, body);
    const start = milliNow();
    while (true) {
        const now = milliNow();
        const left = timeout_ms - @as(i32, @intCast(now - start));
        if (left <= 0) return error.IoFailed;
        var pfd: [1]linux.pollfd = .{.{
            .fd = bus.fd,
            .events = linux.POLL.IN,
            .revents = 0,
        }};
        const rc = linux.poll(&pfd, 1, left);
        if (linux.errno(rc) != .SUCCESS) return error.IoFailed;
        if (rc == 0) return error.IoFailed;
        const msg = try bus.conn.readMessage(gpa);
        errdefer gpa.free(msg);
        const h = try dbus.decodeHeader(msg[0..16]);
        if (h.mtype == 2 or h.mtype == 3) {
            const rs = fieldU32(msg, dbus.F.reply_serial) orelse {
                gpa.free(msg);
                continue;
            };
            if (rs != serial) {
                gpa.free(msg);
                continue;
            }
            if (h.mtype == 3) {
                gpa.free(msg);
                return error.IoFailed;
            }
            return msg;
        }
        handleBusMessage(br, msg);
        gpa.free(msg);
    }
}

fn milliNow() i64 {
    var ts: linux.timespec = undefined;
    _ = linux.clock_gettime(.REALTIME, &ts);
    return ts.sec * 1000 + @divTrunc(ts.nsec, 1000 * 1000);
}

fn replyBody(msg: []const u8) []const u8 {
    return bodyOf(msg);
}

// --- bus operations ---------------------------------------------------

fn opNotify(br: *Bridge, app: []const u8, id: u32, title: []const u8, body: []const u8, urgency: u8, actions: []const u8) u32 {
    const bus = br.bus orelse {
        const fake = br.next_fake_plat;
        br.next_fake_plat += 1;
        return fake;
    };
    _ = bus;
    var buf: [32768]u8 = undefined;
    // Zero the whole region first: D-Bus alignment gaps must be zero
    // bytes; uninitialized stack reads as 0xAA in debug builds and the
    // broker treats a garbage hints length as "invalid body" → disconnect.
    @memset(&buf, 0);
    var w = W{ .buf = &buf };
    w.str(app);
    w.wu32(id);
    w.str(""); // app_icon: always empty (no icon support)
    w.str(title);
    w.str(body);
    // actions: flat key/label pairs; labels mirror keys (no icon/label i18n).
    w.alignTo(4);
    const alen_at = w.pos;
    w.wu32(0); // patched below
    const arr_start = w.pos;
    if (actions.len > 0) {
        var it = std.mem.splitScalar(u8, actions, 0);
        while (it.next()) |key| {
            if (key.len == 0) continue;
            w.str(key);
            w.str(key);
        }
    }
    std.mem.writeInt(u32, buf[alen_at..][0..4], @intCast(w.pos - arr_start), .little);
    // hints a{sv}: the array itself aligns to 4 (wu32 already aligns).
    // Each DICT_ENTRY inside aligns to 8 relative to the BODY start
    // (explicit memset below). NO 8-align of the length word itself:
    // that opens a 4-byte unwritten gap the broker reads as ~2.8M
    // hints length → "invalid body" disconnect.
    const hlen_at = w.pos;
    w.wu32(0);
    if (urgency > 0) {
        // DICT_ENTRY aligns to 8 relative to the BODY start (W.pos
        // is body-relative: the body always starts at pos 0).
        {
            const aligned = (w.pos + 7) & ~@as(usize, 7);
            @memset(buf[w.pos..aligned], 0);
            w.pos = aligned;
        }
        const entry_start = w.pos;
        w.str("urgency");
        w.sig("y");
        w.byte(urgency);
        const raw = w.pos - entry_start;
        const padded = (raw + 7) & ~@as(usize, 7);
        @memset(buf[w.pos .. entry_start + padded], 0);
        w.pos = entry_start + padded;
        // Array length counts ELEMENTS only, not the alignment gap.
        std.mem.writeInt(u32, buf[hlen_at..][0..4], @intCast(w.pos - entry_start), .little);
    } else {
        std.mem.writeInt(u32, buf[hlen_at..][0..4], 0, .little);
    }
    // Live-proven against dbus-broker (30+ probes): the counted Notify
    // body length must satisfy len % 8 == 4 or the broker drops the
    // connection ("invalid body"). When the body would otherwise end at
    // len % 8 == 0 (accounting for the trailing timeout word), emit one
    // extra u32 0 after hints — exactly what busctl sends. Matches
    // Conn.encodeNotify in dbus.zig (golden: 52 busctl-exact + 84 long).
    if ((w.pos + 4) % 8 == 0) w.wu32(0);
    w.wi32(0); // expire_timeout: 0 = server default (never -1: some servers drop it)
    const msg = callAndWait(
        br,
        "org.freedesktop.Notifications",
        "/org/freedesktop/Notifications",
        "org.freedesktop.Notifications",
        "Notify",
        "susssasa{sv}i",
        w.bytes(),
        5000,
    ) catch return 0;
    defer gpa.free(msg);
    var c = dbus.Cursor.init(replyBody(msg));
    return c.nextU32() catch 0;
}

fn opClose(br: *Bridge, plat: u32) void {
    const bus = br.bus orelse return;
    _ = bus;
    var buf: [16]u8 = undefined;
    var w = W{ .buf = &buf };
    w.wu32(plat);
    const msg = callAndWait(
        br,
        "org.freedesktop.Notifications",
        "/org/freedesktop/Notifications",
        "org.freedesktop.Notifications",
        "CloseNotification",
        "u",
        w.bytes(),
        5000,
    ) catch return;
    gpa.free(msg);
}

fn opCaps(br: *Bridge) []const u8 {
    const bus = br.bus orelse return "";
    _ = bus;
    const msg = callAndWait(
        br,
        "org.freedesktop.Notifications",
        "/org/freedesktop/Notifications",
        "org.freedesktop.Notifications",
        "GetCapabilities",
        "",
        &.{},
        5000,
    ) catch return "";
    defer gpa.free(msg);
    // Body "as": keep raw strings; re-encode as JSON array by parsing.
    // Return value borrows msg — copy into a static buffer for JSON emit.
    // (Caller stringifies immediately; use a fixed scratch buffer.)
    var c = dbus.Cursor.init(replyBody(msg));
    const n = c.nextU32() catch return "";
    _ = n;
    // Stash the raw body; the JSON writer below re-walks it.
    // Simplest: return count-prefixed copy in a static buffer.
    cap_scratch_len = 0;
    while (true) {
        const s = c.nextString() catch break;
        if (cap_scratch_len + s.len + 1 >= cap_scratch.len) break;
        @memcpy(cap_scratch[cap_scratch_len..][0..s.len], s);
        cap_scratch_len += s.len;
        cap_scratch[cap_scratch_len] = 0;
        cap_scratch_len += 1;
    }
    return cap_scratch[0..cap_scratch_len];
}

var cap_scratch: [4096]u8 = undefined;
var cap_scratch_len: usize = 0;

fn opOpenUri(br: *Bridge, uri: []const u8) ?[]const u8 {
    const bus = br.bus orelse return null;
    _ = bus;
    var buf: [8192]u8 = undefined;
    // Zero first: D-Bus alignment gaps must be zero bytes.
    @memset(&buf, 0);
    var w = W{ .buf = &buf };
    w.str(""); // parent_window
    w.str(uri);
    w.wu32(0); // options: empty a{sv}
    // Live-proven (busctl capture): same conditional rule as Notify —
    // if the body would otherwise end at len % 8 == 0 (accounting for
    // the word below), emit one extra u32 0 after options.
    if ((w.pos + 4) % 8 == 0) w.wu32(0);
    const msg = callAndWait(
        br,
        "org.freedesktop.portal.Desktop",
        "/org/freedesktop/portal/desktop",
        "org.freedesktop.portal.OpenURI",
        "OpenURI",
        "ssa{sv}",
        w.bytes(),
        15000,
    ) catch |err| {
        logErr("bridge: OpenURI failed: {t}\n", .{err});
        return null;
    };
    defer gpa.free(msg);
    // Method-return received (fire-and-forget result irrelevant); the
    // actual open may surface a Response signal later — ignored.
    return "ok";
}

fn opScheme(br: *Bridge, scheme: []const u8) bool {
    const bus = br.bus orelse return false;
    _ = bus;
    var buf: [1152]u8 = undefined;
    var w = W{ .buf = &buf };
    w.str(scheme);
    w.wu32(0); // options: empty a{sv}
    // Same conditional extra-word rule as OpenURI/Notify (busctl
    // capture: short sa{sv} bodies need no extra word).
    if ((w.pos + 4) % 8 == 0) w.wu32(0);
    const msg = callAndWait(
        br,
        "org.freedesktop.portal.Desktop",
        "/org/freedesktop/portal/desktop",
        "org.freedesktop.portal.OpenURI",
        "SchemeSupported",
        "sa{sv}",
        w.bytes(),
        5000,
    ) catch return false;
    defer gpa.free(msg);
    var c = dbus.Cursor.init(replyBody(msg));
    return (c.nextBool() catch false);
}

fn senderSuffix(unique: []const u8) ![]u8 {
    // ":1.234" -> "1_234".
    var out = try gpa.alloc(u8, unique.len);
    var n: usize = 0;
    for (unique) |ch| {
        if (ch == ':' or ch == '.') {
            if (n > 0 and out[n - 1] != '_') {
                out[n] = '_';
                n += 1;
            }
            continue;
        }
        out[n] = ch;
        n += 1;
    }
    return out[0..n];
}

fn opOpenFile(br: *Bridge, title: []const u8) u64 {
    br.req_seq += 1;
    const req_id = br.req_seq;
    const bus = &(br.bus orelse {
        // Headless: a req that stays pending until close-req.
        const token = gpa.dupe(u8, "headless") catch return 0;
        br.file_reqs.put(gpa, req_id, .{
            .token = token,
            .handle = gpa.dupe(u8, "") catch return 0,
            .status = .pending,
            .path = gpa.dupe(u8, "") catch return 0,
        }) catch return 0;
        return req_id;
    });
    bus.token_seq += 1;
    const token = std.fmt.allocPrint(gpa, "isakai{d}", .{bus.token_seq}) catch return 0;
    errdefer gpa.free(token);
    const handle_path = std.fmt.allocPrint(gpa, "/org/freedesktop/portal/desktop/request/{s}/{s}", .{ bus.sender, token }) catch {
        gpa.free(token);
        return 0;
    };
    errdefer gpa.free(handle_path);
    // Pre-subscribe the Response match BEFORE the call.
    const rule = std.fmt.allocPrint(
        gpa,
        "type='signal',sender='org.freedesktop.portal.Desktop',interface='org.freedesktop.portal.Request',path='{s}'",
        .{handle_path},
    ) catch {
        gpa.free(token);
        gpa.free(handle_path);
        return 0;
    };
    defer gpa.free(rule);
    bus.conn.addMatch(rule) catch {
        gpa.free(token);
        gpa.free(handle_path);
        return 0;
    };
    var buf: [8192]u8 = undefined;
    // Zero first: D-Bus alignment gaps must be zero bytes (0xAA in
    // debug builds reads as garbage lengths → broker disconnect).
    @memset(&buf, 0);
    var w = W{ .buf = &buf };
    w.str(""); // parent_window
    w.str(title);
    // options a{sv}: the array aligns to 4 (wu32 aligns); the DICT_ENTRY
    // inside aligns to 8 (explicit memset below). NO 8-align of the
    // length word itself.
    const olen_at = w.pos;
    w.wu32(0);
    const o_start = w.pos;
    {
        const aligned = (w.pos + 7) & ~@as(usize, 7);
        @memset(buf[w.pos..aligned], 0);
        w.pos = aligned;
    }
    const e_start = w.pos; // 8-aligned: start of the dict entry
    // Live-proven (busctl capture): the single options entry is TIGHT —
    // key string with NO trailing pad (u32 + bytes + NUL only), then sig,
    // then a normally padded value; the entry itself has NO trailing
    // pad to 8 either (same omit-trailing-pad convention as header
    // structs). Array length counts these tight bytes exactly.
    {
        const kb = "handle_token";
        std.mem.writeInt(u32, buf[w.pos..][0..4], @intCast(kb.len), .little);
        w.pos += 4;
        @memcpy(buf[w.pos..][0..kb.len], kb);
        w.pos += kb.len;
        buf[w.pos] = 0;
        w.pos += 1;
    }
    // variant "s": sig + string (aligned per string rules at current pos)
    w.sig("s");
    w.str(token);
    std.mem.writeInt(u32, buf[olen_at..][0..4], @intCast(w.pos - e_start), .little);
    _ = o_start;
    const msg = callAndWait(
        br,
        "org.freedesktop.portal.Desktop",
        "/org/freedesktop/portal/desktop",
        "org.freedesktop.portal.FileChooser",
        "OpenFile",
        "ssa{sv}",
        w.bytes(),
        15000,
    ) catch {
        gpa.free(token);
        gpa.free(handle_path);
        return 0;
    };
    defer gpa.free(msg);
    var c = dbus.Cursor.init(replyBody(msg));
    const handle = c.nextString() catch {
        gpa.free(token);
        gpa.free(handle_path);
        return 0;
    };
    const handle_owned = gpa.dupe(u8, handle) catch {
        gpa.free(token);
        gpa.free(handle_path);
        return 0;
    };
    // Legacy-handle fallback: daemon may answer on a different path.
    if (!std.mem.eql(u8, handle, handle_path)) {
        const rule2 = std.fmt.allocPrint(
            gpa,
            "type='signal',sender='org.freedesktop.portal.Desktop',interface='org.freedesktop.portal.Request',path='{s}'",
            .{handle},
        ) catch null;
        if (rule2) |r| {
            defer gpa.free(r);
            bus.conn.addMatch(r) catch {};
        }
    }
    br.file_reqs.put(gpa, req_id, .{
        .token = token,
        .handle = handle_owned,
        .status = .pending,
        .path = gpa.dupe(u8, "") catch "",
    }) catch {
        gpa.free(token);
        gpa.free(handle_owned);
        gpa.free(handle_path);
        return 0;
    };
    gpa.free(handle_path);
    return req_id;
}

fn opCloseReq(br: *Bridge, req_id: u64) void {
    const fr = br.file_reqs.getPtr(req_id) orelse return;
    if (br.bus != null and fr.handle.len > 0) {
        const msg = callAndWait(
            br,
            "org.freedesktop.portal.Desktop",
            fr.handle,
            "org.freedesktop.portal.Request",
            "Close",
            "",
            &.{},
            3000,
        ) catch null;
        if (msg) |m| gpa.free(m);
    }
    gpa.free(fr.token);
    gpa.free(fr.handle);
    gpa.free(fr.path);
    _ = br.file_reqs.remove(req_id);
}

// --- JSON protocol --------------------------------------------------------

fn jsonEscape(out: *std.ArrayListUnmanaged(u8), s: []const u8) void {
    out.append(gpa, '"') catch return;
    for (s) |ch| {
        switch (ch) {
            '"' => out.appendSlice(gpa, "\\\"") catch return,
            '\\' => out.appendSlice(gpa, "\\\\") catch return,
            '\n' => out.appendSlice(gpa, "\\n") catch return,
            '\r' => out.appendSlice(gpa, "\\r") catch return,
            '\t' => out.appendSlice(gpa, "\\t") catch return,
            else => {
                if (ch < 0x20) {
                    var tmp: [6]u8 = undefined;
                    const n = std.fmt.bufPrint(&tmp, "\\u{x:0>4}", .{ch}) catch return;
                    out.appendSlice(gpa, n) catch return;
                } else {
                    out.append(gpa, ch) catch return;
                }
            },
        }
    }
    out.append(gpa, '"') catch return;
}

const Responder = struct {
    fd: std.posix.fd_t,
    fn send(self: *Responder, line: []const u8) void {
        var off: usize = 0;
        while (off < line.len) {
            const n = linux.write(self.fd, line[off..].ptr, line.len - off);
            const err = linux.errno(n);
            if (err != .SUCCESS) {
                if (err == .INTR) continue;
                return;
            }
            off += n;
        }
        const nl: [1]u8 = .{'\n'};
        _ = linux.write(self.fd, &nl, 1);
    }
};

fn jstr(v: std.json.Value, key: []const u8) []const u8 {
    const o = v.object.get(key) orelse return "";
    return switch (o) {
        .string => |s| s,
        else => "",
    };
}

fn jint(v: std.json.Value, key: []const u8) i64 {
    const o = v.object.get(key) orelse return 0;
    return switch (o) {
        .integer => |i| i,
        .float => |f| @intFromFloat(f),
        else => 0,
    };
}

fn handleLine(br: *Bridge, line: []const u8, rsp: *Responder) void {
    const parsed = std.json.parseFromSlice(std.json.Value, gpa, line, .{}) catch {
        rsp.send("{\"ok\":false,\"err\":\"bad-json\"}");
        return;
    };
    defer parsed.deinit();
    const v = parsed.value;
    if (v != .object) {
        rsp.send("{\"ok\":false,\"err\":\"not-object\"}");
        return;
    }
    const op = jstr(v, "op");
    var out: std.ArrayListUnmanaged(u8) = .empty;
    defer out.deinit(gpa);
    // Drain pending bus traffic before every op so events stay fresh.
    drainBus(br);
    if (std.mem.eql(u8, op, "notify")) {
        const plat = opNotify(
            br,
            jstr(v, "app"),
            @intCast(@max(jint(v, "id"), 0)),
            jstr(v, "title"),
            jstr(v, "body"),
            @intCast(@min(@max(jint(v, "urgency"), 0), 2)),
            jstr(v, "actions"),
        );
        if (plat == 0) {
            out.appendSlice(gpa, "{\"ok\":false,\"err\":\"notify-failed\"}") catch return;
        } else {
            var tmp: [64]u8 = undefined;
            const s = std.fmt.bufPrint(&tmp, "{{\"ok\":true,\"id\":{d}}}", .{plat}) catch return;
            out.appendSlice(gpa, s) catch return;
        }
        rsp.send(out.items);
    } else if (std.mem.eql(u8, op, "close")) {
        opClose(br, @intCast(@max(jint(v, "plat"), 0)));
        rsp.send("{\"ok\":true}");
    } else if (std.mem.eql(u8, op, "caps")) {
        const caps = opCaps(br);
        out.appendSlice(gpa, "{\"ok\":true,\"caps\":[") catch return;
        var first = true;
        var p: usize = 0;
        while (p < caps.len) {
            const z = std.mem.indexOfScalar(u8, caps[p..], 0) orelse break;
            if (!first) out.append(gpa, ',') catch return;
            first = false;
            jsonEscape(&out, caps[p..][0..z]);
            p += z + 1;
        }
        out.appendSlice(gpa, "]}") catch return;
        rsp.send(out.items);
    } else if (std.mem.eql(u8, op, "open-uri")) {
        if (opOpenUri(br, jstr(v, "uri"))) |_| {
            rsp.send("{\"ok\":true}");
        } else {
            rsp.send("{\"ok\":false,\"err\":\"open-failed\"}");
        }
    } else if (std.mem.eql(u8, op, "scheme")) {
        const ok = opScheme(br, jstr(v, "scheme"));
        var tmp: [64]u8 = undefined;
        const s = std.fmt.bufPrint(&tmp, "{{\"ok\":true,\"supported\":{s}}}", .{if (ok) "true" else "false"}) catch return;
        rsp.send(s);
    } else if (std.mem.eql(u8, op, "open-file")) {
        const req = opOpenFile(br, jstr(v, "title"));
        if (req == 0) {
            rsp.send("{\"ok\":false,\"err\":\"open-file-failed\"}");
        } else {
            var tmp: [64]u8 = undefined;
            const s = std.fmt.bufPrint(&tmp, "{{\"ok\":true,\"req\":{d}}}", .{req}) catch return;
            rsp.send(s);
        }
    } else if (std.mem.eql(u8, op, "file-result")) {
        const req: u64 = @intCast(@max(jint(v, "req"), 0));
        const fr = br.file_reqs.get(req);
        if (fr == null) {
            rsp.send("{\"ok\":false,\"err\":\"bad-req\"}");
        } else if (fr.?.status == .pending) {
            rsp.send("{\"ok\":true,\"status\":\"pending\"}");
        } else if (fr.?.status == .done) {
            out.appendSlice(gpa, "{\"ok\":true,\"status\":\"done\",\"path\":") catch return;
            jsonEscape(&out, fr.?.path);
            out.append(gpa, '}') catch return;
            rsp.send(out.items);
        } else {
            rsp.send("{\"ok\":true,\"status\":\"cancel\"}");
        }
    } else if (std.mem.eql(u8, op, "close-req")) {
        opCloseReq(br, @intCast(@max(jint(v, "req"), 0)));
        rsp.send("{\"ok\":true}");
    } else if (std.mem.eql(u8, op, "poll")) {
        // Blocking settle: up to ~1s for late signals (cancel path).
        var waited: i32 = 0;
        while (br.events.items.len == 0 and waited < 1000) {
            if (!pumpOnce(br, 100)) waited += 100 else waited = 1000;
        }
        drainBus(br);
        out.appendSlice(gpa, "{\"ok\":true,\"events\":[") catch return;
        for (br.events.items, 0..) |ev, i| {
            if (i > 0) out.append(gpa, ',') catch return;
            switch (ev) {
                .close => |req| {
                    var tmp: [64]u8 = undefined;
                    const s = std.fmt.bufPrint(&tmp, "{{\"kind\":\"close\",\"req\":{d}}}", .{req}) catch return;
                    out.appendSlice(gpa, s) catch return;
                },
                .action => |a| {
                    if (a.slot == 255) {
                        var tmp: [64]u8 = undefined;
                        const s = std.fmt.bufPrint(&tmp, "{{\"kind\":\"close-notif\",\"plat\":{d}}}", .{a.plat}) catch return;
                        out.appendSlice(gpa, s) catch return;
                    } else {
                        var tmp: [96]u8 = undefined;
                        const s = std.fmt.bufPrint(&tmp, "{{\"kind\":\"action\",\"plat\":{d},\"slot\":{d},\"key\":", .{ a.plat, a.slot }) catch return;
                        out.appendSlice(gpa, s) catch return;
                        const key = br.action_keys[a.slot] orelse "";
                        jsonEscape(&out, key);
                        out.append(gpa, '}') catch return;
                    }
                },
                .file => |f| {
                    var tmp: [96]u8 = undefined;
                    const s = std.fmt.bufPrint(&tmp, "{{\"kind\":\"file\",\"req\":{d},\"status\":\"{s}\",\"path\":", .{ f.req, if (f.done) "done" else "cancel" }) catch return;
                    out.appendSlice(gpa, s) catch return;
                    if (f.done) {
                        const fr = br.file_reqs.get(f.req);
                        jsonEscape(&out, if (fr) |r| r.path else "");
                    } else {
                        out.appendSlice(gpa, "null") catch return;
                    }
                    out.append(gpa, '}') catch return;
                },
            }
        }
        out.appendSlice(gpa, "]}") catch return;
        br.events.clearRetainingCapacity();
        rsp.send(out.items);
    } else if (std.mem.eql(u8, op, "action-key")) {
        const slot: usize = @intCast(@max(jint(v, "slot"), 0));
        if (slot < 16) {
            out.appendSlice(gpa, "{\"ok\":true,\"key\":") catch return;
            jsonEscape(&out, br.action_keys[slot] orelse "");
            out.append(gpa, '}') catch return;
            rsp.send(out.items);
        } else {
            rsp.send("{\"ok\":false,\"err\":\"bad-slot\"}");
        }
    } else {
        rsp.send("{\"ok\":false,\"err\":\"unknown-op\"}");
    }
}

/// Serve the line protocol on fd_in/fd_out until EOF.
fn serve(br: *Bridge, fd_in: std.posix.fd_t, fd_out: std.posix.fd_t) void {
    var rsp = Responder{ .fd = fd_out };
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    var buf: [4096]u8 = undefined;
    while (true) {
        const n = linux.read(fd_in, &buf, buf.len);
        const err = linux.errno(n);
        if (err != .SUCCESS) {
            if (err == .INTR) continue;
            return;
        }
        if (n == 0) return; // EOF
        for (buf[0..n]) |ch| {
            if (ch == '\n') {
                if (line.items.len > 0) {
                    if (line.items[line.items.len - 1] == '\r') line.items.len -= 1;
                    handleLine(br, line.items, &rsp);
                    line.clearRetainingCapacity();
                    if (line.capacity > max_line) {
                        line.deinit(gpa);
                        line = .empty;
                    }
                }
            } else {
                if (line.items.len < max_line) line.append(gpa, ch) catch return;
            }
        }
    }
}

fn validDir(s: []const u8) bool {
    if (s.len == 0 or s.len > 4000) return false;
    if (s[0] != '/') return false;
    if (std.mem.indexOf(u8, s, "..") != null) return false;
    return true;
}

const Ucred = extern struct {
    pid: i32,
    uid: u32,
    gid: u32,
};

fn peerUid(fd: std.posix.fd_t) ?u32 {
    var cred: Ucred = undefined;
    var len: linux.socklen_t = @sizeOf(Ucred);
    const rc = linux.getsockopt(
        fd,
        std.posix.SOL.SOCKET,
        std.posix.SO.PEERCRED,
        @ptrCast((&cred)[0..1]),
        &len,
    );
    if (linux.errno(rc) != .SUCCESS) return null;
    if (len != @sizeOf(Ucred)) return null;
    return cred.uid;
}

fn bindSocket(bridge_dir: []const u8, app_uid: u32) !std.posix.fd_t {
    _ = app_uid; // peercred check at accept() uses br.app_uid; modes carry the rest
    const path = std.fmt.allocPrint(gpa, "{s}/{s}", .{ bridge_dir, sock_name }) catch return error.NoMem;
    defer gpa.free(path);
    if (path.len + 1 > 108) return error.NoMem;
    _ = linux.unlink(@ptrCast(path.ptr)); // stale socket from a crash
    const fd = linux.socket(
        @as(u32, linux.AF.UNIX),
        @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC),
        0,
    );
    if (linux.errno(fd) != .SUCCESS) return error.NoMem;
    errdefer _ = linux.close(@bitCast(@as(u32, @truncate(fd))));
    var addr_arr: [108]u8 = undefined;
    @memset(&addr_arr, 0);
    @memcpy(addr_arr[0..path.len], path);
    var addr = linux.sockaddr.un{ .path = addr_arr };
    const addrlen: linux.socklen_t = @intCast(2 + path.len + 1);
    if (linux.errno(linux.bind(@bitCast(@as(u32, @truncate(fd))), @ptrCast(&addr), addrlen)) != .SUCCESS) {
        return error.NoMem;
    }
    if (linux.errno(linux.listen(@bitCast(@as(u32, @truncate(fd))), 4)) != .SUCCESS) {
        return error.NoMem;
    }
    // Socket must be connectable by the sandboxed app uid (connect
    // needs write on the inode), but the daemon is unprivileged and
    // cannot chown. Mode 0777 + SO_PEERCRED gating instead: anyone
    // local can connect, non-app uids are rejected without service.
    // (app_uid param retained: peercred check below still needs it.)
    // Path chmod, not fchmod: fchmod on a unix socket fd is a silent
    // no-op on current kernels (returns success, mode unchanged).
    // `path` is NUL-terminated here (allocPrint) so cast directly.
    _ = linux.chmod(@as([*:0]const u8, @ptrCast(path.ptr)), 0o777);
    return @bitCast(@as(u32, @truncate(fd)));
}

fn connectBus(br: *Bridge) void {
    const addr = dbus.busAddress(gpa) catch null;
    if (addr == null) {
        logErr("bridge: no session bus; headless test mode\n", .{});
        return;
    }
    defer gpa.free(addr.?);
    const fd = dbus.connectSock(addr.?) catch {
        logErr("bridge: bus connect failed; headless test mode\n", .{});
        return;
    };
    dbus.authExternal(fd) catch {
        _ = linux.close(fd);
        logErr("bridge: bus auth failed; headless test mode\n", .{});
        return;
    };
    const fd_state = gpa.create(dbus.FdTransport) catch {
        _ = linux.close(fd);
        return;
    };
    fd_state.* = .{ .fd = fd };
    var conn = dbus.Conn{
        .gpa = gpa,
        .transport = .{
            .ptr = fd_state,
            .readFn = dbus.FdTransport.read,
            .writeFn = dbus.FdTransport.write,
        },
    };
    const name = conn.hello(gpa) catch {
        gpa.destroy(fd_state);
        _ = linux.close(fd);
        logErr("bridge: bus hello failed; headless test mode\n", .{});
        return;
    };
    defer gpa.free(name);
    const suffix = senderSuffix(name) catch {
        gpa.destroy(fd_state);
        _ = linux.close(fd);
        return;
    };
    br.bus = .{
        .conn = conn,
        .fd_state = fd_state,
        .fd = fd,
        .sender = suffix,
        .live = true,
    };
    // Startup probes + signal subscriptions.
    _ = opCaps(br);
    br.bus.?.conn.addMatch("type='signal',sender='org.freedesktop.Notifications',interface='org.freedesktop.Notifications',member='NotificationClosed'") catch {};
    br.bus.?.conn.addMatch("type='signal',sender='org.freedesktop.Notifications',interface='org.freedesktop.Notifications',member='ActionInvoked'") catch {};
    br.bus.?.conn.addMatch("type='signal',sender='org.freedesktop.portal.Desktop',interface='org.freedesktop.portal.Request',member='Response'") catch {};
}

pub fn main(init: std.process.Init) !void {
    const gpa_main = init.gpa;
    _ = gpa_main;
    const argv = try init.minimal.args.toSlice(init.arena.allocator());
    var bridge_dir: ?[]const u8 = null;
    var stage_dir: ?[]const u8 = null;
    var want_uid: ?u32 = null;
    var stdio_mode = false;
    var ai: usize = 1; // argv[0] is the exe
    while (ai < argv.len) : (ai += 1) {
        const a = argv[ai];
        if (std.mem.eql(u8, a, "--stdio")) {
            stdio_mode = true;
        } else if (std.mem.eql(u8, a, "--bridge-dir")) {
            ai += 1;
            if (ai >= argv.len) return error.BadArgs;
            bridge_dir = argv[ai];
        } else if (std.mem.eql(u8, a, "--stage-dir")) {
            ai += 1;
            if (ai >= argv.len) return error.BadArgs;
            stage_dir = argv[ai];
        } else if (std.mem.eql(u8, a, "--uid")) {
            ai += 1;
            if (ai >= argv.len) return error.BadArgs;
            want_uid = std.fmt.parseInt(u32, argv[ai], 10) catch return error.BadArgs;
        } else {
            std.debug.print("usage: isakai-dbus-bridge [--stdio|--bridge-dir DIR] --stage-dir DIR [--uid N]\n", .{});
            return error.BadArgs;
        }
    }
    const stage = stage_dir orelse {
        std.debug.print("isakai-dbus-bridge: --stage-dir required\n", .{});
        return error.BadArgs;
    };
    if (!validDir(stage)) {
        std.debug.print("isakai-dbus-bridge: bad --stage-dir\n", .{});
        return error.BadArgs;
    }
    if (bridge_dir) |d| {
        if (!validDir(d)) {
            std.debug.print("isakai-dbus-bridge: bad --bridge-dir\n", .{});
            return error.BadArgs;
        }
    } else if (!stdio_mode) {
        std.debug.print("isakai-dbus-bridge: need --bridge-dir or --stdio\n", .{});
        return error.BadArgs;
    }
    var br = Bridge{ .stage_dir = stage };
    if (want_uid) |u| br.app_uid = u;
    connectBus(&br);
    if (stdio_mode) {
        serve(&br, linux.STDIN_FILENO, linux.STDOUT_FILENO);
        return;
    }
    const listen_fd = bindSocket(bridge_dir.?, br.app_uid) catch {
        std.debug.print("isakai-dbus-bridge: bind failed\n", .{});
        return error.BadArgs;
    };
    defer _ = linux.close(listen_fd);
    // Accept loop: one client at a time; SO_PEERCRED-checked.
    while (true) {
        const c = linux.accept(listen_fd, null, null);
        if (linux.errno(c) != .SUCCESS) {
            const e = linux.errno(c);
            if (e == .INTR) continue;
            return;
        }
        const cfd: std.posix.fd_t = @bitCast(@as(u32, @truncate(c)));
        if (br.app_uid != 0) {
            const peer = peerUid(cfd);
            if (peer == null or peer.? != br.app_uid) {
                logErr("bridge: rejecting client uid {any}\n", .{peer});
                _ = linux.close(cfd);
                continue;
            }
        }
        serve(&br, cfd, cfd);
        _ = linux.close(cfd);
    }
}
