# Host requirements

What a Linux machine needs to build, install, and run isakai apps.
Build/install commands are in [README.md](../README.md). `make install`
uses the intended FHS layout; `zig build --prefix` alone uses a development
layout and is not a substitute for package installation.

This is a technical reference; plans, research decisions and progress live
in [PROGRESS.md](../PROGRESS.md).
Runtime verification commands and host-specific prerequisites are in
[verify-isakai/SKILL.md](../verify-isakai/SKILL.md); results belong in
[PROGRESS.md](../PROGRESS.md).

## OS

- Linux x86_64. No other arch is targeted: translated apps run on the
  host JVM, and the JNI bridge (`libisakai_jni.so`) links against host
  system libraries.
- Privileged windowed isolation needs mount namespace support
  (`unshare(CLONE_NEWNS)`) and `NO_NEW_PRIVS` (`prctl`). Kernel confinement
  must be validated on the target host with the required privileged gate.
- Privileged launches additionally apply kernel confinement
  (`src/confine.zig`) when the kernel provides it:
  - **seccomp-BPF** syscall allowlist (cBPF via
    `prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER)`, requires the
    `NO_NEW_PRIVS` already set). Denied syscalls return `EPERM` — like
    an old kernel, so JVM/library probes degrade gracefully instead of
    crashing. Kernels without seccomp filters: warn, continue.
  - **Landlock** (ABI-negotiated, raw syscalls 444–446): read-only access to
    the JVM, its configuration (including a symlinked conf directory), framework,
    code, bridge and selected system paths; read/write app data and transfer stage.
    Java temporary files use the app cache. REFER/TRUNCATE are handled when supported.
    Kernels without Landlock (`ENOSYS`): warn, continue — UID isolation
    and `NO_NEW_PRIVS` still hold. Hard Landlock errors fail closed
    (launch aborts, exit 127).
  - Debug opt-out: `ISAKAI_CONFINEMENT=0` skips both layers (see the
    environment table).
- A Wayland compositor for windowed launches (headless needs none).
  See "Compositor" below.

## Java

- Host JDK 17 or newer providing `javac` (build) and `java` (run).
  Verified on JDK 27.
- JVM discovery order (`src/launch.zig:findJava`):
  `$JAVA_HOME/bin/java` → bare `java` on `PATH` (unprivileged runs
  only) → highest-feature `/usr/lib/jvm/*/bin/java`. Every candidate is
  probed with `java -version`; the first with feature >= 17 wins.
- Every launch passes `--enable-native-access=ALL-UNNAMED` (JEP 412/472),
  so JDK 23+ restricted-native-access warnings do not apply. Nothing
  extra to configure.

## System libraries (build + run)

Linked by `build.zig` into `libisakai_jni.so`:

| Library | Used by | Notes |
|---|---|---|
| `libxkbcommon` | `src/bridge/xkb.zig` | keymap from the Wayland keymap fd |
| `libpulse-simple` | `src/bridge/audio.zig` | PulseAudio playback for `SoundPool` |
| ICU development headers/libraries (`libicu-dev` / `icu`) | `src/bridge/sqlite_collation.c` | Android UNICODE collation using host ICU |
| `libsqlite3` | `src/bridge/sqlite.zig` | `SQLiteDatabase` backend |
| `zlib` | AOSP libandroidfw (static, `src/native/res_engine.cpp`) | APK entry inflation for resources and assets |

The resource engine compiles AOSP libandroidfw and its host closure
(libbase, libutils, libziparchive, incfs-utils) from the sources pinned in
`build.zig.zon`, statically, with Zig's bundled libc++. `zig build` fetches
them once into `zig-pkg/` and installs ATL's pinned `framework-res.apk`
(AOSP framework resources) as `framework/framework-res.apk`.

Optional runtime dependencies: `ffmpeg`/`ffprobe` for partial local-file audio
decode and frame extraction. Missing tools produce exceptions or null results.
WebView has no engine integration; installing webkit2gtk does not enable web
rendering. Sensors and location report unavailable providers by default;
synthetic values require the explicit test switches listed below.

Build also needs Zig 0.17 and a C toolchain (the native modules use
`link_libc`). The framework classes compile with the host `javac`
(`--release 17`).

Native shim: `src/native/bionic_shim.zig` builds `framework/lib/libisakai_bionic_shim.so` (LD_PRELOAD for `__android_log_*`, `property_*`, `android_dlopen_ext`); injected via `LD_PRELOAD` in both privileged (`uid_helper` fixed 9-slot envp) and unprivileged (`cloneSandboxEnv`) launch paths; Landlock permits reading/loading staged libraries.

## Privilege: polkit + pkexec (3 split actions, M6f)

