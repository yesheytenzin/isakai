// SPDX-License-Identifier: GPL-3.0-or-later

//! Minimal D-Bus client: session-bus framing over AF_UNIX, AUTH EXTERNAL,
//! Hello, method calls, addMatch, and a small pump for replies/signals.
//!
//! Wire format: fixed 16-byte header (endian 'l', type, flags, version,
//! body-len, serial, header-fields-len), header fields as an ARRAY with a
//! NO length word (fields_len counts padded struct bytes MINUS the last
//! struct's trailing pad, sd-bus convention enforced by dbus-broker).
//! STRUCT{BYTE field, VARIANT value} structs pack back-to-back directly
//! after the (absent) length word (NO pad: BYTE and VARIANT align to 1).
//! Each struct is padded to 8 on wire. The header (through the fields
//! array) is then padded to a multiple of 8 from the message start.
//! Supported marshal types: y b u i s o g a v
//! and structs of them. All integers little-endian. Strings/paths are
//! NUL-terminated with the length prefix counting bytes EXCLUDING the
//! NUL (ground truth from sd-bus proxy capture of busctl Hello);
//! padding to 4 (signatures: u8 length, no padding).
//!
//! Live-proven broker quirks (2026-10-10, 30+ probes against dbus-broker;
//! violations drop the connection with "invalid body/header", no error):
//! - Single-string bodies (AddMatch rules) carry NO trailing pad
//!   (4+len+1; dbus-send ground truth). Multi-value bodies pad interior
//!   strings to 4 normally.
//! - A counted Notify-style body must satisfy len % 8 == 4; append one
//!   conditional u32 0 after hints when it would end % 8 == 0
//!   (see Conn.encodeNotify). Same conditional rule for portal OpenURI
//!   bodies; short SchemeSupported bodies need none.
//! - Portal FileChooser options DICT_ENTRY is TIGHT: key string with NO
//!   trailing pad, NO entry pad-to-8; the array length counts the tight
//!   bytes exactly (busctl: 27 for one handle_token entry).
//! - Scanners must accept empty-'g' signature fields (AddMatch replies)
//!   but ONLY for field code 8: body length words mimic the (code,0,0)
//!   shape, so unscoped scans stop there instead of eating body bytes.
//!
//! Auth (dbus-daemon/dbus-broker session socket): NUL byte, then
//! "AUTH EXTERNAL\r\n", server answers "DATA\r\n", client sends
//! "DATA\r\n", server answers "OK <guid>\r\n", client sends
//! "NEGOTIATE_UNIX_FD\r\n" (server may answer "ERROR\r\n" when fds are
//! unsupported — tolerated, we never pass fds), then "BEGIN\r\n". Then
//! the Hello call (org.freedesktop.DBus/Hello) assigns our unique name.
//!
//! Golden tests pin byte-exact vectors for Hello/Notify frames;
//! FakeSocket hermetic tests drive sendCall + Hello-reply decode with no
//! socket.

const std = @import("std");
const linux = std.os.linux;

/// One header field: field code + signature + pre-encoded variant payload
/// (the variant body WITHOUT its own signature header; marshal adds it).
pub const Field = struct {
    code: u8,
    sig: []const u8,
    /// Variant value bytes, already marshalled (e.g. encodeString body).
    value: []const u8,
};

pub const MessageType = enum(u8) {
    method_call = 1,
    method_return = 2,
    err = 3,
    signal = 4,
};

/// Header field codes (spec section "Message Headers").
pub const F = struct {
    pub const path: u8 = 1;
    pub const interface: u8 = 2;
    pub const member: u8 = 3;
    pub const error_name: u8 = 4;
    pub const reply_serial: u8 = 5;
    pub const destination: u8 = 6;
    pub const sender: u8 = 7;
    pub const signature: u8 = 8;
};

pub const DbusError = error{
    Truncated,
    BadString,
    BadHeader,
    AuthFailed,
    HelloFailed,
    MessageTooLong,
    OutOfMemory,
    IoFailed,
    BadServerGuid,
};

pub fn padLen(n: usize) usize {
    return (n + 3) & ~@as(usize, 3);
}

/// On-wire bytes for a D-Bus string: u32 len (EXCLUDING NUL per spec;
/// ground truth from dbus-send proxy capture) + bytes + NUL + pad to 4.
pub fn stringWireLen(s: []const u8) usize {
    return 4 + padLen(s.len + 1);
}

/// On-wire bytes for a type signature: u8 len + bytes + NUL (no padding).
pub fn sigWireLen(s: []const u8) usize {
    return 1 + s.len + 1;
}

/// Encode a D-Bus string into `out`; caller guarantees stringWireLen.
/// Length prefix counts bytes EXCLUDING the trailing NUL. Returns bytes written.
pub fn writeString(out: []u8, s: []const u8) usize {
    std.mem.writeInt(u32, out[0..4], @intCast(s.len), .little);
    @memcpy(out[4..][0..s.len], s);
    out[4 + s.len] = 0;
    const total = stringWireLen(s);
    @memset(out[4 + s.len + 1 .. total], 0);
    return total;
}

