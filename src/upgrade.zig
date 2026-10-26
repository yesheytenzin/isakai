// SPDX-License-Identifier: GPL-3.0-or-later

//! In-place upgrades with certificate continuity and one-generation
//! rollback. Only staged code dirs and the registry entry move; the data
//! dir is never touched, so user data survives upgrade and rollback by
//! construction.

const std = @import("std");
const registry = @import("registry");
const install = @import("install");
const apksig = @import("apksig");
const launch = @import("launch");

/// Certificate-continuity check: the new APK's leaf fingerprint must equal
/// the stored install-time pin, else the upgrade fails closed. Both sides
/// unsigned (null/null, dev fixtures via ISAKAI_ALLOW_UNSIGNED=1) passes;
/// any other null side fails. There is deliberately no --force escape
/// hatch in v1.
pub fn signerMatches(stored: ?[]const u8, fresh: ?[]const u8) bool {
    if (stored == null and fresh == null) return true;
    if (stored == null or fresh == null) return false;
    return std.mem.eql(u8, stored.?, fresh.?);
}

/// M6e continuity: the new APK's verified signer set must equal the
/// pinned set. Falls back to the legacy single-fp pin for pre-M6e
/// entries (stored set empty): fresh set must be exactly that one fp.
/// Unsigned upgrades (ISAKAI_ALLOW_UNSIGNED) only pass when the stored
/// entry is also unsigned — same fail-closed rule as before.
pub fn signerSetMatches(pkg: registry.Package, fresh: []const []const u8) bool {
    const stored = pkg.signer_sha256_set;
    if (stored.len == 0) {
        // Legacy entry: pin lives in signer_sha256 (or null = unsigned).
        if (pkg.signer_sha256 == null and fresh.len == 0) return true;
        if (pkg.signer_sha256 == null or fresh.len != 1) return false;
        return std.mem.eql(u8, pkg.signer_sha256.?, fresh[0]);
    }
    if (stored.len != fresh.len) return false;
    for (stored, fresh) |s, f| {
        if (!std.mem.eql(u8, s, f)) return false;
    }
    return true;
}

pub const UpgradeResult = struct {
    package: []const u8,
    uid: u32,
    code_dir: []const u8,
    prev_code_dir: []const u8,
};

