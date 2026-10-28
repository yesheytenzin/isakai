# Worker report: Notes picker rendering (overlap + blob) — diagnosed, fix needs banned files

Status: **diagnosed to the owning contracts; NO fix landed** — both root
causes live in `framework/android/view/View.java`, which the task forbids
modifying. Exact handoff below. Worktree is probe-free; both pinned flows
pass on the reverted tree.

## Symptoms (reproduced on pinned Notes, real APK)

`evidence/C6/2026-10-27/picker/picker-repro.png` (also `picker-repro-pristine.png` from
the final probe-free run): picker rows show periwinkle (`FFEEF0FF`) bars in
the text area with titles floating on white, and the FAB renders as an
opaque black 56x56 rounded blob covering row text.

## Mechanism 1 — row "overlap" is whitewash, not layout

- Layout is correct (temporary tree dump, since removed —
  `evidence/C6/2026-10-27/picker/tree.txt`): rows stack at y=8/49/90/…, title at top and
  text `layout_below` it, FAB 56x56 at the end/bottom position. The
  RelativeLayout stacked-extents repair holds.
- The app **intentionally** sets each row background at runtime
  (`t.b0.a ← t.b0.R ← … ← StaggeredGridLayoutManager`, captured in
  `evidence/C6/2026-10-27/picker/probes-setbg.txt`): a single-layer `LayerDrawable`
  wrapping a programmatic `GradientDrawable` filled `FFEEF0FF`
  (the `you_dialog_background_color` family; raw field already `FFEEF0FF`
  with tint/filter null — `evidence/C6/2026-10-27/picker/probes-color.txt`). Our
  inflation of `@drawable/widget_round_background` is irrelevant here; the
  app replaces it. Same code runs on real Android, so the `FFEEF0FF` rows
  are app-intended, not our bug.
- The defect is ours: `View.backgroundColor` defaults to `Color.WHITE`
  and `View.onDraw` fills it for **every background-less view**. Each
  title/text `MyTextView` (background null) paints opaque white over its
  own bounds, hiding the row background behind it; only the strip right of
  short titles stays visible as "bars". Pixel proof: titles on `FFFFFF`,
  bars `EEF0FF` (`evidence/C6/2026-10-27/picker/picker-repro.png` samples).
- On AOSP a null background draws nothing (transparent). Our whitewash
  exists as "intra-frame erase", but `Activity.paintRoot` already clears
  the whole frame to white (`Activity.java:1265,1278`), so the per-view
  fill is redundant for erase and wrong everywhere the parent isn't white.
- **Handoff fix (banned file `framework/android/view/View.java`):**
  default `backgroundColor` to transparent (`0`); keep `onDraw`'s fill for
  explicit `setBackgroundColor`. Frame base stays white via `paintRoot`.
  Risk: windows that relied on the whitewash over non-white parents —
  repaint all pinned apps after the change (they should show *more*
  parent-bg, which is the correct behavior).

## Mechanism 2 — black FAB blob is dead drawable-state machinery

- The blob fill is opaque black (`FF000000`) in a 56x56 round rect drawn
  through `RippleDrawable → n6.h.draw → n6.h.f` (MaterialShapeDrawable).
  The FAB's `backgroundTintList` is present and blue (`FF7A90C8`,
  `evidence/C6/2026-10-27/picker/probes-fab.txt`) but never reaches the fill.
- Translated `n6.h` owns a working updater: `onStateChange(int[])` calls
  `l(int[])`, which does `getColorForState + Paint.setColor` (verified in
  translated bytecode). It **never runs**: probes showed no
  `getColorForState` returning black, no `Paint.setColor(black)` — the
  paint keeps its default black because nothing ever sets it.
