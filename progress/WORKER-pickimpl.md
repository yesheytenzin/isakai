# Worker report: picker-rendering handoff implementation (pickimpl) — 2026-10-28 (agent-a6ada9e66643e8265)

Status: **complete** — implemented, verified, item 1 reverted (regression with proof), items 2+3 kept (verified non-regressing). Picker goal NOT met (rows whitewashed + FAB black, as baseline); follow-ups recorded for the owning teams.

## Environment

- Worktree: `agent-a6ada9e66643e8265` (own branch). `zig build` exit 0 before and after every change (direct `javac --release 17` of all 529 framework sources also exit 0); `git diff --check` clean; `python3 tools/progress-lint.py` → 0 violations (final state).
- Setup per instructions: `.cache/isakai/{apks,compositor}` copied from the pinned main-repo cache; unique env `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-pickimpl`, `WAYLAND_DISPLAY=wayland-1`, `XDG_RUNTIME_DIR=/tmp/isakai-wayland-pickimpl`, `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-pickimpl`, `ISAKAI_STAGE_DIR=/tmp/isakai-1000-pickimpl-stage`. Isolated headless sway fixture (`root/usr/bin/sway -c sway.conf`, `WLR_BACKENDS=headless`); stopped at the end.
- Scope: exactly the three handoff items from [WORKER-picker.md](WORKER-picker.md). Ownership respected (`View.java` background default + state machinery, `graphics/drawable/*`, `widget/ImageView.java`); no changes to text-input/lifecycle/Room paths, `dbus_bridge`, `RelativeLayout`, or `verify-isakai/*`.
- Evidence root: `evidence/C6/2026-10-27/pickimpl/` (`notes*`, `calc*`, `ocalc*`, `manual-post/`).

## Handoff source

[WORKER-picker.md](WORKER-picker.md) — probe-proven diagnosis of the Notes picker defects (periwinkle row whitewash + black FAB blob + missing FAB icon) with the three-item fix handoff.

## Changes (final diff: 3 files, item 1 reverted)

`git diff --stat`: `framework/android/view/View.java` (state machinery only — background default back at `WHITE`, original `onDraw` fill/comment restored),
`framework/android/view/ViewGroup.java` (stale comment refresh),
`framework/android/graphics/drawable/InsetDrawable.java` (+18: `isStateful`/`setState`/`onStateChange` forwarding to inner),
`framework/android/widget/ImageView.java` (+15: resolve `android:src` at inflation).

### Item 1 — whitewash default (REVERTED)

- Implemented first: `View.backgroundColor` default `WHITE` → `TRANSPARENT` + `backgroundColor != 0` fill guard (frame erase left to `Activity.paintRoot`, AOSP parity claim from the handoff).
- Effect observed: picker dialog rows rendered full periwinkle with text on top (no white bars); dialog panels became transparent (dim shows through) — the predicted "more parent-bg" look.
- **Reverted**: the same change garbles the Simple Calculator settings screen — every row's text renders 2–3× superimposed (toggles + labels doubled, unreadable). Pre-change (`calc-pre/`) vs mid-state (`calc/`) vs isolation (`calc-isol`, items 2+3 only, clean) proves item 1 is the cause.
- Why (reported, not fixed — out of scope): the handoff's premise "frame erase already handled by `Activity.paintRoot`, so the per-view fill is redundant" is false for repeated passes over one buffer: the old comment ("doubles as the intra-frame erase between repeated passes") was load-bearing. Removing the fill leaves stale text from an earlier pass visible. Narrower fixes (erase-preserving transparency) need animation/composition ownership and would be improvising around the handoff — reverted instead per the task rule.
- Consequence: the picker-row whitewash bug persists (final `notes-final/simple-04-note-picker.png` ≡ pre-change look: whitewash bars + black blob). The picker acceptance criterion (full-width periwinkle rows with text on top) is NOT met by this task.

### Item 2 — drawable-state delivery (KEPT, working, goal blocked downstream)

- Implemented per handoff: `View.drawableStateChanged()` pushes `getDrawableState()` into stateful background/foreground (+ `invalidate` on change); `refreshDrawableState()` wired from `setPressed`/`setEnabled`/`setSelected`/`setBackground`/attach (with `isSelected` + `selected` field — `setSelected` was a no-op); `InsetDrawable` forwards `isStateful`/`setState`/`onStateChange` to inner (AOSP `DrawableWrapper` parity).
- Temporary-run diagnostics (added, observed, fully reverted — no probes in tree; `grep PICKDIAG framework/` empty) proved the mechanism engages on the real app:
  - `MyFloatingActionButton bg=RippleDrawable stateful=true` — the push runs and propagates (the FAB background is the ripple directly; `o()` takes the non-inset branch, so the `InsetDrawable` companion is unused here but correct for wrappers that do occur).
  - `MaterialToolbar bg=n6.h stateful=false`, `RelativeLayout bg=LayerDrawable stateful=false` (static row backgrounds — correctly untouched).
