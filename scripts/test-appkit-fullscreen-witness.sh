#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: the AppKit fullscreen witness requires macOS (Darwin)." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WITNESS="$SCRIPT_DIR/appkit-fullscreen-witness.swift"
REPORT="${KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT:-$(mktemp "${TMPDIR:-/tmp}/kadre-appkit-fullscreen-witness.XXXXXX.json")}"
EXPECTATION="${KADRE_APPKIT_FULLSCREEN_WITNESS_EXPECTATION:-terminal}"
REMOVE_REPORT=false
if [[ -z "${KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT:-}" ]]; then
    REMOVE_REPORT=true
fi

case "$EXPECTATION" in
    terminal|unavailable) ;;
    *)
        echo "Unsupported AppKit fullscreen witness expectation: $EXPECTATION" >&2
        exit 64
        ;;
esac

cleanup() {
    if [[ "$REMOVE_REPORT" == true ]]; then
        rm -f "$REPORT"
    fi
}
trap cleanup EXIT

set +e
swift "$WITNESS" --timeout-seconds "${KADRE_APPKIT_FULLSCREEN_WITNESS_TIMEOUT_SECONDS:-15}" --output "$REPORT"
SWIFT_STATUS=$?
set -e

if [[ ! -f "$REPORT" ]]; then
    echo "AppKit fullscreen witness did not produce a report (status $SWIFT_STATUS)." >&2
    exit "$SWIFT_STATUS"
fi

if (( SWIFT_STATUS != 0 )); then
    cat "$REPORT"
fi

python3 - "$REPORT" "$EXPECTATION" "$SWIFT_STATUS" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as report_file:
    report = json.load(report_file)

expectation = sys.argv[2]
swift_status = int(sys.argv[3])

if not isinstance(report["elapsedMilliseconds"], int) or report["elapsedMilliseconds"] < 0:
    raise SystemExit(f"native AppKit fullscreen witness did not report a duration: {report}")

terminal_callbacks = ["WillEnter", "DidEnter", "WillExit", "DidExit"]
if report["status"] == "passed":
    if swift_status != 0 or report["callbacks"] != terminal_callbacks:
        raise SystemExit(f"native AppKit fullscreen terminal witness was inconsistent: {report}")
elif expectation == "unavailable" and report["status"] == "timed-out":
    if swift_status == 0 or report["callbacks"] != ["WillEnter"]:
        raise SystemExit(f"native AppKit fullscreen unavailability witness was inconsistent: {report}")
else:
    raise SystemExit(f"native AppKit fullscreen witness did not satisfy {expectation}: {report}")
PY
