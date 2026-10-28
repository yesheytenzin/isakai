# Project reference: plan, pins, dependencies

Concatenated live reference (none frozen). Edit the owning section in
place; keep one fact in one section.

## Delivery plan

# Isakai delivery plan (live)

This is the live plan. Task status, implementation details, acceptance gates
and evidence live only in the linked task files. Start at the
[progress index](README.md); this document owns shared scope and design
rationale. Dated research, pins, licenses, and the execution-contract
reference live frozen in [RESEARCH.md](RESEARCH.md) and are not repeated here.
Limitations live with their owning task, never here.

## Immediate goal

Run three pinned Android utility apps as ordinary, persistent Linux windows: complete useful tasks, retain data across restarts, navigate screens, and run under the default isolation path. Broad Android compatibility is a later goal.

## Long-term goal

Run supported Android APKs for x86_64, x86, arm64-v8a and armeabi-v7a as ordinary processes on x86_64 and ARM64 Linux, using a verified native or translated backend per ABI. No Android OS image, Android system container or kernel Binder dependency. Architecture coverage does not imply every Android application is compatible.

## Delivery milestones

Four milestones carry the immediate goal to the long-term goal, one per release decision below. The [progress index](README.md#roadmap) groups every task under its milestone; this section defines them and their order. Release promotion follows these milestones. Implementation follows each task's explicit dependencies. Bounded runtime comparisons in N11/N12 may run once N3/N4 are met, before more custom runtime investment; an experiment is not release acceptance.

- **M1 — x86_64 bytecode compatibility.** The three pinned apps complete useful flows, persist data, navigate, and run under default per-app isolation on x86_64. Delivering now; the acceptance gate is [C7](archive/C7-acceptance-gate.md#acceptance). This is release decision one.
- **M2 — Reuse and same-ISA native foundation.** Decide and pin upstream reuse, expose a trusted runtime/execution interface, take ownership of ABI selection, resource resolution and Bionic/JNI loading for the host ISA, and run bounded N11/N12 runtime comparisons. This is the base every native backend builds on; nothing in M3/M4 is sound without it.
- **M3 — Native host architectures and release.** Both native host architectures reproduce the pinned flows and the same-ISA corpus, with signed split-set installs, CPU rendering, graphics adapters and a published per-backend evidence matrix. This is release decision two.
- **M4 — Foreign-ISA execution.** A foreign-ISA backend passes a separately named corpus and publishes its overhead and limits; foreign-ISA accelerator and mixed-ISA choices are decided on measured fidelity, not demos. This is release decision three. A mixed-ISA optimization must beat the whole-guest baseline on useful work.

Capability families (media, WebView, sensors, broader native/hardware/signature coverage) are release decision four: they enter only with motivating apps and named maintenance owners, and otherwise stay deferred.

## Working rules

- Work from real app failures, but implement Android and Java contracts rather than APK-private class names, resource IDs or package-prefix rules. Class count, compilation and headless exit zero do not establish application compatibility.
- Shared-contract repairs retain the affected app flow and seek coverage in an independent existing APK or version. Record missing cross-app coverage explicitly; do not infer general support from one successful screen.
- Resource selection belongs to N5, NDK asset ownership and fd/offset access to N18, and native windows/graphics to N15. N18 depends on N5/N6, not rendering or GPU adoption.
- Fix lifecycle, dispatch and isolation before expanding ABI or hardware scope.
- Preserve existing installer, translator, Wayland, storage and bridge work. Prove each behavioral fix through the affected visible UI flow.
- Treat missing capabilities explicitly. Fake coordinates, immediate delayed callbacks and success-shaped no-ops are not acceptable production substitutes.
- Reuse a suitable host backend when it reduces maintenance, after a small feasibility test. Avoid reimplementing an entire browser or text stack.
- Keep changes small enough to verify. Size labels are relative engineering scope (S/M/L/XL), not time estimates or delivery promises.

## Acceptance

The current delivery gate and app-specific acceptance scenarios are maintained in [C7](archive/C7-acceptance-gate.md#acceptance).

## Evidence policy

Follow the [verification-artifact policy](../verify-isakai/SKILL.md#verification-artifact-policy).
Verify the real application with existing necessary tooling and record results
in its owning task file. That policy takes precedence over dated research
suggestions about authoring fixtures or regression cases. Use existing Android
applications and upstream artifacts for compatibility work instead.

## Each refactor needs a motivating failure and a stop gate

Per-task review details live in their owning files. Follow the [verification skill](../verify-isakai/SKILL.md) before completing a task.

The first refactor should expose boundaries, not move files for appearance. `src/launch.zig` should consume an already validated runtime/ABI selection; `uid_helper.zig` should allow only trusted backend entrypoints and backend-specific read paths. Resources keep the public Java facade while a native engine owns handles. Rendering, browser and media each need their own callback/lifetime contract. Guest desktop integration uses IPC. Keep capability failures explicit and avoid falling back to another runtime after partial execution, which can corrupt persistent state or hide the actual failure.

## Release decisions and measurements

Use four release decisions. First, all current pinned tasks pass under default isolation with clean restart and signer/upgrade behavior. Second, both native host architectures reproduce those exact tasks and the same-ISA NDK corpus. Third, a foreign-ISA backend passes a separately named corpus and publishes overhead/limitations. Fourth, new capability families enter only with motivating apps and maintenance owners. Measure cold/warm launch, translation time/cache hit, RSS/PSS, idle CPU, frame-time distribution, input latency, fd/thread counts, child cleanup and installed/download size. Initially publish measurements, then choose realistic budgets.

Product success means retained useful workflows, not source count. Track supported app versions per host/APK ABI, percentage of previously supported flows that still pass, blocker diagnosis turnaround, reproducibility from clean machines, security-update lag and upstream patch burden.

The next architectural decision should be purchased with a small complete experiment: a verified APK, real JNI or resource semantics, a visible useful action and confined shutdown. That evidence will reveal whether reuse truly removes maintenance or merely moves it into a fork. Isakai can become a useful compatibility product by defending that decision boundary at every expansion; no single imported runtime can turn a partially implemented Android API into universal application support.
