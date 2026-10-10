# M7 plan — ARM-based mobile apps on x86_64 hosts

Commit baseline: M0–M5 complete, M6 plan in `docs/M6-GAPS-PLAN.md`.
This plan answers: *"support ARM-based mobile applications to run."*

---

## 0. Ground truth first (what "ARM app" actually means here)

An APK is arch-bound in exactly one place: `lib/<abi>/*.so` native
libraries. The DEX bytecode is architecture-neutral — isakai translates
DEX→JVM bytecode, and the JVM runs it on any host arch. **Pure-Java
"ARM" APKs already run on an x86_64 host.** The real M7 scope is:

1. **`lib/arm64-v8a/*.so`** (AArch64, the dominant mobile ABI) — currently
   hard-rejected: `wantEntry` only stages `lib/x86_64/`
   (`src/install.zig:793`), `validateNative` sets `NativeUnsupported`
   otherwise (`src/install.zig:754-760`).
2. **`lib/armeabi-v7a/*.so`** (32-bit ARM) — second priority; many
   legacy apps ship only this. 32-bit emulation is a meaningfully
   harder problem (box64 is 64-bit only; qemu handles both).

So M7 = **ARM native library execution**, layered on M6a's bionic shim.
The Java side needs nothing new — that's the payoff of the DEX→JVM
architecture.

**Also in scope** (cheap, high value): `Build.CPU_ABI` /
`Build.SUPPORTED_ABIS` honesty. Today `Build.java` reports no ABI info —
apps probing the "device" (e.g. Unity/Firebase init) may take wrong
paths. M7 reports the true execution ABI set.

## Research summary (web-verified)

