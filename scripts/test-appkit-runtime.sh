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

run_appkit_phase() {
    local phase="$1"
    shift
    echo "AppKit $phase: started"
    if run_with_timeout 600 "$@"; then
        echo "AppKit $phase: passed"
    else
        local status=$?
        echo "AppKit $phase: failed (status $status)" >&2
        return "$status"
    fi
}

run_appkit_phase preparation \
    ./gradlew :kadre-appkit:jvmTestClasses \
    --refresh-dependencies \
    --no-daemon --stacktrace --console=plain

run_appkit_phase runtime \
    ./gradlew :kadre-appkit:jvmTest \
    --refresh-dependencies \
    --no-daemon --stacktrace --console=plain
