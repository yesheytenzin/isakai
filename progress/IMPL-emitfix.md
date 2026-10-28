# IMPL-emitfix: loop-carried this receiver proof plus View.setActivated (N6 remaining, step 2)

Date: 2026-10-28. Branch: `worktree-agent-a503aefe1664277a8`. Scope:
two files only: `src/translate.zig` (receiver proof) and
`framework/android/view/View.java` (setActivated). Diagnosis taken as
exact from progress/reports/IMPL-verifyerror.md; nothing re-derived.

## Goal, in my own words

Stop the translator from narrowing a `this` receiver to a grandparent
owner, so the translated AlertDialogLayout verifies and the abort
dialog can inflate. Smallest change: teach the receiverIsThis proof to
see `this` arriving through loop-carried stores, plus add the one
missing framework method the chooser needs to resume.

## Diff

`src/translate.zig` (receiver proof, same file only):

- New `receiverReachesThisOnly`: backward reaching-definitions DFS
  over the precomputed preds, same shape as `reachingDefsAllInt`. A
  store to the receiver ends its path well only when it is a
  move-object from `this` (or from a temp that is itself `this`-only
  at the move, depth-capped recursion for R8 copy chains). Any other
  store vetoes. Entry values: non-`this` params veto (a live foreign
  value could reach); non-param temps enter as prologue null, which
  verifies like `this` for protected access. Dead units contribute
  nothing. Payload-safe: the walk follows pred links only, which join
  instruction starts, never payload words.
- `receiverIsThis` falls back to it when the straight-line
  `isThisAlias` scan fails (that scan stops at any branch, so it
  misses loop-carried `this`).
- `preds` threaded through `emitInvokeCall` to `emitInvokeArgs` (two
  call sites); no other signature or file touched. The skip stays
  sound: a `this`-only receiver has the current-class type, assignable
  to every superclass owner, so omitting the checkcast cannot fail
  verification or change runtime behavior.

`framework/android/view/View.java` (setActivated only):

- `activated` field plus `isActivated` getter plus
  `setActivated(boolean)` mirroring `setSelected` exactly (guard,
  assign, `invalidate`, `refreshDrawableState`).

No probes remain; three temporary framework prints used during diagnosis
were fully reverted (final `git diff` shows only the two files above).

## Setup

- `mkdir -p .cache/isakai` plus copied `compositor` and `apks` from
  the main checkout cache.
