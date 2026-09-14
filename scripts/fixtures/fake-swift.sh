#!/usr/bin/env bash
set -euo pipefail

: "${KADRE_FAKE_SWIFT_REPORT:?KADRE_FAKE_SWIFT_REPORT is required}"

output=""
while (($# > 0)); do
    case "$1" in
        --output)
            shift
            output="$1"
            ;;
    esac
    shift
done

[[ -n "$output" ]] || {
    echo "fake Swift did not receive --output" >&2
    exit 64
}

printf '%s\n' "$KADRE_FAKE_SWIFT_REPORT" > "$output"
exit "${KADRE_FAKE_SWIFT_STATUS:-0}"
