# Worker report: honor Paint color filters in the software Canvas (canvasfilter) — 2026-10-28 (agent-acdd5b8fb19ee558b)

Status: **blocked** — Canvas half complete and verified (blue FAB, flows PASS,
repaint identical); FAB plus needs a res-layer icon-load fix outside this
task's Canvas-only scope (follow-up 1).

## Scope

`framework/android/graphics/Canvas.java` only. No `Paint.java` change needed
(the consumption point is entirely canvas-side: `getColorFilter()` already
round-trips). Nothing else touched.

## Change (Canvas.java, +35/-8)

- `paintColor(Paint)`: after folding paint color + paint alpha, applies
  `paint.getColorFilter()` to the result. Every paint-driven op (rect,
  roundRect, circle, oval, arc, path, points, lines, text, drawPaint) filters
  before blending, per the AOSP contract (filter modifies each output pixel
  before blending; default tint mode SRC_IN). Paint-less `drawColor(int)`
  overloads are untouched and stay unfiltered.
- New `paintTexel(Paint)` helper: per-texel transform for the Paint-taking
  `drawBitmap` overloads (all four), `drawBitmapMesh` and `drawPatch`.
  Folds paint alpha into the sampled texel, then applies the paint's color
  filter. Returns null when the paint changes nothing, so the existing
  `emitBitmap` recording path (RecordingCanvas bakes the transform eagerly)
  is preserved. Existing SRC_IN integer math reused, not rewritten.

## Why this is the fix

Probe history from WORKER-pickimpl: the FAB container blue (FF7A90C8)
arrives via `setTintList` into a computed `PorterDuffColorFilter` stored on
the paint (`paint.setColorFilter`), but `Canvas.paintColor` folded only color
+ alpha and never consulted the filter — the resolved tint died at the
canvas and the shape kept its default black paint.

## Verification evidence

Evidence root: `evidence/C6/2026-10-28/canvasfilter/`.
Pre-change: `notes-pre/`, `calc-pre/`, `ocalc-pre/` (all PASS on clean main).
Post-change fresh trees: `notes-post/` (PASS), calc/ocalc pending below.

### Notes (picker FAB)

- Pre (`notes-pre/simple-04-note-picker.png`): black blob.
- Post (`notes-post/simple-04-note-picker.png`): FAB renders BLUE
  (sampled interior `(122,144,200)` = FF7A90C8, the intended container
  color). Pixel census over the FAB bbox: uniform blue, no contrasting
  glyph pixels.
- Plus icon: NOT visible. Diagnosed with temporary probes (added, observed,
  fully reverted — `grep FABDIAG framework/` clean): the FAB's ImageView
  drawable is null across all draws (`drawable=null wh=56x56`, 271 draws);
  neither `setImageDrawable` nor `setImageResource` is ever called on the
  FAB instance (note: translated `h6.d` overrides both setters; the probe
  sat in `ImageView.setImageDrawable`, which the override reaches via
  `invokespecial`, so the override does not blind it); `Resources.getDrawable`
  never fails in the run. Conclusion: AppCompatImageHelper `c0.b` skips the
  `setImageDrawable` call — the `app:srcCompat` styleable lookup (`l3.i(1,-1)`)
  resolves to -1, so the icon drawable is never loaded. That lookup lives in
  res/appcompat machinery (obtainStyledAttributes/TypedArray), outside this
  task's Canvas-only scope. Recorded as follow-up 1. Once the drawable loads,
  it will contrast with the blue: `ImageView.setImageTintList` is a no-op
  stub, so the vector draws its raw fill color (VectorDrawable draws via
  `setPixel`, untouched by this change).

### Calculator / OpenCalc (repaint isolation)

- Pre: both PASS (`calc-pre/`, `ocalc-pre/`).
- Post: both PASS on fresh trees with the final build (`calc-post/`,
  `ocalc-post/`).
- Isolation pairs (byte-exact via PIL ImageChops): keypad initial identical,
  keypad result identical, settings identical, settings-toggled identical —
  no double-draw, no color shift.

## Flake note

First notes-post attempt failed with "did not persist its exact Unicode
contents" (the known environmental save flake, fails on clean trees too
under host contention); immediate retry on the same tree PASSED. Not a
regression.

## Code review (self-review)

- Diff scope: 1 framework file. No harness/test/build changes. No probes
  left; /tmp scripts deleted at cleanup; fixture stopped at end.
- Correctness: filter applies to the blended source pixel only; null-filter
  fast path preserves old behavior exactly (paintTexel null → old emitBitmap
  path; paintColor without filter → old arithmetic). RecordingCanvas override
  signature unchanged; new call sites pass the transform through the same
  10-arg emitBitmap the recorder overrides.
- Risks/limits: (1) Paint-taking drawBitmap with alpha<255 now folds paint
  alpha into texels (previously ignored) — AOSP-correct, but a behavior
  change for any caller relying on the old ignore; no in-tree callers pass
  Paint to those overloads today. (2) The FAB plus stays missing until the
  res-layer icon load is fixed (follow-up 1). (3) Picker-row whitewash
  unchanged (separate View-erase task).

## Follow-ups (out of scope)

1. `res/` ownership: `app:srcCompat` styleable resolution for
   AppCompatImageHelper (`c0.b` → `l3.i(1,-1)` returns -1) so the FAB plus
   drawable loads. Evidence: FABDIAG probe series (reverted), notes-post
   blue-but-iconless FAB screenshot.
2. Picker-row whitewash needs the erase-preserving View change (separate
   task); FAB blue does not fix it.

## Final message (per task)

- FAB blue: YES (FF7A90C8 sampled). Plus: NO (icon drawable never assigned;
  res-layer cause diagnosed, out of scope).
- Flow results: notes PASS, calculator PASS, opencalc PASS (all post-change
  fresh trees).
- Repaint verdict: settings + keypad pre/post pixel-identical (no double-draw,
  no color shift).
