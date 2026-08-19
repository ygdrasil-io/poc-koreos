#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

: "${XDG_RUNTIME_DIR:?XDG_RUNTIME_DIR must name the deterministic Weston runtime directory}"
: "${WAYLAND_DISPLAY:?WAYLAND_DISPLAY must name the deterministic Weston socket}"
test -S "$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY"

LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe KADRE_LINUX_BACKEND=wayland \
  timeout -k 30 180 ./gradlew :samples:compose:desktop:run \
  --args="--window-capture samples/compose/desktop/build/cross-platform-correctness/compose-desktop.window.png" \
  --no-daemon --console=plain

python3 "$script_dir/verify-test-results.py" \
  --png samples/compose/desktop/build/cross-platform-correctness/compose-desktop.window.png \
  --png-target compose-raster
