# Isakai architecture

How the pieces fit. [progress.json](../progress.json) is the only progress
tracker; [PLAN.md](PLAN.md) defines delivery scope and acceptance criteria.
This guide describes implementation mechanisms, not proof of production readiness.

## Behavioral boundaries

The activity loop runs until close/back. Handler posts
run immediately on the caller, and ordinary in-app activity navigation is not
implemented. WebView is a history/HTTP HEAD/reflection shell with no rendering
engine. Sensor and location helpers return synthetic readings; their real
D-Bus providers are not implemented. See the plan for the corresponding work.

## Module map

```
isakai (CLI)            src/main.zig
├── install             src/install.zig   APK inspect/verify/stage/install/uninstall (x86_64 native shim: LD_PRELOAD)
│   ├── registry        src/registry.zig  JSON registry, flock, state machine, terminatePid, signer_sha256_set
│   ├── apksig          src/apksig.zig    APK Signature Scheme v2 verifier (multi-signer ≤16, P-521 unsupported)
│   └── uid_helper      src/uid_helper.zig  privileged allocate/remove/runAs (LD_PRELOAD shim in fixed envp)
├── launch              src/launch.zig    translate-at-install, JVM find/spawn/reap (java.library.path + LD_PRELOAD shim)
│   ├── dex             src/dex.zig       DEX parser
│   ├── translate       src/translate.zig DEX→JVM translator + class writer
│   └── install, registry (above)
└── uid_helper (above, via pkexec as root, 3 polkit actions)
    ├── polkit/bin/isakai-*-uid  wrappers (exec.path per action, no $(command_line))
    └── native/bionic_shim.zig  LD_PRELOAD bionic interposition (log/property/dlopen, spinlock, 64-entry)

isakai_jni (shared lib) src/bridge/jni.zig    JNI natives (static exports)
├── window              src/bridge/window.zig Wayland client (compositor/shm/xdg/seat)
│   └── xkb             src/bridge/xkb.zig    libxkbcommon wrapper + evdev table
├── audio               src/bridge/audio.zig  PulseAudio S16LE (SoundPool + MediaPlayer)
└── desktop/dbus        src/bridge/desktop.zig + dbus.zig (portal and notifications)

framework/ (javac, --release 17)
├── android.app / view / widget / graphics / text   android.* subset
├── android.media       MediaPlayer + MediaMetadataRetriever + IsakaiMedia (ffmpeg)
├── android.webkit      WebView + WebViewClient + WebSettings + CookieManager + JS bridge
├── android.hardware/location  Sensor/Location + managers via IsakaiSensor/IsakaiLocation (virtual fallback)
└── org.isakai.bridge   IsakaiWindow + IsakaiAudio + IsakaiMedia + IsakaiSensor/IsakaiLocation + IsakaiDesktop
    + org.isakai.runtime IsakaiMain + org.isakai.db + prefs
```

## Install → translate → launch data flow

1. `isakai install a.apk`: inspect (binary manifest, DEX magic, x86_64 native
   shim gate — other ABIs still NativeUnsupported) → `apksig.verifyApk`
   (v2, multi-signer ≤16 sorted+deduped, P-521 unsupported, leaf set) →
   stage code + `lib/x86_64/*.so` RO under `<work>/code/<pkg>/<sha256>/` →
   reserve UID → pkexec `isakai-allocate-uid` wrapper (user + 0700 dirs) →
   commit registry with `signer_sha256_set`.
2. `isakai launch <pkg>`: mark starting → `translateApp` (DEX→JVM into
   `classes/`, skipped when the digest marker matches) → spawn the JVM
   (`-cp classes:framework`, `-Djava.library.path=framework/lib:code/lib/x86_64:code/lib`,
   `LD_PRELOAD=libisakai_bionic_shim.so` when framework ships it,
   `IsakaiMain <data> <app> <act> <pkg> <title>`) directly
   (`ISAKAI_NO_PRIVILEGE=1`, shim via cloneSandboxEnv) or via pkexec
   `isakai-run-app` wrapper (fork → NNP+mount-ns/socket mount →
   setgroups(0) → setresgid → setresuid → chdir → Landlock+seccomp → umask 077 → fixed 9-slot
   envp with LD_PRELOAD → exec) → record running/exited.
