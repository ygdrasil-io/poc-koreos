#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "Unsupported host: process-watchdog tests require macOS (Darwin)." >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE="$SCRIPT_DIR/fixtures/watchdog-process-tree.sh"
TEMP_DIR="$(mktemp -d /tmp/kadre-process-watchdog.XXXXXX)"
GROUPS_TO_CLEAN=()

cleanup() {
    local primary_status="$?"
    local cleanup_status=0
    local group

    trap - EXIT
    for group in "${GROUPS_TO_CLEAN[@]}"; do
        if kill -0 -- "-$group" 2>/dev/null; then
            if kill -KILL -- "-$group" 2>/dev/null; then
                :
            else
                cleanup_status=$?
            fi
        fi
    done
    if rm -rf "$TEMP_DIR"; then
        :
    else
        cleanup_status=$?
    fi

    if [[ "$primary_status" -ne 0 ]]; then
        exit "$primary_status"
    fi
    exit "$cleanup_status"
}
trap cleanup EXIT

source "$SCRIPT_DIR/lib/process-watchdog.sh"

fail() {
    echo "FAIL: $1" >&2
    return 1
}

untrack_group() {
    local completed_group="$1"
    local group
    local remaining=()

    for group in "${GROUPS_TO_CLEAN[@]}"; do
        if [[ "$group" != "$completed_group" ]]; then
            remaining+=("$group")
        fi
    done
    GROUPS_TO_CLEAN=("${remaining[@]}")
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

wait_for_file() {
    local file="$1"
    local deadline=$((SECONDS + 5))

    while [[ ! -s "$file" ]]; do
        if (( SECONDS >= deadline )); then
            fail "timed out waiting for $file"
            return 1
        fi
    done
}

wait_for_group_exit() {
    local group="$1"
    local deadline=$((SECONDS + 5))

    while kill -0 -- "-$group" 2>/dev/null; do
        if (( SECONDS >= deadline )); then
            fail "process group $group survived watchdog completion"
            return 1
        fi
    done
}

run_signal_case() {
    local signal_name="$1"
    local expected_status="$2"
    local case_dir="$TEMP_DIR/signal-$signal_name"
    local job_pid
    local wrapper_pid
    local leader_pid
    local member_pid
    local observed_status

    mkdir "$case_dir"
    run_with_timeout 10 "$FIXTURE" leader "$case_dir" &
    job_pid=$!
    wait_for_file "$case_dir/wrapper-pid"
    wait_for_file "$case_dir/leader-pid"
    wait_for_file "$case_dir/member-pid"
    wrapper_pid="$(<"$case_dir/wrapper-pid")"
    leader_pid="$(<"$case_dir/leader-pid")"
    member_pid="$(<"$case_dir/member-pid")"
    GROUPS_TO_CLEAN+=("$leader_pid")

    kill -s "$signal_name" "$wrapper_pid"
    set +e
    wait "$job_pid"
    observed_status=$?
    set -e

    [[ "$observed_status" -eq "$expected_status" ]] ||
        fail "$signal_name returned $observed_status instead of $expected_status"
    wait_for_group_exit "$leader_pid"
    untrack_group "$leader_pid"
    if kill -0 "$leader_pid" 2>/dev/null; then
        fail "$signal_name left leader $leader_pid alive"
    fi
    if kill -0 "$member_pid" 2>/dev/null; then
        fail "$signal_name left member $member_pid alive"
    fi
    [[ "$(<"$case_dir/leader-signal")" == "$signal_name" ]] ||
        fail "leader did not receive $signal_name"
    [[ "$(<"$case_dir/member-signal")" == "$signal_name" ]] ||
        fail "member did not receive $signal_name"
    echo "signal $signal_name -> $observed_status, group reaped"
}

run_startup_timeout_case() {
    local iteration="$1"
    local case_dir="$TEMP_DIR/startup-timeout-$iteration"
    local observed_status
    local leader_pid

    mkdir "$case_dir"
    capture_status observed_status run_with_timeout 1 "$FIXTURE" leader "$case_dir"

    [[ "$observed_status" -eq 124 ]] ||
        fail "startup timeout iteration $iteration returned $observed_status instead of 124"
    wait_for_file "$case_dir/leader-pid"
    leader_pid="$(<"$case_dir/leader-pid")"
    GROUPS_TO_CLEAN+=("$leader_pid")
    wait_for_group_exit "$leader_pid"
    untrack_group "$leader_pid"
}

capture_status status run_with_timeout 5 /bin/sh -c 'exit 7'
[[ "$status" -eq 7 ]] || fail "child status 7 became $status"

run_signal_case TERM 143
run_signal_case INT 130
run_signal_case HUP 129

timeout_dir="$TEMP_DIR/timeout"
mkdir "$timeout_dir"
capture_status status run_with_timeout 1 "$FIXTURE" leader "$timeout_dir"
[[ "$status" -eq 124 ]] || fail "timeout returned $status instead of 124"
wait_for_file "$timeout_dir/leader-pid"
timeout_group="$(<"$timeout_dir/leader-pid")"
GROUPS_TO_CLEAN+=("$timeout_group")
wait_for_group_exit "$timeout_group"
untrack_group "$timeout_group"
echo "timeout -> 124, group reaped"

for iteration in 1 2 3 4; do
    run_startup_timeout_case "$iteration"
done
echo "startup timeout race -> 4/4 returned 124 without a surviving group"

driver_cleanup_source="$(awk '
    /^cleanup\(\) \{/ { capture = 1 }
    capture { print }
    capture && /^}/ { exit }
' "$SCRIPT_DIR/test-uikit-simulator.sh")"
driver_signal_source="$(awk '
    /^handle_signal\(\) \{/ { capture = 1 }
    capture { print }
    capture && /^}/ { exit }
' "$SCRIPT_DIR/test-uikit-simulator.sh")"

run_driver_cleanup_case() (
    local primary_status="$1"
    local shutdown_status="$2"

    eval "$driver_cleanup_source"
    BOOTED_BY_SCRIPT=1
    UDID=TEST-UDID
    run_with_timeout() { return "$shutdown_status"; }
    cleanup "$primary_status"
)

assert_driver_cleanup_status() {
    local primary_status="$1"
    local shutdown_status="$2"
    local expected_status="$3"
    local observed_status

    capture_status observed_status run_driver_cleanup_case "$primary_status" "$shutdown_status"
    [[ "$observed_status" -eq "$expected_status" ]] ||
        fail "primary=$primary_status cleanup=$shutdown_status returned $observed_status instead of $expected_status"
}

assert_driver_cleanup_status 0 0 0
assert_driver_cleanup_status 0 9 9
assert_driver_cleanup_status 7 0 7
assert_driver_cleanup_status 7 9 7

set +e
(
    eval "$driver_cleanup_source"
    eval "$driver_signal_source"
    BOOTED_BY_SCRIPT=1
    UDID=TEST-UDID
    run_with_timeout() { return 9; }
    trap 'cleanup $?' EXIT
    handle_signal 143
)
driver_signal_status=$?
set -e
[[ "$driver_signal_status" -eq 143 ]] ||
    fail "driver TERM status became $driver_signal_status during failing cleanup"
echo "driver primary/cleanup matrix -> 5/5 statuses preserved"
