# WORKER-n18: NDK asset caller survey + N18 readiness (survey only, no implementation)

Date: 2026-10-28. Branch: `worktree-agent-a22956ca0b64ca6af`.
Scope was strictly survey + readiness. **No framework/src changes made or needed.**

## Setup

- `mkdir -p .cache/isakai` + copied `compositor` and `apks` from
  `/home/tenzin/development/isakai/.cache/isakai/` (per task; `compositor` copy
  only used for tree parity, never driven).
- Unique env exported for all commands:
  `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-n18 WAYLAND_DISPLAY=wayland-1
  XDG_RUNTIME_DIR=/tmp/isakai-wayland-n18 ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-n18
  ISAKAI_STAGE_DIR=/tmp/isakai-1000-n18-stage`.
- `zig build` exit **0**, no code changes (`git status --short` clean before
  and after; this file is the only new file).
- Context read first: `progress/N18-ndk-assets.md` (full, Blockers especially),
  `progress/N6-bionic-jni.md` (contract scope), `progress/archive/N4-upstream-pins.md`
  (ABI/ELF inventory context), plus `progress/APPS.md` (pin manifest) and
  `src/bridge/resources.zig` (existing AAsset export inventory).

## Survey method (no probe apps, no harnesses authored)

Every shipped artifact already in scope was examined in place; native `.so`
files were extracted to a scratch dir and inspected with `nm -D` / `readelf -d` /
`strings` only. No code was written, no app was launched (per task: prefer not
to drive; `verify-isakai/SKILL.md` was therefore not needed).

## Candidate results — ranked

**Headline: no suitable NDK asset caller exists in scope today. The N18 blocker
("requires an existing suitable NDK asset caller") is confirmed still open,
verified artifact by artifact below.** Nothing here weakens the acceptance to a
Java asset lookup.

### 1. Simon Tatham's Puzzles (`sgtpuzzles_149.apk`, APPS.md index 3) — closest, still NOT suitable
- Paths examined: `lib/x86_64/libpuzzles.so`, `lib/x86_64/libpuzzlesgen.so`,
  `lib/x86_64/libandroidx.graphics.path.so` (extracted from
  `.cache/isakai/apks/sgtpuzzles_149.apk`).
- `nm -D libpuzzles.so | grep -i AAsset` → **empty**. All 49 undefined imports
  are `LIBC`-versioned libc/libm symbols (`memcpy@LIBC`, `atan2@LIBC`, …); zero
  `AAsset*`/`android_*` undefined symbols; `DT_NEEDED` is only
  `libm.so, libdl.so, libc.so`. Reads its help-page data through the Java layer:
  `classes.dex` strings show `getAssets`/`openFd`/`AssetFileDescriptor` Java-side
  references (4 `AssetManager` hits), i.e. Java descriptor behavior only — which
  N18 explicitly does **not** accept as NDK C ABI evidence.
- Why still ranked first: it is the only same-ISA JNI app already through N6's
  loader (proven `bionic_dlopen` + `JNI_OnLoad` + natives 2026-10-25/26), has a
  rich compressed-asset corpus (`assets/de/*.html` etc., all `Defl:N` per
  `unzip -lv`; only `assets/dexopt/baseline.prof*` are `Stored`), and its
  game-doc pages give a plausible future native-asset exercise path — but only
  if a future version or another app actually calls the NDK API. Today it does not.
- Needs from N6 contract: nothing asset-specific; it only confirms the loader
  half N18 will ride on (see mapping below).

### 2. F-Droid Basic (`fdroid-basic_2000052.apk`, in `.cache/isakai/apks/` but NOT in APPS.md pins) — not suitable, out of pin set
- Path examined: `lib/x86_64/libandroidx.graphics.path.so` (sole x86_64 `.so`).
- `nm -D` → no `AAsset*`/android imports; `DT_NEEDED` only libm/libdl/libc;
  exports `JNI_OnLoad@@LIBANDROIDX.GRAPHICS.PATH`. Pure path-rendering helper.
- Not a candidate: no asset calls, and not a pinned app — adopting it would need
  a pin decision owned elsewhere. Listed only so N18 doesn't re-survey it.

### 3. M1 trio (OpenCalc v3.2.1, Simple Calculator 5.12.0, Simple Notes 6.17.0) — not suitable
- Paths examined via `unzip -l`: none ships any `lib/x86_64/*.so` (0 hits each);
  pure-Java/Kotlin apps with only `assets/dexopt/baseline.prof*`. No native
  caller possible.

### 4. `libandroidx.graphics.path.so` (ships inside sgtpuzzles + fdroid-basic) — not suitable
- Same finding in both APKs: JNI_OnLoad present, no AAsset imports. Not an asset caller.

## Existing export inventory (what N18 will verify against, not change now)

