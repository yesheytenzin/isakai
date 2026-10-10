# M6 plan — closing the remaining gaps

Baseline: all M0–M5 milestones complete (`2fe1cc3` + `19635dc`). Every gap below is
intentional, fail-closed, and documented. This plan sequences them by real-world
app demand, risk, and size. Two needed web research; findings inline.

---

## Item ranking (what order and why)

| # | Milestone | Driver | Effort | Risk |
|---|-----------|--------|--------|------|
| 1 | M6a Native `.so` via bionic shim | #1 real-world blocker (games, QR, codecs) | XL | High |
| 2 | M6b MediaPlayer + media pipeline | #1 API gap in real apps (video/audio) | L | Medium |
| 3 | M6c WebView | web-heavy apps (impossible to avoid) | L | High |
| 4 | M6d Sensors/camera/location backends | fitness/AR apps | M | Low |
| 5 | M6e apksig: multi-signer + P-521 | APK-compat completeness | S | Low |
| 6 | M6f Polkit per-request message | UX polish | S | Low |
| 7 | M6g Privileged-windowed E2E | verification debt, not code | S | Low |
| — | Async epoll + JNI callbacks | **do not build** (see below) | — | — |

---

## M6a — Native code support (bionic shim) — XL, ship as M6 core

**Problem:** apps with `lib/**/*.so` are rejected at install
(`src/install.zig`, `NativeUnsupported`). JNI natives are the single
biggest class of unrunnable real apps.

**Approach: libhybris-style bionic shim (researched).** libhybris (SailfishOS,
production since 2013) proves the architecture: load bionic-linked `.so`
files from a glibc process by shipping a **bionic linker + a bionic→glibc
symbol forwarding layer**, not a syscall emulator. Bionic syscalls ≈ Linux
syscalls (both wrap the same kernel ABI); the work is symbol resolution.

  - Components: (1) embed a bionic `linker` (or `dlopen` a prebuilt
    `libhybris`/`libc_shim`); (2) a shim layer implementing the ~200
    bionic symbols `.so` files actually reference (malloc family, pthread,
    android/log, property_get, ashmem) onto glibc equivalents;
    (3) `dlopen` within the bionic namespace, `dlsym` for `JNI_OnLoad`
    and `Java_*` natives, register into the JVM via `RegisterNatives`.
  - **Scope hard limits (must enforce):** support only
    `System.loadLibrary` from a whitelist registry in `confine.zig`'s
    RO set; never load Android framework `.so`s (surfaceflinger, gralloc)
    — those need the full libhybris HW path, out of scope.
  - Conservative alt if the shim proves too big: ship the APK's `.so`s
    as data, `dlopen` them with glibc and resolve missing bionic symbols
    via a single `libc_shim.so` interposer (smaller, covers ~80% of
    simple JNI libs, e.g. sqlite-only apps).

  1. `install.zig`: accept `lib/<abi>/*.so` when abi == host arch record
     (already recorded by `uname` arch record from M4); stage `lib/` RO.
     Reject other ABIs with `NativeUnsupported` (unchanged).
  2. `src/native.zig` (new): bionic symbol shim table + namespace loader.
     Unit-test symbol table against AOSP bionic `libc.so` export list.
  3. `launch.zig`: preload shim before `System.loadLibrary` resolution;
     `ld_preload`-style env in the helper child (seccomp allowlist gains
     `memfd_create`/`mmap` variants already present).
  4. Real-APK gate: one simple game or utility with a trivial JNI lib
     (e.g. a notes app with native crypto) headless exit 0.
  5. Docs: flip `NativeUnsupported` row in SUPPORTED_APIS to
     "supported: system-ABI-only libs" with the hard limits above.

**Acceptance:** app with `lib/arm64-v8a/*.so`-equivalent x86_64 `.so`
runs; .so staging is read-only under Landlock; uninstall removes lib dir.
Cross-arch (.so for wrong arch) still rejected fail-closed.

## M6b — MediaPlayer + media pipeline — L

**Reality check:** `framework/android/media/` has shells
(`MediaExtractor/MediaFormat/MediaMetadataRetriever`) + working
SoundPool(WAV). No `MediaPlayer.java`. Apps use MediaPlayer for
music/SFX far more than camera/sensors, so this is the highest-demand gap.

  1. `MediaPlayer.java` state machine (Idle→Initialized→Prepared→
     Started/Paused/Stopped/Completed/Error) matching AOSP semantics —
     pure Java, backed by a new `src/bridge/media.zig`.
  2. `bridge/media.zig`: decode via existing PulseAudio bridge +
     **ffmpeg CLI as a subprocess** (no new native deps; ffmpeg is
     near-universal on Arch/Debian) — decode MP3/AAC/OGG/FLAC → S16LE
     PCM, reuse SoundPool's pipeline. `Surface`-less audio first.
  3. Video: `MediaMetadataRetriever.getFrameAtTime` via ffmpeg frame
     extract → existing Bitmap pipeline. Full video render defers to
     M6c's window surface work (one presentation path, not two).
  4. Tests: golden WAV in → decoded PCM equality; MediaPlayer state
     machine transitions (all 12 documented Android states); ffmpeg
     absent → `UnsupportedOperationException` fail-closed (not silent).

