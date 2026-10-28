# IMPL-n18-resurvey: pinned-app native asset caller re-survey + fd proof re-run

Date: 2026-10-28. Branch: `worktree-agent-aeb8ffd74869fcbad` (from `main`).
Step 6 of progress/reports/PLAN-n6-remaining.md. Survey plus verification.
No `framework/`, `src/`, or `verify-isakai/` file was modified. No code fix
was needed, so no handoff was filed.

## Goal, in my own words

Check whether any pinned app now ships a native caller for the NDK asset
interface N18 waits on, and re-run the two-asset fd proof from
progress/reports/IMPL-n18-proof.md on the current build to confirm no
regression. Caller found: no. Fd proof holds: yes.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the main
  checkout cache (`.cache` is gitignored; tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-n18b`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-n18b`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-n18b-stage`.
- Read first: progress/reports/PLAN-n6-remaining.md step 6,
  progress/reports/IMPL-n18-proof.md, progress/WORKER-n18.md,
  verify-isakai/SKILL.md, progress/N18-ndk-assets.md, progress/APPS.md.

## Caller re-survey (complete, current APK bytes)

All four pinned APK shas match progress/APPS.md on current bytes (see
`evidence/2026-10-28/n18-resurvey/native-imports.txt` for full output):

- OpenCalc v3.2.1, Simple Calculator 5.12.0, Simple Notes 6.17.0: zero `.so`
  entries each. No native caller possible, unchanged.
- Simon Tatham's Puzzles v149: 12 `.so` files across arm64-v8a, armeabi-v7a,
  x86, x86_64. `nm -D` grep for `AAsset|AssetManager`: zero hits in all 12.
  All undefined imports in x86_64 `libpuzzles.so` remain LIBC-versioned.
  `DT_NEEDED` unchanged (libm/libdl/libc, plus libpuzzles.so for
  libpuzzlesgen). Raw `strings` grep for `AAsset` across all ABIs: nothing,
  so no caller hides behind `dlopen`/`dlsym` by name either.

Result: still zero native `AAsset_*` call sites in every pinned library on
current bytes. The N18 blocker ("requires an existing suitable NDK asset
caller") stays open. No app was driven because there is no native asset flow
to drive: nothing to open, read, seek, or screenshot under default isolation.
Java-side `openFd` font reflection noted in the prior proof is unchanged in
scope and still does not satisfy the NDK C ABI acceptance.

What would unblock it: a future pinned app shape that links `libandroid.so`
and leaves undefined `AAssetManager_open` / `AAsset_read` / `AAsset_seek` /
`AAsset_openFileDescriptor*` / `AAsset_close` imports in its shipped `.so`,
exercised by a visible flow (for example a game loading data files or a
media app memory-mapping bundled content through the NDK rather than Java).
A future sgtpuzzles version moving its help-page reads to the NDK would also
qualify. Until such a caller ships, app-driven NDK fd traffic stays
unverifiable and the wait continues.

## Fd proof re-run (real engine, current build, pinned APK bytes)

Same two assets as the prior proof, same APK
(`sgtpuzzles_149.apk` sha256 `6b36d553...f3d652e4`).

Necessity note (artifact policy): no existing command reaches
`AAsset_openFileDescriptor64`. The only way to observe its real fd/offset/
length values is to call the built library. One throwaway C caller in
`/tmp/n18bdrv/` (never in the tree, deleted after the run) `dlopen`ed the
freshly built `zig-out/framework/lib/libisakai_jni.so` and drove exported
symbols read-only: `isakai_res_open` over the APK, `AAssetManager_open`,
`AAsset_openFileDescriptor64`, `AAsset_getLength64`, `AAsset_read`,
`AAsset_seek64`, `AAsset_close`, `isakai_res_close`.

Transcript: `evidence/2026-10-28/n18-resurvey/drive.log` (exit 0). Ground
truth in `ground-truth.txt`.

- Stored `assets/dexopt/baseline.prof`: fd=5, start=384, length=4312, length
  equals the Stored size. `pread` bytes sha256
  `bc947c187fb1c07896d82e87cc160e54005bc601fa0a5dd8df57cc1bb2b9c1ae`,
  identical to `unzip -p` and to the prior proof. Stream read after seek-zero
  gives the same bytes. Caller closed the fd.
- Stored `assets/dexopt/baseline.profm`: fd=5 reused after close, start=4756,
  length=462, matching its entry. (Prior proof saw fd=4; the number differs
  because the manager holds the archive fd open in this process. Offsets,
  lengths, and byte identity match, so no regression.)
- Deflated `assets/de/cube.html`: fd=-1 documented failure. Stream fallback
  read 4290 bytes sha256
  `3fa14fd310b11cd7309c95ad2e35b150c9b423114a58f04d5ff4bfffedae4d76`,
  identical to `unzip -p` and to the prior proof.
- Lifetime: 50 consecutive open/seek/read/close rounds, then `res_close`.
  All success, no failure.

## Screenshots

None, same stated limit as the prior proof. No pinned app calls the NDK
asset surface, so there is no window to capture; the proof drives engine
symbols, not a UI flow. Evidence is the exit-0 transcript plus sha256
identity of every byte read.

## Gates

- `zig build` exit 0 (one transient first-run javac failure, known from N5,
  clean on immediate rerun with no source change).
- `git diff --check` clean.
- `python3 tools/progress-lint.py` 0 violations.
- Shared `progress/*.md` files untouched (this file only, plus evidence).

## Cleanup

`/tmp/n18bdrv`, `/tmp/n18b-survey` removed after the run. No fixture was
started (no app flow applies). Unique env dirs removed (empty). `git status`
shows only this file plus the evidence dir. No probes in tree.
