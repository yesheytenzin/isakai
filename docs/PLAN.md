# Isakai delivery plan

## Goal

Run three pinned Android utility apps as ordinary, persistent Linux windows:
complete useful tasks, retain data across restarts, navigate screens, and run
under the default isolation path. Broad Android compatibility is a later goal.

[progress.json](../progress.json) is the **only progress tracker**: task status,
blockers, app versions, verification evidence and historical records live there.
This plan defines scope, dependencies and acceptance criteria; it carries no
parallel completion checklist. See [testing](TESTING.md) for commands and
[architecture](ARCHITECTURE.md) for implementation locations.

## Working rules

- Work from real app failures; class count, compilation and headless exit zero
  do not establish application compatibility.
- Fix lifecycle, dispatch and isolation before expanding ABI or hardware scope.
- Preserve existing installer, translator, Wayland, storage and bridge work.
  Add a focused regression for each behavioral fix and prove the affected flow.
- Treat missing capabilities explicitly. Fake coordinates, immediate delayed
  callbacks and success-shaped no-ops are not acceptable production substitutes.
- Reuse a suitable host backend when it reduces maintenance, after a small
  feasibility test. Avoid reimplementing an entire browser or text stack.
- Keep changes small enough to verify. Size labels are relative engineering
  scope (S/M/L/XL), not time estimates or delivery promises.

## Acceptance scenarios

The starting candidates are OpenCalc, Simple Calculator and Simple Notes.
Exact APK versions and hashes must be pinned in the tracker before acceptance.
Substitute a candidate only with a recorded reason; do not replace a failing app
merely to turn the gate green.

| Candidate | Required visible task |
|---|---|
| OpenCalc | Enter a calculation, verify the result, copy it, resize, close cleanly. |
| Simple Calculator | Calculate, navigate settings and back, change an applicable preference, restart and check persistence. |
| Simple Notes | Create and edit a Unicode note, save, close, reopen, verify contents and navigate back from editing. |

Every app must remain usable beyond 30 seconds and pass under default
confinement. Headless startup remains a smoke check. Screenshots must accompany
asserted actions and saved results; a window screenshot alone is insufficient.

## Delivery sequence

### 1. Restore a reliable launch path

R1, R2 and R3 can be developed independently; R4 proves their combined result.

#### R1 — Remove the production window timeout

**Priority:** P0. **Size:** S. **Depends on:** none.

Replace the five-second deadline with a user-controlled window lifetime. Keep bounded execution in the test harness, not the production activity loop.

**Acceptance:** A window remains responsive beyond 30 seconds; close/back exits cleanly, runs lifecycle cleanup once, and leaves no window or child process.

**Code:** `framework/android/app/Activity.java`.

#### R2 — Repair and prove confinement

**Priority:** P0. **Size:** M. **Depends on:** none.

Separate Landlock version probing from ruleset creation: null/zero arguments with VERSION for the probe, attributes with flags=0 for creation; never close the returned ABI version as an fd. Audit runtime/library paths, broker/stage access, mount ordering, subprocess needs and environment construction. Preserve isolation instead of broadly allowing the host filesystem.

**Acceptance:** Default privileged launch succeeds with confinement enabled. An app-side probe proves permitted data writes, denied code and outside-data writes, denied access to another app’s data, expected UID/GID, NNP, seccomp and a separate mount namespace. Include a live Landlock regression.

**Code:** `src/confine.zig`, `src/uid_helper.zig`.

#### R3 — Verify policy packaging and clean installation

**Priority:** P0. **Size:** S. **Depends on:** none.

Install the policy bundle containing all three actions and their wrappers in each packaging path; remove owned policies on uninstall and migrate the legacy filename. Verify DESTDIR is applied only to the FHS installation, not again to the private Zig staging build. The earlier audit mistook one policy file for one action: the original XML already contained all three actions.

