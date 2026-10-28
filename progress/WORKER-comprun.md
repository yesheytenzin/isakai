---
status: done
updated: 2026-10-28
---

# WORKER-comprun: compose-issued black arrow box + first-row stale duplicates

## Task

Resolve the two compose-issued artifacts pinned on the Calculator Settings
screen: (a) opaque-black 24x24 box over the back-arrow slot (+ smaller black
fragment below-right of it), (b) first-row content clipped by same-frame
duplicate draws at stale offsets. Baseline `before/` flow PASSES with both
artifacts pixel-identical to the 2026-10-27 cosmetics baseline (960 black px
in the 40x48 nav corner: 576 box + 384 fragment). No fix by guessing: every
change needs a before/after screenshot pair. Translated code regenerates per
install and is NOT committable; a fix needing translator changes
(`src/translate.zig`) or framework hooks becomes an exact handoff, not an
improvisation.

Constraints honored: `framework/`, `src/`, `verify-isakai/*` driven read-only
except temporary content-gated stderr probes, all fully reverted
(`grep -c COMPRUN framework/android/graphics/Canvas.java` → 0,
`javap` on the rebuilt `zig-out/framework` class → 0, `git status` shows only
this file + `evidence/`); no probes left in tree; `/tmp` analysis scripts and
scratch dirs deleted; sway fixture stopped after the runs. Recorded here only;
evidence under `evidence/C6/2026-10-28/comprun/`. `zig build` exit 0 before
and after (probe builds and the reverted final build); `git diff --check`
clean; `python3 tools/progress-lint.py` → 0 violations.

## Environment

- Unique env `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-comprun`,
  `WAYLAND_DISPLAY=wayland-1`, `XDG_RUNTIME_DIR` same,
  `ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-comprun`,
  `ISAKAI_STAGE_DIR=/tmp/isakai-1000-comprun-stage`.
  Private headless sway fixture; `.cache/isakai/{compositor,apks}` copied
  from the pinned main-repo cache. Same-UID development mode.
- Work dirs: `/tmp/comprun-calc-work` (probe runs), `/tmp/comprun-final-work`
  (clean final). One transient `install failed: HelperFailed` on first fresh
  install (stale bridge lock); immediate retry installed clean — recorded as
  fixture flake, not product finding.

## Baseline (before fix)

- `python3 verify-isakai/sway-flow.py calculator --work
  /tmp/comprun-calc-work --evidence evidence/.../comprun/before` → **PASS**
  (2+2=4 row, settings opens, decimal pref null→true, decimal row
  `1,5+2,5→4`, reopen persists, 31s liveness, back exit 0).
- `before/simple-03-settings.png` (640x800): black 24x24 fill at cols 4–27 ×
  rows 4–27 (nav slot) + second black fragment (cols 32–39 × rows 20–31 bar +
  cols 16–39 × rows 32–43 block) beside the white "Settings" title (starts
  x≈48); first-row glyph sliver only at rows 64–65 (cols 21–126), rows 66+
  blank to the GENERAL header (y≈167, fully rendered per WORKER-cosmetics).

## Diagnosis

Five instrumented flow runs (temporary `Canvas.java` probes, all reverted)
drove the real settings screen. Probe generations: (1) black-fill + text
inventory with short stacks; (2) + canvas identity, stroke/bitmap/save/clip/
translate tracing; (3) + paint state (raw color, color-filter class, xfermode
class) and 22-frame stacks; (4) bitmap source-rect/identity/paint state;
(5) 24x24-black-fill suppression experiment. All probe flows PASS.

### Artifact (a): exact emitter (probe4 log)

- Exactly ONE opaque-black fill in the whole run, drawn ONCE onto an
  offscreen layer canvas (cv=557023099, identity transform):
  `rect style=FILL color=FF000000 raw=FF000000 filter=null xfer=null
  user=[0.0,0.0,24.0,24.0]`
  `b1.c.d ← d1.c.e0 ← d1.e.g ← f1.d0.e ← f1.k0.d ← y0.j.e ← q1.d0.b ← …`
  (probe4 `COMPRUN n=309`), immediately followed by the arrow vector on the
  same canvas:
  `path verbs=12 color=FF000000 raw=FF000000 filter=null xfer=null`
  `b1.c.i ← d1.c.b0 ← d1.e.e ← f1.f.a ← f1.b.a ← f1.c0.r ← f1.d0.e ← …`
  (probe4 `COMPRUN n=310`).
