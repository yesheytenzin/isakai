# Worker locwin report — 2026-10-28 (agent-ade84edac74fb074a)

Status: fix implemented, verified, committed. Branch: `worktree-agent-ade84edac74fb074a`.
Only `framework/android/view/View.java` differs from HEAD. No shared
`progress/*.md` touched. No probes, /tmp helpers, or fixtures left behind
(sway stopped; /tmp scratch deleted).

## Scope

`framework/android/view/View.java` `getLocationInWindow` + `getLocationOnScreen`
ONLY (touch/draw math already landed per WORKER-notesave and was not touched).
Fix: subtract each ancestor's scroll during the window walk; screen variant
adds the window-to-screen offset. Added lines: 14, removed: 2.

## Environment

- Worktree root as cwd. `zig build` exit 0 before and after (clean tree +
  fix + probe-hook builds). `git diff --check` clean,
  `python3 tools/progress-lint.py` 0 violations.
- Setup per instructions: `.cache/isakai/{compositor,apks}` copied from
  pinned main-repo cache; unique env `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-locwin`
  `WAYLAND_DISPLAY=wayland-1` `XDG_RUNTIME_DIR=/tmp/isakai-wayland-locwin`
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-locwin` `ISAKAI_STAGE_DIR=/tmp/isakai-1000-locwin-stage`.
  Fixture sway launched headless (`WLR_BACKENDS=headless`, `DISPLAY` unset),
  verified `HEADLESS-1 1000x1000` before driving. Same-UID development mode
  (`--work /tmp/locwin-*-work`, fresh `install` per flow).

## Platform-math check (before finalizing)

- Context7 MCP reachable but returned only the one-line reference definitions
  (`getLocationInWindow`: window coordinates; `getLocationOnScreen`: screen
  coordinates) — no AOSP method bodies, exactly the gap the
  cancel-scroll-contracts note predicted. So the math was checked against the
  stable public AOSP structure instead, cross-read with the in-repo parity:
  own left/top, then per ancestor `+ left/top − ancestor scroll`; own scroll
  shifts children, not self; screen = window + window-to-screen offset.
  In-tree touch (`offsetX = mScrollX − child.mLeft`, ViewGroup.java:524-591)
  and draw (`baseX = offsetX − getScrollX()`, ViewGroup.java:772-773) already
  encode the same scroll sign, so reporting now agrees with both.
- `getLocationOnScreen`: no window-position concept exists anywhere in tree
  (no AttachInfo/mWindowLeft; root is laid out at 0,0 and blitted straight
  to the host buffer in `Activity.drawFrame`/`paintRoot`), so window origin
  == screen origin and the offset step is an explicit no-op with the reason
  recorded at the call site — a true screen variant structurally, ready for a
  future window position to flow through.
- Deliberately NOT carried over from AOSP: matrix/rotation mapping (runtime
  matrices are identity-only), `ViewRootImpl.mCurScrollY` (no ViewRoot
  exists), unattached-view zeroing (behavioral no-op here). Zero-scroll
  walks are bit-identical to the old formula (subtraction of 0).

## Deterministic math check (/tmp, deleted after use)

`/tmp/LocwinCheck.java` (javac/java against the fresh framework classes,
removed afterwards): scrolled pager `scr=640` page-1 editor at parent-left
640 reports `(10,20)`; pre-fix formula on the same tree reports `(650,20)`
(first run accidentally executed against the stale classes dir and captured
the old over-report verbatim). Zero-scroll leaf `(16,20)` identical old/new;
own scroll does not move own origin `(0,0)`; vertical scroll `(0,350-300=50)`;
onScreen == inWindow in all cases. 6/6 ok.

## Live scrolled-container probe (temporary hook, removed)

A temporary `Activity.drawFrame` hook (fixed `/tmp/locwin-dump.txt` path —
the sway flow scrubs app env — capped at 40 scrolled frames, printing per
view `bounds scr= new=(scroll-adjusted) old=(plain sum) FOCUSED?`) drove one
notes flow, then was fully removed (`grep -rn locwinProbe|TEMP-LOCWIN
framework/` empty; final diff is View.java only).

Agreement result, frame 1 of 40 (`evidence/C6/2026-10-28/locwin/probe/locwin-dump.txt`):

- `MyViewPager 0,0,640,736 scr=640,0` — the live scrolled case, page 1.
- Page-1 fragment `RelativeLayout 640,33,...`: focused `MyEditText`
  `0,0,161,28` reports **new=(0,97), old=(640,97)**. Old over-reports x by
  exactly scrollX; 640 is the window's right edge (640-wide window), i.e.
  the old value points off the visible page.
- Page-0 fragment descendants report new=(-640,97) — correctly
  off-viewport left (old said (0,97), visibly wrong).
- All unscrolled views (toolbar items, dialog rows): new == old everywhere.
- Drawn position: `simple-03-unicode.png` shows `Isakai 日本語 – note|`
  starting at the window's left edge below the N2 tab — pixel (0,97) region.
- Touch target: the flow tapped EDITOR=(40,110), inside the reported rect
  (0,97)-(161,125); the marker landed and saved.
- **Reported == drawn == touch: yes.**

## Flow verification (clean tree, probe removed)

| flow | tree | result |
|---|---|---|
| notes `sway-flow.py`, fresh work dir + probe hook | +locwin fix | full pass except trailer: marker saved `(2,'N2',MARKER)`, restart persisted, reopen-edit `MARKER+X`, 31s liveness, app `exited with code 0`; DB ends `(2,'N2','Isakai 日本語 – noteX')`. The harness process did not return after the final app exit (no PASS line; evidence complete) — unexplained trailer hang, recorded as a limit, not a flow failure. |
| calculator `sway-flow.py`, fresh tree | +fix, no probe | **PASS** (`2+2`-family result, settings, decimal persist, reopen, 31s liveness, back exit 0) — zero-scroll regression clean |
| notes `sway-flow.py`, fresh tree | +fix, no probe | **PASS**, DB `(2,'N2','Isakai 日本語 – noteX')` |

Evidence: `evidence/C6/2026-10-28/locwin/{probe,calc,notes-final}/`
(screenshots, sway logs, 40-frame dump; calc/notes-final include their
`sway-*.log` + full screenshot series).

## Code review (self-review)

- Scope: `framework/android/view/View.java` only (+14/−2). No `ViewGroup`
  touch/draw (already correct), no Canvas/background (other workers), no
  `verify-isakai/*` (read-only; driven, never modified).
- Blast radius: only views under a scrolled ancestor change value; every
  zero-scroll caller (calculator, OpenCalc, dialogs, `FocusFinder`'s
  `getLocationOnScreen` use) is numerically identical — proven by the unit
  check, the probe's new==old on all unscrolled views, and the calculator
  PASS on the same build.
- Limitations: (1) single-session, same-UID evidence; privileged runs remain
  an operator action. (2) Screen offset is structurally 0 on this host — if a
  real window manager position ever appears, it must be threaded into
  `getLocationOnScreen`, flagged at the call site. (3) The probe-run trailer
  hang is unexplained (calc/notes-final exited promptly, suggesting
  transient); flow acceptance rests on DB + screenshots + app exit 0, all
  captured. (4) Review is self-review, no independent second pair of eyes.
- No material review findings remain; no probes, /tmp helpers, or fixtures
  left behind.
