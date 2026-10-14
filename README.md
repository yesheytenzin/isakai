# Isakai

Run Android apps as native Linux processes. No Android OS, no container, no
kernel binder, no runtime bundle download: host JVM + translated DEX +
reimplemented `android.*` on Wayland.

## Project status

Isakai is a compatibility prototype. Installation, DEX translation, Wayland
rendering, storage and desktop bridges exist, but real-app readiness still
requires dispatch, navigation and app-specific compatibility work. Windows now
remain open until closed; WebView has no rendering engine.

The next delivery target is **three pinned utility apps completing useful
visible tasks under default isolation**. The [progress folder](progress/README.md)
is the single planning and progress source. Each `progress/<ID>.md` owns its
task status, acceptance criteria, blockers, verification evidence and code
review. The index links to tasks without copying statuses. Technical guides
describe implementation and usage. Verification is UI-only: launch pinned
APKs as real windows, drive the visible task, and record screenshots, logs
and exit status in the owning task file. Historical M0–M6 reports and reuse
research are preserved in [HISTORY.md](progress/HISTORY.md).

## Build and install (development preview)

Host requirements: Linux x86_64, Zig 0.17 + a C toolchain, JDK 17+
(`javac` to build, `java` to run), and the JNI system libraries
`libxkbcommon`, `pulseaudio` (`libpulse-simple`), `sqlite3`, ICU development
libraries for SQLite collation — plus
`polkit`/`pkexec` for privileged app isolation. Full detail:
`docs/HOST_REQUIREMENTS.md`.

```sh
git clone https://github.com/yesheytenzin/isakai && cd isakai
make build            # ReleaseSafe binaries + framework + policy (zig-out/)
sudo make install     # /usr/bin/isakai, /usr/bin/isakai-dbus-bridge,
                      # /usr/share/isakai/framework, polkit policy
isakai install app.apk
isakai launch com.example.app
```

Notes:

- The polkit policy (3 actions `id.isakai.{allocate-uid,remove-uid,run-app}`, `auth_admin`) pins each `exec.path` to its wrapper (`/usr/bin/isakai-*-uid` → `isakai uid-helper {allocate,remove,run}`), with per-action messages and no `$(command_line)` leak: install with the default
  `PREFIX=/usr`. A relocatable dev install is possible with
  `make install PREFIX=... INSTALL_POLICY=0`, but privileged
  install/launch then needs `ISAKAI_FRAMEWORK=<prefix>/share/isakai/framework`
  and `ISAKAI_NO_PRIVILEGE=1` (same env vars as the Quickstart below).
  Launch falls back to `/usr/share/isakai/framework` when neither
  `ISAKAI_FRAMEWORK` nor `<work>/../framework` exists.
- Windowed launches need a Wayland compositor (`WAYLAND_DISPLAY` +
  `XDG_RUNTIME_DIR`); headless needs none.
- Uninstall the packaging: `sudo make uninstall`. Remove an app and its
  data: `isakai uninstall <package>`.
- Distro packages: `packaging/arch/PKGBUILD` (`isakai-git`) and
  `packaging/debian/` (`control` + `rules`, debhelper compat 13) both
  install the same FHS layout as `make install`.

## Quickstart (dev)

```sh
zig build                                    # CLI + JNI lib + framework classes
zig-out/bin/fixture-apk /tmp/fixture.apk    # build the SDK-free fixture APK
export ISAKAI_NO_PRIVILEGE=1 ISAKAI_ALLOW_UNSIGNED=1
export ISAKAI_FRAMEWORK=$PWD/zig-out/framework
zig-out/bin/isakai install /tmp/fixture.apk
zig-out/bin/isakai launch org.isakai.fixture            # headless
ISAKAI_WINDOW=1 zig-out/bin/isakai launch org.isakai.fixture  # windowed
```

`zig build` installs to `zig-out/` (`-p <prefix>` overrides): `bin/isakai`,
`bin/fixture-apk`, `bin/{inspect,translate,verify}-test`, `framework/*.class`,
`framework/lib/{libisakai_jni,libisakai_bionic_shim}.so`, `share/polkit-1/actions/*.policy`, `bin/isakai-*-uid` wrappers.

## Usage

```
isakai [--work DIR] [-v|--verbose] [-q|--quiet] <verb> [args]
  install <apk>        verify, stage and register an app
  uninstall <package>  terminate (if running), remove user + data + entry
  launch <package>     translate (if stale), start the app, record exit
  query <package>      show one app's state
  list                 list installed apps
  uid-helper ...       privileged helper (via pkexec; not run directly)
```

Exit codes: 0 ok, 1 operational failure (not installed, already running,
verify failed), 2 usage error. Global `--work` overrides `ISAKAI_WORK`.

## Environment

