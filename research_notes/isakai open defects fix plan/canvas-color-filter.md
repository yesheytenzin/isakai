# Paint Color-Filter Contract for the Software Canvas

## At what stage does the color filter apply relative to paint color/alpha and blending?

### Takeaway
The color filter transforms each drawn source pixel first; the filtered result is then composited onto the canvas target. Implement as tint(source) then blend, never blend then tint.

### Cited Findings
- "If a Drawable has a ColorFilter, each output pixel will be modified by the filter before blending." — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable)
- "If a Drawable has a ColorFilter, each output pixel of the Drawable's drawing contents will be modified by the color filter before it is blended onto the render target of a Canvas." — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable)
- "A Drawable's drawing content will be blended together with its tint before it is drawn to the screen. This functions similarly to `setColorFilter(int, PorterDuff.Mode)`." — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable)
- "Specifies a tint blending mode for this drawable. Defines how this drawable's tint color should be blended into the drawable before it is drawn to screen. Default tint mode is `PorterDuff.Mode.SRC_IN`." — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable)
- The repo's own `Canvas.paintColor()` folds only color and paint alpha (`(c >>> 24) * getAlpha() / 255`), and no `Canvas` draw path reads `Paint.getColorFilter()` (verified by grep over `framework/android/graphics/Canvas.java`: `paintColor`, `drawBitmap`, `drawText`, `drawColor` call sites reference no filter) — local file `framework/android/graphics/Canvas.java`.
- The repo's `Drawable.tintedColor(int)` applies `colorFilter.filterColor(color)` first and returns before any tint math, i.e. explicit filter wins and tint is skipped when a filter is set — local file `framework/android/graphics/drawable/Drawable.java`.

### Inferences
- For the CPU rasterizer, the per-pixel pipeline for every Paint-driven op should be: `src = filter(paintColor(paint))` (or `filter(texel)` modulated by paint alpha for bitmaps), then existing `setIdx` src-over blend. This matches the Drawable-level "before blending" contract and the Skia `SkPaint` stage order (color filter applies to the source before the blend mode), though no Skia page was fetched in this pass.
- `Paint.setColorFilter` returning the previous filter and `getColorFilter` round-tripping already exist in the repo (`framework/android/graphics/Paint.java`); only the consumption point in `Canvas.paintColor`/bitmap/text paths is missing.

### Gaps
- The `Paint.setColorFilter` reference page text ("each pixel drawn with this paint will first have its color modified by the filter, then blended") was not returned by the doc queries; the ordering claim above rests on the Drawable-level docs, which are explicit, plus standard pipeline knowledge.
- No fetched primary source for how `saveLayer` + `Paint` with filter scopes filtering (layer compositing); out of scope for the direct-draw fix but relevant if layers are ever emulated.

## What does PorterDuff SRC_IN do to source and destination pixels, precisely?

### Takeaway
SRC_IN keeps the source (tint/filter) color but clips it to the destination's alpha: result alpha = srcA * dstA, result RGB = srcRGB (unpremultiplied). For an opaque drawn shape the output is exactly the tint color.

