# Dependency and reuse ledger

Owned by [N4](N4-upstream-pins.md). Every component Isakai builds, imports, ships, tests
against, or inspects for reuse is listed here with an immutable pin, its
license/terms, and its integration status. Hashes live in the authoritative
lock files linked below; this ledger adds the terms, obligations, ownership and
honest integration status those locks omit. No component is marked built or
integrated on source inspection alone.

## Integration status legend

- **built** — fetched/compiled and used by the build or harness; pinned by hash.
- **test-input** — an external artifact driven as a fixture; pinned by hash; not redistributed.
- **inspected-only** — read for reuse evaluation (evidence grade E3); never built, linked or integrated. A later task must build it before any "reused" claim.
- **host-provided** — a system dependency the operator installs; documented in [host requirements](../docs/HOST_REQUIREMENTS.md), not vendored.

## This project

| Component | Pin | License | Notes |
|---|---|---|---|
| Isakai | this repository | GPL-3.0-or-later (SPDX headers; [LICENSE](../LICENSE)) | Some runtime/framework files are authored to mirror AOSP APIs; see per-file SPDX headers. [LICENSES/Apache-2.0.txt](../LICENSES/Apache-2.0.txt) is retained for Apache-licensed reference material. |

## Built — toolchain and fixture

Pinned by sha256 in lock files; the build/bootstrap fails closed on a checksum mismatch.

| Component | Version / revision | Pin source | License (identify) | Status |
|---|---|---|---|---|
| Zig | 0.17.0 | [tools/dependencies.json](../tools/dependencies.json) (sha256) | MIT | built |
| Temurin JDK | 17.0.20.1+1 | [tools/dependencies.json](../tools/dependencies.json) (sha256) | GPLv2 with Classpath Exception | built |
| sway | `verify-isakai/compositor/DEPS.lock` sha256 | [DEPS.lock](../verify-isakai/compositor/DEPS.lock) | MIT | built (verification fixture) |
| wlroots | 0.20 — `DEPS.lock` sha256 | [DEPS.lock](../verify-isakai/compositor/DEPS.lock) | MIT | built (verification fixture) |
| libliftoff | `DEPS.lock` sha256 | [DEPS.lock](../verify-isakai/compositor/DEPS.lock) | MIT | built (verification fixture) |

Isakai's own compositor helpers (`verify-isakai/compositor/*.c`) and the DEX→JVM
translator (`src/translate.zig`) are first-party, not vendored.

## Built — resource engine ([N5](N5-resource-engine.md))

Pinned by Zig package content hash in [build.zig.zon](../build.zig.zon); `zig
build` fetches each GitLab path archive once into `zig-pkg/` and fails closed
on a hash mismatch. Compiled statically into `libisakai_jni.so` by
`androidfwLibrary` in [build.zig](../build.zig); the only entry point is
`src/native/res_engine.cpp`.

| Component | Revision / content hash | License (identify) | Status |
|---|---|---|---|
| libandroidfw (ART Standalone `libandroidfw/`) | `66a5d9079b159e9c5819bfeef4a6fff6a5f32719` / `N-V-__8AAJq3LQBOFNVVRy0nI5tQtSIkdlemYgtOKt9iqC83` | Apache-2.0 (`MODULE_LICENSE_APACHE2`, `NOTICE` in the tree) | built |
| libbase (`libbase/`) | same revision / `N-V-__8AAKVtBwB6plbXFq4d0bBHK9l_mAgL2R95nQPpebgR` | Apache-2.0 (AOSP) | built |
| libziparchive (`libziparchive/`) | same revision / `N-V-__8AAPebYQDPq46lkLUvxJUqHlFLAyog52d_ATcmLOv6` | Apache-2.0 (AOSP) | built |
| incfs-utils (`incfs/`) | same revision / `N-V-__8AAKlUAABhNriciDiZp_CntCVV6nVmmXIzauSe4Ic3` | Apache-2.0 (AOSP) | built (`util/map_ptr.cpp` only) |
| libutils, libcutils (`system/core/`) | same revision / `N-V-__8AADCJLQCyctjMhQSjYrUrlM5fiGc9b4KHy1BteGAk` | Apache-2.0 (`system/core/NOTICE`) | built (libutils + binder subset; libcutils `native_handle.c`, `trace-host.c`); upstream liblog replaced by `src/native/android_log.zig` |
| framework-res.apk (ATL `res/framework-res/framework-res.apk`) | `07c34abad5d29864998ed7c7cfc6c80d0765b9df` / `N-V-__8AAMmgPQBV9065uEPOgvgeFH07MiJRYhvYIqN0MQwU` (file sha256 `f12806d89c4277a899a701c4c9ae8b630235a745b3ff9b81293aae3e2a13506e`) | Apache-2.0 (AOSP framework resources; `res/framework-res/NOTICE`) | built (installed as `framework/framework-res.apk`, loaded as the system package) |

ATL's own C API (`androidfw_c_api.cpp`) is not built: it has no AssetManager
or ApkAssets delete. PNG crunching (`Png*.cpp`) and the rest of libcutils are
aapt-only and not built.

## Test-input — pinned APKs

Driven as verification fixtures, pinned by sha256 in [APPS.md](APPS.md); never redistributed.