**Acceptance:** Inspect a clean staging root for exactly the three intended action IDs and matching executable wrappers; verify legacy policy migration, Debian/Arch mappings and package cleanup. One policy file may define all three actions.

**Code:** `Makefile`, `packaging/debian/isakai.install`, `packaging/arch/PKGBUILD`, `polkit`.

#### R4 — Make privileged E2E failures authoritative

**Priority:** P0. **Size:** M. **Depends on:** R1, R2, R3.

Use unique temporary work and fixtures with a known package ID. Explicitly select privileged mode, validate noninteractive elevation, propagate child failures, distinguish skip from pass, and assert a real surface and confinement. Do not accept a lifecycle log emitted before the window opens as proof.

**Acceptance:** A deliberately broken launch or isolation check makes the job fail; no prerequisite produces SKIP locally and failure on the required runner; the valid install/window/input/close/uninstall flow passes and cleans up.

**Code:** `tools/e2e-privileged.sh`.

### 2. Establish reproducible verification

Start Q1 alongside runtime work. Q2 and Q3 are bounded cleanup, not substitutes for real-app acceptance.

#### Q1 — Make local checks and CI reproducible

**Priority:** P1. **Size:** M. **Depends on:** none.

Pin the exact Zig build, JDK and oracle dependency checksums; add dependency bootstrap and clear prerequisite failures. Wire Java behavioral tests and bionic shim tests into explicit targets. Add CI for compile/unit/oracle and a required privileged Wayland runner; distinguish cached checks and skipped checks.

**Acceptance:** A fresh checkout runs documented checks without relying on /tmp leftovers. CI runs Java behaviors and native shim tests, exposes real counts, retains artifacts and fails when required privileged coverage is skipped.

**Code:** `build.zig`, `Makefile`, `oracle/diff.sh`, `docs/TESTING.md`.

#### Q2 — Align APK limits and test ABI selection

**Priority:** P2. **Size:** S. **Depends on:** none.

Make file inspection and hashing limits consistent; retain separate per-entry and expanded-total bounds. Stream hashing if needed. Keep multi-ABI APKs usable when a suitable x86_64 set exists: staging already excludes non-host paths. Diagnose missing host dependencies rather than rejecting every mixed-ABI APK.

**Acceptance:** Boundary cases around 256 MiB and 1 GiB behave as documented; oversized entries reject. A mixed-ABI fixture stages only host libraries; ARM-only input reports NativeUnsupported.

**Code:** `src/install.zig`.

#### Q3 — Automate API catalog and build inventory checks

**Priority:** P2. **Size:** S. **Depends on:** none.

Remove duplicate source arguments, establish a reproducible catalog generator and keep behavior caveats separate from extracted javadocs. Add doc-link and tracker-ID checks so deleted plans cannot silently return through references.

**Acceptance:** Regeneration is deterministic, sources are listed once, and generated descriptions cannot claim a renderer or hardware backend solely because a class exists.

**Code:** `build.zig`, `docs/SUPPORTED_APIS.md`, `tools`.

### 3. Make three apps genuinely usable

Pin apps early, then fix the first observed blockers. Prioritize changes that unblock multiple flows.

#### C1 — Pin three target apps and useful flows

**Priority:** P1. **Size:** S. **Depends on:** none.

Start with OpenCalc, Simple Calculator and Simple Notes as candidates. Record exact version, package, source URL, SHA-256 and license before testing. Choose the first target from measured failures; do not call an app supported because headless startup exits zero.

**Acceptance:** All three app records have immutable APK identity and repeatable visible interaction plus persistence assertions. At least one includes internal screen navigation and one includes Unicode editing.

**Code:** `progress.json`, `tools`.

#### C2 — Add APK compatibility diagnostics

**Priority:** P1. **Size:** M. **Depends on:** C1.

Report ABI selection, unsupported DEX instructions, unresolved classes/methods and first runtime failure. Distinguish implemented behavior, partial behavior, shells and unavailable capabilities; log unsupported calls once per call site without dumping private app data.

