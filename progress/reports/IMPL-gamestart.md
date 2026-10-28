# IMPL-gamestart: puzzles game start on the fixed build (N6 remaining, steps 3 plus 4)

Date: 2026-10-28. Branch: `worktree-agent-a5938f16884eeb2b0`. Scope:
verification only. No `src/`, `framework/`, or `verify-isakai/` file was
modified. No code change was needed or made.

## Goal, in my own words

Prove a puzzles game actually starts on the fixed build (emitter fix
plus PR1 sealed fix plus MotionRange all merged), then stress the
running engine. Smallest change: none. What blocked gets a handoff,
not a fix.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the
  main checkout cache (`.cache` is gitignored; tree stays clean).
- `.cache/n6-bionic-build` copied from main's cache. Without it the
  build silently skips the bionic install and every launch dies in
  `GamePlay.<clinit>` with `UnsatisfiedLinkError ... libdl_bio.so.0`
  (first drive this session). After the copy a `rm -rf .zig-cache`
  plus rebuild was required: the build graph had cached the
  missing-artifact state and would not install bionic otherwise.
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-game`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-game`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-game-stage`.
- Every launch sets `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (the
  stale-system-tree lesson from IMPL-emitfix still applies).
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (needed
  `LD_LIBRARY_PATH=$PWD/.cache/isakai/compositor/root/usr/lib` for
  `libwlroots-0.20.so`; swaybg warning only, known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-game-work`.
- Read first: `progress/reports/PLAN-n6-remaining.md`,
  `progress/reports/IMPL-verifyerror.md`,
  `progress/reports/IMPL-emitfix.md`, `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on the final rerun with zero `failed command`
  lines (the known transient first-run javac failure appeared once
  after the cache wipe; immediate rerun exit 0).
- `git diff --check` clean (no source edits).
- `python3 tools/progress-lint.py` 0 violations.

## Step 3. Game starts: YES

Drive 1, chooser with no intent
(`evidence/2026-10-28/gamestart/puzzles-chooser.log`, 17207 lines):

- Bionic links the real engine: `finished linking libpuzzles.so`,
  `SEARCH JNI_OnLoad ... FOUND JNI_OnLoad in libpuzzles.so`.
- `isakai: activity ...GameChooser start`, mapped window
  `name.boyle.chris.sgtpuzzles` in the sway tree, chooser screenshot
  `puzzles-chooser-full.png` shows the full game grid with icons and
  labels. No VerifyError, no `setActivated` crash, no MotionRange
  crash anywhere in the log.
- With no intent GamePlay defers: `D/GamePlay: no state, starting
  chooser`.

Drive 2, tap on the chooser grid (vinput `click 359 312 80`, the Solo
tile): a Solo game starts. Log lines 17164-17179:

```
isakai: activity name.boyle.chris.sgtpuzzles.GamePlay start
D/GamePlay: URI is: "sgtpuzzles:solo"
D/GamePlay: generating as requested
D/GamePlay: startGame: GameLaunch(GENERATING_FROM_CHOOSER, solo, null, null, null, null)
bionic_dlsym(..., Java_name_boyle_chris_sgtpuzzles_backend_GameEngineImpl_getDefaultParams) called
FOUND Java_name_boyle_chris_sgtpuzzles_backend_GameEngineImpl_getDefaultParams in libpuzzles.so
D/GamePlay: Using default params with orientation: 3x3
D/GameGenerator: exec: [.../lib/x86_64/libpuzzlesgen.so, solo, 3x3]
```

Evidence against the step bar: game activity maps (sway tree plus
screenshots), backend non-null (`GameLaunch(..., solo, ...)` with no
`Unrecognised game` abort, closing the WORKER-n6 sealed-reflection
block behind the PR1 fix), native engine attempted in the bionic log
(`getDefaultParams` resolved in `libpuzzles.so` and returned `3x3`),
pixels on screenshot (chooser grid; the game screen itself never
draws because of the handoff fault below, and the post-crash capture
`puzzles-after-crash.png` is an empty black frame with the window
gone).

Determinism: full relaunch plus identical tap reproduces the same Solo
start and the same crash (`puzzles-rerun.log` lines 17163-17176).

## Handoff: game first-frame blocked by missing Compose windowRecomposer

Both drives die in the first game-frame measure with the identical
stack (`puzzles-chooser.log` line 17179 on, `puzzles-rerun.log` line
17176 on):

```
Exception in thread "main" java.lang.IllegalStateException: Cannot locate windowRecomposer; View name.boyle.chris.sgtpuzzles.buttons.ButtonsView@5a9f4771 is not attached to a window
    at androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalStateException(Unknown Source)
    at androidx.compose.ui.platform.AbstractComposeView.resolveParentCompositionContext(Unknown Source)
    at androidx.compose.ui.platform.AbstractComposeView.ensureCompositionCreated(Unknown Source)
    at androidx.compose.ui.platform.AbstractComposeView.onMeasure(Unknown Source)
    at android.view.View.measure(View.java:592)
    ...
    at android.app.Activity.drawFrame(Activity.java:1301)
    at android.app.Activity.runWindowed(Activity.java:1108)
    at org.isakai.runtime.IsakaiMain.main(IsakaiMain.java:86)
```

The game screen embeds a Compose `ButtonsView`. Our framework window
(`Activity`/`DialogOverlayGroup`) never installs a Compose
composition context, so `resolveParentCompositionContext` has no
`windowRecomposer` to find and every game screen crashes at first
measure. This is a framework Compose-plumbing gap, not a one-line
miss: some owner needs to install a `WindowRecomposer` (or equivalent
composition context) on the window decor view, then rerun the tap
drive and screenshot the Solo board. The main thread dies, so the
process exits; nothing survives to stress.

## Step 4. Blocked, not attempted beyond the start

Step 3 passes, so step 4 was in scope, but there is no live game to
stress: the process is dead within a second of the game start on both
drives. Solved-puzzle plus thread plus callback plus GC plus
exception behavior all wait on the windowRecomposer handoff above.
No flow logs or screenshots beyond the crash exist to record.

## Step 5. Operator command (default-isolated proof)

No scripted privileged puzzles flow exists yet (`sway-flow.py` covers
opencalc/calculator/notes only), so the operator run mirrors the
`tools/e2e-privileged.sh --required` shape by hand: pkexec elevation,
default per-app confinement (`ISAKAI_NO_PRIVILEGE=0`, no bridge/stage
overrides), fresh framework from this commit. From the repo root, with
the private sway fixture up:

```
sudo -n true  # must succeed; same-UID evidence below is not a privileged pass
rm -rf /tmp/isakai-game-priv && ISAKAI_NO_PRIVILEGE=0 ISAKAI_FRAMEWORK=$PWD/zig-out/framework \
  ISAKAI_WINDOW=1 zig-out/bin/isakai --work /tmp/isakai-game-priv \
  install .cache/isakai/apks/sgtpuzzles_149.apk
ISAKAI_NO_PRIVILEGE=0 ISAKAI_FRAMEWORK=$PWD/zig-out/framework ISAKAI_WINDOW=1 \
  zig-out/bin/isakai --work /tmp/isakai-game-priv launch name.boyle.chris.sgtpuzzles
# then: vinput tap on a chooser tile, grim screenshot of the game board,
# retain log plus screenshots under evidence/<date>/gamestart-priv/
```

Expected once the handoff lands: Solo board pixels plus the same
backend/engine log lines as drive 2 above, with the app-side UID/GID
and sandbox assertions from the privileged gate holding.

## Cleanup

No app process survives (both drives end in the fatal main-thread
exception). Fixture (sway) stopped; `/tmp` scratch (build logs, sway
log, zoom crop) deleted; `/tmp/isakai-game-work` kept because the
evidence log references its paths; unique env dirs removed. `git
status` shows only this file plus `evidence/2026-10-28/gamestart/`.
No probes in tree. No shared progress file touched. `verify-isakai/`
read but never modified.

## Verdict

Game starts: yes. Chooser maps, backend resolves (`solo`, PR1 sealed
fix proven in the running app), native engine links, loads, and
returns params. Game-screen pixels and all of step 4 are blocked on
one exact fault: no Compose `windowRecomposer` in the framework
window (stack above). Handoff filed; no code touched.
