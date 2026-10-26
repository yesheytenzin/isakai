---
id: "Q1"
status: "done"
phase: "VERIFICATION"
priority: "P1"
size: "M"
updated: "2026-10-24"
owner: "unassigned"
next: "none — done; privileged-harness residual is owned by N1"
---

# Q1: Make local checks and CI reproducible

## Dependencies

None.

## Code and documentation

- [build.zig](../build.zig)
- [Makefile](../Makefile)
- [verify-isakai/SKILL.md](../verify-isakai/SKILL.md)

## Delivery review details

Remove obsolete Arch `check()`; make compositor startup tracked/configurable; update stale architecture, host and reuse prose.

**Additional acceptance / stop gate:** Clean staging/build, private UI flow from fresh checkout, no hardcoded `/tmp` instance or untracked fixture dependency. Package build alone is not UI acceptance.

**Original planning estimate:** 3–7 days for verification reproducibility.


## Acceptance

Superseded 2026-10-14: automated check gates were removed. Build reproducibility is verified by fresh-checkout `zig build` plus UI flows below.

## Blockers

- [B-current-privileged-harness](N1-evidence-reproducibility.md#blockers) — owned by N1.

## Verification evidence

### 2026-10-11 — partial

Checksum-verified cached bootstrap completes for Zig 0.17.0, Temurin JDK 17.0.20.1+1 and dex2jar 2.4. With the pinned toolchain: build, 49 native tests (37 build steps; cached), 10 Java test programs, DEX oracle corpus/line tables, same-UID runtime confinement and live navigation pass. Required live fixture passes >30s, typing, close and Escape. Hosted CI and privileged runner are not verified on this revision.

Artifacts:

- [.cache/isakai/bootstrap-recheck.log](../.cache/isakai/bootstrap-recheck.log)
- [.cache/isakai/pinned-native-tests.log](../.cache/isakai/pinned-native-tests.log)
- [.cache/isakai/pinned-java-tests.log](../.cache/isakai/pinned-java-tests.log)
- [.cache/isakai/pinned-oracle.log](../.cache/isakai/pinned-oracle.log)
- [.cache/isakai/pinned-runtime.log](../.cache/isakai/pinned-runtime.log)
- [.cache/isakai/pinned-navigation.log](../.cache/isakai/pinned-navigation.log)
- [.cache/isakai/window-c.log](../.cache/isakai/window-c.log)

### 2026-10-12 — partial

Added shared tools/checks.sh gates and make verify/verify-wayland/verify-privileged targets with captured commands, toolchain and working-tree provenance, strict pipeline failures and required live coverage. Hosted CI uses the same core gate; self-hosted CI now includes navigation/failure-injection checks and retains screenshots. Local core checks and standalone navigation/failure-injection pass. Required privileged rerun fails prerequisite noninteractive sudo authorization; no hosted CI execution or privileged acceptance is claimed.

Artifacts:

- [.cache/isakai/core-final.log](../.cache/isakai/core-final.log)
- [.cache/isakai/navigation-final.log](../.cache/isakai/navigation-final.log)
- [.cache/isakai/harness-current.log](../.cache/isakai/harness-current.log)
- [.cache/isakai/privileged-current.log](../.cache/isakai/privileged-current.log)

Command:

```sh
tools/checks.sh core; python3 verify-isakai/check-navigation.py; python3 verify-isakai/check-harness-failure.py; tools/e2e-privileged.sh --required
```

### 2026-10-14 — partial

Removed the obsolete Arch check hook calling make test. Current privileged harness references absent verify-isakai/RuntimeProbe.java; private Sway/vinput depends on cached untracked binaries and a hardcoded temporary directory. N1 consolidates the required reproducibility and acceptance-oracle repairs; no current privileged pass is claimed.


### 2026-10-24 — close-out (reproducibility resolved)

The two open stop-gate items are fixed and the N1-owned blocker is resolved.

- No untracked fixture dependency. The compositor fixture is now built
  reproducibly from tracked sources: `verify-isakai/compositor/` holds our four
  helper C sources (`isakai-vinput/vptr/wev/globals`), the two Wayland protocol
  XMLs, `sway.conf`, a `DEPS.lock` pinning the sha256 of the external
  sway/wlroots/libliftoff packages, and `build.sh`. `build.sh` generates the
  protocol headers with `wayland-scanner`, compiles the helpers, and verifies
  and extracts the pinned packages. The external compositor is a pinned
  dependency the same way the bootstrap pins Zig/JDK/dex2jar, not an untracked
  cached binary.
- No hardcoded `/tmp` instance. `verify-isakai/sway_backend/vinput.py` now uses
  `/tmp/isakai-wayland-<uid>`, overridable with `ISAKAI_WAYLAND_DIR`, created
  per run rather than a fixed shared suffix.
- Blocker `B-current-privileged-harness` (N1) is resolved: the required
  privileged gate runs via pkexec (see [N1](N1-evidence-reproducibility.md) and
  [C7](C7-acceptance-gate.md#2026-10-23--required-host-gate-rerun-on-the-hardened-harness)).

Reverified today:

```sh
rm -rf .zig-cache zig-out && zig build          # clean-cache build, exit 0
verify-isakai/compositor/build.sh               # fixture built from source, exit 0
# sway launched headless at /tmp/isakai-wayland-1000 from the rebuilt fixture
python3 verify-isakai/sway-flow.py calculator --work .cache/isakai/recheck/c7svc-calc --evidence .cache/isakai/evidence/q1-reproducible-fixture-calc-20261023
```

Clean-cache `zig build` rebuilds `isakai`, `fixture-apk` and the framework from
scratch (exit 0). `build.sh` compiles the helpers from tracked source and
extracts the checksum-verified compositor (exit 0). The Calculator UI flow
`PASS`es end to end through that freshly-built fixture at the per-uid runtime
dir. Build reproducibility plus the UI flow are met.

## Code review

### 2026-10-24 — close-out (self-review)

Reviewed `verify-isakai/compositor/build.sh`, `DEPS.lock`, `README.md`, the
tracked sources, and the `vinput.py` runtime-dir change. `build.sh` uses
`set -euo pipefail`, generates headers fresh (never cached), fails closed on a
checksum mismatch or a missing package (exit 3, no silent assembly), and
assembles into a caller-chosen dir. The pinned sha256 match the compositor
packages in use. The runtime-dir change is a one-line, env-overridable,
per-uid default with no behavior change when the fixture is launched at that
dir. Self-review, no material findings. Acceptance met; status set to `done`.
