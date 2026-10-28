# Worker: erase-preserving picker whitewash fix (erasefix) — 2026-10-28 (agent-aa7778d3afbf40cce)

Status: **done** — implemented, verified, isolation repaints clean. Picker whitewash fixed, no repeated-pass regression.

## Scope

- `framework/android/view/View.java` background default/fill only.
- `framework/android/app/Activity.java` paintRoot/frame-clear only.
- Goal: narrower fix per [reports/isakai open defects fix plan.md](../reports/isakai%20open%20defects%20fix%20plan.md)
  (whitewash section) + [research_notes/.../background-erase.md](../research_notes/isakai%20open%20defects%20fix%20plan/background-erase.md):
  keep the opaque frame clear as the once-per-presented-frame base, shrink
  per-view fills from unconditional erase to actual background styling
  (or isOpaque-driven hints). Must fix picker-row whitewash WITHOUT
  regressing repeated-pass screens (calculator settings double-draw —
  see [WORKER-pickimpl.md](WORKER-pickimpl.md) item-1 revert proof; do NOT
  re-apply global transparency as-is).
- Out of scope: Canvas.java, drawables, RelativeLayout, text/input/lifecycle,
  verify-isakai/* (drive read-only), dbus_bridge.

## Environment

- Worktree: `agent-aa7778d3afbf40cce` (own branch).
- Setup per instructions: `.cache/isakai/{apks,compositor}` copied from pinned
  main-repo cache; unique env `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-erasefix`,
  `WAYLAND_DISPLAY=wayland-1`, `XDG_RUNTIME_DIR=/tmp/isakai-wayland-erasefix`,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-erasefix`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-erasefix-stage`. Isolated headless sway fixture.
- Gates: `zig build` exit 0 before/after; `git diff --check` clean;
  `python3 tools/progress-lint.py` 0 violations.
- Evidence root: `evidence/C6/2026-10-28/erasefix/`.

## Acceptance

- Notes picker shows full-width periwinkle rows with text on top, NO white bars (screenshot).
- Calculator settings toggled repeatedly: screenshot after 3+ toggles shows NO
  superimposed text; keypad + animation frames (if drivable) repaint clean.
- Calculator + opencalc + notes flows all PASS on fresh trees.
- Glyph-edge darkness compared pre/post on text-heavy screens.
- No probes left in tree; /tmp scripts deleted; fixture stopped at end.

## Verification evidence

All flows on the isolated sway fixture, same-UID dev mode. Baselines
re-ran on current main first (`calc-base`, `notes-base`: both PASS, settings
clean, picker whitewashed + blue blob — confirming the defect on main).

| Flow | Tree | Work dir | Result |
|---|---|---|---|
| notes | fix (View.java ancestor rule) | `/tmp/erasefix-notes` (fresh) | **PASS** (`notes-fix/`: exact Unicode persist, restart, reopen-edit, 31s liveness, exit 0) |
| calculator | fix | `/tmp/erasefix-calc` (fresh) | **PASS** (`calc-fix/`: 2+2=4 + fresh history row, settings open, decimal pref flips, back exit 0) |
| opencalc | fix | `/tmp/erasefix-ocalc` (fresh) | **PASS** (`ocalc-fix/`: 12+34=46, shrink/grow) |
| calculator manual | fix | `/tmp/erasefix-calc` (same tree, relaunched) | settings opened, decimal row toggled 3x (`simple-manual-31/32/33-toggled.png`), window closed, `run complete`, no surviving process |

- Picker (`notes-fix/simple-04-note-picker.png`): full-width periwinkle rows with text on top, NO white bars. Blue FAB blob + missing plus persist (separate out-of-scope items: blob is tint-at-canvas, already blue; plus icon loads via appcompat TypedArray path).
- Settings isolation (`calc-fix/simple-03-settings.png` entry, `simple-04-toggled.png` after flow toggle, `simple-manual-33-toggled.png` after 3 manual toggles): single-copy text throughout, no superimposition — pixel-identical to baseline (`diffpixels=0/512000`).
- Keypad (`calc-fix/simple-02-result.png` + `-zoom`): text-only keys clean; zoom crop pixel-identical to baseline (`0/5481`).
- Glyph-edge darkness pre/post: `calc 01-initial 0/512000`, `calc 04-toggled 0/512000`, `notes 03-unicode 0/512000`, `ocalc 01-initial + 02-result 0/512000` vs canvasfilter post — all `meandelta=0.000`. No darkening, no shift.
- OpenCalc bottom-center white rect: pre-existing (present in `canvasfilter/ocalc-pre/opencalc-02-result.png`, pre-change tree), not a regression. Blue `46` selection in `04-shrunk` is app selection state on the shrunk pass; flow shrink/grow assertions pass.
- Root-cause proof (temporary probes, fully reverted — `grep DIAGDBL/diagnFrame/TEMP DIAG framework/` clean): with global transparency, translated compose class `r1.v2` draws 321 repeats within single `paintRoot` frames via `r1.v2.l -> r1.s1.a -> ViewGroup.drawChild -> child.draw` (compose `q1.w`/`w0` layout-node draw pass), bounds wobbling 576↔584px; `g0.s` 12x. Same-frame translucent re-blend over stale glyphs (HostText pre-blend stores opaque) is the darkening mechanism; the white per-view fill was the intra-frame erase. The ancestor rule keeps that erase exactly where no drawable ancestor paints.
- Frame-clear verification: `Activity.paintRoot` clears opaque WHITE on both paths (mapped + copy) before `root.draw`, called once per presented frame from `drawFrame` (initial, activity/root switch, dirty, resize). Left untouched — no `Activity.java` change in final diff.
- Limits: animation frames not separately drivable (pinned flows have no animation steps; the 3-toggle + reopen repaints are the accumulation probes). History-dialog screenshot still open (pickimpl limitation, unchanged).

## Code review

- Self-review scope: 1 framework file (`View.java`: `onDraw` guard + `hasDrawableBackgroundAncestor`), no harness/build/test changes. Evidence PNGs + app logs under `evidence/C6/2026-10-28/erasefix/`; new task file only (no shared progress file touched).
- Correctness: condition preserves the old zero-size guard (`right > left && bottom > top` — 0x0 compose hosts still skip the fill, so no whole-canvas wipe); default `backgroundColor` stays WHITE so explicit-color views and drawable-less subtrees render exactly as baseline (proven by the all-zero pixel diffs); drawable-backed subtrees (picker rows, dialog panels over decor art) skip the redundant fill and show ancestor art, matching the AOSP background-less-view contract. Ancestor walk reuses the in-tree `while (p instanceof View)` idiom (cf. `View.java:892`).
- No material findings: no new allocations on the draw path beyond the walk (O(depth), ~10–20 steps, trivial at 640×480); no behavior change for views with explicit backgrounds/drawables; `getParent()` null-safe (loop ends at non-View parent). Performance: per-view fill count strictly decreases (fills only skipped, never added).
- Residual risk: a background-less view that is BOTH repeatedly drawn in one frame AND under a drawable-backed ancestor would accumulate — no such case in the three pinned flows (settings compose chain has no drawable ancestors, proven by clean isolation shots), but a future app could hit it; the fix for that would be idempotent replay in the translated runtime (out of scope), not re-widening the fill.

## Code review

(see above — self-review complete, no material findings; diff is View.java only, probes reverted, gates green)

## Final message (per task)

- Whitewash gone: **yes** — notes picker shows full-width periwinkle rows with text on top, no white bars (`notes-fix/simple-04-note-picker.png`).
- Settings clean after repeats: **yes** — entry + flow toggle + 3 manual toggles all single-copy, pixel-identical to baseline.
- Flow results: **notes PASS, calculator PASS, opencalc PASS** (all on fresh trees); manual 3-toggle session closed with `run complete` and no surviving process.
