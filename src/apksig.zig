// SPDX-License-Identifier: GPL-3.0-or-later

//! APK Signature Scheme v2 verification. v2-only by design: Android 11+
//! requires v2+, and the spec mandates fail-closed (no v1 fallback when a
//! v2 block exists). Leaf-pin model: no CA/PKIX chain validation, exactly
//! like Android's own installer (any cert is an identity; updates must
//! match the pinned signer).
//!
//! Port structure follows agusibrahim/apksig-go; crypto uses Zig std
//! (Certificate DER parse + RSA PKCS1v15/PSS + ECDSA P-256/P-384).
//! Reference: https://source.android.com/docs/security/features/apksigning/v2

const std = @import("std");

pub const SigError = error{
    EocdNotFound,
    TrailingBytes,
    NoSigningBlock,
    BadBlockMagic,
    BlockSizeMismatch,
    NoV2Block,
    Truncated,
    BadLengthPrefix,
    NoSigners,
    SignerFailed,
    UnknownAlgorithm,
    UnsupportedAlgorithm,
    DigestMismatch,
    CertParseFailed,
    NoCertificates,
    PublicKeyMismatch,
    DigestOrderMismatch,
    OutOfMemory,
};

// APK Signing Block magic: "APK Sig Block 42".
const block_magic = "APK Sig Block 42";
const v2_id: u32 = 0x7109871a;

// Signature algorithm IDs.
const sig_rsa_pss_sha256: u32 = 0x0101;
const sig_rsa_pss_sha512: u32 = 0x0102;
const sig_rsa_pkcs1_sha256: u32 = 0x0103;
const sig_rsa_pkcs1_sha512: u32 = 0x0104;
const sig_ecdsa_sha256: u32 = 0x0201;
const sig_ecdsa_sha512: u32 = 0x0202;
const sig_dsa_sha256: u32 = 0x0301;

const ContentHash = enum { sha256, sha512 };

fn contentHash(alg: u32) SigError!ContentHash {
    return switch (alg) {
        sig_rsa_pss_sha256, sig_rsa_pkcs1_sha256, sig_ecdsa_sha256, sig_dsa_sha256 => .sha256,
        sig_rsa_pss_sha512, sig_rsa_pkcs1_sha512, sig_ecdsa_sha512 => .sha512,
        else => error.UnknownAlgorithm,
    };
}

fn readInt(comptime T: type, data: []const u8, at: usize) SigError!T {
    comptime std.debug.assert(T == u16 or T == u32 or T == u64);
    if (at + @sizeOf(T) > data.len) return error.Truncated;
    return std.mem.readInt(T, data[at..][0..@sizeOf(T)], .little);
}

/// Length-prefixed slice: u32 LE length + bytes. Advances pos.
fn readLP(data: []const u8, pos: *usize) SigError![]const u8 {
    const len = try readInt(u32, data, pos.*);
    pos.* += 4;
    const end = std.math.add(usize, pos.*, len) catch return error.BadLengthPrefix;
    if (end > data.len) return error.Truncated;
    const out = data[pos.*..end];
    pos.* = end;
    return out;
}

pub const Eocd = struct {
    offset: usize,
    cd_start: u64,
    cd_size: u64,
    comment_len: u16,
};

/// Find EOCD by scanning the last 64KiB+22 bytes for 0x06054b50.
pub fn findEocd(data: []const u8) SigError!Eocd {
    if (data.len < 22) return error.EocdNotFound;
    const window_start = if (data.len > 65558) data.len - 65558 else 0;
    var at = data.len - 22;
    while (true) {
        if (std.mem.readInt(u32, data[at..][0..4], .little) == 0x06054b50) {
            const comment_len = std.mem.readInt(u16, data[at + 20 ..][0..2], .little);
            if (at + 22 + comment_len == data.len) {
                const cd_size = std.mem.readInt(u32, data[at + 12 ..][0..4], .little);
                const cd_start = std.mem.readInt(u32, data[at + 16 ..][0..4], .little);
                return .{
                    .offset = at,
                    .cd_start = cd_start,
                    .cd_size = cd_size,
                    .comment_len = comment_len,
                };
            }
        }
        if (at == window_start) break;
        at -= 1;
    }
    return error.EocdNotFound;
}

pub const SigningBlock = struct {
    start: usize, // offset of size1 field
    end: usize, // == central directory start
};

/// Locate the APK Signing Block immediately before the central directory.
pub fn locateBlock(data: []const u8, eocd: Eocd) SigError!SigningBlock {
    if (eocd.cd_start < 24 or eocd.cd_start > data.len) return error.NoSigningBlock;
    const footer_at: usize = @intCast(eocd.cd_start - 24);
    const size2 = try readInt(u64, data, footer_at);
    if (!std.mem.eql(u8, data[footer_at + 8 .. footer_at + 24], block_magic)) {
        return error.NoSigningBlock;
    }
    const total = std.math.add(u64, size2, 8) catch return error.BlockSizeMismatch;
    if (total > eocd.cd_start) return error.BlockSizeMismatch;
    const start: usize = @intCast(eocd.cd_start - total);
    const size1 = try readInt(u64, data, start);
    if (size1 != size2) return error.BlockSizeMismatch;
    return .{ .start = start, .end = @intCast(eocd.cd_start) };
}

