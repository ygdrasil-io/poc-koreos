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
EVIDENCE_FILES=("APK-001.json" "APK-002.json" "APK-003.json" "APK-004.json" "APK-005.json" "APK-006.json" "APK-007.json" "APK-008.json" "APK-009.json" "APK-010.json" "APK-011.json" "APK-012.json" "APK-013.json")
RUNTIME_EVIDENCE_DIRECTORY="$REPO_ROOT/kadre/runtime/build/contract-evidence"
RUNTIME_EVIDENCE_FILES=("INP-002.json" "WIN-005.json" "WIN-006.json" "INT-001.json")
DIAGNOSTICS_DIRECTORY="$REPO_ROOT/kadre/backend/appkit/build/ci-diagnostics"
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
    local attempt=1
    local maximum_attempts=1
    local statuses
    local status
    local tee_status
    local log_file

    # The isolated NSApplication proof occasionally receives SIGTRAP from the managed macOS
    # runner even though the same commit succeeds in a fresh process. Retry only that native
    # process failure once; Kotlin test failures, timeouts, and every other Gradle failure stay
    # immediately blocking.
    if [[ "$phase" == "tests" || "$phase" == "evidence" ]]; then
        maximum_attempts=2
    fi
    mkdir -p "$DIAGNOSTICS_DIRECTORY"

    while (( attempt <= maximum_attempts )); do
        log_file="$DIAGNOSTICS_DIRECTORY/${phase}-attempt-${attempt}.log"
        echo "Kadre AppKit $phase: started (attempt $attempt/$maximum_attempts)"

        set +e
        run_with_timeout 600 "$@" 2>&1 | tee "$log_file"
        statuses=("${PIPESTATUS[@]}")
        set -e
        status="${statuses[0]}"
        tee_status="${statuses[1]}"

        if (( tee_status != 0 )); then
            echo "Kadre AppKit $phase: could not write diagnostics (status $tee_status)" >&2
            return "$tee_status"
        fi
        if (( status == 0 )); then
            echo "Kadre AppKit $phase: passed (attempt $attempt/$maximum_attempts)"
            return 0
        fi
        if (( attempt < maximum_attempts )) && grep -Fq "finished with non-zero exit value 133" "$log_file"; then
            echo "Kadre AppKit $phase: retrying once after native SIGTRAP; first log: $log_file" >&2
            ((attempt += 1))
            continue
        fi

        echo "Kadre AppKit $phase: failed (status $status; log: $log_file)" >&2
        return "$status"
    done
}

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
cd "$REPO_ROOT"

run_phase tests \
    "$GRADLEW" \
    :kadre:backend:appkit:appKitNativeTests \
    --refresh-dependencies \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain

run_phase evidence \
    "$GRADLEW" \
    :kadre:contracts:validator:generateRuntimeContractEvidence \
    :kadre:contracts:validator:generateAppKitContractEvidence \
    "-PkadreContractCommit=$COMMIT" \
    --refresh-dependencies \
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
