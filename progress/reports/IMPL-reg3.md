# IMPL-reg3: register-3 VerifyError in translated ComposerImpl start

Date: 2026-10-28. Branch: `worktree-agent-abfe5a05e0540b180`. Owns the
translator handoff from `progress/reports/IMPL-recomposer.md`.

## Goal, in my own words

Clear the register-3 VerifyError in translated ComposerImpl start code,
so game composition runs past start into pixels. Smallest change: fix
the emitter that misassigns the register.

## Fault, as found in bytecode

Pre-fix drive reproduced the handoff crash byte-identically. Solo
starts (`GameLaunch(GENERATING_FROM_CHOOSER, solo)`), then the main
thread dies in `CompositionImpl.<init>` with the register-3 VerifyError
and the process exits. Full text plus stack retained in
`evidence/2026-10-28/reg3/puzzles-drive.log` line 17464.

Disassembly of the translated class (`javap -c`, slice in
`composer-start-before.txt`) showed JVM local 3 loaded both ways. Local
3 is the first Object parameter. Two `if-nez` sites emitted `iload_3`
with `ifne` (JVM 2822, 2866) while every other use emitted `aload_3`.
One local used as int and reference fails verification at class load.
`javap -v` confirms frameless v49 output (major 49, no StackMapTable)
with stack 13, locals 50, args size 5.

DEX ground truth (throwaway `/tmp` disassembler, deleted after use):
the method has 31 registers with 5 ins, so the Object parameter lives
in v29 and maps to JVM local 3. v29 is never stored. It is null-tested
with `if-nez` at DEX 858 and 870, then `move_object`d into v8 and v4.
Both are legitimate reference uses. The poisoning came from DEX 456
and 568 (`const-wide/16 v28`): R8 reused the dead int parameter v28
for a long temporary, and the translator's `storeTracked` marked the
pair half (`types[v29] = .long`) as a side effect. The linear tracker
carried that `.long` across the control-flow boundary to DEX 858,
where the `ifz` arm trusted it and emitted the int form. The backward
truth is reference: no store to v29 reaches either site, and the entry
value is an Object parameter.

## Change (src/translate.zig, ifz arm only)

Valid DEX never tests a long, double, or float with `ifz`. Only int
and reference are testable there. A wide or float linear type at an
`ifz` is therefore definitionally stale, leaked from a non-reaching
path. The arm now resolves that state flow-sensitively with the
existing `reachingDefsAllInt` walk instead of trusting the tracker.
All other states lower exactly as before. The diff is three code lines
plus a five-line comment. No allocation, mapping, or prologue change.

The store side was deliberately left alone. The pair-half mark is the
first wrong record, but removing it risks genuine wide pairs, while
the read-side fix only alters a state whose old output was always a
VerifyError (an `iload` of a wide slot, or an int versus reference
split on one local). For float bits the walk returns int through the
producers' int kind, so float-bit lowers are byte-identical.

## Setup

- `mkdir -p .cache/isakai` plus `compositor` and `apks` from the main
  cache, plus `.cache/n6-bionic-build` from main. Same cached
  missing-artifact state as the gamestart runs, so `rm -rf .zig-cache`
  plus rebuild preceded the green build.
- Unique env: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-reg3`,
  `XDG_RUNTIME_DIR` same, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-reg3`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-reg3-stage`.
- Every launch sets `ISAKAI_FRAMEWORK=$PWD/zig-out/framework` and
  `ISAKAI_NO_PRIVILEGE=1` (same-UID development mode; the install
  helper has no privilege path here).
- Private headless sway fixture from `.cache/isakai/compositor`.
- Fresh installs of pinned `sgtpuzzles_149.apk` (sha256 `6b36d553…3d652e4`)
  to `/tmp/isakai-game-reg3` and pinned calculator to
  `/tmp/isakai-calc-reg3`.

## Gates

- `zig build` exit 0 before and after the edit.
- `git diff --check` clean.
- `python3 tools/progress-lint.py` 0 violations.

## Drives: VerifyError gone, composition runs past start, pixels no

Post-fix drive on a fresh install (`puzzles-fixed.log`): Solo starts,
no VerifyError on any drive line, and the translated `start-BaiHCIY`
loads clean. The fixed class has zero `iload_3` and five `aload_3`
(slice in `composer-start-after.txt`); both fault sites now emit
`aload_3` with `ifnonnull`. Execution runs through
`CompositionImpl.composeInitial`, the `Recomposer`, and
`ComposerImpl.doCompose-aFTiNEg` into
`ProvideAndroidCompositionLocals`. That call chain is the proof. Class
load plus composition past start both pass on the real artifact.

Game pixels are not reached. First composition now dies in
`HapticDefaults` with `NoSuchMethodError:
'boolean android.os.Vibrator.areAllPrimitivesSupported(int[])'`
(`puzzles-fixed.log` line 17463). The suppressed
`View.clearViewTranslationCallback` gap from the recomposer handoff
still rides along. Both need framework files, which sit outside this
task's translate-only boundary, so per the tripwire they file the
handoff below instead of a fix. `game-after-fix.png` records the
post-fix frame at the new fault.

## Regression: calculator flow PASS

`python3 verify-isakai/sway-flow.py calculator --work
/tmp/isakai-calc-reg3 --evidence evidence/2026-10-28/reg3/` on the
fixed build: exit 0 with `PASS: calculator flow (isolated sway)`.
Log plus screenshots beside the puzzles evidence. The `ifz` change
does not disturb the working Compose app. `verify-isakai/` was driven
read-only and never modified.

## Review (self-review, no independent reviewer ran)

Scope: the one-arm `translate.zig` diff, the before and after
disassembly slices, the DEX slice, both drive logs, and the
calculator evidence. The use survey is complete for the two fault
registers. v28 has no int use after the wide stores, and v29 has no
other int-position use anywhere, so no second miscompilation of this
pair hides behind the fixed sites. The walk terminates (visited-set
DFS, already trusted by the conflicted path in the same arm) and runs
at most once per `ifz` on an already-impossible state, so working
methods pay nothing. `paramKind` returns int for float parameters and
wide producers report int kind, so float-bit lowers are unchanged.
Material findings: none in this diff. Remaining limitation: sibling
poisonings outside `ifz` (an int-position invoke argument reading a
pair-clobbered register) keep the old behavior; no such site exists
in this method, and the drive would fail loudly with the register
number if one appears elsewhere.

## Handoff: framework stubs for first-composition locals

Composition is proven past start and waits on two framework methods,
both outside the translate boundary.

1. `android.os.Vibrator.areAllPrimitivesSupported(int[])Z`,
   called from Compose `HapticDefaults` during
   `ProvideAndroidCompositionLocals` on every first composition.
   Every Compose screen dies here until `Vibrator` grows it.
2. `android.view.View.clearViewTranslationCallback()`, called from
   translated `AndroidComposeViewTranslationCallbackS` on every
   game-screen detach (carried over from the recomposer handoff;
   still suppressed, still fatal once reached).

## Cleanup

Both app processes dead (each drive ends in its main-thread fatal).
Fixture stopped; `/tmp` scripts, dumps, build logs, pids, and unique
env dirs removed. `/tmp/isakai-game-reg3` and `/tmp/isakai-calc-reg3`
kept because the evidence logs reference their paths. No probes in
tree, no shared progress file touched. `git status` shows this file,
the evidence dir, and `src/translate.zig`.

## Verdict

VerifyError fixed. The register-3 fault is gone from the emitted
class, the class loads clean, and composition runs past start into
`doCompose` on the real artifact. Game-screen pixels wait on the
framework handoff above.
