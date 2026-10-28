# Compose-issued overdraw: can framework code fix it?

## How does Compose render navigation icons and section headers — where could a stale black rect come from?

### Takeaway
Compose draws icons and headers as recorded canvas commands issued from a single host view (`AndroidComposeView`); a black 24×24 box the exact size of a Material `Icon` default is most plausibly a bounds-sized fill whose vector/tint draw commands were lost or never recorded — not a framework view artifact.

### Cited Findings
- Compose renders each frame in three strictly ordered phases — Composition (what UI), Layout (measure + place), Drawing (emit canvas draw commands) — with unidirectional data flow from composition to drawing ([Source](https://developer.android.com/develop/ui/compose/phases)).
- In the composition phase the runtime outputs a tree of layout nodes carrying information for the later phases; each composable maps to a layout node ([Source](https://developer.android.com/develop/ui/compose/phases)).
- The `Icon` composable draws a single-color icon, is `24.dp` in size by default, and is tinted with `LocalContentColor.current` via a `tint` parameter using the same tint mechanism as images ([Source](https://developer.android.com/develop/ui/compose/graphics/images/material)).
- Tinting uses `ColorFilter.tint(color, blendMode)` with `BlendMode.SrcIn` by default, showing the supplied color where the image is displayed ([Source](https://developer.android.com/develop/ui/compose/graphics/images/customize)).
- `Modifier.graphicsLayer` draws composable content into a draw layer providing isolation for drawing instructions "similar to `RenderNode`"; captured instructions "can be re-issued efficiently by the rendering pipeline without re-executing application code" ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- When a layer is rasterized its instructions execute once and the output is captured into an offscreen buffer; compositing that buffer on later frames is faster than re-executing instructions ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- Without an offscreen buffer, `BlendMode.Clear` clears the entire destination, "leaving the window's rendering buffer (black) visible" — i.e. black pixels are the documented default where layer content is absent or cleared ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- Draw calls in Compose are always relative to the parent composable and performed in pixels ([Source](https://developer.android.com/develop/ui/compose/quick-guides/content/video/drawing-in-compose)).
- In this repo the Settings screen's Compose host is `AndroidComposeView` (obfuscated `r1.w`, with `onDraw`/`dispatchDraw`) inside container `r1.s1`, with a `ViewLayer` (`r1.v2`); the app ships its own compose runtime and translated Compose code touches only stubbed framework APIs, including `Paint.xfermodeRendering` and `View.tooltipRendering` (all non-fatal `ISAKAI_UNSUPPORTED`) ([Source](/home/tenzin/development/isakai/progress/WORKER-compose.md)).
- The repo framework already provides `RenderNode` (transform bookkeeping plus deferred `DisplayList`, replayed via `Canvas#drawRenderNode`), `Canvas.saveLayer` overloads, and a `Paint.setXfermode` that flags `xfermodeRendering` unavailable; `View` exposes empty `onDraw`/`dispatchDraw` hooks — but no `AndroidComposeView`/`ViewLayer` exists in `framework/` or `src/` (grep empty), confirming those classes are translation-owned ([Source](/home/tenzin/development/isakai/framework/android/graphics/RenderNode.java)).

### Inferences
- A black box exactly 24×24 (the `Icon` default size) is consistent with: the layout node measured/placed correctly (bounds right) while the vector-path draw commands inside the layer were missing or unrecorded, leaving either a placeholder fill or the black window buffer showing through.
- The stubbed `Paint.xfermodeRendering` path is a concrete suspect: if the translated `Icon` tint (`SrcIn` color filter over a vector) funnels through an unsupported xfermode branch, the runtime may emit the bounds fill without the glyph, or skip the glyph and leave cleared/black pixels.
- Same-frame duplicates at stale offsets fit recorded-instruction replay with a stale transform: a `graphicsLayer`/`RenderNode`-isolated node re-issued with its old placement while a second copy draws at the new placement (draw calls are parent-relative, so a stale parent offset duplicates the whole subtree).

### Gaps
- Could not inspect the obfuscated translated draw path (`r1.*`), so the exact command emitting the black fill (placeholder `drawRect` vs. cleared buffer vs. failed tint) is unconfirmed.
- No access to androidx.compose source or the issue tracker from this environment, so no upstream bug ID for the black-icon symptom.

## What mechanisms exist for stale/duplicated draws in Compose and how are they fixed upstream?

### Takeaway
Compose's own performance model — phase skipping, layer isolation, and offscreen replay — is the mechanism that produces stale/duplicated draws when invalidation is wrong, and upstream fixes it compose-side (deferring state reads to the right phase, scoping invalidation), never from the host view.

### Cited Findings
- Compose skips recomposition, re-layout, or re-draw whenever it can reuse a former result, tracking state reads per phase; it performs only the minimum work to update the UI ([Source](https://developer.android.com/develop/ui/compose/phases)).
- Phase skipping example: a graphic swapping between two same-size icons skips composition and layout and redraws one element ([Source](https://developer.android.com/develop/ui/compose/performance/phases)).
- Cause of staleness: "coding mistakes can make it hard for Compose to know which phases it can safely skip, in which case Compose runs all three phases" — i.e. correctness depends on state reads landing in the right phase ([Source](https://developer.android.com/develop/ui/compose/performance/phases)).
- Layer isolation semantics: with the default compositing strategy, "draw invalidations made outside of this graphicsLayer will not re-record the drawing instructions in this composable as they have not changed" ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- The upstream fix pattern is deferring reads: reading an offset via lambda `Modifier.offset{}` confines the state read to the layout phase so offset changes skip composition; reading color inside `drawBehind{}` confines it to the draw phase, skipping composition and layout ([Source](https://developer.android.com/develop/ui/compose/performance/bestpractices)).
- Explicit layer capture follows the same record/replay model: `graphicsLayer.record { drawContent() }` then `drawLayer(graphicsLayer)` re-issues the recorded content, convertible to a bitmap ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).
- `CompositingStrategy.Offscreen` forces rasterization into an offscreen buffer (clipping content to layer bounds); `Auto` (default) only allocates one when alpha < 1 or a `RenderEffect` is set ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)).

### Inferences
- Stale-offset duplicates are the predictable failure mode of this architecture: an offset/placement state read in the composition phase (instead of a layout-phase lambda) either forces full recomposition or, worse in a translated runtime, updates placement without invalidating/re-recording the isolated layer — so the old recorded commands replay at the old offset alongside the new ones.
- Layer reuse (`rememberGraphicsLayer` + `record`/`drawLayer`) has the same hazard: re-drawing a layer recorded at a stale position duplicates content if the record step is skipped after movement.
- All documented upstream remedies (lambda modifiers, `drawWithCache`, compositing-strategy choice) are changes to composable/runtime code, supporting a compose-runtime-level fix rather than a host-side one.

### Gaps
- No upstream issue-tracker access to cite a specific stale-replay bug and its fix commit; the mechanism above is inferred from documented architecture, not from a matched upstream report.
- Whether the translated runtime misplaces the phase of state reads (vs. merely dropping an invalidate call) cannot be determined without compose-runtime-level tracing.

## Is there any host/framework interception point that could suppress or correct compose-issued overdraw without reimplementing composition?

### Takeaway
No. The host sees only undifferentiated canvas commands from one `AndroidComposeView.onDraw`; it cannot tell a stale rect from a legitimate one, so any framework-side suppression would be guessing — a negative result with reasons.

### Cited Findings
- Compose emits all of a screen's drawing through its host view's draw path (the translated `AndroidComposeView.onDraw`/`dispatchDraw` identified in the spike); per-node commands are internal recorded instructions re-issued by the pipeline, not separate framework view draws ([Source](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers); [Source](/home/tenzin/development/isakai/progress/WORKER-compose.md)).
- The framework interception surface that exists (`View.onDraw`/`dispatchDraw` hooks, `Canvas.saveLayer`, `RenderNode` replay) operates at the granularity of whole-view draws and recorded command lists — it has no record of which LayoutNode, placement epoch, or composition generation a given `drawRect` belongs to ([Source](/home/tenzin/development/isakai/framework/android/graphics/RenderNode.java)).
- Classic View-system artifacts (dirty-rect under-invalidation, overlapping view overdraw, missing `invalidate()` on a `View`) live at the view-tree level where the host owns each node's bounds and draw — none of that applies here, since the icon/header nodes are LayoutNodes inside Compose, not framework Views ([Source](https://developer.android.com/develop/ui/compose/phases)).

### Inferences
- Candidate interceptions and why each fails:
  - *Clip or clear the 24×24 region before/after Compose draws:* cannot distinguish the black placeholder from a legitimately black icon or dark-theme glyph; would erase real content.
  - *Deduplicate identical draw commands in the same frame:* identical overlapping rects are legal output (e.g. layered tints, shadows, `ModulateAlpha` overlapping squares per docs); dedup would corrupt legitimate rendering.
  - *Force full re-record / full redraw on every frame:* only masks staleness at the cost of destroying the phase-skipping performance model, and still would not restore missing vector glyph commands (the black box would persist, just freshly drawn).
  - *Hook at window/compositor level:* the compositor receives finished buffers, with even less provenance than the canvas level.
- Therefore suppression/correction without composition semantics is not merely unimplemented — it is unimplementable safely at any host layer (parent view, canvas, window).

### Gaps
- A full audit of the translated `Canvas` implementation for an existing command-filter hook was not performed; the conclusion rests on the provenance argument, which holds regardless of hook availability.

## If not fixable framework-side, what is the minimal compose-runtime support that would resolve each artifact?

### Takeaway
Each artifact needs one small fix inside the translated compose runtime (which the app ships itself): the black box needs the `Icon` vector/tint draw path to actually record glyph commands, and the duplicates need placement changes to invalidate or reposition the isolated layer.

### Cited Findings
- The app ships its own compose runtime; the framework was never required to reimplement `androidx.*` — only host-contract behavior was missing, and the only Compose-touched framework gaps on this screen are the named `ISAKAI_UNSUPPORTED` stubs (`Paint.xfermodeRendering` among them) ([Source](/home/tenzin/development/isakai/progress/WORKER-compose.md)).
- The documented tint path for icons is `ColorFilter.tint` with `BlendMode.SrcIn` applied to vector content ([Source](https://developer.android.com/develop/ui/compose/graphics/images/customize)).
- The documented invalidation contract is that state reads must land in the latest phase that needs them (layout-phase lambdas for offsets, draw-phase lambdas for colors) so dependent phases re-run and isolated layers re-record ([Source](https://developer.android.com/develop/ui/compose/performance/bestpractices)).

### Inferences
- *Black 24×24 box — minimal runtime support:* implement (or stop stubbing) the exact blend/tint branch the translated `Icon` painter uses — plausibly `Paint` xfermode / `ColorFilter.tint(SrcIn)` over the vector path — so glyph commands record into the layer; fallback if the blend cannot be supported: draw the vector un-tinted (wrong color, right shape) rather than emitting the bounds fill, converting a corruption into a cosmetic tint deviation. This is work in the translated `androidx.compose.ui.graphics` path, not in framework `View`/`Canvas`.
- *Same-frame duplicates at stale offsets — minimal runtime support:* ensure a placement change invalidates (re-records or repositions) the affected isolated layer — i.e. honor the layout-phase offset contract so a move either re-issues the layer at the new offset only, or re-records before replay. One invalidate-on-placement hook in the translated LayoutNode/`ViewLayer` path, not a host-side filter.
- Neither fix requires reimplementing composition: both are confined to the record/replay boundary the translated runtime already owns (`r1.w`/`r1.v2` per the spike).

### Gaps
- Exact function-level patch points in the obfuscated translated runtime (`r1.*`) remain to be located with draw instrumentation in a dedicated session (the WORKER handoff already recommends this) ([Source](/home/tenzin/development/isakai/progress/WORKER-compose.md)).
- If the black fill originates in app code (a deliberate `drawRect` placeholder behind a loading icon) rather than the runtime, the fix would be app-side; runtime tracing is needed to rule that out.
