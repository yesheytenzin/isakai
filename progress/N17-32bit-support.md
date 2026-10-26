---
id: "N17"
status: "pending"
phase: "32BIT_POLICY"
priority: "P3"
size: "L"
updated: "2026-10-14"
owner: "unassigned"
next: "no action until scheduled; start per acceptance criteria when the phase opens"
---

# N17: Decide and prove independent x86 and ARMv7 32-bit support

## Dependencies

- [N6](N6-bionic-jni.md)
- [N8](N8-qemu-arm64-guest.md)
- [N16](N16-compatibility-evidence.md)

## Code and documentation

- [src/install.zig](../src/install.zig)
- [src/launch.zig](../src/launch.zig)
- [src/confine.zig](../src/confine.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [build.zig](../build.zig)
- [packaging/arch/PKGBUILD](../packaging/arch/PKGBUILD)
- [packaging/debian/control](../packaging/debian/control)
- [docs/HOST_REQUIREMENTS.md](../docs/HOST_REQUIREMENTS.md)

## Implementation

1. Use actual target-app demand to choose x86 and/or armeabi-v7a before committing to additional maintained runtimes.
2. Provide matching 32-bit JVM/ART, Bionic loader, bridge and dependencies; inspect IA32/AArch32 hardware/kernel availability.
3. Use qemu-i386/qemu-arm or FEX where appropriate; do not assume 64-bit host support implies 32-bit guest ABI support.
4. Audit pointer widths, float/double/varargs ABI and syscall architecture for the actual direct or translated execution process.

## Acceptance

- Existing 32-bit NDK applications exercise JNI/threads/callback behavior and complete useful default-confined flows.
- Unsupported native compat execution falls back only to a verified whole-guest route or reports unavailable before launch.
- Packaging clearly separates supported bitness/runtime and measured footprint.

## Stop condition

No x86/ARMv7 support claim based on 64-bit success; abandon a low-demand route if maintenance or unavailable runtime/hardware exceeds benefit.

## Effort estimate

Planning estimate: 2–6 additional engineer-weeks per selected route after core native/guest contract.

## Reuse sources

- [https://developer.android.com/ndk/guides/abis](https://developer.android.com/ndk/guides/abis)
- [https://www.qemu.org/docs/master/user/main.html](https://www.qemu.org/docs/master/user/main.html)
- [https://github.com/FEX-Emu/FEX/blob/main/Readme.md](https://github.com/FEX-Emu/FEX/blob/main/Readme.md)
- [https://github.com/ptitSeb/box86](https://github.com/ptitSeb/box86)

## Verification evidence

Not recorded.


## Code review

Not recorded.
