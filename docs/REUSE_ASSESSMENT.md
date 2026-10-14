# Runtime reuse assessment

## Review scope

This is a source-level assessment of Isakai at `6d82bfe` and the upstream
revisions below. Upstream READMEs, build definitions, and relevant loader,
resource, UI, and Native Bridge implementations were inspected directly.
**None of these external components was built or run during this review.**
Source availability and an upstream passing pipeline do not prove an Arch
build, Isakai integration, or application compatibility.

| Project | Reviewed revision |
|---|---|
| [ATL](https://gitlab.com/android_translation_layer/android_translation_layer/-/tree/07c34abad5d29864998ed7c7cfc6c80d0765b9df) | `07c34abad5d29864998ed7c7cfc6c80d0765b9df` |
| [ART Standalone](https://gitlab.com/android_translation_layer/art_standalone/-/tree/66a5d9079b159e9c5819bfeef4a6fff6a5f32719) | `66a5d9079b159e9c5819bfeef4a6fff6a5f32719` |
| [Bionic Translation](https://gitlab.com/android_translation_layer/bionic_translation/-/tree/6253dec7f24a57a94039a088cb436d880e92e3be) | `6253dec7f24a57a94039a088cb436d880e92e3be` |

This document records design recommendations, not task completion. Runtime
verification and adoption evidence still belong in [progress.json](../progress.json).

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

| Candidate | Isakai code affected | Reuse or replacement | Recommendation |
|---|---|---|---|
| ATL Java framework / AOSP implementations | `framework/android/` | Utility classes, API contracts, layout algorithms and focused behavioral ports | Selective source ports; do not add a second conflicting `android.*` JAR |
| ART Standalone | `src/dex.zig`, `src/translate.zig`, `src/launch.zig`, framework build | Alternative direct-DEX execution engine with Android libcore and an existing Bionic loader integration | Separate experimental backend; retain JVM baseline |
| Bionic Translation | `src/native/bionic_shim.zig`, launch environment, JNI loading | Real Android ELF loader plus libc/pthread/C++ ABI wrappers | Highest-value native experiment, not a replacement preload filename |
| ART Standalone's `libandroidfw` C API | `src/arsc.zig`, `framework/android/content/res/`, binary XML parsing | Resource/configuration selection, reference resolution, themes, style bags and assets | High-value bounded experiment that need not adopt ART |
| ATL `src/libandroid/` | Missing Android NDK system-library surface | `AAsset*`, `ALooper*`, `ANativeWindow*`, EGL/Vulkan adaptation | Port per API family; dependencies prevent a wholesale library import |
| ATL GTK/GDK + EGL | `src/bridge/window.zig`, Java views and rendering | Optional graphics/window backend with Wayland and X11 paths | Larger backend choice, not a UI drop-in |
| ATL WebKitGTK implementation | `framework/android/webkit/` | Real host web rendering and asset URI handling | Reference for a named WebView-dependent app; JS bridge remains incomplete upstream |
| ATL location/sensor backends | `framework/org/isakai/bridge/IsakaiLocation.java`, `IsakaiSensor.java`, desktop broker | Portal location and Linux IIO accelerometer integration ideas | Adapt through the existing permission/broker boundary |
| AOSP `apksig` | `src/apksig.zig`; signer policy in install/upgrade | Broader standard signature verification | Evaluate if signature coverage blocks an app; preserve Isakai's signer continuity rules |
| dex2jar (already used by the oracle) | `src/translate.zig`, translation tooling | Alternative DEX-to-JVM compiler / differential reference | Compare before expanding bespoke translation indefinitely |
| PulseAudio / FFmpeg / PipeWire | `src/bridge/audio.zig`, `IsakaiMedia.java` | Existing host playback and decoding; optional future backend | Keep current backends until an app exposes a concrete limitation |
| libhybris | Native compatibility, especially Android drivers | Bionic/host interoperability with hardware-adaptation assumptions | Prefer Bionic Translation for app libraries on a Mesa desktop |
| Binder libraries, Waydroid, redroid | Services / possible alternate system backend | IPC transport and Android-userspace integration references | Not substitutes for missing framework services; not required for current in-process APIs |
| QEMU user mode / AOSP Native Bridge | Future foreign-ABI backend | Whole-process execution experiments / translated-JNI integration interface | Separate milestones; neither supplies a ready ARM-to-x86 JNI backend |
| FEX / Box64 | Future ARM64 host support | x86 guest execution on ARM64 and other supported hosts | Wrong direction for ARM APK native libraries on the current x86_64 host |
| AAPT2, Apktool, JADX, ADB, bundletool | Inspection, fixtures, comparison and split-install tooling | Development tools, not a runtime/framework implementation | Use for diagnostics and test generation; avoid making all of them launcher dependencies |

## Evidence from upstream

- ATL's `src/main-executable/main.c` starts ART through `JNI_CreateJavaVM`,
  configures APK class loading, and returns control to a GLib/GTK event loop.
  `meson.build` also requires `libandroidfw`, Bionic Translation, GTK 4.14+,
  EGL, WebKitGTK and other host libraries. Its API JAR is converted to DEX.
- ART Standalone's `art/runtime/jni/java_vm_ext.cc` calls `bionic_dlopen` and
  `bionic_dlsym`, handles `JNI_OnLoad` and classloader association, and retains
  Native Bridge load/trampoline paths. This integration is work Isakai's
  stock host JVM does not already have.
- Bionic Translation exposes `bionic_dlopen`, `bionic_dlsym`, `bionic_dlclose`
  and `dl_parse_library_path`. It maps Android library names to host/wrapper
  libraries through configuration and includes Android Bionic CTS tests.
  Its README documents executable-level debug/TLS setup, particularly ARM
  TLS placement; this needs host-specific validation, not a blanket assumption
  that an unmodified `java` executable can consume it.
- ART Standalone's Makefile installs a substantial libcore/runtime dependency
  set, `libnativebridge`, and `androidfw_c_api.h`. Its README specifies
  self-hosted builds and wolfSSL with JNI enabled. Building it on current
  Arch, or cross-building it for a translated process, was not established.
- The referenced Ancbie GitHub fork's inspected default branch
  (`Mis012/WIP-2025-07-18`) contains Meson, not a root unified CMake build.
  No specific unified-CMake fork URL was supplied or validated. Treat that
  build claim as unverified rather than a prerequisite or shortcut.

Primary sources:

- [ATL README](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/README.md)
- [ART Standalone README](https://gitlab.com/android_translation_layer/art_standalone/-/blob/master/README.md)
- [Bionic Translation README](https://gitlab.com/android_translation_layer/bionic_translation/-/blob/master/README.md)
- [ATL license](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/master/LICENSE.txt)

## Repository-specific findings

### 1. Native compatibility needs more than the current shim

`src/install.zig:inspect` accepts an APK when *any* `lib/x86_64/*.so` exists;
`wantEntry` stages that ABI only. `src/launch.zig` gives those paths to the
stock JVM and preloads `libisakai_bionic_shim.so`. The shim's
`android_dlopen_ext` simply calls host `dlopen` and ignores `extinfo`.
There is no general Android linker, libc/pthread layout translation,
NDK `libandroid.so`, or foreign-ISA engine in this path.

A loader integration must route app-library loading **and symbol lookup** to
Bionic Translation while leaving host JVM/system libraries with the host
loader. It must also handle `JNI_OnLoad`, static JNI symbol resolution,
`RegisterNatives`, classloader ownership, callbacks, native-created threads,
exceptions and library lifetime. Calling `bionic_dlopen` alone does not
register the library with HotSpot's native-method resolver. Candidate routes
are an explicitly tested JVM loading/registration adapter, selective loader
interposition or a VM modification; ART Standalone already supplies a
runtime-specific implementation. A JNI Invocation API bootstrap can address
executable setup, but does not by itself change the JVM's library resolver.

The current shim also has concrete ABI/behavior mismatches against AOSP
Android 15 headers:

| Symbol | Current shim | AOSP contract |
|---|---|---|
| `__android_log_assert` | Integer condition; returns when nonzero | String condition; logs and aborts whenever called |
| `__android_log_is_loggable` | `(tag, priority, default)` | `(priority, tag, default)` |
| `property_list` | Callback receives `"name=value"` and cookie | Callback receives separate name, value and cookie |
| `__system_property_set` | Returns `void` | Returns `int` status |

Its three unit tests exercise property reads, not these native calling
contracts. Fix or replace the contracts and add a header-compiled C/NDK
caller test; assertion behavior needs a subprocess. Do not infer general
x86_64 JNI support from an APK installing or from host-built fixture code.

Bionic Translation is also partial: its `bionic_android_dlopen_ext` reports
that non-null extension information is not implemented. Do not assume
Android linker namespaces, fd/offset loading or every modern relocation are
solved by adoption. Validate ELF class/machine and required dependency closure
(including an APK's `libc++_shared.so`) rather than selecting an ABI solely
from a directory name.

### 2. Resource handling is a strong selective replacement candidate

`Resources.java` contains its own ARSC parser; `lookupDepth` prefers a default
configuration, otherwise the first matching type chunk, rather than selecting
the best locale/density/night/SDK configuration. `src/arsc.zig` provides
another resource-table path for labels. This is a concrete opportunity to
reuse Android's resource engine instead of accumulating format/selection
patches in two implementations.

ART Standalone's `libandroidfw/include/androidfw/androidfw_c_api.h` exposes
`ApkAssets_load`, `AssetManager_setConfiguration`, `AssetManager_getResource`,
reference resolution, theme/style operations and binary XML parsers. A narrow
JNI/C adapter can retain the public Java resource classes and current window
backend. This is independent of using ART for DEX execution, though the native
library still has dependencies (`libbase`, `libutils`, `libziparchive`, etc.).

Prototype configuration-specific strings/colors, inherited themes, XML
attributes and assets against the existing implementation and Android.
System resources/framework-res also need an explicit policy. Isakai currently
stages extracted entries, not an original APK file: APK-backed loading and
native asset fd/offset semantics require preserving a verified read-only APK
or a deliberately tested staged-assets adapter. Keep installer bounds and
signature/upgrade policy outside the runtime resource engine.

### 3. ART would replace execution, not automatically the framework

Replacing DEX-to-JVM translation would remove a large bespoke compiler surface
and expose Android libcore/DEX facilities. It would not supply the missing
Android application framework, system services, rendering or permissions.

The existing framework is built with `javac --release 17` and uses host-only
APIs: `HostText.java` uses Java2D/AWT, `BitmapFactory.java` uses host image
facilities, vector rendering uses `java.awt.geom`, and `IsakaiMain` uses
`Path.of`. Moving these classes to ART requires a DEX build and a libcore/API
audit, plus replacement host graphics bindings; it is not a change from
`java` to `dalvikvm` in argv. ART's exact supported DEX/core-library behavior
must be measured, not inferred from the age of its original AOSP base.

`uid_helper.zig` currently permits only an absolute executable named `java`
and requires the `IsakaiMain` marker. Its fixed environment, runtime read
paths and `src/confine.zig` must be adapted for an ART backend without turning
the helper into an unrestricted command runner. Prefer an explicit trusted
runtime entrypoint and backend-specific environment/path construction.

### 4. ATL framework and host backends are useful, but coupled

- Pure Java helpers and AOSP-derived layout algorithms are the easiest ports.
  For example, ATL's `android.util.Rational` has no GTK implementation, while
  its `LinearLayout` still requires compatible `View`, `TypedArray` and
  internal styleable resources. Import semantics and regressions, not an
  entire competing class hierarchy.
- Isakai already has queued `Handler`/`Looper` dispatch, a partial activity
  navigator and Java2D-backed text. Older architecture prose describing
  immediate callbacks, no navigator or exclusively ASCII text is not an
  accurate basis for deciding what to replace. Full app-flow acceptance is
  still a separate question.
- ATL's `MessageQueue` native integration uses GLib and `ALooper`; its NDK
  looper wrapper calls AOSP `libutils` C++ symbols. It cannot replace the
  current Java queue by copying one file.
- ATL's `AAssetManager_fromJava` expects a native `mObject` field. Isakai's
  Java asset manager needs an adapter/ownership model before that native
  asset implementation can work.
- ATL has a WebKitGTK rendering path, gated by `ATL_UGLY_ENABLE_WEBVIEW`, but
  `evaluateJavascript` is empty and `addJavascriptInterface` contains an
  app-specific shortcut, not a general bridge. It is a backend reference,
  not a complete Android WebView to transplant.
- ATL's location implementation uses libportal and its accelerometer backend
  reads Linux IIO sysfs. Use the existing Isakai desktop broker for privileged
  host access and permission decisions; do not solve adoption by exposing the
  user's session bus or broad host filesystem access to the APK.

### 5. Native graphics requires a window/backend boundary

Isakai's `src/bridge/window.zig` stores Wayland wire object IDs and draws SHM
buffers; it does not own libwayland `wl_display*`/`wl_surface*` proxies. ATL's
`ANativeWindow` implementation expects GTK/GDK widgets, real Wayland/X11
objects and EGL window handles. Those representations are not interchangeable.

For GLES/Vulkan apps, evaluate a libwayland-client/libwayland-egl backend or
an alternate GTK/GDK backend, with Mesa/EGL/GLES/Vulkan doing GPU work. Port
Android native-window and surface semantics around it; merely linking EGL
will not convert an existing SHM surface into an Android native window.
Retain the current software path while testing resize, synchronization,
input and confinement. X11 is not currently implemented in Isakai; ATL/GDK
could provide it only as part of a tested backend change.

## Proposed integration boundary

Keep these responsibilities in Isakai:

- APK inspection, v2 signature verification, version/signer continuity, and
  per-app registry state.
- App data ownership, UID allocation, privileged launch, Landlock/seccomp,
  mount namespace setup, and process cleanup.
- Desktop window identity, Wayland socket confinement, compositor lifecycle,
  evidence capture, and the three-app acceptance harness.

Evaluate external components behind narrow adapters:

1. A resource-engine adapter around `libandroidfw`, independently of ART.
2. A native-library loader **and JNI-resolution** adapter around Bionic
   Translation for staged `lib/x86_64` libraries.
3. An execution adapter that can start a small ART Standalone experiment
   without changing the production launcher or mixing framework backends.
4. A graphics adapter only if an app's measured native EGL/OpenGL requirement
   cannot be served by the current bridge.
5. Focused AOSP/ATL framework ports when a real app failure identifies a
   missing semantic API.

Keep the Zig launcher/process manager. Rewriting it in Rust, replacing the
existing SQLite/preferences stack, or switching PulseAudio to PipeWire is
not a prerequisite for reuse. PulseAudio clients may already work through a
PipeWire PulseAudio-compatible server.

## Proof-of-concept sequence

1. Make the native ABI contracts honest and test calls compiled against
   Android headers. Inventory ELF ABI/dependencies separately from Java/API
   failures in the compatibility report.
2. Run a bounded `libandroidfw` resource/configuration comparison. Measure
   dependency footprint and semantics before replacing production parsers.
3. Build Bionic Translation's own loader example/tests, then exercise a real
   NDK-built x86_64 JNI fixture: constructors, dependency loading, `JNI_OnLoad`,
   static and registered methods, Java/native callbacks and attached threads.
   Keep unsafe unresolved-symbol/unknown-relocation test switches out of
   production. A successful standalone `dlopen` is only the first gate.
4. Build ART Standalone separately, execute minimal DEX, then evaluate one
   pinned utility APK and an ART-compatible framework/bootstrap. Compare
   against the JVM backend; do not delete the translator on build success.
5. Compare each candidate against Isakai's app-flow, packaging, signer and
   confinement checks, including default isolation and cleanup. Measure
   launch latency, RSS and installed size rather than promising improvements.
6. Adopt only a component that reduces concrete compatibility work without
   weakening security or acceptance gates. Keep separate experiments for
   GPU/WebView/foreign ABI instead of making all of them prerequisites.

## ABI and Android-system boundaries

- **Bytecode-only apps:** DEX is not tied to the APK's ARM/x86 native ABI. The
  current host-JVM translation route already addresses this case, subject to
  translator, libcore and framework compatibility.
- **x86_64 native:** current symbol subset only; Bionic loader/JNI/NDK work is
  still needed for general support.
- **x86 native on x86_64:** a 64-bit JVM cannot load a 32-bit `.so`. Evaluate a
  separate 32-bit JVM/ART process, loader and JNI/system-library build. The
  current seccomp filter pins `AUDIT_ARCH_X86_64`; an i386 process would need
  its own audited syscall filter, not a disabled sandbox.
- **ARM64/ARM32 native on x86_64:** QEMU user mode can execute a complete
  foreign-architecture process with matching libraries. A guest JVM/ART plus
  guest-built Isakai bridges is a useful research route, but does not permit
  loading ARM JNI directly into a native x86_64 JVM. ARM32 remains a separate
  bitness/ABI task. Foreign-process launch needs its own confinement audit.
- **Mixed-ISA JNI:** AOSP Native Bridge defines load/trampoline/runtime
  callbacks; its own README explicitly says AOSP does not provide an actual
  bridge implementation. Stock HotSpot does not acquire those ART hooks by
  linking `libnativebridge`. ART Standalone contains relevant integration,
  but still needs an actual translator, guest linker, callbacks/thread/signal
  handling and version-matched bridge implementation.
- **Binder:** Isakai's Java `Binder` is a same-process shell, not Android IPC.
  libgbinder/libbinder provide transport, not ActivityManager, PackageManager,
  Media services or Google services. Importing system_server/frameworks/base
  wholesale changes the project into an Android-userspace backend. Keep
  Waydroid/redroid as references or a deliberately separate compatibility
  mode, not an invisible dependency of the Wine-like runner.

## Licensing and source references

ATL has a GPLv3 root license and retains AOSP-licensed files. Isakai is
GPL-3.0-or-later; preserve imported copyright/license notices and record
modifications and source revisions. ART/Bionic trees combine upstream and
third-party code; do a file/dependency license audit rather than assuming a
single repository license. Runtime libraries, codecs, wolfSSL build options
and test APK redistribution also need review before packaging.

Additional inspected implementation sources:

- [ART's Bionic/JNI loader integration](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/art/runtime/jni/java_vm_ext.cc)
- [libandroidfw C API](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/libandroidfw/include/androidfw/androidfw_c_api.h)
- [Bionic Translation loader](https://gitlab.com/android_translation_layer/bionic_translation/-/blob/6253dec7f24a57a94039a088cb436d880e92e3be/linker/dlfcn.c) and [CTS test instructions](https://gitlab.com/android_translation_layer/bionic_translation/-/blob/6253dec7f24a57a94039a088cb436d880e92e3be/tests/README.md)
- [ATL native-window implementation](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/src/libandroid/native_window.c)
- [ATL WebView API implementation](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/src/api-impl/android/webkit/WebView.java)
- [AOSP logging header](https://android.googlesource.com/platform/system/logging/+/refs/tags/android-15.0.0_r1/liblog/include/android/log.h), [system properties header](https://android.googlesource.com/platform/bionic/+/refs/tags/android-15.0.0_r1/libc/include/sys/system_properties.h), and [cutils properties header](https://android.googlesource.com/platform/system/core/+/refs/tags/android-15.0.0_r1/libcutils/include/cutils/properties.h)
- [Native Bridge explanation](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/system/core/libnativebridge/README.md) and [interface](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/system/core/libnativebridge/include/nativebridge/native_bridge.h)
- [QEMU user-mode documentation](https://www.qemu.org/docs/master/user/main.html), [FEX direction/requirements](https://github.com/FEX-Emu/FEX/blob/main/Readme.md), [Box64](https://github.com/ptitSeb/box64/blob/main/README.md), and [libhybris scope](https://github.com/libhybris/libhybris/blob/master/README.md)

ARM execution, full Android services, media and GPU-native rendering remain
independent milestones, not prerequisites for the current three-app target.
This review does not close that target or change its progress status.
