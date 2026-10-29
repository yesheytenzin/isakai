# IMPL-stress: Solo survives real play signals, input fault blocks solved puzzle

Date: 2026-10-29. Branch: `worktree-agent-a49f6f951d7d943b0`.
Parent task: [N6](N6-bionic-jni.md) step 4 (thread plus callback plus GC
plus exception behavior, unblocked by the generated board).

## Goal, in my own words

Prove the running native engine survives real play: start Solo, play
moves toward a solved puzzle through scripted taps, exercise engine
callbacks, force GC pressure mid-game, and trigger an engine exception
path. Verification only unless a one-line owned fix blocks.

## Principles applied

Prove It Works shaped every verdict below: each claim ties to a
screenshot, a log line, or a durable file, never to build success or
a process listing. Fix Root Causes shaped the input investigation: I
traced the dead taps past the seat, the focus guard, and the dispatch
chain instead of stopping at the first plausible layer, and filed the
fault where the trace ended rather than patching around it.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from
  the main checkout cache, plus `.cache/n6-bionic-build` from main
  (build.zig consumes it; without it native loading stays disabled).
- Unique env for all commands:
  `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-stress`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-stress`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-stress-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build, never
  the stale system tree), `ISAKAI_NO_PRIVILEGE=1`, `ISAKAI_WINDOW=1`,
  `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (HEADLESS-1 1000x1000 confirmed via
  swaymsg; swaybg warning only, known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-stress-work`.
- Read first: `progress/reports/IMPL-genexec2.md`,
  `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 (the known transient first-run javac failure
  appeared after the cache wipe; the rerun exits 0, helper at
  `zig-out/bin/isakai-bionic-exec`, all four `*_bio` libs plus the
  `cfg.d` map installed under `zig-out/framework/lib/bionic/`).
- `verify-isakai/` driven read-only (one-shot `isakai-vinput`
  invocations, one `SwayCompositor` focus-guarded tap, grim captures)
  and never modified.
- No sleep over 20s (polling loops and short settles only).

## Drive 1: Solo starts, board generates (PASS)

Launch with no intent (`evidence/2026-10-28/stress/puzzles-drive.log`,
17236 lines): bionic links `libpuzzles.so`, `JNI_OnLoad` resolves,
`GameChooser start`, chooser maps (`chooser-full.png`, full grid with
icons and labels). Tap Solo tile (vinput `click 355 280 80`):
`GamePlay start`, `URI sgtpuzzles:solo`, `GENERATING_FROM_CHOOSER`,
`GameGenerator exec`, then `GameView: onSizeChanged: 1000, 944` and
the second `setContentView` root swap that genexec2 drive 2 also
shows as its last line (normal game-start completion, not a fault).
`game-initial.png` (22351 bytes) shows the generated Solo 9x9 board
with givens, back arrow, `Solo` title, no Error dialog. Durable state
`shared_prefs/state.xml` holds `savedGame_solo` (`GAME :4:Solo`,
`PARAMS :3:3x3`, `SEED`, `DESC`, `AUXINFO`, `NSTATES :1:1`).

## Play toward solved: FAULT, GamePlay ignores all input

Fifteen scripted inputs, zero app response. Each step followed by a
grim capture diffed pixel-by-pixel against the base frame (all
`bbox None`) plus a log window check (zero non-bionic lines):

- Tap empty cell `click 318 104`, then `key 7`
  (`game-move1.png`, identical).
- Tap empty cell `click 124 470`, then `type 5`
  (`game-move2.png`, identical).
- Focus-guarded `SwayCompositor.click(124, 565)` with the window
  confirmed focused (`game-move3.png`, identical).
- Toolbar sweep `click {20,100,500,900} 28` including the back arrow
  (`game-sweep.png`, identical, still on GamePlay).
- Tap center `click 512 490` plus `key 1/5/9`
  (`game-keys.png`, identical).
- `key Escape` twice (`game-escape.png`, identical; the second press
  added zero log lines, ruling out the first press's coincident root
  swap as input-caused).
- Drag `down/move/move/up` across three cells
  (`game-drag.png`, identical).

The chooser tap in the same session proves the seat, the vinput
pointer protocol (`move` precedes every `click`), and the
Activity dispatch path deliver pointer events. GameView carries
`onTouchEvent`, `dispatchKeyEvent`, `onKeyDown/Up` overrides, and
the framework `ViewGroup`/`View` dispatch chain hit-tests normally,
so delivery should reach it. The main loop is alive throughout
(`jstack`: main RUNNABLE in `IsakaiWindow.nRun`, game JVM 280 MB,
timer/GC threads healthy). Durable `state.xml` is byte-unchanged
(`NSTATES :1:1`), so no move ever applied. No crash, no stack, no
app-side exception: the fault is silent input loss somewhere between
the poll queue and the GamePlay view hierarchy. Not a one-line
obvious fix; no tree change made.

Handoff for the owner: reproduce with this drive's log plus any one
screenshot pair, then instrument what `window.pollInput` returns
after the GamePlay root swap versus before it. Suspects in order:
(1) pointer/key batches stop arriving after the second
`setContentView` (seat-listener or queue state tied to the old
root); (2) GameView drops touches on engine state the Java side
never finishes (touches return false with no log); (3) a
zero-bounds invisible overlay consumes the events. The chooser
working in the same process rules out the compositor, vinput, and
the Activity-level dispatch loop.

## Engine callbacks (PARTIAL PASS)

- Java to native: 12 of 37 `GameEngineImpl` natives resolve through
  `bionic_dlsym` and run: `identifyBackend`, `fromSavedGame`,
  `getColours`, `getCurrentParams`, `getDefaultParams`,
  `getPresets`, `getUiVisibility`, `requestKeys`, `resizeEvent`,
  `serialise`, `setCursorVisibility`, `suggestDensity`
  (`puzzles-drive.log` `FOUND` lines).
- Native to Java: the draw surface callbacks render the full board
  (`game-initial.png` givens grid); no other rendering of native
  pixels is possible without them.
- Not exercised: `keyEvent` (no input reaches the game, see fault)
  and `timerTick` (absent across the whole run; the Solo screen
  shows no timer surface).

## GC pressure mid-game (PASS)

Three `jcmd GC.run` against the live game JVM (pid 25465): heap
`used 52434K` down to `used 17774K` (eden fully reclaimed, old
gen stable). After: process alive, `game-after-gc.png`
pixel-identical to the pre-GC board, zero app-side exceptions in
the log. Native mappings and JNI global state survive full
collections.

## Engine exception path (PASS)

Relaunch with a bogus deep link
(`isakai ... launch name.boyle.chris.sgtpuzzles --data
"sgtpuzzles:notagame:xyz"`, log
`evidence/2026-10-28/stress/puzzles-bogus.log`):
`D/GamePlay: URI is: "sgtpuzzles:notagame:xyz"`, then the app shows
`Error / Unrecognised game in URI: sgtpuzzles:notagame:xyz`
(`game-bogus.png`). Process stays alive, no uncaught exception, no
runtime corruption. The engine's game-resolution failure surfaces
through the handled dialog path end to end.

## Operator command for N6 step 5 (default-isolated proof)

`sudo -n` needs a password here and no `puzzles` flow exists in
`sway-flow.py` yet, so step 5 stays an operator run. Mirroring the
privileged `sway-flow.py` shape (private fixture plus real
per-app confinement into a fresh work dir), the operator runs,
with the fixture up at `ISAKAI_WAYLAND_DIR`:

```
isakai --work /tmp/isakai-n6-puzzles-priv install \
  .cache/isakai/apks/sgtpuzzles_149.apk
ISAKAI_NO_PRIVILEGE=0 ISAKAI_FRAMEWORK=$PWD/zig-out/framework \
  ISAKAI_WINDOW=1 \
  isakai --work /tmp/isakai-n6-puzzles-priv launch \
  name.boyle.chris.sgtpuzzles
```

The install must run without `ISAKAI_NO_PRIVILEGE` so the app gets
a real uid through the polkit helper (interactive auth); the
launch drops the dev `ISAKAI_BRIDGE_DIR`/`ISAKAI_STAGE_DIR`
overrides so confinement is the default, matching
`AppRun(privileged=True)`. Then: tap Solo, play toward solved,
screenshot under `--evidence`. A `flows_puzzles` module would turn
this into `sway-flow.py puzzles --privileged --work
/tmp/isakai-n6-puzzles-priv`; that module does not exist yet.

## Cleanup

No probes in tree. No `/tmp` scripts created. Unique env dirs
(`/tmp/isakai-wayland-stress`, `/tmp/isakai-1000-stress*`)
removed; `/tmp/isakai-stress-work` kept because both evidence logs
reference its paths. Fixture stopped; zero isakai/java processes
remain. `/tmp` one-shots (`isakai-stress-vinput.log`,
`isakai-stress-app.pid`, `isakai-stress-base.png`,
`isakai-stress-sway.log`, `isakai-stress-build.log`) deleted.

## Verdict

Survival signals pass: Solo generates, 12 engine natives run with
live native-to-Java drawing, full GCs leave the game intact, and
the engine exception path degrades to a handled dialog. The
solved-puzzle leg is blocked by the silent GamePlay input fault
above; thread behavior is covered only by the healthy live thread
set (main poll loop, prefs worker, no app-thread leaks), not by
repeat callback work, which needs input first.