| Path | What | Fit for isakai |
|---|---|---|
| **Reverse box64** (x86→ARM dynarec exists; ARM-on-x86 has partial support upstream) | Userspace dynarec, maps native syscalls to host glibc; "native system libraries" trick | Best perf-per-effort for aarch64-on-x86_64; MIT; packaged (AUR `box64`, Debian `box64`); see ptitSeb/box64 — supports LoongArch/RV64 too |
| **libhoudini / libndk_translation** (Waydroid's ARM bridge) | Intel/Google binary-only translators | Proven at scale on x86 Android (Waydroid users run ARM games), but proprietary blobs, x86-only hosts, legal gray area for redistribution, no source; waydroid issues #2051 (mutual exclusivity bugs), #702 (game crashes) document the fragility |
| **qemu-user-static + binfmt_misc** | Full system-independence emulation of aarch64 processes | ~5–10× slowdown typical; but the **host here already has `qemu-aarch64` binfmt registered** (verified: `/proc/sys/fs/binfmt_misc/qemu-aarch64` exists); zero-code path; packaged everywhere |
| **Cross-compile JVM natives** | AArch64 OpenJDK exists (Temurin/adoptium ships `aarch64` Linux JREs; OpenJDK AArch64 port is a certified platform since JDK 9, JEP 237) | Needed for the qemu/box64 guest, not for the host |
| **project-qemu (Codeberg, Peterson)** | Custom ARM64 ELF loader that runs **Android shared libraries under QEMU**, resolving deps/relocations | Prior art proving "bionic .so under qemu-user + guest linker" works; good reference for the guest-side loader |

**Decision: three-tier fallback chain, one integration point.** The JVM
is DEX-translated (arch-neutral) and stays on the host — only the
app's native `.so` layer needs ARM execution. That containment is what
makes each tier tractable:

- **Tier A (default, correct): qemu-user guest island.** Install a
  Debian/Arch aarch64 sysroot under `framework/aarch64/`: a Temurin
  aarch64 JRE + glibc guest libs. `System.loadLibrary` on an arm64
  `.so` switches the launch path to the guest.

  - Chosen design **A1 — whole-JVM-in-guest**: when an APK has ARM-only
    natives, run the *entire translated JVM* inside qemu-user with an
    aarch64 JDK. DEX→JVM output is arch-neutral JVM class files, so the
    same translated tree loads in the guest JVM. One JVM, real JNI, zero
    protocol work — the alternative (split-process JNI proxying across
    x86/aarch64) means reimplementing JNIEnv over IPC, ~10× the
    engineering for the same correctness. Cost: JIT, GC and the
    framework JNI bridge (`libisakai_jni.so`) all run emulated at
    ~5–10× native — acceptable for utility apps, poor for 3D games
    (Tier B is the escape hatch for those).
- **Tier B (optional accelerator): libndk_translation / libhoudini**
  integration behind an explicit `ISAKAI_ARM_TRANSLATOR=path` opt-in —
  never bundled (proprietary); isakai only wires the dlopen hook so
  users who legally obtain the blobs get near-native speed. Same hook
  shape as M6a's shim, different backend.
- **Tier C (32-bit armeabi-v7a): qemu-arm (32-bit) guest JVM** — only
  aarch64 guest JDKs are meaningfully maintained; 32-bit ARM JREs exist
  (Temurin armv7l) but are slower still. Gate on demand after A1.

## Hard host requirements (verified on this machine)

- `/proc/sys/fs/binfmt_misc/qemu-aarch64` — present ✅ (qemu-user-static).
- Zig cross-compiles `libisakai_jni.so` for aarch64: `zig build
  -Dtarget=aarch64-linux-gnu` (needs guest sysroot or pure-Zig JNI lib —
  current JNI lib is Zig, so cross-build is feasible without a chroot).
- Guest JRE: Temurin aarch64 Linux JRE tarball (adoptium.net), fetched
  at package build time, staged under `framework/jre-aarch64/`
  (~180 MB unpacked). Distro packaging: optional `isakai-arm` package.

## Milestone slices

### M7a — Install-time ARM ABI staging — S (2–3 days)
1. `install.zig`: extend `wantEntry` to stage `lib/arm64-v8a/` (and
   later `lib/armeabi-v7a/`); record primary ABI in registry
   (schema-additive: `native_abi: []const u8 = ""`); keep
   `NativeUnsupported` for mixed/no-executable-ABI cases until M7b.
2. APK ABI resolution = Android semantics (researched): prefer
   `lib/arm64-v8a` over `armeabi-v7a` when both present; x86_64 libs
   still win on x86 hosts (host-native first, matching Android's
   `SUPPORTED_ABIS` order).
3. `Build.java`: add `SUPPORTED_ABIS = {host, "arm64-v8a"}` when M7b
   active; `CPU_ABI` follows. Apps that gate on ABI see honest values.
4. Tests: fat-APK (x86_64+arm64) stages both dirs; arm-only APK installs;
   x86-only on arm host symmetric. Registry round-trips `native_abi`.

### M7b — Guest JVM execution (qemu-user, A1) — L (1–2 weeks)
1. `launch.zig`: if `native_abi == "arm64-v8a"`, wrap the JVM argv in
   `qemu-aarch64` + guest JRE path; `libisakai_jni.so` is cross-built
   Zig (`build.zig -Dtarget=aarch64-linux-gnu` artifact, staged as
   `framework/aarch64/lib/libisakai_jni.so`).
2. `confine.zig`: seccomp filter must run in guest land where the
   syscalls come through qemu — qemu-user makes real syscalls on behalf
   of the guest, so the **same host-side filter works unchanged**
   (qemu issues host syscalls; AUDIT_ARCH check still sees host arch).
   Landlock rules identical (same fs paths). NNP inherits. Verify: run
   the M5 seccomp probe compiled aarch64 under qemu inside the confined
   child — deny/allow behavior must match x86_64 probe results.
3. Wayland/audio/clipboard: guest JVM talks to the same sockets
   (XDG_RUNTIME_DIR passthrough — same paths, no extra mounts);
   framework pipe JNI stays socket-based (bridge), not in-process —
   confirm `bridge/jni.zig` socket paths are arch-neutral (they are:
   file paths under data_dir).
4. Graceful degradation: binfmt absent or qemu missing → loud
   `ArmEmulationUnavailable` at launch (never silent misexec), with
   `ISAKAI_ARM_TRANSLATOR` hook left for Tier B.
5. Tests: (a) cross-built aarch64 `libisakai_jni.so` unit suite under
   qemu; (b) fixture APK with a trivial ARM `libfixture.so` (built via
   zig cc aarch64) headless exit 0; (c) seccomp-in-guest probe as above;
   (d) registry `native_abi` → launch routing table tests.

### M7c — Real-APK ARM gate — M
1. Ladder: pick 2 real OSS F-Droid apps that ship arm64-only natives
   (e.g. a Rust/Go-CLI utility APK, a native-code game). Gate = headless
   exit 0 like M4's OpenCalc/Notes gates.
2. Performance note in docs: qemu-user measured overhead on the gate
   apps (startup wall time vs native-JVM apps); document as
   "acceptable for utility apps, not for 3D games" — games need Tier B.
3. `SUPPORTED_APIS.md`: replace the `NativeUnsupported` row with the
   three-tier table; `linux-interface.json`: no change (seccomp arch
   check unchanged — filter still correct because qemu-user relays
   syscalls with the host's audit arch).

### M7d — Tier B hook (libndk/houdini), opt-in — S, separate
1. `confine.zig` RO set + `launch.zig` env: if `ISAKAI_ARM_TRANSLATOR`
   points to a libndk/houdini tree, `dlopen` it inside the app process
   instead of the qemu guest (fast path for users with the blob).
2. Zero code bundled; docs state the legal position (user-supplied,
   unverified, unsupported). Gate: same fixture with `ISAKAI_ARM_TRANSLATOR`
   set runs natives through the translator.

### Explicitly out of scope for M7
- ARM host support (isakai itself on aarch64 desktops) — nice follow-up;
  Zig cross makes the binary trivial, but JVM/JNI bridge validation on
  real ARM hardware is its own milestone. box64 goes the other way
  (x86-on-ARM) and is irrelevant to this direction.
- 32-bit armeabi-v7a execution (Tier C) — only after arm64 proves out.
- GPU-sharing ARM natives (Vulkan/OpenGL from .so) — same M6a hard
  limit: no Android HW `.so`s; SW rendering path only.

## Sequencing & dependencies

| Order | Slice | Depends on | Gate |
|---|---|---|---|
| 1 | M7a install staging | — | fixture with `lib/arm64-v8a/` stages; mixed ABIs resolve per Android order |
| 2 | M7b guest JVM | M7a | arm-native fixture headless exit 0 under qemu; seccomp probe green in guest |
| 3 | M7c real-APK ladder | M7b | 2 real arm64-natives apps headless exit 0 |
| 4 | M7d Tier B hook | M7a (opt-in, no default) | libndk present → fast path works |

M7a+M7b can start after M6e (apksig) — no dependency on M6a's bionic
shim for the *host* arch; M6a and M7b share `src/native.zig` loader
skeleton and should be sequenced M6a → M7b to reuse the symbol-shim
work where the guest needs bionic-only symbols.

## Sources (researched)

- box64 — x86_64-on-ARM64 userspace dynarec (reverse direction of what
  we need; included to document why NOT chosen as Tier A here):
  https://github.com/ptitSeb/box64 — "runs x86_64 Linux programs on
  ARM64... leverages native system libraries (libc, libm, SDL, OpenGL)".
- libndk/libhoudini ARM translation on x86 (Waydroid prior art, binary
  blobs, mutual-exclusion and stability caveats):
  https://github.com/casualsnek/waydroid_script (ARM translation
  section), waydroid issue #2051.
- qemu-user-static + binfmt_misc mechanics (chosen Tier A mechanism;
  verified registered on this host):
  https://github.com/multiarch/qemu-user-static
  https://ihlenfeldt.net/binfmt-misc
- AArch64 OpenJDK is a first-class certified port (guest JRE legitimacy):
  https://openjdk.org/projects/aarch64-port , JEP 237.
- Temurin aarch64 Linux JRE downloads (guest runtime source):
  https://adoptium.net/temurin/releases?arch=aarch64&os=linux&package=jdk
- Android ABI dirs and precedence:
  https://developer.android.com/ndk/guides/abis