/// Encode a type signature into `out`; caller guarantees sigWireLen.
pub fn writeSig(out: []u8, s: []const u8) usize {
    out[0] = @intCast(s.len);
    @memcpy(out[1..][0..s.len], s);
    out[1 + s.len] = 0;
    return sigWireLen(s);
}

/// Encode a variant wrapping `sig` + `value` bytes (value already marshalled).
pub fn writeVariant(out: []u8, sig: []const u8, value: []const u8) usize {
    const pos: usize = writeSig(out, sig);
    @memcpy(out[pos..][0..value.len], value);
    return pos + value.len;
}

/// Encode one header field (STRUCT{BYTE, VARIANT}) into `out`.
/// Ground truth: byte, then the variant IMMEDIATELY (no pad: BYTE and
/// VARIANT align to 1); every struct except the LAST is padded to 8,
/// the struct is padded to 8. Returns bytes written.
pub fn writeField(out: []u8, field: Field) usize {
    out[0] = field.code;
    var pos: usize = 1;
    pos += writeVariant(out[pos..], field.sig, field.value);
    const total = (pos + 7) & ~@as(usize, 7);
    @memset(out[pos..total], 0);
    return total;
}

/// Build a variant payload for a string-typed header field
/// (destination/interface/member/path/error/sender): the u32+bytes+NUL
/// body that writeField wraps with the "s"/"o" signature.
pub fn variantString(buf: []u8, s: []const u8) []u8 {
    const n = writeString(buf, s);
    return buf[0..n];
}

/// Unpadded string tail: length + bytes + NUL, NO trailing pad.
/// Live-proven MANDATORY for a body consisting of (or ending with) a
/// string (dbus-broker "invalid body" otherwise): dbus-send's working
/// AddMatch body is 4+13+1 = 18 bytes for a 13-char rule, while the
/// padded 20-byte form is rejected. Header member strings ("Hello")
/// use the padded form (both accepted in headers).
pub fn writeStringUnpadded(out: []u8, s: []const u8) usize {
    std.mem.writeInt(u32, out[0..4], @intCast(s.len), .little);
    @memcpy(out[4..][0..s.len], s);
    out[4 + s.len] = 0;
    return 4 + s.len + 1;
}

/// Encode a full message: 16-byte header + fields array + body.
/// `body_sig` is the bare signature (no NUL). Returns total bytes written.
///
/// Alignment (live-proven against dbus-broker): the header fields array
/// starts at message offset 16 directly (NO length word on wire); the
/// STRUCT{BYTE,VARIANT} entries follow immediately (NO pad: BYTE and
/// VARIANT both align to 1), each padded to 8 on wire. The header
/// fields_len counts padded struct bytes MINUS the last struct's trailing
/// pad (sd-bus convention; the pad bytes ARE on wire). The header
/// (through the fields array) is then padded to a multiple of 8.
/// Unpadded value length for a header-field variant payload (no
/// alignment padding): s/o = u32 + bytes + NUL; u/i/b = 4; g = u8 + bytes + NUL.
fn unpaddedValueLen(sig: []const u8, value: []const u8) usize {
    if (sig.len == 1 and (sig[0] == 's' or sig[0] == 'o')) {
        if (value.len < 4) return value.len;
        const n: usize = std.mem.readInt(u32, value[0..4], .little);
        return 4 + n + 1;
    }
    if (sig.len == 1 and (sig[0] == 'u' or sig[0] == 'i' or sig[0] == 'b')) return 4;
    if (sig.len == 1 and sig[0] == 'g') {
        if (value.len < 1) return value.len;
        return 1 + @as(usize, value[0]) + 1;
    }
    return value.len;
}

pub fn writeMessage(
    out: []u8,
    mtype: MessageType,
    flags: u8,
    serial: u32,
    fields: []const Field,
    body_sig: []const u8,
    body: []const u8,
) DbusError!usize {
    // Layout: 16 header + structs (each padded to 8, NO length word)
    // + end pad + body. fields_len = padded bytes MINUS the last
    // struct's trailing pad (pad bytes still emitted for alignment).
    var padded_sum: usize = 0;
    var last_raw_unpadded: usize = 0;
    for (fields) |f| {
        const raw: usize = 1 + sigWireLen(f.sig) + f.value.len;
        const padded: usize = (raw + 7) & ~@as(usize, 7);
        padded_sum += padded;
        last_raw_unpadded = 1 + sigWireLen(f.sig) + unpaddedValueLen(f.sig, f.value);
    }
    const last_pad: usize = (8 - (last_raw_unpadded % 8)) % 8;
    const structs_len: usize = padded_sum - last_pad; // header flen value
    const region: usize = padded_sum; // structs on wire (no len word)
    const hdr_pad = (8 - ((16 + region) % 8)) % 8;
    const total = 16 + region + hdr_pad + body.len;
    if (total > out.len) return error.MessageTooLong;
    if (body_sig.len > 255) return error.MessageTooLong;
    out[0] = 'l';
    out[1] = @intFromEnum(mtype);
    out[2] = flags;
    out[3] = 1;
    std.mem.writeInt(u32, out[4..8], @intCast(body.len), .little);
    std.mem.writeInt(u32, out[8..12], serial, .little);
    std.mem.writeInt(u32, out[12..16], @intCast(structs_len), .little);
    var pos: usize = 16;
    // (no length word: structs start at 16 directly)
    for (fields) |f| pos += writeField(out[pos..], f);
    @memset(out[pos .. pos + hdr_pad], 0);
    pos += hdr_pad;
    @memcpy(out[pos..][0..body.len], body);
    pos += body.len;
    return pos;
}

