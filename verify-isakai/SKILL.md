---
name: verify-isakai
description: Verify Isakai changes through real app flows, review the implementation, and update the owning progress task before declaring completion.
---

# Verify Isakai by testing the UI

There are no automated test suites. Verification means launching real apps
as real windows, driving the visible task, and recording screenshots, logs
and exit status as evidence in the owning [progress task](../progress/README.md).
The `progress/` folder is the only planning and progress source. Each task's
Markdown file owns its status, scope, acceptance criteria, evidence and code
review; the index links to tasks without copying their statuses. This skill
defines the completion procedure, not a second progress checklist.

## Verification-artifact policy

- Never create test cases, test files, test suites, test blocks or test-only
  code. This also applies to temporary files, inline throwaway test cases,
  regression harnesses and generated test scaffolding. Do not recreate the
  removed automated suites or introduce test dependencies.
- Verify by using the real application, existing necessary app-flow tools,
  existing CLI commands, builds and code review. Record observable results
  and verification limits in the owning task; do not replace missing evidence
  with a newly authored test case.
- Do not create scripts or standalone helpers unless they are absolutely
  necessary to fulfill a concrete project requirement that existing code,
  commands or direct application use cannot meet. Explain that necessity in
  the task file, reuse existing components and keep the implementation small.
  Convenience, one-off inspection or speculative future use is insufficient.
- Prefer existing commands for inspection and cleanup. Remove obsolete
  probes, unused scripts and generated debris after checking their callers;
  preserve required tooling, application data and recorded evidence.

## Completing a task

1. Open the owning `progress/<ID>.md` before starting. Read its acceptance
   criteria, dependencies and blockers, then set its frontmatter `status`
   to `in_progress` when work begins.
2. Implement the task and drive its affected real app flow. Record exact
   commands, observed results, environment, screenshots/logs and remaining
   limitations in that task's **Verification evidence** section. Preserve
   earlier evidence and distinguish same-UID from default-isolated runs.
   Build success alone does not prove application behavior. For documentation
   or tooling tasks, verify the affected links, data or real CLI behavior.
3. Review the final diff and the surrounding call sites before marking the
   task done. Check correctness and regressions, duplicate implementations,
   reuse of existing helpers and upstream components, avoidable work or
   allocations, resource cleanup and relevant performance costs. Keep clear
   module boundaries and small interfaces; remove code made dead by the
   change, unused imports and obsolete compatibility paths. Prefer the
   simplest design that meets the task; avoid speculative abstractions,
   unrelated cleanup and optimization without a demonstrated benefit.
4. Fix material review findings, rerun affected verification when changes
   warrant it, and write the review scope, findings/fixes, validation and
   remaining limitations in the same task's **Code review** section. State
   when no material findings remain; do not invent performance measurements
   or claim an independent review when the review was a self-review.
5. Only after the acceptance criteria pass, blockers are resolved and the
   review is complete, change `status` to `done` and update `updated` in that
   task file. Do this after every task completion, before reporting success
   or moving on. If anything required remains, keep `in_progress` or use
   `blocked` with the reason and next action in the task file; reopen a done
   task when a regression invalidates its acceptance.

Do not copy task statuses, acceptance checklists or evidence into the index,
README or another tracker. Link to the owning task instead. Update the index
only when task links or project navigation change; app pins belong solely
in [APPS.md](../progress/APPS.md), and research history is immutable provenance.

## Build

From the repository root, run `zig build`. Use Zig 0.17 and a JDK 17+
compiler; host system libraries are listed in
[host requirements](../docs/HOST_REQUIREMENTS.md).

## UI flows

| Flow | Command | Proves |
|---|---|---|
| Isolated pinned-app flow | `python3 verify-isakai/sway-flow.py {opencalc\|calculator\|notes} --work <dir>` | OpenCalc calculates/copies/resizes and stays alive; Calculator reaches settings, persists the preference and returns via back; Notes edits a Unicode note, saves it and returns to the list. Retains screenshots and app logs under `--evidence`. |
| Live-compositor drive | `python3 verify-isakai/flow-drive.py <opencalc\|calculator\|notes> [--work DIR]` | Same flows under the live compositor with per-action focus guards. Needs a workspace not shared with interactive work. |
| App observer | `python3 verify-isakai/observe-app.py <0\|1\|2> --work <dir>` | Startup screenshots and failures; does not establish useful interaction. |
| Live window | `python3 verify-isakai/window-flow.py --unprivileged --required` | Real fixture stays open >30 seconds, accepts text, closes through compositor and back, records exit zero and removes data. |
| Full privileged flow | `tools/e2e-privileged.sh --required` | Fresh fixture and peer app install; app-side UID/GID, empty groups, NNP, seccomp, distinct mount namespace and filesystem assertions pass; real surface/input/lifetime/close/back pass; users/data removed. |

Without prerequisites, the privileged script returns 77 with SKIP; `--required`
or `ISAKAI_E2E_REQUIRED=1` turns that into exit 1. Build failures, missing
executables, subprocess failures and assertion failures are errors, never
skips. The privileged entry point rejects `--unprivileged`.

The isolated target-app flow requires the private Sway fixture and
`isakai-vinput` built by the repository's compositor fixture. Start that
fixture before invoking `sway-flow.py`; a missing compositor is an environment
failure, not an app-flow pass or skip.

Inspect `opened-*.png` and `typed-*.png` for the fixture text and typed RUNTIME
suffix, and `isolation.log` for app-side results. Record paths and outcomes in
the owning [task file](../progress/README.md). A same-UID pass must not be reported as a
privileged pass. A window screenshot alone is insufficient: evidence must tie
screenshots to asserted actions and saved results.

## Platform references

- [Linux Landlock UAPI](https://docs.kernel.org/userspace-api/landlock.html): version query, ruleset creation, ABI rights and inheritance.
- [polkit action declarations](https://polkit.pages.freedesktop.org/polkit/polkit.8.html): multiple actions per namespace policy file.
- [Hyprland dispatchers](https://wiki.hypr.land/configuring/core/dispatchers/): version-aware focus and graceful close of the exact driven window.
