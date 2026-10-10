// SPDX-License-Identifier: GPL-3.0-or-later

//! PulseAudio playback via libpulse-simple (S16LE; rate/channels chosen
//! per stream, server permitting). Attr NULL (server default buffering).
//! In `zig test` (builtin.is_test) a fake backend records writes in
//! memory so unit tests run headless.

const std = @import("std");
const builtin = @import("builtin");

const use_fake = builtin.is_test;

/// Max single-write byte length: 8 MiB (bounds one JNI copy).
pub const max_write_len: usize = 8 * 1024 * 1024;

pub const sample_rate: u32 = 44100;
pub const channels: u8 = 2;

/// Supported playback rates (pa_simple accepts anything the server
/// allows; we restrict to common rates, fail-closed otherwise).
pub const supported_rates = [_]u32{ 8000, 11025, 16000, 22050, 32000, 44100, 48000 };

/// Supported channel counts: mono or stereo.
pub const supported_channels = [_]u8{ 1, 2 };

pub fn supportsFormat(rate: u32, ch: u8) bool {
    var ok_rate = false;
    for (supported_rates) |r| if (r == rate) {
        ok_rate = true;
        break;
    };
    if (!ok_rate) return false;
    for (supported_channels) |c| if (c == ch) return true;
    return false;
}

const PaFormat = enum(c_int) {
    u8 = 0,
    alaw = 1,
    ulaw = 2,
    s16le = 3,
    s16be = 4,
    float32le = 5,
    float32be = 6,
    s32le = 7,
    s32be = 8,
    s24le = 9,
    s24be = 10,
    s24_32le = 11,
    s24_32be = 12,
    max = 13,
    invalid = -1,
};

const PaDirection = enum(c_int) {
    nodirection = 0,
    playback = 1,
    record = 2,
    upload = 3,
};

const PaSampleSpec = extern struct {
    format: PaFormat,
    rate: u32,
    channels: u8,
    _pad: [3]u8 = .{ 0, 0, 0 },
};

const RealDecls = if (use_fake) struct {} else struct {
    pub extern fn pa_simple_new(
        server: ?[*:0]const u8,
        name: [*:0]const u8,
        dir: PaDirection,
        dev: ?[*:0]const u8,
        stream_name: [*:0]const u8,
        ss: *const PaSampleSpec,
        map: ?*const anyopaque,
        attr: ?*const anyopaque,
        @"error": ?*c_int,
    ) ?*anyopaque;
    pub extern fn pa_simple_write(s: ?*anyopaque, data: [*]const u8, bytes: usize, @"error": ?*c_int) c_int;
    pub extern fn pa_simple_drain(s: ?*anyopaque, @"error": ?*c_int) c_int;
    pub extern fn pa_simple_free(s: ?*anyopaque) void;
};

var fake_open: bool = false;
var fake_bytes: usize = 0;
var fake_drained: bool = false;
var fake_rate: u32 = sample_rate;
var fake_channels: u8 = channels;
pub const Stream = struct {
    handle: *anyopaque,
    rate: u32 = sample_rate,
    channels_n: u8 = channels,

    pub fn write(self: *Stream, data: []const u8) bool {
        if (data.len == 0) return true;
        if (data.len > max_write_len) return false;
        if (use_fake) {
            fake_bytes += data.len;
            return true;
        }
        const rc = RealDecls.pa_simple_write(self.handle, data.ptr, data.len, null);
        return rc == 0;
    }

    pub fn drain(self: *Stream) bool {
        if (use_fake) {
            fake_drained = true;
            return true;
        }
        return RealDecls.pa_simple_drain(self.handle, null) == 0;
    }

    pub fn close(self: *Stream) void {
        if (use_fake) {
            fake_open = false;
            self.handle = @ptrFromInt(1); // poison, never deref'd
            return;
        }
        RealDecls.pa_simple_free(self.handle);
        self.handle = @ptrFromInt(1); // poison, never deref'd
    }
};

pub fn open() ?Stream {
    return openFormat(sample_rate, channels);
}

/// Open a stream at `rate`/`ch` (S16LE). Null when the format is
/// unsupported or the server rejects it. Fake backend: records the
/// format for unit tests.
pub fn openFormat(rate: u32, ch: u8) ?Stream {
    if (!supportsFormat(rate, ch)) return null;
    if (use_fake) {
        if (fake_open) return null;
        fake_open = true;
        fake_bytes = 0;
        fake_drained = false;
        fake_rate = rate;
        fake_channels = ch;
        return .{ .handle = @ptrFromInt(0xA0D10), .rate = rate, .channels_n = ch };
    }
    const ss = PaSampleSpec{ .format = .s16le, .rate = rate, .channels = ch };
    const s = RealDecls.pa_simple_new(
        null,
        "isakai",
        .playback,
        null,
        "isakai-playback",
        &ss,
        null,
        null,
        null,
    ) orelse return null;
    return .{ .handle = s, .rate = rate, .channels_n = ch };
}


/// Test helper: bytes written through the fake backend since open.
pub fn fakeWritten() usize {
    return fake_bytes;
}

/// Test helper: whether drain was called since open.
pub fn fakeDrained() bool {
    return fake_drained;
}

/// Test helper: rate negotiated by the last fake open.
pub fn fakeRate() u32 {
    return fake_rate;
}

/// Test helper: channels negotiated by the last fake open.
pub fn fakeChannels() u8 {
    return fake_channels;
}

test "fake backend open/write/drain/close round-trips" {
    const st = open() orelse return error.TestUnexpectedResult;
    var s = st;
    try std.testing.expect(s.write("abcd"));
    try std.testing.expectEqual(@as(usize, 4), fakeWritten());
    try std.testing.expect(!fakeDrained());
    try std.testing.expect(s.drain());
    try std.testing.expect(fakeDrained());
    s.close();
}

test "fake backend rejects oversize write" {
    const st = open() orelse return error.TestUnexpectedResult;
    var s = st;
    defer s.close();
    // Heap, not stack: max_write_len+1 is 8 MiB + 1.
    const buf = try std.testing.allocator.alloc(u8, max_write_len + 1);
    defer std.testing.allocator.free(buf);
    try std.testing.expect(!s.write(buf));
    try std.testing.expectEqual(@as(usize, 0), fakeWritten());
}

test "openFormat negotiates 2+ rates and rejects bad formats" {
    try std.testing.expect(supportsFormat(44100, 2));
    try std.testing.expect(supportsFormat(48000, 2));
    try std.testing.expect(supportsFormat(22050, 1));
    try std.testing.expect(!supportsFormat(12345, 2));
    try std.testing.expect(!supportsFormat(44100, 6));
    var s44 = openFormat(44100, 2) orelse return error.TestUnexpectedResult;
    try std.testing.expectEqual(@as(u32, 44100), fakeRate());
    try std.testing.expectEqual(@as(u8, 2), fakeChannels());
    s44.close();
    var s48 = openFormat(48000, 2) orelse return error.TestUnexpectedResult;
    try std.testing.expectEqual(@as(u32, 48000), fakeRate());
    s48.close();
    try std.testing.expect(openFormat(12345, 2) == null);
    try std.testing.expect(openFormat(44100, 6) == null);
}