| Variable | Default | Meaning |
|---|---|---|
| `ISAKAI_WORK` | `~/.local/share/isakai` | registry + staged code + app data |
| `ISAKAI_FRAMEWORK` | `<work>/../framework`, then `/usr/share/isakai/framework` | compiled `android.*` + `libisakai_jni.so` (dev: `zig-out/framework`) |
| `ISAKAI_WINDOW` | unset | `1` = windowed launch, else headless |
| `ISAKAI_NO_PRIVILEGE` | unset | `1` = run as self, skip pkexec (dev/test) |
| `ISAKAI_NONINTERACTIVE` | unset | `1` = `sudo -n` instead of pkexec prompts |
| `ISAKAI_ALLOW_UNSIGNED` | unset | `1` = install unsigned APKs (dev fixtures only) |
| `ISAKAI_ZEROCOPY` | `1` | `0` = force the pixel-copy post path (debug) |
| `JAVA_HOME` | unset | first JVM candidate (`$JAVA_HOME/bin/java`) |

## Tools

- `fixture-apk <out.apk>` — SDK-free fixture APK (binary manifest +
  hand-assembled DEX driving the current milestone's UI).
- `inspect-test <apk>` — print manifest/DEX summary or the rejection reason.
- `translate-test [--dump-lines] [--keep-going] <dex> <outdir>` — batch
  DEX→JVM translation; `--keep-going` reports per-class failures.
- `verify-test <apk>` — print all v2 fingerprints (sorted, per-signer) or the `SigError` (P-521/RSA>4096/digest mismatch).
- `tools/compat-report.py <apk> --output <report.json>` — machine-readable ABI/linkage gap report, including bounded ELF-header checks. Installation rejects invalid or mismatched x86_64 headers; this does not prove Bionic/JNI compatibility.
- `tools/e2e-privileged.sh --required` — app-side isolation and live window acceptance; missing prerequisites and failed launches fail the required gate. See [verification instructions](verify-isakai/SKILL.md).
- `verify-isakai/sway-flow.py`, `flow-drive.py`, `observe-app.py` — drive pinned APKs through visible UI flows on a compositor and retain screenshots/logs.

## Layout

- `src/main.zig` — CLI dispatch
- `src/install.zig` — APK inspect/verify/stage/install/uninstall (x86_64 .so allow, other ABIs NativeUnsupported)
- `src/apksig.zig` — APK Signature Scheme v2 verifier (multi-signer ≤16, P-521 unsupported)
- `src/registry.zig` — package registry, state machine, terminatePid
- `src/dex.zig` — DEX parser
- `src/translate.zig` — DEX→JVM translator + class writer
- `src/launch.zig` — install-time translate, JVM discovery, spawn/reap
- `src/uid_helper.zig` — privileged allocate/remove/runAs
- `src/native/bionic_shim.zig` — LD_PRELOAD bionic interposition (log/property/dlopen)
- `src/bridge/` — `window.zig` (Wayland client), `jni.zig` (natives),
  `xkb.zig` (keymap/keycodes), `audio.zig` (PulseAudio), `desktop.zig`/`dbus.zig`
- `framework/` — Java `android.*` (media/WebView/hardware/location) + `org.isakai` bridge/runtime
- `tools/` — fixture builder, APK debug probes, compat-report, UI flow drivers
- `verify-isakai/` — UI verification drivers (isolated sway flows, observer) + instructions
- `polkit/` — privilege policy source (staged by the build)
- `docs/` — architecture, host requirements, supported APIs
- `progress/` — shared plan, pinned apps, research history and one Markdown file per task

## Development

There are no automated test suites; verification is UI-only. Build with
`zig build`, install a pinned APK, launch it as a real window, drive the
visible task from the [progress index](progress/README.md), and record screenshots,
logs and exit status in that task's Markdown file. Review the final changes
and update the task status before reporting completion, as required by the
[verification skill](verify-isakai/SKILL.md).
Its [verification-artifact policy](verify-isakai/SKILL.md#verification-artifact-policy)
also governs what development files may be created.

- [Project plan, research and progress](progress/README.md): the single entry
  point; task files own statuses, acceptance gates, evidence and reviews.
- [Architecture](docs/ARCHITECTURE.md): system map and host interface index.
- [Host requirements](docs/HOST_REQUIREMENTS.md): dependencies and environment.
- [API catalog](docs/SUPPORTED_APIS.md): available classes and behavioral limits.
- [Verification instructions](verify-isakai/SKILL.md): UI drivers and evidence rules.

## License

GPL-3.0-or-later.

AOSP-derived default color values retain Apache-2.0 attribution in
[SystemColors.java](framework/android/content/res/SystemColors.java); see
[the Apache license](LICENSES/Apache-2.0.txt).