3. Windowed (`ISAKAI_WINDOW=1`): `IsakaiMain` runs the Activity through
   `runWindowed` — open toplevel, draw into SHM (zero-copy map or copy
   post), pump input, close. Headless runs `runHeadless` instead.

## Trust boundaries

- **Unprivileged CLI** never touches other users' data. Everything
  privileged goes through `isakai uid-helper {allocate,remove,run}`
  as root via three polkit actions `id.isakai.{allocate-uid,remove-uid,run-app}`
  (`auth_admin`, per-action wrappers `isakai-*-uid` pin exec.path, no `$(command_line)` leak).
- **Narrow helper**: `allocate`/`remove` validate the package name
  (strict dotted segments), UID (200000–299999), and data dir
  (dirfd-anchored, NOFOLLOW, 0700); `remove` unlinks via dirfds only.
- **runAs allowlist**: `argv[0]` absolute + world-executable + basename
  `java`, and argv must contain `org.isakai.runtime.IsakaiMain`.
  Per-app environment includes `TMPDIR`, `XDG_RUNTIME_DIR`, `HOME`, `PATH`,
  `ISAKAI_BRIDGE`, `ISAKAI_STAGE`, and conditional `WAYLAND_DISPLAY` and `LD_PRELOAD`.
- **Multi-signer pin**: all v2 signers' SPKI fingerprints (≤16, sorted+deduped) stored as `signer_sha256_set`; upgrade requires exact set match (legacy single-fp fallback when stored set empty); P-521 stays UnsupportedAlgorithm (no secp521r1 in Zig 0.17), RSA >4096 rejected.
- **Translator fail-closed**: unknown opcodes/sections reject the class
  (`UnsupportedOpcode`/`BadSection`); `translate-test --keep-going`
  surveys real APKs without aborting.

## DEX → JVM translator

`translate.zig` lowers each DEX method to JVM bytecode (major v49, pure
inference verification — no StackMaps needed): typed register tracking
(int/float/ref/long/double), wide-pair locals, exception tables from
DEX tries, LineNumberTables from `debug_info`, synthesized
const/class/array/cmp/switch/fill lowerings, `ldc`/`ldc_w`/`ldc2_w`
constant pools (Modified UTF-8, CESU-8 astral). Interface lists,
abstract/native/interface flags, and annotations-adjacent attributes
are preserved so translated classes verify and link.

Translator behavior is compared by the differential oracle (`oracle/diff.sh`):
smali corpus → DEX → our translator vs dex2jar → execute both on the
host JVM → diff stdout. `oracle/lines.sh` additionally checks line
tables against baksmali and `javap -l`.

## Wayland / JNI bridge

`window.zig` is a hand-rolled client (no libwayland): registry bind,
`wl_shm` pools (memfd, double-buffered, remade on resize),
xdg_surface/toplevel (title from manifest label, app-id = package),
`wl_seat` keyboard + pointer (keymap fd → libxkbcommon; client-side
repeat; 256-event ring). The client also implements `wl_touch`,
`wl_output` metrics and data-device clipboard integration.

`jni.zig` exposes static natives (`nOpen/nPost/nMapBuffer/nCommit/
nPollSize/nSize/nRun/nPollInput/nClose`) via fixed JNIEnv slot indices
(no `RegisterNatives`). `IsakaiWindow` (Java) wraps them: `post(int[])`
copies ARGB→XRGB; `mapBuffer` returns a DirectByteBuffer over the live
SHM slot (caller must drop it before resize/close — stale access is a
native crash, enforced by convention). Input arrives as 8-int batches
via `nPollInput` (no upcalls). The JVM needs
`--enable-native-access=ALL-UNNAMED` (JEP 412/472).

The framework (`android.view/viewGroup`, widgets, `Canvas` software
raster, `BitmapFont` 5x7 glyphs) renders into the posted buffers; the
Activity pump dispatches `KeyEvent`/`MotionEvent` to the focused /
hit-tested view and redraws on the dirty flag.

