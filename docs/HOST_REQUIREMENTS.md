# Host requirements

What a Linux machine needs to build, install, and run isakai apps.
Clean-machine install: see `README.md` (Quickstart) — `zig build`,
then `zig build install -p /` (or the distro package) for the
`/usr/bin/isakai` + framework + polkit layout.

## OS

- Linux x86_64. No other arch is targeted: translated apps run on the
  host JVM, and the JNI bridge (`libisakai_jni.so`) links against host
  system libraries.
- Any modern kernel works; privileged windowed isolation needs user +
  mount namespace support (`unshare(CLONE_NEWNS)`) and `NO_NEW_PRIVS`
  (`prctl`). Verified on 7.2.8.
- Privileged launches additionally apply kernel confinement
  (`src/confine.zig`) when the kernel provides it:
  - **seccomp-BPF** syscall allowlist (cBPF via
    `prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER)`, requires the
    `NO_NEW_PRIVS` already set). Denied syscalls return `EPERM` — like
    an old kernel, so JVM/library probes degrade gracefully instead of
    crashing. Kernels without seccomp filters: warn, continue.
  - **Landlock** (ABI v1, raw syscalls 444–446): read-only access to the
    JVM + framework + staged code trees, read/write to the app data dir.
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
| `libsqlite3` | `src/bridge/sqlite.zig` | `SQLiteDatabase` backend |

Optional runtime deps (graceful fallback when absent): `ffmpeg`/`ffprobe` (M6b `MediaPlayer`/`MediaMetadataRetriever` — decode + frame extract; `UnsupportedOperationException`/null when missing), `webkit2gtk-4.1` shape (M6c `WebView` headless JS/history without engine, ISAKAI_WEBVIEW=0 gate; full webkit2gtk helper deferred).

Build also needs Zig 0.17 and a C toolchain (the native modules use
`link_libc`). The framework classes compile with the host `javac`
(`--release 17`).

Native shim: `src/native/bionic_shim.zig` builds `framework/lib/libisakai_bionic_shim.so` (LD_PRELOAD for `__android_log_*`, `property_*`, `android_dlopen_ext`); injected via `LD_PRELOAD` in both privileged (`uid_helper` fixed 9-slot envp) and unprivileged (`cloneSandboxEnv`) launch paths; Landlock RO already covers the code `lib/` tree.

## Privilege: polkit + pkexec (3 split actions, M6f)

- Privileged ops go through `isakai uid-helper ...` as root via `pkexec` and three polkit actions `id.isakai.{allocate-uid,remove-uid,run-app}` (`auth_admin`, per-action messages, no `$(command_line)` leak), staged from `polkit/id.isakai.allocate-uid.policy` + wrappers `polkit/bin/isakai-*-uid` (`build.zig` + `make install` place them in `bin/`; `make uninstall` removes them).
- Each action pins `org.freedesktop.policykit.exec.path` to its wrapper (`/usr/bin/isakai-*-uid` → `exec /usr/bin/isakai uid-helper {allocate,remove,run} "$@"`): the wrappers + `/usr/bin/isakai` MUST be at those paths or authorization is silently denied. `pkexec` matches `exec.path` exactly; the wrappers keep the CLI byte-identical while giving the dialog per-operation text.
- Dev/test bypass: `ISAKAI_NO_PRIVILEGE=1` runs everything as self (no pkexec, no app user). Missing wrappers (relocatable dev installs) fall back to direct `pkexec $exe uid-helper <op> ...` argv — behaviour byte-identical, only the dialog text is generic.

## Compositor (windowed launches)

- A Wayland compositor exposing a unix socket via
  `WAYLAND_DISPLAY` + `XDG_RUNTIME_DIR` (absolute `$WAYLAND_DISPLAY`
  used as-is, else `$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`).
- Privileged windowed runs bind-mount the socket into the app sandbox;
  the app uid connects as "other", so the socket inode needs the write
  bit for other: `chmod o+w $XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`. The
  runtime dir itself stays 0700 — only the socket is bind-mounted in.
  (Details: `docs/ARCHITECTURE.md`, "Privileged windowed sandboxing".)
- Headless launches need no compositor. `DBUS_*` is never propagated to
  the app; desktop integration goes through the per-app
  `isakai-dbus-bridge` daemon.

## Disk / RAM

Ballparks, not minimums:

- Toolchain + checkout: Zig (~100 MB) + JDK 17+ (~300 MB).
- Per installed app: staged APK contents + translated `classes/` —
  single-digit MB for typical apps.
- Runtime: one host JVM per running app (~100–200 MB RSS) plus two
  Wayland SHM buffers (width × height × 4 bytes each, remade on resize).

## Environment variables

All `ISAKAI_*` names read anywhere in `src/` + JNI (`grep -rho
'ISAKAI_[A-Z_]*' src framework`):

| Variable | Set by | Default | Meaning |
|---|---|---|---|
| `ISAKAI_WORK` | user | `~/.local/share/isakai` | registry + staged code + app data (`--work` overrides) |
| `ISAKAI_FRAMEWORK` | user (dev) | `<work>/../framework` | compiled `android.*` + `libisakai_jni.so` (dev: `zig-out/framework`) |
| `ISAKAI_WINDOW` | user | unset | `1` = windowed launch, else headless |
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
| `ISAKAI_WEBVIEW` | user | unset | `0`/`false` = disable `WebView.loadUrl` (M6c, fail-closed via onReceivedError) |
| `JAVA_HOME` | user | unset | first JVM candidate (`$JAVA_HOME/bin/java`) |
