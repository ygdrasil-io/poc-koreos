#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "[ci-wayland-runtime] ERROR: missing command to execute" >&2
  exit 2
fi

if ! command -v weston >/dev/null 2>&1; then
  echo "[ci-wayland-runtime] ERROR: weston is not installed" >&2
  exit 1
fi

export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/kadre-wayland-runtime}"
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

SOCKET_NAME="${KADRE_WAYLAND_SOCKET:-wayland-ci}"
SOCKET_PATH="$XDG_RUNTIME_DIR/$SOCKET_NAME"
WESTON_LOG="${WESTON_LOG:-/tmp/kadre-weston.log}"
WESTON_STDOUT="${WESTON_STDOUT:-/tmp/kadre-weston.stdout}"

cleanup_weston() {
  if [ -n "${WESTON_PID:-}" ] && kill -0 "$WESTON_PID" >/dev/null 2>&1; then
    if ! kill "$WESTON_PID" >/dev/null 2>&1; then :; fi
    if ! wait "$WESTON_PID" >/dev/null 2>&1; then :; fi
  fi
}

print_weston_logs() {
  echo "[ci-wayland-runtime] Weston log: $WESTON_LOG" >&2
  if [ -f "$WESTON_LOG" ]; then cat "$WESTON_LOG"; fi
  echo "[ci-wayland-runtime] Weston stdout/stderr: $WESTON_STDOUT" >&2
  if [ -f "$WESTON_STDOUT" ]; then cat "$WESTON_STDOUT"; fi
}

trap cleanup_weston EXIT INT TERM

rm -f "$SOCKET_PATH"

weston \
  --backend=headless-backend.so \
  --renderer=pixman \
  --width=800 \
  --height=600 \
  --socket="$SOCKET_NAME" \
  --idle-time=0 \
  --log="$WESTON_LOG" \
  >"$WESTON_STDOUT" 2>&1 &
WESTON_PID=$!

for _ in {1..60}; do
  if [ -S "$SOCKET_PATH" ]; then
    break
  fi
  if ! kill -0 "$WESTON_PID" >/dev/null 2>&1; then
    echo "[ci-wayland-runtime] ERROR: Weston exited before creating socket: $SOCKET_PATH" >&2
    print_weston_logs
    exit 1
  fi
  sleep 0.5
done

if [ ! -S "$SOCKET_PATH" ]; then
  echo "[ci-wayland-runtime] ERROR: Wayland socket did not appear: $SOCKET_PATH" >&2
  print_weston_logs
  exit 1
fi

export WAYLAND_DISPLAY="$SOCKET_NAME"
export KADRE_LINUX_BACKEND=wayland
unset KADRE_WAYLAND_DISABLE_NATIVE

(
  watched_pid=$$
  trap '' INT TERM
  while kill -0 "$watched_pid" >/dev/null 2>&1; do
    sleep 1
  done
  cleanup_weston
) &

trap - EXIT INT TERM

echo "[ci-wayland-runtime] Weston ready on WAYLAND_DISPLAY=$WAYLAND_DISPLAY"
exec "$@"