/// Find the value of an ID-value pair (e.g. v2 block) inside the signing block.
pub fn findPair(data: []const u8, block: SigningBlock, want_id: u32) SigError!?[]const u8 {
    var pos = block.start + 8;
    const end = block.end - 24;
    while (pos < end) {
        const pair_len = try readInt(u64, data, pos);
        pos += 8;
        if (pair_len < 4) return error.BadLengthPrefix;
        const id = try readInt(u32, data, pos);
        pos += 4;
        const val_len = pair_len - 4;
        const val_end = std.math.add(usize, pos, val_len) catch return error.BadLengthPrefix;
        if (val_end > end) return error.Truncated;
        if (id == want_id) return data[pos..val_end];
        pos = val_end;
    }
    return null;
}

/// Compute the v2 content digest over sections [0,block) + [cd,eocd-end).
/// EOCD's cd-offset field is patched to the block start before hashing.
pub fn contentDigest(
    gpa: std.mem.Allocator,
    data: []const u8,
    block: SigningBlock,
    eocd: Eocd,
    hash: ContentHash,
) SigError![]u8 {
    const chunk_size: usize = 1 << 20;
    // Sections in order: 1 = [0, block.start), 3 = [cd_start, eocd.offset), 4 = EOCD (patched).
    var digests: std.ArrayListUnmanaged([]const u8) = .empty;
    defer {
        for (digests.items) |d| gpa.free(d);
        digests.deinit(gpa);
    }
    const sections = [_]struct { from: usize, to: usize }{
        .{ .from = 0, .to = block.start },
        .{ .from = @intCast(eocd.cd_start), .to = eocd.offset },
    };
    for (sections) |s| {
        var off = s.from;
        while (off < s.to) {
            const end = @min(off + chunk_size, s.to);
            const chunk = data[off..end];
            const cd = try hashChunk(gpa, chunk, hash);
            try digests.append(gpa, cd);
            off = end;
        }
    }
    // EOCD section: copy and patch cd-offset field (+16) to block start.
    {
        const eocd_bytes = try gpa.dupe(u8, data[eocd.offset..]);
        defer gpa.free(eocd_bytes);
        std.mem.writeInt(u32, eocd_bytes[16..][0..4], @intCast(block.start), .little);
        var off: usize = 0;
        while (off < eocd_bytes.len) {
            const end = @min(off + chunk_size, eocd_bytes.len);
            const cd = try hashChunk(gpa, eocd_bytes[off..end], hash);
            try digests.append(gpa, cd);
            off = end;
        }
    }
    // Top: H(0x5A || count LE-u32 || concat(digests)).
    {
        var cb: [4]u8 = undefined;
        std.mem.writeInt(u32, &cb, @intCast(digests.items.len), .little);
        const parts = try gpa.alloc([]const u8, digests.items.len + 1);
        defer gpa.free(parts);
        parts[0] = &cb;
        @memcpy(parts[1..], digests.items);
        return switch (hash) {
            .sha256 => hashWith(std.crypto.hash.sha2.Sha256, 0x5A, parts, gpa),
            .sha512 => hashWith(std.crypto.hash.sha2.Sha512, 0x5A, parts, gpa),
        };
    }
}

/// One-shot hash H(prefix || concat(parts)). Centralizes the sha256/sha512
/// branches of contentDigest's top hash and hashChunk: callers frame their
/// length words as ordinary parts.
fn hashWith(comptime H: type, prefix: u8, parts: []const []const u8, gpa: std.mem.Allocator) SigError![]u8 {
    var h = H.init(.{});
    h.update(&[_]u8{prefix});
    for (parts) |p| h.update(p);
    var out: [H.digest_length]u8 = undefined;
    h.final(&out);
    return gpa.dupe(u8, &out) catch return error.OutOfMemory;
}

fn hashChunk(gpa: std.mem.Allocator, chunk: []const u8, hash: ContentHash) SigError![]u8 {
    var lb: [4]u8 = undefined;
    std.mem.writeInt(u32, &lb, @intCast(chunk.len), .little);
    const parts = [_][]const u8{ &lb, chunk };
    return switch (hash) {
        .sha256 => hashWith(std.crypto.hash.sha2.Sha256, 0xA5, &parts, gpa),
        .sha512 => hashWith(std.crypto.hash.sha2.Sha512, 0xA5, &parts, gpa),
    };
}

const Cert = std.crypto.Certificate;

/// Strip DER-INTEGER leading zero padding (added for the sign bit).
fn stripLeadingZeros(b: []const u8) []const u8 {
    var i: usize = 0;
    while (i + 1 < b.len and b[i] == 0) : (i += 1) {}
    return b[i..];
}

fn managedFromBytes(gpa: std.mem.Allocator, bytes: []const u8) SigError!std.math.big.int.Managed {
    var m = std.math.big.int.Managed.init(gpa) catch return error.OutOfMemory;
    errdefer m.deinit();
    const s = stripLeadingZeros(bytes);
    const hex_digits = "0123456789abcdef";
    var hex = gpa.alloc(u8, s.len * 2) catch return error.OutOfMemory;
    defer gpa.free(hex);
    for (s, 0..) |byte, i| {
        hex[2 * i] = hex_digits[byte >> 4];
        hex[2 * i + 1] = hex_digits[byte & 0xF];
    }
    m.setString(16, hex) catch return error.CertParseFailed;
    return m;
}

