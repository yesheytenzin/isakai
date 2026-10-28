# Bionic Loader Extension Contracts and NDK Asset fd Access

## android_dlopen_ext: what android_dlextinfo carries, what a single-namespace loader must honor vs safely ignore

### Takeaway
`android_dlextinfo` carries 7 fields gated by a `flags` bitmask; the loader validates unknown flag bits and two bad combinations (OFFSET-without-FD, NAMESPACE-with-null-namespace) and fails the load, treats NULL `extinfo` as plain `dlopen`, and a single-namespace shim can safely ignore-or-reject the WebView-only RELRO/reserved-address flags but must explicitly handle (honor or loudly reject) `USE_LIBRARY_FD`, `USE_LIBRARY_FD_OFFSET`, `FORCE_LOAD`, and `USE_NAMESPACE`.

### Cited Findings
- `android_dlextinfo` fields are `flags` (uint64_t bitmask), `reserved_addr`/`reserved_size`, `relro_fd`, `library_fd`/`library_fd_offset`, `library_namespace` — [NDK android_dlextinfo reference](https://developer.android.com/ndk/reference/structandroid/dlextinfo); full header with identical comments at `libc/include/android/dlext.h` — [bionic dlext.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/android/dlext.h)
- `android_dlopen_ext(filename, flags, info)`: "`__filename` and `__flags` arguments are the same as for dlopen(3), with the Android-specific flags supplied via the `flags` member of `__info`", and `__info` is `_Nullable` (NULL is legal) — [NDK libdl reference](https://developer.android.com/ndk/reference/group/libdl)
- Flag values: `RESERVED_ADDRESS 0x1`, `RESERVED_ADDRESS_HINT 0x2`, `WRITE_RELRO 0x4`, `USE_RELRO 0x8`, `USE_LIBRARY_FD 0x10`, `USE_LIBRARY_FD_OFFSET 0x20`, `FORCE_LOAD 0x40`, `USE_NAMESPACE 0x200`, `RESERVED_ADDRESS_RECURSIVE 0x400`, plus `VALID_FLAG_BITS` mask (retired 0x80/0x100 ART bits must NOT be accepted) — [bionic dlext.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/android/dlext.h)
- Unknown flag bits fail the load: `if ((extinfo->flags & ~(ANDROID_DLEXT_VALID_FLAG_BITS)) != 0) { DL_OPEN_ERR("invalid extended flags..."); return nullptr; }` — [bionic linker.cpp do_dlopen](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp)
- `USE_LIBRARY_FD_OFFSET` without `USE_LIBRARY_FD` fails: `DL_OPEN_ERR("invalid extended flag combination ...")` — [bionic linker.cpp do_dlopen](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp)
- `USE_NAMESPACE` with null `library_namespace` fails: `DL_OPEN_ERR("ANDROID_DLEXT_USE_NAMESPACE is set but extinfo->library_namespace is null")`; otherwise the target namespace replaces the caller namespace (`ns = extinfo->library_namespace`) — [bionic linker.cpp do_dlopen](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp)
- `USE_LIBRARY_FD`: "Use `library_fd` instead of opening the file by name. The filename parameter is still used to identify the library"; `USE_LIBRARY_FD_OFFSET`: "read it starting at `library_fd_offset`... mainly useful for loading a library stored within another file (such as uncompressed inside a ZIP archive)" — [bionic dlext.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/android/dlext.h)
- `FORCE_LOAD`: "do not use `stat(2)` to check if the library has already been loaded... multiple ELF files share the same filename" — [bionic dlext.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/android/dlext.h)
- `USE_NAMESPACE`: "This flag is for internal use only (since there is no NDK API for namespaces)"; RELRO/reserved flags are all documented "mainly useful for the system WebView implementation" — [bionic dlext.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/android/dlext.h)
- Without extinfo the linker calls `dlopen_ext(filename, flags, nullptr, caller_addr)` — NULL extinfo is the plain-dlopen path, and the default namespace is the caller's (`get_caller_namespace(caller)`) — [bionic dlfcn.cpp](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/dlfcn.cpp)
- `dlopen` flags themselves are validated: anything outside `RTLD_NOW|RTLD_LAZY|RTLD_LOCAL|RTLD_GLOBAL|RTLD_NODELETE|RTLD_NOLOAD` fails with "invalid flags to dlopen" — [bionic linker.cpp do_dlopen](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp)

### Inferences
- Current shim (`android_dlopen_ext` in `src/native/bionic_shim.zig`) ignores `extinfo` entirely and forwards to `dlopen`; the minimal correct single-namespace contract is: NULL extinfo → plain forward; else validate `flags & ~VALID_FLAG_BITS` → fail, OFFSET-without-FD → fail, any RELRO/reserved-address bits → fail loudly (or ignore only if proven no app/game lib uses them — failing is safer and matches platform for unserviceable requests), `USE_NAMESPACE` → fail loudly (no namespaces exist in the shim), `USE_LIBRARY_FD`/`FORCE_LOAD` → either forward to a loader that honors them or fail loudly, never silently ignore.
- `RESERVED_ADDRESS_RECURSIVE` only modifies reserved/RELRO propagation to DT_NEEDED deps, so it is safely rejected alongside the reserved/RELRO bits in a single-namespace design.
- Single-namespace simplification (explicitly marked): platform resolves missing extinfo-namespace to the *caller's* namespace; the shim has exactly one namespace, so "caller namespace" == "the bionic loader" always — no `library_namespace` handling beyond rejecting the flag.

### Gaps
- Whether any real game/JNI lib in isakai's scope actually passes non-NULL extinfo (e.g.FMOD/Wwise-style plugins, Unity) — needs a grep over the pinned app libs, not established here.
- Exact numeric values of `RTLD_NODELETE`/`RTLD_NOLOAD` on bionic vs glibc for flag-forwarding fidelity — not checked; the shim forwards `flags` verbatim to both loaders today.

## dlerror: who owns the error string, thread-locality, when it must be set/cleared on guest symbol lookup failure

### Takeaway
The error string is owned by the loader in a per-thread 512-byte buffer the caller must not free; every failing `dlopen`/`dlsym`/`dlclose` overwrites it, success leaves it untouched, and `dlerror()` atomically returns the pending pointer and clears it (second consecutive call returns NULL). Guest `dlsym` failure must therefore write the shim's thread-local buffer; guest success must not clear a pending host error.

### Cited Findings
- "`dlerror(3)` returns a human-readable error message describing the most recent failure from one of the `<dlfcn.h>` functions on the calling thread. This function also clears the error, so a second call (or a call before any failure) will return NULL" — [bionic dlfcn.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/dlfcn.h)
- Storage is per-thread: `char* current_dlerror` + `char dlerror_buffer[512]` (`__BIONIC_DLERROR_BUFFER_SIZE 512`) live in the thread's `bionic_tls`/`pthread_internal` struct — [bionic pthread_internal.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/bionic/pthread_internal.h)
- Set path: `__bionic_format_dlerror(msg, detail)` copies into the thread's `dlerror_buffer` and points `current_dlerror` at it; read path: `__loader_dlerror()` does `__bionic_set_dlerror(nullptr)` and returns the old pointer — i.e. read-and-clear in one step — [bionic dlfcn.cpp](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/dlfcn.cpp)
- Failure-only writes: `dlopen_ext` formats the error only `if (result == nullptr)`; `dlsym_impl` formats only on `do_dlsym` failure and returns the symbol otherwise; `__loader_dlclose` formats only `if (result != 0)` — success never touches the buffer, so a stale error survives a later success until `dlerror()` is called — [bionic dlfcn.cpp](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/dlfcn.cpp)
- `dlsym` failure modes that must set the error: null symbol name, invalid handle, symbol not found, TLS symbol in solib with no TLS segment — [bionic linker.cpp do_dlsym](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp)
- `dladdr` explicitly does NOT feed `dlerror`: "unlike the other `<dlfcn.h>` functions, in this case dlerror() will _not_ have more information" — [bionic dlfcn.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/dlfcn.h)
- glibc/POSIX parity note: glibc `dlerror` is likewise per-thread (TLS), returns a pointer the caller must not free, and is cleared on read — the shim's `threadlocal var err_buf: [512]u8` in `src/native/bionic_shim.zig` already mirrors the 512-byte size, but its `dlerror()` falls through to `real_dlerror()` when its own buffer is empty, which is correct only if guest success never clears and guest failure always writes.

### Inferences
- Mapping onto `src/native/bionic_shim.zig`: `dlsym` on a tracked (bionic) handle must `setError` from `bionic_dlerror` on failure (it does) and must NOT call `clearError` on the host-error path when the guest lookup was never attempted; conversely guest success should `clearError` only if the error being cleared is the guest's own — current code clears unconditionally on any success, which can swallow a pending host error that platform semantics would preserve. Whether that matters depends on callers calling `dlerror` strictly after a NULL return (normal JNI/loader usage), where behavior is already correct.
- The returned pointer must stay valid until the next failing call on the same thread: the shim's `threadlocal err_buf` satisfies this; it must never return a pointer into a freed/temporary buffer.

### Gaps
- Could not verify from NDK docs (Context7 NDK reference has no `dlerror` page) whether NDK documents the "success leaves stale error" rule — the finding rests on bionic source + `dlfcn.h` header comments, which are authoritative platform behavior but not NDK prose.
- Exact `do_dlsym` "undefined symbol" message text for guest-lookup failure parity — not captured; only the mechanism (buffer write on failure) is established.

## AAssetManager.openFd: when it returns a real fd+offset+length (Stored vs Compressed), exact failure modes

### Takeaway
`AAsset_openFileDescriptor[64]` returns a real fd plus file offset/length **only** for uncompressed (`Stored`) entries backed by `_FileAsset`; for `Deflated` entries (`_CompressedAsset`) `openFileDescriptor` unconditionally returns −1, and the NDK contract is "returns < 0 if direct fd access is not possible (for example, if the asset is compressed)".

### Cited Findings
- "Open a new file descriptor that can be used to read the asset data... Returns < 0 if direct fd access is not possible (for example, if the asset is compressed)" — [NDK AAsset_openFileDescriptor](https://developer.android.com/ndk/reference/group/asset); 64-bit variant truncates nothing and is required for large files — [NDK asset group summary](https://developer.android.com/ndk/reference/group/asset)
- `_FileAsset::openFileDescriptor` success paths: (a) mapped asset with owned fd → `dup(mFd)`, `*outStart = mMap->offset()`, `*outLength = mMap->length()`; (b) mapped without fd → `open(fname, O_RDONLY)` with map offset/length; (c) plain file asset → `open(mFileName, O_RDONLY)` with `mStart`/`mLength`; returns −1 when `mFileName == NULL` or `dup`/`open` fails — [frameworks/base Asset.cpp](https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-22.1/libs/androidfw/Asset.cpp)
- `_CompressedAsset::openFileDescriptor` is a one-liner: `virtual int openFileDescriptor(...) const { return -1; }` — compressed entries can never yield an fd — [frameworks/base Asset.h](https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-22.1/libs/androidfw/include/androidfw/Asset.h)
- Base-class contract comment: `openFileDescriptor` is pure virtual on `Asset` with the doc noting the compressed case ("asset is compressed") — [frameworks/base Asset.h](https://raw.githubusercontent.com/LineageOS/android_frameworks_base/lineage-22.1/libs/androidfw/include/androidfw/Asset.h)
- `isakai_asset_fd` (res_engine.cpp:331) already forwards to `asset->openFileDescriptor(&s,&l)` and returns −1 on `fd < 0`, and `isakai_asset_range` reuses the same call, closing the probe fd — correct shape for the Stored-only contract — [repo src/native/res_engine.cpp]
- The Zig bridge (`AAsset_openFileDescriptor64` in `src/bridge/resources.zig`) null-checks asset/start/length and forwards to `isakai_asset_fd`; the 32-bit variant truncates via `@intCast` — matches the NDK "truncated to 32-bit" note.

### Inferences
- No bridge change is needed for the Stored-vs-Compressed rule itself: libandroidfw enforces it and the bridge propagates −1. The remaining N18 work is (a) confirming `resources.zig` callers treat `fd < 0` as "fall back to stream reads" rather than as a fatal error, and (b) confirming `isakai_asset_range`'s open-then-close probe cannot leak the fd on the error path (it returns before `close` only when `fd < 0`, so no leak — verified by reading the code).
- Which concrete assets are Stored vs Deflated is an aapt packaging decision (`noCompress` extensions, `.mp3`-style rules, `aaptOptions`), not an NDK runtime decision — inference, not verified against aapt source here. Tests must therefore cover both: a known-uncompressed entry (fd ≥ 0, usable pread at start/length) and a known-compressed entry (fd < 0).

### Gaps
- Exact `ZipFileRO` method check (`kCompressStored` vs `kCompressDeflated`) at `AssetManager2` open time that selects `_FileAsset` vs `_CompressedAsset` — not fetched; the class-level −1 vs fd behavior is established but the dispatch site is not cited.
- Whether `AAsset_openFileDescriptor` on an asset opened with `AASSET_MODE_BUFFER`/`STREAMING` vs `RANDOM` changes fd availability — no source found stating a mode dependence; assumed mode-independent (gap noted, likely no dependence since fd path keys off compression, not mode).

## Host-vs-guest dlopen classification: correct fallback order and where the boundary must be explicit

### Takeaway
Platform bionic has no host/guest split — namespace + caller-address determine visibility, with `USE_NAMESPACE` internal-only — so the host-first-then-bionic fallback is purely an isakai single-namespace design choice; the boundary must be explicit at exactly four points: `dlopen` fallback order, handle-tagged `dlsym`/`dlclose` routing, per-loader `dlerror` attribution, and `android_dlopen_ext` validation before any fallback.

### Cited Findings
- Namespace selection on platform: `ns = get_caller_namespace(caller)` from the caller's address, overridable only by `USE_NAMESPACE`+`library_namespace`; "`This flag is for internal use only (since there is no NDK API for namespaces)`" — [bionic linker.cpp do_dlopen](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp) and [bionic dlext.h](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/libc/include/android/dlext.h)
- `dlsym` on platform resolves in the caller's namespace for `RTLD_DEFAULT`/`RTLD_NEXT` (`dlsym_linear_lookup(ns, ...)`) and in the handle's own solib otherwise (`dlsym_handle_lookup`) — routing is by handle identity, never by retrying another loader — [bionic linker.cpp do_dlsym](https://raw.githubusercontent.com/LineageOS/android_bionic/lineage-22.1/linker/linker.cpp)
- Current shim order (host-first): `dlopen` tries `real_dlopen` first and only retries `bionic_dlopen` when glibc rejects the library; `in_bionic` TLS flag prevents recursive re-entry from ATL's own host-`dlopen` fallback — [repo src/native/bionic_shim.zig]
- Handle tagging exists: `trackHandle`/`isTracked`/`untrackHandle` route `dlsym`/`dlclose` to `bionic_*` vs `real_*` — [repo src/native/bionic_shim.zig]

### Inferences
- Host-first is the right order for isakai (inference): HotSpot/JVM and glibc-built JNI libs must keep exact glibc semantics (the shim's header comment states glibc exports are never interposed), and only libraries glibc *rejects* (Android sonames `libc.so`/`libm.so`/`libdl.so`, `@LIBC` versions) can be Android DSOs — trying bionic first would risk shadowing a host lib of the same filename. This ordering has no platform counterpart and must be documented as an isakai-only rule.
- Boundary points that must stay explicit (inference from the gaps in current code): (1) `android_dlopen_ext` must validate extinfo *before* the host attempt — a `USE_NAMESPACE`/bad-flags request must fail even if the host would happily dlopen the file, otherwise host success silently violates the caller's contract; (2) `dlerror` attribution — a failed host attempt followed by a successful bionic load must clear (current code does via `clearError` on success), while a failed bionic retry must surface the *bionic* message, not the stale host one (current code overwrites via `setError` — correct); (3) `dlclose`/`dlsym` on untracked handles must never touch the bionic loader (current code honors this); (4) `RTLD_DEFAULT`/`RTLD_NEXT` dlsym from guest code searches only the namespace of the caller — the shim's flat `real_dlsym(handle,...)` forward for untracked handles approximates this for host callers but guest `RTLD_DEFAULT` lookups needing host symbols have no defined route (open design question for N6).
- Single-namespace simplification (explicitly marked): full multi-namespace support (isolated namespaces, linked-namespace search lists, `android_create_namespace`/`add_soinfo_overrides`) is out of scope; the shim's one bionic loader instance *is* the app namespace, and every namespace-taking API fails loudly rather than emulating isolation.

### Gaps
- Guest `RTLD_DEFAULT`/`RTLD_NEXT` semantics across the host/guest boundary (e.g. guest lib calling `dlsym(RTLD_DEFAULT, "malloc")`) — no platform rule covers two loaders; needs an N6 design decision, not more research.
- Whether `RTLD_NODELETE`/`RTLD_NOLOAD` forwarded to `bionic_dlopen` are honored or ignored by ATL's loader — not checked against the pinned ATL `bionic_translation` build.