/// Stateful byte-stream decoder for header fields + body values.
/// Alignment is message-relative: every Cursor starts at a message (or
/// body) offset of 0, so aligning pos here matches the spec.
pub const Cursor = struct {
    buf: []const u8,
    pos: usize = 0,

    pub fn init(buf: []const u8) Cursor {
        return .{ .buf = buf };
    }

    pub fn rest(self: *const Cursor) []const u8 {
        return self.buf[self.pos..];
    }

    fn alignTo(self: *Cursor, n: usize) void {
        self.pos = (self.pos + n - 1) & ~(n - 1);
    }

    pub fn nextU32(self: *Cursor) DbusError!u32 {
        self.alignTo(4);
        if (self.pos + 4 > self.buf.len) return error.Truncated;
        const v = std.mem.readInt(u32, self.buf[self.pos..][0..4], .little);
        self.pos += 4;
        return v;
    }

    pub fn nextI32(self: *Cursor) DbusError!i32 {
        return @bitCast(try self.nextU32());
    }

    pub fn nextU8(self: *Cursor) DbusError!u8 {
        if (self.pos + 1 > self.buf.len) return error.Truncated;
        const v = self.buf[self.pos];
        self.pos += 1;
        return v;
    }

    pub fn nextBool(self: *Cursor) DbusError!bool {
        return (try self.nextU32()) != 0;
    }

    pub fn nextString(self: *Cursor) DbusError![]const u8 {
        _ = try self.nextU32();
        // Ground truth: daemon sends len EXCLUDING NUL; tolerate either.
        // Scan for NUL from pos rather than trusting declared length.
        // The value may run to the buffer end (unpadded last header
        // struct or exact-size body): clamp, don't error.
        const avail = self.buf[self.pos..];
        const nul = std.mem.indexOfScalar(u8, avail, 0) orelse return error.BadString;
        const s = avail[0..nul];
        const want: usize = (nul + 1 + 3) & ~@as(usize, 3);
        self.pos += @min(want, avail.len);
        return s;
    }

    pub fn nextSig(self: *Cursor) DbusError![]const u8 {
        if (self.pos + 1 > self.buf.len) return error.Truncated;
        const n: usize = self.buf[self.pos];
        self.pos += 1;
        if (self.pos + n + 1 > self.buf.len) return error.Truncated;
        const s = self.buf[self.pos..][0..n];
        if (self.buf[self.pos + n] != 0) return error.BadString;
        self.pos += n + 1;
        return s;
    }

    /// Variant: signature + value bytes positioned after it.
    /// Returns the inner signature; the cursor sits at the value start.
    pub fn nextVariant(self: *Cursor) DbusError![]const u8 {
        return self.nextSig();
    }
};

/// Parsed message header (borrows the input bytes).
pub const Header = struct {
    mtype: u8,
    flags: u8,
    body_len: u32,
    serial: u32,
    fields_len: u32,
};

/// Decode the 16-byte fixed header.
pub fn decodeHeader(buf: []const u8) DbusError!Header {
    if (buf.len < 16) return error.Truncated;
    if (buf[0] != 'l') return error.BadHeader; // LE only
    if (buf[3] != 1) return error.BadHeader;
    return .{
        .mtype = buf[1],
        .flags = buf[2],
        .body_len = std.mem.readInt(u32, buf[4..8], .little),
        .serial = std.mem.readInt(u32, buf[8..12], .little),
        .fields_len = std.mem.readInt(u32, buf[12..16], .little),
    };
}

