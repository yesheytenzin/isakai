# Roadmap: pending and deferred tasks

Machine-readable per-task lines (`Status: <status> — <next>`) feed
`tools/progress-status.py`. Bodies below are the harvested definitions.
To activate a task: restore its file from git history, delete its
section here, rerun the status script.

<a id="N7"></a>
<a id="N7"></a>

## N7: Deliver native AArch64 host builds packaging and audited confinement

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N2](archive/N2-abi-selection.md)
- [N3](archive/N3-trusted-runtime.md)
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

<a id="N8"></a>
<a id="N8"></a>

## N8: Establish whole-guest QEMU ARM64 application execution on x86_64

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N1](archive/N1-evidence-reproducibility.md)
- [N3](archive/N3-trusted-runtime.md)
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

<a id="N9"></a>
<a id="N9"></a>

## N9: Compare FEX and Box64 for x86 guest execution on ARM64

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N7](ROADMAP.md#N7)
- [N8](ROADMAP.md#N8)

## Code and documentation

- [src/launch.zig](../src/launch.zig)
- [src/confine.zig](../src/confine.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [tools/compat-report.py](../tools/compat-report.py)
- [docs/HOST_REQUIREMENTS.md](../docs/HOST_REQUIREMENTS.md)

## Implementation

1. Use the established whole-guest process/desktop IPC contract with an x86_64 runtime and app ABI.
2. Evaluate FEX first with required guest RootFS, QEMU as comparator and optional Box64 on the same inputs.
3. Separate guest Bionic/JNI requirements from translator CPU capability and host API wrapping.
4. Measure useful app work, callbacks, signals, memory and maintenance cost on real ARM64 hardware.

## Acceptance

- Native reference, QEMU and selected accelerator produce identical useful task results.
- Default confinement and process cleanup pass; selected accelerator shows reproducible benefit.
- Direction is documented explicitly as x86/x86_64 on ARM64.

## Stop condition

A synthetic ELF launch or per-app fragile host-library hacks do not justify default adoption; retain the working baseline.

## Effort estimate

Planning estimate: 1–3 engineer-weeks after guest boundary and ARM64 host exist.

## Reuse sources

- [https://github.com/FEX-Emu/FEX/blob/main/Readme.md](https://github.com/FEX-Emu/FEX/blob/main/Readme.md)
- [https://github.com/ptitSeb/box64/blob/main/README.md](https://github.com/ptitSeb/box64/blob/main/README.md)
- [https://www.qemu.org/docs/master/user/main.html](https://www.qemu.org/docs/master/user/main.html)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N10"></a>
<a id="N10"></a>

## N10: Time-box Digitalis ARM64-to-x86_64 mixed-ISA feasibility

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N4](archive/N4-upstream-pins.md)
- [N8](ROADMAP.md#N8)

## Code and documentation

- [progress/N10.md](#N10)
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

<a id="N11"></a>
<a id="N11"></a>

## N11: Compare ART Standalone against the existing JVM backend

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N3](archive/N3-trusted-runtime.md)
- [N4](archive/N4-upstream-pins.md)

## Scheduling

Run this bounded comparison once N3/N4's required contracts are available.
It is an M2 reuse decision, not gated on native graphics or foreign-ISA
execution. Preserve the existing JVM until the challenger passes the same
useful flow and confinement requirements.

## Code and documentation

- [src/launch.zig](../src/launch.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [build.zig](../build.zig)
- [src/translate.zig](../src/translate.zig)
- [framework/org/isakai/runtime/IsakaiMain.java](../framework/org/isakai/runtime/IsakaiMain.java)
- [framework/android/graphics/HostText.java](../framework/android/graphics/HostText.java)
- [framework/android/graphics/BitmapFactory.java](../framework/android/graphics/BitmapFactory.java)

## Implementation

1. Build pinned ART/Bionic dependencies in a temporary local prefix using explicit recipes.
2. Execute existing managed DEX and same-ISA NDK application artifacts, then one pinned utility app with an audited framework subset; do not create test cases or fixture files.
3. Inventory Java2D/AWT/host-image/Path.of and core-library dependencies; document actual supported DEX features.
4. Compare app behavior, native integration, startup/RSS/size, patch burden and confinement against JVM; choose one strategic production direction.

## Acceptance

- Actual managed/native callbacks execute; build or --help output alone fails acceptance.
- Chosen app completes useful task under default isolation or exact blocking dependencies are recorded.
- Decision identifies retained JVM fallback and bounded migration scope rather than deleting translator on build success.

## Stop condition

Stop the spike at its timebox if dependency build/runtime requirements are unresolved; do not infer current DEX fidelity from the original Android 6 source base.

## Effort estimate

Planning estimate: 2–5 engineer-days initial spike; 3–6 weeks minimal backend; framework/render migration can add 1–3 engineer-months.

## Reuse sources

- [https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/README.md](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/README.md)
- [https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/art/runtime/jni/java_vm_ext.cc](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/art/runtime/jni/java_vm_ext.cc)
- [https://source.android.com/docs/core/runtime/dex-format](https://source.android.com/docs/core/runtime/dex-format)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N12"></a>
<a id="N12"></a>

## N12: Compare Robolectric Simulator and dex2jar for APK execution

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N3](archive/N3-trusted-runtime.md)
- [N4](archive/N4-upstream-pins.md)

## Scheduling

Run this bounded comparison once N3/N4's required contracts are available.
It is an M2 reuse decision, not gated on native graphics or foreign-ISA
execution. A successful build does not authorize replacing the current backend.

## Code and documentation

- [src/translate.zig](../src/translate.zig)
- [src/launch.zig](../src/launch.zig)
- [src/install.zig](../src/install.zig)
- [build.zig](../build.zig)
- [framework/org/isakai/runtime/IsakaiMain.java](../framework/org/isakai/runtime/IsakaiMain.java)
- [progress/N12.md](#N12)

## Implementation

1. Run a pinned source-built app with official Robolectric Simulator APIs first.
2. Translate that exact APK with pinned dex2jar and initialize manifest/resources/instrumented classloader/shadows without mixing duplicate android.* owners.
3. Compare complete multidex/features and real task behavior against current translator/JVM; include conversion tool/input hash in artifact cache key.
4. Verify Linux ARM64 native runtime availability, JNI feasibility and OS isolation separately from Robolectric classloader sandbox.

## Acceptance

- Same source-app and APK useful task, back/restart/persistence and confined shutdown are demonstrated.
- Exact failures and missing framework/native boundaries are recorded if arbitrary APK adaptation is not viable.
- Adopt dex2jar independently only when it improves measured conversion behavior.

## Stop condition

A large maintained fork of Robolectric internals or unavailable required ARM64 native binaries stops production adoption; retain it as oracle if useful.

## Effort estimate

Planning estimate: 5–10 engineer-days comparative spike; 4–8 engineer-weeks limited pilot only after pass.

## Reuse sources

- [https://robolectric.org/simulator/](https://robolectric.org/simulator/)
- [https://robolectric.org/architecture/](https://robolectric.org/architecture/)
- [https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17)
- [https://github.com/pxb1988/dex2jar/releases/tag/v2.4](https://github.com/pxb1988/dex2jar/releases/tag/v2.4)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N13"></a>
<a id="N13"></a>

## N13: Use apksig verification and atomic signed split-set installation

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N2](archive/N2-abi-selection.md)
- [N4](archive/N4-upstream-pins.md)
- [N5](N5-resource-engine.md)

## Code and documentation

- [src/apksig.zig](../src/apksig.zig)
- [src/install.zig](../src/install.zig)
- [src/upgrade.zig](../src/upgrade.zig)
- [src/registry.zig](../src/registry.zig)
- [src/launch.zig](../src/launch.zig)
- [framework/android/content/res/AssetManager.java](../framework/android/content/res/AssetManager.java)
- [framework/android/content/IsakaiPackageManager.java](../framework/android/content/IsakaiPackageManager.java)

## Implementation

1. Invoke pinned public ApkVerifier before privilege changes; return bounded structured signer/scheme/lineage/error results.
2. Preserve signer continuity policy and version/platform policy rather than treating verifier success as update authorization.
3. Install base/feature/configuration split sets atomically with matching package/version/signers and deterministic resource/multidex ordering.
4. Use bundletool extract-apks/device-spec as selection reference; preserve existing signatures and reject partial/inconsistent sets.

## Acceptance

- Corrupt/changed payload, wrong signer and malformed signature/split inputs leave prior app/data intact.
- Required valid signature/rotation schemes are verified according to pinned library policy.
- A signed split app selects truthful ABI/locale/density paths and completes useful UI through update/rollback.

## Stop condition

Do not silently debug-sign third-party bundles, call unstable apksig internals or claim all schemes from library presence alone.

## Effort estimate

Planning estimate: 2–4 engineer-weeks pilot and selected split-app workflow.

## Reuse sources

- [https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/README.md](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/README.md)
- [https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/src/main/java/com/android/apksig/ApkVerifier.java](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/src/main/java/com/android/apksig/ApkVerifier.java)
- [https://developer.android.com/tools/bundletool](https://developer.android.com/tools/bundletool)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N14"></a>
<a id="N14"></a>

## N14: Introduce CPU Skia rendering and consistent Unicode text layout

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N3](archive/N3-trusted-runtime.md)
- [N4](archive/N4-upstream-pins.md)

## Code and documentation

- [framework/android/graphics/Canvas.java](../framework/android/graphics/Canvas.java)
- [framework/android/graphics/Paint.java](../framework/android/graphics/Paint.java)
- [framework/android/graphics/Bitmap.java](../framework/android/graphics/Bitmap.java)
- [framework/android/graphics/HostText.java](../framework/android/graphics/HostText.java)
- [framework/android/text/StaticLayout.java](../framework/android/text/StaticLayout.java)
- [framework/android/widget/EditText.java](../framework/android/widget/EditText.java)
- [src/bridge/window.zig](../src/bridge/window.zig)
- [src/bridge/jni.zig](../src/bridge/jni.zig)
- [build.zig](../build.zig)

## Implementation

1. Wrap SkCanvas/SkSurface/Paint/Path ownership behind a narrow host C ABI, initially drawing into current CPU SHM buffers.
2. Unify shaping, measurement, drawing, hit testing, selection and fallback through established text libraries.
3. Evaluate pinned Layoutlib Java delegates selectively; do not replace lifecycle with preview assumptions.
4. Retain current CPU path until visual/input parity; audit font and dependency licenses and bounded caches.

## Acceptance

- Real APK corpus renders and edits Arabic/Indic/Tibetan/bidi/combining/emoji text with consistent cursor and selection on both host architectures.
- Clipping/alpha/paths/gradients/resize and saved Unicode behavior agree with reference within documented tolerances.
- Repeated UI cycles have bounded caches/fd/thread counts under isolation.

## Stop condition

Do not promote a preview-only renderer or mismatch text measurement and drawing; defer GPU until CPU correctness passes.

## Effort estimate

Planning estimate: 3–6 engineer-weeks bounded pilot.

## Reuse sources

- [https://skia.org/docs/](https://skia.org/docs/)
- [https://harfbuzz.github.io/](https://harfbuzz.github.io/)
- [https://freetype.org/](https://freetype.org/)
- [https://github.com/unicode-org/icu](https://github.com/unicode-org/icu)
- [https://android.googlesource.com/platform/frameworks/layoutlib/+/refs/heads/main/README](https://android.googlesource.com/platform/frameworks/layoutlib/+/refs/heads/main/README)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N15"></a>
<a id="N15"></a>

## N15: Provide native windows and Mesa graphics adapters

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N5](N5-resource-engine.md)
- [N6](N6-bionic-jni.md)
- [N14](ROADMAP.md#N14)
- [N18](N18-ndk-assets.md)

## Code and documentation

- [src/bridge/window.zig](../src/bridge/window.zig)
- [src/bridge/jni.zig](../src/bridge/jni.zig)
- [framework/android/content/res/AssetManager.java](../framework/android/content/res/AssetManager.java)
- [framework/org/isakai/bridge/IsakaiWindow.java](../framework/org/isakai/bridge/IsakaiWindow.java)
- [src/native/bionic_shim.zig](../src/native/bionic_shim.zig)
- [build.zig](../build.zig)

## Implementation

1. Define ANativeWindow ownership and Java-native object lifetime explicitly. Reuse the NDK asset contract from N18 rather than reimplementing it here.
2. Choose libwayland-client/libwayland-egl proxy ownership or tested GDK backend instead of reusing raw wire IDs as native pointers.
3. Bind packaged Mesa EGL/GLES with context/buffer/synchronization/resize handling; compare ANGLE only where justified.
4. Keep CPU fallback; add Vulkan/AHardwareBuffer as separate capability scopes after selected NDK app needs them.

## Acceptance

- An Android-NDK-built assets/window/GLES app renders, accepts input, resizes and recovers context loss with correct lifetime.
- Selected x86 and ARM GPUs plus CPU fallback pass meaningful scenes and confined cleanup.
- Preserve N18's asset fd/offset behavior and verified read-only dependent library paths.

## Planning boundary

NDK asset ownership is tracked independently in [N18](N18-ndk-assets.md), which depends
on N5/N6 but not on N14 or this graphics task. N5 does not depend on N15.

## Stop condition

Linking EGL or creating a host context alone is insufficient; absent native-window contracts or confinement bypass blocks promotion.

## Effort estimate

Planning estimate: 4–8 engineer-weeks selected GLES pilot; broader Vulkan/hardware-buffer behavior additional.

## Reuse sources

- [https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/src/libandroid/native_window.c](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/src/libandroid/native_window.c)
- [https://docs.mesa3d.org/egl.html](https://docs.mesa3d.org/egl.html)
- [https://github.com/google/angle/blob/main/README.md](https://github.com/google/angle/blob/main/README.md)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N16"></a>
<a id="N16"></a>

## N16: Publish exact compatibility and performance evidence by backend

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N1](archive/N1-evidence-reproducibility.md)
- [N2](archive/N2-abi-selection.md)

## Code and documentation

- [progress/N16.md](#N16)
- [docs/SUPPORTED_APIS.md](../docs/SUPPORTED_APIS.md)
- [README.md](../README.md)
- [tools/compat-report.py](../tools/compat-report.py)
- [verify-isakai/sway-flow.py](../verify-isakai/sway-flow.py)
- [verify-isakai/sway_backend/flows_opencalc.py](../verify-isakai/sway_backend/flows_opencalc.py)
- [verify-isakai/sway_backend/flows_calculator.py](../verify-isakai/sway_backend/flows_calculator.py)
- [verify-isakai/sway_backend/flows_notes.py](../verify-isakai/sway_backend/flows_notes.py)

## Implementation

1. Maintain a versioned utility/JNI/C++threaded/native-assets/ARM-only corpus with byte-identical inputs and reference Android runs.
2. Separate install, ELF inventory, loader, JNI, launch and completed workflow results; include host/APK ISA, bitness and backend identity.
3. Record cold/warm launch, translation cache, RSS/PSS, idle CPU, frame/input latency, fd/thread counts, size and cleanup with each accepted flow.
4. Keep all live completion status/evidence in progress/; publish a generated or reviewed capability view without creating a second tracker.
5. Reconcile historical OpenCalc PASS records with the current clean-state pass and warm-state Syntax/copy failure; never preserve a support claim solely because an older helper returned success.

## Acceptance

- Every support claim links to immutable provenance, actual asserted actions, screenshots and exit/cleanup evidence.
- Current three-app gate and each future architecture gate remain independently visible.
- Performance budgets are calibrated from comparable useful tasks, not assumed upstream speedups.

## Stop condition

No universal compatibility percentage or performance claim without defined corpus, denominator and comparable measured runs.

## Effort estimate

Planning estimate: 3–7 engineer-days initial corpus/report format; recurring per-release evidence work.

## Reuse sources

- [https://developer.android.com/ndk/guides/abis](https://developer.android.com/ndk/guides/abis)
- [https://source.android.com/docs/compatibility/overview](https://source.android.com/docs/compatibility/overview)
- [https://developer.android.com/google/play/integrity/verdicts](https://developer.android.com/google/play/integrity/verdicts)

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="N17"></a>
<a id="N17"></a>

## N17: Decide and prove independent x86 and ARMv7 32-bit support

Status: pending — no action until scheduled; start per acceptance criteria when the phase opens

## Dependencies

- [N6](N6-bionic-jni.md)
- [N8](ROADMAP.md#N8)
- [N16](ROADMAP.md#N16)

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

<a id="F1"></a>
<a id="F1"></a>

## F1: Harden media for a selected app

Status: deferred — no action while deferred; revisit only if the task is un-deferred

## Dependencies

None.

## Code and documentation

- [framework/android/media/MediaPlayer.java](../framework/android/media/MediaPlayer.java)
- [framework/org/isakai/bridge/IsakaiMedia.java](../framework/org/isakai/bridge/IsakaiMedia.java)

## Delivery review details

Introduce asynchronous bounded playback beneath MediaPlayer/IsakaiMedia using GStreamer or FFmpeg plus host audio.

**Additional acceptance / stop gate:** 30-minute stream, pause/seek/loop/EOS/errors, timeout/cancel, bounded memory and confined access. Stop “MediaCodec supported” unless its own contract passes.

**Original planning estimate:** 3–6 weeks for a selected audio/video app.


## Acceptance

Long audio plays with bounded memory; looping repeats; pause/seek/resume work; a hung decoder times out; errors propagate; the same flow passes under default isolation.

## Verification evidence

### 2026-10-12 — partial

Confirmed defect fixed: IsakaiMedia read stdout and stderr to EOF before waitFor, so a decoder that never wrote could not be timed out. All host processes now go through one supervisor with the deadline started before the wait, concurrent bounded pipe draining (stdout capped and fatal on overflow, stderr keeps the tail), forced kill plus reap on timeout/interrupt, and stdin closed so a decoder cannot block on input. Regression: org.isakai.bridge.HostProcessTest (hung child times out, pipes separated, stderr tail cap, exit text, overflow kill, stdin EOF). Streaming PCM playback/state machine and content URIs remain open.

Artifacts:

- `/tmp/isakai-checks-final/java.log`

Command:

```sh
zig build test-java
```


## Code review

Not recorded.

<a id="F2"></a>
<a id="F2"></a>

## F2: Integrate a real WebView engine

Status: deferred — no action while deferred; revisit only if the task is un-deferred

## Dependencies

None.

## Code and documentation

- [framework/android/webkit](../framework/android/webkit)

## Delivery review details

BrowserBackend beneath `framework/android/webkit`, process/profile ownership in launch and broker.

**Additional acceptance / stop gate:** Offline/HTTPS page, general JS, cookie restart, origin-safe bridge, IME/input, child crash recovery; browser sandbox enabled.

**Original planning estimate:** 4–8 weeks pilot, larger for broad Android WebView parity.


## Acceptance

A pinned app renders and interacts with its required page, executes general JavaScript, persists required cookies and handles engine failure without crashing the app.

## Verification evidence

Not recorded.


## Code review

Not recorded.

<a id="F3"></a>
<a id="F3"></a>

## F3: Prove ARM native execution before broad integration

Status: deferred — no action while deferred; revisit only if the task is un-deferred

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

<a id="F4"></a>
<a id="F4"></a>

## F4: Expose honest sensors and location capabilities

Status: deferred — no action while deferred; revisit only if the task is un-deferred

## Dependencies

None.

## Code and documentation

- [framework/org/isakai/bridge/IsakaiSensor.java](../framework/org/isakai/bridge/IsakaiSensor.java)
- [framework/org/isakai/bridge/IsakaiLocation.java](../framework/org/isakai/bridge/IsakaiLocation.java)

## Delivery review details

Real provider discovery through `IsakaiSensor`, `IsakaiLocation` and authenticated desktop broker.

**Additional acceptance / stop gate:** Denial/unavailable/cancellation and real host values; no raw user session-bus or broad device grants.

**Original planning estimate:** 1–3 weeks per selected provider family.


## Acceptance

No fixed location is presented as a real reading; unavailable providers are reported; enabled backend reads reflect the host and stop after cancellation.

## Verification evidence

### 2026-10-12 — partial

Fabricated production readings removed: without a host provider (and without the explicit test switch ISAKAI_FAKE_SENSORS/ISAKAI_FAKE_LOCATION or -Disakai.fakeSensors/-Disakai.fakeLocation), SensorManager advertises no sensor, registerListener returns false, LocationManager reports providers disabled and getLastKnownLocation returns null. A listener removed before its queued delivery now receives nothing (location registration tokens; sensor registrations are marked inactive before removal). Regression: android.hardware.SensorLocationTest covers both modes plus the cancellation paths. Real provider discovery (iio-sensor-proxy/GeoClue2 through the bridge) is still unwired.

Artifacts:

- `/tmp/isakai-checks-final/java.log`

Command:

```sh
zig build test-java
```


## Code review

Not recorded.

<a id="F5"></a>
<a id="F5"></a>

## F5: Expand native, hardware and signature coverage on demand

Status: deferred — no action while deferred; revisit only if the task is un-deferred

## Dependencies

None.

## Code and documentation

- [src/native/bionic_shim.zig](../src/native/bionic_shim.zig)
- [src/apksig.zig](../src/apksig.zig)
- [framework](../framework)

## Delivery review details

Bound defaults and release property lock before callbacks in `src/native/bionic_shim.zig`.

**Additional acceptance / stop gate:** 91+NUL buffer canary intact; reentrant callback returns; actual app launches unaffected. Does not claim general ABI compatibility.


## Acceptance

For each activated capability, record the motivating app, a bounded design, failure behavior and a real-app acceptance flow before implementation.

## Verification evidence

Not recorded.


## Code review

Not recorded.
