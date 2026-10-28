# IMPL-pixels5: Game pixels on the TextView.getLayout build (verification only)

Date: 2026-10-28. Branch: `worktree-agent-aa20acfaf880c38c6`. Scope:
verification only. No `src/`, `framework/`, or `verify-isakai/` file was
modified. No code change was needed or made.

## Goal, in my own words

Show game pixels now that TextView exposes its layout. Smallest change:
none. A new fault stops the drive and files a handoff with its stack,
not a fix.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the
  main checkout cache, plus `.cache/n6-bionic-build` from main followed
  by `rm -rf .zig-cache` plus rebuild (`.cache` is gitignored, tree
  stays clean). The first drive this session skipped the n6 cache copy
  and died in `GamePlay.<clinit>` with `UnsatisfiedLinkError ...
  libdl_bio.so.0` (the known missing-artifact state from IMPL-gamestart,
  not a new fault); the copy plus rebuild fixed the build, and the
  second drive below is the recorded one.
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pixels5`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pixels5`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pixels5-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build at merged
  commit `027b64a`, never the stale system tree), `ISAKAI_NO_PRIVILEGE=1`,
  `ISAKAI_WINDOW=1`, `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (`WLR_BACKENDS=headless`,
  `HEADLESS-1 1000x1000` confirmed via swaymsg; swaybg warning only,
  known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-pixels5-work`.
- Read first: `progress/IMPL-pixels4.md`,
  `progress/reports/IMPL-gamestart.md`, `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on the final rerun with zero `failed command`
  lines (the known transient first-run javac failure appeared once
  after the cache wipe; immediate rerun exit 0, bionic
  `libdl_bio.so.0` present at 154576 bytes).
- `git status` shows only this file plus
  `evidence/2026-10-28/pixels5/`. No probes, no `/tmp` scripts (none
  created; one build log deleted), fixture stopped, no sleep over 20s
  (polling loops only, longest single sleep 20s).
- `verify-isakai/` driven read-only (imported `vinput.VInput` for one
  tap) and never modified.

## Drive: chooser maps, Solo starts, game pixels yes

One launch, chooser with no intent
(`evidence/2026-10-28/pixels5/puzzles-drive.log`, 17180 lines):

- Bionic links the real engine, `GameChooser start`, window
  `name.boyle.chris.sgtpuzzles` mapped and focused in the sway tree.
  `chooser-full.png` shows the full game grid with icons and labels.
  No VerifyError, no `setActivated` crash, no MotionRange crash.
- With no intent GamePlay defers: `D/GamePlay: no state, starting
  chooser`.
- Tap on the Solo tile (vinput `click 380 292 80`, tile center read
  off the chooser screenshot): a Solo game starts:

```
isakai: activity name.boyle.chris.sgtpuzzles.GamePlay start
D/GamePlay: URI is: "sgtpuzzles:solo"
D/GamePlay: startGame: GameLaunch(GENERATING_FROM_CHOOSER, solo, null, null, null, null)
D/GamePlay: Using default params with orientation: 3x3
D/GameGenerator: exec: [.../lib/x86_64/libpuzzlesgen.so, solo, 3x3]
```

- The old getLayout fault is gone: zero `getLayout`,
  `onDescendantInvalidated`, or `areAllPrimitivesSupported` lines in
  the log, and zero `Exception` lines in all 17180 lines. Composition
  runs past the sized game view (`D/GameView: onSizeChanged: 1000, 944`,
  `density: 1.0`) into the first draw, where the game screen's dialog
  overlay measures and draws without crashing. The app process stays
  alive through the screenshot.
- `game-after-tap.png` (9475 bytes, 5.6% dark vs the 2987-byte fully
  black frames of every prior drive) shows visible game pixels: the
  game screen with back arrow, dimmed board background, and a drawn
  AlertDialog titled `Error` with body `Cannot run program ...`
  (`dialog-zoom.png` crops the dialog band; the detail line renders
  too small to read at 1000px). The dialog drawing at all proves
  `DialogTitle.onMeasure` now resolves `TextView.getLayout`. Verdict
  on the task question: pixels yes.

## Observation for next owner (no stack, not a fault)

The drawn `Error / Cannot run program ...` dialog is app-level UI, not
a framework crash: the process lives, the log holds no exception. It
almost certainly surfaces the `GameGenerator exec` of
`libpuzzlesgen.so` failing to run as a program under the sandbox (log
line 17174 is the last generator line; no generator output follows).
Some owner who wants a playable board starts at how the sandbox execs
the generator binary. Left as an observation because the task pass
bar is visible game pixels, which is met.

## Cleanup

App process killed after the screenshot (it was alive; direct ps
check confirms zero isakai processes after). Fixture stopped; unique
env dirs (`/tmp/isakai-wayland-pixels5`,
`/tmp/isakai-1000-pixels5*`) removed; `/tmp/isakai-pixels5-work` kept
because the evidence log references its paths. No probes in tree, no
shared progress file touched.

## Verdict

Pixels yes. Chooser maps, Solo starts on the tap, composition runs
past the sized game view into the first draw with zero exceptions,
and `evidence/2026-10-28/pixels5/game-after-tap.png` shows the game
screen plus a drawn Error dialog (the TextView.getLayout link holds).
Handoff observation filed for the generator exec; no code touched.
