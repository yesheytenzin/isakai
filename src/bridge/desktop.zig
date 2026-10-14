// SPDX-License-Identifier: GPL-3.0-or-later

//! JNI-side desktop client: connects to the per-app `isakai-dbus-bridge`
//! unix socket (`$ISAKAI_BRIDGE/bridge.sock`, spawned by the launcher as
//! the user) and speaks the newline-JSON protocol from
//! `src/dbus_bridge.zig`.
//!
//! No D-Bus here: the sandbox env carries no DBUS_* variables, so the
//! JVM side can never reach the session bus directly. All bus traffic
//! goes through the bridge.
//!
//! Event kinds in 8-int poll batches (0/-1/null policy matches the JNI
//! bridge: 0 events = none, -1 = bad/closed handle or bad batch):
//!   10 = close-req  [10, reqLo, reqHi, 0, 0, 0, 0, 0]
//!   11 = action     [11, plat, slot, 0, 0, 0, 0, 0]   (key via actionKey)
//!   12 = file-result[12, reqLo, reqHi, status, 0, 0, 0, 0] (1=ok, -1=cancel)
//!
//! Threading: all calls are synchronous and serialized by a global
//! mutex; the Java loop polls from one thread.

const std = @import("std");
const linux = std.os.linux;

const gpa = std.heap.c_allocator;

const sock_name = "bridge.sock";
const max_line: usize = 65536;

var fd: ?std.posix.fd_t = null;
var action_keys: [16]?[]u8 = .{null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null};

fn bridgeSockPath() ?[]u8 {
    const raw = std.c.getenv("ISAKAI_BRIDGE") orelse return null;
    const dir = std.mem.span(raw);
    if (dir.len == 0 or dir[0] != '/') return null;
    return std.fmt.allocPrint(gpa, "{s}/{s}", .{ dir, sock_name }) catch null;
}

fn ensureLocked() bool {
    if (fd != null) return true;
    const path = bridgeSockPath() orelse return false;
    defer gpa.free(path);
    if (path.len + 1 > 108) return false;
    const s = linux.socket(@as(u32, linux.AF.UNIX), @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC), 0);
    if (linux.errno(s) != .SUCCESS) return false;
    const cfd: std.posix.fd_t = @bitCast(@as(u32, @truncate(s)));
    errdefer _ = linux.close(cfd);
    var arr: [108]u8 = undefined;
    @memset(&arr, 0);
    @memcpy(arr[0..path.len], path);
    var addr = linux.sockaddr.un{ .path = arr };
    const addrlen: linux.socklen_t = @intCast(2 + path.len + 1);
    if (linux.errno(linux.connect(cfd, @ptrCast(&addr), addrlen)) != .SUCCESS) return false;
    fd = cfd;
    return true;
}

fn writeAll(f: std.posix.fd_t, buf: []const u8) bool {
    var off: usize = 0;
    while (off < buf.len) {
        const n = linux.write(f, buf[off..].ptr, buf.len - off);
        const err = linux.errno(n);
        if (err != .SUCCESS) {
            if (err == .INTR) continue;
            return false;
        }
        off += n;
    }
    return true;
}

fn readLine(f: std.posix.fd_t, out: *std.ArrayListUnmanaged(u8)) bool {
    out.clearRetainingCapacity();
    var b: [1]u8 = undefined;
    while (out.items.len < max_line) {
        const n = linux.read(f, &b, 1);
        const err = linux.errno(n);
        if (err != .SUCCESS) {
            if (err == .INTR) continue;
            return false;
        }
        if (n == 0) return out.items.len > 0;
        if (b[0] == '\n') return true;
        if (b[0] == '\r') continue;
        out.append(gpa, b[0]) catch return false;
    }
    return true;
}

fn dropConnLocked() void {
    if (fd) |f| {
        _ = linux.close(f);
        fd = null;
    }
}

/// Send `req` (one line, no newline), read one reply line into `out`.
/// False on any I/O failure (connection dropped).
fn roundTrip(req: []const u8, out: *std.ArrayListUnmanaged(u8)) bool {
    if (!ensureLocked()) return false;
    const f = fd.?;
    if (!writeAll(f, req)) {
        dropConnLocked();
        return false;
    }
    if (!writeAll(f, "\n")) {
        dropConnLocked();
        return false;
    }
    if (!readLine(f, out)) {
        dropConnLocked();
        return false;
    }
    return true;
}