/// Header field value lookup: scan the fields array for `code` and
/// return the variant signature plus the raw value bytes. Supports the
/// s/o/g/u/i/b singleton signatures we emit; null otherwise.
///
/// Ground truth (sd-bus capture, Hello flen=109=32+16+32+29): [u32 len]
/// [structs...] with the first struct at offset 0 (no length word)
/// (no pad); STRUCT{BYTE,VARIANT} has NO pad after the code byte; every
/// struct except the LAST is padded to 8. The scanner therefore accepts
/// a value that runs exactly to `end` (unpadded last struct) as well as
/// padded values. String lengths count bytes EXCLUDING the NUL.
pub fn findField(fields_bytes: []const u8, code: u8) ?struct { sig: []const u8, value: []const u8 } {
    // fields_bytes starts at message offset 16: structs packed directly
    // (NO array length word on wire). Callers pass exactly the struct
    // bytes (16 .. 16+flen).
    var pos: usize = 0;
    const end = fields_bytes.len;
    while (pos < end) {
        const start = pos;
        if (pos + 1 > end) break;
        const c = fields_bytes[pos];
        const sig_pos = start + 1;
        if (sig_pos + 1 > end) break;
        const sig_len: usize = fields_bytes[sig_pos];
        if (sig_pos + 1 + sig_len + 1 > end) break;
        const sig_s = fields_bytes[sig_pos + 1 ..][0..sig_len];
        if (fields_bytes[sig_pos + 1 + sig_len] != 0) break;
        const val_start: usize = sig_pos + 1 + sig_len + 1;
        var val_len: usize = 0;
        if (sig_s.len == 1 and (sig_s[0] == 's' or sig_s[0] == 'o')) {
            if (val_start + 4 > end) break;
            const n = std.mem.readInt(u32, fields_bytes[val_start..][0..4], .little);
            // Accept values running exactly to the array end (unpadded
            // last struct, sd-bus wire form) as well as padded values.
            const need: usize = 4 + n + 1;
            val_len = if (val_start + need == end) need else 4 + padLen(n + 1);
        } else if (sig_s.len == 1 and sig_s[0] == 'g') {
            if (val_start + 1 > end) break;
            val_len = 1 + fields_bytes[val_start] + 1;
        } else if (sig_s.len == 1 and (sig_s[0] == 'u' or sig_s[0] == 'i' or sig_s[0] == 'b')) {
            val_len = 4;
        } else break;
        if (val_start + val_len > end) break;
        if (c == code) return .{ .sig = sig_s, .value = fields_bytes[val_start .. val_start + val_len] };
        const consumed = (val_start + val_len) - start;
        const padded = (consumed + 7) & ~@as(usize, 7);
        if (padded == 0) break;
        pos = start + padded;
    }
    return null;
}

/// Minimal blocking socket wrapper with a serial counter.
/// Transport-agnostic read/write via function pointers so tests can drive
/// it with a fake socket.
pub const Transport = struct {
    ptr: *anyopaque,
    readFn: *const fn (ptr: *anyopaque, buf: []u8) DbusError!usize,
    writeFn: *const fn (ptr: *anyopaque, buf: []const u8) DbusError!void,
};

