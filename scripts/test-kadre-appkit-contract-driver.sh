#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DRIVER="$SCRIPT_DIR/test-kadre-appkit-contracts.sh"
FAKE_GRADLE="$SCRIPT_DIR/fixtures/fake-gradlew.sh"
TEMP_DIR="$(mktemp -d /tmp/kadre-appkit-driver.XXXXXX)"
EVIDENCE_DIRECTORY="$REPO_ROOT/kadre/backend/appkit/build/contract-evidence"
EVIDENCE_FILES=("APK-001.json" "APK-002.json")

cleanup() {
    local status="$?"
    trap - EXIT
    rm -rf "$TEMP_DIR"
    rm -rf "$EVIDENCE_DIRECTORY"
    exit "$status"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $1" >&2
    return 1
}

capture_status() {
    local output_name="$1"
    shift
    local observed
    set +e
    "$@"
    observed=$?
    set -e
    printf -v "$output_name" '%s' "$observed"
}

TRACE="$TEMP_DIR/success.trace"
KADRE_GRADLEW="$FAKE_GRADLE" \
KADRE_FAKE_GRADLE_TRACE="$TRACE" \
KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "success path did not run exactly two Gradle phases"
[[ "$(sed -n '1p' "$TRACE")" == *":kadre:backend:appkit:appKitNativeTests"* ]] || fail "first phase did not run AppKit tests"
[[ "$(sed -n '2p' "$TRACE")" == *":kadre:contracts:validator:generateAppKitContractEvidence"* ]] ||
    fail "second phase did not generate contract evidence"
[[ "$(sed -n '2p' "$TRACE")" == *"-PkadreContractCommit=0123456789abcdef"* ]] ||
    fail "evidence phase did not receive the checked-out commit"
if grep -q -- "--refresh-dependencies" "$TRACE"; then
    fail "driver used --refresh-dependencies"
fi
for evidence_file in "${EVIDENCE_FILES[@]}"; do
    [[ -s "$EVIDENCE_DIRECTORY/$evidence_file" ]] || fail "success path did not produce $evidence_file"
done

rm -rf "$EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/failure.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_TEST_STATUS=17 \
    GITHUB_SHA="fedcba9876543210" \
    bash "$DRIVER"

[[ "$observed_status" == "17" ]] || fail "test failure status 17 became $observed_status"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "1" ]] || fail "evidence phase ran after a test failure"
[[ ! -e "$EVIDENCE_DIRECTORY" ]] || fail "test failure produced evidence"

echo "Kadre AppKit contract driver behavior: passed"
