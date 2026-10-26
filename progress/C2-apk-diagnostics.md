---
id: "C2"
status: "done"
phase: "COMPATIBILITY"
priority: "P1"
size: "M"
updated: "2026-10-23"
owner: "unassigned"
next: "none — done; reopen only if a regression invalidates acceptance"
---

# C2: Add APK compatibility diagnostics

## Dependencies

- [C1](C1-target-apps.md)

## Code and documentation

- [tools/inspect-test.zig](../tools/inspect-test.zig)
- [tools/translate-test.zig](../tools/translate-test.zig)
- [src/launch.zig](../src/launch.zig)
- [framework](../framework)

## Acceptance

Each pinned APK yields a machine-readable report (tools/compat-report.py); seeded ABI, missing-method and unsupported-opcode failures identify the relevant class/method or library and a useful next action.

## Verification evidence

### 2026-10-11 — partial

All three pinned APKs have JSON static reports; seeded missing method, ARM-only library and unsupported opcode diagnostics pass. Runtime logs retain original constructor/lifecycle errors. Reachability and unsupported-call coverage remain incomplete.

Artifacts:

- [.cache/isakai/report-tests.log](../.cache/isakai/report-tests.log)
- [.cache/isakai/evidence](../.cache/isakai/evidence)

Command:

```sh
python3 verify-isakai/check-compat-report.py
```

### 2026-10-11 — partial

Runtime report now retains nested causes and separate thread failures, with bounded symbolic stack locations and no ordinary exception payloads. Seeded nested NoSuchMethodError and redaction regression passes. Legacy capability shells are still only partly instrumented.

Artifacts:

- [.cache/isakai/report-tests.log](../.cache/isakai/report-tests.log)

### 2026-10-12 — partial

Regenerated machine-readable static linkage reports for all three pinned APKs using existing translated classes, with the current Notes runtime log. Reports explicitly do not assert fresh translation, reachability, or support; unresolved class/member findings remain numerous. Seeded compatibility diagnostic checks pass.

Artifacts:

- [.cache/isakai/evidence/com.darkempire78.opencalculator.current.json](../.cache/isakai/evidence/com.darkempire78.opencalculator.current.json)
- [.cache/isakai/evidence/com.simplemobiletools.calculator.current.json](../.cache/isakai/evidence/com.simplemobiletools.calculator.current.json)
- [.cache/isakai/evidence/com.simplemobiletools.notes.pro.current.json](../.cache/isakai/evidence/com.simplemobiletools.notes.pro.current.json)
- [.cache/isakai/checks/core/compat-report.log](../.cache/isakai/checks/core/compat-report.log)

Command:

```sh
python3 tools/compat-report.py <pinned.apk> --classes-dir <classes> --output <report>; python3 verify-isakai/check-compat-report.py
```

### 2026-10-14 — partial

Added bounded ELF-header inventory and default-unavailable sensor/location capability descriptions. Five CLI diagnostic APK variants classified matching host, mislabeled ARM, ARM-only, truncated and nested-path libraries. These runs preceded the new installer header gate; post-gate CLI rejection verification remains pending. Headers do not establish dynamic dependency, Bionic or JNI compatibility.

Artifacts:

- [.cache/isakai/evidence/audit-20261014/native-diagnostics.json](../.cache/isakai/evidence/audit-20261014/native-diagnostics.json)


### 2026-10-23 — close-out

Acceptance met and reverified on the current tool. `tools/compat-report.py`
produces a machine-readable JSON report for each of the three pinned APKs:

```sh
python3 tools/compat-report.py .cache/isakai/apks/OpenCalc.v3.2.1.apk          --output /tmp/c2-opencalc.json
python3 tools/compat-report.py .cache/isakai/apks/calculator-fdroid-release.apk --output /tmp/c2-calculator.json
python3 tools/compat-report.py .cache/isakai/apks/notes-fdroid-release.apk      --output /tmp/c2-notes.json
```

All three exit 0 (missing classes/members: OpenCalc 16/449, Calculator 55/741,
Notes 54/733). Each report carries `schema_version`, `apk_sha256`,
`unresolved_classes`, `unresolved_members`, `runtime_failures`, `abi`,
`capabilities` and honest `notice` fields. A missing-member finding names the
owner class, member and descriptor, the calling classes, and a next action, for
example `android/content/Intent.createChooser(...)` with callers
`a/d0`, `com/simplemobiletools/commons/activities/AboutActivity` and
`"Verify reachability in the target flow, then implement or report this API
explicitly."`. The `abi` block reports host arch, bytecode-only status and the
installer selection, with a notice that ELF headers do not establish runtime
compatibility.

## Code review

### 2026-10-23 — close-out (self-review)

Reviewed `tools/compat-report.py`. It reads the APK by sha256-identified path,
runs the translator inventory, and emits unresolved classes/members with
owner/name/descriptor/callers and a next action, plus the ABI and capability
sections. The honesty notices are correct and important: static linkage
findings are flagged as possibly-unreachable and not proof of runtime failure,
and ELF presence is not claimed as ABI compatibility. Output is deterministic
per APK. Self-review, no material findings. Acceptance met; status set to
`done`.