- Privileged ops go through `isakai uid-helper ...` as root via `pkexec` and three polkit actions `id.isakai.{allocate-uid,remove-uid,run-app}` (`auth_admin`, per-action messages, no `$(command_line)` leak), defined together in `polkit/id.isakai.policy` and the `polkit/bin/isakai-*-uid` wrappers. Make install migrates the legacy bundle filename and stages all three actions and wrappers; uninstall removes them.
- Each action pins `org.freedesktop.policykit.exec.path` to its wrapper (`/usr/bin/isakai-*-uid` → `exec /usr/bin/isakai uid-helper {allocate,remove,run} "$@"`): the wrappers + `/usr/bin/isakai` MUST be at those paths or authorization is silently denied. `pkexec` matches `exec.path` exactly; the wrappers keep the CLI byte-identical while giving the dialog per-operation text.
- Dev/test bypass: `ISAKAI_NO_PRIVILEGE=1` runs everything as self (no pkexec, no app user). Missing wrappers (relocatable dev installs) fall back to direct `pkexec $exe uid-helper <op> ...` argv — behaviour byte-identical, only the dialog text is generic.

## Compositor (windowed launches)

- A Wayland compositor exposing a unix socket via
  `WAYLAND_DISPLAY` + `XDG_RUNTIME_DIR` (absolute `$WAYLAND_DISPLAY`
  used as-is, else `$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`).
- Privileged windowed runs bind-mount the socket into the app sandbox;
  grant the app UID socket access using an ACL such as
  `setfacl -m u:<app-uid>:rw <socket>`. The runtime directory stays 0700;
  only the socket is bind-mounted in. The test harness restores its ACL.
  (Details: `docs/ARCHITECTURE.md`, "Privileged windowed sandboxing".)
- Headless launches need no compositor. `DBUS_*` is never propagated to
  the app; desktop integration goes through the per-app
  `isakai-dbus-bridge` daemon.

## Disk / RAM

Ballparks, not minimums:

- Toolchain + checkout: Zig (~100 MB) + JDK 17+ (~300 MB).
- Per installed app: the retained read-only `base.apk`, staged APK
  contents and translated `classes/` — single-digit to low double-digit MB
  for typical apps.
- Runtime: one host JVM per running app (~100–200 MB RSS) plus two
  Wayland SHM buffers (width × height × 4 bytes each, remade on resize).

## Environment variables

All `ISAKAI_*` names read anywhere in `src/` + JNI (`grep -rho
'ISAKAI_[A-Z_]*' src framework`):

| Variable | Set by | Default | Meaning |
|---|---|---|---|
| `ISAKAI_WORK` | user | `~/.local/share/isakai` | registry + staged code + app data (`--work` overrides) |
| `ISAKAI_FRAMEWORK` | user (dev) | `<work>/../framework`, then `/usr/share/isakai/framework` | compiled `android.*` + `libisakai_jni.so` (dev: `zig-out/framework`) |
| `ISAKAI_WINDOW` | user | unset | `1` = windowed; `run` defaults to windowed when Wayland is available, otherwise headless |
| `ISAKAI_NO_PRIVILEGE` | user | unset | `1` = run as self, skip pkexec (dev/test) |
| `ISAKAI_CONFINEMENT` | user (debug) | unset | `0` = skip Landlock + seccomp in the privileged child (confinement debug opt-out) |
| `ISAKAI_NONINTERACTIVE` | user | unset | `1` = `sudo -n` instead of pkexec prompts |
| `ISAKAI_ALLOW_UNSIGNED` | user | unset | `1` = install unsigned APKs (dev fixtures only) |
| `ISAKAI_ZEROCOPY` | user | `1` (enabled) | `0` = force the pixel-copy post path (debug); read by the JNI bridge |
| `ISAKAI_NO_BRIDGE` | user/tests | unset | `1` = skip spawning the per-app desktop bridge (headless/bridge-less runs) |
| `ISAKAI_BRIDGE_DIR` | user/tests | `<work>/run/<pkg>` | override for the launcher-owned bridge dir (validated: absolute, no `..`) |
| `ISAKAI_STAGE_DIR` | user/tests | `<work>/stage/<pkg>` | override for the app-owned stage dir (same validation) |
| `ISAKAI_BRIDGE` | launcher → app | — | bridge dir inside the sandbox; the JVM's `IsakaiDesktop` connects to `$ISAKAI_BRIDGE/bridge.sock`. Never set by hand. |
| `ISAKAI_STAGE` | launcher → app | — | stage dir inside the sandbox. Never set by hand. |
| `ISAKAI_SENSOR_LIGHT` | test | `100` | light sensor virtual lux for `SensorManager` (M6d) |
| `ISAKAI_FAKE_SENSORS` | test | unset | `1` opts into synthetic accelerometer/light readings; no host provider is wired |
| `ISAKAI_FAKE_LOCATION` | test | unset | `1` opts into synthetic location readings; no host provider is wired |
| `ISAKAI_WEBVIEW` | user | unset | `0`/`false` = disable `WebView.loadUrl` (M6c, fail-closed via onReceivedError) |
| `ISAKAI_RESOURCES` | user (comparison) | unset | `legacy` = resolve resources with the staged-table Java parser instead of libandroidfw; unprivileged launches only (the privileged child gets a fixed environment) |
| `JAVA_HOME` | user | unset | first JVM candidate (`$JAVA_HOME/bin/java`) |
