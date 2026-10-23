# Isolated compositor fixture (reproducible)

The pinned-app UI flows run under a private, headless Sway compositor so they
never touch the operator's desktop session. This directory holds the tracked,
reproducible source of that fixture.

## What is tracked here

- `isakai-vinput.c`, `isakai-vptr.c`, `isakai-wev.c`, `isakai-globals.c` — our
  own helpers (persistent virtual pointer + keyboard driver, globals probe,
  event watcher). These are the code the verification harness actually drives.
- `virtual-keyboard.xml`, `wlr-virtual-pointer.xml` — the Wayland protocol
  definitions the helpers bind. Headers are generated at build time with
  `wayland-scanner`, never cached.
- `sway.conf` — the headless 1000x1000 output configuration.
- `DEPS.lock` — sha256 of the external compositor packages (sway, wlroots,
  libliftoff), pinned the same way the toolchain bootstrap pins Zig/JDK/dex2jar.
- `build.sh` — assembles the fixture from the above.

## Build

```sh
verify-isakai/compositor/build.sh [OUT_DIR] [DEPS_DIR]
```

Default `OUT_DIR` is `.cache/isakai/compositor` (where the harness looks). It
generates the protocol headers, compiles the four helpers from the tracked
sources, copies `sway.conf`, then verifies and extracts the pinned compositor
packages. Host build dependencies: `wayland-scanner`, `gcc`, and the
`wayland-client` + `xkbcommon` development libraries.

## External compositor dependency

`sway`, `wlroots` (0.20) and `libliftoff` are third-party binaries, not our
code, so they are a pinned dependency rather than tracked source — the same
treatment the bootstrap gives the Zig and JDK toolchains. Place the three
`*.pkg.tar.zst` files (Arch packages) in `DEPS_DIR`; `build.sh` fails closed
unless their sha256 matches `DEPS.lock`. They can be obtained with `pacman`
(`pacman -Sw sway wlroots0.20 libliftoff`, from the package cache) or an Arch
mirror, then checksum-verified against `DEPS.lock`.

## Runtime directory

The harness runtime directory defaults to `/tmp/isakai-wayland-<uid>` and is
overridable with `ISAKAI_WAYLAND_DIR`; it is created per run, never a fixed
shared instance. Start the fixture by launching the built `root/usr/bin/sway`
with `WLR_BACKENDS=headless`, `XDG_RUNTIME_DIR` set to that directory and
`LD_LIBRARY_PATH` pointing at `root/usr/lib`, then run the flows.
