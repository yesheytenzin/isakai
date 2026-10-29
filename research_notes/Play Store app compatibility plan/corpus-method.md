# Compatibility corpus method (Waydroid / ChromeOS / Proton lessons for isakai)

## How do projects like Waydroid, ChromeOS, or Proton pick their compatibility corpus and track per-app status at scale?

### Takeaway
Nobody tracks "all apps": each project starts from a small curated/whitelisted corpus (top-played titles, community-nominated reports), grades every entry on a fixed public rubric, and scales status tracking with crowdsourced reports plus structured environment metadata — the shared platform team then works the subsystems, not the apps.

### Cited Findings
- Steam Deck compatibility uses four public per-title grades — Verified, Playable, Unsupported, Unknown — and Valve states it is "reviewing the entire Steam catalog," with ratings updated as Deck/Proton features unblock previously-Unsupported games — [Source](https://www.steamdeck.com/en/verified)
- A title earns Deck Verified only by passing four fixed boxes: Input (full controller support, correct icons, on-screen keyboard), Display (default 1280x800/720 resolution, legible text), Seamlessness (no compatibility warnings, controller-navigable launcher), System Support (game plus all middleware, including anti-cheat, supported by Proton) — [Source](https://www.steamdeck.com/en/verified)
- Proton's issue tracker encodes corpus management in labels: "Game compatibility" marks whitelisted games, "Whitelist Request" / "Whitelist Update Request" is the community nomination queue for titles tested working, alongside subsystem labels (.NET, XAudio2, DXVK/D3D9, Mesa/NVIDIA drivers, overlay) — [Source](https://github.com/ValveSoftware/Proton/labels)
- ProtonDB grades titles on a fixed medal ladder — platinum, gold, silver, bronze, borked — with parallel native-client and Deck-Verified dimensions, observed in the site's client bundle alongside report-ranking facets (popular, wilsonRating, playerCount, mostBorked, fixWanted) — [Source](https://www.protondb.com/)
- Waydroid triages at scale through a mandatory structured bug template (Waydroid version, device class, OS, kernel, desktop environment, GPU, plus `waydroid.log`, logcat, dmesg, config) — i.e., every per-app report arrives with the environment metadata needed to cluster it to a subsystem — [Source](https://github.com/waydroid/waydroid/blob/main/.github/ISSUE_TEMPLATE/bug_report.yml)
- Android's own Compatibility Test Suite (CTS) is the contract-first extreme: a fixed, versioned, runnable corpus (per Android release, with CDD definition documents) that devices run locally rather than any per-app list — [Source](https://source.android.com/docs/compatibility/cts)
- ChromeOS publishes an app-side test plan instead of a corpus: multi-window/orientation state persistence, window resize/maximize/free-resize without crashing, mouse/keyboard interaction review, sensor-availability manifest triage (touchscreen/GPS marked `required="false"`), Wi-Fi-based location fallback — [Source](https://developer.android.com/topic/arc)

### Inferences
- The adoptable pattern is: small pinned corpus (this repo already has it — 4 pinned APKs in `progress/APPS.md` with sha256 pins) → fixed per-app grade rubric → structured failure reports with environment metadata → subsystem clustering. Isakai's `verify-isakai` pinned-app flows plus screenshots/logs are the seed of the same system; the missing pieces are the public grade ladder and the report template.
- Corpus selection everywhere is popularity/community-weighted (most-played, whitelist requests, wilsonRating/playerCount facets), never alphabetical or exhaustive — the plan should define selection criteria (e.g., top-free per category, FOSS-licensed for pinning) rather than app names.

### Gaps
- ProtonDB's exact report-submission fields (Proton version, distro, GPU driver, tweaks) could not be confirmed from a primary source — protondb.com and /help render client-side and the API endpoint probed returned a not-found page; only medal-tier names and ranking facets were recovered from the JS bundle.
- Valve's original Steam Play whitelist criteria (2018 announcement) could not be extracted — the announcement page body is login/JS-gated.

## What failure taxonomy separates shared-contract fixes (one fix, many apps) from app-specific accommodations?

### Takeaway
The taxonomy that works is symptom-at-app / root-cause-at-subsystem: per-app entries record the visible failure, but fixes land in the shared layer (graphics, audio, input, fonts, middleware), with per-title accommodations kept as data/config outside the core and re-validated against vanilla builds.

### Cited Findings
- Proton's changelog is written per-game ("Now playable: X", "Fixed Y crashing on AMD GPUs") but the fixes are overwhelmingly shared-subsystem work: video playback, font rendering (Traditional Chinese, Korean input), keyboard input, multi-monitor launch placement, redistributable-install loops, NVIDIA Optical Flow/DLSS API support — [Source](https://github.com/ValveSoftware/Proton/wiki/Changelog)
- Proton keeps its few title-specific accommodations as data, not code forks: `xalia-fixups.gudl` (per-window-class UI automation rules applied on top of a shared ruleset) and launch-environment scripts in the repo root, separate from Wine/DXVK/vkd3d subsystem code — [Source](https://github.com/ValveSoftware/Proton)
- Proton's tracker asks reporters to retest issues "with vanilla Proton" ("Need Retest" label), i.e., a reported failure must reproduce without per-title custom builds before it counts as a shared-contract bug — [Source](https://github.com/ValveSoftware/Proton/labels)
- Recent Waydroid issues cluster exactly this way: per-app symptoms (Instagram RenderThread SIGSEGV, Instagram/TikTok black-video-with-audio, camera crash, ARM64 game hang) rooted in shared subsystems (minigbm/gralloc vs cros_gralloc_h fallback, OMX media manifest, libndk translator, HWC) — [Source](https://github.com/waydroid/waydroid/issues)
- Deck Verified's "System Support" box makes the rule explicit: the game *and all its middleware* (including anti-cheat) must be supported by Proton — middleware-level, not title-level, support is the gate — [Source](https://www.steamdeck.com/en/verified)

### Inferences
- Proposed isakai taxonomy, mirroring the above: L0 install/parse/signing → L1 launch/lifecycle (activity, process, permissions) → L2 render/layout (view system, hwui/canvas, window management) → L3 input (touch, keyboard, back/focus) → L4 media/sensors (codec, camera, GPS) → L5 platform services (Play Services, accounts, push) → L6 native/ABI (NDK, JNI, translators). A failure counts as shared-contract when two pinned apps from different authors hit the same layer; single-app-single-layer failures get recorded against the app, fixed only if the fix is in the shared contract.
- This preserves the repo's shared-fix-first discipline (no per-APK hooks): the only permitted "accommodation" layer is data (pins, config, environment), never branched runtime code — same role as Proton's gudl fixups.

### Gaps
- No source found quantifying what fraction of Proton/Waydroid fixes are shared vs title-specific; the discipline is inferred from repo layout and changelog content, not stated policy.

## What metrics prove progress (install rate, launch rate, feature-complete rate) and what automation drives them?

### Takeaway
Mature projects measure a funnel — install → launch → playable/feature-complete — per corpus entry, aggregate it into tier distributions, and automate the funnel stages: scripted install/launch smoke (Robo-style crawlers, device farms), contract suites (CTS), and crowdsourced depth (real-user flows) for what automation cannot reach.

### Cited Findings
- The Deck Verified ladder (Verified/Playable/Unsupported/Unknown) plus ProtonDB medals are distribution metrics: progress is proven by titles migrating upward between fixed tiers over time, not by anecdotes — [Source](https://www.steamdeck.com/en/verified)
- Firebase Test Lab provides the automation model for the top of the funnel: Robo tests that automatically crawl an app on real and virtual devices across a device matrix, invokable from console, gcloud CLI, Android Studio, or CI — [Source](https://firebase.google.com/docs/test-lab)
- CTS is the automation model for contract depth: a runnable, versioned suite asserting platform behavior independent of any app, run on-device via Trade Federation — [Source](https://source.android.com/docs/compatibility/cts)
- ChromeOS's ARC test plan defines the manual middle of the funnel that automation misses: state persistence across minimize/resource-pressure/restore, resize/maximize/free-resize crash checks, keyboard/mouse parity, sensor-absence behavior — scripted as human test cases, not unit tests — [Source](https://developer.android.com/topic/arc)
- Waydroid's `waydroid bugreport` + logcat/dmesg attachment rule shows the evidence standard that makes crowdsourced metrics trustworthy: every status claim carries logs, not just a screenshot — [Source](https://github.com/waydroid/waydroid/blob/main/.github/ISSUE_TEMPLATE/bug_report.yml)

### Inferences
- Adoptable funnel for isakai, extending the existing `verify-isakai` flows (isolated pinned-app flow, live-compositor drive, privileged gate — all already evidence-graded with screenshots/logs/exit status): stage 1 install rate (APK parses, installs, appears), stage 2 launch rate (first frame renders, no crash-loop), stage 3 flow-complete rate (the pinned real-surface task finishes and persists), stage 4 regression rate (previously-green flows still green after each change). The repo already does stages 1–3 manually per pinned app; the method addition is per-app grade recording plus a "vanilla retest" rule before any shared-contract claim.
- Full Robo-crawler/CI automation is premature for isakai (no harness, skill forbids test scaffolding); the near-term automation is the existing flow scripts run across all pinned apps on every change, with results recorded in PROGRESS.md — the human-driven equivalent of Test Lab's matrix.

### Gaps
- Android Vitals bad-behavior thresholds (crash/ANR rates) could not be extracted — the vitals crash page body is nav/JS-heavy and the threshold figures were not recovered; do not cite numeric thresholds without a source.
- No primary source found for Proton's internal automation (how Valve regression-tests Proton across the whitelisted corpus before release); the changelog documents outcomes, not the harness.

## What is a sane phase order: which app categories unlock first and compound into later ones?

### Takeaway
Every project orders by dependency depth: offline single-activity apps first (they exercise only install/launch/layout/input), then storage/persistence, then media/sensors, then networked/account/Play-Services apps, then graphics- and ABI-heavy games last — each phase's shared fixes compound because later categories reuse the same subsystems.

### Cited Findings
- Deck Verified's four boxes are implicitly ordered by depth: Input + Display (needed by everything) → Seamlessness (launchers, warnings) → System Support (middleware/anti-cheat, the hardest) — [Source](https://www.steamdeck.com/en/verified)
- ChromeOS ARC guidance orders the same way: basic large-screen/window behavior first, then input parity (mouse/keyboard), then sensor-absence handling, then capability-specific work (location fallback, camera) — [Source](https://developer.android.com/topic/arc)
- Waydroid's live issue backlog demonstrates the dependency stack empirically: windowing/compositor and app-lifecycle issues are foundational, while the long tail is media (OMX manifest, black video), camera, and ARM-translation (libndk) — the categories that only matter once basic apps already run — [Source](https://github.com/waydroid/waydroid/issues)
- Proton's history follows the same arc: years of graphics/audio/input/middleware fixes first, with anti-cheat-dependent multiplayer titles the last frontier (still gated under "System Support") — [Source](https://github.com/ValveSoftware/Proton/wiki/Changelog)
- This repo's own trajectory matches: offline utilities first (Calculator, Notes, OpenCalc flows proven), then the native/ABI target (Puzzles JNI app) — matching "simple contract apps before native/platform-service apps" — [Source](../verify-isakai/SKILL.md)

### Inferences
- Recommended phase order for the plan: P0 offline utilities (calculator, notes, puzzles — done, the compounding base) → P1 storage/clipboard/sharing contracts → P2 media playback and camera/sensor-gated apps → P3 networked + account apps → P4 Play-Services-dependent apps → P5 native-heavy games. Entry criterion per phase is a small pinned set (3–5 apps, FOSS-pinnable like the current APPS.md pins); exit criterion is all pinned apps at the top grade for two consecutive runs.
- Each phase must state which shared subsystems it stresses (e.g., P2 = codecs/surfaceflinger-equivalents) so that "app picks" stay a method: any app exercising the same layers is an acceptable substitute.

### Gaps
- No project publishes an explicit "phase order" document; the ordering above is inferred from rubric structure, changelog history, and issue-backlog composition, not quoted policy.
