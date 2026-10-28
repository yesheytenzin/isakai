# IMPL-recomposer: window-owned composition context for the puzzles game screen

Date: 2026-10-28. Branch: `worktree-agent-a09ce6f16e7aea68b`. Owns the
recomposer handoff from `progress/reports/IMPL-gamestart.md`.

## Goal, in my own words

Give the game screen the Compose composition context it crashes without,
so first-frame measure finds windowRecomposer and pixels appear. Smallest
change: install the context at the window owner.

## Fault, as found in bytecode

The bundled `AbstractComposeView.resolveParentCompositionContext`
(decompiled from the translated tree, javap ground truth) resolves in
this order: `parentContext` field, then the `getCompositionContext` tag
walk over self plus ancestors, then `isAttachedToWindow`, then a walk to
the `android.R.id.content` root where it creates a lifecycle-aware window
recomposer (needs a `ViewTreeLifecycleOwner` tag, the main looper, and the
policy factory). The IMPL-gamestart crash died at the attach check.

Two framework gaps fed it. The game shows a progress dialog, so the run
loop swaps the root to `DialogOverlayGroup` and detaches the held game
tree; the overlay still measures that detached tree, so `ButtonsView`
reports unattached. And nothing ever installs a view-tree lifecycle owner
on the window decor: the translated `ComponentActivity` chain carries no
`ViewTreeLifecycleOwner.set` call, so even an attached view would die one
step later with `ViewTreeLifecycleOwner not found`.

## Change (framework window plus owner paths only)

- `framework/android/app/HeadlessWindow.java`: the constructor tags the
  decor with the owning activity under the lifecycle-runtime owner key
  (`2131296811`, ComponentActivity parity: owners live on
  `window.getDecorView()`). Plain activities fail the translated `as?`
  check, so the tag stays inert for them.
- `framework/android/app/DialogOverlayGroup.java`: one getter for the
  held background tree.
- `framework/android/app/Activity.java`: `attachRoot`/`detachRoot`
  helpers attach the overlay-held tree alongside the overlay at all three
  run-loop sites, so a measured tree always reports attached.

No translator, loader, input, or `verify-isakai/` file touched.

## Setup

- `mkdir -p .cache/isakai` plus `compositor`, `apks` from the main cache
  and `n6-bionic-build` from the main `.cache`. The bionic copy landed
  after the first build, so `rm -rf .zig-cache` plus rebuild was required
  before `libdl_bio.so.0` linked (the cached missing-artifact state from
  IMPL-gamestart, reproduced exactly).
- Unique env: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-recomp`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-recomp`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-recomp-stage`.
- Every launch sets `ISAKAI_FRAMEWORK=$PWD/zig-out/framework`.
- Private headless sway fixture from `.cache/isakai/compositor`.
- Fresh install of pinned `sgtpuzzles_149.apk` to `/tmp/isakai-game-recomp`.

## Gates

- `zig build` exit 0 before and after (the known transient first-run
  javac failure appeared after each cache wipe; immediate rerun exit 0).
- `git diff --check` clean.
- `python3 tools/progress-lint.py` 0 violations.

## Drives: windowRecomposer fault gone, pixels no

Drive 1 (`puzzles-drive.log`) plus identical-tap rerun (`puzzles-rerun.log`):
chooser maps, Solo starts (`GameLaunch(GENERATING_FROM_CHOOSER, solo)`),
native engine resolves params, overlay root installs. The
`Cannot locate windowRecomposer` throw is gone on both drives:
resolution now reaches `Wrapper_androidKt.setContent` through the
attach-time `onAttachedToWindow` path (`Activity.attachRoot` in the
stack). Both drives then die deterministically in the same deeper place:

```
Exception in thread "main" java.lang.VerifyError:
(class: androidx/compose/runtime/ComposerImpl,
 method: start-BaiHCIY signature: (IILjava/lang/Object;Ljava/lang/Object;)V)
Register 3 contains wrong type
    at androidx.compose.runtime.CompositionImpl.<init>(Unknown Source)
    at androidx.compose.ui.platform.Wrapper_androidKt.setContent(Unknown Source)
    at androidx.compose.ui.platform.AbstractComposeView.ensureCompositionCreated(Unknown Source)
    at androidx.compose.ui.platform.AbstractComposeView.onAttachedToWindow(Unknown Source)
```

Drive 1 line 17182, rerun line 17179, byte-identical fault. `game2.png`
is a black frame: no game pixels. Verdict on the task question: pixels no.

## Handoff: translator-emitted ComposerImpl plus one View gap

The remaining fault is outside the window/owner boundary, so per the task
tripwire this files the handoff instead of a fix.

1. Translator output for compose-runtime. `ComposerImpl.start-BaiHCIY`
   (value-class-mangled `start`) fails JVM verification with register 3
   holding the wrong type. The dex ships valid; the translated class is
   not. Note the contrast: Calculator's older compose-runtime translates
   and composes today (regression below), so this smells like a gap on
   newer compose bytecode shapes, not a wholesale compose failure. The
   next owner starts at the translator's emitter for this method.
2. Framework `View` gap, seen suppressed on the detach path:
   `NoSuchMethodError: android.view.View.clearViewTranslationCallback()`,
   called from translated
   `AndroidComposeViewTranslationCallbackS` during
   `AndroidComposeView.onDetachedFromWindow`. Once composition survives,
   every game-screen detach (dialog dismiss swaps roots) will throw this
   unless `View` grows the method.

## Regression: calculator flow PASS

`python3 verify-isakai/sway-flow.py calculator --work
/tmp/isakai-calc-recomp` on the changed framework: settings opened, the
decimal preference toggled and persisted across reopen, back navigated,
31s liveness, exit 0. The attach and decor-tag changes do not disturb the
working Compose app. Evidence beside the puzzles logs in
`evidence/2026-10-28/recomposer/`.

## Review (self-review, no independent reviewer ran)

Scope: the three-file diff above, plus the two drive logs and the
calculator evidence. Attach symmetry holds at all three run-loop sites;
attach and detach are idempotent on re-entry, so dialog dismiss churn is
safe. The decor tag is inert for non-`LifecycleOwner` activities (the
translated `as?` check skips it) and for apps whose owner key differs
(unread tag). No duplicate owner plumbing was added; the one new accessor
is package-private. Material findings: none in this diff. Remaining
limitation: the owner key is pinned to this Compose app line (recorded
above); a repinned APK reassigns it and the failure returns loudly as
`ViewTreeLifecycleOwner not found`, not silently.

## Cleanup

App processes dead (both drives end in the main-thread fatal). Fixture
stopped; `/tmp` scratch and unique env dirs removed;
`/tmp/isakai-game-recomp` and `/tmp/isakai-calc-recomp` kept because the
evidence logs reference their paths. No probes in tree, no `/tmp`
scripts, no shared progress file touched. `git status` shows this file,
the evidence dir, and the three framework files.

## Verdict

Pixels no. The window now owns the composition context path it crashed
without (proven: the fault moved past resolution into composition
start, deterministically across two drives), and the game screen waits on
the translator handoff above.
