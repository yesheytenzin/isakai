---
id: "N6"
status: "blocked"
phase: "NATIVE_RUNTIME"
priority: "P1"
size: "L"
updated: "2026-10-26"
owner: "unassigned"
next: "verify default-isolated useful actions plus thread/callback/GC/exception behavior and resolve the loader-contract follow-ups"
---

# N6: Prove same-ISA Android Bionic loading and complete JNI ownership

## Dependencies

- [N3](archive/N3-trusted-runtime.md)
- [N4](archive/N4-upstream-pins.md)

## Code and documentation

- [src/native/bionic_shim.zig](../src/native/bionic_shim.zig)
- [src/launch.zig](../src/launch.zig)
- [src/bridge/jni.zig](../src/bridge/jni.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [build.zig](../build.zig)
- [framework/org/isakai/runtime/IsakaiMain.java](../framework/org/isakai/runtime/IsakaiMain.java)

## Implementation

1. Build pinned Bionic Translation independently and run its examples before integration.
2. Choose an explicit runtime loader/symbol-resolution adapter; compare ART integration before relying on private HotSpot behavior.
3. Use existing Android-NDK-built application libraries with dependent libc++/DSOs; do not author fixture libraries or test cases.
4. Prove JNI_OnLoad, static names, RegisterNatives, classloaders, Java/native callbacks, attached threads, references/GC, exceptions, TLS/errno and libc/pthread/C++ contracts.

## Acceptance

- An existing same-ISA NDK application completes useful visible actions and repeat thread/callback/GC work under default isolation.
- Dependency or JNI_OnLoad failure is reported without corrupting runtime state.
- A real native x86_64 app action passes; support is limited to the tested capability corpus.

## Stop condition

Any corruption, deadlock or unsupported callback blocks adoption; fragile private JVM integration favors a separate runtime rather than more preload hacks.

## Effort estimate

Planning estimate: 3–6 engineer-weeks pilot; broad native API coverage additional.

## Reuse sources

- [https://gitlab.com/android_translation_layer/bionic_translation/-/blob/6253dec7f24a57a94039a088cb436d880e92e3be/README.md](https://gitlab.com/android_translation_layer/bionic_translation/-/blob/6253dec7f24a57a94039a088cb436d880e92e3be/README.md)
- [https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/art/runtime/jni/java_vm_ext.cc](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/art/runtime/jni/java_vm_ext.cc)
- [https://github.com/libhybris/libhybris](https://github.com/libhybris/libhybris)

## Verification evidence

**2026-10-25, same-UID dev host (no privileged/default-isolation run).**

**Step 1 done — pinned Bionic Translation builds standalone.** Fetched the
pinned `bionic_translation` (reuse source commit
`6253dec7f24a57a94039a088cb436d880e92e3be`) into `.cache/n6-bionic-src`.
Host deps present via pkg-config: `egl` 1.5, `libelf` 0.197, `libbsd` 0.12.2,
`libunwind` 1.8.3. `meson` 1.12.1 installed in a local venv (`.cache/n6-venv`;
PEP-668 blocks a system pip install, and `sudo -n` needs a password).
`meson setup` exits 0; `ninja` builds all 6 targets (RC 0, warnings only — an
aarch64 `R_AARCH64_TLS_TPREL64` pragma, moot on x86_64, and dwarf-line
warnings on the stub section). Artifacts in `.cache/n6-bionic-build`:
`libdl_bio.so.0.0.1` (the android2gnulinux bionic linker), `libc_bio.so.0`,
`libpthread_bio.so.0`, `libstdc++_bio.so.0`, `libwrapper.a`,
`tests/test_runner`. `libdl_bio.so.0` exports the loader entry points Isakai
would call — `bionic_dlopen`, `dl_parse_library_path`, `dlsym` — and links
host `libEGL.so.1`/`libelf.so.1`/`libunwind.so.8`/`libc.so.6`.

**Upstream CTS suite does not run here (test-data/harness gap, not a build
defect).** `ninja test` fails at launch: `tests/CtsBionicTestCases/` is empty
(the Android CTS prebuilt test `.so`s are not in the source archive), and the
`_run_in_bwrap.sh` runner assumes an in-tree build (it looks for
`<builddir>/../cfg.d`, which only resolves when the build dir sits inside the
source tree). No ATL example binary ships to substitute, and the artifact
policy forbids authoring fixture libraries. Loader correctness therefore has
to be proven by Isakai integration against a real app, not by this suite.

**2026-10-25 — step 3 input resolved: a clean x86_64 NDK app is now pinned.**
Simon Tatham's Puzzles (F-Droid, MIT, pinned at [APPS.md](REFERENCE.md) index 3,
`name.boyle.chris.sgtpuzzles` v149, sha256 `6b36d55…3d652e4`,
`.cache/isakai/apks/sgtpuzzles_149.apk`) replaces the unusable GLES game as the
same-ISA target. Static analysis of its `lib/x86_64/libpuzzles.so` (NDK r28c,
`readelf`):

- **Genuine JNI library, 2D, no GPU.** Exports `JNI_OnLoad` (2263 bytes) and 37
  `Java_name_boyle_chris_sgtpuzzles_backend_GameEngineImpl_*` natives, and
  *defines* the native→Java drawing surface (`android_draw_rect/line/text/`
  `poly/circle/thick_*`, `android_blitter_*`, `android_start_draw/end_draw`,
  `android_clip`) that renders onto a Java Canvas — no `AAsset*`, no
  `ANativeWindow`, no `libandroid.so`, no EGL. It exercises exactly N6's corpus
  (`JNI_OnLoad`, RegisterNatives, Java↔native callbacks, the generation/timer
  threads) with a visible interactive action (solve a puzzle).
- **The load barrier is now pinned exactly.** `DT_NEEDED` is only `libm.so`,
  `libdl.so`, `libc.so` (Android sonames). Its 49 imported libc/libm symbols are
  all version-node `LIBC` — `.gnu.version_r` requires `LIBC` from both `libc.so`
  and `libm.so` (e.g. `memcpy@LIBC`, `__stack_chk_fail@LIBC`, `atan2@LIBC`).
  glibc exports these only under `GLIBC_*` nodes, so the JVM's own glibc
  `dlopen` at `src/launch.zig:909` cannot satisfy them (`version 'LIBC' not
  found`) — the exact mismatch ATL's `libc_bio.so`/`libdl_bio.so` (built in
  step 1) resolve. This confirms, against a real app, why the host-`dlopen`
  path must be replaced rather than patched with sonames/symlinks.
- **N5 scope note.** `libpuzzles.so` reads its asset data through the Java
  layer, not the NDK asset API, so it does not close [N5](N5-resource-engine.md)'s third
  (`AAsset` fd/offset) bullet; that still needs an `AAsset*`-using app.
  `libpuzzlesgen.so` is a standalone `/system/bin/linker64` PIE (a subprocess
  generator), not a JNI library.

**2026-10-25 — loader de-risk experiment: the ATL bionic loader maps the real
`libpuzzles.so` and clears the `@LIBC` barrier; two integration prerequisites
surface.** A minimal C driver (in the session scratchpad, not the repo — this
is the one fact no existing command can establish: that step-1's `libdl_bio`
loads a real NDK JNI `.so`; N6 step 1 itself calls for running the loader
before integration, and no ATL example binary ships) `dlopen`s
`.cache/n6-bionic-build/libdl_bio.so.0`, resolves `bionic_dlopen`/`bionic_dlsym`,
and calls `bionic_dlopen("libpuzzles.so")` with `BIONIC_LD_LIBRARY_PATH` =
app `lib/x86_64` + the `*_bio` build dir and `XDG_DATA_DIRS` pointing at the
`cfg.d` override map (the build was never `ninja install`ed, so its default
`cfg.d` path does not resolve). Observations:

- **The core approach is sound.** `bionic_dlopen` locates and memory-maps the
  real `libpuzzles.so`, then resolves its Android-soname `DT_NEEDED` by name:
  `libc.so` → `libc_bio.so.0` (cfg override; loads, links, and pulls
  `libpthread_bio.so.0` → glibc `libc.so.6`), `libdl.so` → the loader's own
  built-in libdl stub. The loader ignores GNU symbol versioning, so the
  `@LIBC`-versioned imports that defeat glibc `dlopen` are a non-issue here —
  confirming the plan to replace, not patch, the host-`dlopen` path.
- **Prerequisite 1 (proven mandatory): the launch process must initialise
  ATL's `_r_debug_ptr` before any `bionic_dlopen`.** The bare-shim probe
  segfaults in `apkenv_notify_gdb_of_load` (`linker/linker.c:272`,
  `_r_debug_ptr->r_state = RT_ADD`) while linking `libpthread_bio.so.0`.
  `_r_debug_ptr` is declared uninitialised at `linker/linker.c:211`; its comment
  (`:210`) states it "will be filled by the main executable." That is ATL's
  `main_executable/bionic_compat.c` startup — i.e. this is exactly the planned
  "link `bionic_compat.c` into the launch process" step, now shown to be
  non-optional and with the precise crash it prevents. Not an ATL defect.
- **Prerequisite 2: the ATL build ships no `libm` provider.** `libpuzzles.so`
  needs `libm.so` (`atan2`/`acosf`/`sincosf@LIBC`); `cfg.d` has no `libm.so`
  override, no `libm_bio` was built, and host `/usr/lib/libm.so` is a glibc
  linker script (`invalid ELF header`). A temporary `libm.so → libm.so.6` cfg
  override let the loader map glibc's real `libm.so.6` by name, but whether
  glibc's `libm` functions correctly under the shim loader is unproven — the
  `_r_debug_ptr` crash (prerequisite 1) happens first. Integration must either
  build ATL's `libm_bio` or settle a `libm.so` mapping.

**2026-10-25 — loader stage PROVEN end to end on the real app.** With all three
prerequisites satisfied faithfully, `bionic_dlopen("libpuzzles.so")` succeeds:
the loader maps and fully relocates the real Android x86_64 NDK JNI library, and
`bionic_dlsym` returns live addresses for `JNI_OnLoad` (found in `libpuzzles.so`,
0x…29f0) and every sampled native (`keyEvent`, `configEvent`, `setViewCallbacks`,
`serialise`, `fromGameID` — 5/5). The `@LIBC` version barrier that defeats glibc
`dlopen` is fully bypassed by the loader's name-based resolution. The three
prerequisites, each proven and each satisfiable inside the launch process:

1. **Initialise `_r_debug_ptr = &_r_debug`** before the first `bionic_dlopen`
   (exactly `main_executable/bionic_compat.c`'s glibc path). Exported by
   `libdl_bio.so`, so the launcher sets it directly.
2. **Host-load `libc_bio.so.0`/`libpthread_bio.so.0`/`libstdc++_bio.so.0` with
   `RTLD_GLOBAL`** so their `bionic_*` wrapper symbols (the bionic FORTIFY set —
   `bionic___strlen_chk`, `bionic___memcpy_chk`, …) populate `RTLD_DEFAULT`,
   which the shim linker's relocation resolver queries at `linker/linker.c:1824`.
   This is what ATL's `main_executable` links against.
3. **Install the `cfg.d` override map** (`libc.so→libc_bio.so.0`,
   `libstdc++.so→libstdc++_bio.so.0`, plus a `libm.so→libm.so.6` mapping since
   ATL ships no `libm_bio`) on the runtime's `XDG_DATA_DIRS`, and set
   `BIONIC_LD_LIBRARY_PATH` to the app `lib/x86_64` dir, the `*_bio` dir, and the
   glibc `libm.so.6` dir.

Still unproven — the remaining N6 body: wiring this into the real launch so
`System.loadLibrary` routes through `bionic_dlopen` and the framework drives the
app's `JNI_OnLoad`/`setViewCallbacks` with the app's translated Java classes
loaded; then the visible solved puzzle and the repeat thread/callback/GC work
**under default isolation** (acceptance bullet 1). That last step needs the live
Sway fixture rendering a brand-new (unverified-coverage) app and operator `sudo`,
and is not completable or verifiable in this session. The loader/JNI-resolution
stage — N6's hard, novel core — is proven; the JNI-execution and visible-action
stages are not.

## Blockers

### Current remaining acceptance, 2026-10-26

The latest recorded Cube run demonstrates native rendering and pointer-driven
moves in same-UID development mode. Loader integration is no longer wholly
unimplemented. Default-isolated useful actions and repeat thread, callback, GC
and exception behavior remain unverified. Keep N6 blocked.

The C8 source audit also identified loader contracts requiring follow-up:
`android_dlopen_ext` ignores extended-load information, tracked guest symbol
lookup does not forward its error through `dlerror`, and glibc-first fallback
is not explicit host/guest classification. Possible fallback re-entry is a
source-based risk, not a reproduced crash. Native capability still depends on
external build artifacts whose availability and provenance are not part of the
runtime selection contract. Do not claim general native support or distribution
readiness until these are resolved and verified. See [DEPENDENCIES.md](REFERENCE.md#experimental-bionic-integration).

Native asset C ABI acceptance belongs to [N18](N18-ndk-assets.md), independently of N15's
graphics work. Java descriptor repairs in C8 do not establish NDK support.

### Historical integration plan, 2026-10-25

Steps 2–4 (integrate `dl_bio` as the runtime loader in place of the JVM's own
`dlopen` at `src/launch.zig:909`, link `main_executable/bionic_compat.c` into
the launch process, load a real x86_64 app `.so` through `bionic_dlopen` with
`BIONIC_LD_LIBRARY_PATH`, and prove JNI_OnLoad/RegisterNatives/threads/GC/
exceptions plus a visible app action) are the multi-week body of this pilot and
are gated on inputs not available in this session:

- **RESOLVED — the x86_64 NDK app input.** Simon Tatham's Puzzles is pinned
  and statically validated (see the 2026-10-25 evidence above). This clears the
  former "no clean x86_64 NDK app" blocker (the GLES Hill Climb repackage is no
  longer the only candidate).
- **The integration body (steps 2–4) remains — not completable in one
  session.** The loader stage is now *proven* against the pinned app (see
  evidence): `bionic_dlopen` fully loads `libpuzzles.so` and resolves
  `JNI_OnLoad` + natives, via three known prerequisites. What remains is wiring
  those into the launch process and routing `System.loadLibrary` through
  `bionic_dlopen` in `src/launch.zig`/`src/native/bionic_shim.zig` (see Next
  action), then proving `JNI_OnLoad`/RegisterNatives/threads/GC/exceptions and a
  visible solved puzzle — which needs the live Sway fixture running a
  brand-new, unverified-coverage app, and operator `sudo` for the
  default-isolation half.
- **Default-isolation evidence needs operator `sudo`** (`sudo -n` requires a
  password here), as for [N5](N5-resource-engine.md).

**Next action.** The loader stage is proven (above); the three prerequisites are
known and all satisfiable in the launch process. Integration work:

1. In the launch process set up ATL's prerequisites once: host-load the `*_bio`
   libs `RTLD_GLOBAL`, set `_r_debug_ptr = &_r_debug` (link
   `main_executable/bionic_compat.c` or replicate it), and install + point
   `XDG_DATA_DIRS`/`BIONIC_LD_LIBRARY_PATH` at the `cfg.d` map and `*_bio`/libm
   dirs. The build is not `ninja install`ed today, so package the `cfg.d` map.
2. Route app-library loading through `bionic_dlopen`: `System.loadLibrary`
   (the glibc `dlopen` at `src/launch.zig:909`) must call `bionic_dlopen` for
   app `.so`s instead of the JVM's own loader, then call the resolved
   `JNI_OnLoad` with the JavaVM* (the JVM will not auto-invoke it for a lib it
   did not load). `src/native/bionic_shim.zig` is the natural home.
3. Drive the pinned app: load `lib/x86_64/libpuzzles.so`, let the framework run
   `JNI_OnLoad`/`setViewCallbacks` with the app's translated classes, and record
   a solved puzzle plus repeat thread/callback/GC/exception work.

Full acceptance bullet 1 also needs the live Sway fixture rendering the app and
operator `sudo` for the default-isolation half.

**2026-10-26 — integrated loader and JNI run (same-UID development mode).**
After `zig build`, the real launch command
`ISAKAI_NO_PRIVILEGE=1 ISAKAI_FRAMEWORK=$PWD/zig-out/framework
ISAKAI_WINDOW=1 ISAKAI_NO_BRIDGE=1 ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-isosway
ISAKAI_STAGE_DIR=/tmp/isakai-1000-isosway-stage zig-out/bin/isakai
--work /tmp/isakai-n6 launch name.boyle.chris.sgtpuzzles` loaded the pinned
`lib/x86_64/libpuzzles.so` through the interposed loader. The log shows the
library mapped and relocated, `android.app.Application.onCreate`, activity
startup, and repeated native lookups for the app and Isakai window callbacks.
This is real launch evidence for the loader/JNI boundary, not the standalone
probe (`/tmp/n6-rerun.log`).

The first visible-flow attempt exposed two framework API gaps. The narrow
fixes were `View.getForeground()`/`setForeground()` and public access for
`View.getSuggestedMinimumWidth/Height()`: appcompat invokes the latter on
child `View` references, which the JVM verifier rejected while they were
protected. After rebuilding, the same app reached its chooser and stayed
alive under the isolated compositor.

Verification evidence from the corrected run:

- `.cache/isakai/evidence/simon-n6-puzzles-window.png` — real mapped
  `name.boyle.chris.sgtpuzzles` window, with the chooser's rendered
  instruction text.
- `.cache/isakai/evidence/simon-n6-puzzles-after-click.png` and
  `simon-n6-puzzles-longpress.png` — compositor input delivered without a
  process crash.
- `/tmp/n6-rerun4.log` — `JNI_OnLoad`, `GameChooser`, `GamePlay`, bridge
  connection, and native `IsakaiWindow` polling callbacks.

The chooser's game tiles/icons do not render in the translated framework, so
clicks cannot yet reach a solved puzzle. This is a framework rendering/layout
limitation, not a loader failure. No privileged/default-isolation run or
solved-puzzle action is claimed.

**2026-10-26 continuation — native-rendered Cube move under private Sway (same-UID only).** Rebuilt with `zig build` after adding `Configuration.hardKeyboardHidden` (constants, field copy/update/diff), `View.setAccessibilityPaneTitle`, `ViewGroup.updateViewLayout`, and the observed `KeyEvent.isShiftPressed` / `isAltPressed` methods. `javap` confirmed the compiled framework exports these members. The real launch command from the preceding paragraph, with intent `sgtpuzzles:cube:c4x4:5B10,2`, reaches `GamePlay`; `/tmp/pi-bg/252848-bg11.log` records JNI_OnLoad, native `keyEvent` and `timerTick` lookups, and `run complete`, with no `NoSuchFieldError`, `NoSuchMethodError`, or crash. Closing the surface through Sway produced `isakai: frame post failed: ConnectionClosed` during teardown; the process still exited 0. The mapped window was `Cube` under the private Sway fixture. Screenshots `.cache/isakai/evidence/n6-puzzles-cube-after-api-fixes.png`, `n6-puzzles-after-pointer-r3.png` and `n6-puzzles-after-two-moves-r3.png` show the native-rendered board changing after pointer actions sent through the existing `verify-isakai/sway_backend/vinput.py` driver. This proves a visible native game action in same-UID development mode; it does not prove a solved puzzle, default isolation, or repeat callback/GC/thread stress. N6 remains blocked on those acceptance items. N5's NDK AAsset stream and fd/offset acceptance remains open.

## Filed findings (2026-10-26 audit and review, fixed 2026-10-26)

Trail: `.audit/fixall.tsv`. Build-verified; no live native run exists.

- `nMapBuffer` never checked array length: rejects zero-length arrays
  (`src/bridge/jni.zig`).
- `_r_debug` backed by one byte while the loader stores a struct: now
  declared at true ELF size and layout (`src/native/bionic_shim.zig`).
- Success never cleared `dlerror` and Android lookup failures left no
  message: cleared on every success, loader message recorded on failure
  (`src/native/bionic_shim.zig`).
- Fixed 64-slot library table failed the 65th load: table grows on demand
  (`src/native/bionic_shim.zig`).
- Column getters returned 0/NULL on dead statements: all seven now throw
  (`src/bridge/sqlite.zig`). Multi-row transaction wrap is BLOCKED: the
  loop owner is `ContentProvider.java` and the Zig bridge sees single
  statements; recipe recorded in the trail.
- Input ring dropped oldest events and cleared the loss flag on partial
  drain: drops mergeable motion first, flag clears only on full drain with
  a dropped count (`src/bridge/window.zig`).
- Graphics buffer survived unmapping and clipboard lied about success:
  pool-generation check on commit, failure when unbound
  (`src/bridge/window.zig`, `src/bridge/jni.zig`).

## Code review

Self-review of the N6 integration changes, the affected launch path, and the
framework compatibility fixes on 2026-10-26. The loader is initialized once,
host libraries stay on glibc, successful bionic handles are tracked before
later `dlsym`/`dlclose`, and missing ATL artifacts fail as a normal native-load
error rather than silently falling back. `zig build` exits 0 and
`git diff --check` exits 0. The observed missing framework members were added at their owning APIs and
confirmed in the compiled framework. The Cube board renders and pointer actions
visibly change it. The earlier chooser tiles remain unrendered, and a solved
puzzle or default-isolation run is not proven. No material loader defect was
found. This is a self-review, not an independent review.

**2026-10-26 independent source review — changes requested.** Findings:

- **P1 loader fallback recursion** (`src/native/bionic_shim.zig:433–436`):
  ATL's missing-library fallback calls ordinary `dlopen`, which re-enters the
  interposer when a `DT_NEEDED` dependency is absent from both search paths.
  Add a re-entry guard or bypass the interposer for ATL's host fallback.
- **P2 wrong Up navigation target** (`framework/android/app/Activity.java:937–939`):
  `navigateUpTo` finishes only the top activity even if the target is farther
  down the stack, so `A → B → C` navigating from C to A displays B.
- **P2 density override ignored** (`framework/android/content/res/ResEngine.java:160`):
  resource selection uses the original `DisplayMetrics.densityDpi` rather than
  a `Configuration.densityDpi` override in configuration contexts.

Comment review additionally found incomplete/no-op `loadStateListAnimator`
and `Theme.rebase` behavior described as complete, a stale ripple `null`
comment, an inaccurate read-only staging claim, and numeric atomic state
comments where named states would be clearer. `docs/ARCHITECTURE.md` and
`docs/HOST_REQUIREMENTS.md` also still describe a fixed 9-slot envp although
`src/uid_helper.zig` now builds a 10-slot array for up to 9 entries plus its
terminator. These findings are not fixed in this review. Review checks were
source-level and `git diff --check`; no build or live flow was run as part of
the review. The unverified default-isolation and stress gates above remain
open. This supersedes the earlier self-review's “no material loader defect”
conclusion for the reviewed diff.