/// Square-and-multiply modular exponentiation: result = base^exp mod mod.
/// All operands non-negative; exp consumed LSB-first.
fn powmod(
    gpa: std.mem.Allocator,
    result: *std.math.big.int.Managed,
    base: *const std.math.big.int.Managed,
    exp: *const std.math.big.int.Managed,
    mod: *const std.math.big.int.Managed,
) SigError!void {
    const Big = std.math.big.int.Managed;
    var r = Big.init(gpa) catch return error.OutOfMemory;
    defer r.deinit();
    r.set(1) catch return error.OutOfMemory;
    var q = Big.init(gpa) catch return error.OutOfMemory;
    defer q.deinit();
    var rem = Big.init(gpa) catch return error.OutOfMemory;
    defer rem.deinit();
    Big.divTrunc(&q, &rem, base, mod) catch return error.OutOfMemory;
    var bb = rem.clone() catch return error.OutOfMemory;
    defer bb.deinit();
    var e = exp.clone() catch return error.OutOfMemory;
    defer e.deinit();
    var tmp = Big.init(gpa) catch return error.OutOfMemory;
    defer tmp.deinit();
    var q2 = Big.init(gpa) catch return error.OutOfMemory;
    defer q2.deinit();
    var r2 = Big.init(gpa) catch return error.OutOfMemory;
    defer r2.deinit();
    const nbits = e.bitCountAbs();
    var i: usize = 0;
    while (i < nbits) : (i += 1) {
        if (e.isOdd()) {
            tmp.mul(&r, &bb) catch return error.OutOfMemory;
            Big.divTrunc(&q2, &r2, &tmp, mod) catch return error.OutOfMemory;
            const nr = r2.clone() catch return error.OutOfMemory;
            r.deinit();
            r = nr;
        }
        Big.shiftRight(&e, &e, 1) catch return error.OutOfMemory;
        if (i + 1 < nbits) {
            tmp.mul(&bb, &bb) catch return error.OutOfMemory;
            Big.divTrunc(&q2, &r2, &tmp, mod) catch return error.OutOfMemory;
            const nb = r2.clone() catch return error.OutOfMemory;
            bb.deinit();
            bb = nb;
        }
    }
    const nr = r.clone() catch return error.OutOfMemory;
    result.deinit();
    result.* = nr;
}

