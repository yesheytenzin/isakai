// SPDX-License-Identifier: GPL-3.0-or-later

//! Runtime selection policy for launch preparation and execution requests.

const std = @import("std");
const registry = @import("registry");

pub const Backend = enum { jvm_v1 };
pub const DexSupport = enum { translated_jvm_classes };
pub const NativeAbi = enum { none, x86_64 };
pub const JniOwnership = enum { host_jvm_loads_guest_libraries };
pub const EnvironmentPolicy = enum { inherited_without_dbus, fixed_sandbox_v1 };

pub const PreparedArtifacts = struct {
    code_root: []const u8,
    classes: []const u8,
    cache_version: []const u8,
};

pub const RuntimeReads = struct {
    framework: []const u8,
    code_root: []const u8,
};

pub const JvmProfile = struct {
    backend: Backend = .jvm_v1,
    dex: DexSupport = .translated_jvm_classes,
    native_abi: NativeAbi,
    jni: JniOwnership = .host_jvm_loads_guest_libraries,
    environment: EnvironmentPolicy,
    artifacts: PreparedArtifacts,
    reads: RuntimeReads,
};

pub const RuntimeSelection = union(Backend) {
    jvm_v1: JvmProfile,
};

pub const cache_version = "isakai-runtime-cache-v1";

pub fn select(
    io: std.Io,
    gpa: std.mem.Allocator,
    pkg: registry.Package,
    framework: []const u8,
    privileged: bool,
) !RuntimeSelection {
    const host = try registry.Registry.hostArch(gpa);
    defer gpa.free(host);
    if (!std.mem.eql(u8, host, "x86_64")) return error.UnsupportedHostAbi;
    if (pkg.arch.len != 0 and !std.mem.eql(u8, pkg.arch, host)) return error.InstalledHostAbiMismatch;
    const native_abi: NativeAbi = if (pkg.native_abi.len == 0)
        .none
    else if (std.mem.eql(u8, pkg.native_abi, "x86_64"))
        .x86_64
    else
        return error.UnsupportedNativeAbi;
    _ = io;
    const classes = try std.fmt.allocPrint(gpa, "{s}/classes", .{pkg.apk});
    return .{ .jvm_v1 = .{
        .native_abi = native_abi,
        .environment = if (privileged) .fixed_sandbox_v1 else .inherited_without_dbus,
        .artifacts = .{ .code_root = pkg.apk, .classes = classes, .cache_version = cache_version },
        .reads = .{ .framework = framework, .code_root = pkg.apk },
    } };
}

pub fn deinit(selection: *RuntimeSelection, gpa: std.mem.Allocator) void {
    switch (selection.*) {
        .jvm_v1 => |profile| gpa.free(profile.artifacts.classes),
    }
}