`src/bridge/resources.zig` already exports the full stream/fd/dir surface N18
acceptance names: `AAssetManager_fromJava`, `AAssetManager_open`,
`AAsset_read`, `AAsset_seek`/`seek64`, `AAsset_getLength[64]`,
`AAsset_getRemainingLength[64]`, `AAsset_getBuffer`, `AAsset_isAllocated`,
`AAsset_openFileDescriptor[64]`, `AAsset_close`, `AAssetManager_openDir`,
`AAssetDir_getNextFileName`, `AAssetDir_rewind`, `AAssetDir_close`, backed by
`isakai_asset_*` (`open/read/seek/length/remaining/buffer/is_allocated/fd/range/close/list`).
Notable for the checklist: `isakai_asset_range` returns false for compressed
entries (documented-failure path exists at the engine layer); the NDK-level
question N18 must prove live is what `AAsset_openFileDescriptor[64]` returns
for a compressed asset (must be the documented failure, e.g. -1, never a
fabricated fd) plus fd-ownership semantics (who closes, dup vs owned) for
uncompressed/`Stored` entries.

## Mapping: what each future candidate needs from N6's verified loader contract

N6's open contract follow-ups (from N6 Blockers 2026-10-26) are the exact
prerequisites any NDK asset caller will exercise, because asset entry points
reach the app through the same loaded `.so`:

1. **`android_dlopen_ext` ignoring extended-load info** — asset-calling `.so`s
   with unusual load needs (relro, reserved address hints) may hit this first.
   N18 must re-check the chosen caller's load flags against whatever N6 settles.
2. **Guest symbol lookup not forwarding errors through `dlerror`** — an asset
   caller missing an `AAsset*` export at link time must fail with a diagnosable
   `dlerror`, not a silent NULL. N18's negative testing depends on this.
3. **glibc-first fallback not explicit host/guest classification + possible
   fallback re-entry** (P1: `src/native/bionic_shim.zig:433–436`, interposer
   re-entry when a `DT_NEEDED` dep is absent) — any new candidate `.so` with a
   broader `DT_NEEDED` set than libpuzzles' (libm/libdl/libc) widens the
   re-entry surface. N18 must load the candidate only after this guard exists.
4. **Default-isolation + repeat thread/callback/GC/exception behavior** (N6
   acceptance bullet 1 remainder) — asset lifetime semantics (manager outlives
   streams; repeated open/read/close) are only meaningful once the loader runs
   the caller under default isolation. Same-UID-only evidence does not transfer.

## Readiness checklist for the day N6 lands (execute in order)

1. Confirm N6's contract is recorded (loader follow-ups 1–4 above closed, with
   evidence), then re-run `zig build` (expect exit 0, no changes).
2. Re-survey: for every then-current pinned APK, list `lib/x86_64/*.so` and run
   `nm -D | grep -i AAsset`; the first `.so` with `U AAssetManager_*`/`U AAsset_*`
   imports becomes the N18 caller. If none, N18 stays blocked — do not author a probe.
3. Classify the caller's assets with `unzip -lv`: pick one `Stored`
   (expect real owned fd + correct offset/length) and one `Defl:N` (expect
   documented failure, never a fabricated fd) target entry.
4. Drive the caller under default isolation per `verify-isakai/SKILL.md`:
   open → read bytes (compare against `unzip -p` ground truth) → seek/length/
   remaining → fd/offset/length check (`/proc/<pid>/fd`, `lseek`) → close;
   repeat open/read/close cycles; confirm manager lifetime and no leaks.
5. Record in N18: exact APK + version + sha256, ABI, actions, screenshots,
   fd/offset/length values, compressed-failure code observed, limits.

## Handoff / notes

- **Trivially safe prep changes: none found — none landed.** The export surface
  is already complete at the symbol level; remaining risk is all behavioral
  (fd ownership, compressed-failure code, lifetime under isolation), which can
  only be settled live against a real caller behind N6's contract. Per scope,
  reported here instead of changed.
- Watch item (not a change): NDK also defines `AAssetManager_openDir` sibling
  semantics and `AAsset_getBuffer` for compressed assets (NULL + length); the
  checklist's byte-comparison step covers both implicitly.
- No other workers' areas touched. No `/tmp` debris left (scratch
  `/tmp/n18survey`, `/tmp/n18survey/dex`, `/tmp/n18-build.log` removed; unique
  env dirs `/tmp/isakai-wayland-n18`, `/tmp/isakai-1000-n18`,
  `/tmp/isakai-1000-n18-stage` removed after use — they were empty).

## Evidence paths examined (citable)

- `progress/N18-ndk-assets.md`, `progress/N6-bionic-jni.md`,
  `progress/archive/N4-upstream-pins.md`, `progress/APPS.md`,
  `src/bridge/resources.zig` (exports lines 507–612, engine decls 53–62).
- `.cache/isakai/apks/sgtpuzzles_149.apk` — `unzip -l` (lib + assets listing),
  `unzip -lv` (Defl:N vs Stored), `nm -D`/`readelf -d`/`strings` on all three
  `lib/x86_64/*.so`; `strings classes.dex | grep AssetManager|getAssets|openFd`.
- `.cache/isakai/apks/fdroid-basic_2000052.apk` — `unzip -l`, `nm -D`/`readelf -d`
  on `lib/x86_64/libandroidx.graphics.path.so`.
- `.cache/isakai/apks/{calculator,notes}-fdroid-release.apk`,
  `.cache/isakai/apks/OpenCalc.v3.2.1.apk` — `unzip -l` (no x86_64 `.so`).
- `zig build` exit 0 (unique n18 env), `git status --short` clean.