**Acceptance:** Each pinned APK yields a machine-readable report; seeded ABI, missing-method and unsupported-opcode failures identify the relevant class/method or library and a useful next action.

**Code:** `tools/inspect-test.zig`, `tools/translate-test.zig`, `src/launch.zig`, `framework`.

#### C3 — Implement the main-thread message queue

**Priority:** P1. **Size:** L. **Depends on:** C1.

Give each looper a stable queue and owning thread. Implement queued dispatch, monotonic delays, cancellation and message ordering; integrate main-loop pumping with window events and shutdown. A singleton MessageQueue alone does not fix dispatch semantics.

**Acceptance:** Worker-posted main callbacks run on the UI thread; delayed callbacks do not run early; cancellation works; self-rescheduling callbacks do not recurse; queue identity is stable and shutdown terminates cleanly.

**Code:** `framework/android/os/Handler.java`, `framework/android/os/Looper.java`, `framework/android/os/Message.java`, `framework/android/app/Activity.java`.

#### C4 — Support in-app activity navigation

**Priority:** P1. **Size:** L. **Depends on:** C1, C3.

Implement explicit activity resolution, a minimal activity stack, intent extras, result delivery, back/finish and lifecycle transitions needed by the targets. Determine required manifest metadata before extending its parser.

**Acceptance:** A target opens a second screen, passes data, returns a result, and navigates back without destroying the app unexpectedly or duplicating lifecycle calls.

**Code:** `framework/android/app/Activity.java`, `framework/android/content/Context.java`, `framework/org/isakai/runtime/IsakaiMain.java`, `src/install.zig`.

#### C5 — Make target layouts and text usable

**Priority:** P1. **Size:** L. **Depends on:** C1, C3.

Fix layout rules, resource/theme selection and drawing based on target failures. Add Unicode-capable text measurement/rendering and the editing, selection, clipboard and IME behavior the selected flows require. Evaluate an existing host text backend before expanding the ASCII rasterizer.

**Acceptance:** Target screenshots have usable bounds and no overlapping controls; input, cursor movement, copy/paste and saved Unicode text match expectations after resizing and reopening.

**Code:** `framework/android/widget/RelativeLayout.java`, `framework/android/graphics/BitmapFont.java`, `framework/android/widget/EditText.java`, `framework/android/content/res`, `framework/android/view/LayoutInflater.java`.

#### C6 — Close measured translator and framework failures

**Priority:** P1. **Size:** L. **Depends on:** C1, C2.

Work from the first blocking failure in the pinned app flows. Add minimal regression cases for type-flow merges, exceptions, interfaces, reflection/annotations and newer instructions when required. Do not equate DEX version parsing or successful class emission with execution compatibility.

**Acceptance:** Each fixed failure has a focused regression and the affected real flow passes. Differential oracle passes; unresolved failures stay explicit rather than becoming silent default returns.

**Code:** `src/dex.zig`, `src/translate.zig`, `framework`, `oracle/corpus`.

#### C7 — Pass the three-app acceptance gate

**Priority:** P1. **Size:** M. **Depends on:** R4, C3, C4, C5, C6, Q1.

Run pinned real APKs through visible core tasks, restart/persistence and default confinement. Capture commands, environment, logs and screenshots, then record evidence in progress.json.

**Acceptance:** All three apps complete their defined flows with expected saved results, remain usable beyond 30 seconds, and exit cleanly. Upgrade/rollback preserves data and a changed signer is rejected.

**Code:** `tools`, `progress.json`.

### 4. Expand only when target apps require it

Deferred work is retained in the tracker. Promote a task only with an app-driven reason and an explicit acceptance gate.

#### F1 — Harden media for a selected app

**Priority:** P2. **Size:** L. **Depends on:** none.

