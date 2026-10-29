# ARM-native execution: AArch64 Android .so as native Linux processes

## Bionic linker's needs on an ARM64 glibc host

### Takeaway
The stock bionic linker cannot simply `dlopen()` an Android .so from a glibc process: it assumes it owns TLS (via `tpidr_el0`), signal plumbing, and symbol resolution, so a host loader must interpose host libc symbols, give bionic code its own thread-control block, and map LOAD segments at bionic-expected alignment.

### Cited Findings
- The bionic `ElfReader` records `max_align_`/`min_align_` from `p_align` of every LOAD segment and computes the mapping around the runtime `page_size()` (`kPageSize`), reserving address space and loading segments itself rather than delegating to the host loader — [linker_phdr.cpp, AOSP bionic mirror](https://github.com/aosp-mirror/platform_bionic/blob/main/linker/linker_phdr.cpp)
- Bionic thread-local storage is reached through the bionic TCB: the linker resolves TLS symbols via `__get_bionic_tcb()` against the process `static_tls_layout`, i.e. it expects `tpidr_el0` to point at a bionic-layout TCB, which a glibc-owned thread does not provide — [linker.cpp, AOSP bionic mirror](https://github.com/aosp-mirror/platform_bionic/blob/main/linker/linker.cpp)
- Bionic reserves private real-time signals and installs per-process handlers with `sigaction`/`tgkill` (see `BIONIC_SIGNAL_RUN_ON_ALL_THREADS` handling in `pthread_internal.cpp`), so any host process sharing an address space with bionic code must arbitrate signal numbers and handlers with glibc and the JVM — [pthread_internal.cpp, AOSP bionic mirror](https://github.com/aosp-mirror/platform_bionic/blob/main/libc/bionic/pthread_internal.cpp)
- Android's supported native ABI surface is ELF, little-endian, with `arm64-v8a` = AArch64/Armv8.0 only; the NDK documents ELF as the mandatory binary format — [Android NDK ABIs guide](https://developer.android.com/ndk/guides/abis)
- libhybris's `hooks.c` (244 `hybris_hook` references) exists precisely because bionic libc symbols (pthread, mutex/cond, dlsym, etc.) must be redirected to host glibc equivalents with ABI translation (e.g. Android vs glibc `pthread_mutex_t` layouts, futex-based cond emulation, `pthread_create` wrappers that avoid direct TLS handling) — [hooks.c, libhybris](https://github.com/libhybris/libhybris/blob/master/hybris/common/hooks.c)

### Inferences
- A JVM-runner loader has three separable jobs: (a) an ELF mapper that honors `p_align`/RELRO like `ElfReader::LoadSegments`; (b) a TLS bridge that installs a bionic TCB on every thread entering game code (likely by switching `tpidr_el0` on entry/exit); (c) a symbol interposition table for every libc symbol the game .so imports, in the libhybris style.
- Threads created by the game (pthread_create inside bionic code) vs threads created by the JVM will have different TCBs; entry-point trampolines must normalize this.

### Gaps
- No reliable source found for the exact `tpidr_el0` layout offsets (`offset_bionic_tcb`) across NDK revisions; must be read from the pinned NDK's `libc/private/bionic_tls.h`.
- Whether stock `linker64` can run unmodified as a host executable (it expects `/system` properties, `kPmdSize` assumptions) vs needing a fork: unresolved from sources.

## Page-size direction: 4K binaries vs 16K kernels and the reverse

### Takeaway
4K-built binaries fail on 16K kernels; 16K-built binaries run on 4K kernels (with some memory waste). Only the small-alignment-on-large-page direction is fatal, and it fails at load time in the linker.

### Cited Findings
- On a 16 KB system the bionic linker hard-rejects under-aligned libraries: `"program alignment (%zu) cannot be smaller than system page size (%zu)"` and returns false from `LoadSegments` unless the 16 KiB app-compat path is active — [linker_phdr.cpp `LoadSegments`](https://github.com/aosp-mirror/platform_bionic/blob/main/linker/linker_phdr.cpp)
- Google Play requires all apps targeting Android 15 (API 35)+ to support 16 KB pages on 64-bit devices, with updates blocked from Feb 1, 2027 without it; un-rebuilt apps "won't work on 16 KB devices" — [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- The required build change is `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` (default in NDK r28+); a misaligned GNU_RELRO (start+size not 16K-multiple inside a shared LOAD segment) segfaults at `mprotect` time because the linker write-protects a range overlapping writable data — [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- Bionic's escape hatch is twofold and both are Android-only: a per-ELF 16 KiB app-compat mode (loads with 4K `seg_align`, initial mapping RW, compat RELRO handling, gated on `bionic.linker.16kb.app_compat.enabled` / package-manager backcompat mode) and kernel "page size migration" via `/sys/kernel/mm/pgsize_migration/enabled` plus `NT_ANDROID_TYPE_PAD_SEGMENT` notes — neither exists on a stock glibc host kernel — [linker_phdr.cpp](https://github.com/aosp-mirror/platform_bionic/blob/main/linker/linker_phdr.cpp)
- Over-alignment is benign: the loader aligns *up* (`__builtin_align_up`, `mapping_align`), and 16K-aligned images carry `p_align=0x4000` LOADs that map cleanly on 4K pages; Google's guidance treats rebuilding with 16K alignment as the compatible-default direction (AGP 8.5.1+/NDK r28 output runs on both) — [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes); [linker_phdr.cpp](https://github.com/aosp-mirror/platform_bionic/blob/main/linker/linker_phdr.cpp)

### Inferences
- For the isakai JVM runner: mandate 16K-aligned game .so files (check with `llvm-readelf -Wl` / `check_elf_alignment.sh` at ingest) and implement the strict path only — do not reimplement Android's 16K backcompat mode on the host. Legacy 4K-only game binaries are then a documented reject-at-load case, matching Play's own direction.
- The reverse waste (16K-aligned .so on 4K host) costs resident memory per segment tail page; negligible for one game .so.

### Gaps
- No source quantified the RSS cost of 16K-aligned libraries on 4K kernels for large game .so files; expected small but unmeasured.
- Whether any shipping game .so files still lack 16K alignment in 2026 (past the Play deadline) is unknown; ingest-time measurement needed.

## Known projects and their hard lessons

### Takeaway
Nobody loads production Android app .so files directly into glibc processes except libhybris (for vendor HALs); full-app runners all retreat to containers with a real bionic userland. The lessons are: interpose everything, translate threading primitives, attach every native thread to the VM, and expect crash-handler conflicts.

### Cited Findings
- libhybris is explicitly "a way to load drivers compiled for Android from regular linux processes", i.e. bionic-linked .so files inside glibc/musl processes, by hooking bionic libc calls into host equivalents — [libhybris README](https://github.com/libhybris/libhybris/blob/master/README.md)
- libhybris's scope warning transfers directly: even for drivers, audio/camera/etc. needed per-subsystem bridge wrappers (e.g. `pulseaudio-modules-droid`), and since Android 8 most services moved to binder IPC (`libgbinder`) rather than direct .so loading — [libhybris README](https://github.com/libhybris/libhybris/blob/master/README.md)
- Waydroid, the current full-Android-app runner on desktop Linux, does not load .so files into host processes at all: it boots a full (LineageOS-based) Android system in Linux namespaces (user, pid, uts, net, mount, ipc) with direct hardware access — [Waydroid README](https://github.com/waydroid/waydroid)
- JNI rule that bites every native bridge: a thread started with `pthread_create`/`std::thread` has no `JNIEnv` and cannot make JNI calls until attached via `AttachCurrentThread()`/`AttachCurrentThreadAsDaemon()`; `JNIEnv` cannot be shared between threads — [JNI tips](https://developer.android.com/training/articles/perf-jni)
- Android's linker enforces VNDK-style link namespaces isolating vendor/system symbol resolution; a host loader that dumps bionic and glibc symbols into one namespace will reproduce exactly the collisions namespaces were built to prevent — [NDK stable APIs / namespace context](https://developer.android.com/ndk/guides/stable_apis)

### Inferences
- Container-vs-direct-load tradeoff for isakai: Waydroid proves containers work but cost a full Android image and per-app windows through a foreign compositor; direct-load (libhybris-style) is the only path consistent with a JVM runner process, but inherits the full hooking burden for every libc symbol the game imports.
- Concrete hard lessons to budget for: (1) symbol collisions — game .so `dlsym`/link order must resolve against bionic-provided symbols first, glibc second, never merged; (2) every game-created thread that calls back into Java must be attached to the JVM (and detached on exit) or JNI calls crash; (3) bionic's reserved-signal use and the JVM's own signal handlers (SIGSEGV/SIGBUS for implicit null checks) will collide — one owner must chain to the other.
- libhybris's pthread translation layer (mutex/cond/rwlock layout translation, `pthread_create` wrappers) is the closest prior art to reuse or port for the game-.so case.

### Gaps
- No public source found documenting a project that loads *app-level* (non-HAL) Android .so files with JNI into a host JVM at production quality; isakai's runner would be novel here.
- Signal-chaining specifics between bionic `BIONIC_SIGNAL_*` handlers and HotSpot's handlers: not found in sources; needs empirical work on the ARM64 host.

## Host kernel and distro features that matter

### Takeaway
The host kernel's page size is the single gating feature; MTE/HWASAN and execute-protection details are secondary but can crash unprepared loaders. "Exec-shield" as such is an x86 concept and irrelevant on ARM64.

### Cited Findings
- arm64 Linux supports three page sizes selected at kernel build time: 4K, 16K, 64K (`ARM64_4K_PAGES` / `ARM64_16K_PAGES` / `ARM64_64K_PAGES` in Kconfig, affecting VA bits, mmap randomization, and PMD sizing) — a distro kernel's choice fixes the minimum LOAD alignment for every native mapping — [arm64 Kconfig, torvalds/linux](https://github.com/torvalds/linux/blob/master/arch/arm64/Kconfig)
- The running page size is observable via `getconf PAGE_SIZE` (Android's own 16K test procedure uses `adb shell getconf PAGE_SIZE` expecting 16384) — the runner should assert this at startup and refuse or warn on mismatch — [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)
- MTE tag-check faults are per-thread, opt-in via `prctl(PR_SET_TAGGED_ADDR_CTRL, …)` with SYNC/ASYNC/ASYMM modes; SYNC raises synchronous `SIGSEGV` (`SEGV_MTESERR`), ASYNC raises asynchronous `SIGSEGV` (`SEGV_MTEAERR`, fault address unknown) — a game built with HWASAN tagging running on an MTE host kernel will deliver SIGSEGVs the runner/JVM must not misinterpret as ordinary crashes — [MTE in AArch64 Linux, kernel docs](https://www.kernel.org/doc/html/latest/arch/arm64/memory-tagging-extension.html)
- Stock bionic `linker_phdr.cpp` already handles Armv8.5-A `PROT_BTI` (branch target identification) on executable segments via GNU properties; an ARM64 host loader must preserve `PT_GNU_PROPERTY`/BTI and PAC bits when re-mapping segments or JIT-adjacent game code faults — [linker_phdr.cpp `Load`](https://github.com/aosp-mirror/platform_bionic/blob/main/linker/linker_phdr.cpp)

### Inferences
- Distro checklist for the ARM64 host: (a) 4K-page kernel (still the norm; verify, don't assume); (b) MTE disabled by default (it is — opt-in per thread) but detect MTE-capable CPUs and never enable tag checks process-wide since the JVM is not MTE-clean; (c) no exec-shield equivalent to configure — rely on standard NX via `mprotect`/`PT_GNU_STACK`, plus BTI/PAC preservation.
- 64K-page host kernels (some server distros) would reject even 16K-built game binaries by the same `min_align_ < kPageSize` rule; treat 64K hosts as unsupported unless game .so files are rebuilt with 64K alignment.

### Gaps
- Which 2026 distros ship 16K-page ARM64 kernels by default (Ubuntu's 2025-era 16K push, RHEL) was not verified; needs a per-distro survey before promising Play-binary compatibility.
- HWASAN-instrumented game builds additionally require the tagged-address ABI and allocator support on the host; no source surveyed covers running HWASAN Android binaries outside Android.
