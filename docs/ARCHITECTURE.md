# Isakai architecture

How the pieces fit. Milestone status lives in `progress.json`; the design
log with links lives in `docs/limitations-fix-plan.md`.

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
└── desktop/dbus        src/bridge/desktop.zig + dbus.zig (portal, notifications, sensors/location D-Bus shape)

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
   `isakai-run-app` wrapper (fork → NNP+mount-ns → Landlock+seccomp →
   setgroups(0) → setresgid → setresuid → chdir → umask 077 → fixed 9-slot
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
  Per-app env is exactly `TMPDIR`, `XDG_RUNTIME_DIR`, `HOME`, `PATH`.
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

Correctness is held by the differential oracle (`oracle/diff.sh`):
smali corpus → DEX → our translator vs dex2jar → execute both on the
host JVM → diff stdout. `oracle/lines.sh` additionally checks line
tables against baksmali and `javap -l`.

## Wayland / JNI bridge

`window.zig` is a hand-rolled client (no libwayland): registry bind,
`wl_shm` pools (memfd, double-buffered, remade on resize),
xdg_surface/toplevel (title from manifest label, app-id = package),
`wl_seat` keyboard + pointer (keymap fd → libxkbcommon; client-side
repeat; 256-event ring). No touch, no clipboard, no wl_output yet.

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

Windowed launches work under isolation: `uid-helper run` takes
`--wayland-sock <abs>`, bind-mounts the compositor socket onto
`<data>/run/<base>` inside a private mount namespace (root pre-drop,
fail-closed `_exit(127)`), then rewrites the app env to
`WAYLAND_DISPLAY=<base>` + `XDG_RUNTIME_DIR=<data>/run`. The JNI
`nOpen` resolver consumes exactly that pair. Verified E2E 2026-10-10
(fixture + real app, exit 0, uid/gid 200000+, NNP=1, split mount-ns).

**Compositor prerequisite**: the app uid connects to the socket as
"other", so the socket inode needs the write bit for other
(`chmod o+w $XDG_RUNTIME_DIR/$WAYLAND_DISPLAY`); the runtime dir
itself stays 0700 since only the socket is bind-mounted in.
Hyprland does not UID-gate Wayland clients beyond file perms.

**Desktop bridge**: `work/run/<pkg>/` stays launcher-owned 0711 (the
unprivileged bridge daemon binds there; the app traverses x-only to
connect). The socket is path-`chmod`'d 0777 — `fchmod` on a unix
socket fd is a silent no-op on current kernels — and clients are
gated by SO_PEERCRED against `--uid`. `work/stage/<pkg>/` is
app-owned 0700 (direct transfer writes). Only `stage/` is chowned
by the helper at install.