/// D-Bus connection: owns the transport, serial, unique name.
pub const Conn = struct {
    gpa: std.mem.Allocator,
    transport: Transport,
    serial: u32 = 0,
    unique_name: ?[]u8 = null,

    pub fn nextSerial(self: *Conn) u32 {
        self.serial += 1;
        if (self.serial == 0) self.serial = 1;
        return self.serial;
    }

    /// Send one method call. Returns the serial used.
    pub fn sendCall(
        self: *Conn,
        dest: []const u8,
        path: []const u8,
        iface: []const u8,
        member: []const u8,
        body_sig: []const u8,
        body: []const u8,
    ) DbusError!u32 {
        // Variant payloads need stringWireLen each; cap the stack usage.
        if (dest.len > 1024 or path.len > 1024 or iface.len > 1024 or member.len > 1024) return error.MessageTooLong;
        if (body_sig.len > 255) return error.MessageTooLong;
        var f0: [1088]u8 = undefined;
        var f1: [1088]u8 = undefined;
        var f2: [1088]u8 = undefined;
        var f3: [1088]u8 = undefined;
        var f4: [260]u8 = undefined;
        const dest_v = variantString(&f0, dest);
        const path_v = variantString(&f1, path);
        const iface_v = variantString(&f2, iface);
        // Broker-verified layout (live probe 2026-10-10): destination,
        // path, interface, member order; every struct padded on wire,
        // header flen excludes the last struct's trailing pad (sd-bus
        // convention: wire 112, flen 110). Any other order or flen
        // makes dbus-broker drop the connection (EOF, no error).
        const member_v = variantString(&f3, member);
        var fields: [6]Field = undefined;
        var nf: usize = 0;
        fields[nf] = .{ .code = F.destination, .sig = "s", .value = dest_v };
        nf += 1;
        fields[nf] = .{ .code = F.path, .sig = "o", .value = path_v };
        nf += 1;
        fields[nf] = .{ .code = F.interface, .sig = "s", .value = iface_v };
        nf += 1;
        fields[nf] = .{ .code = F.member, .sig = "s", .value = member_v };
        nf += 1;
        if (body_sig.len > 0) {
            const n = writeSig(&f4, body_sig);
            fields[nf] = .{ .code = F.signature, .sig = "g", .value = f4[0..n] };
            nf += 1;
        }
        const serial = self.nextSerial();
        var out: [65536]u8 = undefined;
        const n = try writeMessage(&out, .method_call, 0, serial, fields[0..nf], body_sig, body);
        try self.transport.writeFn(self.transport.ptr, out[0..n]);
        return serial;
    }

    /// Read exactly `n` bytes into `buf` (blocking, EINTR-retry inside
    /// the transport).
    pub fn readFull(self: *Conn, buf: []u8) DbusError!void {
        var off: usize = 0;
        while (off < buf.len) {
            const n = try self.transport.readFn(self.transport.ptr, buf[off..]);
            if (n == 0) return error.IoFailed;
            off += n;
        }
    }

    /// Scan packed header structs (no length word) and return the
    /// offset one past the last struct (each padded to 8). Area starts
    /// at message offset 16; the returned offset is area-relative
    /// (add 16 for the message offset).
    /// `area` starts at message offset 16. Last struct's trailing pad is
    /// included (it IS on wire); the header flen excludes it.
    pub fn structsEnd(area: []const u8) usize {
        var pos: usize = 0;
        while (pos < area.len) {
            const start = pos;
            if (pos + 1 > area.len) break;
            if (area[pos] == 0) break; // header end-pad
            const sig_pos = start + 1;
            if (sig_pos + 1 > area.len) break;
            const sig_len: usize = area[sig_pos];
            if (sig_len > 8) break;
            if (sig_pos + 1 + sig_len + 1 > area.len) break;
            const sig_s = area[sig_pos + 1 ..][0..sig_len];
            if (area[sig_pos + 1 + sig_len] != 0) break;
            const val_start: usize = sig_pos + 1 + sig_len + 1;
            var val_len: usize = 0;
            const code8: u8 = area[start];
            if (sig_len == 0 and code8 == 8) {
                // Empty 'g' signature (e.g. AddMatch replies carry
                // signature "": value is just len 0 + NUL, no payload).
                // Restricted to code 8: a body length word (u32 < 65536)
                // mimics the (code, 0, 0) shape, so unscoped scans must
                // stop there instead of eating body bytes.
                val_len = 0;
            } else if (sig_s.len == 1 and (sig_s[0] == 's' or sig_s[0] == 'o')) {
                if (val_start + 4 > area.len) break;
                const n: usize = std.mem.readInt(u32, area[val_start..][0..4], .little);
                if (n > 1024 * 1024) break;
                // Value may run past `area` (body-adjacent last struct):
                // require only the string+NUL, then pad within bounds.
                if (val_start + 4 + n + 1 > area.len) break;
                val_len = 4 + padLen(n + 1);
            } else if (sig_s.len == 1 and sig_s[0] == 'g') {
                if (val_start + 1 > area.len) break;
                val_len = 1 + area[val_start] + 1;
            } else if (sig_s.len == 1 and (sig_s[0] == 'u' or sig_s[0] == 'i' or sig_s[0] == 'b')) {
                val_len = 4;
            } else break;
            if (val_start + val_len > area.len) break;
            const consumed = (val_start + val_len) - start;
            pos = start + ((consumed + 7) & ~@as(usize, 7));
        }
        return pos;
    }

    /// Read one full message into an owned buffer (header + fields + body).
    /// The header fields array is [u32 len][structs]; fields_len excludes
    /// the last struct's trailing pad (<=7), so read flen + 8 slack,
    /// scan for the true struct end, then read padding + body.
    pub fn readMessage(self: *Conn, gpa: std.mem.Allocator) DbusError![]u8 {
        var fixed: [16]u8 = undefined;
        try self.readFull(&fixed);
        const h = try decodeHeader(&fixed);
        if (h.fields_len > 16 * 1024 * 1024 or h.body_len > 16 * 1024 * 1024) return error.MessageTooLong;
        // fields_len excludes the last struct's trailing pad (<=7).
        // Read flen bytes, then up to 7 more one at a time until the
        // struct scan ends cleanly (EOF tolerated: the frame may end or
        // the transport may return short reads, as FakeSock does).
        var pre = gpa.alloc(u8, @as(usize, h.fields_len) + 8) catch return error.OutOfMemory;
        defer gpa.free(pre);
        var got: usize = 0;
        {
            const want: usize = h.fields_len;
            var off: usize = 0;
            while (off < want) {
                const n = try self.transport.readFn(self.transport.ptr, pre[off..want]);
                if (n == 0) return error.IoFailed;
                off += n;
            }
            got = want;
            var extra: usize = 0;
            while (extra < 8) {
                const send_now: usize = structsEnd(pre[0..got]);
                // Clean end: scan ends within what we have, either
                // exactly at the end or at zero padding. NOTE: send_now
                // > got means the scan ran past available bytes (the
                // last struct's pad isn't in yet) — keep reading, do NOT
                // treat overshoot as clean (that desyncs the stream).
                if (send_now <= got and (send_now == got or pre[send_now] == 0)) break;
                const n = self.transport.readFn(self.transport.ptr, pre[got..][0..1]) catch break;
                if (n == 0) break;
                got += n;
                extra += n;
            }
        }
        const pre_len: usize = got;
        const send: usize = structsEnd(pre[0..got]);
        const hdr_end: usize = 16 + send;
        const hdr_pad = (8 - (hdr_end % 8)) % 8;
        const total = hdr_end + hdr_pad + h.body_len;
        if (total < 16 or total > 32 * 1024 * 1024) return error.MessageTooLong;
        var out = gpa.alloc(u8, total) catch return error.OutOfMemory;
        errdefer gpa.free(out);
        @memcpy(out[0..16], &fixed);
        const have_structs: usize = @min(send, pre_len);
        @memcpy(out[16 .. 16 + have_structs], pre[0..have_structs]);
        const rest_off: usize = 16 + have_structs;
        // Bytes already read beyond the structs (pad preview).
        const pre_pad: usize = pre_len - have_structs;
        const need_pad: usize = hdr_pad + @as(usize, h.body_len);
        const from_pre: usize = @min(pre_pad, need_pad);
        @memcpy(out[rest_off .. rest_off + from_pre], pre[have_structs..][0..from_pre]);
        if (need_pad > from_pre) try self.readFull(out[rest_off + from_pre ..]);
        return out;
    }

    /// Encode a Notify body (susssasa{sv}i). `actions`/`hints` are
    /// pre-encoded array payloads (usually empty = 4-byte zero length).
    /// Live-proven against dbus-broker + quickshell (2026-10-10): the
    /// counted body length must satisfy len % 8 == 4, or the broker drops
    /// the connection ("invalid body", 30+ probes). An exact 8-item body
    /// with len % 8 == 0 gets one extra u32 0 after hints (busctl emits
    /// exactly this form); otherwise the body is sent as-is. The daemon
    /// parses timeout from the 8th position (extra word = timeout 0,
    /// matching busctl behavior) and tolerates the shape either way.
    /// Returns bytes used.
    pub fn encodeNotify(
        out: []u8,
        app: []const u8,
        replaces: u32,
        icon: []const u8,
        summary: []const u8,
        body_text: []const u8,
        actions: []const u8,
        hints: []const u8,
        timeout_ms: i32,
    ) DbusError!usize {
        var pos: usize = 0;
        pos += writeString(out[pos..], app);
        pos = (pos + 3) & ~@as(usize, 3);
        if (pos + 4 > out.len) return error.MessageTooLong;
        std.mem.writeInt(u32, out[pos..][0..4], replaces, .little);
        pos += 4;
        pos += writeString(out[pos..], icon);
        pos += writeString(out[pos..], summary);
        pos += writeString(out[pos..], body_text);
        pos = (pos + 3) & ~@as(usize, 3);
        if (pos + 4 + actions.len + 4 + hints.len + 4 + 4 > out.len) return error.MessageTooLong;
        std.mem.writeInt(u32, out[pos..][0..4], @intCast(actions.len), .little);
        pos += 4;
        @memcpy(out[pos..][0..actions.len], actions);
        pos += actions.len;
        std.mem.writeInt(u32, out[pos..][0..4], @intCast(hints.len), .little);
        pos += 4;
        @memcpy(out[pos..][0..hints.len], hints);
        pos += hints.len;
        // Conditional extra u32 0 after hints (see above): only when
        // the body would otherwise end with len % 8 == 0 (accounting
        // for the trailing timeout word).
        if ((pos + 4) % 8 == 0) {
            std.mem.writeInt(u32, out[pos..][0..4], 0, .little);
            pos += 4;
        }
        pos = (pos + 3) & ~@as(usize, 3);
        std.mem.writeInt(i32, out[pos..][0..4], timeout_ms, .little);
        pos += 4;
        return pos;
    }

    /// Send a Notify call. Returns the serial used; the caller matches the
    /// method_return's reply_serial and reads the u32 notification id.
    pub fn sendNotify(
        self: *Conn,
        app: []const u8,
        replaces: u32,
        icon: []const u8,
        summary: []const u8,
        body_text: []const u8,
        timeout_ms: i32,
    ) DbusError!u32 {
        var body: [4096]u8 = undefined;
        const n = try encodeNotify(&body, app, replaces, icon, summary, body_text, &.{}, &.{}, timeout_ms);
        return self.sendCall(
            "org.freedesktop.Notifications",
            "/org/freedesktop/Notifications",
            "org.freedesktop.Notifications",
            "Notify",
            "susssasa{sv}i",
            body[0..n],
        );
    }

    /// Send CloseNotification(id). Fire-and-forget (no reply).
    pub fn sendClose(self: *Conn, id: u32) DbusError!void {
        var body: [4]u8 = undefined;
        std.mem.writeInt(u32, &body, id, .little);
        _ = try self.sendCall(
            "org.freedesktop.Notifications",
            "/org/freedesktop/Notifications",
            "org.freedesktop.Notifications",
            "CloseNotification",
            "u",
            &body,
        );
    }

    /// Call GetCapabilities; the caller reads the reply body's `as`
    /// (array of strings) with a Cursor.
    pub fn sendGetCapabilities(self: *Conn) DbusError!u32 {
        return self.sendCall(
            "org.freedesktop.Notifications",
            "/org/freedesktop/Notifications",
            "org.freedesktop.Notifications",
            "GetCapabilities",
            "",
            &.{},
        );
    }

    /// Call OpenURI SchemeSupported(scheme): body is the scheme string +
    /// an empty a{sv}. The reply body is one boolean.
    pub fn sendSchemeSupported(self: *Conn, scheme: []const u8) DbusError!u32 {
        var body: [1088]u8 = undefined;
        var pos: usize = 0;
        pos += writeString(body[pos..], scheme);
        if (pos + 4 > body.len) return error.MessageTooLong;
        std.mem.writeInt(u32, body[pos..][0..4], 0, .little);
        pos += 4;
        return self.sendCall(
            "org.freedesktop.portal.Desktop",
            "/org/freedesktop/portal/desktop",
            "org.freedesktop.portal.OpenURI",
            "SchemeSupported",
            "sa{sv}",
            body[0..pos],
        );
    }

    /// Add a match rule (fire-and-forget; the daemon sends no reply).
    pub fn addMatch(self: *Conn, rule: []const u8) DbusError!void {
        // Single-string body: trailing pad OMITTED (live-proven: the
        // padded form is rejected with "invalid body"; dbus-send's
        // working frame uses 4+len+1 unpadded).
        var body: [1152]u8 = undefined;
        if (4 + rule.len + 1 > body.len) return error.MessageTooLong;
        const n = writeStringUnpadded(&body, rule);
        _ = try self.sendCall(
            "org.freedesktop.DBus",
            "/org/freedesktop/DBus",
            "org.freedesktop.DBus",
            "AddMatch",
            "s",
            body[0..n],
        );
    }

    /// Hello: returns the assigned unique name (owned).
    pub fn hello(self: *Conn, gpa: std.mem.Allocator) DbusError![]u8 {
        const serial = try self.sendCall(
            "org.freedesktop.DBus",
            "/org/freedesktop/DBus",
            "org.freedesktop.DBus",
            "Hello",
            "",
            &.{},
        );
        while (true) {
            const msg = try self.readMessage(gpa);
            defer gpa.free(msg);
            const h = try decodeHeader(msg[0..16]);
            if (h.mtype == 2 or h.mtype == 3) {
                if (16 > msg.len) continue;
                const fields = msg[16..];
                const rs = findField(fields, F.reply_serial) orelse continue;
                if (rs.value.len < 4) continue;
                const got = std.mem.readInt(u32, rs.value[0..4], .little);
                if (got != serial) continue;
                const hdr_end: usize = 16 + structsEnd(msg[16..]);
                if (hdr_end > msg.len) continue;
                const body = msg[hdr_end..];
                var bc = Cursor.init(body);
                const name = bc.nextString() catch return error.HelloFailed;
                if (self.unique_name) |old| gpa.free(old);
                self.unique_name = gpa.dupe(u8, name) catch return error.OutOfMemory;
                return gpa.dupe(u8, name) catch return error.OutOfMemory;
            }
            // Ignore signals arriving before the reply.
        }
    }
};

