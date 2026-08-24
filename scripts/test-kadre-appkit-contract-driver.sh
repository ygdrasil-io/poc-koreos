#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DRIVER="$SCRIPT_DIR/test-kadre-appkit-contracts.sh"
FAKE_GRADLE="$SCRIPT_DIR/fixtures/fake-gradlew.sh"
TEMP_DIR="$(mktemp -d /tmp/kadre-appkit-driver.XXXXXX)"
EVIDENCE="$REPO_ROOT/kadre/backend/appkit/build/contract-evidence/contract-evidence.json"

cleanup() {
    local status="$?"
    trap - EXIT
    rm -rf "$TEMP_DIR"
    rm -f "$EVIDENCE"
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
KADRE_FAKE_EVIDENCE_PATH="$EVIDENCE" \
GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "success path did not run exactly two Gradle phases"
[[ "$(sed -n '1p' "$TRACE")" == *":kadre:backend:appkit:jvmTest"* ]] || fail "first phase did not run new AppKit tests"
[[ "$(sed -n '2p' "$TRACE")" == *":kadre:contracts:validator:generateAppKitContractEvidence"* ]] ||
    fail "second phase did not generate contract evidence"
[[ "$(sed -n '2p' "$TRACE")" == *"-PkadreContractCommit=0123456789abcdef"* ]] ||
    fail "evidence phase did not receive the checked-out commit"
if grep -q -- "--refresh-dependencies" "$TRACE"; then
    fail "driver used --refresh-dependencies"
fi
[[ -s "$EVIDENCE" ]] || fail "success path did not produce evidence"

rm -f "$EVIDENCE"
TRACE="$TEMP_DIR/failure.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_PATH="$EVIDENCE" \
    KADRE_FAKE_TEST_STATUS=17 \
    GITHUB_SHA="fedcba9876543210" \
    bash "$DRIVER"

[[ "$observed_status" == "17" ]] || fail "test failure status 17 became $observed_status"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "1" ]] || fail "evidence phase ran after a test failure"
[[ ! -e "$EVIDENCE" ]] || fail "test failure produced evidence"

echo "Kadre AppKit contract driver behavior: passed"
