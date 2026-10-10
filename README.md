# Isakai (Zig rewrite)

Run Android apps as native Linux processes. No Android OS, no container, no
kernel binder, no runtime bundle download: host JVM + translated DEX +
reimplemented `android.*` on Wayland.

## Status: M5 in progress

`progress.json` is the milestone tracker. M0 (install pipeline), M1 (DEX
translate + headless run), M2 (Wayland window), M3 (keyboard/pointer input),
M4 (app services) are complete and gated on this machine. M5 (release:
packaging, security, upgrades) is current.

## Install (clean machine)

Host requirements: Linux x86_64, Zig 0.17 + a C toolchain, JDK 17+
(`javac` to build, `java` to run), and the JNI system libraries
`libxkbcommon`, `pulseaudio` (`libpulse-simple`), `sqlite3` — plus
`polkit`/`pkexec` for privileged app isolation. Full detail:
`docs/HOST_REQUIREMENTS.md`.

```sh
git clone https://github.com/yesheytenzin/isakai && cd isakai
make build            # ReleaseSafe binaries + framework + policy (zig-out/)
make test             # unit tests
sudo make install     # /usr/bin/isakai, /usr/bin/isakai-dbus-bridge,
                      # /usr/share/isakai/framework, polkit policy
isakai install app.apk
isakai launch com.example.app
```

Notes:

- The polkit policy (`id.isakai.allocate-uid`, `auth_admin`) pins
  `exec.path` to `/usr/bin/isakai`: install with the default
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
./oracle/diff.sh                             # differential translator oracle
```

`zig build` installs to `zig-out/` (`-p <prefix>` overrides): `bin/isakai`,
`bin/fixture-apk`, `bin/{inspect,translate,verify}-test`, `framework/*.class`,
`framework/lib/libisakai_jni.so`, `share/polkit-1/actions/*.policy`.

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
- `verify-test <apk>` — print the v2 leaf fingerprint or the `SigError`.
- `oracle/diff.sh [case...]` — smali corpus → DEX → our translator vs
  dex2jar → execute both, diff stdout. Needs dex-tools at
  `$D2J` (default `/tmp/oracle/dex2jar/dex-tools-v2.4`); see
  `docs/TESTING.md`. `oracle/lines.sh` checks LineNumberTables.
- `tools/fetch-apksig-vectors.sh` — one-time re-vendor of the AOSP golden
  APKs into `testdata/apksig/` (tests themselves are offline).

## Layout

- `src/main.zig` — CLI dispatch
- `src/install.zig` — APK inspect/verify/stage/install/uninstall
- `src/apksig.zig` — APK Signature Scheme v2 verifier + golden tests
- `src/registry.zig` — package registry, state machine, terminatePid
- `src/dex.zig` — DEX parser
- `src/translate.zig` — DEX→JVM translator + class writer
- `src/launch.zig` — install-time translate, JVM discovery, spawn/reap
- `src/uid_helper.zig` — privileged allocate/remove/runAs
- `src/bridge/` — `window.zig` (Wayland client), `jni.zig` (natives),
  `xkb.zig` (keymap/keycodes)
- `framework/` — Java `android.*` subset + `org.isakai` bridge/runtime
- `tools/` — fixture builder, debug probes, vector fetch script
- `oracle/` — differential translator corpus + harnesses
- `testdata/apksig/` — vendored AOSP v2 golden vectors
- `polkit/` — privilege policy source (staged by the build)
- `docs/` — fix plan, testing guide, architecture, host requirements, supported APIs

## Development

`zig build test` (all unit tests) and `./oracle/diff.sh` (translator
parity) must be green. Design log: `docs/limitations-fix-plan.md`;
system map: `docs/ARCHITECTURE.md`; test inventory: `docs/TESTING.md`;
host requirements: `docs/HOST_REQUIREMENTS.md`; supported API list:
`docs/SUPPORTED_APIS.md`.

## License

GPL-3.0-or-later.