/// Raw-fd transport over a connected AF_UNIX socket.
pub const FdTransport = struct {
    fd: std.posix.fd_t,

    pub fn read(ptr: *anyopaque, buf: []u8) DbusError!usize {
        const self: *FdTransport = @ptrCast(@alignCast(ptr));
        while (true) {
            const rc = linux.read(self.fd, buf.ptr, buf.len);
            switch (linux.errno(rc)) {
                .SUCCESS => return rc,
                .INTR => continue,
                .AGAIN => return 0,
                else => return error.IoFailed,
            }
        }
    }

    pub fn write(ptr: *anyopaque, buf: []const u8) DbusError!void {
        const self: *FdTransport = @ptrCast(@alignCast(ptr));
        var off: usize = 0;
        while (off < buf.len) {
            const rc = linux.write(self.fd, buf[off..].ptr, buf.len - off);
            switch (linux.errno(rc)) {
                .SUCCESS => {
                    if (rc == 0) return error.IoFailed;
                    off += rc;
                },
                .INTR => continue,
                .AGAIN => return error.IoFailed,
                else => return error.IoFailed,
            }
        }
    }
};

/// Connect to `path` (AF_UNIX stream, CLOEXEC).
pub fn connectSock(path: []const u8) DbusError!std.posix.fd_t {
    if (path.len + 1 > 108) return error.MessageTooLong;
    const rc = linux.socket(@as(u32, linux.AF.UNIX), @as(u32, linux.SOCK.STREAM | linux.SOCK.CLOEXEC), 0);
    if (linux.errno(rc) != .SUCCESS) return error.IoFailed;
    const fd: std.posix.fd_t = @intCast(rc);
    errdefer _ = linux.close(fd);
    var path_arr: [108]u8 = undefined;
    @memset(&path_arr, 0);
    var addr = linux.sockaddr.un{ .path = path_arr };
    @memcpy(addr.path[0..path.len], path);
    const addrlen: linux.socklen_t = @intCast(2 + path.len + 1);
    if (linux.errno(linux.connect(fd, @ptrCast(&addr), addrlen)) != .SUCCESS) return error.IoFailed;
    return fd;
}