**Acceptance:** an app using `MediaPlayer.start()/pause()/seekTo()`
against an MP3 completes without crash; SoundPool path unchanged.

## M6c — WebView without shipping an engine — L (researched)

**Findings:** embedding a real engine is the only viable route —
three options researched:

| Option | Verdict |
|---|---|
| JCEF (Chromium Embedded Framework Java port) | Heaviest: ships a full Chromium (~100+ MB), Swing-based. Wrong shape for isakai (Wayland, no AWT). |
| SWT Browser | Pulls GTK/SWT; window integration is X11-shaped. No. |
| **webkit2gtk-4.1 via a helper process** | ✅ Chosen: system package on Arch (extra) + Debian; out-of-process so a webkit crash can't take the JVM; C API easy from Zig. |

**Design: out-of-process WebView.** A tiny `isakai-webview` helper (Zig,
webkit2gtk-4.1) speaks a small Wayland-adjacent protocol or embeds via
`wlr-layer-shell`/subsurface into the app's window. `android.webkit.*`
java classes become a thin IPC client (WebViewClient/JS-bridge callbacks
over the existing bridge socket pattern from `src/bridge/desktop.zig`).

  1. `src/bridge/webview.zig`: spawn helper, protocol = request ID +
     URL + JS-eval + load-state events (reuse `dbus.zig` message framing
     style; keep it a private socket, not D-Bus).
  2. `framework/android/webkit/`: `WebView`, `WebViewClient` (the 6
     methods apps actually override), `WebSettings` (JS enabled by
     default), `CookieManager` (in-helper cookie jar), `ValueCallback`
     bridged async via poll pump.
  3. Sandboxing: helper runs under the same seccomp/Landlock confinement;
     webkit's own sandbox adds a layer.
  4. Fallback: webkit2gtk missing → install-time is fine, launch-time
     loud `WebViewUnavailable` with `ISAKAI_WEBVIEW=0` opt-out.
  5. Gate: a WebView-using test app renders, JS→Java (`addJavascriptInterface`)
     round-trips, back/forward works.

**Acceptance:** real OSS app with WebView activity (e.g. a simple RSS
reader) renders and navigates; crash of helper ≠ crash of app
(restartable, state = URL stack).

## M6d — Sensors/camera/location — M (only if apps demand)

`SensorManager`/`CameraManager`(enumeration shell)/`LocationManager` are
empty by design. Real demand order: **sensors first** (accelerometer from
`iio-sensor-proxy` via D-Bus — bridge already speaks D-Bus,
`src/bridge/dbus.zig`), then location (GeoClue D-Bus, same pattern),
camera last (PipeWire; only if an app gate needs it).

  1. `SensorManager.getSystemSensorManager` → D-Bus `SensorProxy`
     (accelerometer/light) + fallback virtual sensors (rotation =
     display orientation; light = 0). Ships value through existing
     poll-pump input path (no new event loop — deliberately).
  2. `LocationManager`: GeoClue2 `GetClient` → `Start`, position updates
     via signals; permission denial → `SecurityException` (Android parity).
  3. Camera: keep enumeration shell; real capture only with a concrete
     app gate (pipeline.py None today).

**Acceptance:** a sensor demo app receives onSensorChanged at ~60 Hz;
location demo gets a fix (GeoClue present) or explicit `PermissionDenied`.

## M6e — apksig completeness — S (1–2 days)

Current: single-signer pinned (`src/apksig.zig:696` rejects extra
signers), RSA≤4096, P-256/P-384, P-521 → `UnsupportedAlgorithm`
(golden-rejected, `testdata/apksig/README.md:15`).

  1. **Multi-signer (recommended to add):** real apps rarely ship it, but
     the Android platform semantics are "verify all signers, then the
     caller pins one". Implement: parse signer list
     (length-prefixed already — loop instead of `pos != len → error`),
     verify each, require ≥1, expose all fingerprints; `upgrade.zig`
     continuity check extends from "the" fingerprint to "the pinned
     fingerprint ∈ set" (registry schema additive: `signer_sha256`
     stays, add `signers: [sha256]` optional). Vectors exist:
     `v2-only-two-signers.apk` is already vendored.
  2. **P-521 (add if free):** NIST P-521 = secp521r1 — existing ECDSA
     P-256/P-384 path extends with one curve addition (~30 lines:
     curve params + key-length switch in
     `verifySignature`). RSA-8192 stays rejected (pointless).
  3. Update golden rejection tests: `two-signers.apk` flips from
     `SignerFailed` to a verify pass with 2 fingerprints.

