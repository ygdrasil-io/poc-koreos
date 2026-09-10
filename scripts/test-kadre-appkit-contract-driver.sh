#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DRIVER="$SCRIPT_DIR/test-kadre-appkit-contracts.sh"
FAKE_GRADLE="$SCRIPT_DIR/fixtures/fake-gradlew.sh"
TEMP_DIR="$(mktemp -d /tmp/kadre-appkit-driver.XXXXXX)"
EVIDENCE_DIRECTORY="$REPO_ROOT/kadre/backend/appkit/build/contract-evidence"
EVIDENCE_FILES=("APK-001.json" "APK-002.json" "APK-003.json" "APK-004.json" "APK-005.json" "APK-006.json" "APK-007.json" "APK-008.json" "APK-009.json" "APK-010.json" "APK-011.json" "APK-012.json" "APK-013.json" "APK-014.json" "APK-017.json")
RUNTIME_EVIDENCE_DIRECTORY="$REPO_ROOT/kadre/runtime/build/contract-evidence"
RUNTIME_EVIDENCE_FILES=("INP-002.json" "WIN-005.json" "WIN-006.json" "INT-001.json" "DSP-001.json" "RUN-007.json")

cleanup() {
    local status="$?"
    trap - EXIT
    rm -rf "$TEMP_DIR"
    rm -rf "$EVIDENCE_DIRECTORY"
    rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
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
KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "success path did not run exactly two Gradle phases"
[[ "$(sed -n '1p' "$TRACE")" == *":kadre:backend:appkit:appKitNativeTests"* ]] || fail "first phase did not run AppKit tests"
[[ "$(sed -n '2p' "$TRACE")" == *":kadre:contracts:validator:generateAppKitContractEvidence"* ]] ||
    fail "second phase did not generate AppKit contract evidence"
[[ "$(sed -n '2p' "$TRACE")" == *":kadre:contracts:validator:generateRuntimeContractEvidence"* ]] ||
    fail "second phase did not generate runtime contract evidence"
[[ "$(sed -n '2p' "$TRACE")" == *"-PkadreContractCommit=0123456789abcdef"* ]] ||
    fail "evidence phase did not receive the checked-out commit"
if [[ "$(grep -c -- "--refresh-dependencies" "$TRACE")" != "2" ]]; then
    fail "driver did not refresh the published KFFI snapshot in both Gradle phases"
fi
for evidence_file in "${EVIDENCE_FILES[@]}"; do
    [[ -s "$EVIDENCE_DIRECTORY/$evidence_file" ]] || fail "success path did not produce $evidence_file"
done
for evidence_file in "${RUNTIME_EVIDENCE_FILES[@]}"; do
    [[ -s "$RUNTIME_EVIDENCE_DIRECTORY/$evidence_file" ]] || fail "success path did not produce $evidence_file"
done

rm -rf "$EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-apk010.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=APK-010 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing APK-010 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing APK-010 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-apk011.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=APK-011 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing APK-011 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing APK-011 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-apk013.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=APK-013 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing APK-013 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing APK-013 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-apk014.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=APK-014 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing APK-014 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing APK-014 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-apk017.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=APK-017 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing APK-017 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing APK-017 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-win005.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=WIN-005 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing WIN-005 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing WIN-005 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-win006.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=WIN-006 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing WIN-006 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing WIN-006 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-inp002.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=INP-002 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing INP-002 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing INP-002 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-dsp001.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=DSP-001 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing DSP-001 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing DSP-001 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/missing-run007.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_MISSING_EVIDENCE=RUN-007 \
    GITHUB_SHA="0123456789abcdef" \
    bash "$DRIVER"

[[ "$observed_status" != "0" ]] || fail "missing RUN-007 evidence passed the AppKit contract gate"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "2" ]] || fail "missing RUN-007 evidence did not run both Gradle phases"

rm -rf "$EVIDENCE_DIRECTORY"
rm -rf "$RUNTIME_EVIDENCE_DIRECTORY"
TRACE="$TEMP_DIR/failure.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_TEST_STATUS=17 \
    GITHUB_SHA="fedcba9876543210" \
    bash "$DRIVER"

[[ "$observed_status" == "17" ]] || fail "test failure status 17 became $observed_status"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "1" ]] || fail "evidence phase ran after a test failure"
[[ ! -e "$EVIDENCE_DIRECTORY" ]] || fail "test failure produced evidence"
[[ ! -e "$RUNTIME_EVIDENCE_DIRECTORY" ]] || fail "test failure produced runtime evidence"

TRACE="$TEMP_DIR/native-sigtrap.trace"
capture_status observed_status env \
    KADRE_GRADLEW="$FAKE_GRADLE" \
    KADRE_FAKE_GRADLE_TRACE="$TRACE" \
    KADRE_FAKE_EVIDENCE_DIRECTORY="$EVIDENCE_DIRECTORY" \
    KADRE_FAKE_RUNTIME_EVIDENCE_DIRECTORY="$RUNTIME_EVIDENCE_DIRECTORY" \
    KADRE_FAKE_TEST_STATUS=133 \
    KADRE_FAKE_TEST_OUTPUT="Process 'Gradle Test Executor 1' finished with non-zero exit value 133" \
    GITHUB_SHA="fedcba9876543210" \
    bash "$DRIVER"

[[ "$observed_status" == "133" ]] || fail "native SIGTRAP status 133 became $observed_status"
[[ "$(wc -l < "$TRACE" | tr -d ' ')" == "1" ]] ||
    fail "native SIGTRAP retried the AppKit test phase"

echo "Kadre AppKit contract driver behavior: passed"
