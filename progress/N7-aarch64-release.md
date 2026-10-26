---
id: "N7"
status: "pending"
phase: "ARM64_HOST"
priority: "P1"
size: "L"
updated: "2026-10-14"
owner: "unassigned"
next: "no action until scheduled; start per acceptance criteria when the phase opens"
---

# N7: Deliver native AArch64 host builds packaging and audited confinement

## Dependencies

- [N2](N2-abi-selection.md)
- [N3](N3-trusted-runtime.md)
- [N6](N6-bionic-jni.md)

## Code and documentation

- [build.zig](../build.zig)
- [src/install.zig](../src/install.zig)
- [src/launch.zig](../src/launch.zig)
- [src/confine.zig](../src/confine.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [src/bridge/jni.zig](../src/bridge/jni.zig)
- [packaging/arch/PKGBUILD](../packaging/arch/PKGBUILD)
- [packaging/debian/control](../packaging/debian/control)
- [docs/HOST_REQUIREMENTS.md](../docs/HOST_REQUIREMENTS.md)

## Implementation

1. Replace x86_64 ABI/path and packaging assumptions with target-aware explicit capabilities.
2. Implement and audit AArch64 seccomp syscall/architecture policy for the executable actually making kernel syscalls.
3. Build matching native libraries, selected runtime and Bionic adapter; verify x18/TLS/thread behavior.
4. Run on physical ARM64 hardware with documented kernel, GPU, page size and distro; include applicable 4K/16K page configurations.

## Acceptance

- Three pinned useful workflows and same-ISA ARM64 NDK corpus pass with default confinement.
- Forbidden syscall/filesystem/cross-app cases remain denied without disabling policy.
- Clean ARM64 package install/upgrade/uninstall and child cleanup pass.

## Stop condition

Cross-compilation alone does not pass; unavailable hardware or disabled confinement leaves ARM64 support unverified.

## Effort estimate

Planning estimate: 2–4 engineer-weeks after same-ISA native pilot, excluding hardware procurement.

## Reuse sources

- [https://developer.android.com/ndk/guides/abis](https://developer.android.com/ndk/guides/abis)
- [https://docs.kernel.org/userspace-api/seccomp_filter.html](https://docs.kernel.org/userspace-api/seccomp_filter.html)
- [https://gitlab.com/android_translation_layer/bionic_translation/-/blob/6253dec7f24a57a94039a088cb436d880e92e3be/README.md](https://gitlab.com/android_translation_layer/bionic_translation/-/blob/6253dec7f24a57a94039a088cb436d880e92e3be/README.md)

## Verification evidence

Not recorded.


## Code review

Not recorded.
