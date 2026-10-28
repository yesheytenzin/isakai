# Android background erase contract for a single-buffer rasterizer

## When a view invalidates, what exactly gets redrawn and what clears pixels first

### Takeaway
Official Android has no per-view erase step: `invalidate()` schedules a redraw of the view, the dirty region is internally calculated (caller-supplied rects are ignored since API 21), and whatever was underneath — parent content, window background, or a preserved buffer — simply shows through unless the view paints opaque pixels over it.

### Cited Findings
- `invalidate()` means "invalidate the whole view"; `onDraw(Canvas)` is where "the view can use [the Canvas] to draw itself", and the guide stresses views are redrawn frequently so no drawing objects should be allocated in `onDraw` — [Create a custom drawing](https://developer.android.com/develop/ui/views/layout/custom-views/custom-drawing).
- `invalidate(Rect)` / `invalidate(l,t,r,b)` were deprecated in API 28: "The switch to hardware accelerated rendering in API 14 reduced the importance of the dirty rectangle. In API 21 the given rectangle is ignored entirely in favor of an internally-calculated area" — [View reference](https://developer.android.com/reference/android/view/View).
- `isOpaque()`: "An opaque View guarantees that it will draw all the pixels overlapping its bounds using a fully opaque color. Opaque Views are treated in a special way by the View hierarchy, possibly allowing it to perform optimizations during invalidate/draw passes" — [View reference](https://developer.android.com/reference/android/view/View). This is the entire official erase story: opaque views erase by overdrawing, and the hierarchy is allowed to skip work behind them.
- Software path: "The content of the Surface is never preserved between `unlockCanvasAndPost()` and `lockCanvas()`, for this reason, every pixel within the Surface area must be written. The only exception to this rule is when a dirty rectangle is specified, in which case, non-dirty pixels will be preserved" — [SurfaceHolder reference](https://developer.android.com/reference/android/view/SurfaceHolder). So on the software path the redraw scope is the dirty rect and stale pixels outside it are legitimate reuse, while everything inside must be repainted by overdraw.
- Isakai's implementation target diverges here: `View.invalidate()` sets a boolean `dirty` and bubbles it to the root, `isDirtyAndClear()` is the root check, and every pass calls `root.draw(canvas)` — the full tree is always repainted, caller rects are dropped (`invalidate(Rect)` just calls `invalidate()`), and there is no dirty-region scoping at all — `framework/android/view/View.java` (~lines 736-741, 809-821). The load-bearing per-view whitewash lives in `View.onDraw`: when `background == null` on a bounded view it fills with `backgroundColor` (default white) — `framework/android/view/View.java` (~lines 1398-1407).

### Inferences
- In AOSP, nothing clears pixels "first" for a view: the software canvas arrives with either undefined contents (full lock, everything must be rewritten) or preserved contents (dirty lock), and coverage comes from the window background plus opaque overdraw down the tree, not from an explicit clear step.
- Because isakai repaints the whole tree every pass, its redraw scope is the whole frame, which under the `lockCanvas()` contract means every pixel must be written every pass — a requirement its full-frame clear plus full-tree draw satisfies, and its per-view whitewash over-satisfies.

### Gaps
- Current developer.android.com pages no longer document the `ViewRootImpl` software traversal (`drawSoftware`, `Canvas.lockCanvas(dirty)`, DecorView background pass); the "preserve vs rewrite" contract above is from `SurfaceHolder`, the closest surviving official statement.
- Which clears the "global-transparent experiment" changed (frame clear, per-view fill, or both) was not established from the repo; the implementer must check the working tree/diff.

## How hardware DisplayLists/RenderNodes vs software canvas differ in erase responsibility

### Takeaway
Hardware rendering is a retained, replay-every-frame scene: invalidation only re-records one node's commands, and each frame composites the whole scene onto a fresh framebuffer, so views never erase. Software rendering is an immediate persistent buffer where stale pixels survive unless overwritten, so erase equals opaque overdraw.

### Cited Findings
- `Canvas.drawColor(int)` "fill[s] the entire canvas' bitmap (restricted to the current clip) with the specified color, using srcover porterduff mode" — [Canvas reference](https://developer.android.com/reference/android/graphics/Canvas). Erase-by-fill is clip-scoped SRC_OVER overdraw, not a buffer reset (opaque color required; see next section for why transparent fails).
- `HardwareBufferRenderer.setContentRoot(RenderNode)`: "It is not necessary to call this whenever the content recording changes. Any mutations to the RenderNode content ... will be applied whenever a new RenderRequest is issued" — [HardwareBufferRenderer reference](https://developer.android.com/reference/android/graphics/HardwareBufferRenderer) (retrieved via Context7). The scene is retained; each render request replays it.
- `invalidate(Rect)` forcing "a redraw of only that region" survives only as a deprecated software-era semantic — [Context7 Android Developers docs](https://developer.android.com/reference/androidx/compose/ui/platform/AbstractComposeView); the platform `View` docs above confirm the rect is ignored on the hardware path.

### Inferences
- Hardware erase responsibility sits entirely below views: the GPU/SurfaceFlinger frame is rebuilt from display lists every frame onto a fresh buffer, so a background-less view cannot ghost — there is no previous frame in its buffer to ghost from. `invalidate()` there means "re-record my display list," never "clear my pixels."
- Software erase responsibility is distributed overdraw: window background first, then each opaque view/parent covering its bounds. A background-less view contributes zero coverage — correct, because whatever is beneath it (already repainted this pass, or legitimately preserved) is what should show.
- Isakai's `RecordingCanvas`/`DisplayList`/`drawRenderNode` (`framework/android/graphics/Canvas.java` header) replays onto the same persistent software canvas, i.e. it has the display-list *recording* shape with the software *buffer* lifetime — the combination in which erase responsibility silently falls through the cracks.

### Gaps
- The hardware-acceleration guide's former display-list/invalidate passages are gone from the current page (it returned no display-list content when fetched 2026-10-28); the replay-every-frame characterization above is inference from long-standing platform behavior, not a currently citable doc paragraph.

## What Activity/Window frame erase covers, and when it runs relative to view draws

### Takeaway
In isakai, frame erase is a full-window opaque fill that runs strictly before any view draws, once per presented frame. Official Android's analog is the window background painted at the start of traversal — also before (beneath) all views — never a per-view step.

### Cited Findings
- `Activity.paintRoot` per pass: map (or allocate) the window buffer, `canvas.drawColor(Color.WHITE)` over the whole frame, `root.draw(canvas)`, ARGB→XRGB fixup, then `commit`/`post` — `framework/android/app/Activity.java` (`paintRoot`, ~lines 1255-1286). Both the zero-copy (`mapBuffer`) and copy (`new Canvas(w,h)` + `post`) paths begin with the same full-frame WHITE fill, and it precedes `root.draw`.
- `drawFrame` (measure → layout → `dispatchHostPreDraw` → `paintRoot`) is the single funnel: the window loop calls it for the initial frame, on activity/root switches, on dirty (`isDirtyAndClear()`), and per input drain — `framework/android/app/Activity.java` (~lines 1026, 1043, 1069-1072, 1234-1244). Every presented frame therefore passes through the WHITE clear.
- `View.drawAt` clips to the view's own bounds and paints `background` or `onDraw` content strictly inside that clip — `framework/android/view/View.java` (~lines 1352-1391). Per-view fills are clip-scoped overdraw inside the frame, ordered by the tree walk (parents before children, in-order siblings).

### Inferences
- Ordering in both systems is: frame erase first (whole window), then views back-to-front, each confined to its clip. Nothing runs *after* a view to clean up behind it; nothing runs *before* a single view except its ancestors' overdraw.
- Given the full-frame WHITE clear runs before every `root.draw`, cross-pass staleness is already impossible on the current code — the per-view whitewash cannot be load-bearing for previous-frame pixels. It can only be load-bearing if (a) the experiment also removed/weakened the frame clear, or (b) some pass reuses the mapped buffer without going through `paintRoot`. The mapped SHM buffer does persist across frames (`new Canvas(mapped, w, h)` wraps, never zeroes), so any pass that skips the WHITE fill inherits the previous frame verbatim.

### Gaps
- No surviving official doc paragraph states "DecorView draws the window background before views" verbatim; treated as inference.
- The exact buffer-reuse path that makes the whitewash load-bearing (experiment scope or a `paintRoot`-bypassing draw) needs one working-tree check by the implementer.

## For a single persistent pixel buffer with no GPU, known-correct erase strategies and their costs

### Takeaway
Three correct options exist — full-frame opaque clear per pass, dirty-rect clear, per-view clip clear — and the repo's own blending code proves why "transparent" is not a fourth: transparent writes are a documented no-op, and re-drawn antialiased text provably darkens without an opaque reset.

### Cited Findings
- `setIdx`: "Src-over store: opaque colors overwrite, translucent colors blend"; alpha 0 returns early leaving the destination pixel untouched; otherwise `blend(src,dst)` SRC_OVER — `framework/android/graphics/Canvas.java` (~lines 186-220). Consequences: (1) `drawColor(TRANSPARENT)` / transparent `backgroundColor` erases nothing; (2) any translucent pixel redrawn over itself darkens toward the source color.
- `drawColor(int)` with no mode always blends (`paintRect` → `setIdx`); only `drawColor(color, PorterDuff.Mode.SRC/CLEAR)` replaces via `paintRectRaw`/`setIdxRaw` — `framework/android/graphics/Canvas.java` (~lines 846-856). A true clip-scoped reset must use the SRC/CLEAR mode path, not a plain fill.
- Text accumulation mechanism: `HostText.draw` reads the current pixel (`raster.getRGB` → `canvas.getPixel`), pre-blends the glyph coverage over it, and stores the result back **opaque** (`0xff000000|...`) — `framework/android/graphics/HostText.java` (~lines 52-58). Redrawing the same glyph without an opaque reset blends coverage over already-darkened pixels, so edges darken every pass — exactly the reported "double-drawn text on repeated passes."
- The `lockCanvas()` contract (cited above) already prices the strategies: full rewrite every pass, or tracked dirty rects with preserved surroundings — [SurfaceHolder reference](https://developer.android.com/reference/android/view/SurfaceHolder).

### Inferences
- (a) **Full-frame opaque clear per pass** (status quo WHITE in `paintRoot`): cost one O(w·h) fill per presented frame; matches the no-dirty-rect `lockCanvas` contract ("every pixel must be written"); trivially correct with full-tree redraw and needs no bookkeeping. This is the narrowest correct erase for isakai's architecture, and it already exists.
- (b) **Dirty-rect clear** (opaque fill or SRC/CLEAR of the invalidated union before redraw): cost O(dirty area) but requires real rect plumbing — isakai's dirty flag is a single boolean and `invalidate(Rect)` drops the rect, so this buys complexity (union/intersection bookkeeping, preserved-region discipline) for a buffer that is fully repainted anyway. Not worthwhile until partial redraw exists.
- (c) **Per-view clip clear** (status quo whitewash in `onDraw`): cost O(sum of bounded view areas) with multiplied overdraw where views overlap, and it conflates two jobs — background color vs. erase. Correct only as erase if it runs before *all* painting in the clip (it does: `onDraw` precedes children) and only needed at all if the frame clear is absent. Narrowing path: restore the opaque frame clear, then shrink per-view fills to actual background styling (or `isOpaque()`-driven hints per the official contract), verifying text-heavy screens (settings toggles) and animation frames for accumulation.
- Caveat: `Mode.CLEAR` writes 0x00000000, but `paintRoot`'s ARGB→XRGB fixup forces alpha to 0xFF afterward (transparent-black becomes opaque black), so CLEAR is only useful mid-pass under subsequent opaque overdraw, never as a final frame state.
- Suggested verification (no new test scaffolding per repo policy): drive the settings-toggle and animation flows, screenshot repeated passes over one buffer, and compare glyph edge darkness with/without the opaque frame clear.

### Gaps
- No performance numbers were measured; the O(area) costs above are reasoning from the fill loops, not benchmarks. At 640×480-class buffers all three are small in absolute terms — measure only if frames regress.
- Whether `mapBuffer` ever hands back a buffer whose previous contents survive a missed clear (vs. compositor-side clearing) was read from the Java side only; the native `IsakaiWindow`/`nPost` path was not audited.