fn esc(dst: *std.ArrayListUnmanaged(u8), s: []const u8) void {
    dst.append(gpa, '"') catch return;
    for (s) |ch| {
        switch (ch) {
            '"' => dst.appendSlice(gpa, "\\\"") catch return,
            '\\' => dst.appendSlice(gpa, "\\\\") catch return,
            '\n' => dst.appendSlice(gpa, "\\n") catch return,
            else => {
                if (ch < 0x20) {
                    var tmp: [6]u8 = undefined;
                    const n = std.fmt.bufPrint(&tmp, "\\u{x:0>4}", .{ch}) catch return;
                    dst.appendSlice(gpa, n) catch return;
                } else dst.append(gpa, ch) catch return;
            },
        }
    }
    dst.append(gpa, '"') catch return;
}

/// Extract an integer field `"key":NUM` from a flat JSON object.
fn numField(line: []const u8, key: []const u8) ?i64 {
    var needle: [64]u8 = undefined;
    const n = std.fmt.bufPrint(&needle, "\"{s}\":", .{key}) catch return null;
    const at = std.mem.indexOf(u8, line, n) orelse return null;
    var i = at + n.len;
    while (i < line.len and (line[i] == ' ' or line[i] == '\t')) i += 1;
    var neg = false;
    if (i < line.len and line[i] == '-') {
        neg = true;
        i += 1;
    }
    var v: i64 = 0;
    var any = false;
    while (i < line.len and line[i] >= '0' and line[i] <= '9') : (i += 1) {
        any = true;
        v = v * 10 + (line[i] - '0');
    }
    if (!any) return null;
    return if (neg) -v else v;
}

/// Extract a string field `"key":"..."` with minimal unescaping.
fn strField(line: []const u8, key: []const u8, alloc: std.mem.Allocator) ?[]u8 {
    var needle: [64]u8 = undefined;
    const n = std.fmt.bufPrint(&needle, "\"{s}\":\"", .{key}) catch return null;
    const at = std.mem.indexOf(u8, line, n) orelse return null;
    var i = at + n.len;
    var out: std.ArrayListUnmanaged(u8) = .empty;
    errdefer out.deinit(alloc);
    while (i < line.len) {
        const ch = line[i];
        if (ch == '"') return out.toOwnedSlice(alloc) catch null;
        if (ch == '\\' and i + 1 < line.len) {
            const e = line[i + 1];
            switch (e) {
                '"', '\\', '/' => out.append(alloc, e) catch return null,
                'n' => out.append(alloc, '\n') catch return null,
                'r' => out.append(alloc, '\r') catch return null,
                't' => out.append(alloc, '\t') catch return null,
                'u' => {
                    // \uXXXX: only ASCII range decoded, else '?'.
                    if (i + 5 < line.len) {
                        const hex = line[i + 2 .. i + 6];
                        const cp = std.fmt.parseInt(u21, hex, 16) catch {
                            out.append(alloc, '?') catch return null;
                            i += 6;
                            continue;
                        };
                        if (cp < 0x80) {
                            out.append(alloc, @intCast(cp)) catch return null;
                        } else {
                            out.append(alloc, '?') catch return null;
                        }
                        i += 6;
                        continue;
                    } else return null;
                },
                else => out.append(alloc, e) catch return null,
            }
            i += 2;
        } else {
            out.append(alloc, ch) catch return null;
            i += 1;
        }
    }
    out.deinit(alloc);
    return null;
}

fn okTrue(line: []const u8) bool {
    return std.mem.indexOf(u8, line, "\"ok\":true") != null;
}

// --- public API ---------------------------------------------------------