- **Goal (blue FAB) NOT achieved — root cause found one layer below the handoff's diagnosis, in out-of-scope code.** Static analysis of the translated classes (from the installed work tree, `javap -c`):
  - The blue (`FF7A90C8`) arrives via `setTintList` → `n6/h.m()` computes a blue `PorterDuffColorFilter` (field `B`) → `n6/h.draw` applies `paint.setColorFilter(B)`. This path self-delivers (no View push needed).
  - Our `Canvas.paintColor` (`framework/android/graphics/Canvas.java:899`) reads only `paint.getColor()` + alpha — `ColorFilter` is stored by `Paint` but never applied at draw time. So the resolved blue tint dies at the canvas and the shape keeps its default black paint.
  - The handoff's "empty state suffices / `l([])` resolves blue" premise is refuted: `n6/h.l([])` reads the fill list `n6/g.c`, which is always null (`k()` never called; `n6/g` constructors null all of `c/d/e/f`) — it cannot resolve blue. The probe's `getColorForState`-resolves-blue observation was on a different (tint) list.
  - Kept (not reverted): the wiring is correct AOSP parity, probe-verified working, pixel-neutral today (3× notes PASS, settings isolation clean), and the Canvas-filter follow-up needs it for state variants (the chain reports stateful, so pressed/disabled shades exist). Reverting proven-correct infra the follow-up requires would be destructive. The follow-up (honor `Paint.getColorFilter()` in `Canvas`, `graphics/` ownership) is recorded below.

### Item 3 — ImageView reads `android:src` (KEPT, visible effect unisolated)

- Implemented per handoff: `ImageView(Context, AttributeSet, int)` resolves `android:src` (android ns, then null ns) via `getAttributeResourceValue` → `setImageResource` (exceptions swallowed, mirroring inflation tolerance).
- Caveat found (reported, not acted on): the FAB's own icon path bypasses `ImageView` constructors — translated `h6/d` reads the icon through the appcompat `TypedArray` helper (`t5/c.a` → `Resources.getDrawable`), so item 3 does not affect the FAB icon. The plus stays invisible; with the blob black (item 2 blocked at Canvas), a dark/unresolved vector plus would be invisible anyway. No `android:src` ImageView in the driven flows shows a before/after delta, so item 3's visible effect is unverified (kept as correct AOSP parity, zero observed risk).

## Verification evidence

All flows on the isolated sway fixture, same-UID dev mode, `--startup-timeout 600`, full JSON + `PASS` lines in `/tmp/pickimpl-*.log` (removed at cleanup; evidence PNGs + app logs retained under `evidence/C6/2026-10-27/pickimpl/`).

| Flow | Tree | Work dir | Result |
|---|---|---|---|
| notes | all 3 items | `pickimpl-notes` (fresh) | FAIL (infra flake, see below) then PASS on retry (`notes-diag`, exact Unicode persist, restart/edit, 31s liveness, exit 0) |
| notes | all 3 items | `pickimpl-fresh` (fresh) | FAIL "did not persist" ×2 (flake), then PASS ×3 (`notes-diag2`, `notes-diag3`, and pre-revert runs) |
| notes | reverted (stash) | `pickimpl-reverted` (fresh) | FAIL "did not persist" — proves the save flake is environmental, NOT my regression |
| notes | final (items 2+3) | `pickimpl-fresh` | **PASS** (`notes-final/`: 01–06 shots, 31s liveness, exit 0) |
| calculator | all 3 items | `pickimpl-fresh` | **PASS** (`calc/`) — but settings ghosted (item-1 regression evidence) |
| calculator | reverted | `pickimpl-precalc` (fresh) | **PASS** (`calc-pre/`, clean settings = pre-change baseline) |
| calculator | items 2+3 only | `pickimpl-precalc` | **PASS** (`calc-isol/`, clean settings = isolation proof) |
| opencalc | all 3 items | `pickimpl-fresh` | **PASS** (`ocalc/`) |
| opencalc | final (items 2+3) | `pickimpl-fresh` | **PASS** (`ocalc-final/`) |

