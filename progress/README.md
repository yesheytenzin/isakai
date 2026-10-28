# Isakai progress

The `progress/` folder is the single planning and progress source. Each task file owns its status, scope, dependencies, acceptance, evidence and code review. This index is navigation and roadmap only; it does not copy task statuses or completion checklists. Open a task to see its current status.

## Start here

Start with [C8](archive/C8-shared-contracts.md) for the cross-app compatibility audit and repairs,
[C7](archive/C7-acceptance-gate.md) for the pinned-flow acceptance record, and [C6](archive/C6-translator-framework-failures.md) for
translator/framework failures. Resource and native work belongs to [N5](N5-resource-engine.md),
[N6](N6-bionic-jni.md) and [N18](N18-ndk-assets.md). Read each task for its current status and evidence;
historical app passes do not establish support for the current implementation.

- [Shared delivery plan and design rationale](REFERENCE.md)
- [Pinned app manifest](APPS.md)  <!-- machine-read by tools/pinned_apps.py; do not merge elsewhere -->
- [Historical evidence and research](HISTORY.md)
- [Verification and completion procedure](../verify-isakai/SKILL.md)

## Roadmap

Milestones map to the four release decisions in [REFERENCE.md](REFERENCE.md#delivery-plan). A task's fine-grained `phase:` label groups under the milestone shown here. Open a task for its status; the state below is milestone-level orientation only.

| Milestone | Outcome (REFERENCE.md release decision) |
|---|---|
| **M1 — x86_64 bytecode compatibility** | Pinned apps complete useful flows, persist data, and run under default isolation. |
| **M2 — Reuse & same-ISA native foundation** | Trusted runtime execution, resource and Bionic/JNI ownership, and bounded runtime comparisons. |
| **M3 — Native host architectures & release** | Both host architectures, signed split installs, NDK assets, rendering, graphics and per-backend evidence. |
| **M4 — Foreign-ISA execution** | A foreign-ISA backend passes a named corpus; accelerator choices follow measured fidelity. |
| **Capability expansion** | Media, WebView, sensors and hardware enter with motivating apps and maintenance owners. |

## Open work

- [N5: Replace duplicate resource selection with a libandroidfw adapter](N5-resource-engine.md)
- [N6: Prove same-ISA Android Bionic loading and complete JNI ownership](N6-bionic-jni.md)
- [N18: Provide NDK asset ownership and fd/offset access](N18-ndk-assets.md)

## Roadmap

Pending and deferred task definitions live in [ROADMAP.md](ROADMAP.md);
restore a task's file from git history when its phase opens. Deferred
tasks (F1–F5) need explicit promotion.

## Done, archived

Completed tasks live in [archive/](archive/) with their evidence;
per-task state in [STATUS.md](STATUS.md).

## Reference

- [REFERENCE.md](REFERENCE.md) (delivery plan, dependencies)
- [APPS.md](APPS.md) (pinned APKs — machine-read single source)
- [FINDINGS.md](FINDINGS.md) (triage inbox, one owning task per item)
- [HISTORY.md](HISTORY.md) (immutable record)

## Task status meanings

- `pending`: Not started
- `in_progress`: Implementation or verification underway
- `blocked`: Requires a stated prerequisite or external change
- `done`: Acceptance criteria have current recorded evidence and code review is complete, with material findings resolved
- `deferred`: Outside the active milestone unless explicitly promoted

## Current blocker owners

Open the owning blocker sections in [C8](archive/C8-shared-contracts.md#blockers-and-remaining-work),
[C6](archive/C6-translator-framework-failures.md#blockers), [N5](N5-resource-engine.md#blockers), [N6](N6-bionic-jni.md#blockers),
[N18](N18-ndk-assets.md#blockers) and [N1](archive/N1-evidence-reproducibility.md). Keep causes and required next actions
there rather than copying mutable blocker conclusions into this index.

The blockers-and-status view is generated, never hand-edited:
[STATUS.md](STATUS.md) derives Open / Constrained / Done lanes from task
frontmatter via `tools/progress-status.py` (lint fails when stale).

## Maintenance

Keep shared rationale, pins and dependencies in REFERENCE.md; mutable open-task information in exactly one place (`progress/<ID>.md` for N5/N6/N18, `progress/ROADMAP.md` sections for roadmap tasks, `progress/archive/<ID>.md` for done tasks). Preserve dated history without treating it as current status. Add or rename a task by updating its milestone group here; do not maintain a separate JSON tracker, status table or duplicate task file. When a task's milestone changes, move its link to the right group. Technical guides outside this folder are reference-only. Never state a task's status in body prose — frontmatter owns status and STATUS.md renders it. Research outputs land under progress/reports/ (finals, including WORKER completion reports) and progress/research-notes/<title>/ (notes).
