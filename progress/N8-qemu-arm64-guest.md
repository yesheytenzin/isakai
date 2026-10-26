---
id: "N8"
status: "pending"
phase: "FOREIGN_ISA"
priority: "P2"
size: "L"
updated: "2026-10-14"
owner: "unassigned"
next: "no action until scheduled; start per acceptance criteria when the phase opens"
---

# N8: Establish whole-guest QEMU ARM64 application execution on x86_64

## Dependencies

- [N1](N1-evidence-reproducibility.md)
- [N3](N3-trusted-runtime.md)
- [N6](N6-bionic-jni.md)

## Code and documentation

- [src/launch.zig](../src/launch.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [src/confine.zig](../src/confine.zig)
- [src/bridge/jni.zig](../src/bridge/jni.zig)
- [src/bridge/window.zig](../src/bridge/window.zig)
- [src/bridge/audio.zig](../src/bridge/audio.zig)
- [framework/org/isakai/bridge/IsakaiWindow.java](../framework/org/isakai/bridge/IsakaiWindow.java)
- [build.zig](../build.zig)

## Implementation

1. Prepare a matching ARM64 guest JVM/ART, Isakai bridge and dependency sysroot under a pinned trusted manifest.
2. Execute that complete userspace process through qemu-aarch64; keep supervisor/desktop services host-native.
3. Define versioned IPC/shared-buffer ownership for pixels/input/audio/permission requests; never cross JNI/function pointers between ISAs.
4. Audit host-translator syscalls, runtime paths, process signals and child cleanup; retain no Android OS/kernel Binder requirement.

## Acceptance

- An existing ARM64-only NDK application matches native ARM reference behavior including callbacks/threads/signals.
- One real ARM64-only app completes a useful visible task with confined shutdown/restart.
- Report launch/RSS/size/latency and exact unsupported features without claiming general foreign JNI loading.

## Stop condition

If runtime correctness cannot pass the timebox, record the exact missing syscall/runtime contract and leave the backend unavailable; do not import a mandatory full Android image.

## Effort estimate

Planning estimate: 2–4 engineer-weeks ARM64 pilot; ARM32 separate.

## Reuse sources

- [https://www.qemu.org/docs/master/user/main.html](https://www.qemu.org/docs/master/user/main.html)

## Verification evidence

Not recorded.


## Code review

Not recorded.
