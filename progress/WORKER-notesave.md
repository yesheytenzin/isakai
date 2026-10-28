# Worker notesave report — 2026-10-28 (agent-adfd320b5bb1f1fb7)

Status: fix implemented, verifying. Branch: `worktree-agent-adfd320b5bb1f1fb7`.
No shared `progress/*.md` touched. All diagnostic probes removed;
only `framework/android/view/ViewGroup.java` differs from HEAD.

## Environment

- Worktree root as cwd. `zig build` exit 0 (clean tree + probe builds).
- Setup per instructions: `.cache/isakai/{compositor,apks}` copied from
  pinned main-repo cache; unique env `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-notefix`
  `WAYLAND_DISPLAY=wayland-1` `XDG_RUNTIME_DIR=/tmp/isakai-wayland-notefix`
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-notefix` `ISAKAI_STAGE_DIR=/tmp/isakai-1000-notefix-stage`.
  Note: `verify-isakai/sway-flow.py` hardcodes its own bridge dirs
  (`/tmp/isakai-<uid>-isosway`) for launch; the task's bridge/stage dirs
  apply to manual probes. All flows same-UID development mode.
- `git diff --check` / `python3 tools/progress-lint.py` gates run before commit.

## Session traps found (all mine, all recovered)

1. **Private sway on the X11 backend silently scales pointer input.**
   Launching sway from a Hyprland session inherits `DISPLAY` and wlroots
   picks the X11 backend (`X11-1`, here 941x1026) instead of headless.
   vinput sends absolute motion with extent 1000x1000, so every tap is
   remapped by output-size/1000 (measured: Java received
   `x = 0.941*rel - 5.9`, `y = 1.026*rel + 2.6`, exactly 941/1000 and
   1026/1000). Top-edge icon taps (Notes PLUS, Calculator settings) miss
   left/down; big keypad targets still land. Looks exactly like an
   app-side hit-test defect. Fix: launch the fixture sway with
   `WLR_BACKENDS=headless` and `DISPLAY`/`WAYLAND_DISPLAY`/`SWAYSOCK`
   unset (keep `XDG_RUNTIME_DIR`), verified `HEADLESS-1 1000x1000` and a
   3x3 tap grid then reports exact 1:1 coordinates. Possible contributor
   to the C7 systematic settings/confirm misses — unproven for them.
2. **Stale app processes steal the window.** `isakai launch` forks the
   Java app; killing the launcher leaves it mapped. Later runs fail with
   `AlreadyRunning` (empty app log, screenshots of the stale window) or
   drive the stale app. Kill by worktree-scoped `IsakaiMain` match and
   delete stale `isakai.lock` files. Never bare `pkill -f` (self-matches
   the calling shell).
3. **`install` and ACL regrants need `ISAKAI_NO_PRIVILEGE=1`; without it
   `pkexec` blocks with no auth agent.** Same for `helpers.ensure_read`
   inside flow DB polls — a flow that hits it can stall past any
   external timeout. Same-UID runs should never need it; if a flow
   hangs in the save poll, suspect this path.

## Before-series (clean tree, headless session)

6 fresh-tree runs, unmodified tree (`git stash`ed probes, rebuilt clean):
6/6 FAIL with the exact reported signature — dialog confirm saves the
title row, body UPDATE never lands:

| run | result | DB rows |
|---|---|---|
| 1 | FAIL `did not persist` | `(1,'General note',''), (2,'N2','')` |
| 2 | FAIL | `(1,'General note',''), (2,'N2','')` |
| 3 | FAIL | `(1,'General note',''), (2,'N2','')` |
| 4 | FAIL | `(1,'General note',''), (2,'N2','')` |
| 5 | FAIL | `(1,'General note',''), (2,'N2','')` |
| 6 | FAIL | `(1,'General note',''), (2,'N2','')` |

Full per-run evidence: `evidence/C6/2026-10-27/notesave/before{1..6}/`
(screenshots incl. `simple-03-unicode` showing `Isakai \u65e5\u672c\u8a9e
– note` in the editor, `simple-04-note-picker`, app logs).
Raw TSV: `/tmp/notesave-before.tsv`. On this loaded box (sibling
workers active) the miss rate is 6/6; the task's quiet-box record shows
the same signature intermittently (N2/'' then N9/BODY9 on retry), so the
mechanism is timing-sensitive, not load-caused.

...

## Diagnosis (save trigger chain)

Traced end to end via bytecode + JDB + runtime probes (all probes
temporary, removed before final build):

- Folder tap dispatches through translated appcompat menu handling
  (`g.q0.f` → `MainActivity.o0`) — proven by JDB stack on the live UI
  thread (`ActionMenuItemView.onClick → ... → g.q0.f → o0 → g8.g.W`).
- `o0` looks up the current fragment in the pager adapter map
  (`k.get(getCurrentItem())`, `instanceof g8/g`) and calls `g8/g.W`.
- `g8/g.W` reads live text via `U()` (= fragment field `q0.getText()`),
  compares with the note value, and saves (`S` → `j.a.g` → bg thread
  → `INSERT OR REPLACE`) unless equal-and-unchanged.
- The title INSERT (dialog confirm) always lands; the body UPDATE never
  compiles in failing runs (SQL probe over 6 before-runs + 5 probe
  runs: only seed + confirm INSERTs, zero UPDATE-family statements,
  no execSQL/execute paths either).
- JDB field reads on a failing run: map `{0=General-frag,
  1=N2-frag}`, `W` runs on N2's fragment whose note is
  `Note(id=2,title=N2,value=)` but whose editor field `q0` is an
  EMPTY unfocused `MyEditText`, while the marker sits in a DIFFERENT
  focused editor instance belonging to General's (pos 0) fragment.
  `U()` returns `''`, equals-skip fires, no write. No exception anywhere.
- Tree dumps at tap time show two laid-out body editors under the
  `MyViewPager` (focused one holds the marker, other empty), each with
  the fragment text-watcher registered — i.e. two live text-fragment
  editors, input landing in General's while the save reads N2's.
- Watchers DO fire on every edit path (`refresh() → super.setText`
  notifies; paste verified `watchers=2 old= new=<marker>`), focus is
  correct, Room/SQLite work, touch mapping is 1:1 on a headless
  session. The save gate itself is sound given consistent inputs.
- Open question RESOLVED (see Fix): the pager sets `scrollX=640` +
  `currentItem=1` (proven by probe), but OUR `ViewGroup` never
  applies scroll in touch dispatch or child drawing. Display stays on
  page 0 and taps hit page 0's editor while the save reads page 1
  (N2, empty). Display, touch, and logical page all disagree.
  AOSP parity citations: `ViewGroup.dispatchTransformedTouchEvent`
  offsets by `mScrollX - child.mLeft` / `mScrollY - child.mTop`
  (platform/frameworks/base, core/java/android/view/ViewGroup.java);
  `View.draw` translates the canvas by the scroll before drawing
  content/children; `View.scrollTo`/`getScrollX` document the
  scrolled-position contract (developer.android.com reference for
  `android.view.View`).

## Fix

`framework/android/view/ViewGroup.java` only (+26/−12, no banned
files touched):
- `dispatchTouchEvent`: hit-test and descend offsets now add
  `getScrollX()/getScrollY()` at all four sites (DOWN loop,
  intercept-cancel, touch-target MOVE/UP, POINTER_DOWN), mirroring
  AOSP's `offsetX = mScrollX - child.mLeft` adapted to this file's
  per-level own-left convention.
- `dispatchDrawAt`: children draw at
  `(offsetX - getScrollX(), offsetY - getScrollY())` so the viewport
  shows the scrolled page, matching what touch now hits.
- `dispatchDraw` deliberately untouched (translated custom-draw
  hook; making it draw children would double-draw per C6's
  dispatchDraw analysis). `drawChild` untouched (no in-tree callers;
  translated `RecyclerView` ships its own final `drawChild`).
  `getLocationInWindow` untouched (nothing in the save path uses
  it; noted as follow-up).
- Blast radius: only views with nonzero scroll are affected, and a
  probe census across all probe runs shows ONLY the notes
  `MyViewPager` ever sets nonzero scroll (`0,0 -> ±640,0`).
  Calculator/OpenCalc/notes-dialog flows never scroll → zero
  behavior change there (verified by regression runs below).

## Verification

Clean fixed build (`zig build` exit 0, `git diff --check` clean,
`python3 tools/progress-lint.py` 0 violations; all diagnostic
`ISAKAI_*` probes removed — verified by `grep -rn ISAKAI_ framework/`
returning nothing):

| series | tree | result |
|---|---|---|
| BEFORE `sway-flow.py notes` x6, fresh tree/run | unmodified | **0/6 save** — every run `FAIL did not persist`, DB `(N2,'')` with the marker visibly in the editor |
| AFTER `sway-flow.py notes` x6, fresh tree/run | +scroll fix | **5/6 full PASS** (save + restart + reopen-edit `markerX` + 31s liveness + clean close); run 3 FAILs on an empty editor (paste never landed — input-delivery flake, see below), DB `(N2,'')` with neither note carrying text |
| save-gate level (marker present in editor) | — | **0/6 before → 5/5 after** |
| calculator pinned flow, fresh tree | +fix | **PASS** (`2+2=4`, settings, decimal persist, reopen, 31s liveness, back exit 0) |
| opencalc pinned flow, fresh tree | +fix | **PASS** (`12+34=46`, clipboard `46`, resize, liveness, clean close) |

Raw TSVs: `/tmp/notesave-before.tsv`, `/tmp/notesave-after.tsv`.
Evidence dirs: `evidence/C6/2026-10-27/notesave/{before,after}{1..6}/`,
`notesave/{regcalc,regocal}/`, probe series `notesave/probe-sql*/`,
`notesave/quiet1`, `notesave/quiet1b` (same-tree N3 save), and
`notesave/tainted-x11-session/` (pre-headless failures, kept labeled).

Run-3 note: the flow's editor was empty (paste lost: click+CTRL+A+
`wl-copy`+CTRL+V chain), so there was nothing to save and the gate
correctly skipped. This matches the established input-delivery flake
family (WORKER-input: taps/keys intermittently vanish, never wrong;
needs no log confirmation per the buffering lesson — screenshots + DB
are the arbiters). Unrelated to the save gate; the fix touches only
touch dispatch + child draw offsets (keyboard/clipboard path untouched).

## Code review (self-review)

- Scope: `framework/android/view/ViewGroup.java` only (+26/−12).
  No `View.java` background/dispatch, `RelativeLayout`,
  `ImageView`, `graphics/drawable/*`, `src/dbus_bridge.zig`, or
  `verify-isakai/*` changes. Ownership respected (lifecycle-adjacent
  input plumbing; another worker's file would have meant a handoff).
- Mechanism reviewed against AOSP: touch offset
  `mScrollX - child.mLeft` (dispatchTransformedTouchEvent) and
  scroll-translated child painting (View/ViewGroup draw); our code
  did neither, now does both, keyed off the same `getScrollX/Y`
  the translated pager already sets.
- No new allocations in the hot path (two floats + two ints per
dispatch/draw); no behavior change when scroll is 0 (all other
flows); translated `dispatchDraw` left empty (no double-draw);
`getLocationInWindow` scroll follow-up explicitly deferred, not
forgotten.
- Validation: honest before/after miss counts above (6+6 runs, no
  cherry-picking; run 3 analyzed, not hidden), plus calculator +
  opencalc regression PASS on the same build.
- Limitations: (1) single-session evidence (one box, one day);
  default-confinement (privileged) runs remain an operator action
  and are unaffected by this diff. (2) The run-3 input flake is
  classified, not fixed (harness/input-delivery scope).
  (3) `getLocationInWindow` still ignores scroll (no in-tree caller
  needs it; filed as follow-up, not fixed here).
- No material review findings remain; no probes, /tmp helpers, or
  fixtures left behind (sway + apps stopped; `/tmp/*.py` helpers
  and stale locks removed).
