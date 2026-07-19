#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: AppKit runtime tests require macOS (Darwin)." >&2
    exit 1
fi

if [[ ! -x /usr/bin/perl ]]; then
    echo "Missing host prerequisite: /usr/bin/perl is required for the external timeout." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$SCRIPT_DIR/lib/process-watchdog.sh"

cd "$REPO_ROOT"
run_with_timeout 600 \
    ./gradlew :kadre-appkit:jvmTest \
    --no-daemon --stacktrace --console=plain