/// Post a notification; returns the platform id, 0 on failure.
pub fn notify(app: []const u8, id: i32, title: []const u8, body: []const u8, channel: []const u8, urgency: i32) u32 {
    var req: std.ArrayListUnmanaged(u8) = .empty;
    defer req.deinit(gpa);
    req.appendSlice(gpa, "{\"op\":\"notify\",\"app\":") catch return 0;
    esc(&req, app);
    var tmp: [128]u8 = undefined;
    const s = std.fmt.bufPrint(&tmp, ",\"id\":{d},\"title\":", .{id}) catch return 0;
    req.appendSlice(gpa, s) catch return 0;
    esc(&req, title);
    req.appendSlice(gpa, ",\"body\":") catch return 0;
    esc(&req, body);
    const s2 = std.fmt.bufPrint(&tmp, ",\"urgency\":{d},\"channel\":", .{@max(urgency, 0)}) catch return 0;
    req.appendSlice(gpa, s2) catch return 0;
    esc(&req, channel);
    req.append(gpa, '}') catch return 0;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    if (!roundTrip(req.items, &line)) return 0;
    if (!okTrue(line.items)) return 0;
    const v = numField(line.items, "id") orelse return 0;
    if (v <= 0) return 0;
    return @intCast(v);
}

pub fn cancel(plat: u32) void {
    var tmp: [64]u8 = undefined;
    const req = std.fmt.bufPrint(&tmp, "{{\"op\":\"close\",\"plat\":{d}}}", .{plat}) catch return;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    _ = roundTrip(req, &line);
}

/// Open a URI; true on method-return success.
pub fn openUri(uri: []const u8) bool {
    var req: std.ArrayListUnmanaged(u8) = .empty;
    defer req.deinit(gpa);
    req.appendSlice(gpa, "{\"op\":\"open-uri\",\"uri\":") catch return false;
    esc(&req, uri);
    req.append(gpa, '}') catch return false;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    if (!roundTrip(req.items, &line)) return false;
    return okTrue(line.items);
}

/// Start a file pick; 0 on failure.
pub fn openFileStart(title: []const u8) u64 {
    var req: std.ArrayListUnmanaged(u8) = .empty;
    defer req.deinit(gpa);
    req.appendSlice(gpa, "{\"op\":\"open-file\",\"title\":") catch return 0;
    esc(&req, title);
    req.append(gpa, '}') catch return 0;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    if (!roundTrip(req.items, &line)) return 0;
    if (!okTrue(line.items)) return 0;
    const v = numField(line.items, "req") orelse return 0;
    if (v <= 0) return 0;
    return @intCast(v);
}

pub const FileResult = struct {
    status: enum { pending, done, cancel },
    path: []u8, // owned; valid when done ("" otherwise)
};

/// Poll one file-pick result without blocking.
pub fn fileResult(req_id: u64) ?FileResult {
    var tmp: [64]u8 = undefined;
    const req = std.fmt.bufPrint(&tmp, "{{\"op\":\"file-result\",\"req\":{d}}}", .{req_id}) catch return null;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    if (!roundTrip(req, &line)) return null;
    if (!okTrue(line.items)) return .{ .status = .cancel, .path = gpa.dupe(u8, "") catch return null };
    const st = strField(line.items, "status", gpa) orelse return null;
    defer gpa.free(st);
    if (std.mem.eql(u8, st, "pending")) return null;
    if (std.mem.eql(u8, st, "done")) {
        const p = strField(line.items, "path", gpa) orelse (gpa.dupe(u8, "") catch return null);
        return .{ .status = .done, .path = p };
    }
    return .{ .status = .cancel, .path = gpa.dupe(u8, "") catch return null };
}

pub fn closeReq(req_id: u64) void {
    var tmp: [64]u8 = undefined;
    const req = std.fmt.bufPrint(&tmp, "{{\"op\":\"close-req\",\"req\":{d}}}", .{req_id}) catch return;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    _ = roundTrip(req, &line);
}