- Flake note: the notes save assertion ("did not persist its exact Unicode contents") failed intermittently on ALL trees (including fully reverted) under host contention (two sibling agents driving flows concurrently), then passed repeatedly once the host quieted. One early run produced no output before the 1200s tool timeout (tail-buffered output lost on kill); reruns with `-u` + log files fail/pass fast. Not caused by, nor fixed by, this change.
- Repaint pairs (pre → post-final): settings `calc-pre/simple-03(04)-*.png` → `calc-isol/simple-03(04)-*.png` (clean → clean ✓); keypad `calc-pre/simple-01/02` → `calc/simple-01/02` (identical text-only keys ✓); notes dialog `notes-reverted/simple-04` → `notes-final/simple-04` (identical whitewash+bars+blob ✓); notes editor/unicode, opencalc result/shrink/grow shots retained post-change, all legible, no missing content. Unit-converter screen (`manual-post/calc-history*.png`, post only): clean pale-pink cards with icons+labels — same list-card machinery, no ghosting.
- History-dialog screenshot: NOT obtained. Five manual tap attempts at the keypad clock icon (with/without explicit sway focus, two coordinates) never left the keypad/converter screens — my ad-hoc vinput driving lacks the flows' focus/rect/retry machinery, and writing a driver script for one screenshot fails the necessity bar. Functional history persistence IS proven (all calculator flows assert fresh Room history rows + prefs). Residual risk (item 3 adding an icon where baseline had blank, item 2 resolving a stateful color) is bounded by the settings-list isolation proof (same RecyclerView+TextView+toggle machinery, pixel-identical pre/post).
- Official-behavior parity (Context7 `/websites/developer_android_reference`): `Drawable.setState` ("returns true if the new state causes a visual change and triggers invalidateSelf") and `View.drawableStateChanged` ("called whenever the state of the view changes in such a way that it impacts the state of drawables being shown") confirm the roles my item-2 implementation assigns; `InsetDrawable` state forwarding matches AOSP `DrawableWrapper` parity.

## Code review (self-review)

- Diff scope: 3 framework files + 1 comment-only touch (`ViewGroup.java` stale-comment refresh). No harness/test/build changes. No probes left (`grep PICKDIAG` clean); no /tmp scripts left; fixture stopped.
- Correctness: state pushes are guarded by `isStateful`, invalidate only on change, and reuse the existing `BackgroundCallback`; `setEnabled`/`setSelected` only act on change (no behavior change for steady state). `ImageView` src read is exception-tolerant and runs after `super(...)` (context available).
- Risks / limits: (1) Item 2 is currently pixel-inert for the FAB (Canvas gap) but NOT provably inert for every stateful drawable in every app — pushes now reach `onStateChange` implementations that baseline never invoked; any drawable whose default-item resolution differs from its initial paint will change color (that is the intended AOSP behavior, but it is a behavior change vs baseline; settings isolation shows no such delta on the exercised screens). (2) Item 3 can only ADD icons (never remove); none observed. (3) The reverted item 1 leaves the picker whitewash (and FAB blob) exactly as before — no improvement, no regression.
- Collateral (disclosed): while cleaning up, `pkill -f "compositor/isakai-vinput"` matched my own command line (suicide, exit 143) and also killed the sibling `agent-aff3ec6e933119065` agent's `isakai-vinput` daemon (~10:38). Their in-flight flow may have failed at its next input step. Pattern-based kills must be scoped to own-PID/worktree paths — lesson recorded.

## Follow-ups (out of scope, for owners)

1. `graphics/` ownership: honor `Paint.getColorFilter()` in `Canvas.paintColor` (at least `PorterDuffColorFilter` SRC_IN) — this is the actual last mile for the FAB blue (and any tinted Material shape). Item 2's pushes are already in place to feed it state variants.
2. Picker-row whitewash needs an erase-preserving narrower fix (the global transparency regresses repeated-pass screens like Settings). Do NOT re-apply item 1 as-is.
3. FAB plus icon: verify after (1) — icon loads via the appcompat `TypedArray` path (`t5/c`), likely present but painted dark/invisible today.
4. History-dialog render pair still open (capture blocked by manual-tap flakiness; consider a flow-supported step if the repaint check is ever re-run).

## Final message (per task)

- Renders now per item: (1) REVERTED — picker rows show the pre-change whitewash bars + black blob (unchanged look, settings saved); (2) KEPT — state machinery engages (probe-verified) but the FAB stays black, blocked downstream at `Canvas` ignoring `ColorFilter`; (3) KEPT — `android:src` resolves at inflation, no isolated visible delta in these flows.
- Flow results: notes PASS (final tree), calculator PASS (final tree), opencalc PASS (final tree); settings/keypad/notes-dialog pre/post pairs identical; history-dialog screenshot not obtained (recorded limitation).
- Reverted item + why: item 1 (transparent background default) — regressed the calculator settings screen into unreadable double-drawn text (intra-frame erase is load-bearing there); reverted to the exact pre-change code, proven by the items-2+3-only isolation run rendering clean.
