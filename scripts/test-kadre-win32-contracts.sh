#!/usr/bin/env bash
# Gate des contrats Win32 (Windows uniquement) : tests O2+O3, génération des
# preuves contractuelles, assertion de non-vacuité — miroir de
# test-kadre-wayland-contracts.sh, sans continue-on-error ni faux succès.
set -euo pipefail

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*|Windows_NT) ;;
    *)
        echo "Unsupported host: Kadre Win32 contract tests require Windows (Git Bash/MSYS)." >&2
        exit 1
        ;;
esac

if [[ ! -x /usr/bin/perl ]]; then
    echo "Missing host prerequisite: /usr/bin/perl is required for the external watchdog." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLEW="${KADRE_GRADLEW:-$REPO_ROOT/gradlew}"
WIN32_BACKEND_BUILD="$REPO_ROOT/kadre/backend/win32/build"
EVIDENCE_DIRECTORY="$WIN32_BACKEND_BUILD/contract-evidence"
EVIDENCE_FILES=("W32-001.json" "W32-002.json" "W32-003.json" "W32-004.json" "W32-005.json" "W32-006.json" "W32-007.json" "W32-008.json" "W32-009.json" "W32-010.json" "W32-011.json" "BCK-025.json" "SES-002.json" "PMP-001.json")
DIAGNOSTICS_DIRECTORY="$WIN32_BACKEND_BUILD/ci-diagnostics"
TEST_TIMEOUT_SECONDS="${KADRE_WIN32_TEST_TIMEOUT_SECONDS:-480}"
EVIDENCE_TIMEOUT_SECONDS=300
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
    local timeout_seconds="$2"
    shift 2
    local statuses
    local status
    local tee_status
    local log_file

    mkdir -p "$DIAGNOSTICS_DIRECTORY"
    log_file="$DIAGNOSTICS_DIRECTORY/${phase}.log"
    echo "Kadre Win32 $phase: started"

    set +e
    run_with_timeout "$timeout_seconds" "$@" 2>&1 | tee "$log_file"
    statuses=("${PIPESTATUS[@]}")
    set -e
    status="${statuses[0]}"
    tee_status="${statuses[1]}"

    if (( tee_status != 0 )); then
        echo "Kadre Win32 $phase: could not write diagnostics (status $tee_status)" >&2
        return "$tee_status"
    fi
    if (( status == 0 )); then
        echo "Kadre Win32 $phase: passed"
        return 0
    fi

    echo "Kadre Win32 $phase: failed (status $status; log: $log_file)" >&2
    return "$status"
}

rm -rf "$EVIDENCE_DIRECTORY"
cd "$REPO_ROOT"

# Bureau interactif requis : un runner sans bureau = échec de gate, pas un skip.
run_phase tests "$TEST_TIMEOUT_SECONDS" \
    "$GRADLEW" \
    :kadre:backend:win32:win32ContractTests \
    --refresh-dependencies \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain "$@"

run_phase evidence "$EVIDENCE_TIMEOUT_SECONDS" \
    "$GRADLEW" \
    :kadre:contracts:validator:generateWin32ContractEvidence \
    -PkadreContractCommit="$COMMIT" \
    --no-daemon \
    --stacktrace \
    --console=plain "$@"

for evidence_file in "${EVIDENCE_FILES[@]}"; do
    if [[ ! -s "$EVIDENCE_DIRECTORY/$evidence_file" ]]; then
        echo "Missing or empty Win32 contract evidence: $EVIDENCE_DIRECTORY/$evidence_file" >&2
        exit 1
    fi
done

echo "Kadre Win32 contracts gate: passed"
