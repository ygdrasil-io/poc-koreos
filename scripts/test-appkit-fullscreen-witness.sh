#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: the AppKit fullscreen witness requires macOS (Darwin)." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WITNESS="$SCRIPT_DIR/appkit-fullscreen-witness.swift"
REPORT="${KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT:-$(mktemp "${TMPDIR:-/tmp}/kadre-appkit-fullscreen-witness.XXXXXX.json")}"
REMOVE_REPORT=false
if [[ -z "${KADRE_APPKIT_FULLSCREEN_WITNESS_OUTPUT:-}" ]]; then
    REMOVE_REPORT=true
fi

cleanup() {
    if [[ "$REMOVE_REPORT" == true ]]; then
        rm -f "$REPORT"
    fi
}
trap cleanup EXIT

if ! swift "$WITNESS" --timeout-seconds "${KADRE_APPKIT_FULLSCREEN_WITNESS_TIMEOUT_SECONDS:-15}" --output "$REPORT"; then
    [[ -f "$REPORT" ]] && cat "$REPORT"
    exit 1
fi

python3 - "$REPORT" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as report_file:
    report = json.load(report_file)

if report["status"] != "passed":
    raise SystemExit(f"native AppKit fullscreen witness did not pass: {report}")
if report["callbacks"] != ["WillEnter", "DidEnter", "WillExit", "DidExit"]:
    raise SystemExit(f"native AppKit fullscreen callbacks were unexpected: {report}")
if not isinstance(report["elapsedMilliseconds"], int) or report["elapsedMilliseconds"] < 0:
    raise SystemExit(f"native AppKit fullscreen witness did not report a duration: {report}")
PY
