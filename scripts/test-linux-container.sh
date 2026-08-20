#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 1 || ( "$1" != glibc && "$1" != musl ) ]]; then
  echo "usage: $0 <glibc|musl>" >&2
  exit 2
fi

libc="$1"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"

if [[ "${KADRE_CONTAINERIZED:-}" != "$libc" ]]; then
  case "$libc" in
    glibc) image="gradle:9.2.1-jdk25" ;;
    musl) image="gradle:9.2.1-jdk25-alpine" ;;
  esac
  if [[ "$libc" == musl ]]; then
    exec docker run --rm \
      -e "KADRE_CONTAINERIZED=$libc" \
      -v "$repo_root:/work" \
      -w /work \
      --entrypoint /bin/sh \
      "$image" \
      -c 'apk add --no-cache bash && exec bash /work/scripts/test-linux-container.sh musl'
  fi
  exec docker run --rm \
    -e "KADRE_CONTAINERIZED=$libc" \
    -v "$repo_root:/work" \
    -w /work \
    "$image" \
    bash /work/scripts/test-linux-container.sh "$libc"
fi

case "$libc" in
  glibc)
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
      xvfb xauth x11-utils libx11-6 libxi6 libxkbcommon0 libwayland-client0 weston
    ;;
  musl)
    apk add --no-cache \
      xvfb xdpyinfo xauth libx11 libxi libxkbcommon wayland weston \
      weston-backend-headless weston-shell-desktop
    ;;
esac

chmod +x gradlew scripts/test-x11-xvfb.sh scripts/ci-wayland-runtime.sh
./gradlew :kadre:jvmTest --tests '*LinuxBackendDetectorTest*' --tests '*LinuxBackendLaunchTest*' --refresh-dependencies --no-daemon --console=plain
scripts/test-x11-xvfb.sh
scripts/ci-wayland-runtime.sh ./gradlew \
  :kadre-wayland:jvmTest \
  --tests '*WaylandLoopContractTest*' \
  --refresh-dependencies \
  --no-daemon --console=plain
