#!/usr/bin/env bash
# Gate des contrats Wayland (Linux uniquement) : tests O2+O3, génération des
# preuves contractuelles, assertion de non-vacuité — miroir de
# test-kadre-appkit-contracts.sh, sans continue-on-error ni faux succès.
set -euo pipefail

if [[ "$(uname -s)" != "Linux" ]]; then
    echo "Unsupported host: Kadre Wayland contract tests require Linux." >&2
    exit 1
fi

if [[ ! -x /usr/bin/perl ]]; then
    echo "Missing host prerequisite: /usr/bin/perl is required for the external watchdog." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLEW="${KADRE_GRADLEW:-$REPO_ROOT/gradlew}"
WAYLAND_BACKEND_BUILD="$REPO_ROOT/kadre/backend/wayland/build"
EVIDENCE_DIRECTORY="$WAYLAND_BACKEND_BUILD/contract-evidence"
EVIDENCE_FILES=("BCK-017.json" "SES-001.json" "SUR-001.json")
DIAGNOSTICS_DIRECTORY="$WAYLAND_BACKEND_BUILD/ci-diagnostics"
TEST_TIMEOUT_SECONDS="${KADRE_WAYLAND_TEST_TIMEOUT_SECONDS:-900}"
EVIDENCE_TIMEOUT_SECONDS=600
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
    echo "Kadre Wayland $phase: started"

    set +e
    run_with_timeout "$timeout_seconds" "$@" 2>&1 | tee "$log_file"
    statuses=("${PIPESTATUS[@]}")
    set -e
    status="${statuses[0]}"
    tee_status="${statuses[1]}"

    if (( tee_status != 0 )); then
        echo "Kadre Wayland $phase: could not write diagnostics (status $tee_status)" >&2
        return "$tee_status"
    fi
    if (( status == 0 )); then
        echo "Kadre Wayland $phase: passed"
        return 0
    fi

    echo "Kadre Wayland $phase: failed (status $status; log: $log_file)" >&2
    return "$status"
}

rm -rf "$EVIDENCE_DIRECTORY"
cd "$REPO_ROOT"

# Compositor réel requis : indisponible = échec de gate, pas un skip.
mkdir -p "$DIAGNOSTICS_DIRECTORY"
"$SCRIPT_DIR/ci-wayland-runtime.sh" version | tee -a "$DIAGNOSTICS_DIRECTORY/weston-version.log"
"$SCRIPT_DIR/ci-wayland-runtime.sh" start
trap '$SCRIPT_DIR/ci-wayland-runtime.sh stop || true' EXIT
source "$REPO_ROOT/build/wayland/weston.env"

run_phase tests "$TEST_TIMEOUT_SECONDS" \
    "$GRADLEW" \
    :kadre:backend:wayland:waylandContractTests \
    --refresh-dependencies \
    --rerun-tasks \
    --no-daemon \
    --stacktrace \
    --console=plain

run_phase evidence "$EVIDENCE_TIMEOUT_SECONDS" \
    "$GRADLEW" \
    :kadre:contracts:validator:generateWaylandContractEvidence \
    -PkadreContractCommit="$COMMIT" \
    --no-daemon \
    --stacktrace \
    --console=plain

for evidence_file in "${EVIDENCE_FILES[@]}"; do
    if [[ ! -s "$EVIDENCE_DIRECTORY/$evidence_file" ]]; then
        echo "Missing or empty Wayland contract evidence: $EVIDENCE_DIRECTORY/$evidence_file" >&2
        exit 1
    fi
done

echo "Kadre Wayland contracts gate: passed"
