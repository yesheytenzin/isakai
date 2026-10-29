# Play Store App Compatibility Layers

Objective: which layers determine whether arbitrary Play apps run on Linux (x86_64 + ARM64), comparing Waydroid, Android-x86/Bliss, ChromeOS ARCVM, and microG. Ranked by how many apps each layer unlocks. Current as of October 2026; shipped behavior only.

## Full-system containers vs API reimplementation: where the compat comes from, and why each project chose its shape

### Takeaway
Nearly all broad Play Store compatibility comes from running a full Android system (container, VM, or bare metal) — API reimplementation (microG) only covers the Google-services slice on top of an already-complete Android. Every project that targets "arbitrary Play apps" ships a full Android userspace; nobody attempts framework-API reimplementation as the base layer.

### Cited Findings
- Waydroid "uses Linux namespaces (user, pid, uts, net, mount, ipc) to run a full Android system in a container" with "direct access to needed hardware", shipping "a minimal customized Android system image based on LineageOS", currently Android 13 — [Waydroid docs](https://docs.waydro.id/)
- Same architecture restated in the project README: container boots "a full Android system on a regular GNU/Linux system" — [Waydroid README](https://github.com/waydroid/waydroid)
- ChromeOS "run[s] a full version of the Android framework" and brought "the Google Play Store to ChromeOS" in 2016 so "the same apps that run on phones and tablets run on Chromebooks" — [ChromeOS.dev Android](https://chromeos.dev/en/android)
- ChromeOS runs Android apps as ARCVM (Android in a crosvm VM); the Chromium containers FAQ lists "Are Android apps (ARCVM) going away?" among supported-VM questions, confirming ARCVM is VM-based and distinct from the older container approach — [ChromiumOS containers/VMs](https://www.chromium.org/chromium-os/developer-library/guides/containers/containers-and-vms/)
- microG GmsCore is explicitly "a free software reimplementation of Google's Play Services" that "allows applications calling proprietary Google APIs to run on AOSP-based ROMs like LineageOS" — i.e. it presupposes a full AOSP system underneath and only replaces the GMS slice — [microG GmsCore wiki](https://github.com/microg/GmsCore/wiki)
- microG requires "a ROM that supports signature spoofing so GmsCore can pretend the existence of the official Play Services"; stock LineageOS only added microG-scoped spoofing in builds from 2024-02-26 — [microG Signature-Spoofing wiki](https://github.com/microg/GmsCore/wiki/Signature-Spoofing)
- Android-x86 is a full Android OS ported to x86 hardware (native boot/install project with kernel work, not a compatibility layer) — its documentation index is organized around installation, kernel customization, and hardware enablement (graphics, audio, camera, battery) — [Android-x86 docs](https://www.android-x86.org/documentation.html)
- Bliss OS (Android-x86 derivative) status as of 2026: "Public images are paused. The source is not" — full-OS-image projects depend on volunteer release engineering, which is a fragility of the bundled-image shape — [Bliss OS](https://blissos.org/)

### Inferences
- Rough split: ~90% of compatibility comes from the full-system layer (real ART, real framework services, real WebView, real media stack); microG-style API reimplementation covers only the GMS-dependent tail (~most apps nominally depend on GMS, but only a subset hard-fail without it — see next section). No project chose framework reimplementation because the Android framework surface (tens of thousands of APIs plus behavioral quirks apps depend on) is uneconomical to clone — even microG scopes itself to the GMS IPC surface.
- Shape choices follow distribution constraints, not technical superiority: Waydroid chose LXC + LineageOS image + host Mesa to avoid shipping a kernel and to reuse the host GPU stack; Android-x86/Bliss chose native boot for bare-metal performance at the cost of owning drivers; ChromeOS chose a VM (ARCVM) for the security boundary ChromeOS requires between host and Android; microG chose reimplementation because its goal is de-Googling phones that already run Android, not running Android on Linux.
- For a no-bundled-Android-image host (Isakai's preference): every shipped high-compat solution contradicts that preference — all of them bundle a full Android userspace. API-level reimplementation (the only image-free shape) tops out at microG's coverage and still needs AOSP underneath, so it is not a viable image-free path on a plain Linux host.

### Gaps
- No reliable public per-project "X% of Play apps run" figures found; the ranking below is inferred from subsystem-failure data, not measured app-census statistics.
- I found no primary source quantifying Waydroid's app-compat rate vs Android-x86's; both are anecdotal/community-reported.

## Which subsystems cause the most app failures in the wild (GMS, WebView, media codecs, GPU, sensors, SafetyNet/Play Integrity, NDK ABIs)

### Takeaway
Ranked by apps unlocked: (1) GMS/Play Services presence, (2) NDK ABI coverage (ARM translation on x86_64), (3) Play Integrity/SafetyNet attestation, (4) GPU/rendering, (5) WebView, (6) media codecs/DRM, (7) sensors/location. GMS absence is the single largest hard-failure source; attestation is the hardest to fix without Google's keys.

### Cited Findings
- microG's own problem-app tracker is dominated by two failure modes: Maps API rendering failures (Uber, Lyft, free2move, N26 map screens) and missing push-notification registration (N26, Threema, Prism, Messenger Lite) — [microG Problem-Apps wiki](https://github.com/microg/GmsCore/wiki/Problem-Apps)
- microG implementation status (shipped): Full — Drive, exposure notifications, FCM/push, fused location, Maps v1; Mostly — FIDO2, Maps v2 (Mapbox build), Play Asset Delivery, Play Integrity/SafetyNet, SMS retriever; Partial — account auth, Cast, Games, vision (barcode only); None — Auto, fitness, fonts, geofencing, in-app updates, Nearby, Wear — [microG Implementation-Status wiki](https://github.com/microg/GmsCore/wiki/Implementation-Status)
- microG is actively maintained as of 2026: v0.3.17.252432 released 2026-09-29 with DroidGuard and Play Integrity interface-definition fixes — [microG release notes](https://github.com/microg/GmsCore/releases/tag/v0.3.17.252432)
- Waydroid with GMS shows the uncertified-device path: first launch with GAPPS notifies the device "is not certified for Google Play Protect" and the user must self-register the Android ID at google.com/android/uncertified — i.e. Waydroid ships real GMS but without Google certification, so attestation-dependent apps remain gated — [Waydroid Play certification FAQ](https://docs.waydro.id/faq/google-play-certification)
- ARM-native apps do not work in stock Waydroid: ARM translation (libhoudini/libndk) is provided only by a third-party community script (@casualsnek's waydroid_script), extracting the libs from Google emulator images via @sickcodes' extractor — not bundled, not officially supported — [Waydroid community projects FAQ](https://docs.waydro.id/faq/community-projects-we-like)
- Waydroid GPU goes through the host Mesa stack (vendor images bundle Mesa GPU drivers; HALIUM builds strip them), with a software-rendering fallback via `ro.hardware.egl=swiftshader` — so GPU compat is host-Mesa compat, and VMs need explicit fallback config — [Waydroid docs via Context7](https://docs.waydro.id/faq/get-waydroid-to-work-through-a-vm)
- Google's post-November-2024 signature-check changes broke older spoofing patches: microG notes patchers "still work properly only on Android < 9" and even the 2018 Signature Spoofing Checker "cannot check for the latest changes to the signature checking code made by Google, starting from 2024-11" — the GMS-compat surface is a moving target Google can shift at will — [microG Signature-Spoofing wiki](https://github.com/microg/GmsCore/wiki/Signature-Spoofing)

### Inferences
- Layer ranking by apps unlocked (inferred, no census data — see Gaps): (1) real GMS or equivalent unlocks the largest single block (push, maps, auth, games services — the failure modes dominating microG's tracker); (2) ARM translation on x86_64 unlocks the next block (a large share of games ship ARM-only .so files; stock Waydroid without the community script simply cannot load them); (3) Play Integrity/SafetyNet is a smaller but nearly unfixable block (banking, DRM-video, some games) without Google-signed keys; (4) GPU via host Mesa covers most 2D/utility rendering but is where games fall over (shader/driver quirks, Vulkan gaps); (5) WebView mostly works wherever a full system image ships it (it fails only in stripped or reimplemented stacks); (6) media codecs/DRM: software codecs cover casual playback, Widevine L1/hardware DRM does not survive outside certified devices; (7) sensors/location cause graceful degradation (denial paths) rather than hard failures in most utility apps.
- microG's "Mostly" on Play Integrity/SafetyNet should be read skeptically: the status table marks API presence, while actual verdict-passing depends on server-side DroidGuard/attestation that Google actively hardens (cf. the 2024-11 signature-check changes); the Sept 2026 release still lists DroidGuard/Integrity as fix-in-progress areas.

### Gaps
- No reliable source found on what fraction of top-1000 Play apps hard-require SafetyNet/Play Integrity vs merely calling GMS APIs; estimates vary wildly and I report none.
- No citable primary source found for ChromeOS's ARM-on-x86 bridge specifics (Intel Houdini licensing) or Widevine level on Waydroid (expected L3-only, unverified) — kept out of findings.
- WebView and media-codec failure rates on Waydroid/Android-x86 rest on community anecdote; no systematic data found.

## What ChromeOS ARC does differently that lets it pass app compatibility at scale

### Takeaway
ARC's advantage is institutional, not architectural cleverness: Google ships it, so it carries certification, real GMS with valid attestation keys, Play Store, and CTS-tested framework behavior. Technically it is the same "full Android in a box" shape as Waydroid (container historically, VM now) with bridged GPU/input/sensors — the difference is that every hard layer (attestation, DRM, NDK bridge, GMS) is licensed and signed.

### Cited Findings
- ARC runs "a full version of the Android framework" with the Play Store built in, and Google tells developers their "single Android APK" scales to Chromebooks — app-compat work is pushed to developers via large-screen/input/window guidelines, not handled by translation layers — [ChromeOS.dev Android](https://chromeos.dev/en/android)
- ARCVM runs Android inside crosvm virtual machines managed by ChromeOS lifecycle daemons (Concierge/Maitred), i.e. a hardware-isolated VM boundary rather than Waydroid's namespace container — [ChromiumOS containers/VMs](https://www.chromium.org/chromium-os/developer-library/guides/containers/containers-and-vms/)
- ChromeOS publishes per-subsystem porting docs (window management, rendering differences, input compatibility, camera orientations, manifest compatibility) treating remaining incompatibilities as app-side adaptations for landscape/x86/windowed devices — the platform-educates-developers model, only available to a platform owner — [ChromeOS.dev Android section](https://chromeos.dev/en/android)

### Inferences
- ARC passes at scale because of four things an independent Linux project cannot replicate: (a) Google-signed builds that pass Play Integrity/SafetyNet and Widevine L1 server-side; (b) CTS/GTS-tested framework behavior so apps' implicit API assumptions hold; (c) licensed ARM-translation and codec stacks; (d) developer leverage — Play Console flags and docs push developers to fix large-screen/x86 issues. Architecture (VM vs container) is secondary: ARC++ was a container and also passed, because certification traveled with it.
- The lesson for Linux hosts: the compat ceiling for any uncertified approach is "everything except attestation/DRM-gated apps." Waydroid's uncertified-device self-registration flow is the visible boundary of that ceiling — basic Play Services work, but server-gated verdicts do not.

### Gaps
- I could not verify from a primary source the exact ARCVM Android version cadence vs AOSP or the ARM-bridge implementation on x86 Chromebooks; omitted rather than asserted.
- Whether ARCVM passes Play Integrity *strong* verdicts (hardware-backed) on all vs some Chromebook models is unverified.

## Minimum viable layer set for casual games plus utility apps (the bulk of Play installs)

### Takeaway
For casual 2D games + utility apps (no banking, no HD DRM video, no competitive anti-cheat): full Android userspace + working GMS API surface (real GMS or microG at "Full/Mostly" APIs) + GPU compositing through host GL with software fallback + ARM translation on x86_64. Sensors, location, and push can degrade to denial/unavailable without blocking this segment.

### Cited Findings
- The utility-app failure modes in microG's tracker (maps display, push registration) map exactly to GMS APIs microG now marks Full (FCM, fused location, Maps v1) or Mostly (Maps v2 Mapbox build) — i.e. the utility segment is coverable without real GMS — [microG Implementation-Status](https://github.com/microg/GmsCore/wiki/Implementation-Status) and [Problem-Apps](https://github.com/microg/GmsCore/wiki/Problem-Apps)
- API gaps that remain (Auto, Wear, fitness, geofencing, in-app updates, Nearby) fall almost entirely outside the casual-game/utility segment — [microG Implementation-Status](https://github.com/microg/GmsCore/wiki/Implementation-Status)
- Casual-game blockers cluster in the two layers microG cannot supply: ARM-native game engines need libndk/libhoudini-style translation (unofficial add-on in Waydroid, not bundled) and GPU rendering needs host Mesa or swiftshader fallback — [Waydroid community projects](https://docs.waydro.id/faq/community-projects-we-like), [Waydroid VM GPU FAQ](https://docs.waydro.id/faq/get-waydroid-to-work-through-a-vm)
- Attestation-gated apps (banking, some DRM/anti-cheat games) sit outside the viable set for uncertified stacks: Waydroid's own docs route users through manual uncertified-device registration, which restores Play Services access but not certified-device verdicts — [Waydroid Play certification FAQ](https://docs.waydro.id/faq/google-play-certification)

### Inferences
- Minimum viable set, in unlock order: (1) complete framework/ART (no reimplementation — this is table stakes); (2) GMS API surface at microG-"Mostly" level or better (push, maps, auth basics) — real GMS preferred where licensable, microG acceptable for this segment; (3) ARM translation on x86_64 (non-negotiable for games; on ARM64 hosts this layer drops out entirely, which is a strong argument for ARM64-first); (4) GPU path with graceful software fallback (utility apps survive swiftshader-class rendering; 3D games need host GL); (5) honest unavailability for sensors/location/camera (denial paths, not crashes); (6) WebView + software media codecs bundled in the image. Explicitly out of scope for the viable set: Play Integrity-passing attestation, hardware DRM, fitness/wear/auto APIs.
- Host-arch note: on ARM64 Linux, layer (3) disappears and most of layer (4) gets easier (mobile GPU drivers/ANGLE paths are better exercised on ARM SoCs), so the minimum viable set is strictly smaller on ARM64 than x86_64.

### Gaps
- "Bulk of Play installs" is asserted from general market knowledge (casual games + utilities dominate install counts); I found no citable 2026 category-share source during this pass.
- No data found on what share of casual games ship ARM-only vs x86_64-native .so files in 2026 (x86_64-native builds have grown via Chromebook targeting, which would shrink layer 3's importance — unverified either way).
