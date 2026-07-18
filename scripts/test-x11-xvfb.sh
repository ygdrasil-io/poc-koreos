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
display_lock=""
xvfb_pid=""

cleanup() {
  local status=$?
  trap - EXIT
  set +e
  if [ -n "$xvfb_pid" ]; then
    if kill -0 "$xvfb_pid" >/dev/null 2>&1; then
      kill "$xvfb_pid" >/dev/null 2>&1
    fi
    wait "$xvfb_pid" >/dev/null 2>&1
  fi
  if [ -n "$temporary_directory" ]; then
    rm -rf "$temporary_directory"
  fi
  if [ -n "$display_lock" ]; then
    rmdir "$display_lock" >/dev/null 2>&1
  fi
  exit "$status"
}
trap cleanup EXIT

display_number=""
for ((candidate = 90; candidate <= 199; candidate += 1)); do
  candidate_lock="${TMPDIR:-/tmp}/kadre-xvfb-display-${candidate}.lock"
  if [ -e "/tmp/.X${candidate}-lock" ] || [ -S "/tmp/.X11-unix/X${candidate}" ]; then
    continue
  fi
  if mkdir "$candidate_lock" 2>/dev/null; then
    if [ -e "/tmp/.X${candidate}-lock" ] || [ -S "/tmp/.X11-unix/X${candidate}" ]; then
      rmdir "$candidate_lock"
      continue
    fi
    display_number=$candidate
    display_lock=$candidate_lock
    break
  fi
done

if [ -z "$display_number" ]; then
  echo "[test-x11-xvfb] ERROR: no free X11 display in :90-:199" >&2
  exit 1
fi

temporary_directory=$(mktemp -d "${TMPDIR:-/tmp}/kadre-xvfb.XXXXXX")
export DISPLAY=":$display_number"
export XAUTHORITY="$temporary_directory/Xauthority"
xvfb_log="$temporary_directory/Xvfb.log"

touch "$XAUTHORITY"
chmod 600 "$XAUTHORITY"
cookie=$(od -An -N16 -tx1 /dev/urandom | tr -d '[:space:]')
xauth -f "$XAUTHORITY" add "$DISPLAY" MIT-MAGIC-COOKIE-1 "$cookie" >/dev/null

Xvfb "$DISPLAY" \
  -screen 0 1280x720x24 \
  -nolisten tcp \
  -auth "$XAUTHORITY" \
  >"$xvfb_log" 2>&1 &
xvfb_pid=$!

deadline=$((SECONDS + 10))
while ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; do
  if ! kill -0 "$xvfb_pid" >/dev/null 2>&1; then
    echo "[test-x11-xvfb] ERROR: Xvfb exited before becoming ready" >&2
    cat "$xvfb_log" >&2
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    echo "[test-x11-xvfb] ERROR: Xvfb was not ready within 10 seconds" >&2
    cat "$xvfb_log" >&2
    exit 1
  fi
  sleep 0.1
done

echo "[test-x11-xvfb] Xvfb ready on DISPLAY=$DISPLAY"

set +e
"$repo_root/gradlew" \
  :kadre-x11:jvmTest \
  --tests '*X11NativeIntegrationTest*' \
  --tests '*X11LoopContractTest*' \
  --no-daemon
gradle_status=$?
set -e

exit "$gradle_status"