**Acceptance:** `verify-test v2-only-two-signers.apk` reports 2
fingerprints; P-521 vector verifies; `zig build test` green.

## M6f — Polkit per-request message — S (1 day)

**Research finding:** the deferred note "impossible via pkexec" is
**correct for pkexec** but the polkit D-Bus API does support dynamic
messages — the `pkexec` shortcut is what's static. Two real options:

  1. **D-Bus mechanism (the documented path):** isakai registers as the
     *mechanism*: call
     `org.freedesktop.PolicyKit1.Authority.CheckAuthorization(subject,
     {"id.isakai.allocate-uid"}, details{"package" → pkg.name}, flags,
     cancel_id)`. The action's `<message>` supports `$(package)` when
     the mechanism passes it in `details` — this is exactly what the
     deferred note said was missing ("needs a D-Bus mechanism"). Then
     the privileged operation is performed by a tiny root daemon (or
     `pkexec` on a fixed argv) after authorization — pkexec's
     `$(command_line)` compromise disappears.
     - Cost: a mechanism daemon + D-Bus policy (~1–2 days, `src/bridge/dbus.zig`
       already speaks D-Bus) + moving `uid-helper allocate/remove/run`
       behind it.
     - Payoff: the auth dialog reads "Authentication is required to
       install <Package Name>" instead of a raw command line.
  2. **Alternative if D-Bus mechanism is too heavy:** keep pkexec but
     add 2–3 split actions (`install-package`, `launch-package`,
     `remove-package`) with distinct `<message>` texts + `argv1` pins.
     Per-*request* package name still impossible, but the dialog stops
     being generic. Half a day.

Recommendation: do (2) now as a hygiene fix; (1) only if a desktop
integration milestone (app store / tray) happens.

## M6g — Privileged-windowed E2E — S, environment-gated

Not code: needs a machine with passwordless elevation. Plan:

  1. Provide `tools/e2e-privileged.sh` (sudo -n gate, polkit policy
     install check, then: uid-helper allocate → install → windowed
     launch with `--wayland-sock` → verify Hyprland client class +
     helper bind-mount worked → remove).
  2. CI: a GitHub Actions runner with passwordless sudo executes it
     (`sudo -n true` gate, skip otherwise). That also closes the
     "privileged headless E2E" debt from M4/M5.
  3. Until then the manual runbook lives in docs/TESTING.md.

---

## Suggested sequencing

| Order | Milestone | Why this order |
|---|---|---|
| 1 | **M6e** apksig (S) | small, independent, closes security surface |
| 2 | **M6f** polkit split actions (S) | small UX win, no deps |
| 3 | **M6a** native .so shim (XL) | biggest real-app blocker; start early, long runway |
| 4 | **M6b** MediaPlayer (L) | second biggest app demand; builds on audio bridge |
| 5 | **M6c** WebView (L) | biggest single dependency; needs protocol design |
| 6 | **M6d** sensors/location (M) | only after an app gate needs it |
| 7 | **M6g** privileged E2E in CI | any time a runner exists |

Rationale: 6a/6b/6c are ranked by "which apps can't run today", matching
the project's real-APK ladder methodology (Phase 3 in
limitations-fix-plan.md). M6a and M6b are independent — can run as
parallel slices once M6e/f land. M6c is the riskiest (new process,
new IPC, security surface) so it should not block on M6a.

## Sources

- libhybris approach (bionic-on-glibc loading prior art):
  https://github.com/libhybris/libhybris — "load drivers compiled for
  Android from regular linux processes"; works by hooking bionic→glibc,
  i.e. symbol interposition, not syscall emulation.
- polkit per-request messages: `CheckAuthorization` D-Bus API passes
  `details` keys into the action `<message>` interpolation:
  https://www.freedesktop.org/software/polkit/docs/latest/eggdbus-interface-org.freedesktop.PolicyKit1.Authority.html
  (pkexec itself can't; it is a fixed-argv wrapper — hence the original
  deferral, and option 2 above).
- webkit2gtk availability: Arch extra `webkit2gtk-4.1` (current), Debian
  `libwebkit2gtk-4.1-dev` — packaged everywhere isakai targets.
  https://archlinux.org/packages/extra/x86_64/webkit2gtk-4.1
