# Runtime reuse assessment

## Decision

Keep Isakai as the product and evaluate external Android-runtime projects as
selective components. Do not replace Isakai's launcher, APK registry,
signature policy, Wayland window lifecycle, storage layout, or confinement
path with ATL until an equivalent acceptance run passes.

The pasted architecture proposal is directionally correct: Android APKs need
more than an ELF loader, and reusing ART, Bionic compatibility, and existing
Android framework work is faster than recreating those systems. It is not,
however, a drop-in implementation for this repository. ATL uses a different
APK/data model, GTK/EGL integration, build system, and runtime boundary, while
Isakai already has a DEX-to-JVM translator and a Java `android.*` framework.

## Candidate mapping

| Candidate | What it can provide | Fit with Isakai | Initial decision |
|---|---|---|---|
| [Android Translation Layer](https://gitlab.com/android_translation_layer/android_translation_layer) | APK launcher, Android framework implementations, GTK/EGL desktop integration, ART/Bionic integration | Overlaps launcher, framework, UI, storage, and lifecycle; useful as a compatibility reference and controlled experiment | Reuse selectively; do not wholesale-import |
| [ART Standalone](https://gitlab.com/android_translation_layer/art_standalone) | Linux-hosted `dalvikvm`, `libandroidfw`, ART and dependencies | Potential long-term replacement for the custom DEX-to-JVM execution path, but changes class loading, boot classpath, JNI, and process integration | Prototype separately |
| [Bionic Translation](https://gitlab.com/android_translation_layer/bionic_translation) | Bionic linker, pthread/libc wrappers, Android-linked `.so` loading on musl/glibc | Directly relevant to Isakai's current x86_64 native-library gate and future JNI support; requires main-executable integration and EGL symbols | Highest-value native compatibility experiment |
| AOSP `frameworks/base` and `frameworks/native` | Reference implementations and API behavior | Good source of semantics and focused ports; importing system services wholesale would pull in Binder and Android system assumptions | Use as reference, not as a subtree dependency |
| Wayland/GTK/EGL | Linux windows, input, graphics contexts | Isakai already has a hand-rolled Wayland/software-rendering path; ATL's GTK/EGL approach may help native graphics but would alter UI ownership | Compare only for native/GPU workloads |
| PipeWire/FFmpeg | Host media/audio backends | Isakai already has PulseAudio and FFmpeg bridges | Reuse individual backend ideas only when a measured app requires them |
| QEMU user mode, FEX, Box64 | Foreign-architecture execution | None is a drop-in ARM Android JNI solution; they do not solve Bionic, linker, callbacks, or ART integration by themselves | Separate research milestone |
| AOSP Native Bridge | Interface for translated native libraries | Relevant boundary for ARM native code, but requires a real guest linker/translator and JNI bridge | Defer until x86 native loading is reliable |

## Evidence from upstream

- ATL's repository documents a Linux launcher, a Meson build, explicit
  activity selection, per-APK data directories, GTK/EGL operation, and a
  workflow of adding Android API implementations as applications expose
  missing behavior.
- ART Standalone documents a self-hosted build, a `dalvikvm` executable,
  `libandroidfw`, and a dependency on Bionic Translation. Its build is based
  on a large downstream AOSP-derived tree rather than a small embeddable
  library.
- Bionic Translation documents a modified Android linker and wrappers for
  libc, pthread, and standalone C++ behavior. It currently requires copying
  `main_executable/bionic_compat.c` into the host executable and configuring
  library search through environment variables.
- All three projects are public GitLab repositories with active recent
  upstream activity as of this assessment. Their build systems and runtime
  assumptions must still be validated locally before adoption.

Primary sources:

- [ATL README](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/README.md)
- [ART Standalone README](https://gitlab.com/android_translation_layer/art_standalone/-/blob/master/README.md)
- [Bionic Translation README](https://gitlab.com/android_translation_layer/bionic_translation/-/blob/master/README.md)
- [ATL license](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/LICENSE.txt)

## Proposed integration boundary

Keep these responsibilities in Isakai:

- APK inspection, v2 signature verification, version/signer continuity, and
  per-app registry state.
- App data ownership, UID allocation, privileged launch, Landlock/seccomp,
  mount namespace setup, and process cleanup.
- Desktop window identity, Wayland socket confinement, compositor lifecycle,
  evidence capture, and the three-app acceptance harness.

Evaluate external components behind narrow adapters:

1. A native-library loader adapter around Bionic Translation for staged
   `lib/x86_64` libraries.
2. An execution adapter that can start a small ART Standalone `dalvikvm`
   experiment without changing the production launcher.
3. A graphics adapter only if an app's measured native EGL/OpenGL requirement
   cannot be served by the current bridge.
4. Focused AOSP framework ports when a real app failure identifies a missing
   semantic API.

## Proof-of-concept sequence

1. Build and run Bionic Translation's own tests or smallest documented
   loading example on the supported host.
2. Load one staged x86_64 native library from an Isakai app directory and
   compare symbol resolution, environment, JNI entry, and failure behavior.
3. Build ART Standalone and execute a minimal DEX class with an explicit
   boot/class path; record build prerequisites and runtime assumptions.
4. Attempt one pinned Java/Kotlin utility APK in an isolated experiment.
5. Compare against Isakai's existing app-flow, packaging, signer, and
   confinement checks.
6. Adopt only a component that reduces concrete compatibility work without
   weakening the security or acceptance gates.

ARM32/ARM64 execution, Binder/system services, full media, and GPU-native
rendering remain independent milestones. They are not prerequisites for the
current three-app delivery target.
