# Isakai progress

The `progress/` folder is the single planning and progress source. Each task file owns its status, scope, dependencies, acceptance, evidence and code review. This index is navigation and roadmap only; it does not copy task statuses or completion checklists. Open a task to see its current status.

## Start here

Start with [C8](C8.md) for the cross-app compatibility audit and repairs,
[C7](C7.md) for the pinned-flow acceptance record, and [C6](C6.md) for
translator/framework failures. Resource and native work belongs to [N5](N5.md),
[N6](N6.md) and [N18](N18.md). Read each task for its current status and evidence;
historical app passes do not establish support for the current implementation.

- [Shared delivery plan and design rationale](PLAN.md)
- [Pinned app manifest](APPS.md)
- [Historical evidence and research](HISTORY.md)
- [Verification and completion procedure](../verify-isakai/SKILL.md)

## Roadmap

Milestones map to PLAN.md's four release decisions. A task's fine-grained `phase:` label groups under the milestone shown here. Open a task for its status; the state below is milestone-level orientation only.

| Milestone | Outcome (PLAN.md release decision) |
|---|---|
| **M1 — x86_64 bytecode compatibility** | Pinned apps complete useful flows, persist data, and run under default isolation. |
| **M2 — Reuse & same-ISA native foundation** | Trusted runtime execution, resource and Bionic/JNI ownership, and bounded runtime comparisons. |
| **M3 — Native host architectures & release** | Both host architectures, signed split installs, NDK assets, rendering, graphics and per-backend evidence. |
| **M4 — Foreign-ISA execution** | A foreign-ISA backend passes a named corpus; accelerator choices follow measured fidelity. |
| **Capability expansion** | Media, WebView, sensors and hardware enter with motivating apps and maintenance owners. |

## Tasks by milestone

Listed in dependency order within each milestone (a task's `Dependencies` section is authoritative). Open a task for its status.

### M1 — x86_64 bytecode compatibility

Runtime hardening and isolation:
- [R1: Remove the production window timeout](R1.md)
- [R2: Repair and prove confinement](R2.md)
- [R3: Verify policy packaging and clean installation](R3.md)
- [R4: Make privileged E2E failures authoritative](R4.md)

App compatibility:
- [C1: Pin three target apps and useful flows](C1.md)
- [C2: Add APK compatibility diagnostics](C2.md)
- [C3: Implement the main-thread message queue](C3.md)
- [C4: Support in-app activity navigation](C4.md)
- [C5: Make target layouts and text usable](C5.md)
- [C6: Close measured translator and framework failures](C6.md) — done; history-list render and xkb stress tracked as explicit deferred defects
- [C7: Pass the three-app acceptance gate](C7.md)
- [C8: Replace app accommodations with shared compatibility contracts](C8.md)

Verification, release evidence and project hygiene:
- [Q1: Make local checks and CI reproducible](Q1.md)
- [Q2: Align APK limits and test ABI selection](Q2.md)
- [Q3: Automate API catalog and build inventory checks](Q3.md)
- [N1: Make private-compositor release evidence reproducible](N1.md)
- [D1: Consolidate plans and establish one progress tracker](D1.md)
- [D2: Enforce artifact policy and remove obsolete project files](D2.md)

### M2 — Reuse & same-ISA native foundation

- [N4: Pin upstream source, licenses, builds and maintenance ownership](N4.md)
- [N2: Select explicit APK ABI and inventory ELF dependency compatibility](N2.md)
- [N3: Expose a trusted runtime preparation and execution interface](N3.md)
- [N5: Replace duplicate resource selection with a libandroidfw adapter](N5.md)
- [N6: Prove same-ISA Android Bionic loading and complete JNI ownership](N6.md)
- [N11: Compare ART Standalone against the existing JVM backend](N11.md)
- [N12: Compare Robolectric Simulator and dex2jar for APK execution](N12.md)

### M3 — Native host architectures & release

- [N7: Deliver native AArch64 host builds, packaging and audited confinement](N7.md)
- [N13: Use apksig verification and atomic signed split-set installation](N13.md)
- [N14: Introduce CPU Skia rendering and consistent Unicode text layout](N14.md)
- [N18: Provide NDK asset ownership and fd/offset access](N18.md)
- [N15: Provide native windows and Mesa graphics adapters](N15.md)
- [N16: Publish exact compatibility and performance evidence by backend](N16.md)

### M4 — Foreign-ISA execution

- [N8: Establish whole-guest QEMU ARM64 application execution on x86_64](N8.md)
- [N9: Compare FEX and Box64 for x86 guest execution on ARM64](N9.md)
- [N10: Time-box Digitalis ARM64-to-x86_64 mixed-ISA feasibility](N10.md)
- [N17: Decide and prove independent x86 and ARMv7 32-bit support](N17.md)

### Capability expansion (on demand, deferred)

- [F1: Harden media for a selected app](F1.md)
- [F2: Integrate a real WebView engine](F2.md)
- [F3: Prove ARM native execution before broad integration](F3.md)
- [F4: Expose honest sensors and location capabilities](F4.md)
- [F5: Expand native, hardware and signature coverage on demand](F5.md)

## Task status meanings

- `pending`: Not started
- `in_progress`: Implementation or verification underway
- `blocked`: Requires a stated prerequisite or external change
- `done`: Acceptance criteria have current recorded evidence and code review is complete, with material findings resolved
- `deferred`: Outside the active milestone unless explicitly promoted

## Current blocker owners

Open the owning blocker sections in [C8](C8.md#blockers-and-remaining-work),
[C6](C6.md#blockers), [N5](N5.md#blockers), [N6](N6.md#blockers),
[N18](N18.md#blockers) and [N1](N1.md). Keep causes and required next actions
there rather than copying mutable blocker conclusions into this index.

## Maintenance

Keep shared architecture/reuse rationale in PLAN.md, APK pins in APPS.md, and mutable task information in exactly one `progress/<ID>.md`. Preserve dated history without treating it as current status. Add or rename a task by updating its milestone group here; do not maintain a separate JSON tracker, status table or duplicate task file. When a task's milestone changes, move its link to the right group. Technical guides outside this folder are reference-only.
