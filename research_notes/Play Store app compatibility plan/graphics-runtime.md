# Graphics and Runtime Gaps: JVM Runner vs Arbitrary Play Store Apps

## What graphics APIs do Play games actually use, and how do translation layers serve them on Linux?

### Takeaway
Play games overwhelmingly target Vulkan and OpenGL ES 3.1/3.2 (Unity and Unreal both standardize on these); on Linux the standard serving stack is ANGLE (GLES→Vulkan/GL) for API translation plus Mesa Venus (Vulkan forwarding) / Zink (GL-over-Vulkan) and gfxstream for virtualized guests — all real, shippable open-source components, but none of them removes the need for a host GPU driver with Vulkan.

### Cited Findings
- Unity supports Android 8.0 (API 26)+ and lists graphics API support as Vulkan: yes; OpenGL ES 3.1: yes; OpenGL ES 3.2: yes; GLES 1.x/2.0/3.0: no, with GLES 3.1 the minimum supported version — [Source](https://docs.unity3d.com/Manual/android-requirements-and-compatibility.html)
- Unreal Engine (UE 5.8 era) supports Android 8+ on 64-bit Arm CPUs with compatible graphics APIs OpenGL ES 3.2 and Vulkan 1.1 (on Android 10+ devices with compatible drivers); compatible GPU families listed are Mali-T8xx/G5x/G6x/G7x/G6xx/G7xx/G9xx, Adreno 5xx–8xx, PowerVR/IMG B-series and newer, Samsung Xclipse — [Source](https://dev.epicgames.com/documentation/en-us/unreal-engine/android-development-requirements-for-unreal-engine)
- Google Play requires new apps and updates to target API 35 (Android 15) since Aug 31, 2025; UE 5.7+ provides target-SDK 35 support — [Source](https://dev.epicgames.com/documentation/en-us/unreal-engine/android-development-requirements-for-unreal-engine)
- ANGLE translates OpenGL ES 2.0/3.0/3.1 to Vulkan, desktop GL, GLES, D3D9/11 (ES 3.2 support in progress on some backends); ANGLE with the Vulkan backend passed ES 3.2 conformance (v2.1.2, Sept 2023) and it also implements EGL 1.5; it is the default WebGL backend in Chrome/Firefox on Windows and is used on Linux/macOS/mobile browser variants — [Source](https://angleproject.org/)
- gfxstream ("Graphics Streaming Kit", formerly Vulkan Cereal) is Google's collection of code generators and libraries for streaming rendering APIs from a VM guest to host — i.e., the mechanism Android Emulator / Cuttlefish-class virtualized Android uses to forward guest GLES/Vulkan to the host — [Source](https://github.com/google/gfxstream)
- Mesa Venus is a virtio-GPU protocol for Vulkan command serialization (guest Vulkan → host Vulkan); host needs Vulkan 1.1 + VK_KHR_external_memory_fd, tested against ANV 21.1+, RADV 21.1+, NVIDIA proprietary 570.86+, Mali r32p0+, Turnip 22.0+, PanVK 25.1+, Lavapipe 22.1+; guest kernel needs virtio-gpu blob/context-init params (≈5.16+), works with crosvm or QEMU (`virtio-gpu-gl,blob=true,venus=true`) — [Source](https://docs.mesa3d.org/drivers/venus.html)
- Mesa Zink is a Gallium driver emitting Vulkan calls to give full desktop OpenGL on Vulkan-only devices; OpenGL 2.1 is the minimum it can expose (needs Vulkan 1.0 + extensions like timeline semaphore, dynamic rendering, robustness2, scalar block layout), with higher GL levels gated on further host features (transform feedback, geometry/tessellation shaders, etc.) — [Source](https://docs.mesa3d.org/drivers/zink.html)

### Inferences
- A JVM-based runner on Linux does not need to reimplement GLES/Vulkan itself: the viable architecture is EGL/GLES API surface → ANGLE-on-host-Vulkan (or host GL), and for native (NDK) games the Vulkan entry points must be served by the host Vulkan driver, optionally via Venus-style forwarding if the runner sandboxes rendering in a guest/VM.
- The binding constraint is the host GPU: anything ANGLE/Zink/Venus needs bottoms out at a real Vulkan-capable Linux driver; software fallback exists (Lavapipe) but is not a performance story for games.
- Engine coverage check: serving GLES 3.2 + Vulkan 1.1 faithfully covers both Unity's minimum (GLES 3.1) and UE's declared APIs; UE's enumerated GPU list is a device-compat statement, not an API restriction, so a translator passing conformance should satisfy engine feature detection (GPU-name allowlists in old games remain a residual risk).

### Gaps
- Could not verify exact Android CDD-mandated Vulkan/GLES versions (source.android.com CDD pages render client-side; fetches returned JS shells with zero content matches). Report writer should not cite a specific "CDD requires Vulkan X.Y" number from these notes.
- No reliable source found on what share of top Play games ship native Vulkan vs GLES-only renderers; engine defaults above are the proxy.

## What media stack do video/social apps need, and what is shippable without certified DRM?

### Takeaway
Clear (non-DRM) playback — ExoPlayer/Media3 over platform decoders plus software fallbacks (FFmpeg/AV1/VP9 extensions) — is fully shippable on a reimplemented stack; the hard boundary is Widevine DRM, which requires certified CDM/TEE integration, so Netflix/Prime/Disney+ HD and other protected streams are not shippable without certification, while YouTube-style clear DASH/HLS is.

### Cited Findings
- ExoPlayer uses Android's MediaDrm API for protected playback; minimum versions: Widevine "cenc" API 19 (DASH, FMP4-HLS), Widevine "cbcs" API 25, ClearKey "cenc" API 21, PlayReady SL2000 Android-TV only — [Source](https://developer.android.com/media/media3/exoplayer/drm)
- Without DRM, ExoPlayer core use cases need only API 23: audio, video, video-with-effects, DASH, SmoothStreaming, HLS (incl. AES-128 HLS); HDR video needs API 24; Widevine cbcs DASH/HLS needs 25 — [Source](https://developer.android.com/media/media3/exoplayer/supported-devices)
- By default ExoPlayer uses Android's platform decoders (MediaCodec), so supported formats depend on the platform; additionally ExoPlayer ships optional software decoder extensions (must be manually built/included) for AV1, VP9, FLAC, Opus, FFmpeg, MIDI, IAMF, MPEG-H — [Source](https://developer.android.com/media/media3/exoplayer/supported-formats)
- ExoPlayer supports live and on-demand RTSP (H264 with SPS/PPS in SDP, ADTS-AAC, AC3) over UDP unicast or TCP-interleaved — [Source](https://developer.android.com/media/media3/exoplayer/rtsp)
- Widevine integration for new devices/chipsets follows an established certification process (robustness rules review per device type/chipset), and Widevine runs a Certified Implementation Partner (CWIP) training/certification program for encoders/packagers/proxies/Android integrations — [Source](https://www.widevine.com/solutions/widevine-drm)
- Async MediaCodec buffer queueing (reduces dropped frames/underruns, esp. DRM/high-framerate content) is default on Android 12+, backportable to API 23 via DefaultRenderersFactory — [Source](https://developer.android.com/media/media3/exoplayer/customization)

### Inferences
- For a JVM runner, MediaCodec can be reimplemented over host codecs (FFmpeg/GStreamer): that covers the entire clear-content matrix above (H.264/H.265/VP9/AV1/AAC, DASH/HLS/RTSP), since ExoPlayer only needs a MediaCodec-shaped API for clear content.
- MediaDrm/Widevine must be stubbed as unavailable (or ClearKey-only): apps querying DRM will see "no secure decoder," which degrades protected playback to SD/unavailable exactly as on uncertified Android builds — a known, standard failure mode rather than a crash, provided the MediaDrm API exists and reports unsupported.
- Social apps (TikTok/Instagram/Reels) are predominantly clear-content + camera; their risk is camera/codec performance, not DRM. Subscription video apps are the ones that hit the certified-only wall.

### Gaps
- Could not verify from a primary source whether Widevine L3 (software-only, no TEE) is licensable for a Linux desktop-class runner or is Android/ChromeOS-build-gated; treat all Widevine levels as certified-only until verified.
- MediaCodec platform page (developer.android.com/media/platform/mediacodec) is JS-rendered and yielded no citable content; codec-specific behavior notes (e.g., async queueing defaults) come from Media3 docs instead.

## What breaks when running DEX bytecode on a JVM instead of ART?

### Takeaway
DEX is not JVM bytecode: a translator must handle multidex packaging, the full Dalvik opcode set including invoke-custom/method-handles, R8/ProGuard-optimized artifacts, Kotlin metadata-driven behaviors, hidden-API (non-SDK) restrictions apps expect to be enforced leniently-or-not per targetSdk, and above all JNI — native methods in real apps assume ART's JNI semantics (RegisterNatives discipline, Modified UTF-8, @FastNative/@CriticalNative, GC interaction).

### Cited Findings
- The Dalvik Executable spec caps method references invocable from a single DEX file (the 64K limit); exceeding it requires multidex configuration, and multidex is on by default only when minSdkVersion ≥ 21, otherwise the androidx.multidex library + MultiDex.install/attachBaseContext is required and secondary dexes are searched via a special class loader — [Source](https://developer.android.com/studio/build/multidex)
- The DEX format natively contains method-handles lists, call-site identifier lists, and bootstrap-linker method handles returning java.lang.invoke.CallSite (i.e., invoke-custom / invoke-polymorphic support is a format-level feature) — [Source](https://source.android.com/docs/core/dalvik/dex-format)
- Android restricts non-SDK (hidden) API use via blocklist (blacklist) plus conditionally-blocked (dark-grey) lists gated by max-target-sdk; OEMs may add to the blocklist but not remove from it; a documented example is hidden field Landroid/os/Message;->flags:I accessed via JNI — [Source](https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces)
- JNI tips (perf-jni) findings: apps must register native methods up front with RegisterNatives from JNI_OnLoad, and explicit registration is strictly required on Android 12+; string data uses Modified UTF-8 (NewStringUTF) vs UTF-16 (GetStringChars, not zero-terminated, \\u0000 allowed); @FastNative/@CriticalNative annotations constrain GC interaction (no blocking/critical-region violations), and @CriticalNative was unimplemented on Android 8–10 — [Source](https://developer.android.com/training/articles/perf-jni)

### Inferences
- Translation gotchas in priority order for arbitrary Play APKs: (1) JNI fidelity — most games and many social/video apps ship .so files and call into them constantly; ART-specific JNI behaviors (Modified UTF-8, local/global ref handling, @FastNative/@CriticalNative, RegisterNatives requirement) must be emulated or native libs fail in subtle ways. (2) Hidden-API expectations — apps that reflect into android.* internals behave differently depending on enforcement level; a reimplemented android.* must decide per-API stub/implement/block and match the targetSdk-gated behavior, or apps that probe will misroute. (3) invoke-custom/Kotlin — Kotlin stdlib/coroutines and newer Java features (lambdas, method refs, string concat) desugar to invoke-custom/method-handles that a naive DEX→JVM translator can mistranslate; the JVM does support invokedynamic, so faithful mapping is possible but must be explicit. (4) R8 artifacts — release APKs are heavily renamed/inlined; behavior is equivalent but stack traces, reflection-by-name, and serialization edge cases break if the translator assumes unobfuscated shapes. (5) Multidex/class-loader order — secondary-dex lookup order and LinearAlloc-era keep-lists affect class resolution; default-on ≥API 21 simplifies the common case.
- ART's dex2oat/AOT/JIT pipeline is a performance detail, not a correctness one, for a JVM translator — but ART GC semantics observable via JNI (pinning, critical sections) are correctness-relevant.

### Gaps
- No citable primary source found in this pass for ART-vs-JVM semantic divergences beyond JNI/hidden-API (e.g., exact differences in verification strictness, exception table handling, or Kotlin metadata version skew). AOSP runtime docs (source.android.com/docs/core/runtime) index the topics (dexpreopt, ART TI, JIT config, GC) but the landing page fetch returned nav only — [Source](https://source.android.com/docs/core/runtime).
- Could not quantify what fraction of Play apps use hidden APIs; treat as "common enough to matter" (Google maintains a full restriction list) without a number.

## What NDK ABI coverage matters (arm64 vs x86_64, 16KB pages, device features)?

### Takeaway
arm64-v8a is the must-have ABI (Play's 64-bit mandate; UE requires 64-bit Arm and drops 32-bit), x86_64 is needed only for the long tail of apps that ship it, and 16 KB page alignment is now a hard install/run gate for apps targeting API 35+ — a JVM runner executing translated native code or bundling .so shims must honor it.

### Cited Findings
- Play's 64-bit requirement centers on shipping arm64-v8a and/or x86_64 .so files alongside 32-bit ones (lib/arm64-v8a is the 64-bit ARM folder, lib/x86_64 the 64-bit x86 folder); build docs cover enabling them via abiFilters, -DANDROID_ABI, or APP_ABI — [Source](https://developer.android.com/google/play/requirements/64-bit)
- UE requires a 64-bit Arm CPU on Android 8+, and UE 5.6 supports both 4 KB and 16 KB page sizes — [Source](https://dev.epicgames.com/documentation/en-us/unreal-engine/android-development-requirements-for-unreal-engine)
- Beginning with Android 15 (API 35), apps targeting API 35+ must support 16 KB page sizes with proper 16 KB ELF alignment (LOAD segments on separate 16 KB pages); AOSP supports 16 KB pages from Android 15, Pixel 9 series runs 16 KB pages (15 QPR2+), Google ships experimental 16 KB system images (ARM 64 v8a + x86_64 Atom), and a check_elf_alignment.sh script exists for verification — [Source](https://developer.android.com/guide/practices/page-sizes)
- NDK docs: ndk-build targets all non-deprecated ABIs by default (APP_ABI can restrict, e.g. armeabi-v7a + x86_64); the old armeabi and 32/64-bit MIPS ABIs were removed — [Source](https://developer.android.com/ndk/guides/abis)

### Inferences
- For a host-side JVM runner on x86_64 Linux, the practical NDK story is: (a) most games ship arm64-v8a — running their native code requires either binary translation/emulation (slow, out of scope for "standard solutions") or hoping for an x86_64 slice in the fat APK/AAB; (b) therefore the runner should prefer APK splits containing x86_64 .so files when available and clearly document arm-only native games as unsupported without emulation; (c) any .so the runner itself loads/patches must be 16 KB-aligned to run on API-35-targeting flows.
- Device-feature gating (`<uses-feature android:glEsVersion>`, camera/AR/GPS declarations) is a Play-filtering concern: the runner's declared feature set determines which APK splits/variants it can plausibly install, so the reimplemented android.* should report a coherent feature/GL-version profile (e.g., GLES 3.2 + Vulkan 1.1 per engine requirements above) rather than zeros.

### Gaps
- No verified numbers on x86_64-slice availability among top Play games (ChromeOS/Play-on-PC pressure suggests major engines ship x86_64, but uncited — do not quote a share).
- 16 KB enforcement mechanics on the host side (whether the JVM runner must align its own mappings or only bundled .so files) need a design decision; the cited page covers Android devices, and host-Linux page-size behavior (4 KB vs 16 KB/64 KB kernels) is a separate, unresearched question.
