#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: Kadre AppKit contract tests require macOS (Darwin)." >&2
    exit 1
fi

if [[ ! -x /usr/bin/perl ]]; then
    echo "Missing host prerequisite: /usr/bin/perl is required for the external watchdog." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLEW="${KADRE_GRADLEW:-$REPO_ROOT/gradlew}"
EVIDENCE="$REPO_ROOT/kadre/backend/appkit/build/contract-evidence/contract-evidence.json"
source "$SCRIPT_DIR/lib/process-watchdog.sh"

if [[ ! -x "$GRADLEW" ]]; then
    echo "Gradle wrapper is not executable: $GRADLEW" >&2
    exit 1
fi

COMMIT="${GITHUB_SHA:-}"
if [[ -z "$COMMIT" ]]; then
    COMMIT="$(git -C "$REPO_ROOT" rev-parse HEAD)"
fi

run_phase() {
    local phase="$1"
    shift
    echo "Kadre AppKit $phase: started"
    if run_with_timeout 600 "$@"; then
        echo "Kadre AppKit $phase: passed"
    else
        local status=$?
        echo "Kadre AppKit $phase: failed (status $status)" >&2
        return "$status"
    fi
}

rm -f "$EVIDENCE"
cd "$REPO_ROOT"

run_phase tests \
    "$GRADLEW" \
    :kadre:backend:appkit:jvmTest \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain

run_phase evidence \
    "$GRADLEW" \
    :kadre:contracts:validator:generateAppKitContractEvidence \
    "-PkadreContractCommit=$COMMIT" \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain

if [[ ! -s "$EVIDENCE" ]]; then
    echo "Kadre AppKit evidence is missing or empty: $EVIDENCE" >&2
    exit 1
fi