/// FIPS 186-4 DSA verify (SHA-256 digest truncated to q bits) over
/// signed_data, given Dss-Parms (p,q,g), public y, and DER (r,s).
fn verifyDsa(
    gpa: std.mem.Allocator,
    signed_data: []const u8,
    p_bytes: []const u8,
    q_bytes: []const u8,
    g_bytes: []const u8,
    y_bytes: []const u8,
    der_sig: []const u8,
) SigError!void {
    const Big = std.math.big.int.Managed;
    const key_ok = switch (stripLeadingZeros(p_bytes).len * 8) {
        1024 => stripLeadingZeros(q_bytes).len * 8 == 160,
        2048 => blk: {
            const qb = stripLeadingZeros(q_bytes).len * 8;
            break :blk qb == 224 or qb == 256;
        },
        3072 => stripLeadingZeros(q_bytes).len * 8 == 256,
        else => false,
    };
    if (!key_ok) return error.UnsupportedAlgorithm;
    var p = try managedFromBytes(gpa, p_bytes);
    defer p.deinit();
    var q = try managedFromBytes(gpa, q_bytes);
    defer q.deinit();
    var g = try managedFromBytes(gpa, g_bytes);
    defer g.deinit();
    var y = try managedFromBytes(gpa, y_bytes);
    defer y.deinit();
    const der = Cert.der;
    const seq = der.Element.parse(der_sig, 0) catch return error.SignerFailed;
    if (seq.identifier.tag != .sequence) return error.SignerFailed;
    const r_elem = der.Element.parse(der_sig, seq.slice.start) catch return error.SignerFailed;
    if (r_elem.identifier.tag != .integer) return error.SignerFailed;
    const s_elem = der.Element.parse(der_sig, r_elem.slice.end) catch return error.SignerFailed;
    if (s_elem.identifier.tag != .integer) return error.SignerFailed;
    if (s_elem.slice.end != der_sig.len) return error.SignerFailed;
    var r = try managedFromBytes(gpa, der_sig[r_elem.slice.start..r_elem.slice.end]);
    defer r.deinit();
    var s = try managedFromBytes(gpa, der_sig[s_elem.slice.start..s_elem.slice.end]);
    defer s.deinit();
    var zero = Big.init(gpa) catch return error.OutOfMemory;
    defer zero.deinit();
    if (Big.order(r, zero) != .gt or Big.order(r, q) != .lt) return error.SignerFailed;
    if (Big.order(s, zero) != .gt or Big.order(s, q) != .lt) return error.SignerFailed;
    var digest: [32]u8 = undefined;
    std.crypto.hash.sha2.Sha256.hash(signed_data, &digest, .{});
    const qbits: usize = q.bitCountAbs();
    const nbytes: usize = @min(digest.len, (qbits + 7) / 8);
    const excess: usize = nbytes * 8 - @min(qbits, nbytes * 8);
    var zbytes: [32]u8 = undefined;
    @memcpy(zbytes[0..nbytes], digest[0..nbytes]);
    if (excess > 0 and nbytes > 0) zbytes[nbytes - 1] >>= @intCast(excess);
    var z = try managedFromBytes(gpa, zbytes[0..nbytes]);
    defer z.deinit();
    var two = Big.init(gpa) catch return error.OutOfMemory;
    defer two.deinit();
    two.set(2) catch return error.OutOfMemory;
    var qm2 = Big.init(gpa) catch return error.OutOfMemory;
    defer qm2.deinit();
    qm2.sub(&q, &two) catch return error.OutOfMemory;
    var w = Big.init(gpa) catch return error.OutOfMemory;
    defer w.deinit();
    try powmod(gpa, &w, &s, &qm2, &q);
    var ua = Big.init(gpa) catch return error.OutOfMemory;
    defer ua.deinit();
    ua.mul(&z, &w) catch return error.OutOfMemory;
    var qq = Big.init(gpa) catch return error.OutOfMemory;
    defer qq.deinit();
    var uam = Big.init(gpa) catch return error.OutOfMemory;
    defer uam.deinit();
    Big.divTrunc(&qq, &uam, &ua, &q) catch return error.OutOfMemory;
    var ub = Big.init(gpa) catch return error.OutOfMemory;
    defer ub.deinit();
    ub.mul(&r, &w) catch return error.OutOfMemory;
    var ubm = Big.init(gpa) catch return error.OutOfMemory;
    defer ubm.deinit();
    Big.divTrunc(&qq, &ubm, &ub, &q) catch return error.OutOfMemory;
    var gua = Big.init(gpa) catch return error.OutOfMemory;
    defer gua.deinit();
    try powmod(gpa, &gua, &g, &uam, &p);
    var yub = Big.init(gpa) catch return error.OutOfMemory;
    defer yub.deinit();
    try powmod(gpa, &yub, &y, &ubm, &p);
    var prod = Big.init(gpa) catch return error.OutOfMemory;
    defer prod.deinit();
    prod.mul(&gua, &yub) catch return error.OutOfMemory;
    var qqp = Big.init(gpa) catch return error.OutOfMemory;
    defer qqp.deinit();
    var modp = Big.init(gpa) catch return error.OutOfMemory;
    defer modp.deinit();
    Big.divTrunc(&qqp, &modp, &prod, &p) catch return error.OutOfMemory;
    var v = Big.init(gpa) catch return error.OutOfMemory;
    defer v.deinit();
    Big.divTrunc(&qqp, &v, &modp, &q) catch return error.OutOfMemory;
    if (!v.eql(r)) return error.SignerFailed;
}

/// ECDSA DER-signature verify over signed_data with a SEC1 public key,
/// collapsing the 4 P-256/P-384 x SHA-256/SHA-512 branches. Errors map to
/// SignerFailed like the branches it replaces.
fn ecdsaVerify(comptime Curve: type, comptime Hash: type, sig: []const u8, signed_data: []const u8, key: []const u8) SigError!void {
    const E = std.crypto.sign.ecdsa.Ecdsa(Curve, Hash);
    const s = E.Signature.fromDer(sig) catch return error.SignerFailed;
    const pk = E.PublicKey.fromSec1(key) catch return error.SignerFailed;
    s.verify(signed_data, pk) catch return error.SignerFailed;
}

