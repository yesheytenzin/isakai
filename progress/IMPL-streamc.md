# Stream C: host graphics inventory and GLES 3.2 + Vulkan 1.1 gap report

Goal in own words: survey what graphics this host offers and name exactly
what blocks serving GLES 3.2 plus Vulkan 1.1 to apps. Survey plus gap
report only. No engine integration.

Brief: `reports/Play Store app compatibility plan.md` (graphics section).
Status: survey done, no code changed.

## Host capability summary

Single host GPU. No second GPU and no fallback Vulkan driver.

- GPU: Intel UHD Graphics TigerLake-H GT1 (deviceID 0x9a68, PCI 00:02.0).
- Driver: Mesa 26.2.5-arch1.1, Intel open-source (ANV Vulkan, Iris GL).
- Vulkan: instance 1.4.363, device apiVersion 1.4.354. Vulkan 1.1 is a
  subset and is fully covered. Device extensions include
  external-memory-fd, external-memory-dma-buf, external-semaphore-fd,
  timeline-semaphore, swapchain, wayland-surface, queue-family-foreign
  (see `vk-extensions.txt`).
- GLES: 3.2 on every EGL platform probed (GBM, X11, Wayland) and on GLX.
  GL core and compat both 4.6. Raw outputs: `vulkaninfo-summary.txt`,
  `vulkaninfo-full.txt`, `glxinfo-B.txt`, `eglinfo.txt`.
- Zink: ships with Mesa (`zink_dri.so`) and renders on this host under
  `MESA_LOADER_DRIVER_OVERRIDE=zink`. Present host-side, unwired in runner.
- ANGLE: not installed. No package, no `libEGL_angle`, no binary.
- Venus: not a path here. Only the Mesa defaults conf ships; no Venus ICD
  and no virtio-gpu guest exist because the runner is a process, not a VM.
  Host-side Venus prerequisites (ext-memory fd, dma-buf) are present but
  irrelevant without a guest.
- gfxstream: host libs and headers exist only as a qemu dependency
  (`libgfxstream_backend.so`, `gfxstream-0.1.2-1`). No guest, no stream,
  nothing wired. Not a path without a guest.
- Software Vulkan fallback: absent. No Lavapipe (`libvulkan_lvp.so`
  missing), no SwiftShader. Only legacy `swrast_dri.so` for GL.

## Engine minimums mapped against inventory

Unity needs GLES 3.1 and 3.2 plus Vulkan on Android 8+. Unreal needs
GLES 3.2 plus Vulkan 1.1. The host hardware and drivers cover every one
of these APIs natively. Nothing about the host blocks either engine.
Every block sits in the runner between the app and the host.

## Ordered gap list

Gaps are ordered by dependency. Each names the file or component that
must change. Full table: `evidence/2026-10-29/streamc/gap-table.csv`.

1. No `android.opengl`, EGL, or GLES JNI bindings at all.
   `framework/android/opengl/` does not exist and `src/bridge/jni.zig`
   has no GL entry points. Every engine fails at class load before any
   GPU work starts. New framework package plus JNI entries.
2. Compositor client is CPU SHM only. `src/bridge/window.zig` presents
   XRGB8888 SHM buffers and has no EGL, dmabuf, or `wl_egl_window` path.
   Even with bindings there is nowhere to present a GL frame.
   Change `src/bridge/window.zig`.
3. No Vulkan surface wiring. Host Vulkan 1.4 is reachable only from host
   processes. No framework Vulkan package and no native VkSystem surface
   path exist. New bindings plus native wiring.
4. No ANGLE integration. Nothing installed and nothing selects it. Optional
   on this host (native GLES 3.2 already serves), needed for Vulkan-only
   devices per the plan. New packaging plus driver selection.
5. Zink present but unselected. Uses the existing Mesa file; only runner
   backend selection is new.
6. No software Vulkan fallback. Lavapipe or SwiftShader packaging plus
   runner fallback selection are new. Driverless hosts otherwise get
   nothing for GL or Vulkan apps.
7. Feature and version profile is a stub.
   `framework/android/content/pm/PackageManager.java`
   `hasSystemFeature()` returns false unconditionally, so Play filtering
   and engine feature detection cannot behave. Wire real values plus
   ConfigurationInfo GLES version.

Venus and gfxstream need no change. Both require a guest VM the
architecture does not have, so they are documented non-paths, not gaps.

## Handoff, not landed

No probe fix appeared. The smallest candidate, returning true for
`FEATURE_OPENGLES_EXTENSION_PACK` from `hasSystemFeature`, would lie to
Play filtering while gaps 1 and 2 still block every GL call, so it stays
unlanded. The honest order is bindings first, then present path, then
truthful feature flags.

## Verification evidence

- `zig build` exit 0, no-op survey with no source edits.
- `git diff --check` clean (new untracked files only, no hunks).
- No test files created. No fixture started. No sleep used.
- Raw inventory under `evidence/2026-10-29/streamc/`.

## Review

Self-review of the diff and surrounding call sites. The change set is one
new doc file plus new evidence files. No source, framework, or config
touched, so no callers change and no regression surface exists. Grep
confirms `framework/android/opengl/` absence, SHM-only present path in
`src/bridge/window.zig`, and the `return false` stub in
`PackageManager.java`. No material findings. Limitation: all verdicts are
self-review; a human with fresh eyes has not read this.

## Note on PROGRESS.md

`PROGRESS.md` says it is the single status source and forbids per-task
files. This stream was ordered to record only in
`progress/IMPL-streamc.md` and never touch shared files, which avoids
concurrent-write conflicts with parallel streams. This file therefore
carries the Stream C record and the coordinator merges what it needs.
