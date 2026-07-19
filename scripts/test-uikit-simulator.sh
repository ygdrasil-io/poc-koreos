#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: UIKit simulator tests require macOS (Darwin)." >&2
    exit 1
fi

if [[ ! -x /usr/bin/perl ]]; then
    echo "Missing host prerequisite: /usr/bin/perl is required for the external timeout." >&2
    exit 1
fi

if ! command -v xcrun >/dev/null 2>&1; then
    echo "Missing host prerequisite: xcrun is required for iOS simulator tests." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$SCRIPT_DIR/lib/process-watchdog.sh"

select_recent_iphone() {
    run_with_timeout 600 xcrun simctl list devices available | awk '
        /^-- iOS / { in_ios = 1; next }
        /^-- / { in_ios = 0 }
        in_ios && /^[[:space:]]+iPhone/ {
            if (match($0, /\([0-9A-F-]+\)/)) {
                udid = substr($0, RSTART + 1, RLENGTH - 2)
                state = ($0 ~ /\(Booted\)/) ? "Booted" : "Shutdown"
            }
        }
        END {
            if (udid != "") print udid, state
        }
    '
}

create_recent_iphone() {
    local runtime_id
    local device_type_id
    runtime_id="$(run_with_timeout 600 xcrun simctl list runtimes available | awk '/^iOS / { runtime = $NF } END { print runtime }')"
    device_type_id="$(run_with_timeout 600 xcrun simctl list devicetypes | awk '
        /^iPhone/ && match($0, /\(com\.apple\.CoreSimulator\.SimDeviceType\.[^)]+\)/) {
            print substr($0, RSTART + 1, RLENGTH - 2)
            exit
        }
    ')"
    if [[ -z "$runtime_id" || -z "$device_type_id" ]]; then
        echo "No available iOS runtime and iPhone device type were found." >&2
        return 1
    fi
    run_with_timeout 600 xcrun simctl create "Kadre UIKit ${runtime_id##*.iOS-} $$" "$device_type_id" "$runtime_id"
}

CREATED_BY_SCRIPT=0
selection="$(select_recent_iphone)"
if [[ -n "$selection" ]]; then
    read -r UDID DEVICE_STATE <<<"$selection"
else
    UDID="$(create_recent_iphone)"
    DEVICE_STATE="Shutdown"
    CREATED_BY_SCRIPT=1
fi

BOOTED_BY_SCRIPT=0
cleanup() {
    local primary_status="$1"
    local cleanup_status=0

    trap - EXIT
    if [[ "$BOOTED_BY_SCRIPT" -eq 1 ]]; then
        if run_with_timeout 600 xcrun simctl shutdown "$UDID"; then
            cleanup_status=0
        else
            cleanup_status=$?
        fi
    fi

    if [[ "$primary_status" -ne 0 ]]; then
        exit "$primary_status"
    fi
    exit "$cleanup_status"
}
handle_signal() {
    local signal_status="$1"

    trap - INT TERM HUP
    exit "$signal_status"
}
trap 'cleanup $?' EXIT
trap 'handle_signal 130' INT
trap 'handle_signal 143' TERM
trap 'handle_signal 129' HUP

if [[ "$DEVICE_STATE" != "Booted" ]]; then
    BOOTED_BY_SCRIPT=1
    run_with_timeout 600 xcrun simctl boot "$UDID"
fi

if [[ "$CREATED_BY_SCRIPT" -eq 1 ]]; then
    echo "Created iOS simulator $UDID for this test run."
else
    echo "Selected existing iOS simulator $UDID ($DEVICE_STATE)."
fi

run_with_timeout 600 xcrun simctl bootstatus "$UDID" -b

export SIMULATOR_UDID="$UDID"
cd "$REPO_ROOT"
set +e
run_with_timeout 600 \
    ./gradlew \
    :kadre-uikit:iosSimulatorArm64Test \
    :samples:hello-triangle-ios:iosSimulatorArm64Test \
    --no-daemon --stacktrace --console=plain
gradle_status=$?
set -e
exit "$gradle_status"
