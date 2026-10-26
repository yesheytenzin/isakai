# Fix-all: review + audit findings (todolist)

Playbook: figure-it-out (no narrower playbook fits 37 fixes across 6 areas).
Rigor: high for sandbox/installer (one-way doors), medium for framework parity,
low for nice-to-haves. No arena: every fix shape is already specified.

## Done predicate (falsifiable)
- Every fix below applied or explicitly skipped with reason.
- `zig build` exit 0, `git diff --check` clean.
- Calculator pinned flow PASS post-change (same-UID), proving no regression
  on the exercised path. Privileged-only paths verify by build + code
  evidence (sudo blocked); recorded as such, not claimed as flow-proven.

## Phases
- [x] Phase A: frame (done predicate, scope, rigor above). Checkpoint: user
  granted full autonomy ("fix all"); tree stays uncommitted (no commit asked).
- [x] Phase B: design (6 workers across disjoint file seams, shared worktree;
  workers must NOT run builds — cache contention — verification centralized).
- [x] Phase C: run (workers report before/after evidence per fix).
- [x] Phase D: trail (.audit/fixall.tsv, one row per unit).
- [x] Phase E: verify (build + calculator flow) and hand back.

## Units
- [x] A privileged boundary: F1 18->19, F2 fail-closed, F17 path trust,
  F18 fixed pkexec path, N2 --data cap. Files: uid_helper, confine,
  launch, main. (src/*)
- [x] B installer/registry: F3 verify staged base.apk, F4 exact-name
  uninstall, F6 pid identity, F13 version compare, F14 StagingExists reuse,
  F15 registry fsync+tmp, F16 uninstall order, stale comments. (install,
  registry, upgrade, compat-report)
- [x] C translator: F19 fail-open tables/payloads/offsets, F20 method
  budget + pool caps, F21 .so header bounds + relabel report.
  (translate, dex, install .so gate only)
- [x] D framework Java: F8 pop-all-above, F9 activity meta-data, F10 qualify
  in contains(), F11 theme+chained overrides, F12 dangling ref + dp sizes,
  F30 hex ids, N1 reset-before-mark, audit F5 SharedPrefs commit status.
- [x] E native bridge: audit F7 nMapBuffer length, F22 _r_debug struct,
  F23 dlerror clear/record, F24 dynamic lib table, N4 sqlite handles+txn,
  N5 input ring flag, N6 buffer generation + clipboard false.
- [x] F resources/anims/dialogs: F25 AlertDialog button+Builder, F26
  hardKeyboardHidden packing, F27 native Theme.setTo(null), F28
  reverse int/object, F29 press/release set, N7 legacy parser deletion
  (ONLY if pinned flows pass on native per existing evidence logs).
- [x] Skipped by design (record, do not implement): delete Keyboard/
  KeyboardView/CountDownTimer/LogPrinter/Printer (other owners' fresh
  files); delete loadStateListAnimator body (marked shortcut with trigger).
- [x] Central verify: `zig build` green after one fix-round (unused `exe`
  param in launch.zig); `git diff --check` clean; Calculator flow PASS on
  a fresh tree (fixall-calc, same-UID). Fraction base (1,1) in F12 matches
  the AOSP manifest meta-data convention. N4 transaction wrap stays blocked
  (needs the ContentProvider.java owner); N7 legacy parser kept (gate failed
  honestly: pinned evidence runs on the staged parser).