/// Resolve the session bus address: $DBUS_SESSION_BUS_ADDRESS
/// (unix:path=... only) else $XDG_RUNTIME_DIR/bus. Caller owns result.
/// Null means "no usable session bus" (abstract-only, TCP-only, unset):
/// callers run headless instead of failing.
pub fn busAddress(gpa: std.mem.Allocator) DbusError!?[]u8 {
    const raw = std.c.getenv("DBUS_SESSION_BUS_ADDRESS");
    if (raw) |r| {
        const s = std.mem.span(r);
        if (s.len > 0) {
            // Accept unix:path=/x (first address wins); abstract-only or
            // TCP addresses are refused (null = headless fallback).
            if (std.mem.startsWith(u8, s, "unix:path=")) {
                const semi = std.mem.indexOfScalar(u8, s, ';') orelse s.len;
                const p = s["unix:path=".len..semi];
                if (p.len == 0) return null;
                return gpa.dupe(u8, p) catch return error.OutOfMemory;
            }
            return null;
        }
    }
    const rt = std.c.getenv("XDG_RUNTIME_DIR") orelse return null;
    const rs = std.mem.span(rt);
    if (rs.len == 0) return null;
    return std.fmt.allocPrint(gpa, "{s}/bus", .{rs}) catch return error.OutOfMemory;
}