- The layer bitmap is then blitted 6× per settings frame onto the screen
  canvas (3 consecutive frames, identical pattern): 5× at stale dev offset
  `(16,20)–(40,44)`, 1× at settled `(4,4)–(28,28)`, all via
  `b1.c.j ← d1.c.r ← q1.d0.r ← d1.e.c ← f1.d0.e ← f1.k0.d ← … ← q1.d.e`
  (probe4 `n=311…377…`, same `bmp=1256837057`, `src=[0,0,24,24]`,
  `alpha=255`). The blit paint carries
  `filter=android.graphics.BlendModeColorFilter` — which the framework drops
  (inherits `ColorFilter.filterColor`, logs one
  `ISAKAI_UNSUPPORTED ColorFilter.softwareTransform at b1.c.j bci=323`,
  returns texel unchanged). The drop is benign here: the source bitmap is
  already fully opaque black and the requested tint is BLACK
  (`scrolledColor=BLACK`), so honoring it would change nothing.
- The stale squares are trimmed by tight clips active at blit time (e.g.
  `clip [12,16,44,48]` under the prevailing translates, probe4 lines
  ~2810–2820), leaving exactly the observed bar+block fragment.
- Ruled out, with evidence: (i) NO dropped tint/xfermode at the draw calls —
  both paints are plain opaque black (`raw=FF000000 filter=null xfer=null`),
  the only `xfermodeRendering` stub in the run fires once from `r6.h.<clinit>`
  (vector-rasterizer static paint, unrelated call chain); the translator did
  not silently drop a setter (it demonstrably translates `setXfermode` —
  the r6.h call executed and logged). The runtime draws exactly what it
  intends. (ii) NOT app-level: the emitter chain contains only runtime
  classes (`f1/d1/b1/q1/y0`); zero app frames; upstream `SimpleNavigationIcon`
  (`Box{clip,clickable+ripple}{Icon(ArrowBack)}`, WORKER-cosmetics) contains
  no such background. So: runtime-emitted bounds fill, color explained by
  `scrolledColor=BLACK` (compose `Color.Black` long `4278190080` confirmed as
  the `d1.e.g` brush via `b1/t.b`), rect itself unexplained upstream —
  consistent with a DrawImage-behind-image convention or ripple state-layer
  stub in the translated `f1.d0.e` DrawImage path (which also handles
  `ALPHA_8/RGB_565/ARGB_4444` bitmap configs just above the call site).
- Suppression proof (probe5, `suppress/`): dropping exactly that one fill
  (`color==BLACK && FILL && 24x24`, the only black fill in the flow →
  `COMPRUNSUPPRESS n=1` once) renders a PERFECT back arrow ← at (4,4) plus a
  faint arrow-ghost at the stale offset; flow still **PASS**. The 12-verb
  path was intact all along; the fill is the sole cover; removal is safe
  (no other draw in the flow depends on it).

### Artifact (b): same replay disease at text level (probe4 log, frame cv=1808009213)

- Every settings string is drawn up to 7× in ONE frame at up to 4 dev
  offsets. Decoded Java hashes: `len=16/-2106254323` = "Customize colors"
  (row 1): 3× `(50,147)`, 2× `(50,83)`, 1× `(20,53)`, 1× `(0,0)`;
  `len=23/-28189297` = "Customize widget colors" (row 2): same shape at
  `(50,204)/(50,140)/(20,110)/(0,0)`; title `len=8/1499275331` = "Settings":
  `(0,0)/(56,19)/(8,0)`. Uniform deltas: stale-A = settled+(-30,-30),
  stale-B = settled+(0,+64), stale-C = (0,0).
- The visible pixels ARE the stale ghosts: row-2 glyphs at rows 114–124
  start at x=20 (= stale `(20,110)` draw); the row-1 sliver at rows 64–65
  starts at x=21 (= bottom of the stale `(20,53)` text peeking below the
  64px white app bar). Settled draws (`(50,83)`, `(50,140)`, …) are buried.
- Two dispatch routes reach the same layer replay per frame: settled/stale
  draws via `…r1.v2.dispatchDraw ← View.drawAt ← View.draw ←
  ViewGroup.drawChild ← r1.s1.a` (translated nested replay: a parent layer's
  replay draws child layers), and the `(0,0)` copies via the pure framework
  route `…r1.v2.dispatchDraw ← View.drawAt ← ViewGroup.dispatchDrawAt ←
  View.drawAt ← ViewGroup.dispatchDrawAt`. Every layer's record list
  (`r1.v2.dispatchDraw → r1.b2.a(b1/r)` replay, children via `r1.v2.l →
  r1.s1.a → drawChild`) carries content from multiple placement epochs —
  re-record APPENDS instead of clearing (3× one epoch, 2× settled).
