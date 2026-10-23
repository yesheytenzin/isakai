#!/usr/bin/env bash
# Build the isolated-compositor verification fixture reproducibly from tracked
# sources. Our own helpers (virtual input, globals probe, event watcher) are
# compiled from the C sources beside this script. The sway + wlroots + liftoff
# compositor is an external dependency, pinned by sha256 in DEPS.lock and
# obtained separately (see README.md); this script verifies and extracts it.
#
# Usage: verify-isakai/compositor/build.sh [OUT_DIR] [DEPS_DIR]
#   OUT_DIR   where the fixture is assembled (default .cache/isakai/compositor)
#   DEPS_DIR  where the pinned *.pkg.tar.zst live   (default OUT_DIR)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
out="${1:-$root/.cache/isakai/compositor}"
deps="${2:-$out}"
mkdir -p "$out"

echo "[1/3] generate Wayland protocol headers"
wayland-scanner client-header "$here/wlr-virtual-pointer.xml" "$out/wlr-virtual-pointer-unstable-v1-client-protocol.h"
wayland-scanner private-code  "$here/wlr-virtual-pointer.xml" "$out/wlr-virtual-pointer-protocol.c"
wayland-scanner client-header "$here/virtual-keyboard.xml"    "$out/virtual-keyboard-unstable-v1-client-protocol.h"
wayland-scanner private-code  "$here/virtual-keyboard.xml"    "$out/virtual-keyboard-protocol.c"

echo "[2/3] compile helpers from tracked sources"
cflags="$(pkg-config --cflags wayland-client xkbcommon)"
libs="$(pkg-config --libs wayland-client xkbcommon)"
gcc -O2 -I"$out" -o "$out/isakai-vinput" "$here/isakai-vinput.c" "$out/wlr-virtual-pointer-protocol.c" "$out/virtual-keyboard-protocol.c" $cflags $libs
gcc -O2 -I"$out" -o "$out/isakai-vptr"   "$here/isakai-vptr.c"   "$out/wlr-virtual-pointer-protocol.c" $cflags $libs
gcc -O2 -I"$out" -o "$out/isakai-wev"    "$here/isakai-wev.c"    $cflags $libs
gcc -O2 -I"$out" -o "$out/isakai-globals" "$here/isakai-globals.c" $cflags $libs
cp "$here/sway.conf" "$out/sway.conf"

echo "[3/3] verify + extract the pinned compositor packages"
missing=0
while read -r sum name; do
  [ -z "$name" ] && continue
  if [ ! -f "$deps/$name" ]; then
    echo "  MISSING $name (see README.md to obtain it; pinned $sum)"
    missing=1
    continue
  fi
  echo "$sum  $deps/$name" | sha256sum -c - >/dev/null
done < "$here/DEPS.lock"
if [ "$missing" = 1 ]; then
  echo "compositor packages absent; helpers built, fixture root not assembled."
  exit 3
fi
rm -rf "$out/root"; mkdir -p "$out/root"
for pkg in "$deps"/*.pkg.tar.zst; do tar -I zstd -xf "$pkg" -C "$out/root"; done
echo "DONE: fixture assembled at $out (sway at root/usr/bin, helpers built from source)"