/// Verify one (alg, signature) pair over signed_data with the SPKI public key.
/// Supported: RSA PKCS1v15 + PSS (1024–4096 bit), ECDSA P-256/P-384 x
/// SHA-256/SHA-512, DSA (1024/2048/3072-bit, FIPS 186-4). P-521 and
/// RSA >4096 are explicit UnsupportedAlgorithm rejections.
fn verifySignature(
    gpa: std.mem.Allocator,
    alg: u32,
    sig: []const u8,
    signed_data: []const u8,
    spki: []const u8,
) SigError!void {
    // Unknown signature algorithm IDs are tolerated by the caller (which
    // skips the entry); anything reaching the key branches is a known ID.
    switch (alg) {
        sig_rsa_pss_sha256,
        sig_rsa_pss_sha512,
        sig_rsa_pkcs1_sha256,
        sig_rsa_pkcs1_sha512,
        sig_ecdsa_sha256,
        sig_ecdsa_sha512,
        sig_dsa_sha256,
        => {},
        else => return error.UnknownAlgorithm,
    }
    const der = Cert.der;
    const outer = der.Element.parse(spki, 0) catch return error.CertParseFailed;
    if (outer.identifier.tag != .sequence) return error.CertParseFailed;
    const algo_seq = der.Element.parse(spki, outer.slice.start) catch return error.CertParseFailed;
    const algo_oid_elem = der.Element.parse(spki, algo_seq.slice.start) catch return error.CertParseFailed;
    if (algo_oid_elem.identifier.tag != .object_identifier) return error.CertParseFailed;
    const oid = spki[algo_oid_elem.slice.start..algo_oid_elem.slice.end];
    const key_bits = der.Element.parse(spki, algo_seq.slice.end) catch return error.CertParseFailed;
    if (key_bits.identifier.tag != .bitstring) return error.CertParseFailed;
    // BIT STRING content: first byte = unused-bits count (must be 0), rest = key.
    const bits = spki[key_bits.slice.start..key_bits.slice.end];
    if (bits.len < 1 or bits[0] != 0) return error.CertParseFailed;
    const key = bits[1..];

    const rsa_oid = [_]u8{ 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01 };
    const ec_oid = [_]u8{ 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01 };
    const dsa_oid = [_]u8{ 0x2A, 0x86, 0x48, 0xCE, 0x38, 0x04, 0x01 };
    const p256_oid = [_]u8{ 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07 };
    const p384_oid = [_]u8{ 0x2B, 0x81, 0x04, 0x00, 0x22 };

    if (std.mem.eql(u8, oid, &rsa_oid)) {
        const comps = Cert.rsa.PublicKey.parseDer(key) catch return error.CertParseFailed;
        if (comps.exponent.len > comps.modulus.len) return error.CertParseFailed;
        if (sig.len != comps.modulus.len) return error.SignerFailed;
        switch (comps.modulus.len) {
            inline 128, 256, 384, 512 => |modulus_len| {
                const pk = Cert.rsa.PublicKey.fromBytes(comps.exponent, comps.modulus) catch return error.SignerFailed;
                const s: *const [modulus_len]u8 = sig[0..modulus_len];
                switch (alg) {
                    sig_rsa_pkcs1_sha256 => Cert.rsa.PKCS1v1_5Signature.verify(modulus_len, s, signed_data, pk, std.crypto.hash.sha2.Sha256) catch return error.SignerFailed,
                    sig_rsa_pkcs1_sha512 => Cert.rsa.PKCS1v1_5Signature.verify(modulus_len, s, signed_data, pk, std.crypto.hash.sha2.Sha512) catch return error.SignerFailed,
                    sig_rsa_pss_sha256 => Cert.rsa.PSSSignature.verify(modulus_len, s, signed_data, pk, std.crypto.hash.sha2.Sha256) catch return error.SignerFailed,
                    sig_rsa_pss_sha512 => Cert.rsa.PSSSignature.verify(modulus_len, s, signed_data, pk, std.crypto.hash.sha2.Sha512) catch return error.SignerFailed,
                    else => return error.SignerFailed,
                }
                return;
            },
            else => return error.UnsupportedAlgorithm,
        }
    }
    if (std.mem.eql(u8, oid, &ec_oid)) {
        const params = der.Element.parse(spki, algo_oid_elem.slice.end) catch return error.CertParseFailed;
        if (params.identifier.tag != .object_identifier) return error.CertParseFailed;
        const curve_oid = spki[params.slice.start..params.slice.end];
        const is_p256 = std.mem.eql(u8, curve_oid, &p256_oid);
        const is_p384 = std.mem.eql(u8, curve_oid, &p384_oid);
        if (!is_p256 and !is_p384) return error.UnsupportedAlgorithm;
        switch (alg) {
            sig_ecdsa_sha256 => {
                if (is_p256) {
                    try ecdsaVerify(std.crypto.ecc.P256, std.crypto.hash.sha2.Sha256, sig, signed_data, key);
                } else {
                    try ecdsaVerify(std.crypto.ecc.P384, std.crypto.hash.sha2.Sha256, sig, signed_data, key);
                }
                return;
            },
            sig_ecdsa_sha512 => {
                if (is_p256) {
                    try ecdsaVerify(std.crypto.ecc.P256, std.crypto.hash.sha2.Sha512, sig, signed_data, key);
                } else {
                    try ecdsaVerify(std.crypto.ecc.P384, std.crypto.hash.sha2.Sha512, sig, signed_data, key);
                }
                return;
            },
            else => return error.SignerFailed,
        }
    }
    if (std.mem.eql(u8, oid, &dsa_oid)) {
        if (alg != sig_dsa_sha256) return error.SignerFailed;
        const params_seq = der.Element.parse(spki, algo_oid_elem.slice.end) catch return error.CertParseFailed;
        if (params_seq.identifier.tag != .sequence) return error.CertParseFailed;
        const p_elem = der.Element.parse(spki, params_seq.slice.start) catch return error.CertParseFailed;
        if (p_elem.identifier.tag != .integer) return error.CertParseFailed;
        const q_elem = der.Element.parse(spki, p_elem.slice.end) catch return error.CertParseFailed;
        if (q_elem.identifier.tag != .integer) return error.CertParseFailed;
        const g_elem = der.Element.parse(spki, q_elem.slice.end) catch return error.CertParseFailed;
        if (g_elem.identifier.tag != .integer) return error.CertParseFailed;
        if (g_elem.slice.end != algo_seq.slice.end) return error.CertParseFailed;
        if (key.len < 1) return error.CertParseFailed;
        const y_outer = der.Element.parse(key, 0) catch return error.CertParseFailed;
        if (y_outer.identifier.tag != .integer) return error.CertParseFailed;
        if (y_outer.slice.end != key.len) return error.CertParseFailed;
        try verifyDsa(
            gpa,
            signed_data,
            spki[p_elem.slice.start..p_elem.slice.end],
            spki[q_elem.slice.start..q_elem.slice.end],
            spki[g_elem.slice.start..g_elem.slice.end],
            key[y_outer.slice.start..y_outer.slice.end],
            sig,
        );
        return;
    }
    return error.UnsupportedAlgorithm;
}
/// Strict DER check: every length in the buffer must use minimal encoding
/// (short form <128, else fewest bytes, no leading zeros, no 0x80).
/// std's DER parser accepts non-minimal long forms, but Android's X.509
/// parser rejects them (V2_SIG_MALFORMED_CERTIFICATE), so the leaf cert
/// gets this extra pass. Recurses into constructed types.
fn checkStrictTlv(der: []const u8, at: usize) SigError!usize {
    if (at + 2 > der.len) return error.CertParseFailed;
    const size_byte = der[at + 1];
    var hdr_len: usize = 2;
    var len: usize = 0;
    if (size_byte < 0x80) {
        len = size_byte;
    } else {
        const n: usize = size_byte & 0x7F;
        if (n == 0 or n > 4) return error.CertParseFailed;
        if (at + 2 + n > der.len) return error.CertParseFailed;
        if (der[at + 2] == 0) return error.CertParseFailed;
        for (der[at + 2 ..][0..n]) |b| len = (len << 8) | b;
        hdr_len += n;
        const min: usize = if (n == 1) 128 else (@as(usize, 1) << @intCast(8 * (n - 1)));
        if (len < min) return error.CertParseFailed;
    }
    const val_end = std.math.add(usize, at + hdr_len, len) catch return error.CertParseFailed;
    if (val_end > der.len) return error.CertParseFailed;
    if (der[at] & 0x20 != 0) {
        var pos = at + hdr_len;
        while (pos < val_end) pos = try checkStrictTlv(der, pos);
        if (pos != val_end) return error.CertParseFailed;
    }
    return val_end;
}

