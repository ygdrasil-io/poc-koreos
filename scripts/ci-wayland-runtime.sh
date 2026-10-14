#!/usr/bin/env bash
# Lance ou arrête un Sway headless isolé (wlroots headless, même famille
# d'O3 que le harnais kffi) pour la gate Wayland.
#   ci-wayland-runtime.sh start   — démarre le compositor, attend le socket,
#                                   écrit build/wayland/weston.env (sourçable)
#   ci-wayland-runtime.sh stop    — arrête proprement
#   ci-wayland-runtime.sh version — affiche `sway --version` (preuve B-3)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WAYLAND_DIR="$REPO_ROOT/build/wayland"
RUNTIME_DIR="${KADRE_WAYLAND_RUNTIME_DIR:-/tmp/kadre-wayland-runtime}"
SOCKET_NAME="${KADRE_WAYLAND_SOCKET:-kadre-wayland-ci}"
PID_FILE="$WAYLAND_DIR/sway.pid"
LOG_FILE="$WAYLAND_DIR/sway.log"
ENV_FILE="$WAYLAND_DIR/weston.env"

mkdir -p "$WAYLAND_DIR"

case "${1:-}" in
start)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "Sway already running (pid $(cat "$PID_FILE"))." >&2
        exit 1
    fi
    command -v sway >/dev/null || { echo "sway is not installed." >&2; exit 1; }
    mkdir -p "$RUNTIME_DIR"
    chmod 700 "$RUNTIME_DIR"
    rm -f "$ENV_FILE"
    SWAY_CONFIG="$RUNTIME_DIR/sway.config"
    cat > "$SWAY_CONFIG" <<'CONFIG'
output HEADLESS-1 mode 800x600
output HEADLESS-1 position 0 0
output HEADLESS-1 scale 1
output HEADLESS-1 transform normal
output HEADLESS-1 bg #000000 solid_color
workspace 1 output HEADLESS-1
seat seat0 fallback true
CONFIG
    umask 077
    SWAY_SOCKET_NAME="$SOCKET_NAME" XDG_RUNTIME_DIR="$RUNTIME_DIR" \
    WLR_BACKENDS=headless WLR_RENDERER=pixman WLR_HEADLESS_OUTPUTS=1 \
    WLR_LIBINPUT_NO_DEVICES=1 \
        sway --config "$SWAY_CONFIG" >"$LOG_FILE" 2>&1 &
    SWAY_PID=$!
    echo "$SWAY_PID" > "$PID_FILE"
    # sway nomme son socket lui-même (wayland-N) : on détecte le nom réel
    for _ in $(seq 1 240); do
        SOCKET_PATH="$(find "$RUNTIME_DIR" -maxdepth 1 -type s -name 'wayland-*' 2>/dev/null | sort | tail -1 || true)"
        if [[ -n "$SOCKET_PATH" && -S "$SOCKET_PATH" ]]; then
            ACTUAL_SOCKET="$(basename "$SOCKET_PATH")"
            cat > "$ENV_FILE" <<ENV
export XDG_RUNTIME_DIR="$RUNTIME_DIR"
export WAYLAND_DISPLAY="$ACTUAL_SOCKET"
ENV
            echo "Sway ready: socket $SOCKET_PATH (pid $SWAY_PID)."
            exit 0
        fi
        if ! kill -0 "$SWAY_PID" 2>/dev/null; then
            echo "Sway exited before publishing its socket; log: $LOG_FILE" >&2
            exit 1
        fi
        sleep 0.25
    done
    echo "Sway did not publish its socket within 60s; log: $LOG_FILE" >&2
    exit 1
    ;;
stop)
    if [[ -f "$PID_FILE" ]]; then
        PID="$(cat "$PID_FILE")"
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID"
            wait "$PID" 2>/dev/null || true
            echo "Sway stopped (pid $PID)."
        fi
        rm -f "$PID_FILE"
    fi
    ;;
version)
    sway --version
    ;;
*)
    echo "Usage: $0 {start|stop|version}" >&2
    exit 64
    ;;
esac