### Cited Findings
- SRC_IN is the default tint/mode: `setTintMode` doc states a null mode means the default `PorterDuff.Mode.SRC_IN` — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable).
- "Developers should consider using `BlendMode` instead, as it provides a wider variety of tinting options" and all modes live in `PorterDuff.Mode` — [Source](https://developer.android.com/reference/android/graphics/PorterDuff).
- The `PorterDuffColorFilter(int, Mode)` constructor's deprecation was reversed (undeprecated: "now fully supported") — [Source](https://developer.android.com/sdk/api_diff/29-incr/changes/android.graphics.PorterDuffColorFilter).
- The repo implements SRC_IN in `PorterDuffColorFilter.filterColor` as: `out = (filterRGB) | ((filterA * dstA + 127) / 255 << 24)` — local file `framework/android/graphics/PorterDuffColorFilter.java`.
- The repo implements the same math for Drawable tint in `Drawable.tintedColor`: `alpha = (tintA * colorA + 127) / 255; return (tintRGB) | (alpha << 24)` — local file `framework/android/graphics/drawable/Drawable.java`.

### Inferences
- In color-filter usage the roles are: filter color = Porter-Duff *source*, drawn content (paint color, texel) = *destination*. So `SRC_IN` output (premultiplied) is `Co = Sc * Da`, `Ao = Sa * Da`; unpremultiplied integer form is exactly the two repo snippets above (RGB passes through, alpha multiplies with +127/255 rounding).
- The repo's `SRC_ATOP` branch (`result = dstA-preserved, channels mixed by srcA`) is consistent with this role assignment (source scaled by srcA over destination), which corroborates the SRC_IN reading; `SRC`/`DST`/`CLEAR` branches are trivially correct.
- FAB container tint case: container color is opaque and the shape fill is opaque, so filtered pixels equal the container color exactly; the corner rounding comes from the shape geometry, not the filter.

### Gaps
- No fetched page stated the `Co = Sc*Da` formula verbatim (the PorterDuff reference page lists modes without per-mode math in the returned snippets); the formula is inferred from the two in-repo implementations plus standard Porter-Duff definitions. A Skia `SkBlendMode`/`SkPorterDuff` doc fetch would close this.
- Whether AOSP rounds `(Sa*Da)` with +127 or truncates in the native `PorterDuffColorFilter` fast path is not confirmed; the repo uses round-to-nearest, a <=1 LSB difference either way.

## Which draw operations honor the filter and which ignore it?

### Takeaway
In AOSP every Paint-driven draw honors the Paint's ColorFilter (shapes, bitmaps, text, drawPaint); the paint-less `drawColor(int)` / `drawBitmap` without Paint bypass it. The repo currently honors it in none of the Paint paths — only Drawable subclasses apply tint/filter via `tintedColor`/`drawBitmapFiltered`.

### Cited Findings
- Drawable-level: filter applies to "each output pixel of the Drawable's drawing contents" before blending — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable). Drawable `draw(Canvas)` "respect[s] optional effects such as alpha (set via setAlpha) and color filter (set via setColorFilter)" — [Source](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable).
- Monochromatic images are expected to be recolored by the host "typically using a SRC_IN filter" — [Source](https://developer.android.com/reference/androidx/wear/watchface/complications/data/MonochromaticImage) (evidence that bitmap/image contents pass through the SRC_IN filter path).
- Repo `Canvas.drawColor(int)` / `drawColor(int, Mode)` / `drawColor(long[, BlendMode])` take no Paint and never consult a filter — local file `framework/android/graphics/Canvas.java` lines ~847-876.
- Repo `Canvas.drawBitmap(Bitmap, ...)` overloads taking `Paint` ignore the paint entirely (no alpha, no filter; they call `emitBitmap` with no transform), and `drawTextImpl` uses only `paintColor(paint)` — local file `framework/android/graphics/Canvas.java` lines ~1508-1560, ~1625-1640.
- Repo `BitmapDrawable.draw` is the one bitmap path that does tint: `canvas.drawBitmapFiltered(bitmap, ..., this::tintedColor)` — local file `framework/android/graphics/drawable/BitmapDrawable.java`.
- Repo `Canvas.drawVertices` is a recorded no-op and `drawPicture` draws nothing, so filter questions are moot there — local file `framework/android/graphics/Canvas.java`.

### Inferences
- Fix surface for faithfulness: apply `paint.getColorFilter()` inside `paintColor(Paint)` (covers rect/roundRect/circle/oval/arc/path/points/lines/text/drawPaint at once) plus per-texel filtering in the `Paint`-taking `drawBitmap` overloads (filter after sampling, combined with paint alpha). `drawColor(int)` without Paint must stay unfiltered.
- The FAB-blob symptom history in this repo (background-tint forwarding fix reverted as exonerated; blob traced to the rounded-rect call) suggests the visible defect is downstream of tint math, consistent with "Drawable tint works, Paint filter is dropped" — see `progress/C6-translator-framework-failures.md` line ~1265.

### Gaps
- No fetched AOSP page enumerates per-op filter behavior (e.g. whether `drawBitmapMesh`/`drawPatch` with Paint apply the filter, or whether `drawText` with an RGBA shader-backed paint filters before or after shader evaluation). Standard behavior is that all Paint ops filter, but this is inferred, not sourced.
- Whether a null-Paint `drawBitmap` in AOSP applies any Drawable-level filter is Drawable-dependent; not resolved here.

## How does Drawable.setTintList flow into Paint.setColorFilter in appcompat/Material code?

### Takeaway
`setTintList` + `setTintMode` (default SRC_IN) are fused into a single color filter at draw time; an explicit `setColorFilter` overrides/replaces tint. Material FAB container color rides this exact path. The repo models it inline (`tintedColor`) instead of materializing a filter object.

### Cited Findings
- "`setTintList`: Specifies tint color for this drawable as a color state list... Note: Setting a color filter via `setColorFilter(ColorFilter)` or `setColorFilter(int, PorterDuff.Mode)` overrides tint." — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable)
- "`setColorFilter(ColorFilter)`: Specify an optional color filter for the drawable... Note: Setting a non-`null` color filter disables `tint`." — [Source](https://developer.android.com/reference/android/graphics/drawable/Drawable)
- Support-library mirrors keep SRC_IN default: "Does not modify the current tint mode, which is `SRC_IN` by default" (`setSupportButtonTintList`, `setTrackTintList`) — [Source](https://developer.android.com/reference/kotlin/androidx/appcompat/widget/SwitchCompat).
- Repo `Drawable.setTintList` stores the list and warns for non solid/gradient/bitmap/vector drawables; `tintedColor` gives `colorFilter` strict precedence over tint — local file `framework/android/graphics/drawable/Drawable.java`.
- Repo progress history records this design decision: "drawable tint for solid SRC_IN" and "SRC_IN/stateful tint" for VectorDrawable; "LayerDrawable parent tint, and non-SRC_IN tint remain incomplete" — `progress/C6-translator-framework-failures.md` lines ~745, ~889.

### Inferences
- AOSP framework `Drawable.updateTintFilter()` (not fetched; standard implementation knowledge) builds `new PorterDuffColorFilter(tintColorForState, tintMode)` pre-API-29 / `BlendModeColorFilter` on 29+ and calls `setColorFilter`; appcompat `DrawableCompat`/`AppCompatDrawableManager` and Material `MaterialShapeDrawable.setTintList` funnel container color through the same `setTintList` entry point. No appcompat/Material sources exist in this repo, so this leg is inferred from the platform contract plus the FAB symptom notes.
- Because the repo computes tint inline rather than via `setColorFilter`, any appcompat code path that calls `drawable.getColorFilter()` expecting the fused filter will see null — a known divergence if FAB code asserts on it; drawing output is unaffected for the SRC_IN solid case.

### Gaps
- No appcompat or Material library sources were reachable (no vendored sources, doc queries returned only framework Drawable docs); the `DrawableCompat.wrap().mutate().setTintList()` + `MaterialShapeDrawable` container-tint leg is inferred, not sourced.
- API 29+ migration (`PorterDuffColorFilter` -> `BlendModeColorFilter`, `setColorFilter(int, Mode)` deprecation) is documented as undeprecation reversal — [Source](https://developer.android.com/sdk/api_diff/29-incr/changes/android.graphics.PorterDuffColorFilter) — but which branch a given FAB build uses (framework vs appcompat backport) was not determined.
