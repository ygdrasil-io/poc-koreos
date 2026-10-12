#!/usr/bin/env bash
# Lance ou arrête un Weston headless isolé (baseline B-5 de
# kadre/WAYLAND-PHASE-0-KFFI-AUDIT.md) pour la gate Wayland.
#   ci-wayland-runtime.sh start   — démarre le compositor, attend le socket,
#                                   écrit build/wayland/weston.env (sourçable)
#   ci-wayland-runtime.sh stop    — arrête proprement
#   ci-wayland-runtime.sh version — affiche `weston --version` (preuve B-3)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WAYLAND_DIR="$REPO_ROOT/build/wayland"
RUNTIME_DIR="${KADRE_WAYLAND_RUNTIME_DIR:-/tmp/kadre-wayland-runtime}"
SOCKET_NAME="${KADRE_WAYLAND_SOCKET:-kadre-wayland-ci}"
PID_FILE="$WAYLAND_DIR/weston.pid"
LOG_FILE="$WAYLAND_DIR/weston.log"
ENV_FILE="$WAYLAND_DIR/weston.env"

mkdir -p "$WAYLAND_DIR"

case "${1:-}" in
start)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "Weston already running (pid $(cat "$PID_FILE"))." >&2
        exit 1
    fi
    command -v weston >/dev/null || { echo "weston is not installed." >&2; exit 1; }
    mkdir -p "$RUNTIME_DIR"
    chmod 700 "$RUNTIME_DIR"
    rm -f "$RUNTIME_DIR/$SOCKET_NAME" "$ENV_FILE"
    XDG_RUNTIME_DIR="$RUNTIME_DIR" weston \
        --backend=headless-backend.so \
        --renderer=pixman \
        --width=800 \
        --height=600 \
        "--socket=$SOCKET_NAME" \
        --idle-time=0 \
        >"$LOG_FILE" 2>&1 &
    WESTON_PID=$!
    echo "$WESTON_PID" > "$PID_FILE"
    for _ in $(seq 1 240); do
        if [[ -S "$RUNTIME_DIR/$SOCKET_NAME" ]]; then
            cat > "$ENV_FILE" <<ENV
export XDG_RUNTIME_DIR="$RUNTIME_DIR"
export WAYLAND_DISPLAY="$SOCKET_NAME"
ENV
            echo "Weston ready: socket $RUNTIME_DIR/$SOCKET_NAME (pid $WESTON_PID)."
            exit 0
        fi
        if ! kill -0 "$WESTON_PID" 2>/dev/null; then
            echo "Weston exited before publishing its socket; log: $LOG_FILE" >&2
            exit 1
        fi
        sleep 0.25
    done
    echo "Weston did not publish its socket within 60s; log: $LOG_FILE" >&2
    exit 1
    ;;
stop)
    if [[ -f "$PID_FILE" ]]; then
        PID="$(cat "$PID_FILE")"
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID"
            wait "$PID" 2>/dev/null || true
            echo "Weston stopped (pid $PID)."
        fi
        rm -f "$PID_FILE"
    fi
    ;;
version)
    weston --version
    ;;
*)
    echo "Usage: $0 {start|stop|version}" >&2
    exit 64
    ;;
esac