| App | Version | License (identify) | Status |
|---|---|---|---|
| OpenCalc (`com.darkempire78.opencalculator`) | v3.2.1 | GPL-3.0 | test-input (pinned in [APPS.md](APPS.md)) |
| Simple Calculator (`com.simplemobiletools.calculator`) | 5.12.0 | GPL-3.0 | test-input (pinned in [APPS.md](APPS.md)) |
| Simple Notes (`com.simplemobiletools.notes.pro`) | 6.17.0 | GPL-3.0 | test-input (pinned in [APPS.md](APPS.md)) |
| F-Droid Basic (`org.fdroid.basic`) | 2.0.2 (versionCode 2000052) | GPL-3.0 | test-input for [N2](N2-abi-selection.md): a real 4-ABI (arm64-v8a/armeabi-v7a/x86/x86_64) native APK. sha256 `bbc4a34a7f65b200f9e40c4c74af4745de5cdbff45752ac0172916deb9f3c97d`, from the official F-Droid repo (`https://f-droid.org/repo/org.fdroid.basic_2000052.apk`). Not a target app; drives the ABI-selection and ELF-inventory paths. |

## Experimental Bionic integration

Bionic Translation `6253dec7f24a57a94039a088cb436d880e92e3be` has a standalone
Meson build and development loader/JNI integration recorded in [N6](N6-bionic-jni.md).
`build.zig` can copy externally built artifacts from `-Dbionic-dir` or its
local cache default. This is not a reproducible source-built dependency in
`build.zig.zon`, and a matching checkout alone does not establish artifact
provenance. Confirm component and transitive licenses, capture build inputs,
configuration and output hashes, and make backend availability explicit before
distribution. Same-UID development evidence is not default-isolation acceptance.

## Inspected-only — reuse research candidates

Pinned by immutable revision in [PLAN.md](PLAN.md). Read for reuse evaluation (E3). **None of these rows is built, linked or integrated** (the resource-engine subset is listed under Built above). A later milestone (M2 N3/N5/N6, M4 experiments) must build the chosen one against its own recipe before any "reused" claim; until then these are references, not dependencies.

| Candidate | Pinned revision / tag | License (identify) | Reuse intent |
|---|---|---|---|
| Android Translation Layer (ATL) | `07c34abad5d29864998ed7c7cfc6c80d0765b9df` | GPL-3.0 (`LICENSE.txt` confirmed) | Build ordering, WebView reference; its Apache-2.0 `framework-res.apk` is built (above) |
| ART Standalone (rest of the tree) | `66a5d9079b159e9c5819bfeef4a6fff6a5f32719` | Apache-2.0 (AOSP) | JNI loader (N6/N11); its libandroidfw closure is built (above) |
| Robolectric | `4.17` | confirm upstream LICENSE | Backend bakeoff oracle (N12) |
| dex2jar | `v2.4` (2023-10-03) | Apache-2.0 | Parity reference only (`src/translate.zig` comments) and N12 bakeoff candidate; Isakai ships its own DEX→JVM translator and does not build or fetch dex2jar |
| killerdevildog ATL CMake wrapper | `4ed75fbf108c5593a65b6ee1295d5445c935aaf4` | confirm upstream | Build-order reference only; hardcodes amd64/lib64 and masks failures — do not reuse as-is |

Digitalis (N10) and its subproject/manifest hashes are to be captured before that experiment, per PLAN.md.

## Host-provided

System dependencies the operator installs; documented in [host requirements](../docs/HOST_REQUIREMENTS.md), not vendored: a Wayland compositor (Hyprland or sway), `wayland-scanner`, `gcc`, libwayland-client, libxkbcommon, polkit (`pkexec`), `setfacl`/`getfacl`, `grim`, `wtype`, `hyprctl`.

## Maintenance ownership and update policy

- Single maintainer owns this ledger and the pins. Every pin is an immutable revision or sha256, never a mutable branch head.
- Toolchain and fixture pins change only by editing the lock file and recording the new version + sha256; the bootstrap/build verifies the checksum and fails closed on mismatch.
- Licenses are re-identified on every version bump. A dependency whose redistribution terms are unknown, whose source is unpinned, or whose build masks failures blocks distribution (N4 stop condition); unrelated experiments may continue.
- Research candidates are re-pinned, not followed at head; promoting one from inspected-only to built requires a tracked task, its own build recipe, and a dependency-graph capture.

## Gaps (open, tracked here)

- **Some upstream licenses are identified but not yet confirmed against the upstream LICENSE file** (Bionic Translation, Robolectric, the CMake wrapper). Bionic is experimentally built and integrated as described above, not inspected-only. Its unconfirmed license closure and external build provenance block distribution of that backend. Robolectric and the CMake wrapper remain inspected-only; confirm their terms before adoption.
- **An earlier Q1 note described a dex2jar bootstrap.** The current tree does not fetch or build dex2jar (`tools/dependencies.json` holds only Zig and JDK; `src/translate.zig` is first-party and references dex2jar only for behavioral parity). dex2jar is therefore ledgered as inspected-only, not built. If a future task bootstraps it as an oracle, add its sha256 to a hash lock and move it to the built table.
