# IMPL-verifyerror: translator VerifyError diagnosis (N6 remaining, step 1)

Date: 2026-10-28. Branch: `worktree-agent-aa29be409dbc73652`. Scope:
diagnosis only. No `src/`, `framework/`, or `verify-isakai/` file was
modified. No code fix was proposed beyond naming the emitter.

## Goal, in my own words

Capture the exact JVM VerifyError that blocks translated game layout and
name the translator function that emits the bad bytecode. Diagnosis only,
no code changes.

## Setup

- `mkdir -p .cache/isakai` + copied `compositor` and `apks` from the main
  checkout cache (`.cache` is gitignored; tree stays clean).
- Bionic artifacts copied from main's `.cache/n6-bionic-build` (four pinned
  `*_bio.so.0`, hashes match WORKER-n6 bit-for-bit); `rm -rf .zig-cache`
  was not needed this run.
- Unique env for all commands: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-verr`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-verr`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-verr-stage`.
- Private headless Sway fixture from this worktree's
  `.cache/isakai/compositor` (swaybg warning only, known non-fatal).
- Fresh install of pinned `sgtpuzzles_149.apk` (sha256
  `6b36d5537984523c57984a784156eb853a92c940fded286483687484f3d652e4`,
  matches `progress/APPS.md`) to `/tmp/isakai-verr-work`.
- Read first: `research_notes/Remaining N6 N18 fix plan/
  verifyerror-translation.md`, `progress/reports/WORKER-n6.md`,
  `progress/reports/IMPL-pr1-sealed.md`, `verify-isakai/SKILL.md`.

## Gates

- `zig build` exit 0 on rerun (first run hit the known transient javac
  failure also recorded in IMPL-n18-proof; immediate rerun exit 0 with no
  source change).
- `git diff --check` clean (no source edits).
- `python3 tools/progress-lint.py` 0 violations.

## App-flow drives (what ran instead of the old abort path)

Two launches under the private fixture, same command shape as WORKER-n6
Drive B (`ISAKAI_NO_PRIVILEGE=1`, `ISAKAI_WINDOW=1`,
`ISAKAI_NO_BRIDGE=1`, unique bridge/stage/wayland dirs):

1. `--data "sgtpuzzles:cube:c4x4:5B10,2"` → exit 1, **no VerifyError**.
2. `--data "sgtpuzzles:nosuchgame:foo"` (deliberate abort-path probe) →
   exit 1, **no VerifyError**.

Both die earlier than WORKER-n6's run, in `GameChooser.onResume`, with:

```
java.lang.NoSuchMethodError: 'void android.view.View.setActivated(boolean)'
    at name.boyle.chris.sgtpuzzles.GameChooser.onResume(Unknown Source)
```

`framework/android/view/View.java` has no `setActivated` at all, so the
chooser cannot resume and `GamePlay` (hence the abort dialog) never
starts. That is a separate framework API gap, out of scope here; it is
why the live flow cannot currently reach the layout code. No intent
string avoids it (both valid-game and bogus-game URIs crash identically).

## VerifyError reproduced (direct class load, same translated bytes)

The translated `AlertDialogLayout` class from the fresh install was
loaded with `Class.forName` under jshell (classpath: translated
`classes/` first, then `zig-out/framework`). Full text:

```
java.lang.VerifyError: (class: androidx/appcompat/widget/AlertDialogLayout,
  method: onMeasure signature: (II)V) Bad access to protected data
```

Identical class, method, and detail string to WORKER-n6's app-flow
report. This error kind carries no `bci=` offset (there is no branch or
type mismatch to point at; the receiver's static type is wrong), so the
fault is located by enumerating the protected accesses in `onMeasure`
instead. Evidence: `evidence/2026-10-28/verifyerror/jshell-load.txt`.

## Fault site (javap)

- `javap -v`: major version **49**, minor 0, **zero** `StackMapTable`
  entries — frameless inference verification, as the research note
  predicts. Evidence `AlertDialogLayout.javap-v.txt`.
- Full `onMeasure` disassembly: `onMeasure.javap-c.txt` (504 lines).
- The only protected member invoked in `onMeasure` is
  `android.view.ViewGroup.measureChildWithMargins` (framework javap:
  `protected void measureChildWithMargins(...)`; evidence
  `framework-ViewGroup.javap-p.txt`). Every other callee is public in
  the framework or in the translated classes (`View.setMeasuredDimension`
  is public in Isakai's framework, `LinearLayoutCompat.onMeasure` is
  public, `resolveMinimumHeight` is an own public static).
- Offsets 994–1008:
  `aload_3; checkcast android/view/ViewGroup; ... ;
  invokevirtual ViewGroup.measureChildWithMargins`.
  The receiver's verifier type is `ViewGroup`, but the current class is
  `androidx.appcompat.widget.AlertDialogLayout` (different package from
  `android.view`, subclass of `ViewGroup` via translated
  `LinearLayoutCompat` — evidence
  `translated-LinearLayoutCompat.javap-p.txt`). JVMS protected-access
  requires the receiver to be the current class or a subclass, so this
  is exactly "Bad access to protected data".
- AndroidX source cross-check (`androidx-source-excerpt.txt`, upstream
  `AlertDialogLayout.java` `tryOnMeasure`): both `setMeasuredDimension`
  and `measureChildWithMargins(child, ...)` are unqualified `this`
  calls. javac emits `this` with the current-class type; the translator
  emitted a `ViewGroup`-narrowed receiver.
- Slot-3 audit (the receiver's JVM local): the only stores are
  `aconst_null` (prologue zero) and two `aload_0; astore_3` (`this`).
  The slot's natural inference type is therefore `AlertDialogLayout`
  and the access would verify **without** the checkcast. The checkcast
  alone is the fault, not a branch merge (no merge type conflict), not
  a descriptor (all descriptors resolve), and not new/init pairing.

## Emitter pointer (read-only, no edit)

`emitInvokeArgs` in `src/translate.zig` (line 1555), specifically the
receiver-checkcast block (~lines 1586–1633). Its skip guards already name
this hazard ("narrowing a current-class receiver to the super breaks
protected access verification"), but the `this` proof,
`receiverIsThis` (line 803), is a bounded straight-line backward scan
(`isThisAlias` stops at any branch). Here `this` reaches the receiver
register through loop-carried `aload_0; astore_3` stores, so the scan
returns false and the checkcast to the owner (`android/view/ViewGroup`,
a grandparent — hence not caught by the direct-super skip either) is
emitted. Fix territory (not done here): teach the skip to recognise
loop-carried `this` (e.g. a reaching-definitions check over the
precomputed preds, the same machinery `reachingDefsAllInt` already
uses), or skip the checkcast whenever the owner is a superclass of the
current class and the receiver slot's reaching stores are `this`-only.
Category: **slot discipline** (receiver static-type discipline at invoke
sites).

## Cleanup

Fixture (sway) stopped; `/tmp/isakai-verr-work`, `/tmp/verr-ev`,
`/tmp/sway` scratch, and the unique env dirs removed. `git status`
shows only this file plus `evidence/2026-10-28/verifyerror/`. No probes
in tree. No shared progress file touched.

## Verdict

Emitter named with evidence: `emitInvokeArgs` receiver-checkcast logic
(`src/translate.zig:1555`, skip-guard failure at ~1586–1633 via
`receiverIsThis` at :803). Backend for step 2: remove or condition that
checkcast for superclass owners with `this`-only reaching stores;
proof bar is `Class.forName` load clean plus the app-flow abort dialog
rendering (once the separate `View.setActivated` framework gap lets the
chooser resume).