/// Upgrade an installed app to the build in `apk_path`: inspect, re-verify
/// the v2 signature on the same path as install, enforce signer continuity,
/// stage the new code dir alongside the old one, translate it eagerly, then
/// atomically switch the registry entry (apk + signer + installed_at) and
/// keep the old code dir as `prev_apk`.
///
/// Fail-closed: a continuity mismatch, a missing install, or any staging
/// failure leaves the registry and the old code dir untouched. The old dir
/// is kept (one generation), never deleted; only an evicted grandparent
/// (the previous prev_apk) is best-effort removed after a committed switch.
pub fn upgrade(
    io: std.Io,
    gpa: std.mem.Allocator,
    env: *const std.process.Environ.Map,
    work: []const u8,
    apk_path: []const u8,
) anyerror!UpgradeResult {
    var m = try install.inspect(io, gpa, apk_path);
    defer install.freeManifest(gpa, &m);
    const fingerprints: ?[][]u8 = apksig.verifyApk(io, gpa, apk_path) catch |err| blk: {
        if (registry.envFlag(env, "ISAKAI_ALLOW_UNSIGNED")) {
            std.log.warn("upgrading with unsigned APK (ISAKAI_ALLOW_UNSIGNED=1)", .{});
            break :blk null;
        }
        return err;
    };
    defer {
        if (fingerprints) |fs| {
            for (fs) |f| gpa.free(f);
            gpa.free(fs);
        }
    }

    var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
    defer lock.release();
    var reg = try registry.Registry.loadChecked(io, gpa, work);
    defer reg.deinit();
    const pkg = reg.get(m.package) orelse return error.NotInstalled;
    if (!signerSetMatches(pkg, fingerprints orelse &.{})) return error.SignerMismatch;

    // Downgrades are rejected: the new build must not be older than installed.
    if (m.version_code < pkg.version_code) return error.DowngradeRejected;

    const digest = try install.sha256File(io, gpa, apk_path);
    defer gpa.free(digest);
    const code_dir = try std.fmt.allocPrint(gpa, "{s}/code/{s}/{s}", .{ work, m.package, digest });
    errdefer gpa.free(code_dir);
    if (std.mem.eql(u8, code_dir, pkg.apk)) return error.AlreadyCurrent;
    const code_parent = try std.fmt.allocPrint(gpa, "{s}/code/{s}", .{ work, m.package });
    defer gpa.free(code_parent);
    try std.Io.Dir.cwd().createDirPath(io, code_parent);

    try install.stage(io, gpa, apk_path, code_dir);
    errdefer std.Io.Dir.cwd().deleteTree(io, code_dir) catch {};
    try install.verifyRetained(io, gpa, code_dir, digest);
    try install.verifyStagedIdentity(io, gpa, code_dir, &m, fingerprints orelse &.{});

    // Eager translate on the launch-time path (idempotent via its marker):
    // a broken build fails the upgrade, never the next launch. Failure
    // removes the staged dir; the registry still points at the old one.
    try launch.translateApp(io, gpa, code_dir);

    // ATOMIC SWITCH under the registry lock: repoint apk, pin the new
    // signer, stamp installed_at. reg.save goes through writeAtomic
    // (O_EXCL tmp + fsync + rename): a crash lands on either the old or
    // the new entry, never a torn one. The data dir is not referenced
    // anywhere in this function: upgrade cannot lose user data.
    const prev_code_dir = try gpa.dupe(u8, pkg.apk);
    errdefer gpa.free(prev_code_dir);
    var next = pkg;
    next.apk = code_dir; // reg.add dupes into its arena
    next.prev_apk = prev_code_dir; // ditto
    if (fingerprints) |fs| {
        next.signer_sha256 = if (fs.len > 0) fs[0] else null; // ditto (or null)
        next.signer_sha256_set = fs; // ditto (dupes every element)
    }
    next.installed_at = install.nowSeconds(io);
    next.version_code = m.version_code;
    try reg.updateState(io, gpa, work, next);

    // One generation only: the evicted grandparent (the previous prev_apk),
    // if any, is best-effort removed now that the switch is committed.
    // Failure only warns; the registry already points at the new code.
    if (pkg.prev_apk) |grandparent| {
        if (!std.mem.eql(u8, grandparent, code_dir) and !std.mem.eql(u8, grandparent, pkg.apk)) {
            std.Io.Dir.cwd().deleteTree(io, grandparent) catch |err| {
                std.log.warn("upgrade: keeping orphaned grandparent {s}: {t}", .{ grandparent, err });
            };
        }
    }

    return .{
        .package = try gpa.dupe(u8, pkg.name),
        .uid = pkg.uid,
        .code_dir = code_dir,
        .prev_code_dir = prev_code_dir,
    };
}

pub const RollbackResult = struct {
    package: []const u8,
    code_dir: []const u8,
    prev_code_dir: []const u8,
};

/// Roll back to the previous generation: when `prev_apk` is set and its
/// code dir still exists, atomically repoint the registry at it and keep
/// the newer generation as the new `prev_apk`, so rollback toggles. Fails
/// clearly (error.NoPreviousVersion / error.PreviousGone) otherwise, with
/// the registry untouched. The data dir is never touched.
pub fn rollback(
    io: std.Io,
    gpa: std.mem.Allocator,
    work: []const u8,
    selector: []const u8,
) anyerror!RollbackResult {
    var lock = try registry.Registry.Lock.acquire(io, gpa, work, true);
    defer lock.release();
    var reg = try registry.Registry.loadChecked(io, gpa, work);
    defer reg.deinit();
    const pkg = reg.resolve(gpa, selector) catch return error.NotInstalled;
    const prev = pkg.prev_apk orelse return error.NoPreviousVersion;
    // The previous generation must still be on disk; never repoint the
    // registry at a missing dir.
    _ = std.Io.Dir.cwd().statFile(io, prev, .{}) catch return error.PreviousGone;
    var next = pkg;
    next.apk = prev;
    next.prev_apk = pkg.apk;
    try reg.updateState(io, gpa, work, next);
    return .{
        .package = try gpa.dupe(u8, pkg.name),
        .code_dir = try gpa.dupe(u8, prev),
        .prev_code_dir = try gpa.dupe(u8, pkg.apk),
    };
}

