---
status: in_progress
updated: 2026-10-28
---

# WORKER-cosmetics: Compose Settings cosmetics + file-picker cancel proof

## Task

Close the two leftovers from [WORKER-compose](WORKER-compose.md):
(a) Settings cosmetics — first row clipped at top, section header
truncated to "GENER", back arrow paints as black box, unchecked boxes as
plain outlines; (b) file-picker cancel path proof — the dbus poll fix
left cancel latency past one slice reasoned-but-unproven.

Constraints honored: no Compose runtime work; `View` background/state
and `ImageView` src untouched (owned by [WORKER-picker](WORKER-picker.md));
diagnose at the owning layer, fix minimally; screenshot each cosmetic
before/after on the real Settings screen; calculator flow must still PASS
on a fresh tree. Policy bars new test files — none created (all driving
via the existing `sway-flow.py`; one throwaway `/tmp` copy with only the
hardcoded bridge dirs swapped to the task's unique env, deleted after).

## Diagnosis

Worked from the real Settings screen (Simple Calculator 5.12.0,
`SettingsActivity` → commons `SimpleColumnScaffold`; app sources fetched
from upstream GitHub to identify the composables). Temporary
content-gated stderr probes were used throughout and fully reverted
(`grep COSPROBE framework/` empty; see Code review).

- **Header truncation ("GENER").** `SettingsTitleTextComponent` renders
  `text.uppercase()` (`"GENERAL"`, 7 chars) at 14sp, `maxLines=1`,
  `overflow=Ellipsis`, in a wrap-content box. Probe:
  `BoringLayout.isBoring … width=66.91016` (correct HostText advance).
  The box was right, but `Layout.relayout` broke lines by
  `BitmapFont.cellWidth` counts (`cellWidth(14)=12px`, true avg ~9.5px):
  `maxChars = 67/12 = 5`, so our layout wrapped `"GENERAL"` into
  `"GENER"+"AL"`; compose (maxLines=1) clips to line 0 → `"GENER"`.
  Owning layer: `Layout` line-breaking vs AOSP advance semantics
  (developer.android.com: `Paint` measurement returns character
  *advances*; layout must break past the width, not past a cell count).
- **Back arrow black box.** Magenta-swap isolation proved the arrow
  vector itself renders perfectly (bbox 4–20, 12 verbs) but is buried
  under a compose-issued opaque-black 24×24 `drawRect` at the nav-icon
  slot root origin, drawn immediately before the path. Upstream
  `SimpleNavigationIcon` (`Box(clip,clickable+ripple){ Icon(ArrowBack,
  tint=scrolledColor=BLACK) }`) contains no such background; the ripple
  draws circles only when pressed. Source of the rect not isolated to a
  framework defect — OPEN (see Limits).
- **First-row clip.** Settled row-1 text draws at dev y≈83, below the
  64px app bar — i.e. positioned correctly — but the same frame also
  carries identical draws at stale offsets ((0,0), (20,53)) plus white
  row-background rects that cover the settled text (only glyph tops at
  rows 64–65 survive). Offsets constant across frames; RenderNode/Picture
  replay paths are dead code (no callers), so the duplicates are
  compose-issued. Root cause not isolated to a framework defect — OPEN.
- **Unchecked boxes "plain outlines".** M3 `Checkbox` draws the border
  via `drawRoundRect(borderColor, cornerRadius=radius-halfStroke,
  style=Stroke)` (`androidx` M3 1.2.0-alpha09
  `Checkbox.kt::drawBox`, verified against upstream source). Probed:
  `rx=ry=1.0` for the unchecked stroke (M3-correct:
  `cornerSize−halfStroke` at density 1), color `#40484D`
  (≈ onSurfaceVariant — correct). A 1px-radius arc rasterizes to an
  effectively square corner at this fixture's density 1; the fill
  (`rx=ry=2.0`) rounds visibly. Verdict: geometry faithful to M3 spec,
  "plain" look is density-1 rasterization, not a framework defect.
  No change required (recorded, not fixed).

## Change (2 hunks, no banned files touched)

- `framework/android/text/Layout.java` — `relayout()` breaks lines by
  true per-code-point advances (`paint.measureText`, memoized per
  distinct code point) instead of fixed pixel-font cell counts. Fixes
  the `"GENER"` wrap-then-clip. `cellWidth`/`lineStep` keep their
  cursor/metrics uses.
- `framework/android/graphics/Canvas.java` — non-identity
  `drawRoundRect` no longer degenerates to a plain quad: it maps the
  rect to device space and rasterizes the same rounded shape there via
  the new `drawRoundRectDevice` (+ `devArcOutline`/`devDisc` mirrors of
  the identity helpers). Fixes sharp-corner fills/outlines under the
  translate transforms compose always carries.

## Verification evidence (under `evidence/C6/2026-10-27/cosmetics/`)

Env for every run: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-cosmet`,
`WAYLAND_DISPLAY=wayland-1`, `XDG_RUNTIME_DIR` same,
`ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-cosmet`,
`ISAKAI_STAGE_DIR=/tmp/isakai-1000-cosmet-stage`,
`ISAKAI_NO_PRIVILEGE=1`; private headless sway fixture; `zig build`
exit 0 before and after; `git diff --check` clean;
`python3 tools/progress-lint.py` → 0 violations.

- `before/` — baseline calculator flow **PASS** on a fresh
  `isakai install` tree (same-UID dev mode): shows first-row sliver
  (rows 64–65 only), `"GENER"` (x21–68, rows 167–176), black-box arrow,
  sharp outline (`#40484D`, 19×19 at x587–605/y396–414).
- `after/` — flow **PASS** with both fixes: `"GENERAL"` renders fully
  (x21–80+), checked fills visibly rounded (10x zoom), unchecked
  outline still square (per Diagnosis: correct 1px-radius
  rasterization), row-1 clip and arrow unchanged (OPEN).
- `final/` — flow **PASS on a second fresh tree**
  (`.cache/isakai/cosmet-final-calc`, since removed) with all settings
  assertions holding: `2+2=4` history row, decimal-mark pref flip +
  persistence across restart, `04-toggled`/`06-reopened` shots,
  31 s liveness, exit 0. Fresh-tree settings screen matches `after/`.
- Cancel path (§Cancel path): standalone-bridge op timings +
  no-pinned-picker grep + `poll`-every-slice code path (below).

## Cancel path

- No pinned flow exercises file pick: `sway-flow.py`
  calculator/opencalc/notes never issue `ACTION_GET_CONTENT` /
  `ACTION_OPEN_DOCUMENT` (grep over `verify-isakai/sway_backend/`);
  calculator covers keypad+settings, OpenCalc keypad/copy/resize, notes
  text edit. So no existing surface drives a picker cancel.
- Strongest observable, driven against the real binary (no new files;
  throwaway `python3 -c` socket clients, deleted): standalone
  `isakai-dbus-bridge --bridge-dir /tmp/isakai-1000-cancelpb …`:
  `poll` → **0.0–0.1 ms ×5** (the removed up-to-1 s blocking settle is
  gone at the op level); `open-file` → req 1 (8.1 ms);
  `file-result` while pending → `pending` (no spurious cancel);
  `poll` with pending req → no events; `close-req` → ok.
- App side (code-read, unchanged): `Activity.pollDesktopResults()`
  drains `IsakaiDesktop.poll` **every window-loop slice**
  (`Activity.java` run loop), and kind-12/status=`-1` delivers
  `onActivityResult(…, RESULT_CANCELED)` to the pending request.
  With loop quantum ~0.11 s (WORKER-compose spike7) and a ~0 ms poll
  op, a queued cancel-shaped event is app-visible within ~one slice
  plus app-scheduled handling.
- Precisely what remains unproven: end-to-end through a real portal
  picker (headless fixture has no file portal — `open-file` stays
  pending by design, confirmed above) and any app-side handling past
  `onActivityResult`. A future file-picker flow should assert cancel
  explicitly; no harness was built per the no-new-test-files policy.

## Code review (self-review)

- Diff is 2 files, +87/−23: `Layout.relayout` advance wrap;
  `Canvas.drawRoundRectDevice` + helpers. No banned files touched
  (`View`/`ImageView`/drawables untouched); no translator/src changes;
  no Compose runtime changes.
- Reuse: `paintRect`/`paintPixel`/`mapPoint`/`scaleFactor` reused;
  helper mirrors the identity branch it now shares semantics with.
  `halfWidth` still used elsewhere (no dead code). Unused-var risk:
  none (build clean, no new warnings introduced).
- Perf: relayout now measures one advance per distinct code point per
  layout (memoized HashMap, no per-char TextLayout repeat); full flows
  (calculator, incl. long settings lists) pass with unchanged timing
  behavior. No benchmark invented.
- Correctness: wrap change preserves `\n` behavior and
  width≤0 (unconstrained) behavior; non-identity roundrect falls back
  to the same pixel math as identity (verified: fills now round;
  strokes with rx≥2 would round; rx=1 strokes rasterize per spec).
  `getLineWidth`/`getDesiredWidth`/ellipsize already measured via
  HostText, so measure and wrap are now consistent.
- Probes: all temporary COSPROBE edits reverted across `Canvas`,
  `Paint`, `VectorDrawable`, `BoringLayout`, `Activity`
  (`grep COSPROBE framework/` empty); magenta isolation hack reverted;
  `/tmp` probe evidence dirs and the `/tmp` flow-copy deleted;
  sway fixture + bridges stopped after the runs.
- Remaining limitation: arrow + first-row clip stay OPEN with the
  mechanism evidence above; unchecked outline recorded as
  correct-per-spec (not a defect).

## Per-item verdict

- Section header truncation ("GENER" → "GENERAL"): **fixed**,
  before/after screenshots in `before|after|final/simple-03-settings.png`.
- Checked-box / round-rect corners: **fixed** (fills provably rounded;
  10x zooms inspected).
- Unchecked outline: **diagnosed correct-per-spec** (M3 `rx=1`,
  `#40484D`, faithful 1px rasterization; "plain" = density-1 fixture).
- Back-arrow black box: **open** (compose-issued opaque 24×24 rect
  covers a correctly rendered arrow; no framework defect isolated).
- First-row clip: **open** (settled position correct; covered by
  same-frame duplicate draws at stale offsets; no framework defect
  isolated).
- Cancel path: **proven within one slice modulo app handling**
  (0.0–0.1 ms poll op + every-slice drain + RESULT_CANCELED path);
  end-to-end picker cancel **open** (no portal, no pinned surface).
