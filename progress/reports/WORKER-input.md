# Worker report: input-delivery flake diagnosis + harness hardening

Date: 2026-10-27. Branch: `worktree-agent-a6743808871309926`.
Scope: `verify-isakai` harness only — no `framework/`, no `src/` changes.
Env: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-input`, `WAYLAND_DISPLAY=wayland-4`,
`XDG_RUNTIME_DIR=/tmp/isakai-wayland-input`,
`ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-input`,
`ISAKAI_STAGE_DIR=/tmp/isakai-1000-input-stage`, isolated headless sway.
Harness contract note: `sway_backend/vinput.py::isolated_env` hardcodes
`WAYLAND_DISPLAY=wayland-1` under the runtime dir, so the fixture sway was
launched with `WAYLAND_DISPLAY=wayland-1`; the shell-level `wayland-4` only
avoids clashing with other sessions. (Launching sway with `SWAYSOCK` set in
the environment suppresses its IPC socket; unset it before launch.)

## Diagnosis

**The flake is real, intermittent, position-independent whole-tap loss.**
Identical taps sometimes have zero app-observable effect, never a wrong
effect. Measured on the unmodified tree:

- Flow level (accepted arbiter, fresh OpenCalc install per run,
  `python3 verify-isakai/sway-flow.py opencalc`): **1 miss in 6 runs (17%)**.
  The miss is the canonical signature — red `12+34` + red `Syntax` preview
  with no new history row (`/tmp/input-evidence/flowbefore-5/`).
- Probe level (same tap primitive, `/tmp` throwaway scripts, since deleted):
  first-tap-of-series whole-tap loss reproduced 3/3 (app display unchanged
  >1s after tap). Mid-series per-tap log-absence (~25%) was shown to be
  mostly **stdout buffering artifact**, not delivery loss: miss screenshots
  showed the tapped digit already on the display (e.g. `55,555`, `5,555,555,555`)
  while the `Equation` log line arrived seconds later. Lesson: confirm
  delivery from durable app state (DB/prefs/screenshot), never the log tail.
- Geometry ruled out as the cause: misses hit fat center-key targets
  (digit `5` at window-relative `(240,510)`) at the same rate; window rect
  was `(100,100,640,800)` on every tap; longer hold (300ms vs 80ms) did not
  change the pattern.

**One concrete silent-miss mechanism reproduced in-harness:** the app window
maps transiently small (observed `640x480`) and grows to the `640x800`
contract within ~1s. A fixed-contract tap issued in that window (e.g.
`(560,750)`) lands ~270px below the window on the background and vanishes
with no error. Nothing validates tap coordinates against the live rect.

**Adjacent observation (out of scope, not changed):** rapid-fire commits
crash OpenCalc's history RecyclerView (`IndexOutOfBoundsException:
Inconsistency detected`, adapter `z0.e`) — an app/framework race under
burst input, distinct from the delivery flake. Probes were slowed to 4s
settles to stay out of that regime.

Root loss mechanism below the harness (vinput→sway→socket→bridge→view)
remains unidentified — consistent with the prior C6 reading (256-deep queue
with latch, pointer path bypassing touch assembly; no in-repo drop found).
The hardening below assumes only what was measured: taps can vanish, so
never tap blind and never accept a single unconfirmed commit tap.

## Diff (3 files, +82/−15)

- `verify-isakai/sway_backend/compositor.py`
  - `_await_inside(x, y)`: re-reads the live rect until the tap point is
    inside it (bounded 5s for resizes), else raises `SwayError` naming
    coords + rect instead of tapping the background silently. Used by
    `click`, `click_window`, `down_window`, `drag_window` (both endpoints).
  - `attempt_until(tap, wait, tries=3, label)`: runs `tap()` until `wait()`
    observes the app-side effect; records `{step: label, attempts: n}` in
    flow steps as evidence. `wait()` must read durable app state
    (DB/prefs), never the process log. Re-taps only while no effect exists,
    so a landed tap is never doubled — its durable record stops the loop.
- `verify-isakai/sway_backend/flows_opencalc.py`: `=` commit goes through
  `attempt_until` (3 tries, 8s history polls); 2.0s post-commit settle kept
  so the copy step runs on the proven cadence.
- `verify-isakai/sway_backend/flows_calculator.py`: both keypad commits
  (`2+2=`, decimal) retry the whole clear+digits+`=` sequence until the
  Room history shows the new row (clear-first, so a retry can't double).

`hold_ms=80` and all coordinates are unchanged — before/after runs tap
identically; only confirmation was added.

## Before/after numbers (honest)

| Series | Tree | `=`-miss FAIL | Other FAIL | PASS |
|---|---|---|---|---|
| BEFORE `sway-flow.py opencalc` x6, fresh install/run | unmodified | 1/6 (run 5, preview-no-row) | 0 | 5/6 |
| AFTER `sway-flow.py opencalc` x6, fresh install/run | hardened (no post-commit settle yet) | 0/6 | 1/6 run 4: commit landed (`46` recorded) but clipboard copy held sentinel; stale display (`12+34`, no `46`) suggests the copy ran mid-settle — 2.0s settle restored after | 5/6 |
| AFTER-EXT `sway-flow.py opencalc` x6 more (runs 7–12) | hardened + settle | 2/6 (runs 9, 12: **triple-miss clusters**, see below) | 0 | 4/6 |

`=`-miss class: **1/6 before → 2/12 after** (matched 6-run protocol plus a 6-run
extension). No rate improvement is demonstrated — and the retry never absorbed
a live miss: every passing after-run committed on attempt 1 (`attempts: 1`
in steps evidence). The after-misses are a harsher mode than the historical
signature: **multi-tap dead periods** (runs 9 and 12: three `=` taps over
~25s, all with zero effect, while keyboard typing in the same runs logged
normally and geometry/focus checks passed). A bounded harness retry covers
isolated single-tap misses; it cannot cover a 25s pointer-dead period, so
runs 9/12 fail loudly with the precise `...had no app-observed effect after
3 taps` message instead of the old 20s-timeout message — diagnostic value,
not a pass. Prime suspect for the clusters, unproven: per-launch app-side
pointer-object setup (keyboard path alive, pointer motion+button dead,
all-or-nothing per launch, no harness-visible signal). Proving it needs an
instrumented session with bridge seat/pointer logging — `src/` scope, out of
bounds here; recommended next step, not this diff.

## Flow verification on the change

- calculator: PASS (fresh tree; `2+2=4`, decimal flip `1,5+2,5=4`,
  persistence, 31s liveness, back exit 0; both commits `attempts: 1`).
- opencalc: PASS (fresh tree; `12+34=46`, clipboard `46`, resize, 31s
  liveness, clean close) — plus the 12 after-series passes above.
- notes: BLOCKED by a pre-existing app-side failure, not this change:
  6 consecutive runs fail `the new note did not persist its exact Unicode
  contents` — every tap demonstrably lands (dialogs open, `Isakai 日本語 –
  note` visible in the editor, picker opens) but the save-before-picker
  never writes the row. Reproduced identically on the **unmodified tree**
  (`git stash` control), matching the intermittent C6 baseline record.
  Needs its own session; input delivery is exonerated.

## Gates

`zig build` exit 0 before and after (no `framework/`/`src/` changes);
`git diff --check` clean; `python3 tools/progress-lint.py` 0 violations.
No test files added (policy); all `/tmp` probe scripts deleted after use;
sway + apps stopped at close. Isolated flows only — no privileged run
(no auth agent in this session); default confinement still needs an
operator run and is unaffected by this diff.
