# Task status (generated — do not hand-edit)

Derived from task frontmatter by `tools/progress-status.py`.
Edit the owning task file, rerun the script, commit both.

## Open — in progress or blocked

- [N5](N5-resource-engine.md): review the C8 AssetFileDescriptor repair and record actual caller evidence or the remaining verification limit
- [N6](N6-bionic-jni.md): verify default-isolated useful actions plus thread/callback/GC/exception behavior and resolve the loader-contract follow-ups

## Constrained — pending or deferred

- [F1](F1-media-hardening.md): no action while deferred; revisit only if the task is un-deferred
- [F2](F2-webview-engine.md): no action while deferred; revisit only if the task is un-deferred
- [F3](F3-arm-native-proof.md): no action while deferred; revisit only if the task is un-deferred
- [F4](F4-sensors-location.md): no action while deferred; revisit only if the task is un-deferred
- [F5](F5-native-hardware-coverage.md): no action while deferred; revisit only if the task is un-deferred
- [N10](N10-digitalis-feasibility.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N11](N11-art-compare.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N12](N12-robolectric-compare.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N13](N13-signed-splits.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N14](N14-skia-text.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N15](N15-native-windows.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N16](N16-compatibility-evidence.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N17](N17-32bit-support.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N18](N18-ndk-assets.md): find a suitable existing NDK asset caller and await N6's verified loader contract; do not author a probe app
- [N7](N7-aarch64-release.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N8](N8-qemu-arm64-guest.md): no action until scheduled; start per acceptance criteria when the phase opens
- [N9](N9-fex-box64-compare.md): no action until scheduled; start per acceptance criteria when the phase opens

## Done

- [C1](C1-target-apps.md): none — done; reopen only if a regression invalidates acceptance
- [C2](C2-apk-diagnostics.md): none — done; reopen only if a regression invalidates acceptance
- [C3](C3-main-thread-queue.md): none — done; reopen only if a regression invalidates acceptance
- [C4](C4-activity-navigation.md): none — done; reopen only if a regression invalidates acceptance
- [C5](C5-layouts-text.md): none — done; reopen only if a regression invalidates acceptance
- [C6](C6-translator-framework-failures.md): none — N3 privileged gate passed 2026-10-27 with retained evidence; default-isolation follow-ups belong to C7; reopen on regression
- [C7](C7-acceptance-gate.md): optional: privileged per-app pinned runs (same-UID flows green on main); XKB/independent-review owned elsewhere
- [C8](C8-shared-contracts.md): record affected app-flow evidence for the source replacement; blank Compose settings rendering remains a known gap
- [D1](D1-progress-tracker.md): none — done; reopen only if a regression invalidates acceptance
- [D2](D2-artifact-policy.md): none — done; reopen only if a regression invalidates acceptance
- [N1](N1-evidence-reproducibility.md): provision an in-sandbox observation path for write-denial and cross-app read-denial; acceptance is met without it
- [N2](N2-abi-selection.md): none — done; pin standalone malformed/wrong-machine APKs only if a diagnostic coverage gap is demonstrated
- [N3](N3-trusted-runtime.md): none — privileged gate passed 2026-10-27 with evidence retained; reopen on regression
- [N4](N4-upstream-pins.md): none — done; reopen only if a regression invalidates acceptance
- [Q1](Q1-ci-reproducibility.md): none — done; privileged-harness residual is owned by N1
- [Q2](Q2-apk-limits.md): none — done; reopen only if a regression invalidates acceptance
- [Q3](Q3-api-catalog.md): none — done; reopen only if a regression invalidates acceptance
- [R1](R1-window-timeout.md): none — done; reopen only if a regression invalidates acceptance
- [R2](R2-confinement-proof.md): none — done; reopen only if a regression invalidates acceptance
- [R3](R3-packaging-install.md): none — done; reopen only if a regression invalidates acceptance
- [R4](R4-privileged-failures.md): none — done; privileged-harness residual is owned by N1

