# Isakai progress

The `progress/` folder is the single planning and progress source. Each task file owns its status, scope, dependencies, acceptance, evidence and code review. This index contains navigation only; do not copy task statuses or completion checklists here.

## Start here

**Current phase:** `COMPATIBILITY`

**Next task:** [C7](C7.md)

**Immediate objective:** Three pinned utility apps complete useful visible flows, preserve data and run under default isolation.

- [Shared delivery plan and reuse decisions](PLAN.md)
- [Pinned app manifest](APPS.md)
- [Historical evidence and research](HISTORY.md)
- [Verification and completion procedure](../verify-isakai/SKILL.md)

## Task status meanings

- `pending`: Not started
- `in_progress`: Implementation or verification underway
- `blocked`: Requires a stated prerequisite or external change
- `done`: Acceptance criteria have current recorded evidence and code review is complete, with material findings resolved
- `deferred`: Outside the active three-app gate unless explicitly promoted

## Tasks

Open the task to see its current status. Update that file after every completion; the verification skill requires a review of the final changes before `done`. Previously recorded statuses and evidence were migrated as historical claims, not reverified by this conversion.

- [R1: Remove the production window timeout](R1.md)
- [R2: Repair and prove confinement](R2.md)
- [R3: Verify policy packaging and clean installation](R3.md)
- [R4: Make privileged E2E failures authoritative](R4.md)
- [C1: Pin three target apps and useful flows](C1.md)
- [C2: Add APK compatibility diagnostics](C2.md)
- [C3: Implement the main-thread message queue](C3.md)
- [C4: Support in-app activity navigation](C4.md)
- [C5: Make target layouts and text usable](C5.md)
- [C6: Close measured translator and framework failures](C6.md)
- [C7: Pass the three-app acceptance gate](C7.md)
- [Q1: Make local checks and CI reproducible](Q1.md)
- [Q2: Align APK limits and test ABI selection](Q2.md)
- [Q3: Automate API catalog and build inventory checks](Q3.md)
- [F1: Harden media for a selected app](F1.md)
- [F2: Integrate a real WebView engine](F2.md)
- [F3: Prove ARM native execution before broad integration](F3.md)
- [F4: Expose honest sensors and location capabilities](F4.md)
- [F5: Expand native, hardware and signature coverage on demand](F5.md)
- [D1: Consolidate plans and establish one progress tracker](D1.md)
- [D2: Enforce artifact policy and remove obsolete project files](D2.md)
- [N1: Make private-compositor release evidence reproducible](N1.md)
- [N2: Select explicit APK ABI and inventory ELF dependency compatibility](N2.md)
- [N3: Expose a trusted runtime preparation and execution interface](N3.md)
- [N4: Pin upstream source licenses builds and maintenance ownership](N4.md)
- [N5: Replace duplicate resource selection with a libandroidfw adapter](N5.md)
- [N6: Prove same-ISA Android Bionic loading and complete JNI ownership](N6.md)
- [N7: Deliver native AArch64 host builds packaging and audited confinement](N7.md)
- [N8: Establish whole-guest QEMU ARM64 application execution on x86_64](N8.md)
- [N9: Compare FEX and Box64 for x86 guest execution on ARM64](N9.md)
- [N10: Time-box Digitalis ARM64-to-x86_64 mixed-ISA feasibility](N10.md)
- [N11: Compare ART Standalone against the existing JVM backend](N11.md)
- [N12: Compare Robolectric Simulator and dex2jar for APK execution](N12.md)
- [N13: Use apksig verification and atomic signed split-set installation](N13.md)
- [N14: Introduce CPU Skia rendering and consistent Unicode text layout](N14.md)
- [N15: Provide stable NDK assets native windows and Mesa graphics adapters](N15.md)
- [N16: Publish exact compatibility and performance evidence by backend](N16.md)
- [N17: Decide and prove independent x86 and ARMv7 32-bit support](N17.md)

## Current blocker owners

- [Compatibility and retained-state blockers](C6.md#blockers)
- [Privileged harness blocker](N1.md#blockers)

## Maintenance

Keep shared architecture/reuse rationale in PLAN.md, APK pins in APPS.md, and mutable task information in exactly one `progress/<ID>.md`. Preserve dated history without treating it as current status. Add or rename a task by updating its link here; do not maintain a separate JSON tracker, status table or duplicate task file. Technical guides outside this folder are reference-only.
