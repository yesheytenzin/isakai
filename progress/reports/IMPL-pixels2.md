# IMPL-pixels2: Solo start past composition start on the Vibrator-service build (verification only)

Date: 2026-10-28. Branch: `worktree-agent-a93c50e70480e170f`. Scope:
verification only. No `src/`, `framework/`, or `verify-isakai/` file was
modified (`git status` shows only this file plus
`evidence/2026-10-28/pixels2/`). No code change was needed or made.

## Goal, in my own words

Show game pixels now that the Vibrator service resolves. Smallest change:
none. A new fault stops the drive and files a handoff, not a fix.

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from the
  main checkout cache, plus `.cache/n6-bionic-build` from main followed
  by `rm -rf .zig-cache` plus rebuild (same cached missing-artifact
  state as the pixels run; `.cache` is gitignored, tree stays clean).
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pixels2`,
  `XDG_RUNTIME_DIR` same, `WAYLAND_DISPLAY=wayland-1`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pixels2`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pixels2-stage`,
  plus `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` (fresh build at merged
  commit `1504c27`, never the stale system tree), `ISAKAI_NO_PRIVILEGE=1`,
  `ISAKAI_WINDOW=1`, `ISAKAI_NO_BRIDGE=1`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (1000x1000 HEADLESS-1 output confirmed via
  swaymsg; no fatal warnings in `sway.log`).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-pixels2-work`.
- Read first: `progress/reports/IMPL-pixels.md`,
  `progress/reports/IMPL-gamestart.md`, `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on the final rerun with zero `failed command`
  lines (the known transient first-run javac failure appeared once
  after the cache wipe; immediate rerun exit 0).
- `git status` shows only this file plus `evidence/2026-10-28/pixels2/`.
  No probes, no `/tmp` scripts left, fixture stopped, no sleep over 20s
  (polling loops only, 2s cadence).
- `verify-isakai/` driven read-only (imported `vinput.VInput` for one
  tap) and never modified.

## Drive: chooser maps, Solo starts, pixels no

One launch, chooser with no intent
(`evidence/2026-10-28/pixels2/puzzles-drive.log`, 17162+ lines):

- Bionic links the real engine, `GameChooser start` (log line 17150),
  window `name.boyle.chris.sgtpuzzles` mapped in the sway tree.
  `chooser-full.png` shows the full game grid with icons and labels
  (Keen, Net, Solo, Bridges, Dominosa, Filling, Flood, Inertia, ...).
  No VerifyError, no `setActivated` crash, no MotionRange crash.
- With no intent GamePlay defers: `D/GamePlay: no state, starting
  chooser`.
- Tap on the Solo tile (vinput `click 340 295 80`, tile center read
  off the chooser screenshot): a Solo game starts (log lines
  17164-17174):

```
isakai: activity name.boyle.chris.sgtpuzzles.GamePlay start
D/GamePlay: URI is: "sgtpuzzles:solo"
D/GamePlay: generating as requested
D/GamePlay: startGame: GameLaunch(GENERATING_FROM_CHOOSER, solo, null, null, null, null)
D/GamePlay: Using default params with orientation: 3x3
```

- Composition reaches `ProvideAndroidCompositionLocals` through the
  attach-time path (`Activity.attachRoot` in the stack) and dies there
  in `HapticDefaults` with the fault below (first and only occurrence
  log line 17179). The main thread dies, so the process exits.
  `game-after-tap.png` is a 1000x1000 fully black frame
  (dark-pixel fraction 1.000 vs 0.004 for `chooser-full.png`):
  no game pixels. Verdict on the task question: pixels no.

## Handoff: null Vibrator persists because the typed overload still returns null

The merged fix (`1504c27`) covers only the String overload
`Context.getSystemService(String)` for `"vibrator"`. Read-only
inspection of `framework/android/content/Context.java` shows the typed
overload `getSystemService(Class<T>)` (line 352) has branches for
ActivityManager, ShortcutManager, InputMethodManager,
AccessibilityManager and AutofillManager, but no `Vibrator` branch,
so it returns null. Compose `ProvideAndroidCompositionLocals`
resolves the vibrator through the typed
`getSystemService(Vibrator.class)` path, so `<local3>` is still null
and the exact prior stack recurs byte-identically (single occurrence,
`puzzles-drive.log` lines 17179-17214):

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
	at android.app.Activity.runWindowed(Activity.java:1108)
	at org.isakai.runtime.IsakaiMain.main(IsakaiMain.java:86)
```

This is the same symptom as the IMPL-pixels handoff surviving its
supposed fix, so it reads as a persisting fault on an uncovered
overload rather than a brand-new crash site: the String-overload
no-op Vibrator never reaches the composition locals. Every Compose
screen reaches `ProvideAndroidCompositionLocals` on first
composition, so this still blocks all Compose first-frames, not just
the game. Per the task tripwire this files the handoff instead of a
fix. Next owner starts at the typed `getSystemService(Class)`
overload (and its `ContextWrapper` passthrough, already wired).

## Cleanup

App process dead (drive ends in the main-thread fatal, zero launch
processes remain per direct pgrep check). Fixture stopped; `/tmp`
build log and unique env dirs removed; `/tmp/isakai-pixels2-work`
kept because the evidence log references its paths. No probes in
tree, no shared progress file touched.

## Verdict

Pixels no. Chooser maps, Solo starts on the tap, composition runs past
start into `ProvideAndroidCompositionLocals`, and the drive stops on
the persisting null-Vibrator fault through the uncovered typed
`getSystemService(Class)` overload (stack above, first occurrence
line 17179). Handoff filed; no code touched.