/// Extract the raw SubjectPublicKeyInfo DER (tag+length+value) from a
/// certificate: Certificate -> TBSCertificate -> skip serial, sig-alg,
/// issuer, validity, subject (version is [0] EXPLICIT optional) -> SPKI.
fn extractSpki(leaf_der: []const u8) SigError![]const u8 {
    const der = Cert.der;
    const cert = der.Element.parse(leaf_der, 0) catch return error.CertParseFailed;
    const tbs = der.Element.parse(leaf_der, cert.slice.start) catch return error.CertParseFailed;
    var pos = tbs.slice.start;
    // Optional [0] EXPLICIT version (context tag 0, constructed = 0xA0).
    if (leaf_der[pos] == 0xA0) {
        const v = der.Element.parse(leaf_der, pos) catch return error.CertParseFailed;
        pos = v.slice.end;
    }
    // Skip serial, signature-alg, issuer, validity, subject.
    for (0..5) |_| {
        const e = der.Element.parse(leaf_der, pos) catch return error.CertParseFailed;
        pos = e.slice.end;
    }
    const spki_start = pos;
    const spki = der.Element.parse(leaf_der, pos) catch return error.CertParseFailed;
    if (spki.identifier.tag != .sequence) return error.CertParseFailed;
    return leaf_der[spki_start..spki.slice.end];
}

const Signer = struct {
    signed_data: []const u8,
    sig_algs: []u32,
    sigs: [][]const u8,
    digest_algs: []u32,
    digests: [][]const u8,
    certs: [][]const u8,
    public_key: []const u8,
};

