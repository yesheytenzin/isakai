---
id: "N5"
status: "in_progress"
phase: "RESOURCES"
priority: "P1"
size: "L"
updated: "2026-10-26"
owner: "unassigned"
next: "review the C8 AssetFileDescriptor repair and record actual caller evidence or the remaining verification limit"
---

# N5: Replace duplicate resource selection with a libandroidfw adapter

## Dependencies

- [N3](N3-trusted-runtime.md)
- [N4](N4-upstream-pins.md)

## Code and documentation

- [src/native/res_engine.cpp](../src/native/res_engine.cpp) — C ABI over AOSP AssetManager2 with explicit handle deletion
- [src/bridge/resources.zig](../src/bridge/resources.zig) — JNI natives for `android.content.res.ResEngine`
- [src/native/android_log.zig](../src/native/android_log.zig) — android/log.h shared by the bionic shim and libandroidfw
- [framework/android/content/res/ResEngine.java](../framework/android/content/res/ResEngine.java), [AndroidfwTable.java](../framework/android/content/res/AndroidfwTable.java), [ResXmlParser.java](../framework/android/content/res/ResXmlParser.java), [ResourceTable.java](../framework/android/content/res/ResourceTable.java)
- [framework/android/content/res/Resources.java](../framework/android/content/res/Resources.java) (legacy `Table`/`LegacyTheme` behind `ISAKAI_RESOURCES=legacy`), [AssetManager.java](../framework/android/content/res/AssetManager.java), [TypedArray.java](../framework/android/content/res/TypedArray.java)
- [src/install.zig](../src/install.zig) — retained `base.apk`; `src/arsc.zig` removed
- [build.zig](../build.zig), [build.zig.zon](../build.zig.zon), [DEPENDENCIES.md](DEPENDENCIES.md#built--resource-engine-n5)

## Implementation

1. Preserve a verified read-only original APK with explicit lifetime and update ownership.
2. Place a narrow JNI/C resource backend below existing Java APIs using pinned libandroidfw; retain current parser behind a fallback flag during comparison.
3. Map locale/density/night/SDK configurations, theme/style/reference and XML semantics, system resources and ordered split paths.
4. Consolidate CLI label/resource lookup onto the same engine or a tested facade; define native asset fd/offset behavior and handle deletion.

## Acceptance

- Default/en/fr, mdpi/xhdpi, day/night, inherited themes, vector color references and binary XML agree with reference Android.
- Malformed data fails safely and resource handles close; selected pinned app remains usable after resize/configuration changes.
- Java asset streams and APK-backed descriptors preserve bytes, range and ownership. NDK C ABI stream/fd integration is accepted independently in [N18](N18-ndk-assets.md).

## Stop condition

Do not remove the current parser until required parity and dependency packaging are demonstrated; unexpected full-Android-service dependency stops this narrow adoption.

## Effort estimate

Planning estimate: 2–4 engineer-weeks for a bounded pilot.

## Reuse sources

- [https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/libandroidfw/include/androidfw/androidfw_c_api.h](https://gitlab.com/android_translation_layer/art_standalone/-/blob/66a5d9079b159e9c5819bfeef4a6fff6a5f32719/libandroidfw/include/androidfw/androidfw_c_api.h)

## Blockers

### Current acceptance boundary, 2026-10-26

[C8](C8-shared-contracts.md) repairs the discovered Java AssetFileDescriptor range and backing
file ownership defect (implemented, self-reviewed, exercised by the passing
pinned flows; a directly observed bounded-range asset read is still
unverified). Earlier resource-selection evidence does not prove this
wrapper correct. Keep N5 unfinished until the affected implementation is reviewed
and its actual caller evidence or remaining verification limit is recorded.

The former native-caller acceptance moves to [N18](N18-ndk-assets.md), which depends on
N5/N6, not N15 or Skia. N15 consumes N18. This removes the completion cycle;
it does not claim that NDK asset access already works. Existing N6 records now
include same-UID Bionic loading and a visible native game action, so the older
statements that no Android DSO loads describe their original dates only.

### Historical blocker investigations

The following records predate that acceptance split and preserve their original
observations. Their references to the third native acceptance bullet now belong
to N18 rather than blocking N5 on graphics integration.

The first two acceptance bullets are proven (see Verification evidence). The
third bullet, asset stream and uncompressed fd/offset access in an existing
visible NDK application, cannot be closed under this task alone. A 2026-10-24
session investigated the full path and recorded the reasons here; decision
trail `.audit/n5.tsv`.

- **No Android-built DSO loads today, and that is N6, not N5.** The runtime
  accepts and stages x86_64 slices (`src/runtime.zig:48`), but the app's
  libraries are still handed to the host ELF loader (`src/launch.zig:909`),
  which cannot resolve an Android DSO's `DT_NEEDED libc.so/libm.so/libdl.so`
  against Android `@LIBC` symbol versions: the host `libc.so`/`libm.so` are
  linker scripts, `libdl.so` is absent, and host versions are `GLIBC_*`. A
  concrete example, F-Droid Basic's `lib/x86_64/libandroidx.graphics.path.so`,
  also expects an ART-private `android.graphics.Path.mNativePath` layout Isakai
  does not have (`framework/android/graphics/Path.java:21`). Honest native
  loading means integrating a Bionic loader plus JNI registration, which is the
  work scoped to [N6](N6-bionic-jni.md).
- **No existing app in reach exercises the acceptance.** The three pinned apps
  carry no native libraries; F-Droid Basic imports no `AAsset*` symbol. The
  closest upstream candidate found, ScummVM 2.9.0 x86_64, is stream-only in its
  asset backend (no `AAsset_openFileDescriptor`) and requires EGL, so it does
  not prove the fd/offset bullet. The acceptance wording ("an existing visible
  NDK application") and the project artifact policy both forbid authoring a
  probe app to stand in for one, and no Android NDK toolchain is installed on
  this host to build one in any case.
- **Isakai exposes no NDK `AAssetManager_*`/`AAsset_*` C ABI.** The engine has
  host-side `isakai_asset_*` operations (`src/native/res_engine.cpp:301`) but
  no external NDK asset surface, and `isakai_asset_range` closes the fd before
  returning, so it cannot back `AAsset_openFileDescriptor` as written. This
  adapter is small and designed (see the session design note), but writing it
  before a loader and an app exist would be unverifiable beyond compiling, so
  it is deferred to [N15](N15-native-windows.md) where a real caller can exercise it.
- **Privileged evidence is extra, not an N5 acceptance bullet.** The supported
  `pkexec` route needs operator authentication (`sudo -n` requires a password
  in this session). Default-isolation evidence is required once the selected
  native app can run; an unavailable privileged observation stays labelled and
  is never replaced by a same-UID claim.

**Next action.** Do [N6](N6-bionic-jni.md) (same-ISA Bionic loading and JNI ownership),
then [N15](N15-native-windows.md)'s NDK asset adapter over `ResEngine.NativeAsset` and a real
fd-returning `isakai_asset_*`, then drive a selected existing NDK app and record
its stream and fd/offset asset reads here to close the third bullet.

**2026-10-25 re-grounding (no change to status).** An operator "fix continue"
directive prompted a fresh check of the three external blockers rather than
inheriting them. Findings: no Android NDK toolchain is installed
(`ndk-build` absent, `ANDROID_NDK_*` unset), so a probe app cannot be built
here even setting aside the artifact policy that forbids authoring one;
`sudo -n` still needs a password, so the privileged gate cannot run. A locally
downloaded APK, Hill Climb Racing 1.67.0 (`~/Downloads`), ships only
`arm64-v8a` and `armeabi-v7a` with no x86_64 slice, so it is a foreign-ISA
(M4) target, not an N5/N6 same-ISA candidate. An extended web search found no
reachable open-source x86_64 NDK app that uses `AAsset_openFileDescriptor`.
Reading [N6](N6-bionic-jni.md) confirms the structural point: N6's own acceptance also
requires "an existing same-ISA NDK application," so N5's third bullet and all
of N6 share one binding constraint, an existing x86_64 Android app that bundles
native libraries. Closure is gated on operator-supplied inputs (such an app,
an NDK toolchain, and eventually operator `sudo`); the engine itself has no
open defect. Decision trail rows appended to `.audit/n5.tsv`.

## Verification evidence

All runs on 2026-10-24, same-UID development mode (`ISAKAI_NO_PRIVILEGE=1`) in
the private headless Sway fixture (`/tmp/isakai-wayland-1000`). None is a
privileged result. Decision trail: `.audit/n5.tsv`.

**Build and dependencies.** `zig build` exits 0. It fetches the pinned ART
Standalone subtrees and ATL `framework-res.apk` (content hashes in
`build.zig.zon`), compiles libandroidfw, libbase, libutils, libziparchive,
incfs-utils and two libcutils files statically into `libisakai_jni.so`, and
installs `zig-out/framework/framework-res.apk`. `ldd libisakai_jni.so` adds only
the host `libz.so.1`. `git diff --check` exits 0.

**Retained APK.** `isakai --work W install OpenCalc.v3.2.1.apk` stores
`code/<pkg>/<digest>/base.apk` mode 0444, `cmp` identical to the pinned APK,
sha256 `43b6befd…a4510f3` equal to the code-dir name. `stage()` now extracts
every entry from that copy and `verifyRetained` checks its digest (install and
upgrade). `uninstall` removes the tree.

**Pinned apps on libandroidfw** (`verify-isakai/sway-flow.py`, no
`isakai: resources` fallback line in any app log):

- OpenCalc fresh install: PASS — calculate/copy `46`, resize 640x800 →
  480x600 → 720x860, 31 s liveness, exit 0
  (`.cache/isakai/evidence/n5-androidfw-opencalc-4`, `-5`, `-6`,
  `n5-final-opencalc-r1`, `-r2`). One run of six (`n5-final-opencalc`) recorded
  `Syntax` for `12+34`; it did not reproduce in the two immediate reruns
  (HISTORY.md already records a state-dependent OpenCalc `Syntax` failure).
- Simple Calculator fresh install: PASS — settings, decimal preference, back,
  reopen, 31 s, exit 0 (`n5-androidfw-calculator-2`, `n5-final-calculator`).
- Simple Notes over the retained baseline data plus the verified `base.apk`:
  PASS (`n5-androidfw-notes-6`, `n5-final-notes2`). One batch run
  (`n5-final-notes`) reported no mapped window; a manual relaunch mapped the
  `Notes` window and the rerun passed. A fresh-install Notes run fails
  identically at HEAD (control build in a scratch worktree), so that state is
  pre-existing.
- Harness taps moved to measured positions because framework ActionButton
  styles now give toolbar actions their Android 48dp width: Calculator settings
  (592→560,16), Notes folder (536→488,15).

**Configuration selection** (real APKs; engine checks via `jshell` over the
built framework classes, labelled as inspection, not app flows):

- Locale: OpenCalc's window title (label resolved by the runtime engine) is
  `Calculator` by default and `Calculatrice` with
  `JAVA_TOOL_OPTIONS=-Duser.language=fr -Duser.country=FR`
  (`n5-config/opencalc-fr.log`). The removed CLI parser always returned the
  default string.
- Density: `drawable/abc_btn_check_to_on_mtrl_000` resolves to `res/w_.png`
  (density 160) at 160 dpi and `res/-B.png` (320) at 320 dpi; the PNGs are
  32×32 and 64×64 in the APK — the mdpi/xhdpi 2× ratio.
- Day/night: through OpenCalc's `style/AppTheme`, `android:colorBackground`
  is `#ffffffff` (day) / `#ff121212` (night) and `android:textColorPrimary`
  switches to `m3_dark_default_color_primary_text`. With the app's own
  `FORCE_DAY_NIGHT=2`, AppCompat's night override reaches
  `createConfigurationContext` (`uiMode=0x20`, `n5-config/opencalc-night-diag.log`
  from a temporary diagnostic since removed) and the app runs. The window stays
  light (`n5-config/opencalc-night-forced.png`) because `Activity.paintRoot`
  paints `Color.WHITE` and `View` defaults to white: a renderer limitation, not
  selection.
- Inherited themes: `AppTheme` resolves app, Material3 and framework layers —
  `android:textAppearanceLarge` → `TextAppearance.Material.Large`,
  `listPreferredItemHeight` → 64dp, `windowAnimationStyle` →
  `Animation.Material.Activity` (AOSP Material values). The legacy engine
  resolves none of these three.
- Binary XML and vector/color references: all three apps inflate through the
  native `ResXMLParser`; references arrive as AOSP decimal `@<id>` and resolve
  (Notes `shortcut_plus` layer-list, toolbar vector icons in all apps). The
  Isakai fixture (`tools/fixture-apk.zig`) now builds an AOSP-valid table and
  launches titled `Fixture` (`n5-config/fixture-window.png`); both engines
  return identical strings, color, dimension and string array for it.

**Malformed data and handles.** A PNG opened as XML, a missing XML/asset, a
`../` asset path, ids `0`, `0x02010000`, `0x7f13ffff`, a style read as a string
and a string read as an array all raise `FileNotFoundException`/
`NotFoundException`; the plain-text fixture layout is rejected as AOSP does. No
malformed archive was synthesized (artifact policy); a missing `base.apk`
falls back to the legacy engine with a logged reason (pre-N5 installs,
`n5-androidfw-opencalc-1`). Handle lifetime: 20,000 open/close rounds each of
XML blocks, themes (Cleaner only) and assets raise VmRSS 116→310 MiB once
(heap growth), then a further 20,000 rounds add 2 MiB — no per-handle retention.

**Assets.** `AssetManager.open` bytes equal `unzip -p` (sha256) for OpenCalc
`dexopt/baseline.prof` (stored) and F-Droid Basic `PublicSuffixDatabase.list`
(deflated). `openFd` returns `base.apk` with start 384 / length 2286 (and
9856 / 1415 for F-Droid's `baseline.profm`) whose bytes match; the compressed
entry raises AOSP's "probably compressed" error.

## Filed findings (2026-10-26 review, fixed 2026-10-26)

Trail: `.audit/fixall.tsv`.

- `hardKeyboardHidden` never reached the native selector: added to the
  packed config and applied in `isakai_res_set_config` (`ResEngine.java`,
  `res_engine.cpp`).
- `Theme.setTo(null)` cleared on legacy but ignored on native: native theme
  now resets on null or foreign input (`AndroidfwTable.java`, `ResEngine.java`,
  `resources.zig`, `res_engine.cpp`).
- Legacy parser deletion was proposed and REJECTED by its own gate: pinned
  evidence runs on the staged parser with native-first automatic fallback,
  so the parser, flag, and branches stay. Gate and evidence in the trail.

## Code review

Self-review of the full diff (no independent reviewer ran; no subagents were
spawned this session).

- Findings fixed: ATL's C API leaked AssetManager/ApkAssets → replaced by
  `res_engine.cpp` with explicit deletes; missing framework package failed every
  app style (`Failed to find parent 0x0103…`) → pinned AOSP `framework-res.apk`;
  `Drawable` parsed decimal `@<id>` as hex; interpolators read floats with
  `getAttributeFloatValue`; `animated-selector` unsupported;
  `createConfigurationContext` recursed (StackOverflowError) and
  `Activity.getResources` bypassed the base context; the fixture tool wrote
  `ResTable_entry.size` 16 and a bag parent `0xFFFFFFFF`. Removed dead members
  (`ACCESS_BUFFER`, unused cookie/key plumbing, a one-caller build helper,
  removed test hooks) and `src/arsc.zig`.
- Ownership: ApkAssets are cached for the process (open Assets read the
  archive); engines, themes, XML blocks and assets close explicitly with a
  Cleaner fallback; inner handle classes keep their engine reachable.
- Remaining limitations: night mode is selected but not visibly rendered (white
  default window/view backgrounds); `getIdentifier` with a null package returns
  0 as on Android; each configuration context creates a new AssetManager2
  (no ResourcesManager cache); TypedArray strings resolve eagerly; split APKs
  are supported by `ResEngine.open` but no installer path supplies them yet;
  `src/upgrade.zig` and `tools/fixture-apk.zig` were already unformatted at
  HEAD.

**2026-10-25 follow-up review fixes (`zig build` exits 0).** An external
code-review pass flagged two robustness defects in the new native code, both
fixed:

- `src/native/android_log.zig`: the variadic log entry points forwarded their
  format string straight to `vsnprintf`, but AOSP's liblog allows a null
  format — `LOG_ALWAYS_FATAL_IF(cond)` with no message passes a null `fmt`, and
  `LOG_ALWAYS_FATAL(fmt, …)` passes a null `cond` to `__android_log_assert`.
  A null format to `vsnprintf` is undefined behavior and segfaults on glibc, so
  a failed libandroidfw check (the malformed-data path this task exercises)
  crashed instead of aborting with its message. `fmt` (all four printers) and
  `cond` (the assert) are now optional and null-guarded, keeping the diagnostic
  and a clean abort. Verified by build; the trigger is a fatal native check, and
  the artifact policy forbids synthesizing malformed archives to force one, so
  no app-flow evidence was produced for it.
- `src/native/res_engine.cpp`: `isakai_asset_list` allocated `size + 1` bytes
  but copied only `size`, leaving the trailing byte uninitialized; it now copies
  `c_str()`'s guaranteed NUL into that byte, which also keeps an empty directory
  returning a non-null buffer (preserving `malloc(0)`-independent behavior).
  Functionally inert (the Zig caller slices by the returned length); verified by
  build.

**2026-10-25 blocker re-verification (no change to status).** A "completely
fix" directive prompted a hands-on recheck of all three external blockers;
each still holds. No NDK toolchain (`ndk-build` absent, `ANDROID_NDK_*`
unset); `sudo -n` still needs a password. Correction to the earlier
re-grounding: `~/Downloads` now also holds an uptodown repackage of Hill
Climb Racing that **does** ship `lib/x86_64` (`libuptodown-native.so`,
`libutd-services-native.so`, `libandroidx.graphics.path.so`,
`libdatastore_shared_counter.so`) — so an x86_64 NDK app with native libs is
in reach for [N6](N6-bionic-jni.md). It does **not** close N5's third bullet: none of its
x86_64 `.so`s import any `AAsset*`/`AAssetManager_*` symbol, so there is no
native asset-stream or fd/offset caller to exercise, and nothing x86_64 can
run until N6's Bionic loader exists. N5 has no open code defect; closure stays
gated on an operator-supplied app that uses the NDK asset API, an NDK
toolchain, and operator `sudo`.

**2026-10-25 update — a native x86_64 app is now pinned, but not for this
bullet.** Simon Tatham's Puzzles ([APPS.md](APPS.md) index 3) is pinned as the
same-ISA target for [N6](N6-bionic-jni.md); its `lib/x86_64/libpuzzles.so` is a genuine JNI
library but imports no `AAsset*`/`AAssetManager_*` symbol (it reads assets via
the Java layer), so it still does **not** exercise N5's third (asset fd/offset)
bullet. That bullet remains gated on an `AAsset`-using x86_64 app plus N6's
loader; no change to N5 status.

**2026-10-26 code review — changes requested.** Source review found a defect in
archive-backed `AssetFileDescriptor` handling: `AssetManager.openFd()` returns
the APK plus an entry offset/length, but
`framework/android/content/res/AssetFileDescriptor.java:createInputStream()`
reads from the start of the archive without enforcing the entry bounds. This
can return ZIP bytes instead of asset contents. NDK AAsset stream/fd acceptance
is still unverified; this finding is distinct from that operator-gated flow.
Comment review also found stale read-only staging claims in
`src/install.zig` and `framework/android/content/IsakaiPackageManager.java`;
staging accepts all ABI libraries and other files are owner-writable. No files
were changed during review. Existing build and real-flow evidence above is not
a validation of these findings.
