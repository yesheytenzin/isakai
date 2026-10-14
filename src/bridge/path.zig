// SPDX-License-Identifier: GPL-3.0-or-later

//! Single definition of the per-app desktop-bridge endpoint.
//!
//! `AF_UNIX` addresses live in `sockaddr_un.sun_path`, which holds 108
//! bytes including the terminating NUL: a socket path longer than 107
//! bytes cannot be bound or connected. Work directories are arbitrary and
//! often deep (`<work>/run/<package>/bridge.sock` exceeded the limit in
//! the OpenCalc acceptance runs), so the endpoint is derived instead from
//! a digest of (work dir, package) under a short fixed base:
//!
//!     /tmp/isakai-<launcher-uid>-<16 hex>/bridge.sock
//!
//! The base is `/tmp` rather than `$XDG_RUNTIME_DIR` because the sandboxed
//! app uid must traverse the directory in the privileged path, and
//! `$XDG_RUNTIME_DIR` is typically mode 0700 owned by the launcher.
//!
//! Consumers that must agree byte-for-byte: the launcher that creates the
//! directory and spawns the daemon (`src/launch.zig`), the daemon that
//! binds the socket (`src/dbus_bridge.zig`), the privileged helper that
//! passes `ISAKAI_BRIDGE` to the sandbox and grants Landlock access
//! (`src/uid_helper.zig`), and the JNI client inside the sandbox
//! (`src/bridge/desktop.zig`).

const std = @import("std");

/// Socket file name inside the endpoint directory.
pub const sock_name = "bridge.sock";

/// Bytes available in `sockaddr_un.sun_path` including the terminating NUL.
pub const max_path: usize = 108;

/// Longest directory path that still leaves room for `/bridge.sock` + NUL.
pub const max_dir: usize = max_path - 1 - (1 + sock_name.len);

/// Base directory for endpoint directories. `/tmp` is the documented
/// default on every supported distribution and is traversable by any uid.
pub const tmp_base = "/tmp";

/// Prefix of every endpoint directory name.
pub const dir_prefix = "isakai-";

/// Stable identity of one (work dir, package) pair: 16 lowercase hex
/// characters of SHA-256. Deterministic so the launcher and the helper
/// derive the same endpoint without a side channel.
pub const Digest = [16]u8;

pub fn digest(work: []const u8, package: []const u8) Digest {
    var h = std.crypto.hash.sha2.Sha256.init(.{});
    h.update("isakai-bridge-v1\x00");
    h.update(work);
    h.update("\x00");
    h.update(package);
    var sum: [32]u8 = undefined;
    h.final(&sum);
    const hex = "0123456789abcdef";
    var out: Digest = undefined;
    for (sum[0..8], 0..) |b, i| {
        out[i * 2] = hex[b >> 4];
        out[i * 2 + 1] = hex[b & 0xf];
    }
    return out;
}

/// Endpoint directory for `work`/`package` owned by launcher `uid`.
/// Caller owns the result.
pub fn dirFor(gpa: std.mem.Allocator, work: []const u8, package: []const u8, uid: u32) ![]u8 {
    return std.fmt.allocPrint(gpa, "{s}/{s}{d}-{s}", .{ tmp_base, dir_prefix, uid, digest(work, package) });
}

/// `<dir>/bridge.sock`. Caller owns the result.
pub fn socketPath(gpa: std.mem.Allocator, dir: []const u8) ![]u8 {
    return std.fmt.allocPrint(gpa, "{s}/{s}", .{ dir, sock_name });
}

/// True when `path` is a usable endpoint directory: absolute, free of
/// `..`, short enough that `<path>/bridge.sock` fits in `sun_path`, and
/// named inside the endpoint base so a launcher cannot ask the privileged
/// helper to grant or bind somewhere unrelated.
pub fn validDir(path: []const u8) bool {
    if (path.len == 0 or path.len > max_dir) return false;
    if (path[0] != '/') return false;
    if (std.mem.indexOf(u8, path, "..") != null) return false;
    if (!std.mem.startsWith(u8, path, tmp_base ++ "/" ++ dir_prefix)) return false;
    if (std.mem.indexOfScalar(u8, path[tmp_base.len + 1 ..], '/') != null) return false;
    return true;
}

/// True when a full socket path fits `sun_path` with its NUL.
pub fn fitsSocketPath(path: []const u8) bool {
    return path.len + 1 <= max_path;
}

fn startsWith(haystack: []const u8, needle: []const u8) bool {
    return std.mem.startsWith(u8, haystack, needle);
}