## Privileged windowed sandboxing

The isolated windowed path works as follows: `uid-helper run` takes
`--wayland-sock <abs>`, bind-mounts the compositor socket onto
`<data>/run/<base>` inside a private mount namespace (root pre-drop,
fail-closed `_exit(127)`), then rewrites the app env to
`WAYLAND_DISPLAY=<base>` + `XDG_RUNTIME_DIR=<data>/run`. The JNI
`nOpen` resolver consumes exactly that pair. The acceptance harness checks app-side isolation and a real surface;
see [verification instructions](../verify-isakai/SKILL.md).

**Compositor prerequisite**: grant the app UID access to the socket inode
(e.g. an ACL `setfacl -m u:<app-uid>:rw <socket>`). The runtime directory
stays 0700 since only the socket is bind-mounted in. The verification harness
restores the original ACL after testing.
Hyprland does not UID-gate Wayland clients beyond file perms.

**Desktop bridge**: the socket lives at `/tmp/isakai-<uid>-<digest16>/bridge.sock` (`src/bridge/path.zig`; launcher-owned 0711, verified owner and mode, digest of the work dir + package). It cannot live under the work tree: `sockaddr_un.sun_path` holds 107 bytes plus NUL and `<work>/run/<pkg>/bridge.sock` exceeded that for deep work dirs, which silently disabled desktop integration. Launcher, privileged helper, Landlock grant and the JNI client all consume this one definition; the launcher logs readiness and the daemon logs accepted client uids. `work/run/<pkg>/` remains only as the legacy anchor for callers that pass no `--bridge-dir` (the
unprivileged bridge daemon binds there; the app traverses x-only to
connect). The socket is path-`chmod`'d 0777 — `fchmod` on a unix
socket fd is a silent no-op on current kernels — and clients are
gated by SO_PEERCRED against `--uid`. `work/stage/<pkg>/` is
app-owned 0700 (direct transfer writes). Only `stage/` is chowned
by the helper at install.

## Host interface index

This reference replaces the separate interface checklist. It identifies code,
not completion status; current work and evidence live only in
[progress.json](../progress.json).

| Host interface | Implementation location / boundary |
|---|---|
| Files, ZIP staging, permissions and hashing | `src/install.zig`, `src/registry.zig`, `src/uid_helper.zig` |
| JVM discovery, spawn/reap and termination | `src/launch.zig`, `src/registry.zig` |
| App users and credential drop | `src/uid_helper.zig`; useradd/userdel and privileged wrappers |
| Polkit action definitions | `polkit/*.policy`, `polkit/bin/`; one bundle declares three actions |
| Mount namespaces and no-new-privileges | `src/uid_helper.zig` |
| Seccomp and Landlock | `src/confine.zig`; ABI-negotiated filesystem rights, applied after privilege drop |
| APK v2 signature verification | `src/apksig.zig`; upgrade signer checks in `src/upgrade.zig` |
| Manifest and resource tables | `src/install.zig`, `src/arsc.zig`, Java resource classes |
| Wayland surfaces, SHM, frame/input/output events and clipboard | `src/bridge/window.zig` |
| Keyboard keymaps | `src/bridge/xkb.zig`, system libxkbcommon |
| JNI exports and direct buffers | `src/bridge/jni.zig` |
| PulseAudio playback | `src/bridge/audio.zig`, system libpulse-simple |
| SQLite | `src/bridge/sqlite.zig`, system libsqlite3 |
| Desktop notifications, URI and file portals | `src/bridge/desktop.zig`, `src/bridge/dbus.zig`, `src/dbus_bridge.zig` |
| Selected Android native symbols | `src/native/bionic_shim.zig`; LD_PRELOAD, not full bionic |
| Audio decoding and frame extraction | `framework/org/isakai/bridge/IsakaiMedia.java`; host ffmpeg/ffprobe subprocesses |
| Application event pumping | `framework/android/app/Activity.java`; Java queue semantics require C3 |

The current pump polls native input instead of making JNI input upcalls.
A new epoll/timerfd/eventfd architecture is not required by the delivery plan.
Real WebView, sensor-proxy and GeoClue backends are not present.