- Bionic artifacts copied from main's `.cache/n6-bionic-build` (four
  pinned `*_bio.so.0`, hashes match WORKER-n6); `rm -rf .zig-cache`
  plus rebuild so the artifacts install under
  `zig-out/framework/lib/bionic/`.
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-emitfix`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-emitfix`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-emitfix-stage`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (swaybg warning only, known non-fatal).
- Fresh installs of pinned `sgtpuzzles_149.apk` and
  `calculator-fdroid-release.apk` with `ISAKAI_NO_PRIVILEGE=1`.
- Critical env lesson: launches must set
  `ISAKAI_FRAMEWORK=$PWD/zig-out/framework`. Without it the launcher
  falls back to the distro `/usr/share/isakai/framework`, which
  predates the trampoline fix and `setActivated`; every puzzles launch
  then dies instantly in SGTPuzzles with no error. This cost most of
  the session to isolate (temporary prints proved no framework hop
  fired; `main.zig` resolution plus a javap of the installed copy
  closed it).

## Gates

- `zig build` exit 0 before the fix, after the fix, and after the
  probe revert (the known transient first-run javac failure appeared
  once after the cache wipe; immediate rerun exit 0).
- `git diff --check` clean.
- `python3 tools/progress-lint.py` 0 violations.

## Verification evidence

Translator fix, real artifact (`/tmp/isakai-emitfix-work4`, fresh
install, fixed translator):

- `evidence/2026-10-28/emitfix/jshell-load.txt`: `Class.forName` for
  `AlertDialogLayout`, `LinearLayoutCompat`, and `GameChooser` all
  load clean. The baseline (pre-fix) run reproduced the exact
  `VerifyError: Bad access to protected data` on the same class.
- `evidence/2026-10-28/emitfix/AlertDialogLayout.javap-c.txt`: zero
  `checkcast android/view/ViewGroup` in `onMeasure` (was four, one
  before each protected access site); the protected
  `ViewGroup.measureChildWithMargins` invoke remains at offset 978 and
  now verifies with the natural `AlertDialogLayout` receiver type.
- Pre/post translation diff of `SGTPuzzles`, `TaskStackBuilder`,
  `GameChooser`: byte-identical. `GamePlay` differs only by removed
  narrowing checkcasts plus branch-target shifts, no logic change.

App flows, private fixture, fresh framework:

- `evidence/2026-10-28/emitfix/puzzles-chooser.log`: chooser path, no
  intent. `GameChooser start`, `GamePlay start`, no `setActivated`
  crash (previously `NoSuchMethodError` in `onResume`). Window mapped
  (`sgtpuzzles` visible in sway tree).
- `evidence/2026-10-28/emitfix/puzzles-chooser.png`: chooser renders
  the full game grid with icons and labels on screen.
- `evidence/2026-10-28/emitfix/puzzles-abort.log`: bogus-game URI
  path. `GamePlay start`, `URI is: "sgtpuzzles:nosuchgame:foo"`, then
  the exact abort chain from the diagnosis (`abort <- onNewIntent <-
  onResume` into `messageBox -> AlertDialog.onCreate ->
  setContentView -> LayoutInflater.inflate`). No VerifyError anywhere.
  The translated layout class instantiates and inflates at runtime.
- Calculator regression: `PASS: calculator flow (isolated sway)` with
  the fixed translator and fresh framework
  (`com.simplemobiletools.calculator.sway-calculator.log` plus
  `simple-*.png` in the same evidence dir).

## Handoff: abort dialog pixels blocked by InputDevice$MotionRange

The abort run dies inside dialog inflation with:

```
java.lang.NoClassDefFoundError: android/view/InputDevice$MotionRange
    at LayoutInflater.reflectView (getConstructor)
    ... AlertDialog.onCreate <- Dialog.show <- GamePlay.messageBox
Caused by: ClassNotFoundException: android/view/InputDevice$MotionRange
```

`framework/android/view/InputDevice.java` has no `MotionRange` nested
class. Some inflated dialog view references it in a constructor
signature, so layout inflation cannot complete and the dialog never
reaches the screen. Adding it is a third file outside this task's
boundary (`translate.zig` receiver logic plus `View.setActivated`
only), so per the boundary rule this step stops here and hands off:
add `InputDevice.MotionRange` (AOSP shape: source, axis, min/max,
flat, fuzz, resolution accessors) in its own change, then rerun the
bogus-game URI drive and screenshot the dialog. Everything this step
owns is proven above; the handoff is one missing framework nested
class, not a translator problem.

## Cleanup

Fixture (sway) stopped; app closed through the compositor (`destroyed`,
`run complete`, exit 0 for the chooser run); `/tmp` scratch
(strace-less logs, pid files, sway log) deleted; work dirs left under
`/tmp` as evidence paths reference them. `git status` shows only this
file, `src/translate.zig`, `framework/android/view/View.java`, plus
`evidence/2026-10-28/emitfix/`. No probes in tree. No shared progress
file touched. `verify-isakai/` read but never modified.

## Verdict

Fixed within the file boundary: the receiver proof sees
loop-carried `this` (VerifyError gone, proven by load plus runtime
inflation) and `View.setActivated` exists (chooser resumes and
renders, proven by screenshot). Calculator regression passes.
Dialog-on-screen pixels need the `InputDevice$MotionRange` handoff above.
