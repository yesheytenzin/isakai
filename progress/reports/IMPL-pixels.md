# IMPL-pixels: Solo game start past composition start (verification only)

Date: 2026-10-28. Branch: `worktree-agent-a259ce78b5ae81bcb`. Scope:
verification only. No `src/`, `framework/`, or `verify-isakai/` file was
modified. No code change was needed or made.

## Goal, in my own words

Show game pixels on screen past composition start on the fresh build
(emitter fix, MotionRange, recomposer plumbing, register-3 fix, Vibrator
primitives plus clearViewTranslationCallback all merged). Smallest change:
none. A new fault stops the drive and files a handoff, not a fix.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the
  main checkout cache, plus `.cache/n6-bionic-build` from main followed
  by `rm -rf .zig-cache` plus rebuild (same cached missing-artifact
  state as the gamestart runs; `.cache` is gitignored, tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pixels`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pixels`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pixels-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build, never the
  stale system tree), `ISAKAI_NO_PRIVILEGE=1`, `ISAKAI_WINDOW=1`,
  `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (swaybg warning only, known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-pixels-work`.
- Read first: `progress/reports/IMPL-gamestart.md`,
  `progress/reports/IMPL-recomposer.md`, `progress/reports/IMPL-reg3.md`,
  `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on the final rerun with zero `failed command`
  lines (the known transient first-run javac failure appeared once
  after the cache wipe; immediate rerun exit 0).
- `git diff` empty, `git status` shows only this file plus
  `evidence/2026-10-28/pixels/`. No probes, no `/tmp` scripts left,
  fixture stopped, no sleep over 20s (polling loops only).
- `verify-isakai/` driven read-only (imported `vinput.VInput` for one
  tap) and never modified.

## Drive: chooser maps, Solo starts, pixels no

One launch, chooser with no intent
(`evidence/2026-10-28/pixels/puzzles-drive.log`, 17496 lines):

- Bionic links the real engine, `GameChooser start` (log line 17150),
  window `name.boyle.chris.sgtpuzzles` mapped in the sway tree.
  `chooser-full.png` shows the full game grid with icons and labels
  (Keen, Net, Solo, Bridges, Dominosa, Filling, Flood, Inertia, ...).
  No VerifyError, no `setActivated` crash, no MotionRange crash.
- With no intent GamePlay defers: `D/GamePlay: no state, starting
  chooser`.
- Tap on the Solo tile (vinput `click 325 312 80`, tile center read
  off the chooser screenshot): a Solo game starts (log lines
  17455-17460):

```
isakai: activity name.boyle.chris.sgtpuzzles.GamePlay start
D/GamePlay: URI is: "sgtpuzzles:solo"
D/GamePlay: generating as requested
D/GamePlay: startGame: GameLaunch(GENERATING_FROM_CHOOSER, solo, null, null, null, null)
D/GamePlay: Using default params with orientation: 3x3
```

- Composition runs further than every prior drive: past `ComposerImpl`
  load (register-3 fix holds), past `CompositionImpl.composeInitial`,
  through the `Recomposer` into `doCompose-aFTiNEg` and
  `ProvideAndroidCompositionLocals`. No `NoSuchMethodError` for
  `Vibrator.areAllPrimitivesSupported` and no
  `clearViewTranslationCallback` fault: both prior handoff items are
  closed by the merged primitives.
- First composition then dies in `HapticDefaults` with the new fault
  below (first occurrence log line 17461). The main thread dies, so
  the process exits. `game-after-tap.png` is a black frame: no game
  pixels. Verdict on the task question: pixels no.

## Handoff: null Vibrator service at first composition

The framework `Vibrator` class now has the method, but the Vibrator
instance Compose resolves from the context is null. Exact stack
(`puzzles-drive.log` lines 17461-17496, single occurrence):

```
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "android.os.Vibrator.areAllPrimitivesSupported(int[])" because "<local3>" is null
	at androidx.compose.ui.platform.HapticDefaults$$ExternalSyntheticApiModelOutline1.m(Unknown Source)
	at androidx.compose.ui.platform.AndroidCompositionLocals_androidKt.ProvideAndroidCompositionLocals(Unknown Source)
	at androidx.compose.ui.platform.WrappedComposition$setContent$1$1.invoke(Unknown Source)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(Unknown Source)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(Unknown Source)
	at androidx.compose.runtime.AnchoredGroupPath.CompositionLocalProvider(Unknown Source)
	at androidx.compose.ui.platform.WrappedComposition$setContent$1$1.invoke(Unknown Source)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(Unknown Source)
	at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke(Unknown Source)
	at androidx.compose.runtime.internal.Utils_jvmKt.invokeComposable(Unknown Source)
	at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(Unknown Source)
	at androidx.compose.runtime.CompositionImpl.composeContent(Unknown Source)
	at androidx.compose.runtime.Recomposer.composeInitial$runtime_release(Unknown Source)
	at androidx.compose.runtime.CompositionImpl.composeInitial(Unknown Source)
	at androidx.compose.runtime.Latch$await$2$2.invoke(Unknown Source)
	at androidx.compose.ui.platform.AndroidComposeView.setOnViewTreeOwnersAvailable(Unknown Source)
	at androidx.compose.ui.platform.WrappedComposition.setContent(Unknown Source)
	at androidx.compose.ui.platform.WrappedComposition.onStateChanged(Unknown Source)
	at androidx.lifecycle.LifecycleRegistry$ObserverWithState.dispatchEvent(Unknown Source)
	at androidx.lifecycle.LifecycleRegistry.addObserver(Unknown Source)
	at androidx.compose.runtime.Latch$await$2$2.invoke(Unknown Source)
	at androidx.compose.ui.platform.AndroidComposeView.setOnViewTreeOwnersAvailable(Unknown Source)
	at androidx.compose.ui.platform.WrappedComposition.setContent(Unknown Source)
	at androidx.compose.ui.platform.Wrapper_androidKt.setContent(Unknown Source)
	at androidx.compose.ui.platform.AbstractComposeView.ensureCompositionCreated(Unknown Source)
	at androidx.compose.ui.platform.AbstractComposeView.onAttachedToWindow(Unknown Source)
	at android.view.View.dispatchHostAttached(View.java:1172)
	at android.view.View.dispatchHostAttached(View.java:1182)
	[... repeated dispatchHostAttached frames ...]
	at android.app.Activity.attachRoot(Activity.java:1320)
	at android.app.Activity.runWindowed(Activity.java:1105)
	at org.isakai.runtime.IsakaiMain.main(IsakaiMain.java:86)
```

The path runs through the attach-time composition
(`Activity.attachRoot` in the stack, via `onAttachedToWindow`), so the
recomposer plumbing holds and the next owner starts at whatever
provides the system Vibrator service to the composition locals
(`getSystemService(VIBRATOR_SERVICE)` returning null, or the
composition-local provider defaulting to null). Every Compose screen
reaches `ProvideAndroidCompositionLocals` on first composition, so
this blocks all Compose first-frames, not just the game. Per the task
tripwire this files the handoff instead of a fix.

## Cleanup

App process dead (drive ends in the main-thread fatal, zero launch
processes remain). Fixture stopped; `/tmp` scripts, sway log, and
unique env dirs removed; `/tmp/isakai-pixels-work` kept because the
evidence log references its paths. No probes in tree, no shared
progress file touched.

## Verdict

Pixels no. Chooser maps, Solo starts on the tap, composition runs past
start into `ProvideAndroidCompositionLocals`, and the drive stops on
one exact new fault: null Vibrator service at `HapticDefaults`
(stack above, first occurrence line 17461). Handoff filed; no code
touched.