/// Read a CRLF-terminated line (no NUL) from fd into buf. Returns length.
fn readLine(fd: std.posix.fd_t, buf: []u8) DbusError!usize {
    var pos: usize = 0;
    while (pos < buf.len) {
        var b: [1]u8 = undefined;
        const rc = linux.read(fd, &b, 1);
        switch (linux.errno(rc)) {
            .SUCCESS => {},
            .INTR => continue,
            else => return error.AuthFailed,
        }
        if (rc == 0) return error.AuthFailed;
        if (b[0] == '\n') break;
        if (b[0] == '\r') continue;
        buf[pos] = b[0];
        pos += 1;
    }
    return pos;
}

fn writeAllFd(fd: std.posix.fd_t, buf: []const u8) DbusError!void {
    var off: usize = 0;
    while (off < buf.len) {
        const rc = linux.write(fd, buf[off..].ptr, buf.len - off);
        switch (linux.errno(rc)) {
            .SUCCESS => {
                if (rc == 0) return error.AuthFailed;
                off += rc;
            },
            .INTR => continue,
            else => return error.AuthFailed,
        }
    }
}

/// AUTH EXTERNAL handshake (empty initial response): NUL + "AUTH
/// EXTERNAL\r\n" -> "DATA\r\n" -> "DATA\r\n" -> "OK <guid>\r\n" ->
/// "NEGOTIATE_UNIX_FD\r\n" (ERROR tolerated) -> "BEGIN\r\n".
pub fn authExternal(fd: std.posix.fd_t) DbusError!void {
    try writeAllFd(fd, &[_]u8{0});
    try writeAllFd(fd, "AUTH EXTERNAL\r\n");
    var line: [256]u8 = undefined;
    const n1 = try readLine(fd, &line);
    if (!std.mem.eql(u8, line[0..n1], "DATA")) return error.AuthFailed;
    try writeAllFd(fd, "DATA\r\n");
    const n2 = try readLine(fd, &line);
    if (!std.mem.startsWith(u8, line[0..n2], "OK ")) return error.AuthFailed;
    if (n2 <= 3) return error.BadServerGuid;
    try writeAllFd(fd, "NEGOTIATE_UNIX_FD\r\n");
    const n3 = try readLine(fd, &line);
    const l3 = line[0..n3];
    if (!std.mem.startsWith(u8, l3, "AGREE_UNIX_FD") and !std.mem.eql(u8, l3, "ERROR")) return error.AuthFailed;
    try writeAllFd(fd, "BEGIN\r\n");
}

/// Open a session-bus connection: resolve address, connect, auth, Hello.
/// Returns the fd + state (caller closes via connClose); the Conn borrows
/// them. `unique_name` is owned by the Conn.
pub fn connect(gpa: std.mem.Allocator) DbusError!struct { conn: Conn, fd_state: *FdTransport, fd: std.posix.fd_t } {
    const path = try busAddress(gpa);
    defer if (path) |p| gpa.free(p);
    const p = path orelse return error.AuthFailed;
    const fd = try connectSock(p);
    errdefer _ = linux.close(fd);
    try authExternal(fd);
    const state = gpa.create(FdTransport) catch return error.OutOfMemory;
    state.* = .{ .fd = fd };
    var conn = Conn{
        .gpa = gpa,
        .transport = .{ .ptr = state, .readFn = FdTransport.read, .writeFn = FdTransport.write },
    };
    const name = try conn.hello(gpa);
    gpa.free(name);
    return .{ .conn = conn, .fd_state = state, .fd = fd };
}

pub fn connClose(gpa: std.mem.Allocator, fd_state: *FdTransport, fd: std.posix.fd_t) void {
    _ = linux.close(fd);
    gpa.destroy(fd_state);
}

// ---------------------------------------------------------------------------
// Tests: marshal vectors + hermetic fake-socket framing.
// ---------------------------------------------------------------------------

const FakeSock = struct {
    read_buf: std.ArrayListUnmanaged(u8) = .empty,
    read_pos: usize = 0,
    written: std.ArrayListUnmanaged(u8) = .empty,
    gpa: std.mem.Allocator,

    fn read(ptr: *anyopaque, buf: []u8) DbusError!usize {
        const self: *FakeSock = @ptrCast(@alignCast(ptr));
        const avail = self.read_buf.items.len - self.read_pos;
        if (avail == 0) return error.IoFailed; // EOF, not AGAIN
        const n = @min(avail, buf.len);
        @memcpy(buf[0..n], self.read_buf.items[self.read_pos..][0..n]);
        self.read_pos += n;
        return n;
    }

    fn write(ptr: *anyopaque, buf: []const u8) DbusError!void {
        const self: *FakeSock = @ptrCast(@alignCast(ptr));
        self.written.appendSlice(self.gpa, buf) catch return error.OutOfMemory;
    }
};

fn hexVal(c: u8) u8 {
    return switch (c) {
        '0'...'9' => c - '0',
        'a'...'f' => c - 'a' + 10,
        'A'...'F' => c - 'A' + 10,
        else => 0,
    };
}