fn parseSigner(gpa: std.mem.Allocator, data: []const u8) SigError!Signer {
    var pos: usize = 0;
    const signed_data = try readLP(data, &pos);
    const sigs_raw = try readLP(data, &pos);
    const public_key = try readLP(data, &pos);
    if (pos != data.len) return error.BadLengthPrefix;

    // Signatures, digests, certs are all back-to-back LPs (no counts).
    var sig_algs: std.ArrayListUnmanaged(u32) = .empty;
    errdefer sig_algs.deinit(gpa);
    var sigs: std.ArrayListUnmanaged([]const u8) = .empty;
    errdefer sigs.deinit(gpa);
    {
        var sp: usize = 0;
        while (sp < sigs_raw.len) {
            const entry = try readLP(sigs_raw, &sp);
            var ep: usize = 0;
            const alg = try readInt(u32, entry, ep);
            ep += 4;
            const sig = try readLP(entry, &ep);
            if (ep != entry.len) return error.BadLengthPrefix;
            try sig_algs.append(gpa, alg);
            try sigs.append(gpa, sig);
            if (sig_algs.items.len > 16) return error.SignerFailed;
        }
        if (sig_algs.items.len == 0) return error.SignerFailed;
    }

    // Signed data: LP(digests) || LP(certs) || LP(attrs).
    var digest_algs: std.ArrayListUnmanaged(u32) = .empty;
    errdefer digest_algs.deinit(gpa);
    var digests: std.ArrayListUnmanaged([]const u8) = .empty;
    errdefer digests.deinit(gpa);
    var certs: std.ArrayListUnmanaged([]const u8) = .empty;
    errdefer certs.deinit(gpa);
    {
        var dp: usize = 0;
        const digests_raw = try readLP(signed_data, &dp);
        const certs_raw = try readLP(signed_data, &dp);
        _ = try readLP(signed_data, &dp); // attrs (additional attributes)
        // Trailing bytes after attrs are ignored (forward-compat; observed
        // 4 trailing bytes in real apksigner output). Signature verification
        // covers the raw signed_data, so this weakens nothing.
        var gp: usize = 0;
        while (gp < digests_raw.len) {
            const entry = try readLP(digests_raw, &gp);
            var ep: usize = 0;
            const alg = try readInt(u32, entry, ep);
            ep += 4;
            const dg = try readLP(entry, &ep);
            if (ep != entry.len) return error.BadLengthPrefix;
            try digest_algs.append(gpa, alg);
            try digests.append(gpa, dg);
            if (digest_algs.items.len > 16) return error.DigestOrderMismatch;
        }
        var cp: usize = 0;
        while (cp < certs_raw.len) {
            try certs.append(gpa, try readLP(certs_raw, &cp));
            if (certs.items.len > 16) return error.NoCertificates;
        }
        if (certs.items.len == 0) return error.NoCertificates;
    }
    return .{
        .signed_data = signed_data,
        .sig_algs = try sig_algs.toOwnedSlice(gpa),
        .sigs = try sigs.toOwnedSlice(gpa),
        .digest_algs = try digest_algs.toOwnedSlice(gpa),
        .digests = try digests.toOwnedSlice(gpa),
        .certs = try certs.toOwnedSlice(gpa),
        .public_key = public_key,
    };
}

fn freeSigner(gpa: std.mem.Allocator, s: *Signer) void {
    gpa.free(s.sig_algs);
    gpa.free(s.sigs);
    gpa.free(s.digest_algs);
    gpa.free(s.digests);
    gpa.free(s.certs);
}

/// Verify a v2 block. Returns the SHA-256 fingerprints (lowercase hex) of
/// ALL signers' leaf certs, sorted. Per upstream Android semantics every
/// signer must verify; install pins the whole set.
pub fn verifyV2(
    gpa: std.mem.Allocator,
    apk: []const u8,
    v2block: []const u8,
    block: SigningBlock,
    eocd: Eocd,
) SigError![][]u8 {
    // v2 value = LP(signers blob); blob = back-to-back LP(signer).
    // Every signer is verified (upstream semantics); the returned set is
    // the identity a registry pins for upgrade continuity.
    var pos: usize = 0;
    const blob = try readLP(v2block, &pos);
    if (pos != v2block.len) return error.BadLengthPrefix;
    var fps: std.ArrayListUnmanaged([]u8) = .empty;
    errdefer {
        for (fps.items) |f| gpa.free(f);
        fps.deinit(gpa);
    }
    var bpos: usize = 0;
    while (bpos < blob.len) {
        const signer_raw = try readLP(blob, &bpos);
        const fp = try verifyOneSigner(gpa, apk, signer_raw, block, eocd);
        try fps.append(gpa, fp);
        if (fps.items.len > 16) return error.SignerFailed;
    }
    if (fps.items.len == 0) return error.SignerFailed;
    // Deterministic order: sort + dedup (two signers may share a cert).
    std.mem.sort([]u8, fps.items, {}, struct {
        fn lt(_: void, a: []u8, b: []u8) bool {
            return std.mem.order(u8, a, b) == .lt;
        }
    }.lt);
    var w: usize = 0;
    for (fps.items, 0..) |f, i| {
        if (i == 0 or !std.mem.eql(u8, f, fps.items[w - 1])) {
            fps.items[w] = f;
            w += 1;
        } else {
            gpa.free(f);
        }
    }
    fps.items.len = w;
    return fps.toOwnedSlice(gpa) catch return error.OutOfMemory;
}

