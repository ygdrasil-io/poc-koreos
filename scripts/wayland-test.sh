#!/usr/bin/env bash
# Run a Kadre Gradle command against a headless Weston compositor inside a Linux container,
# so the Wayland backend can be tested from a macOS dev box.
#
#   scripts/wayland-test.sh                       # default: hello-compose --window-capture
#   scripts/wayland-test.sh ./gradlew :samples:hello-window:run --no-daemon
#
# The repo is mounted at /work; a named volume persists ~/.gradle across runs.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE=kadre-wayland

docker build -t "$IMAGE" "$REPO_ROOT/docker/wayland-test"

docker run --rm -t \
  -v "$REPO_ROOT":/work \
  -v kadre-gradle-cache:/root/.gradle \
  "$IMAGE" "$@"
