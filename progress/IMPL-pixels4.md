# IMPL-pixels4: Solo start past layer placement on the onDescendantInvalidated build (verification only)

Date: 2026-10-28. Branch: `worktree-agent-ae6590dba9d196048`. Scope:
verification only. No `src/`, `framework/`, or `verify-isakai/` file was
modified (`git status` shows only this file plus
`evidence/2026-10-28/pixels4/`). No code change was needed or made.

## Goal, in my own words

Show game pixels now that the layer invalidation link is closed. Smallest
change: none. A new fault stops the drive and files a handoff, not a
fix.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the
  main checkout cache, plus `.cache/n6-bionic-build` from main followed
  by `rm -rf .zig-cache` plus rebuild (same cached missing-artifact
  state as the pixels runs; `.cache` is gitignored, tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pixels4`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pixels4`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pixels4-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build at merged
  commit `1dc51d8`, never the stale system tree), `ISAKAI_NO_PRIVILEGE=1`,
  `ISAKAI_WINDOW=1`, `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (`WLR_BACKENDS=headless`,
  `HEADLESS-1 1000x1000` confirmed via swaymsg; one launch without the
  backend flag failed closed on the Wayland backend and was restarted
  headless; swaybg warning only, known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-pixels4-work`.
- Read first: `progress/reports/IMPL-pixels.md`,
  `progress/reports/IMPL-pixels2.md`,
  `progress/reports/IMPL-pixels3.md`, `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on the final rerun with zero `failed command`
  lines (the known transient first-run javac failure appeared once
  after the cache wipe; immediate rerun exit 0).
- `git status` shows only this file plus `evidence/2026-10-28/pixels4/`.
  No probes, no `/tmp` scripts (none created), fixture stopped, no
  sleep over 20s (polling loops only, 2s cadence, longest single sleep
  8s).
- `verify-isakai/` driven read-only (imported `vinput.VInput` for one
  tap) and never modified.

## Drive: chooser maps, Solo starts, composition passes layer placement, pixels no

One launch, chooser with no intent
(`evidence/2026-10-28/pixels4/puzzles-drive.log`, 17214 lines):

- Bionic links the real engine, `GameChooser start`, window
  `name.boyle.chris.sgtpuzzles` mapped in the sway tree.
  `chooser-full.png` shows the full game grid with icons and labels.
  No VerifyError, no `setActivated` crash, no MotionRange crash.
- With no intent GamePlay defers: `D/GamePlay: no state, starting
  chooser`.
- Tap on the Solo tile (vinput `click 350 295 80`, tile center read
  off the chooser screenshot): a Solo game starts:

```
isakai: activity name.boyle.chris.sgtpuzzles.GamePlay start
D/GamePlay: URI is: "sgtpuzzles:solo"
D/GamePlay: generating as requested
D/GamePlay: startGame: GameLaunch(GENERATING_FROM_CHOOSER, solo, null, null, null, null)
D/GamePlay: Using default params with orientation: 3x3
D/GameGenerator: exec: [.../lib/x86_64/libpuzzlesgen.so, solo, 3x3]
```

- The old onDescendantInvalidated fault is gone: zero
  `onDescendantInvalidated`, `HapticDefaults`, or
  `areAllPrimitivesSupported` lines in the log. Composition runs past
  first-frame layer placement into a sized game view:
  `D/GameView: onSizeChanged: 1000, 944` with `density: 1.0`. Then the
  draw pass dies in dialog measure with the new fault below (first and
  only occurrence log line 17184). The main thread dies, so the process
  exits. `game-after-tap.png` is a 1000x1000 fully black frame (2987
  bytes, byte-identical size to the pixels3 black frame): no game
  pixels. Verdict on the task question: pixels no.

## Handoff: missing TextView.getLayout at dialog-measure time

The framework `TextView` class has no `getLayout()` method, but
appcompat's `DialogTitle.onMeasure` calls it while measuring the game
screen's dialog overlay during the first draw pass. Exact stack
(`puzzles-drive.log` lines 17184-17214, single occurrence):

```
Exception in thread "main" java.lang.NoSuchMethodError: 'android.text.Layout android.widget.TextView.getLayout()'
	at androidx.appcompat.widget.DialogTitle.onMeasure(Unknown Source)
	at android.view.View.measure(View.java:592)
	at android.widget.LinearLayout.onMeasure(LinearLayout.java:83)
	at android.view.View.measure(View.java:592)
	at android.widget.LinearLayout.onMeasure(LinearLayout.java:83)
	at android.view.View.measure(View.java:592)
	at androidx.appcompat.widget.AlertDialogLayout.onMeasure(Unknown Source)
	at android.view.View.measure(View.java:592)
	at android.view.ViewGroup.measureChild(ViewGroup.java:706)
	at android.view.ViewGroup.measureChildren(ViewGroup.java:711)
	at android.view.ViewGroup.onMeasure(ViewGroup.java:722)
	at androidx.appcompat.widget.ContentFrameLayout.onMeasure(Unknown Source)
	at android.view.View.measure(View.java:592)
	at android.view.ViewGroup.measureChild(ViewGroup.java:706)
	at android.view.ViewGroup.measureChildren(ViewGroup.java:711)
	at android.view.ViewGroup.onMeasure(ViewGroup.java:722)
	at android.view.View.measure(View.java:592)
	at android.view.ViewGroup.measureChild(ViewGroup.java:706)
	at android.view.ViewGroup.measureChildren(ViewGroup.java:711)
	at android.view.ViewGroup.onMeasure(ViewGroup.java:722)
	at android.view.View.measure(View.java:592)
	at android.app.DialogOverlayGroup.onMeasure(DialogOverlayGroup.java:55)
	at android.view.View.measure(View.java:592)
	at android.app.Activity.drawFrame(Activity.java:1301)
	at android.app.Activity.runWindowed(Activity.java:1108)
	at org.isakai.runtime.IsakaiMain.main(IsakaiMain.java:86)
```

The path runs through `DialogOverlayGroup.onMeasure` (not the Compose
layer path), so this is a dialog-measure gap, reached here on the game
screen's first draw after the game view already sized to 1000x944.
Per the task tripwire this files the handoff instead of a fix. Next
owner starts at `framework/android/widget/TextView.java` (add a
`getLayout()` returning the laid-out `android.text.Layout`) and reruns
this tap drive.

## Cleanup

App process dead (drive ends in the main-thread fatal; direct ps
check shows no isakai processes, only the fixture until stopped).
Fixture stopped; unique env dirs (`/tmp/isakai-wayland-pixels4`,
`/tmp/isakai-1000-pixels4*`) removed; `/tmp/isakai-pixels4-work` kept
because the evidence log references its paths. No probes in tree, no
shared progress file touched.

## Verdict

Pixels no. Chooser maps, Solo starts on the tap, composition runs past
layer placement into a sized game view (the onDescendantInvalidated
default holds), and the drive stops on one exact new fault: missing
`TextView.getLayout` at `DialogTitle.onMeasure` (stack above, first
occurrence line 17184). Handoff filed; no code touched.
