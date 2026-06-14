#!/usr/bin/env bash
# (Re)generate X11 FFM bindings for Kadre via Docker.
# Uses kextract to parse X11 system headers and produce Kotlin Panama FFM bindings.
#
# Output is written to build/x11-codegen/ inside the container (mounted from repo).
# Compare with current hand-written bindings in ffi/x11/src/jvmMain/kotlin/.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE=kadre-x11-codegen

docker build -t "$IMAGE" "$REPO_ROOT/docker/x11-codegen"

TTY_FLAG=""
[ -t 1 ] && TTY_FLAG="-t"

docker run --rm $TTY_FLAG \
  -v "$REPO_ROOT":/work \
  -v kadre-gradle-cache:/root/.gradle \
  "$IMAGE" bash /work/docker/x11-codegen/generate.sh

echo ""
echo "Generated files are in: $REPO_ROOT/build/x11-codegen/"
echo "Compare with:           $REPO_ROOT/ffi/x11/src/jvmMain/kotlin/"
