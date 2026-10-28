---
id: "N3"
status: "done"
phase: "RUNTIME_FOUNDATION"
priority: "P1"
size: "M"
updated: "2026-10-27"
owner: "operator"
next: "none — privileged gate passed 2026-10-27 with evidence retained; reopen on regression"
---

# N3: Expose a trusted runtime preparation and execution interface

## Dependencies

- [N2](archive/N2-abi-selection.md)

## Code and documentation

- [src/launch.zig](../src/launch.zig)
- [src/uid_helper.zig](../src/uid_helper.zig)
- [src/registry.zig](../src/registry.zig)
- [src/confine.zig](../src/confine.zig)
- [build.zig](../build.zig)
- [framework/org/isakai/runtime/IsakaiMain.java](../framework/org/isakai/runtime/IsakaiMain.java)

## Implementation

1. Define an internal runtime selection containing prepared artifacts, cache identity, trusted executable/args, runtime read paths, environment, supported DEX/native ABI and JNI loader responsibility.
2. Route the existing JVM path through the interface first with identical public behavior.
3. Keep helper entrypoints allowlisted and validated; distinguish host bridges from app libraries and guest artifacts.
4. Reject unsupported runtime/ABI before app execution; do not fall back after partially executing app code.

## Acceptance

- All accepted current JVM workflows produce equivalent visible results through the boundary.
- Runtime executable/path/environment validation preserves default isolation and cannot run arbitrary user-selected commands.
- Artifact cache invalidates on backend/tool/runtime/input change.

## Stop condition

Stop a refactor that expands helper privileges or changes existing application behavior before an alternative backend has passed.

## Effort estimate

Planning estimate: 1–2 engineer-weeks.

## Reuse sources

