---
id: "D1"
status: "done"
phase: "DOCUMENTATION"
priority: "P1"
size: "S"
updated: "2026-10-14"
owner: "unassigned"
next: "none — done; reopen only if a regression invalidates acceptance"
---

# D1: Consolidate plans and establish one progress tracker

## Dependencies

None.

## Code and documentation

- [progress/D1.md](../progress/D1-progress-tracker.md)
- [README.md](../README.md)
- [docs](../docs)
- [progress/README.md](../progress/README.md)
- [tools/pinned_apps.py](../tools/pinned_apps.py)
- [verify-isakai/SKILL.md](../verify-isakai/SKILL.md)

## Acceptance

The progress/ folder is the only planning and progress source. Each task has one Markdown file owning its status, implementation, acceptance, verification evidence and code review. The index contains links without copied statuses; app pins have one source; all readers and technical guides use the new layout. The verification skill requires review and a task-file update before every future completion.

## Verification evidence

### 2026-10-11 — passed

Consolidated four plans into docs/PLAN.md; replaced the separate host-interface checklist with the architecture reference index; corrected reference guides. Validated 20 unique task IDs, valid statuses, acyclic dependencies, existing code paths, plan/tracker alignment, local Markdown links, absent superseded references and git diff --check. Documentation and two comment references only; no runtime behavior changed.

### 2026-10-14 — passed

Folded docs/PLAN.md scope and per-task acceptance criteria into progress.json; deleted docs/PLAN.md and docs/TESTING.md. Removed all automated suites (Zig test blocks, Java *Test.java, oracle corpus, apksig vectors, check-*.py harnesses, checks.sh, test CI); verification is UI-only per verification_policy. References updated in README, docs, build.zig, Makefile and verify-isakai/SKILL.md.

### 2026-10-14 — passed

Consolidated the delivery plan, 17 roadmap tasks and reuse research into progress.json; removed the standalone report, roadmap JSON, three research notes and reuse assessment. Preserved five original Markdown sources as hash-verified immutable snapshots and all prior task/app evidence. README and reference guides point here. Validated 37 unique task IDs, known statuses, existing code paths, acyclic dependencies, 17 new pending tasks and 514 active local links. Removed three stale active references to deleted oracle/testing files. Runtime code was not changed by this consolidation.

Artifacts:

- [former tracker](HISTORY.md#source-provenance)
- [README.md](../README.md)


### 2026-10-14 — passed

Replaced the JSON tracker with 37 task Markdown files and four shared
references in progress/. Every task owns its frontmatter status, acceptance,
dependencies, evidence and review; the index links to tasks without copied
statuses. Shared plan, APK pins and historical research each have one owner.
All prior task evidence, app evidence, pins and five research snapshots were
preserved. App evidence is maintained in C7.md; blockers have one owning task
and other tasks link to it. The retired progress.json file was removed.

Validation passed for 37 unique tasks and their metadata/dependencies,
acyclic dependency graph, preserved evidence and 971 navigable local links.
All three pinned APK hashes passed the real fetch CLI after JSON removal,
including execution from /tmp. The sway flow, live flow and observer help
entry points load successfully; all eight changed Python files compile.
The manifest reader rejects missing/multiple blocks, empty manifests,
duplicate packages, invalid digests and missing required fields. The skill
validator and git diff --check pass.

Commands:

```sh
python3 tools/fetch-apps.py
python3 verify-isakai/sway-flow.py --help
python3 verify-isakai/flow-drive.py --help
python3 verify-isakai/observe-app.py --help
python3 -m py_compile tools/pinned_apps.py tools/fetch-apps.py verify-isakai/sway-flow.py verify-isakai/flow-drive.py verify-isakai/observe-app.py verify-isakai/sway_backend/probe_calc.py verify-isakai/sway_backend/probe_calc_menu.py verify-isakai/sway_backend/probe_equals.py
git diff --check
```

No Android compatibility or full UI acceptance rerun is claimed by this
documentation/tooling migration. Existing historical completion labels were
preserved; future completions require the updated verification procedure.

## Code review

### 2026-10-14 — self-review complete

Reviewed the Markdown migration, verification skill, shared manifest reader,
all seven consumers, navigation links and retired JSON references.

- Confirmed each task status has one owner and the index does not duplicate
  statuses or completion checklists. Detailed per-task review gates were
  moved from the shared plan to their owning task files.
- Consolidated APK-pin parsing in one small standard-library helper;
  consumers do not maintain another manifest or parser. All three original
  package/version/hash records remain byte-for-byte equivalent.
- Removed the obsolete JSON tracker and imports made unused in three probes.
  Replaced the probes' hardcoded checkout root with paths derived from their
  source locations, and verified CLI use outside the repository directory.
- Checked parse failures remain explicit and prevent silent fallback to
  stale data. Invalid-record and duplicate-package checks pass.
- Kept the implementation modular and limited to one manifest read per
  consumer invocation. Added no package dependency, caching layer or generic
  tracking framework; no performance benchmark is claimed.
- Confirmed task evidence and immutable research were preserved and scoped
  verification results do not claim Android runtime compatibility.

No material findings remain for this migration. Unresolved application,
native-runtime and privileged-harness issues remain in their owning tasks.
