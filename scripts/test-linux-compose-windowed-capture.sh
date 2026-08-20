#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

: "${XDG_RUNTIME_DIR:?XDG_RUNTIME_DIR must name the deterministic Weston runtime directory}"
: "${WAYLAND_DISPLAY:?WAYLAND_DISPLAY must name the deterministic Weston socket}"
test -S "$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY"

LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe KADRE_LINUX_BACKEND=wayland \
  KADRE_DIAGNOSE_WAYLAND_EGL=1 EGL_LOG_LEVEL=debug LIBGL_DEBUG=verbose MESA_DEBUG=context \
  timeout -k 30 600 ./gradlew :samples:compose:desktop:run \
  --args="--window-capture build/cross-platform-correctness/compose-desktop.window.png" \
  --refresh-dependencies \
  --no-daemon --console=plain

python3 "$script_dir/verify-test-results.py" \
  --png samples/compose/desktop/build/cross-platform-correctness/compose-desktop.window.png \
  --png-target compose-raster
