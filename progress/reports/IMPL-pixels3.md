# IMPL-pixels3: Solo start past composition locals on the dual-overload Vibrator build (verification only)

Date: 2026-10-28. Branch: `worktree-agent-a2efedd5ba538ee6f`. Scope:
verification only. No `src/`, `framework/`, or `verify-isakai/` file was
modified (`git status` shows only this file plus
`evidence/2026-10-28/pixels3/`). No code change was needed or made.

## Goal, in my own words

Show game pixels now that both Vibrator overloads resolve. Smallest
change: none. A new fault stops the drive and files a handoff, not a
fix.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the
  main checkout cache, plus `.cache/n6-bionic-build` from main followed
  by `rm -rf .zig-cache` plus rebuild (same cached missing-artifact
  state as the pixels runs; `.cache` is gitignored, tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pixels3`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pixels3`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pixels3-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build at merged
  commit `ab6b8ca`, never the stale system tree), `ISAKAI_NO_PRIVILEGE=1`,
  `ISAKAI_WINDOW=1`, `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (`WLR_BACKENDS=headless`,
  `HEADLESS-1 1000x1000` confirmed via swaymsg; first launch took the
  X11 backend and was restarted headless; swaybg warning only, known
  non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-pixels3-work`.
- Read first: `progress/reports/IMPL-pixels.md`,
  `progress/reports/IMPL-pixels2.md`, `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on the final rerun with zero `failed command`
  lines (the known transient first-run javac failure appeared once
  after the cache wipe; immediate rerun exit 0).
- `git status` shows only this file plus `evidence/2026-10-28/pixels3/`.
  No probes, no `/tmp` scripts (none created), fixture stopped, no
  sleep over 20s (polling loops only, 2s cadence).
- `verify-isakai/` driven read-only (imported `vinput.VInput` for one
  tap) and never modified.

## Drive: chooser maps, Solo starts, composition passes locals, pixels no

One launch, chooser with no intent
(`evidence/2026-10-28/pixels3/puzzles-drive.log`, 17233 lines):

- Bionic links the real engine, `GameChooser start` (log line 17150),
  window `name.boyle.chris.sgtpuzzles` mapped in the sway tree.
  `chooser-full.png` shows the full game grid with icons and labels.
  No VerifyError, no `setActivated` crash, no MotionRange crash.
- With no intent GamePlay defers: `D/GamePlay: no state, starting
  chooser`.
- Tap on the Solo tile (vinput `click 355 290 80`, tile center read
  off the chooser screenshot): a Solo game starts (log lines
  17164-17175):

```
isakai: activity name.boyle.chris.sgtpuzzles.GamePlay start
D/GamePlay: URI is: "sgtpuzzles:solo"
D/GamePlay: generating as requested
D/GamePlay: startGame: GameLaunch(GENERATING_FROM_CHOOSER, solo, null, null, null, null)
D/GamePlay: Using default params with orientation: 3x3
D/GameGenerator: exec: [.../lib/x86_64/libpuzzlesgen.so, solo, 3x3]
```

- The old null-Vibrator fault is gone: zero `HapticDefaults` or
  `areAllPrimitivesSupported` lines in the log. Composition runs past
  `ProvideAndroidCompositionLocals` into measure/layout, through
  `AndroidComposeView.onLayout` down to `Activity.drawFrame`
  (`Activity.java:1303`), then dies in first-frame placement with the
  new fault below (first and only occurrence log line 17180). The
  main thread dies, so the process exits. `game-after-tap.png` is a
  1000x1000 fully black frame (2987 bytes vs 94994 for
  `chooser-full.png`): no game pixels. Verdict on the task question:
  pixels no.

## Handoff: missing ViewParent.onDescendantInvalidated at first-frame placement

The framework `ViewParent` interface has no
`onDescendantInvalidated(View, View)` method, but Compose's
`GraphicsLayerOwnerLayer.move` calls it during layer placement on the
first game frame. Exact stack (`puzzles-drive.log` lines 17180-17233,
single occurrence):

```
Exception in thread "main" java.lang.NoSuchMethodError: 'void android.view.ViewParent.onDescendantInvalidated(android.view.View, android.view.View)'
	at name.boyle.chris.sgtpuzzles.GameView$$ExternalSyntheticApiModelOutline0.m(Unknown Source)
	at androidx.compose.ui.platform.GraphicsLayerOwnerLayer.move--gyyYBs(Unknown Source)
	at androidx.compose.ui.node.NodeCoordinator.updateLayerBlock(Unknown Source)
	at androidx.compose.ui.node.NodeCoordinator.placeSelf-MLgxB_4(Unknown Source)
	at androidx.compose.ui.node.InnerNodeCoordinator.placeAt-f8xVGno(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate$layoutChildrenBlock$1.invoke(Unknown Source)
	at androidx.compose.runtime.snapshots.SnapshotId_jvmKt.observe(Unknown Source)
	at androidx.compose.runtime.snapshots.SnapshotStateObserver$ObservedScopeMap.observe(Unknown Source)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate.placeOuterCoordinator-MLgxB_4(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate.placeAt-f8xVGno(Unknown Source)
	at androidx.compose.ui.layout.Placeable$PlacementScope.placeRelativeWithLayer$default(Unknown Source)
	at androidx.compose.ui.draw.PainterNode$measure$1.invoke(Unknown Source)
	at androidx.compose.ui.node.LookaheadCapablePlaceable$layout$1.placeChildren(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate$layoutChildrenBlock$1.invoke(Unknown Source)
	at androidx.compose.runtime.snapshots.SnapshotId_jvmKt.observe(Unknown Source)
	at androidx.compose.runtime.snapshots.SnapshotStateObserver$ObservedScopeMap.observe(Unknown Source)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate.layoutChildren(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate.onNodePlaced$ui_release(Unknown Source)
	at androidx.compose.ui.node.InnerNodeCoordinator.placeAt-f8xVGno(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate$layoutChildrenBlock$1.invoke(Unknown Source)
	at androidx.compose.runtime.snapshots.SnapshotId_jvmKt.observe(Unknown Source)
	at androidx.compose.runtime.snapshots.SnapshotStateObserver$ObservedScopeMap.observe(Unknown Source)
	at androidx.compose.ui.node.OwnerSnapshotObserver.observeReads$ui_release(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate.placeOuterCoordinator-MLgxB_4(Unknown Source)
	at androidx.compose.ui.node.MeasurePassDelegate.placeAt-f8xVGno(Unknown Source)
	at androidx.compose.ui.layout.Placeable$PlacementScope.placeRelative$default(Unknown Source)
	at androidx.compose.ui.node.MeasureAndLayoutDelegate.remeasureAndRelayoutIfNeeded(Unknown Source)
	at androidx.compose.ui.node.MeasureAndLayoutDelegate.measureAndLayout(Unknown Source)
	at androidx.compose.ui.platform.AndroidComposeView.onLayout(Unknown Source)
	at android.view.View.layout(View.java:642)
	at androidx.compose.ui.platform.AbstractComposeView.onLayout(Unknown Source)
	at android.view.View.layout(View.java:642)
	at android.widget.RelativeLayout.onLayout(RelativeLayout.java:346)
	at android.view.View.layout(View.java:642)
	at com.google.android.material.appbar.HeaderScrollingViewBehavior.layoutChild(Unknown Source)
	at com.google.android.material.appbar.ViewOffsetBehavior.onLayoutChild(Unknown Source)
	at androidx.coordinatorlayout.widget.CoordinatorLayout.onLayout(Unknown Source)
	at android.view.View.layout(View.java:642)
	at android.widget.FrameLayout.onLayout(FrameLayout.java:66)
	at android.view.View.layout(View.java:642)
	at android.widget.LinearLayout.onLayout(LinearLayout.java:134)
	at android.view.View.layout(View.java:642)
	at android.view.ViewGroup.onLayout(ViewGroup.java:736)
	at android.view.View.onLayout(View.java:650)
	at android.view.View.layout(View.java:642)
	at android.app.DialogOverlayGroup.onLayout(DialogOverlayGroup.java:67)
	at android.view.View.onLayout(View.java:650)
	at android.view.View.layout(View.java:642)
	at android.app.Activity.drawFrame(Activity.java:1303)
	at android.app.Activity.runWindowed(Activity.java:1108)
	at org.isakai.runtime.IsakaiMain.main(IsakaiMain.java:86)
```

The call site is Compose's graphics-layer placement
(`GraphicsLayerOwnerLayer.move`, reached via
`placeRelativeWithLayer`), so this blocks any Compose screen that
places a graphics layer on first frame, not just the game. Per the
task tripwire this files the handoff instead of a fix. Next owner
starts at `framework/android/view/ViewParent.java` (add the
`onDescendantInvalidated` default) and reruns this tap drive.

## Cleanup

App process dead (drive ends in the main-thread fatal; direct ps
check shows no sway or isakai processes). Fixture stopped; unique env
dirs (`/tmp/isakai-wayland-pixels3`, `/tmp/isakai-1000-pixels3*`)
removed; `/tmp/isakai-pixels3-work` kept because the evidence log
references its paths. No probes in tree, no shared progress file
touched.

## Verdict

Pixels no. Chooser maps, Solo starts on the tap, composition runs past
`ProvideAndroidCompositionLocals` into first-frame layout (the
dual-overload Vibrator fix holds), and the drive stops on one exact
new fault: missing `ViewParent.onDescendantInvalidated` at
`GraphicsLayerOwnerLayer.move` (stack above, first occurrence line
17180). Handoff filed; no code touched.