/// Drain desktop events into `batch` (groups of 8 ints). Returns the
/// event count, 0 for none, -1 on bridge failure. Kinds 10/11/12.
pub fn poll(batch: []i32) i32 {
    if (batch.len < 8 or batch.len % 8 != 0) return -1;
    var line: std.ArrayListUnmanaged(u8) = .empty;
    defer line.deinit(gpa);
    if (!roundTrip("{\"op\":\"poll\"}", &line)) return -1;
    if (!okTrue(line.items)) return -1;
    // Walk the events array: split top-level {...} objects.
    const arr = std.mem.indexOf(u8, line.items, "\"events\":[") orelse return 0;
    var pos = arr + "\"events\":[".len;
    var n: usize = 0;
    const max_events: usize = @intCast(batch.len / 8);
    while (n < max_events) {
        const open = std.mem.indexOfScalarPos(u8, line.items, pos, '{') orelse break;
        // Match braces (no nesting inside events except none).
        var depth: usize = 0;
        var end = open;
        var in_str = false;
        var esc_next = false;
        while (end < line.items.len) : (end += 1) {
            const ch = line.items[end];
            if (esc_next) {
                esc_next = false;
                continue;
            }
            if (in_str) {
                if (ch == '\\') esc_next = true else if (ch == '"') in_str = false;
                continue;
            }
            if (ch == '"') in_str = true else if (ch == '{') depth += 1 else if (ch == '}') {
                depth -= 1;
                if (depth == 0) break;
            } else if (ch == ']' and depth == 0) break;
        }
        if (depth != 0) break;
        const obj = line.items[open .. end + 1];
        pos = end + 1;
        const kind = strField(obj, "kind", gpa) orelse {
            if (std.mem.indexOf(u8, obj, "]") != null) break;
            continue;
        };
        defer gpa.free(kind);
        const base = n * 8;
        if (std.mem.eql(u8, kind, "close")) {
            const req = numField(obj, "req") orelse continue;
            batch[base] = 10;
            batch[base + 1] = @truncate(req);
            batch[base + 2] = @truncate(req >> 32);
            batch[base + 3] = 0;
            batch[base + 4] = 0;
            batch[base + 5] = 0;
            batch[base + 6] = 0;
            batch[base + 7] = 0;
            n += 1;
        } else if (std.mem.eql(u8, kind, "action") or std.mem.eql(u8, kind, "close-notif")) {
            const plat = numField(obj, "plat") orelse continue;
            if (std.mem.eql(u8, kind, "close-notif")) {
                // Synthesize as a close-req-style event kind 10 with
                // plat in slot 1 (Java distinguishes by no pending req).
                batch[base] = 10;
                batch[base + 1] = @truncate(plat);
                batch[base + 2] = -1; // marker: notification close
                batch[base + 3] = 0;
                batch[base + 4] = 0;
                batch[base + 5] = 0;
                batch[base + 6] = 0;
                batch[base + 7] = 0;
                n += 1;
            } else {
                const slot = numField(obj, "slot") orelse continue;
                batch[base] = 11;
                batch[base + 1] = @truncate(plat);
                batch[base + 2] = @truncate(slot);
                batch[base + 3] = 0;
                batch[base + 4] = 0;
                batch[base + 5] = 0;
                batch[base + 6] = 0;
                batch[base + 7] = 0;
                // Cache the key for nGetActionKey.
                const key = strField(obj, "key", gpa) orelse continue;
                const s: usize = @intCast(@max(slot, 0));
                if (s < 16) {
                    if (action_keys[s]) |old| gpa.free(old);
                    action_keys[s] = key;
                } else gpa.free(key);
                n += 1;
            }
        } else if (std.mem.eql(u8, kind, "file")) {
            const req = numField(obj, "req") orelse continue;
            const st = strField(obj, "status", gpa) orelse continue;
            defer gpa.free(st);
            batch[base] = 12;
            batch[base + 1] = @truncate(req);
            batch[base + 2] = @truncate(req >> 32);
            batch[base + 3] = if (std.mem.eql(u8, st, "done")) 1 else -1;
            batch[base + 4] = 0;
            batch[base + 5] = 0;
            batch[base + 6] = 0;
            batch[base + 7] = 0;
            n += 1;
        }
        // Stop at the array end.
        var j = pos;
        while (j < line.items.len and (line.items[j] == ' ' or line.items[j] == ',')) j += 1;
        if (j < line.items.len and line.items[j] == ']') break;
    }
    return @intCast(n);
}

/// Key for a kind-11 slot (borrowed; null on bad slot).
pub fn actionKey(slot: usize) ?[]const u8 {
    if (slot >= 16) return null;
    return action_keys[slot];
}

