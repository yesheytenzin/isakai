# Isakai progress (single source)

This file replaces every former `progress/*.md` task file, `ROADMAP.md`,
`REFERENCE.md`, `STATUS.md`, `OPEN.md`, `FINDINGS.md`, `HISTORY.md`,
`RESEARCH.md`, `PLAN.md`, `DEPENDENCIES.md`, `reports/`, and
`research-notes/`. Those were removed 2026-10-29. Nothing was deleted for
being wrong. Done work is summarized below with its proof. Raw history
survives in git (`git log -- progress/`).

Machine-readable APK pins live in `progress/APPS.md`, read by
`tools/pinned_apps.py`. That file is data, not documentation, and stays.

## What works

Three pinned utility apps complete useful visible flows on fresh installs,
same-UID development mode, verified on screenshots plus app-owned storage
plus exit 0. Calculator computes with history rows and persists settings
and decimals across restart. Notes saves exact Unicode bodies and survives
restart and edit. OpenCalc commits `12+34=46` with clipboard and resize.
Settings renders full content fast after the bridge poll fix. History
dialog shows stacked formulas with results and dismisses by tap or Escape.
Note picker shows full-width periwinkle rows, blue FAB with plus icon.

Default isolation is proven separately. The required privileged gate
passes end to end with uid, no-new-privs, seccomp, and mount-namespace
evidence. Puzzles installs as uid 200003, launches confined, renders the
full chooser, and generates Guess, Magnets, Fifteen, and Solo boards with
played moves persisting. Generator subprocesses run under the bionic
loader through the installed exec runner.

Framework contracts repaired at their owning layers, each proven on the
real surface: unbounded RelativeLayout measurement, clipboard service on
every context, nonblocking bridge poll, drawable-state delivery,
ImageView src resolution, canvas color filters, advance-based line wrap,
device-space round rects, scrolled-pager touch plus draw offsets,
scroll-aware window coordinates, TextView layout exposure, translator
sealed-metadata preservation, loader extinfo plus dlerror rules, input
seat re-acquisition, and the fits-windows methods AppCompat probes for.

## Proof shots

- `evidence/proof/calc-settings.png`: Settings with GENERAL header, toggles, checkboxes.
- `evidence/proof/calc-history.png`: history rows with formulas and results.
- `evidence/proof/notes-picker.png`: periwinkle rows with text, blue FAB, plus icon.
- `evidence/proof/notes-unicode.png`: exact Unicode body in the editor.
- `evidence/proof/opencalc-result.png`: committed `46` result.
- `evidence/proof/puzzles-chooser.png`: full game grid under confinement.
- `evidence/proof/puzzles-solo.png`: generated Solo board, playable.
- `evidence/proof/puzzles-guess.png`, `puzzles-magnets.png`, `puzzles-fifteen.png`: confined generated boards.

Older per-run logs and duplicate screenshots were pruned 2026-10-29.
Every claim above keeps one shot. Re-verify by re-running the flows in
`verify-isakai/SKILL.md`, not by digging history.

## Open

- N18 NDK assets: no pinned library imports NDK asset symbols, so no
  caller exists to prove fd access against. Re-survey pins with
  `nm -D | grep AAsset` when N6-class work lands a native caller.
  Do not author probe apps.
- Input flakes: single taps intermittently vanish with no wrong effect,
  on every surface. Commit taps retry until durable state confirms.
  Watch item, no owner.
- Notes save trigger: the pager gate fix holds, watcher parity holds,
  one fail-then-pass remains on record. Reopen if a third instance hits
  an asserted surface.
- Non-self review: all verdicts to date are self-review plus real-app
  evidence. Needs a human with fresh eyes.
- Services punch list: push, fused location, maps, and basic auth gaps
  ranked with file pointers in `progress/IMPL-streamd.md`. Push transport
dwarfs the rest.
- GPU gap list: host serves Vulkan 1.4 plus GLES 3.2 natively, yet the
  framework has no GL bindings, no EGL frame route, and no feature
  reporting. Ordered gaps in `progress/IMPL-streamc.md`. First fix is
  the `android.opengl` binding surface.
- XKB reorder and delay stress: excluded by the verification-artifact
  policy. Needs a policy exception plus a minimal churn driver.

## Roadmap (scheduled, no action)

N7 native ARM64 hosts, N8 QEMU guest, N9 FEX and Box64 compare, N10
Digitalis feasibility, N11 ART compare, N12 Robolectric compare, N13
signed splits, N14 Skia text, N15 native windows, N16 compatibility
evidence, N17 32-bit support. Start per acceptance criteria when the
phase opens. Narrower NDK asset work waits on the N18 caller above.

## F tracks (promotion plans)

Full program plan with cited sources: `reports/Play Store app compatibility
plan.md`. Six sequenced phases from proven utilities to native games,
each gated by motivating apps plus a named owner, exiting only on top
grades across two consecutive runs. Runnable explicitly excludes
integrity-gated, hardware-DRM, and cheat-guarded apps on any uncertified
host. ARM-only native libraries load directly into the runner on ARM64
hosts with no emulation layer. QEMU-user on x86_64 stays best-effort
with documented GPU and page-size walls. Full coverage comes from
shipping both host architectures with slice-aware install: each host
runs its own native slice, and ARM code never executes on x86 silicon
by design rather than by gap. Per-track entry gates:

A track starts only with a motivating pinned app plus a named owner.
Each plan below is the entry gate, not a backlog.

- F1 media: app with audio or video need. Bounded async playback under
  MediaPlayer via host audio. Proof is a 30-minute stream with
  pause, seek, loop, hung-decoder timeout, and bounded memory, same
  flow under default isolation. Estimate 3 to 6 weeks.
- F2 WebView: app with a required page. Real engine with JS, cookies,
  origin-safe bridge, and child crash recovery. Proof is the pinned
  page rendering and interacting plus failure without app crash.
  Estimate 4 to 8 weeks pilot.
- F3 ARM native proof: required before any broad ARM integration.
  Proof is one native binary executing correctly on hardware.
  Anti-sprawl gate, not a feature.
- F4 sensors and location: app with a provider need. Honest denial
  and unavailable paths, real host values, no broad device grants.
  Proof per provider family with denial cases. Estimate 1 to 3 weeks
  per family.
- F5 native and hardware coverage: on-demand expansion only. Proof
  per addition on its motivating app. No standing scope.

## Rules

One status lives here. Never restate it in body prose anywhere else.
Verification is real app flows only, per `verify-isakai/SKILL.md`.
No test files, no test-only probes, no harnesses. Evidence keeps one
proof shot per claim; per-run logs are not retained.
