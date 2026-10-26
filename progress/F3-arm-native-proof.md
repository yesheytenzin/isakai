---
id: "F3"
status: "deferred"
phase: "DEFERRED"
priority: "P2"
size: "XL"
updated: "2026-10-14"
owner: "unassigned"
next: "no action while deferred; revisit only if the task is un-deferred"
---

# F3: Prove ARM native execution before broad integration

## Dependencies

None.

## Code and documentation

- [src/install.zig](../src/install.zig)
- [src/launch.zig](../src/launch.zig)
- [src/native/bionic_shim.zig](../src/native/bionic_shim.zig)
- [src/confine.zig](../src/confine.zig)

## Acceptance

A real ARM JNI APK installs, loads dependencies, calls Java/native in both directions and completes a visible confined flow; report latency, RSS and distribution size before committing to the backend. Defer ARM32 separately.

## Verification evidence

Not recorded.


## Code review

Not recorded.