/// Verify one signer blob (the pre-M6e verifyV2 body, factored out so the
/// multi-signer loop above can call it per signer).
fn verifyOneSigner(
    gpa: std.mem.Allocator,
    apk: []const u8,
    signer_raw: []const u8,
    block: SigningBlock,
    eocd: Eocd,
) SigError![]u8 {
    var signer = try parseSigner(gpa, signer_raw);
    defer freeSigner(gpa, &signer);

    // 1. Verify signatures over signed_data. Per entry:
    //  - 0x04xx (fs-verity variants) skipped: digests are Merkle-tree
    //    roots, not content digests. At least one non-verity, non-unknown
    //    signature must verify (guaranteed below via verified == 0).
    //  - UnknownAlgorithm/UnsupportedAlgorithm: tolerated (upstream warns
    //    V2_SIG_UNKNOWN_SIG_ALGORITHM and continues); the entry's digest
    //    is still anti-strip checked structurally but never re-hashed.
    //  - any other failure (bad sig, bad key, parse error): fail closed.
    var verified: usize = 0;
    var verifiable = std.mem.zeroes([16]bool);
    var saw_unsupported = false;
    var saw_other_skip = false;
    for (signer.sig_algs, 0..) |alg, idx| {
        const sig = signer.sigs[idx];
        const i = idx;
        if (alg & 0xFF00 == 0x0400) {
            saw_other_skip = true;
            continue;
        }
        verifySignature(gpa, alg, sig, signer.signed_data, signer.public_key) catch |err| switch (err) {
            error.UnsupportedAlgorithm => {
                saw_unsupported = true;
                continue;
            },
            error.UnknownAlgorithm => {
                saw_other_skip = true;
                continue;
            },
            else => return err,
        };
        verified += 1;
        if (i < verifiable.len) verifiable[i] = true;
    }
    if (verified == 0) {
        // Nothing verifiable (e.g. RSA-8192 or ECDSA P-521 only): report
        // UnsupportedAlgorithm rather than a generic SignerFailed so
        // callers can distinguish "weak/unknown crypto" from "forgery".
        if (saw_unsupported and !saw_other_skip) return error.UnsupportedAlgorithm;
        return error.SignerFailed;
    }
    // 2. Anti-strip: digest count/order must match signature count/order
    // (upstream compares the FULL records, unknowns included).
    if (signer.digest_algs.len != signer.sig_algs.len) return error.DigestOrderMismatch;
    for (signer.digest_algs, signer.sig_algs) |da, sa| {
        if (da != sa) return error.DigestOrderMismatch;
    }
    // 3. Recompute content digests and compare over the verified subset;
    // unknown/unsupported/verity entries are skipped (never-verifyable).
    for (signer.digest_algs, 0..) |alg, idx| {
        const claimed = signer.digests[idx];
        const i = idx;
        if (alg & 0xFF00 == 0x0400) continue;
        if (i >= verifiable.len or !verifiable[i]) continue;
        const h = try contentHash(alg);
        const actual = try contentDigest(gpa, apk, block, eocd, h);
        defer gpa.free(actual);
        if (!std.mem.eql(u8, actual, claimed)) return error.DigestMismatch;
    }
    // 4. Leaf cert: parse (format check), require full SPKI DER ==
    // signer public key. parsed.pubKey() is only the key bits; the
    // comparison needs the whole SubjectPublicKeyInfo, extracted below.
    const leaf_der = signer.certs[0];
    // Strict DER minimal-length pass: Android's X.509 parser rejects
    // non-minimal long forms (e.g. 0x820081 for 129) that std accepts.
    if (try checkStrictTlv(leaf_der, 0) != leaf_der.len) return error.CertParseFailed;
    _ = Cert.parse(.{ .buffer = leaf_der, .index = 0 }) catch {
        // std's Certificate tables know no DSA OIDs; for DSA signers the
        // structural walk in extractSpki is the format check.
        const dsa_oid = [_]u8{ 0x2A, 0x86, 0x48, 0xCE, 0x38, 0x04, 0x01 };
        var is_dsa = false;
        for (signer.sig_algs) |sa| {
            if (sa == sig_dsa_sha256) {
                const der = Cert.der;
                const outer = der.Element.parse(signer.public_key, 0) catch return error.CertParseFailed;
                const aseq = der.Element.parse(signer.public_key, outer.slice.start) catch return error.CertParseFailed;
                const aoid = der.Element.parse(signer.public_key, aseq.slice.start) catch return error.CertParseFailed;
                is_dsa = std.mem.eql(u8, signer.public_key[aoid.slice.start..aoid.slice.end], &dsa_oid);
                break;
            }
        }
        if (!is_dsa) return error.CertParseFailed;
    };
    const leaf_spki = extractSpki(leaf_der) catch return error.CertParseFailed;
    if (!std.mem.eql(u8, leaf_spki, signer.public_key)) {
        return error.PublicKeyMismatch;
    }
    // 5. Fingerprint = SHA-256(leaf DER), lowercase hex.
    var fp: [32]u8 = undefined;
    std.crypto.hash.sha2.Sha256.hash(leaf_der, &fp, .{});
    const hex = std.fmt.bytesToHex(fp, .lower);
    return gpa.dupe(u8, &hex) catch return error.OutOfMemory;
}

/// Top-level: verify an APK file's v2 signature. Returns leaf fingerprint.
pub fn verifyApk(io: std.Io, gpa: std.mem.Allocator, apk_path: []const u8) SigError![][]u8 {
    const data = std.Io.Dir.cwd().readFileAlloc(io, apk_path, gpa, .limited(256 * 1024 * 1024)) catch return error.Truncated;
    defer gpa.free(data);
    return verifyBytes(gpa, data);
}

// Pure-bytes verify pipeline: EOCD -> signing block -> v2 pair -> verifyV2.
fn verifyBytes(gpa: std.mem.Allocator, bytes: []const u8) SigError![][]u8 {
    const eocd = try findEocd(bytes);
    const block = try locateBlock(bytes, eocd);
    const maybe_v2 = try findPair(bytes, block, v2_id);
    const v2 = maybe_v2 orelse return error.NoV2Block;
    return verifyV2(gpa, bytes, v2, block, eocd);
}
