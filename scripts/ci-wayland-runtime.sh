#!/usr/bin/env bash
# Lance ou arrête un compositor headless isolé pour la gate Wayland.
#   ci-wayland-runtime.sh start   — démarre le compositor, attend le socket,
#                                   écrit build/wayland/weston.env (sourçable)
#   ci-wayland-runtime.sh stop    — arrête proprement
#   ci-wayland-runtime.sh version — affiche la version réelle (preuve B-3)
#
# KADRE_WAYLAND_COMPOSITOR sélectionne la famille (design
# WAYLAND-PHASE-11-CLOSURE-DESIGN D1) : sway (défaut, wlroots — gate
# bloquante), weston (déterminisme historique B-5), mutter (famille
# GNOME, 46+ : bus D-Bus privé + moniteur virtuel), kwin (famille KDE,
# 5.27+ : bus D-Bus privé + sortie virtuelle). Chaque famille respecte
# le même contrat start|stop|version + fichier d'env.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
WAYLAND_DIR="$REPO_ROOT/build/wayland"
ENV_FILE="$WAYLAND_DIR/weston.env"

COMPOSITOR="${KADRE_WAYLAND_COMPOSITOR:-sway}"
RUNTIME_DIR="${KADRE_WAYLAND_RUNTIME_DIR:-/tmp/kadre-wayland-runtime-$COMPOSITOR}"
PID_FILE="$WAYLAND_DIR/$COMPOSITOR.pid"
LOG_FILE="$WAYLAND_DIR/$COMPOSITOR.log"
DBUS_PID_FILE="$WAYLAND_DIR/$COMPOSITOR-dbus.pid"

mkdir -p "$WAYLAND_DIR"

wait_for_socket() {
    local pid="$1"
    local socket_path
    for _ in $(seq 1 240); do
        socket_path="$(find "$RUNTIME_DIR" -maxdepth 1 -type s -name 'wayland-*' 2>/dev/null | sort | tail -1 || true)"
        if [[ -n "$socket_path" && -S "$socket_path" ]]; then
            cat > "$ENV_FILE" <<ENV
export XDG_RUNTIME_DIR="$RUNTIME_DIR"
export WAYLAND_DISPLAY="$(basename "$socket_path")"
ENV
            echo "$COMPOSITOR ready: socket $socket_path (pid $pid)."
            return 0
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            echo "$COMPOSITOR exited before publishing its socket; log: $LOG_FILE" >&2
            return 1
        fi
        sleep 0.25
    done
    echo "$COMPOSITOR did not publish its socket within 60s; log: $LOG_FILE" >&2
    return 1
}

start_compositor() {
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "$COMPOSITOR already running (pid $(cat "$PID_FILE"))." >&2
        exit 1
    fi
    mkdir -p "$RUNTIME_DIR"
    chmod 700 "$RUNTIME_DIR"
    rm -f "$ENV_FILE"
    local pid
    case "$COMPOSITOR" in
    sway)
        command -v sway >/dev/null || { echo "sway is not installed." >&2; exit 1; }
        local config="$RUNTIME_DIR/sway.config"
        cat > "$config" <<'CONFIG'
output HEADLESS-1 mode 800x600
output HEADLESS-1 position 0 0
output HEADLESS-1 scale 1
output HEADLESS-1 transform normal
output HEADLESS-1 bg #000000 solid_color
workspace 1 output HEADLESS-1
seat seat0 fallback true
CONFIG
        umask 077
        env XDG_RUNTIME_DIR="$RUNTIME_DIR" \
            WLR_BACKENDS=headless WLR_RENDERER=pixman WLR_HEADLESS_OUTPUTS=1 \
            WLR_LIBINPUT_NO_DEVICES=1 \
            sway --config "$config" >"$LOG_FILE" 2>&1 &
        pid=$!
        ;;
    weston)
        command -v weston >/dev/null || { echo "weston is not installed." >&2; exit 1; }
        umask 077
        env XDG_RUNTIME_DIR="$RUNTIME_DIR" \
            WESTON_HEADLESS_WIDTH=800 WESTON_HEADLESS_HEIGHT=600 \
            weston --backend=headless-backend.so --idle-time=0 >"$LOG_FILE" 2>&1 &
        pid=$!
        ;;
    mutter|kwin)
        local compositor_bin
        local -a compositor_args
        if [[ "$COMPOSITOR" == "mutter" ]]; then
            command -v mutter >/dev/null || { echo "mutter is not installed." >&2; exit 1; }
            compositor_bin="mutter"
            compositor_args=(--headless --wayland --virtual-monitor 800x600)
        else
            command -v kwin_wayland >/dev/null || { echo "kwin_wayland is not installed." >&2; exit 1; }
            compositor_bin="kwin_wayland"
            compositor_args=(--virtual --no-lockscreen)
        fi
        command -v dbus-daemon >/dev/null || { echo "dbus-daemon is not installed." >&2; exit 1; }
        # mutter et kwin exigent un bus session : bus PRIVÉ confiné au
        # runtime dir, jamais le bus de l'hôte ; les preuves ne le
        # consomment pas
        umask 077
        rm -f "$RUNTIME_DIR/bus"
        dbus-daemon --session --address="unix:path=$RUNTIME_DIR/bus" \
            --nofork --nopidfile >"$LOG_FILE.dbus" 2>&1 &
        local dbus_pid=$!
        echo "$dbus_pid" > "$DBUS_PID_FILE"
        # le compositeur peut tenter de lancer Xwayland : le répertoire
        # doit exister
        mkdir -p /tmp/.X11-unix && chmod 1777 /tmp/.X11-unix 2>/dev/null || true
        env XDG_RUNTIME_DIR="$RUNTIME_DIR" \
            DBUS_SESSION_BUS_ADDRESS="unix:path=$RUNTIME_DIR/bus" \
            "$compositor_bin" "${compositor_args[@]}" >>"$LOG_FILE" 2>&1 &
        pid=$!
        ;;
    *)
        echo "Unknown KADRE_WAYLAND_COMPOSITOR '$COMPOSITOR' (sway|weston|mutter|kwin)." >&2
        exit 64
        ;;
    esac
    echo "$pid" > "$PID_FILE"
    wait_for_socket "$pid" || { rm -f "$PID_FILE"; exit 1; }
}

case "${1:-}" in
start)
    start_compositor
    ;;
stop)
    if [[ -f "$PID_FILE" ]]; then
        PID="$(cat "$PID_FILE")"
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID"
            wait "$PID" 2>/dev/null || true
            echo "$COMPOSITOR stopped (pid $PID)."
        fi
        rm -f "$PID_FILE"
    fi
    if [[ -f "$DBUS_PID_FILE" ]]; then
        DBUS_PID="$(cat "$DBUS_PID_FILE")"
        if kill -0 "$DBUS_PID" 2>/dev/null; then
            kill "$DBUS_PID"
            wait "$DBUS_PID" 2>/dev/null || true
        fi
        rm -f "$DBUS_PID_FILE"
    fi
    ;;
version)
    case "$COMPOSITOR" in
    sway) sway --version ;;
    weston) weston --version ;;
    mutter) mutter --version ;;
    kwin) kwin_wayland --version ;;
    esac
    ;;
*)
    echo "Usage: $0 {start|stop|version}" >&2
    exit 64
    ;;
esac
