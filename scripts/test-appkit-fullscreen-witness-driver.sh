#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WITNESS_TEST="$SCRIPT_DIR/test-appkit-fullscreen-witness.sh"
FAKE_SWIFT="$SCRIPT_DIR/fixtures/fake-swift.sh"
TEMP_DIR="$(mktemp -d /tmp/kadre-appkit-fullscreen-witness-driver.XXXXXX)"

cleanup() {
    local status="$?"
    trap - EXIT
    rm -rf "$TEMP_DIR"
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

mkdir "$TEMP_DIR/bin"
ln -s "$FAKE_SWIFT" "$TEMP_DIR/bin/swift"

terminal_report='{"status":"passed","callbacks":["WillEnter","DidEnter","WillExit","DidExit"],"elapsedMilliseconds":1}'
timeout_report='{"status":"timed-out","callbacks":["WillEnter"],"failure":"no terminal callback","elapsedMilliseconds":15000}'
failure_report='{"status":"failed","callbacks":["WillEnter","DidFailEnter"],"failure":"entry failed","elapsedMilliseconds":1}'

capture_status observed_status env \
    PATH="$TEMP_DIR/bin:$PATH" \
    KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT="$TEMP_DIR/terminal.json" \
    KADRE_FAKE_SWIFT_REPORT="$terminal_report" \
    KADRE_FAKE_SWIFT_STATUS=0 \
    bash "$WITNESS_TEST"
[[ "$observed_status" == "0" ]] || fail "terminal fullscreen witness report was rejected"

capture_status observed_status env \
    PATH="$TEMP_DIR/bin:$PATH" \
    KADRE_APPKIT_FULLSCREEN_WITNESS_EXPECTATION=unavailable \
    KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT="$TEMP_DIR/timeout.json" \
    KADRE_FAKE_SWIFT_REPORT="$timeout_report" \
    KADRE_FAKE_SWIFT_STATUS=1 \
    bash "$WITNESS_TEST"
[[ "$observed_status" == "0" ]] || fail "an explicitly observed unavailable fullscreen witness was rejected"

capture_status observed_status env \
    PATH="$TEMP_DIR/bin:$PATH" \
    KADRE_APPKIT_FULLSCREEN_WITNESS_EXPECTATION=unavailable \
    KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT="$TEMP_DIR/failure.json" \
    KADRE_FAKE_SWIFT_REPORT="$failure_report" \
    KADRE_FAKE_SWIFT_STATUS=1 \
    bash "$WITNESS_TEST"
[[ "$observed_status" != "0" ]] || fail "an unexpected native fullscreen failure passed as unavailable"

echo "Kadre AppKit fullscreen witness behavior: passed"
