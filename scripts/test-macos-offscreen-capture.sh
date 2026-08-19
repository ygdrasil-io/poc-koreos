#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

./gradlew :samples:hello-triangle:run \
  --args="--capture build/visual/hello-triangle.actual.png" \
  --no-daemon --console=plain

(cd tests/visual && npm install)
node tests/visual/diff-cli.js \
  samples/hello-triangle/build/visual/hello-triangle.actual.png \
  tests/visual/baselines/macos/hello-triangle.png \
  samples/hello-triangle/build/visual/hello-triangle.diff.png \
  0.02 \
  samples/hello-triangle/build/visual/report.json \
  macOS

python3 "$script_dir/verify-test-results.py" \
  --png samples/hello-triangle/build/visual/hello-triangle.actual.png \
  --png-target hello-triangle-baseline