- Placement step that skips invalidation: `r1.v2.e(long)` moves the layer
  via `View.offsetLeftAndRight/offsetTopAndBottom` with NO
  `setInvalidated/invalidate/forceLayout` call (full disassembly, returns
  after `r1/x1.c()`), and framework `View.offset*` are pure field updates
  with no invalidation (real-AOSP parity gap). This gap is RECORDED but is
  explicitly NOT the remedy: every frame repaints fully, so invalidation
  cannot remove same-frame replays; "fixing" offsets would only add
  repaints, not remove duplicates.

## Change

None committed (nothing committable: translated runtime regenerates per
install; `framework/`+`src/` touched only by fully-reverted temporary
probes). Verdicts per artifact:

- (a-i) black bounds fill: **handoff with exact location** — stop emitting
  the 24x24 Black bounds fill in translated `f1.d0.e → d1.e.g(scope, Black,
  …)` (probe4 `n=309` chain above); suppression experiment proves removal
  yields a perfect arrow with zero collateral (exactly 1 affected draw in
  the whole flow, flow still PASS). No safe framework hook exists: the host
  cannot distinguish this fill from legitimate dark content (a general
  small-black-fill rule would corrupt real dark-theme rendering).
- (a-ii) 5 stale blits/frame + (b) stale text replays: **handoff with exact
  location** — translated record/replay boundary: clear-then-record (or drop
  stale child layers from the `r1.s1` container) so `r1.v2.dispatchDraw →
  r1.b2.a` replays one placement epoch per frame, plus invalidate-on-
  placement in `r1.v2.e(long)` so ancestors re-record after a move. The
  framework `View.offset*` AOSP-parity invalidate is NOT the remedy (stated
  to prevent wasted work: it cannot touch same-frame replays).
- Ruled out with evidence (not deferred): framework dropped-tint/xfermode
  (paint-state proof), app-level placeholder (stack proof), translator
  silently dropping a blend setter (r6.h stub proves translation works),
  blit-tint drop mattering (source already black, tint is Black).

## Verification evidence (under `evidence/C6/2026-10-28/comprun/`)

- `before/` — clean-tree baseline flow **PASS**; `simple-03-settings.png`
  shows both artifacts (960 black px in nav 40x48; row-1 sliver rows 64–65).
- `probe4/` — instrumented flow **PASS**; full-trace log with 22-frame
  stacks + paint state (emitter lines `n=309/310`, blits `n=311…377…`,
  per-string 7× inventories, `ColorFilter.softwareTransform` single stub
  line) + identical screenshots.
- `suppress/` — suppression flow **PASS** (`COMPRUNSUPPRESS n=1`, once);
  `simple-03-settings.png` + zoom show the perfect arrow at (4,4) and the
  residual stale ghost; all other pixels and flow assertions unchanged.
- `final/` — clean-tree (probes reverted, fresh `/tmp/comprun-final-work`
  install) flow **PASS**; `simple-03-settings.png` reproduces the artifacts
  pixel-identically to `before/` (960 black px), proving the revert.
- Liveness: every flow above includes the 31s liveness window + back-exit 0.

## Code review (self-review)

- Diff: this file (new) + evidence only. `framework/`, `src/`,
  `verify-isakai/` byte-identical to HEAD except the reverted probe file
  (verified: `git status` clean apart from the two new paths;
  `git diff --check` clean; compiled class contains no probe symbols).
- Probes: all temporary edits confined to `Canvas.java` (drawRect,
  drawRoundRect, drawPath, emitText, drawBitmap(Rect,Rect), save/restore,
  translate/concat, clipRect, emitStroke), content-gated (no app strings
  logged — texts as `(len,hash)` pairs only), capped, stderr to the flow
  log. Reverted with `git checkout --`, rebuilt, reverified (`javap`
  clean + fresh-tree PASS + pixel-identical artifacts).
- Reuse/perf: no product code added or changed; probe builds only affected
  instrumented runs (flow timing unchanged, all PASS).
- Limits: (1) single-session, same-UID evidence; privileged runs remain an
  operator action. (2) Exact upstream analog of the `f1.d0.e` bounds fill
  not confirmable offline (no compose source access) — handoff names the
  two candidate origins (DrawImage-behind-image, ripple state layer) with
  the suppression proof making either removal safe. (3) Stale-text coverage
  order (which white rect buries settled draws) inherited from
  WORKER-cosmetics' white-row-rect probes, not re-proven here — immaterial
  to the handoff (remedy is at the record/replay boundary either way).
  (4) Self-review, no independent second pair of eyes. (5) The residual
  stale ghost (arrow fragment at (16,20), x=20 text ghosts) is expected to
  persist until the translated replay handoff lands — the suppression run
  documents it, not fixes it.
