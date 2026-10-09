# Isakai architecture

How the pieces fit. Milestone status lives in `progress.json`; the design
log with links lives in `docs/limitations-fix-plan.md`.

## Module map

```
isakai (CLI)            src/main.zig
├── install             src/install.zig   APK inspect/verify/stage/install/uninstall
│   ├── registry        src/registry.zig  JSON registry, flock, state machine, terminatePid
│   ├── apksig          src/apksig.zig    APK Signature Scheme v2 verifier
│   └── uid_helper      src/uid_helper.zig  privileged allocate/remove/runAs
├── launch              src/launch.zig    translate-at-install, JVM find/spawn/reap
│   ├── dex             src/dex.zig       DEX parser
│   ├── translate       src/translate.zig DEX→JVM translator + class writer
│   └── install, registry (above)
└── uid_helper (above, via pkexec as root)

isakai_jni (shared lib) src/bridge/jni.zig    JNI natives (static exports)
└── window              src/bridge/window.zig Wayland client (compositor/shm/xdg/seat)
    └── xkb             src/bridge/xkb.zig    libxkbcommon wrapper + evdev table

framework/ (javac, --release 17)
├── android.app / view / widget / graphics / text   android.* subset
└── org.isakai.bridge (IsakaiWindow) + org.isakai.runtime (IsakaiMain)
```

## Install → translate → launch data flow

1. `isakai install a.apk`: inspect (binary manifest, DEX magic, native
   gate) → `apksig.verifyApk` (v2-only, leaf pin) → stage code under
   `<work>/code/<pkg>/<sha256>/` → reserve UID → pkexec
   `uid-helper allocate` (user + 0700 dirs) → commit registry.
2. `isakai launch <pkg>`: mark starting → `translateApp` (DEX→JVM into
   `classes/`, skipped when the digest marker matches) → spawn the JVM
   (`-cp classes:framework`, `IsakaiMain <data> <app> <act> <pkg>
   <title>`) directly (`ISAKAI_NO_PRIVILEGE=1`) or via pkexec
   `uid-helper run` (fork → setgroups(0) → setresgid → setresuid →
   chdir → umask 077 → exec) → record running/exited.
3. Windowed (`ISAKAI_WINDOW=1`): `IsakaiMain` runs the Activity through
   `runWindowed` — open toplevel, draw into SHM (zero-copy map or copy
   post), pump input, close. Headless runs `runHeadless` instead.

## Trust boundaries

- **Unprivileged CLI** never touches other users' data. Everything
  privileged goes through `isakai uid-helper {allocate,remove,run}`
  as root via the single polkit action `id.isakai.allocate-uid`
  (`auth_admin`, message names `$(command_line)`).
- **Narrow helper**: `allocate`/`remove` validate the package name
  (strict dotted segments), UID (200000–299999), and data dir
  (dirfd-anchored, NOFOLLOW, 0700); `remove` unlinks via dirfds only.
- **runAs allowlist**: `argv[0]` absolute + world-executable + basename
  `java`, and argv must contain `org.isakai.runtime.IsakaiMain`.
  Per-app env is exactly `TMPDIR`, `XDG_RUNTIME_DIR`, `HOME`, `PATH`.
- **Single-identity pin**: the v2 leaf SPKI fingerprint is stored at
  install; multi-signer APKs and RSA >4096 are rejected fail-closed.
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

## Privileged windowed limitation

Windowed launches currently require `ISAKAI_NO_PRIVILEGE=1`: the
dropped-privilege child gets a fresh `XDG_RUNTIME_DIR` with no
compositor socket. Brokering the Wayland socket into the sandbox is
M4+ work (a clear stderr hint is printed otherwise).
