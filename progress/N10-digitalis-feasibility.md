---
id: "N10"
status: "pending"
phase: "EXPERIMENT"
priority: "P2"
size: "L"
updated: "2026-10-14"
owner: "unassigned"
next: "no action until scheduled; start per acceptance criteria when the phase opens"
---

# N10: Time-box Digitalis ARM64-to-x86_64 mixed-ISA feasibility

## Dependencies

- [N4](N4-upstream-pins.md)
- [N8](N8-qemu-arm64-guest.md)

## Code and documentation

- [progress/N10.md](../progress/N10-digitalis-feasibility.md)
- [src/launch.zig](../src/launch.zig)
- [src/native/bionic_shim.zig](../src/native/bionic_shim.zig)
- [src/bridge/jni.zig](../src/bridge/jni.zig)
- [build.zig](../build.zig)

## Implementation

1. Pin Digitalis entry manifest and all binary_translation/proxy dependencies; first reproduce its intended upstream application behavior using existing artifacts.
2. Inventory Android-property/fdsan/linker-namespace/JNI Native Bridge assumptions versus chosen Isakai runtime.
3. Test ART-compatible loading separately from any proposed HotSpot integration; prove guest-host callbacks/threads/GC/TLS/signals.
4. Compare actual app action and speed to whole-guest QEMU before deciding to integrate.

## Acceptance

- An ARM64-only NDK APK completes static/registered JNI, Java callbacks, attached threads and a meaningful visible action in the intended Isakai runtime.
- No full Android system image/services become mandatory, and imported code/guest libraries have redistributable terms.
- Measured benefit exceeds integration and maintenance cost for the selected corpus.

## Stop condition

Stop adoption for unknown-signature callbacks needed by targets, mandatory Android system services, extensive private ABI requirements or missing redistribution rights; keep QEMU and native ARM64 routes.

## Effort estimate

Planning estimate: 1–2 engineer-weeks feasibility; 4–12+ weeks integration only after a pass.

## Reuse sources

- [https://github.com/DigitalisX64/digitalis/tree/android-latest-release](https://github.com/DigitalisX64/digitalis/tree/android-latest-release)
- [https://github.com/DigitalisX64/manifest/blob/android-latest-release/digitalis.xml](https://github.com/DigitalisX64/manifest/blob/android-latest-release/digitalis.xml)
- [https://github.com/DigitalisX64/platform_frameworks_libs_binary_translation/blob/android-latest-release/native_bridge/native_bridge.cc](https://github.com/DigitalisX64/platform_frameworks_libs_binary_translation/blob/android-latest-release/native_bridge/native_bridge.cc)
- [https://android.googlesource.com/platform/art/+/refs/heads/main/libnativebridge/README.md](https://android.googlesource.com/platform/art/+/refs/heads/main/libnativebridge/README.md)

## Verification evidence

Not recorded.


## Code review

Not recorded.
