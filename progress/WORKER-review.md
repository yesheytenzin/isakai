# WORKER-review: audit of the stacked C6 changes (fef2eb8..HEAD)

Scope: read-only review of the three stacked changes against their claimed
mechanisms in WORKER-compose / WORKER-input / WORKER-picker / WORKER-c7 /
WORKER-gaps. No builds, no app drives. Diff audited:
`git diff fef2eb8 HEAD -- src framework verify-isakai`
(plus fef2eb8 itself for change 3, which is the base commit).

## 1. `src/dbus_bridge.zig` poll nonblocking drain (fcacc82) — CORRECT, no blocker

Claim: the `"poll"` op's up-to-1 s blocking settle stalled every
window-loop slice at ~1 Hz, delaying input, recompose and redraw; removing
it leaves pickup latency at one slice (~100 ms). Verified against the code:

- Old code looped `pumpOnce(br, 100)` while the queue was empty up to
  1000 ms, on the thread that serves the round-trip. New code does the
  nonblocking `drainBus` and returns. One hunk, comment preserves rationale.
- Blast radius (every window-loop slice): `Activity.java` window loop calls
  `window.run(100)` then `pollDesktopResults()` → `IsakaiDesktop.poll` →
  native `desktop_mod.poll` → `{"op":"poll"}` **once per slice**, plus
  `pollInput`/`pollSize`/`pollMetrics` on separate ops (untouched).
  The ~100 ms next-slice claim matches the `run(100)` quantum. No protocol,
  Java, or translator change; no other `src/` caller depends on the settle.
- File-picker cancel path (the old settle's stated reason): cancel arrives
  as kind-12/status-1 through the same `pollDesktopResults`, so worst case
  goes from "blocked behind 1 s" to "next slice". Strictly better; the
  compose worker's "reasoned, not proven — assert cancel in a future
  picker flow" caveat is honest and sufficient.
- Non-finding: no busy-spin introduced — the loop still sleeps in
  `window.run(100)`; the bridge just stops adding its own second wait.

## 2. `sway_backend` hardening (5ad040e: blind-tap refusal + commit retry) — CORRECT, no blocker

Claims: (a) fixed-contract taps can land on the background during transient
post-map resizes and vanish silently; (b) single taps intermittently never
register, so commit taps retry until durable app state confirms them.
Verified:

- `_await_inside` re-reads the live rect (bounded 5 s) and raises `SwayError`
  with coords + rect instead of tapping blind. Wired into `click`,
  `click_window`, `down_window`, `drag_window` (both endpoints, rect
  re-read after — coordinates identical when the rect is stable, so no
  behavior change on the happy path). `type_text`/`key` correctly exempt
  (keyboard path, no coordinates).
- Acceptance-gate semantics hold — a PASS still requires durable app state:
  calculator `wait_for_new_row` reads the app's own Room `history` table
  via sqlite snapshot (`select id, formula, result ...`, monotonic-id gate);
  opencalc `wait_for_result` reads the app's own `HISTORY_ELEMENTS` prefs
  entry with the N1 `baseline_count` fresh-result gate intact. The retry
  only issues more taps; the predicates are unchanged, so it cannot
  manufacture a PASS. On exhaustion it raises (loud FAIL), never passes.
- No-double-commit reasoning is sound for both call sites: calculator
  retries the whole clear-first sequence (idempotent); opencalc retries the
  single `=` only while no new history row exists. Residual edge (a landed
  commit whose row stays unreadable for the full 8 s poll, then a second
  `=` double-commits) is theoretical — the row write is local and the poll
  is 8 s; and the worker's own data shows every passing run committed on
  attempt 1, i.e. the retry has never fired live.
- Limits honestly recorded by the worker and confirmed as out-of-scope for
  this diff: no rate improvement demonstrated (1/6 before → 2/12 after,
  matched protocol); triple-miss 25 s pointer-dead clusters fail loudly by
  design (a bounded retry cannot cover them; prime suspect is app-side
  pointer setup, needs an instrumented `src/` session). Notes 6/6 save
  failure reproduced on the stashed unmodified tree — pre-existing,
  exonerated.
- Non-findings: `attempt_until` appending steps only on success is fine
  (the failure message carries the tap count); the double `ensure_focus`
  in `click` is one extra sway round-trip, negligible; `drag_window`'s
  second `_await_inside` overwriting `node` is correct (freshest rect wins).

## 3. `framework/android/view/View.java` leaves-only dispatch (fef2eb8, the base) — CORRECT, no blocker

Note: this change is not in fef2eb8..HEAD — it *is* fef2eb8, so there is no
second View.java change stacked on top. `git diff fef2eb8 HEAD -- framework`
is empty, confirming the picker worker's "no banned files touched" claim.

- Mechanism checks out: `drawAt` previously invoked only `dispatchDrawAt`
  (the ViewGroup override point), so translated leaf views' custom drawing
  in the AOSP `dispatchDraw` hook never ran (Compose layers blank). The fix
  routes leaves to the new empty-default `dispatchDraw(canvas)` while groups
  keep `dispatchDrawAt`. Framework leaves don't override `dispatchDraw`,
  so it is a no-op for them — blast radius is every view's `drawAt`, but
  behavior changes only for translated leaves that override the hook.
- The picker handoff (whitewash default, `drawableStateChanged`/
  `refreshDrawableState`, `InsetDrawable` propagation, `ImageView` src) is
  explicitly *not* landed anywhere in the stack — correct per the task's
  file ban, recorded here so nobody assumes the blob/bars are fixed.

## Stack-level notes (not findings against any diff)

- Notes persistence reads inconsistently across reports (compose: PASS with
  one driver-side hang flaked; input: 6/6 save failure incl. unmodified
  tree; quiet re-verify 7288743: "notes save intermittent"). Pattern says
  flaky app-side save, not harness or bridge — needs its own session.
- C7's calculator settings-tap (580 → converter) and notes confirm-tap
  no-ops predate the hardening (C7 tree had no code changes) and no diff
  here touches coordinate mapping or hit-testing; the tap-offset hypothesis
  stays with the C6/framework owner.
- Gaps verdicts (scrim tap/hold dismisses correctly; XKB stress excluded by
  artifact policy) are evidence records with no code attached — nothing to
  audit, nothing blocking.

## Verdict

No change blocks the stack. (1) Bridge poll drain: ship. (2) Harness
hardening: ship — gate semantics preserved, failures fail loud.
(3) Leaves dispatch: ship as base — picker handoff remains open work in
banned files. Explicit non-findings: no busy-spin, no false-PASS path, no
double-commit, no banned-file edits, no test files added (policy clean).
