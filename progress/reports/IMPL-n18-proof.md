# IMPL-n18-proof: NDK asset fd access, live proof + caller hunt

Date: 2026-10-28. Branch: `n18-proof` (from `main`). Scope: verification only.
No `framework/`, `src/`, or `verify-isakai/` file was modified. No code fix
was needed, so no handoff was filed.

## Goal, in my own words

Prove the NDK asset fd contract live against the real built engine for one
Stored and one Deflated entry of a pinned APK, and finish the caller hunt:
every `openFd` call site in the pinned apps, classified memory-map vs stream.

## Setup

- `mkdir -p .cache/isakai` + copied `compositor` and `apks` from the main
  checkout cache (`.cache` is gitignored; tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-n18p`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-n18p`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-n18p-stage`.
- Read first: `reports/Remaining N6 N18 fix plan.md` (asset section),
  `progress/WORKER-n18.md`, `verify-isakai/SKILL.md`, `progress/N18-ndk-assets.md`,
  `progress/N5-resource-engine.md` (+ `progress/reports/WORKER-n5.md` jshell method).

## Caller hunt (complete)

Native `.so` files were extracted from every pinned APK and inspected in place
with `nm -D` / `readelf -d` only. Result: **zero undefined `AAsset*` imports
in every shipped `.so`, all ABIs** (`evidence/2026-10-28/n18-proof/native-imports.txt`).
The `android_*` symbols in `libpuzzles.so` are its own game-drawing routines
(`android_draw_rect`, `android_blitter_*`), not NDK calls. `DT_NEEDED` on the
x86_64 libs is only `libm/libdl/libc` (+ `libpuzzles.so` for `libpuzzlesgen`).
So no pinned app reaches the NDK C asset ABI at all: nothing memory-maps or
streams through `AAsset_*` today.

Java/dex method references (parsed from `method_ids`, exact table in
`evidence/2026-10-28/n18-proof/dex-asset-refs.txt`):

- Memory-map path, all four apps: `AssetManager.openFd` (one declaration
  reference per dex). The only defining caller is
  `androidx.core.graphics.TypefaceCompatApi26Impl.addFontFromAssetManager`
  (defined in sgtpuzzles; the other three dexes carry the same reflective
  `addFontFromAssetManager` / `obtainAddFontFromAssetManagerMethod` strings,
  so the same AndroidX font-loading reflection applies there by inference).
  It memory-maps font files; concrete font names resolve at runtime, so no
  static asset list is claimable.
- Stream paths: `AssetManager.open` (calculator, notes, sgtpuzzles decl refs),
  `ContentResolver.openAssetFileDescriptor` (calculator, notes; Glide
  `AssetPathFetcher` strings present in both), `DeviceProfileWriter.
  openStreamFromAssets` (sgtpuzzles def), WebView `WebViewAssetLoader`
  `AssetsPathHandler` strings (sgtpuzzles). OpenCalc shows only the
  `getAssets`/`openFd` refs (font path); its content flows through Resources.

Net: memory-map = fonts via TypefaceCompat reflection in every pinned app;
streams = Glide/ContentResolver (calculator, notes), WebView asset loader +
profile installer (sgtpuzzles). No app-code `openFd` call site names a
concrete asset file.

## Live proof (real engine, pinned APK bytes)

APK: `sgtpuzzles_149.apk`, Simon Tatham's Puzzles v149, x86_64, sha256
`6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`
(matches `progress/APPS.md` pin). Chosen because it is the only pinned APK
with both Stored and Deflated entries under `assets/`.

Necessity note (artifact policy): no existing command reaches
`AAsset_openFileDescriptor`; the only way to observe its real fd/offset/length
values is to call the built library. I compiled one throwaway C caller in
`/tmp/n18drv/` (never in the tree, deleted after the run) that `dlopen`s the
freshly built `zig-out/framework/lib/libisakai_jni.so` and drives its exported
symbols read-only over the APK. Handle acquisition uses `isakai_asset_open`
with the exact `"assets/<name>"` path shape `AAssetManager_open`
(`src/bridge/resources.zig`) builds before forwarding, so the exercised call
sequence is identical to the NDK path; the fd calls below go through the real
exported `AAsset_openFileDescriptor64`, plus `AAsset_getLength64` and
`AAsset_close`. `src/bridge/resources.zig:563-567` was re-read to confirm the
export is a direct forward to `isakai_asset_fd` with null checks only.

Transcript: `evidence/2026-10-28/n18-proof/drive.log` (exit 0). Ground truth
(`unzip -p` sha256, central directory) in `ground-truth.txt`.

- Stored `assets/dexopt/baseline.prof`: `AAsset_openFileDescriptor64` returned
  **fd=4, start=384, length=4312** (length equals the `unzip -lv` Stored size
  4312). `pread(fd, 4312, 384)` read 4312 bytes whose sha256 is
  `bc947c187fb1c07896d82e87cc160e54005bc601fa0a5dd8df57cc1bb2b9c1ae`,
  identical to `unzip -p`. Caller closed the fd (ownership: caller-owned, no
  leak; a second Stored entry `baseline.profm` returned fd=4 reused after
  close, start=4756, length=462, matching its 462-byte entry).
- Deflated `assets/de/cube.html` (4290 bytes inflated, 1878 stored):
  `AAsset_openFileDescriptor64` returned **fd=-1** (documented failure, no
  fabricated descriptor). Stream fallback (`isakai_asset_read` loop to
  `AAsset_getLength64` = 4290) read 4290 bytes whose sha256 is
  `3fa14fd310b11cd7309c95ad2e35b150c9b423114a58f04d5ff4bfffedae4d76`,
  identical to `unzip -p`.
- Lifetime: 50 consecutive open/read/close rounds, then `res_close`; all
  returned success, no failure.

## Screenshots

None. There is no window to capture: no pinned app calls the NDK asset
surface (the hunt result above), and the proof drives engine symbols, not a
UI flow. No sway binary exists in this environment either. Evidence is the
exit-0 transcript plus sha256 identity of every byte read, which is stronger
than a screenshot for this contract. This is a stated verification limit, not
a pass by screenshot.

## Gates

- `zig build` exit 0 (one transient first-run javac failure, known from N5,
  succeeded on immediate rerun with no source change; final run exit 0).
- `git diff --check` clean.
- `python3 tools/progress-lint.py` 0 violations.
- Shared `progress/*.md` files untouched (this file only, plus evidence).

## Cleanup

`/tmp/n18drv`, `/tmp/n18hunt`, `/tmp/n18-build.log` removed after the run. No
fixture was started (no app flow applies). Unique env dirs removed (empty).
`git status` shows only this file plus the evidence dir. No probes in tree.

## Verdict: proof complete

fd values (Stored baseline.prof: fd=4, start=384, length=4312; Stored
baseline.profm: fd=4, start=4756, length=462), failure code (Deflated
cube.html: fd=-1 with intact 4290-byte stream fallback), and caller list
(zero native call sites; Java memory-map = TypefaceCompat font reflection in
all four apps; streams = Glide/ContentResolver, WebView loader, profile
installer) are recorded above with evidence paths. Remaining limit: no
guest-`.so` NDK call exists to observe yet, so app-driven NDK fd traffic
stays unverified until a pinned app ships one; the C ABI exports themselves
are proven on real archive bytes.
