# ARM translation on x86_64 hosts (plus native-ARM64 alternative)

## FEX-Emu vs Box64 vs QEMU-user for AArch64 game libraries on x86_64

### Takeaway
Only QEMU-user runs ARM64 code on x86_64 at all: FEX-Emu and Box64 are both x86-on-Arm emulators (wrong direction), so the "three-way comparison" collapses to QEMU-user versus the native-ARM64-host alternative, and QEMU-user runs whole guest processes, not an ARM `.so` loaded into an x86_64 JVM runner.

### Cited Findings
- FEX is titled "Emulate x86 Programs on ARM64" and "allows you to run x86 applications on ARM64 Linux devices, similar to qemu-user and box64" — [FEX README](https://github.com/FEX-Emu/FEX/blob/main/Readme.md)
- FEX's own homepage describes it as "a fast linux usermode x86 and x86-64 emulator" for running x86 applications on ARM64 Linux, with OpenGL/Vulkan call forwarding to host libraries and an experimental code cache — [FEX-Emu](https://fex-emu.com/)
- FEX requires ARMv8.0-a or newer hardware plus an x86-64 RootFS fetched by `FEXRootFSFetcher` — [FEX README](https://github.com/FEX-Emu/FEX/blob/main/Readme.md)
- Box64 "enables running x86_64 Linux programs, including games, on non-x86_64 Linux systems such as Arm (64-bit little-endian host system required)" — [Box64 README](https://github.com/ptitseb/box64/blob/main/README.md)
- QEMU user-mode is documented as Linux/BSD-only user-space emulation with per-arch binaries such as `qemu-aarch64` (`qemu-i386 [-h] [-d] [-L path] ... program [arguments...]`) — [QEMU user docs](https://www.qemu.org/docs/master/user/main.html)
- On Linux, QEMU user-mode emulates `clone` by creating a real host thread with a separate virtual CPU per emulated thread, but namespace-affecting clone flags are not supported — [QEMU user docs](https://www.qemu.org/docs/master/user/main.html)
- User-mode emulation "has always mirrored the thread structure of the translated executable," with MTTCG system-mode work additionally improving linux-user stability; multi-threaded TCG runs one thread per vCPU where host memory model and guest both support it — [QEMU MTTCG docs](https://www.qemu.org/docs/master/devel/multi-thread-tcg.html)
- QEMU's only GPU pass-through story (virgl: guest OpenGL translated to Gallium IR, re-emitted as host OpenGL via virglrenderer; guest capped at OpenGL 4.3 by default) is a virtio-gpu **system-emulation** device (`-device virtio-gpu-gl`), with no user-mode equivalent; the default 2D virtio-gpu device explicitly requires the guest to use a software renderer such as Mesa or SwiftShader — [QEMU virtio-gpu docs](https://www.qemu.org/docs/master/system/devices/virtio/virtio-gpu.html)
- QEMU translates system calls generically (endianness, 32/64-bit parameter size) plus device-specific `ioctl()`s, and redirects/synthesizes POSIX signals including real-time signals on Linux — [QEMU user docs](https://www.qemu.org/docs/master/user/main.html)
- The program's own compatibility plan already records the operative rule: "the runner should prefer x86_64 slices where fat APKs carry them while documenting ARM-only native games as unsupported without emulation" — [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)

### Inferences
- FEX's thunking (forwarding GL/Vulkan calls to host libraries) is the attractive model for game libraries, but it exists only in the x86-guest direction; there is no ARM-guest thunking host-side on x86_64, so its game-compat maturity does not transfer to this problem.
- For thread-heavy game `.so` code, QEMU-user's thread mirroring is the least-bad fit of the three, but without user-mode GPU forwarding every GLES/Vulkan call from an ARM game library would need a guest-side software renderer (guest Mesa llvmpipe/SwiftShader inside the `-L` rootfs) — playable for logic, not for 3D games at acceptable frame rates.
- The in-process consumer (ARM `.so` `dlopen`ed by an x86_64 JVM runner) is architecturally incompatible with all three: none can execute guest machine code inside a host-architecture process. QEMU-user could only cover the whole runner (entire JVM + app) running as an AArch64 guest process.

### Gaps
- No current (2026) game-compat tracker state was retrievable for qemu-aarch64 running Android NDK libraries; issue-tracker browsing was out of tool reach.
- No measured 2026 performance figures (TCG throughput vs native for NEON-heavy game loops) found; only the structural facts above are sourced.

## Host requirements and breakage for each option

### Takeaway
QEMU-user needs a guest rootfs (`-L`), `binfmt_misc` for transparent execution, and address-space management (`-B`/`-R`); the hard breakage is page size (host page size cannot be changed — the pretending `-p` option was removed in QEMU 10.2) plus JIT/self-modifying-code costs, while FEX/Box64 requirements are moot on x86_64 hosts.

### Cited Findings
- QEMU provides `qemu-binfmt-conf.sh` to register foreign binaries with the kernel's `binfmt_misc`, with the `F` (fix-binary) flag via `--persistent` so the emulator binary need not be resolved inside chroots — [QEMU testing docs](https://github.com/qemu/qemu/blob/master/docs/devel/testing/main.rst)
- `qemu-aarch64 -L path` sets the guest ELF interpreter prefix (guest rootfs); `-B offset` shifts the guest address base when the host has reserved the needed region; `-R size` pre-allocates guest virtual address space — [QEMU user docs](https://www.qemu.org/docs/master/user/main.html)
- The `-p` option "pretends to control the host page size. However, it is not possible to change the host page size"; it warned since 9.0 and was removed entirely in 10.2 — [QEMU removed-features](https://www.qemu.org/docs/master/about/removed-features.html)
- QEMU handles self-modifying code in user mode by write-protecting pages holding translated blocks and invalidating all translated code in a page on a SEGV-triggered write — [QEMU TCG docs](https://www.qemu.org/docs/master/devel/tcg.html)
- Android's page-size context: Unreal supports both 4 KB and 16 KB page sizes, apps targeting API 35+ must support 16 KB pages with aligned LOAD segments after Android 15's change, and Play has required API-35 targeting since August 2025 — [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)
- FEX's prerequisites (ARMv8 hardware, x86-64 RootFS) cannot be satisfied on an x86_64 host in the needed direction, and Box64 likewise demands a 64-bit-LE Arm (or RISC-V/LoongArch) host — [FEX README](https://github.com/FEX-Emu/FEX/blob/main/Readme.md); [Box64 README](https://github.com/ptitseb/box64/blob/main/README.md)
- QEMU user-mode emulation is supported only on Linux and BSD hosts — [QEMU user docs](https://www.qemu.org/docs/master/user/main.html)

### Inferences
- The 16 KB-page Android binaries the runner will increasingly see (API 35+ mandatory) run on 4 KB-page x86_64 hosts only insofar as their LOAD-segment alignment is a multiple the host honors; anything that `mmap`s or `mprotect`s assuming 16 KB granularity, or JITs code assuming guest page size, is the likeliest silent-breakage class under qemu-aarch64 — and the removed `-p` flag means there is no knob to paper over it.
- JIT engines inside game libraries (Unity IL2CPP output, JS engines in hybrid apps) pay double translation cost under QEMU-user (guest JIT emits ARM code that TCG re-translates) plus page-invalidation churn from the write-protect mechanism; this is workable for correctness but is the expected performance cliff.
- `binfmt_misc` + `-L` rootfs is the only "working setup" of the three on x86_64; FEX/Box64 setups on x86_64 are not downgraded options, they are non-options.

### Gaps
- No sourced confirmation of how qemu-aarch64 (QEMU 9/10, 2026) specifically handles 16 KB-aligned Android NDK binaries on 4 KB x86_64 hosts — the failure mode is inferred from the `-p` removal, not from a cited bug report.
- Seccomp / `clone`-flag interactions for a JVM running wholly under qemu-aarch64 were not researched (namespace flags unsupported per cited docs; ART/JVM behavior under TCG unknown).

## Native alternative: what ARM64 host builds change

### Takeaway
Shipping ARM64 host builds (the plan's N7 release) removes translation entirely — ARM-only `.so` files load directly through the existing bionic loader path — and is the only route that makes ARM-only Unity/Unreal games runnable, since Play mandates arm64-v8a while x86_64 slices are optional and increasingly absent.

### Cited Findings
- Play's 64-bit requirement "centers on arm64-v8a with x86_64 alongside 32-bit slices" — [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)
- Unreal "requires 64-bit Arm on Android 8 plus with OpenGL ES 3.2 and Vulkan 1.1 on compatible drivers" — [Unreal Android requirements](https://dev.epicgames.com/documentation/en-us/unreal-engine/android-development-requirements-for-unreal-engine), via [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)
- Unity supports Android 8.0+ with Vulkan and GLES 3.1/3.2 — [Unity docs](https://docs.unity3d.com/Manual/android-requirements-and-compatibility.html), via [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)
- The final-phase plan "prefers x86_64 native slices where APKs carry them" and requires "the ARM-translation question on x86_64 must be answered explicitly before broad ARM integration proceeds" — [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)
- The program roadmap schedules "N7 native ARM64 hosts" ahead of "N8 QEMU guest, N9 FEX and Box64 compare" — [PROGRESS.md](../../PROGRESS.md)

### Inferences
- On an ARM64 host the runner's existing native path (bionic loader, exec runner, already proven for Puzzles' generator subprocesses) extends to game `.so` files with no new translation layer: the work shifts to GPU drivers (host Vulkan for ANGLE/Venus-class forwarding) rather than CPU emulation.
- The runnable set this unlocks is exactly the plan's end goal: ARM-only Unity/Unreal/Godot-plugin games that ship no x86_64 slice — the segment QEMU-user can serve only whole-process at software-rendered speed, and FEX/Box64 cannot serve at all.
- Sequencing implication: N7 (native ARM64 host) strictly dominates N8/N9 (emulation) for the stated consumer; emulation only matters for developers stuck on x86_64 workstations, never for shipped compatibility.

### Gaps
- Current (2026) ARM64 Linux host hardware/price state (Snapdragon X Linux support, Ampere/Raspberry Pi 5-class boards, Asahi/Apple Silicon) was not researched — no sourced recommendation on which N7 host to buy.
- Whether the runner's x86_64 JVM/host code itself ports cleanly to AArch64 (JNI, loader, seccomp filters) was not assessed.

## Sane entry gate: the single binary proof

### Takeaway
The plan already names the gate: one native ARM binary executing correctly on hardware before any broad ARM integration — run it on a real ARM64 host (N7), not under emulation, so the proof tests the shipping configuration rather than a translator.

### Cited Findings
- "F3 ARM native proof: required before any broad ARM integration. Proof is one native binary executing correctly on hardware. Anti-sprawl gate, not a feature." — [PROGRESS.md](../../PROGRESS.md)
- "the standing rule that native proof means one binary executing correctly on hardware before any sprawl" — [Play Store app compatibility plan](../../reports/Play%20Store%20app%20compatibility%20plan.md)
- Puzzles' generator subprocesses already "run under the bionic loader through the installed exec runner" under confinement — the in-house shape of such a proof — [PROGRESS.md](../../PROGRESS.md)

### Inferences
- The cheapest honest proof is an ARM64 NDK `.so` with a JNI entry point loaded by the existing runner on an ARM64 host, asserting correct return values across the JNI boundary plus one GLES context creation against the host driver. If that passes, N7 investment (host hardware, driver work) is justified; if the projectinstead proves qemu-aarch64 first, it proves a configuration it will never ship.
- Explicitly out of gate scope: FEX/Box64 evaluation on x86_64 (wrong direction — no proof possible), whole-guest VMs (excluded consumer), and any benchmark target (correctness first; speed gates belong to the later graphics phase).

### Gaps
- None on the gate definition itself; the open question is calendar (when N7 opens), which belongs to the roadmap, not this note.
