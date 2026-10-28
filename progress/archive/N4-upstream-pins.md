---
id: "N4"
status: "done"
phase: "REUSE_FOUNDATION"
priority: "P1"
size: "S"
updated: "2026-10-24"
owner: "unassigned"
next: "none — done; reopen only if a regression invalidates acceptance"
---

# N4: Pin upstream source licenses builds and maintenance ownership

## Dependencies

None.

## Code and documentation

- [progress/DEPENDENCIES.md](REFERENCE.md) — the ledger this task delivers
- [tools/dependencies.json](../tools/dependencies.json) — toolchain hash lock
- [verify-isakai/compositor/DEPS.lock](../verify-isakai/compositor/DEPS.lock) — fixture hash lock
- [progress/APPS.md](REFERENCE.md) — pinned APK hashes
- [tools/bootstrap.py](../tools/bootstrap.py)
- [LICENSE](../LICENSE)
- [LICENSES/Apache-2.0.txt](../LICENSES/Apache-2.0.txt)

## Implementation

1. Create a source/dependency ledger with immutable URL/revision, source/license hashes, target ABI, build flags, transitive components, patches, owner and update policy.
2. Use isolated local-prefix recipes for ATL/ART/Bionic experiments; capture full dependency graph and failures.
3. Audit the CMake fork only as build-order reference, removing hardcoded amd64/lib64 assumptions and masked failures if reused.
4. Record unavailable license/dependency evidence explicitly; do not vendor or distribute proprietary translation blobs without permission.

## Acceptance

- A reviewer can reproduce all experiment inputs without mutable branch-head assumptions.
- Every shipped/imported file and dependency has identified terms/notices and source/build obligations.
- No component is labeled built or integrated from source inspection alone.

## Stop condition

Unknown redistribution terms, unpinned source or silently ignored build failures block distribution, while other independent experiments may continue.

## Effort estimate

Planning estimate: 2–5 engineer-days initial ledger; recurring security and patch maintenance.

## Reuse sources

- [https://source.android.com/docs/setup/contribute/licenses](https://source.android.com/docs/setup/contribute/licenses)
- [https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/doc/Build.md](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/doc/Build.md)
- [https://github.com/killerdevildog/android_translation_layer/blob/4ed75fbf108c5593a65b6ee1295d5445c935aaf4/CMakeLists.txt](https://github.com/killerdevildog/android_translation_layer/blob/4ed75fbf108c5593a65b6ee1295d5445c935aaf4/CMakeLists.txt)

## Verification evidence

### 2026-10-24 — ledger delivered

[progress/DEPENDENCIES.md](REFERENCE.md) is the source/dependency/reuse
ledger. Acceptance checks:

- **No mutable branch-head assumptions.** Every entry is an immutable pin: Zig
  and JDK by sha256 in `tools/dependencies.json`; sway/wlroots/libliftoff by
  sha256 in `verify-isakai/compositor/DEPS.lock`; the three APKs by sha256 in
  `APPS.md`; the reuse research candidates (ATL, ART Standalone, Bionic
  Translation, Robolectric, the CMake wrapper) by exact commit/tag from
  PLAN.md. The bootstrap and `build.sh` fail closed on a checksum mismatch.
- **Terms/notices and build obligations identified.** Each built and
  test-input component carries its license (Zig MIT, JDK GPLv2+Classpath,
  sway/wlroots/libliftoff MIT, the three apps GPL-3.0) and its status; this
  project is GPL-3.0-or-later. Host-provided system libraries are listed
  separately as operator-installed, not vendored. Licenses not yet confirmed
  against the upstream LICENSE file are flagged explicitly in the Gaps section
  rather than asserted.
- **Nothing labeled built on inspection alone.** The ledger separates **built**
  (toolchain, fixture), **test-input** (APKs), **inspected-only** (the reuse
  candidates, E3) and **host-provided**. Verifying the tree corrected one
  misclassification found while writing the ledger: an earlier Q1 note implied
  dex2jar was bootstrapped, but the current tree ships a first-party DEX→JVM
  translator (`src/translate.zig`) and neither fetches nor builds dex2jar, so
  it is ledgered inspected-only (parity reference / N12 candidate), not built.

Commands used to confirm the pins exist: read of `tools/dependencies.json`,
`verify-isakai/compositor/DEPS.lock`, `APPS.md` and the PLAN.md revisions;
`grep` of the source tree for dex2jar usage.

## Code review

### 2026-10-24 — close-out (self-review)

The deliverable is documentation (the ledger) plus the dependency links in this
task; no build or runtime code changed. Reviewed the ledger for accuracy
against the tree: every pin resolves to an existing lock entry or a PLAN.md
revision, the integration-status column matches reality (the dex2jar
correction above is the one it caught), and uncertain upstream licenses are
flagged as to-confirm rather than asserted — satisfying the stop condition that
unknown terms or unpinned source block distribution. Self-review, no material
findings. Acceptance met; status set to `done`.
