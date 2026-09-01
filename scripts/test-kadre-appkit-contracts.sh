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
EVIDENCE_DIRECTORY="$REPO_ROOT/kadre/backend/appkit/build/contract-evidence"
EVIDENCE_FILES=("APK-001.json" "APK-002.json" "APK-003.json" "APK-004.json" "APK-005.json" "APK-006.json" "APK-007.json" "APK-008.json" "APK-009.json" "APK-010.json" "APK-011.json" "APK-012.json")
RUNTIME_EVIDENCE_DIRECTORY="$REPO_ROOT/kadre/runtime/build/contract-evidence"
RUNTIME_EVIDENCE_FILES=("WIN-005.json" "WIN-006.json" "INT-001.json")
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

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
cd "$REPO_ROOT"

run_phase tests \
    "$GRADLEW" \
    :kadre:backend:appkit:appKitNativeTests \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain

run_phase evidence \
    "$GRADLEW" \
    :kadre:contracts:validator:generateRuntimeContractEvidence \
    :kadre:contracts:validator:generateAppKitContractEvidence \
    "-PkadreContractCommit=$COMMIT" \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain

for evidence_file in "${EVIDENCE_FILES[@]}"; do
    evidence="$EVIDENCE_DIRECTORY/$evidence_file"
    if [[ ! -s "$evidence" ]]; then
        echo "Kadre AppKit evidence is missing or empty: $evidence" >&2
        exit 1
    fi
done

for evidence_file in "${RUNTIME_EVIDENCE_FILES[@]}"; do
    evidence="$RUNTIME_EVIDENCE_DIRECTORY/$evidence_file"
    if [[ ! -s "$evidence" ]]; then
        echo "Kadre runtime evidence is missing or empty: $evidence" >&2
        exit 1
    fi
done