- Owning break, two layers:
  1. `View.drawableStateChanged()` is an **empty stub**, called by
     nothing (`refreshDrawableState` doesn't exist, `getDrawableState()`
     always returns empty, `setSelected` is a no-op). No view state ever
     reaches any background. (Banned file `View.java`.)
  2. Even past that, `InsetDrawable` (which the FAB impl `h6.l.q`
     installs around the ripple) has **no** `setState`/`onStateChange`/
     `isStateful` propagation to its inner drawable, so state would stop
     there. (Allowed file
     `framework/android/graphics/drawable/InsetDrawable.java` — companion
     fix, insufficient alone.)
- Prior art respected: the reverted background-tint-forwarding attempt is
  NOT repeated — view tint is not the delivery path here; drawable state
  is. Exonerated in passing: canvas blending (src-over), `ColorStateList`
  null-vs-black plumbing, all color/attr resolution paths (no black
  originates from resources; the `FFEEF0FF`s resolve correctly).
- **Handoff fix:** in `View.java` (banned): implement
  `drawableStateChanged()` to push `getDrawableState()` into the
  stateful background/foreground, add `refreshDrawableState()` and wire
  it from `setPressed`/`setEnabled`/`setSelected`/`setBackground`/attach
  (AOSP parity). Empty state suffices for the blob: `l([])` resolves the
  default item (`FF7A90C8`). Companion in `InsetDrawable.java` (allowed):
  propagate `setState`/`onStateChange`/`isStateful` to `inner` (AOSP
  `DrawableWrapper` parity). Then re-verify: blob should become the blue
  container color.
- Adjacent (allowed file, bundle with handoff): the FAB plus icon is
  missing because `ImageView(Context, AttributeSet)` never reads
  `android:src` from XML — runtime `src=null`
  (`evidence/C6/2026-10-27/picker/probes-fab.txt`). Toolbar icons render only because
  they are set programmatically. Fix in
  `framework/android/widget/ImageView.java` (not banned): resolve
  `android:src` during inflation. Not landed here — left for the handoff
  owner so the FAB fix ships whole.

## What was NOT changed

- No banned files touched (`View.java`, `ViewGroup.java`,
  `RelativeLayout.java`, `Dialog*.java`, `Context*.java`,
  `ClipboardManager.java`) — `git diff` on `framework/` is empty.
- No APK-private hooks, no forced minimum heights, no translator changes.
- All temporary probes (`ISAKAIPI_*`, `pickerArmed`, counters) reverted;
  `grep ISAKAIPI framework/` returns zero. No scripts created; no tree
  debris (`git status` shows only `evidence/C6/2026-10-27/picker/` + this file).

## Verification evidence (probe-free final tree)

- `zig build` exit 0; `git diff --check` clean;
  `python3 tools/progress-lint.py` → 0 violations.
- `python3 verify-isakai/sway-flow.py notes --work
  .cache/isakai/picker-work --evidence /tmp/picker-evidence-final
  --startup-timeout 600` → **PASS** (exact Unicode persist, restart/edit,
  31s liveness, exit 0). Picker screenshot still shows bars + blob
  (expected — no fix landed).
- `python3 verify-isakai/sway-flow.py calculator --work
  .cache/isakai/picker-calc --evidence /tmp/picker-evidence-calc
  --startup-timeout 600` → **PASS** (regression check).
- One intermediate run failed with the appended-`X` edit missing
  (`note` vs `noteX`) and passed unchanged on rerun — known single-press
  input flake family, not rendering.
- Unique-env compositor used throughout
  (`ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-picker`, `WAYLAND_DISPLAY`
  forced to `wayland-1` because `sway_backend/vinput.py:isolated_env`
  hardcodes it; bridge/stage dirs per task). Sway + apps stopped after
  the runs; isolated `/tmp` scratch removed.

## Next step for the owner

Someone with `View.java` clearance: apply the two handoff fixes above
(whitewash default + state propagation with the `InsetDrawable`
companion and the `ImageView` src follow-up), then re-run the notes flow
and confirm the picker shows full-width `FFEEF0FF` rows with text on top
and a blue FAB with a plus icon — matching what the app's own runtime
choices describe.
