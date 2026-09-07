#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT="$(mktemp "${TMPDIR:-/tmp}/kadre-display-canary.XXXXXX.json")"
trap 'rm -f "$REPORT"' EXIT

swift "$SCRIPT_DIR/appkit-display-mode-canary.swift" --inspect-only --output "$REPORT"

python3 - "$REPORT" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as report_file:
    report = json.load(report_file)

assert report["requestedModeChange"] is False
assert report["status"] == "observed"
assert isinstance(report["displays"], list)
assert report["displays"], "the inspect-only canary must report at least one display"
assert all("currentMode" in display for display in report["displays"])
assert all("availableModes" in display for display in report["displays"])
PY
