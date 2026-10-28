# C6 remaining verification gaps — worker report (2026-10-27)

Scope was evidence and honest recording only. No code changes to
`framework/`, `src/`, or `verify-isakai/` (`git diff` empty; `git status`
shows only this report plus `evidence/C6/2026-10-27/gaps-scrim/`).
`zig build` exit 0. Drivers were throwaway `/tmp` scripts using the
existing `SwayCompositor` click/hold primitives, deleted after the runs.
Unique env used throughout: `ISAKAI_WAYLAND_DIR=/tmp/isakai-wayland-gaps`
(private sway, `swaybg` error is the known non-fatal fixture warning),
`WAYLAND_DISPLAY` via the harness `isolated_env()`,
`ISAKAI_BRIDGE_DIR=/tmp/isakai-1000-gaps`,
`ISAKAI_STAGE_DIR=/tmp/isakai-1000-gaps-stage`.
App tree was the copied `c6-history-fresh` work dir (history rows already
committed), pinned `calculator-fdroid-release.apk`, same-UID throughout.

## (a) Scrim tap vs 1500ms hold — CLOSED, no hold needed

Exact repro run on the real pinned Calculator: open history at (450,16),
then press at (320,318). Three launches, screenshots per outcome, app
alive throughout, compositor close exit 0 in every run:

- 80ms tap: dialog open
  ([open](../evidence/C6/2026-10-27/gaps-scrim/calculator-gaps-tap-open.png))
  → main calculator, no dialog/scrim
  ([after](../evidence/C6/2026-10-27/gaps-scrim/calculator-gaps-after-tap.png)).
- 1500ms hold: dialog open
  ([open](../evidence/C6/2026-10-27/gaps-scrim/calculator-gaps-hold-open.png))
  → main calculator, no dialog/scrim
  ([after](../evidence/C6/2026-10-27/gaps-scrim/calculator-gaps-after-hold.png)).
- 80ms tap repeat: same dismissal
  ([open](../evidence/C6/2026-10-27/gaps-scrim/calculator-gaps-tap2-open.png) /
  [after](../evidence/C6/2026-10-27/gaps-scrim/calculator-gaps-after-tap2.png)).

Verdict: both tap and hold at (320,318) cancel the dialog via the normal
cancel-on-outside path — correct behavior, no hold-to-dismiss needed.
(320,318) is window-relative scrim (dialog box sits left of it in all
three open shots). The single earlier silent-tap datum (dialog stayed
open, no overlay DOWN logged) is outvoted 3:1 and now reads as the known
occasional single-press-no-effect input flake (same signature as the
OpenCalc `=` miss), not a scrim/hit-testing defect. App logs retained
(`gaps-scrim-app.log`, `gaps-hold-app.log`, `gaps-tap2-app.log`); only
overlay line is the expected `new root DialogOverlayGroup` at open.
Limitation: same-UID only, not default-confinement proof (owned by N3/C7).

## (b) XKB reorder/delay stress — stays EXCLUDED (policy)

No honest observation of this gap is possible with existing tools, so no
new run is claimed. Read-only review of `src/bridge/xkb.zig` confirms what
is guarded (null-state handling in `updateMask`/`keyGetUtf32`/`modActive`/
`keyRepeats`, global fake-state modifier replay, locked-layout group passed
to `xkb_state_update_mask`) — and confirms what nothing exercises:
reordered keymap/modifier delivery and delayed/replaced keymaps mid-stream.
`isakai-vinput` and all three pinned flows only ever produce well-ordered,
single-keymap input; no existing CLI, command, or flow can inject a
reordered/delayed keymap+modifier sequence. Building one (a keymap-churn
harness / synthetic stress driver) is exactly what the verification-artifact
policy forbids: no test files, no test-only code, no harnesses, no
recreated suites. The passing typed flows stand as the only evidence and
do not cover the adversarial sequence.

What would be needed to close it: (1) a policy exception permitting a
minimal keymap-churn driver, or a real-compositor trigger (e.g. layout
switch mid-typing on a device the bridge actually reads — unproven whether
the virtual-keyboard fixture supports this); (2) pass criteria fixed in
advance: no crash under churn (the original SEGV shape), correct
Shift/Caps/nonzero-group behavior afterwards, visible input unaffected.
Until then this stays an explicit exclusion, not a pass.

## Every remaining C6 gap, consolidated

1. Scrim tap/hold — CLOSED (this report, evidence above).
2. XKB reorder/delay stress — EXCLUDED by policy, reason and needs above.
3. Settings screen renders blank (translated custom `r1` views run draw
   entry points but emit nothing; taps still flip/persist prefs) —
   DEFERRED defect, needs a dedicated draw-instrumentation session; not
   re-probed here.
4. Notes picker FAB blob + row overlap (narrowed to translated-Material
   wrong-color resolution; tint/blending/drawable-logging exonerated) —
   DEFERRED defect; not re-probed here.
5. Single-press-no-effect input flakes (tap, `=`, one silent scrim tap) —
   RECORDED pattern, no action until a third instance on an asserted
   surface; this report's 3/3 scrim dismissals weaken the scrim instance.
6. Independent (non-self) review — OPEN, needs the operator; all C6
   verdicts to date are self-review plus real-app evidence.
7. Old Calculator 5.11.3 full rollback usability — OPEN; the new
   measurement/stack/clipboard proofs are on 5.12.0 and do not transfer.
8. Default-confinement evidence for any of the above — owned by N3/C7,
   operator action; everything here is same-UID.

No shared `progress/*.md` or `.audit/*` file was edited.