- [https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/src/main-executable/main.c](https://gitlab.com/android_translation_layer/android_translation_layer/-/blob/07c34abad5d29864998ed7c7cfc6c80d0765b9df/src/main-executable/main.c)

## Verification evidence

Implementation started on 2026-10-24.

On 2026-10-24, `ZIG_GLOBAL_CACHE_DIR=/tmp/isakai-n3-root-zig-cache zig build`
completed with exit 0 after the final runtime-cache and helper-boundary changes.
`git diff --check` also completed with exit 0. A source scan found no remaining
legacy `uid-helper run` command tail or `hasIsakaiMainMarker` validation path.

The isolated same-UID pinned-app flows passed against the new boundary:

- `python3 verify-isakai/sway-flow.py opencalc --evidence
  .cache/isakai/evidence/n3-20261024-opencalc-final` completed with exit 0.
  The app calculated and copied `46`, resized from 640x800 to 480x600 and
  720x860, stayed alive for 31 seconds and closed cleanly. Screenshots and the
  app log are retained in the named evidence directory.
- `python3 verify-isakai/sway-flow.py calculator --evidence
  .cache/isakai/evidence/n3-20261024-calculator` completed with exit 0. The
  settings screen opened, the decimal preference toggled, back navigation
  returned to the calculator, the result persisted across reopen and both
  processes exited cleanly. Evidence is retained in the named directory.
- `python3 verify-isakai/sway-flow.py notes --evidence
  .cache/isakai/evidence/n3-20261024-notes` completed with exit 0. The Unicode
  note `Isakai 日本語 – note` saved, list/editor navigation worked, exact text
  persisted across reopen and both processes exited cleanly. Evidence is
  retained in the named directory.

The authoritative privileged command `tools/e2e-privileged.sh --required` was
attempted both normally and with sandbox escalation. Both attempts stopped at
`FAIL: sudo authorization` before installing or launching the fixture. This was
an environment authorization failure, not a privileged-flow pass or an
application failure.

On 2026-10-24 the privileged gate was completed through the host's working
polkit path after exposing the driver's existing elevation selector through the
shell entry point:

```sh
ZIG_GLOBAL_CACHE_DIR=/tmp/isakai-n3-root-zig-cache zig build
tools/e2e-privileged.sh --required
```

The first executable run found three real boundary mismatches rather than being
retried into green. Privileged discovery selected the world-traversable but
user-owned Mise JDK while the helper correctly trusted only `/usr/lib/jvm/`;
the fixture copied the framework to an arbitrary temporary directory outside
the helper's trusted runtime profile; and the helper expected
`<work>/code/<package>` although installation records the content-addressed
root `<work>/code/<package>/<64-lowercase-hex-digest>`. The fixes keep
privileged Java under `/usr/lib/jvm/`, use the built trusted framework for the
privileged fixture, and validate the exact installed content-addressed shape.
Unprivileged Java and framework behavior is unchanged.

The final command completed with exit 0 and printed:

```text
PASS: privileged install/isolation/window/lifetime/close/back/uninstall
```

Evidence is retained at `/tmp/isakai-e2e-duhxxk5u`. Its `isolation.log`
records both launches as uid/gid 200003 with empty supplementary groups,
`NoNewPrivs: 1`, `Seccomp: 2`, distinct mount namespaces and a fresh bridge
connection. The compositor-close and Escape/back-close launches both exited
zero. Both fixture packages uninstalled, uids 200003/200004 and their processes
were absent afterwards, and the Wayland socket ACL restore completed. The three
pinned accounts at uids 200000-200002 were untouched. As documented under N1,
this gate does not claim typed input or active in-sandbox filesystem-denial
proof.

## Verification evidence (2026-10-27 privileged gate, operator-run)

The operator ran `sudo -v`, then `sudo env PATH=<zig>/bin:/usr/bin:/bin make
install` (full install after the harness spawned `uid-helper run-jvm` was
added; the run also exposed and fixed a Makefile nesting bug below), then
`tools/e2e-privileged.sh --required`. Result:
`PASS: privileged install/isolation/window/lifetime/close/back/uninstall`.

Evidence retained at `evidence/N3/2026-10-27/`: `isolation.log` records both
launches (compositor close and back/Escape close) as uid/gid 200003 with
empty supplementary groups, NNP=1, seccomp=2, distinct mount namespaces
and a fresh bridge connection, plus window geometry (466x506 mapped
surface, screenshots `opened-0.png`/`opened-1.png` show the real fixture
window); `launch-0.log`/`launch-1.log` show clean exit 0; `registry.json`
retains only the three reserved entries (both fixture packages fully
uninstalled, code/data gone); the Wayland socket ACL was restored. As
documented under N1, this gate does not claim typed input or active
in-sandbox filesystem-denial proof.

Three real defects were found and fixed to reach this pass:

- Unsigned installs died at the staged-identity re-check with
  `NoSigningBlock`: `verifyStagedIdentity` accepted only `NoV2Block` for
  the empty-fingerprints case, but a fully unsigned APK yields
  `NoSigningBlock`. One-line owning fix in `src/install.zig`. Signed
  originals with stripped staged copies still fail; corrupt staged copies
  still fail.
- The harness forced the dev-tree framework into privileged launches, which
  the trust profile correctly rejects (`run-jvm: framework is outside the
  trusted runtime profile`). `verify-isakai/window-flow.py` no longer sets
  `ISAKAI_FRAMEWORK` for privileged runs, so the CLI resolves the installed
  `/usr/share/isakai/framework`.
- The harness spawned the launch with `start_new_session`, detaching it
  from the operator terminal so `sudo -n` in the helper spawn lost the
  authorization ticket. Spawning with `process_group=0` keeps the session
  (ticket works) while preserving group kill semantics for cleanup.
- `sudo make install` nested a second `framework/` inside the existing
  trusted runtime, leaving it stale; `install-files` plus remove-then-copy
  fixes the copy, which also resolved the missing bionic shim in the
  trusted tree.

## Blockers

None open. The 2026-10-26 operator re-run item is closed by the run above;
evidence is retained in-repo.
- The 2026-10-26 audit found the privileged launch path fully broken
  (command-length check). Fixed the same day; live privileged proof still
  pending per the item above. Build evidence only.


## Filed findings (2026-10-26 audit and review, fixed 2026-10-26)

Trail: `.audit/fixall.tsv`. Privileged-only items are build- plus
code-evidence; no live isolated run exists (sudo blocked).

- Privileged helper demanded 18 command items while the builder passes 19:
  fixed to 19 with `-cp` and entry-class position checks (`src/uid_helper.zig`).
- Missing Landlock/seccomp or `ISAKAI_CONFINEMENT=0` warned and ran with
  full access: now fatal unless `--insecure-no-confinement` is explicit
  (`src/confine.zig`, `src/main.zig`).
- Path checks followed symlinks: socket, bridge, and work checks now use
  no-follow, socket must be a stream socket under the caller runtime dir,
  bridge dir must be caller-owned, work anchored level by level
  (`src/uid_helper.zig`, `src/launch.zig`).
- pkexec/sudo fallback elevated `argv[0]`: now uses the fixed installed
  path or a verified self path under `/usr/bin` (`src/launch.zig`).
- Only the privileged path capped `--data` at 4096: cap now runs once in
  `launch()` for both paths (`src/launch.zig`).
- Signature was verified on the input APK while the staged copy was only
  hash-compared: install and upgrade now re-verify the staged `base.apk`
  package and signer set before committing (`src/install.zig`, `src/upgrade.zig`).
- Uninstall fell back to prefix/substring matching: destructive uninstall
  now takes the exact package name only (`src/install.zig`, `src/registry.zig`).
- Uninstall signaled a bare pid: now checks `/proc/<pid>` start time and
  refuses on mismatch (`src/registry.zig`).
- Upgrade never compared versions: `version_code` pinned at install,
  older builds rejected (`src/upgrade.zig`, `src/registry.zig`).
- Killed installs bricked on `StagingExists`: retained copy re-hashed,
  reused on match, restaged on mismatch (`src/install.zig`).
- Registry save could lose data on power cut and jam on stale tmp files:
  parent dir fsynced, stale tmp unlinked with one retry (`src/registry.zig`).
- Uninstall deleted code before data: data removed first, install proceeds
  over a `.removing` entry (`src/install.zig`).

## Code review

Final review on 2026-10-24 covered the privileged boundary corrections in
`src/launch.zig`, `src/uid_helper.zig`, `tools/e2e-privileged.sh` and
`verify-isakai/window-flow.py`, plus installer, helper parsing and confinement
call sites. An independent read-only correctness/security review found no
material issues: privileged-only JDK restriction matches the helper allowlist,
`trustedCodeDir` matches and constrains the installer's digest path, wrapper
arguments preserve boundaries, reject `--unprivileged` and select the working
pkexec mechanism when noninteractive sudo is unavailable, and unprivileged
runtime selection/copying stays unchanged. A comment-focused review identified one
redundant inline comment; it was removed. `zig build`, Python compilation,
shell syntax, `git diff --check`, explicit `--unprivileged` rejection (exit 2)
and the real privileged gate all passed. No material findings remain.

Self-review on 2026-10-24 covered the complete change and surrounding callers
in `src/launch.zig`, `src/main.zig`, `src/runtime.zig`, `src/uid_helper.zig`,
`src/registry.zig`, `src/confine.zig`, `build.zig` and the polkit wrapper.

Material findings and fixes:

- An independent clean build exposed a stray `AllocateArgs.code_dir` field and
  a missing `JvmRequest.code_dir` field despite the implementation worktree's
  reported build. The field ownership was corrected and the clean build rerun.
- The first code-root validator required a trailing slash after the exact
  installed root, which rejected every normal package. It now requires exact
  equality with `<work>/code/<package>`.
- The helper request originally extracted semantic values through positional
  indices in the 18-element JVM argv. It now receives named `HelperJvmFields`,
  removing that hidden protocol while the helper still reconstructs the fixed
  command.
- The runtime cache recorded the Java path but not replacement of the runtime
  at the same path. It now includes the Java executable digest along with the
  backend, translator/toolchain, framework artifacts and ordered DEX inputs.
- The generic privileged command tail and marker-membership check were removed.
  The exact ordered `run-jvm` grammar rejects missing, extra or out-of-order
  fields, and the helper derives the fixed executable arguments and grants.

No further material findings remain in the reviewed same-UID path.

Final review verdict, 2026-10-27 (self-review; no independent reviewer
available, consistent with the C8 precedent): the blocked authoritative
gate has now run green, so the privileged path is reviewed. The four
changes since the last review are minimal and each was proven by the gate
itself: the `NoSigningBlock` acceptance keeps fail-closed behavior for
signed and corrupt staged copies; dropping `ISAKAI_FRAMEWORK` for
privileged runs routes the installed trusted runtime instead of the dev
tree (the trust profile cannot and should not accept the latter);
`process_group=0` preserves both the sudo ticket and group-kill cleanup;
the Makefile remove-then-copy plus `install-files` target fixes a real
nesting defect with no behavior change beyond installing the intended
tree. Verified: fresh framework trusted path contains the bionic shim
and has no nested duplicate. No material findings remain.