Activate when a target needs media. Implement bounded streaming, real loop/pause/seek behavior, content URI resolution, concurrent stdout/stderr draining, timeout and subprocess cleanup. Deliver callbacks through the proper looper; verify audio and decoder access under confinement.

**Acceptance:** Long audio plays with bounded memory; looping repeats; pause/seek/resume work; a hung decoder times out; errors propagate; the same flow passes under default isolation.

**Code:** `framework/android/media/MediaPlayer.java`, `framework/org/isakai/bridge/IsakaiMedia.java`.

#### F2 — Integrate a real WebView engine

**Priority:** P2. **Size:** XL. **Depends on:** none.

Activate for a named web-dependent app. Prototype an isolated host engine with rendering, input, navigation, JavaScript, cookies and origin-aware bridge behavior. Evaluate engine compatibility and licensing before selecting it; installing webkit2gtk alone does not enable this code.

**Acceptance:** A pinned app renders and interacts with its required page, executes general JavaScript, persists required cookies and handles engine failure without crashing the app. Test bridge exposure and origin boundaries.

**Code:** `framework/android/webkit`.

#### F3 — Prove ARM native execution before broad integration

**Priority:** P2. **Size:** XL. **Depends on:** none.

Activate only when a selected app is blocked by ARM-only libraries. First validate native ABI/loader compatibility with an actual NDK-built JNI fixture. A whole aarch64 JVM under qemu-user is an experiment, not an approved implementation: account for guest libc/linker, JNI bridge, cross-built host libraries, confinement and packaging. Avoid split-process JNI and proprietary translator hooks until a feasibility result justifies them.

**Acceptance:** A real ARM JNI APK installs, loads dependencies, calls Java/native in both directions and completes a visible confined flow; report latency, RSS and distribution size before committing to the backend. Defer ARM32 separately.

**Code:** `src/install.zig`, `src/launch.zig`, `src/native/bionic_shim.zig`, `src/confine.zig`.

#### F4 — Expose honest sensors and location capabilities

**Priority:** P2. **Size:** M. **Depends on:** none.

Keep simulated values behind explicit test configuration. Report unavailable hardware honestly. Add sensor-proxy/GeoClue integration with permissions, threading, cancellation and lifecycle only when a target needs it.

**Acceptance:** No fixed location is presented as a real reading; unavailable providers are reported; enabled backend reads reflect the host and stop after cancellation.

**Code:** `framework/org/isakai/bridge/IsakaiSensor.java`, `framework/org/isakai/bridge/IsakaiLocation.java`.

#### F5 — Expand native, hardware and signature coverage on demand

**Priority:** P3. **Size:** L. **Depends on:** none.

Use pinned APK failures to prioritize bionic ABI correctness and native dependency loading, codecs/video/capture, camera, Bluetooth/NFC/USB/telephony, background components, signature schemes/key algorithms and signing rotation. Record unsupported Google services, DRM or integrity-dependent flows as compatibility boundaries rather than promising support.

**Acceptance:** For each activated capability, record the motivating app, a bounded design, failure behavior, dedicated regressions and a real-app acceptance flow before implementation.

**Code:** `src/native/bionic_shim.zig`, `src/apksig.zig`, `framework`.

## Maintaining the tracker

Task statuses are `pending`, `in_progress`, `blocked`, `done`, or `deferred`.
Set `done` only when the acceptance criteria have evidence. Each evidence entry
should identify the commit, command/flow, environment, outcome and artifact
location; label cached checks, simulations and skips explicitly. Keep at most
one primary next task, with dependencies recorded by stable task ID.

Historical M0–M6 records are retained inside the tracker for provenance. Their
old completion claims do not override newly discovered blockers. Update only
`progress.json` for progress; edit this plan when scope or acceptance changes.
The documentation consolidation itself is tracked as D1.

The host poll pump can remain until measured latency or correctness requires
another event mechanism. JNI upcalls, epoll/timerfd/eventfd, a StackMapTable
emitter and ARM32 are not prerequisites for the three-app gate.
