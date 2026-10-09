const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    const registry_mod = b.createModule(.{
        .root_source_file = b.path("src/registry.zig"),
        .target = target,
        .optimize = optimize,
    });
    const install_mod = b.createModule(.{
        .root_source_file = b.path("src/install.zig"),
        .target = target,
        .optimize = optimize,
    });
    install_mod.addImport("registry", registry_mod);
    const dex_mod = b.createModule(.{
        .root_source_file = b.path("src/dex.zig"),
        .target = target,
        .optimize = optimize,
    });
    const apksig_mod = b.createModule(.{
        .root_source_file = b.path("src/apksig.zig"),
        .target = target,
        .optimize = optimize,
    });
    install_mod.addImport("apksig", apksig_mod);
    const translate_mod = b.createModule(.{
        .root_source_file = b.path("src/translate.zig"),
        .target = target,
        .optimize = optimize,
    });
    translate_mod.addImport("dex", dex_mod);
    const launch_mod = b.createModule(.{
        .root_source_file = b.path("src/launch.zig"),
        .target = target,
        .optimize = optimize,
    });
    launch_mod.addImport("dex", dex_mod);
    launch_mod.addImport("translate", translate_mod);
    launch_mod.addImport("registry", registry_mod);
    launch_mod.addImport("install", install_mod);
    const helper_mod = b.createModule(.{
        .root_source_file = b.path("src/uid_helper.zig"),
        .target = target,
        .optimize = optimize,
    });
    install_mod.addImport("uid_helper", helper_mod);
    helper_mod.addImport("registry", registry_mod);
    const isakai_mod = b.createModule(.{
        .root_source_file = b.path("src/main.zig"),
        .target = target,
        .optimize = optimize,
    });
    isakai_mod.addImport("install", install_mod);
    isakai_mod.addImport("registry", registry_mod);
    isakai_mod.addImport("launch", launch_mod);
    isakai_mod.addImport("uid_helper", helper_mod);
    const exe = b.addExecutable(.{
        .name = "isakai",
        .root_module = isakai_mod,
    });
    b.installArtifact(exe);
    const window_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/window.zig"),
        .target = target,
        .optimize = optimize,
    });
    const xkb_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/xkb.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    xkb_mod.linkSystemLibrary("xkbcommon", .{});
    window_mod.addImport("xkb", xkb_mod);
    const jni_mod = b.createModule(.{
        .root_source_file = b.path("src/bridge/jni.zig"),
        .target = target,
        .optimize = optimize,
        .link_libc = true,
    });
    jni_mod.addImport("window", window_mod);
    const jni_lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "isakai_jni",
        .root_module = jni_mod,
    });
    const jni_install = b.addInstallFile(jni_lib.getEmittedBin(), "framework/lib/libisakai_jni.so");
    b.getInstallStep().dependOn(&jni_install.step);
    // Framework: compile android.* + IsakaiMain with the host javac.
    const fw = b.addSystemCommand(&.{
        "javac", "--release", "17", "-d",
    });
    const fw_out = fw.addOutputDirectoryArg("framework");
    fw.addFileArg(b.path("framework/android/app/Application.java"));
    fw.addFileArg(b.path("framework/android/app/Activity.java"));
    fw.addFileArg(b.path("framework/android/view/View.java"));
    fw.addFileArg(b.path("framework/android/view/ViewGroup.java"));
    fw.addFileArg(b.path("framework/android/view/KeyEvent.java"));
    fw.addFileArg(b.path("framework/android/view/MotionEvent.java"));
    fw.addFileArg(b.path("framework/android/text/Editable.java"));
    fw.addFileArg(b.path("framework/android/widget/TextView.java"));
    fw.addFileArg(b.path("framework/android/widget/EditText.java"));
    fw.addFileArg(b.path("framework/android/widget/Button.java"));
    fw.addFileArg(b.path("framework/android/graphics/Canvas.java"));
    fw.addFileArg(b.path("framework/android/graphics/Color.java"));
    fw.addFileArg(b.path("framework/android/graphics/BitmapFont.java"));
    fw.addFileArg(b.path("framework/org/isakai/bridge/IsakaiWindow.java"));
    fw.addFileArg(b.path("framework/org/isakai/runtime/IsakaiMain.java"));
    const fw_install = b.addInstallDirectory(.{
        .source_dir = fw_out,
        .install_dir = .prefix,
        .install_subdir = "framework",
    });
    b.getInstallStep().dependOn(&fw_install.step);
    // Polkit policy: staged for packagers to copy into
    // share/polkit-1/actions/. Installed under the prefix so `zig build`
    // stays relocatable; the distro install must place the .policy next to
    // the other actions with exec.path=/usr/bin/isakai (see the policy).
    const policy_install = b.addInstallFile(b.path("polkit/id.isakai.allocate-uid.policy"), "share/polkit-1/actions/id.isakai.allocate-uid.policy");
    b.getInstallStep().dependOn(&policy_install.step);
    const fixture_mod = b.createModule(.{
        .root_source_file = b.path("tools/fixture-apk.zig"),
        .target = target,
        .optimize = optimize,
    });
    const fixture_exe = b.addExecutable(.{
        .name = "fixture-apk",
        .root_module = fixture_mod,
    });
    b.installArtifact(fixture_exe);

    const probe_mod = b.createModule(.{
        .root_source_file = b.path("tools/inspect-test.zig"),
        .target = target,
        .optimize = optimize,
    });
    probe_mod.addImport("install", install_mod);
    const probe_exe = b.addExecutable(.{ .name = "inspect-test", .root_module = probe_mod });
    b.installArtifact(probe_exe);
    const tprobe_mod = b.createModule(.{
        .root_source_file = b.path("tools/translate-test.zig"),
        .target = target,
        .optimize = optimize,
    });
    tprobe_mod.addImport("dex", dex_mod);
    tprobe_mod.addImport("translate", translate_mod);
    const tprobe_exe = b.addExecutable(.{ .name = "translate-test", .root_module = tprobe_mod });
    b.installArtifact(tprobe_exe);
    const vprobe_mod = b.createModule(.{
        .root_source_file = b.path("tools/verify-test.zig"),
        .target = target,
        .optimize = optimize,
    });
    vprobe_mod.addImport("apksig", apksig_mod);
    const vprobe_exe = b.addExecutable(.{ .name = "verify-test", .root_module = vprobe_mod });
    b.installArtifact(vprobe_exe);
    // `zig build run` (no args pass-through on 0.17; use zig-out/bin/isakai).
    const run_step = b.step("run", "Run the isakai CLI");
    const run_cmd = b.addRunArtifact(exe);
    run_step.dependOn(&run_cmd.step);

    // `zig build test` (all unit tests; xkb runs on its hermetic fake
    // backend; jni needs a live JVM so it stays out of this loop).
    const test_step = b.step("test", "Run unit tests");
    for ([_]*std.Build.Module{ isakai_mod, registry_mod, install_mod, dex_mod, translate_mod, launch_mod, apksig_mod, helper_mod, window_mod, xkb_mod }) |mod| {
        const unit_tests = b.addTest(.{ .root_module = mod });
        const run_tests = b.addRunArtifact(unit_tests);
        test_step.dependOn(&run_tests.step);
    }
}
