# Testing Isakai

[PLAN.md](PLAN.md) defines acceptance criteria. [progress.json](../progress.json)
is the only record of task status and test evidence. This guide describes how to
run checks; historical milestone labels are not substitutes for current results.

## Pinned dependencies

```sh
python3 tools/bootstrap.py
source .cache/toolchain/env.sh
python3 tools/check-repository.py
```

The local bootstrap verifies the SHA-256 locks in `tools/dependencies.json`.
Host libraries are listed in [HOST_REQUIREMENTS.md](HOST_REQUIREMENTS.md).

Run the same gates as CI with `make verify`. `make verify-wayland` additionally
requires a live Hyprland session and runs navigation, failure injection and the
same-UID window fixture. `make verify-privileged` requires the privileged fixture
instead; missing prerequisites fail that profile rather than silently skipping.
Logs and toolchain/working-tree provenance are retained under
`.cache/isakai/checks/<profile>/`. These fixture gates do not establish the
three real APK acceptance flows.

## Zig baseline

```sh
zig version
zig build test --summary all
```

The test step has 18 roots: CLI, registry, resources, install, DEX, translator,
launch, APK signatures, UID helper, upgrade, confinement, window, XKB, audio,
D-Bus, desktop, bridge and bionic shim. Build-step counts are not individual test counts;
label cached results as cached. Run Java behavior tests with `zig build test-java`; interactive app flows have separate gates.

Tests include offline APK signature vectors, translator lowerings, registry
state transitions, Wayland wire handling and live Landlock/seccomp child probes.
Privileged UID/mount isolation requires the app-side acceptance flow below.

## Compile the Java framework

Use a temporary output directory rather than overwriting installed classes:

```sh
fw_check=$(mktemp -d)
find framework -name '*.java' -print > "$fw_check/sources.txt"
javac --release 17 -d "$fw_check/classes" @"$fw_check/sources.txt"
```

Compilation checks API shape. Run `python3 tools/test-framework.py` for behavioral tests. RUNTIME/COMPATIBILITY changes need relevant assertions for queue
ordering, thread ownership, cancellation, lifecycle and persisted results.
Current behavioral programs include the main-queue long-press path
(`android.view.LongPressTest`), animator ABI used by RecyclerView
(`android.animation.ValueAnimatorApiTest`), sensor/location capability honesty
plus delivery cancellation (`android.hardware.SensorLocationTest`) and host
process supervision (`org.isakai.bridge.HostProcessTest`).

## Differential translator oracle

```sh
./oracle/diff.sh                    # all corpus cases plus line-table checks
./oracle/diff.sh arith arrays       # selected cases plus line-table checks
```

Prerequisites: host JDK (`java`, `javac`, `javap`), unzip, and dex-tools v2.4
providing `d2j-smali.sh`, `d2j-dex2jar.sh` and baksmali. Set `D2J` to its unpacked
directory; the default is `.cache/toolchain/dex_tools/dex-tools-v2.4`.
Missing tools produce a prerequisite error pointing to the bootstrap. Missing prerequisites are not
passing tests. Build the current `zig-out/bin/translate-test` before the oracle.

Each case compares execution output of our translated classes with dex2jar.
`oracle/lines.sh` also compares line information. Corpus parity is useful but
cannot establish compatibility with all DEX instructions or app behavior.

## Development fixture smoke test

```sh
zig build
fixture_work=$(mktemp -d)
zig-out/bin/fixture-apk "$fixture_work/fixture.apk"
export ISAKAI_NO_PRIVILEGE=1 ISAKAI_ALLOW_UNSIGNED=1
export ISAKAI_FRAMEWORK="$PWD/zig-out/framework"
zig-out/bin/isakai --work "$fixture_work/work" install "$fixture_work/fixture.apk"
zig-out/bin/isakai --work "$fixture_work/work" launch org.isakai.fixture
ISAKAI_WINDOW=1 zig-out/bin/isakai --work "$fixture_work/work" launch org.isakai.fixture
zig-out/bin/isakai --work "$fixture_work/work" uninstall org.isakai.fixture
```

Windowed execution requires a Wayland compositor. This is explicitly an
unprivileged development smoke test; it does not test app UID isolation.
Windows remain open until user close/back; test deadlines belong to the harness.
Headless lifecycle completion does not
exercise user interaction, navigation or asynchronous work.

## Privileged acceptance

Run `tools/e2e-privileged.sh --required` on a Hyprland session with noninteractive
sudo, JDK 17+, grim, wtype and ACL tools. It installs fresh fixture and peer APKs,
executes app-side UID/GID, groups, NNP, seccomp, mount-namespace and filesystem
assertions, then drives real windows through input, >30-second lifetime,
compositor close and back. It checks exit status, lifecycle cleanup, registry
state, and removal of users/data. Artifacts remain in its printed temporary path.

Local missing prerequisites return SKIP/77. Required mode (also enabled by
`ISAKAI_E2E_REQUIRED=1`) fails instead. Runtime failures always fail.
The entry point rejects reduced unprivileged mode. This gate exercises the
privileged helper via sudo; interactive polkit dialogs are a separate host
integration check.

## Real pinned-app flows

`python3 verify-isakai/flow-drive.py <opencalc|calculator|notes> [--work DIR]`
drives a pinned APK under the live compositor with per-action guards: the
target window's workspace is made visible, focus is re-verified before every
keystroke/press, and pointer placement is measured (the virtual pointer's
absolute axis is scaled, so a two-point calibration plus an incremental
correction loop replaces a fixed factor). The harness saves per-step
screenshots and asserts outcomes (clipboard text, exit status, window gone).

Two environment limits are worth knowing before reading a failure: the
compositor re-tiles whenever another window appears, so a harness run needs a
workspace (ideally a session) not shared with interactive work; and clipboard
assertions race with any other session client that copies.

See [verify-isakai/SKILL.md](../verify-isakai/SKILL.md) for staged packaging,
same-UID production sandbox checks and a deliberate launch-failure regression.
The staged packaging check tests actual install/uninstall without modifying
system files. Same-UID checks do not establish privileged isolation.

## Real APK acceptance

Pin exact APK identities in `progress.json` before recording results. Use:

```sh
zig-out/bin/inspect-test app.apk
zig-out/bin/verify-test app.apk
zig-out/bin/translate-test --keep-going classes.dex /tmp/isakai-translated-audit
```

Supply an extracted DEX and a fresh output directory for the translator survey;
repeat for every `classesN.dex`. Translation success is not linkage or UI success.

For each pinned app, verify the visible task from the plan, navigation where
required, resize, input, persistence after restart and user-controlled close.
Run with default confinement and retain command, commit, environment, logs and
screenshots. A prior KeePassDroid translation survey is preserved as historical
tracker evidence; it is not a current whole-app acceptance result.

## Compatibility regressions

```sh
python3 verify-isakai/check-apk-limits.py
python3 verify-isakai/check-compat-report.py
python3 verify-isakai/check-navigation.py
python3 tools/fetch-apps.py
python3 verify-isakai/observe-app.py 0
```

The navigation fixture requires Hyprland and checks a real mapped surface,
intent extras, result delivery, lifecycle order and main-thread callbacks.
The app observer records startup screenshots and failures; it does not establish
useful interaction or app support. Compatibility reports distinguish static
linkage findings from the first recorded runtime error:

```sh
python3 tools/compat-report.py app.apk --output report.json --runtime-log launch.log
```

The CI workflow runs compile, Java/native tests, oracle and sandbox/diagnostic
checks. Its separate self-hosted Wayland job uses the required privileged gate;
runner provisioning and hosted CI execution still need verification.
