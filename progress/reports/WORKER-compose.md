# WORKER-compose: pinned Calculator Settings renders promptly

## Task

Make the pinned Calculator app's Settings screen draw pixels: composition
runs, taps flip prefs, pixels were missing/slow, zero framework
`androidx.compose` support.

## Spike findings (fresh work tree, real surface)

- The Settings screen is Jetpack Compose, fully obfuscated: `r1.w` is
  `AndroidComposeView` (`onDraw`/`dispatchDraw`/`getSharedDrawScope`/
  `getHasPendingMeasureOrLayout`), `r1.s1` its view container
  (`a(b1.r, View, long)` + `dispatchDraw`), `r1.v2` its `ViewLayer`
  (`getOwnerView`/`getLayerId`/`forceLayout`/overridden `invalidate`).
  Runtime/snapshot/coroutines ride along as `j0.*`/`u9.*`. The
  `androidx.compose.*` classes in the APK are 74 leftover
  Modifier-Element/exception stubs — no Composer/Recomposer there, so no
  framework `androidx.*` reimplementation was ever needed. The app ships
  its own compose runtime; only host-contract behavior was missing.
- The earlier "zero Compose classes in draw" census was an obfuscation
  artifact (it grepped for the literal `compose` package name).
- Translated compose code hits only these stubbed framework APIs on this
  screen, all non-fatal `ISAKAI_UNSUPPORTED` (composition, taps and
  persistence all work through them): `View.tooltipRendering`,
  `Paint.xfermodeRendering`, `Environment.sharedExternalStorage`,
  `View.androidSystemBars`, `Activity.taskIconAndColor`,
  `View.hostDragAndDrop`, `RippleDrawable.animation`.
- Root cause of the slow/missing pixels: the window loop ran at ~1 Hz.
  Temporary probes (all reverted) measured `desk=1001ms` on **every**
  slice — `Activity.pollDesktopResults` → `IsakaiDesktop.poll` → native
  `desktop_mod.poll` → `{"op":"poll"}` round-trip to `isakai-dbus-bridge`
  sat in its "blocking settle" (`while empty and waited < 1000:
  pumpOnce(br, 100)`) for the full second whenever the event queue was
  empty, i.e. almost always. `drawFrame` itself measured 22–33 ms; the
  loop simply never got there. Input drained late (DOWN→UP observed
  13 ms–1.1 s for an 80 ms tap), the pref commit landed seconds after the
  tap, and compose recompose invalidates/redraws stacked 2.4–6.8 s behind
  (screenshots went stale→fresh across 5–8 s with no further input).
- A parked `kotlinx.coroutines.DefaultExecutor` seen mid-lag is an
  app-scheduled timed delay in the tap→recompose path, not a framework
  primitive; it is not addressed here (see Limits).

## Change (one hunk, no banned files touched)

- `src/dbus_bridge.zig`, `"poll"` op: removed the up-to-1 s blocking
  settle; the op now does the nonblocking `drainBus` and returns. The app
  polls every window-loop slice, so a signal arriving just after a drain
  is picked up on the next slice (~100 ms). Rationale preserved in the new
  comment. No protocol, Java or translator change; none of the C6-owned
  files (`View`, `ViewGroup`, `RelativeLayout`, `Dialog*`, `Context*`,
  `ClipboardManager`) were modified.

## Verification (real surface, same-UID)

- Fresh-tree spike after the fix: tap → pref flips in <1 s (was ~1.4 s),
  first screenshot at t+1 s already shows the fresh checkbox state (was
  5–8 s). Settings opens with visible rows; one toggle persists
  (`Prefs.xml` read back `true`).
- `python3 verify-isakai/sway-flow.py calculator|opencalc|notes` on fresh
  `isakai install` trees via the isolated Sway fixture (unique
  `ISAKAI_WAYLAND_DIR`/`ISAKAI_BRIDGE_DIR`/`ISAKAI_STAGE_DIR`; the stock
  script's hardcoded `isosway` bridge dirs were honored from env through a
  throwaway `/tmp` copy because two other agents hold live bridges on the
  defaults — flow code and assertions byte-identical, copy deleted after):
  calculator PASS, opencalc PASS, notes PASS (rerun; first notes attempt
  hung driver-side past step 04 with its stdout lost to the 900 s timeout
  and did not reproduce — recorded as flake, not a product finding).
- Evidence (in-tree `.cache`, not committed):
  `.cache/isakai/spike-ev/flows-calc/` (PASS incl. `simple-03-settings.png`,
  `simple-04-toggled.png`), `flows-ocalc/`, `flows-notes/` + driver log,
  `simple-spike7-*.png` (fresh-tree tap→fresh-pixels ≤1 s series).
- `zig build` exit 0; `git diff --check` clean; `python3
  tools/progress-lint.py` 0 violations. No probes remain (`grep`
  SPIKE/IsakaiProbe over `framework/ src/ verify-isakai/ tools/` is
  empty). `/tmp` drive scripts deleted; sway fixture, apps and bridges
  stopped after the runs.

## Limits / handoff

- Residual ~1–2 s toggle-to-pixels latency remains (flow's `04-toggled`
  shot can still match the pre-tap frame while the pref file has already
  flipped; a dedicated spike7 series caught it at ≤1 s). Loop quantum is
  now ~0.11 s, so the remainder is app-scheduled (coroutine delay in the
  translated tap path), not loop starvation. Needs a session with draw
  instrumentation, not a framework rewrite.
- Cosmetic, unchanged by this fix and not acceptance-blocking: first
  settings row clipped at top, section header shows "GENER", back arrow
  paints as a black box, unchecked boxes render as plain outlines.
- The `poll` long-poll also served the file-picker cancel path; no pinned
  flow exercises file pick, so cancel latency past one slice (~100 ms) is
  reasoned, not proven. A future file-picker flow should assert cancel
  explicitly.
- First notes attempt hung driver-side (post-`04-note-picker`, no `05`
  shot, stdout lost); rerun passed clean. If it recurs, suspect the
  unbounded `grim` call in `SwayCompositor.shot` or a stuck `vinput`
  command rather than the app (app log ends normally at the picker root).
