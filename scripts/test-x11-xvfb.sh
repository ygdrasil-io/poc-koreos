#!/usr/bin/env bash
set -euo pipefail

for required_command in Xvfb xdpyinfo xauth; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    echo "[test-x11-xvfb] ERROR: $required_command is not installed" >&2
    exit 1
  fi
done

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
temporary_directory=""
xvfb_pid=""
readiness_pid=""
readiness_watchdog_pid=""

terminate_xvfb() {
  local pid=$1
  local killer_pid

  if kill -0 "$pid" >/dev/null 2>&1; then
    kill -TERM "$pid" >/dev/null 2>&1
    (
      sleep 2
      kill -KILL "$pid" >/dev/null 2>&1
    ) &
    killer_pid=$!
    wait "$pid" >/dev/null 2>&1
    kill "$killer_pid" >/dev/null 2>&1
    wait "$killer_pid" >/dev/null 2>&1
  else
    wait "$pid" >/dev/null 2>&1
  fi
}

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  if [ -n "$readiness_pid" ]; then
    kill -KILL "$readiness_pid" >/dev/null 2>&1
    wait "$readiness_pid" >/dev/null 2>&1
  fi
  if [ -n "$readiness_watchdog_pid" ]; then
    kill "$readiness_watchdog_pid" >/dev/null 2>&1
    wait "$readiness_watchdog_pid" >/dev/null 2>&1
  fi
  if [ -n "$xvfb_pid" ]; then
    terminate_xvfb "$xvfb_pid"
  fi
  if [ -n "$temporary_directory" ]; then
    rm -rf "$temporary_directory"
  fi
  exit "$status"
}
trap cleanup EXIT

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/kadre-xvfb.XXXXXX")
export XAUTHORITY="$temporary_directory/Xauthority"
xvfb_log="$temporary_directory/Xvfb.log"

touch "$XAUTHORITY"
chmod 600 "$XAUTHORITY"
cookie=$(od -An -N16 -tx1 /dev/urandom | tr -d '[:space:]')
for ((candidate = 0; candidate <= 255; candidate += 1)); do
  printf 'add :%d MIT-MAGIC-COOKIE-1 %s\n' "$candidate" "$cookie"
done | xauth -f "$XAUTHORITY" source - >/dev/null

readiness_deadline=$((SECONDS + 10))
coproc KADRE_XVFB {
  exec Xvfb \
    -displayfd 1 \
    -screen 0 1280x720x24 \
    -nolisten tcp \
    -auth "$XAUTHORITY" \
    2>"$xvfb_log"
}
xvfb_pid=$KADRE_XVFB_PID
display_fd=${KADRE_XVFB[0]}

remaining_seconds=$((readiness_deadline - SECONDS))
if (( remaining_seconds <= 0 )) ||
  ! IFS= read -r -t "$remaining_seconds" display_number <&"$display_fd"; then
  echo "[test-x11-xvfb] ERROR: Xvfb did not allocate a display within 10 seconds" >&2
  cat "$xvfb_log" >&2
  exit 1
fi
exec {display_fd}<&-

if [[ ! "$display_number" =~ ^[0-9]+$ ]]; then
  echo "[test-x11-xvfb] ERROR: Xvfb returned invalid display number: $display_number" >&2
  cat "$xvfb_log" >&2
  exit 1
fi
export DISPLAY=":$display_number"

ready=false
while (( SECONDS < readiness_deadline )); do
  xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 &
  readiness_pid=$!
  (
    while (( SECONDS < readiness_deadline )); do
      sleep 0.05
    done
    kill -KILL "$readiness_pid" >/dev/null 2>&1
  ) &
  readiness_watchdog_pid=$!

  probe_status=0
  wait "$readiness_pid" || probe_status=$?
  readiness_pid=""
  if ! kill "$readiness_watchdog_pid" >/dev/null 2>&1; then :; fi
  if ! wait "$readiness_watchdog_pid" >/dev/null 2>&1; then :; fi
  readiness_watchdog_pid=""

  if (( probe_status == 0 )); then
    ready=true
    break
  fi
  sleep 0.05
done

if [ "$ready" != true ]; then
  echo "[test-x11-xvfb] ERROR: Xvfb was not ready within 10 seconds" >&2
  cat "$xvfb_log" >&2
  exit 1
fi

echo "[test-x11-xvfb] Xvfb ready on DISPLAY=$DISPLAY"

set +e
"$repo_root/gradlew" \
  :kadre-x11:jvmTest \
  --tests '*X11NativeIntegrationTest*' \
  --tests '*X11CommonConformanceTest*' \
  --tests '*X11LoopContractTest*' \
  --refresh-dependencies \
  --no-daemon
gradle_status=$?
set -e

exit "$gradle_status"
