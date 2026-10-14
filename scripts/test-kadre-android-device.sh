#!/usr/bin/env bash
# Android device selection + portable process-group watchdog (phase 10, task 1).
#
# Sourced (by scripts/test-kadre-android-contracts.sh and the self-test) it
# exposes:
#   select_android_serial    — explicit ANDROID_SERIAL validated against
#                              `adb devices` (state exactly "device"), else
#                              auto-detection refusing any ambiguity
#                              (exactly one eligible device; eligible = booted);
#   ensure_booted_device     — bounded wait for sys.boot_completed=1, fast-fail
#                              afterwards (the phase-0 unbounded loop is gone);
#   run_with_watchdog        — runs a command in its own process group
#                              (`setsid` where available — Linux CI — else a
#                              backgrounded subshell exec'ing the command),
#                              polls `kill -0`, TERMs the whole group at the
#                              deadline, escalates to KILL, exits 124 on timeout
#                              (GNU timeout semantics; no perl, no GNU-only
#                              tools — portable macOS + Linux).
# Executed directly it performs selection + boot fast-fail, then either runs
# `-- <command...>` under the watchdog or prints the resolved serial.
#
# Environment: KADRE_ANDROID_ADB, ANDROID_SERIAL, ANDROID_HOME,
#   KADRE_ANDROID_BOOT_TIMEOUT_S (60), KADRE_ANDROID_BOOT_POLL_S (2),
#   KADRE_ANDROID_WATCHDOG_TIMEOUT_S (900), KADRE_WATCHDOG_POLL_S (1),
#   KADRE_WATCHDOG_KILL_GRACE_S (10), KADRE_WATCHDOG_MODE (auto|setsid|subshell).

set -euo pipefail

ADB="${ADB:-}"

resolve_adb() {
  if [ -n "${KADRE_ANDROID_ADB:-}" ]; then
    ADB="$KADRE_ANDROID_ADB"
  elif command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
  else
    ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
    ADB="$ANDROID_HOME/platform-tools/adb"
  fi
  if [ ! -x "$ADB" ]; then
    echo "test-kadre-android-device: adb not found at '$ADB' (set KADRE_ANDROID_ADB, put adb on PATH, or set ANDROID_HOME)" >&2
    return 1
  fi
}

require_adb() {
  if [ -z "$ADB" ]; then
    resolve_adb || return 1
  fi
  if [ ! -x "$ADB" ]; then
    echo "test-kadre-android-device: adb not executable at '$ADB'" >&2
    return 1
  fi
}

_adb_devices_output() {
  # adb may print daemon-start noise on stdout; the awk filters only keep
  # "<serial> <state>" table rows. Never aborts: an adb failure is handled by
  # the callers as "no devices reported".
  "$ADB" devices 2>/dev/null || true
}

_adb_device_lines_in_state() { # <state> — prints "<serial> <state>" rows
  _adb_devices_output | awk -v want="$1" 'NR > 1 && NF >= 2 && $2 == want { print $1 " " $2 }'
}

_adb_has_device_in_state() { # <state> — 0 when at least one row matches
  _adb_devices_output | awk -v want="$1" 'NR > 1 && NF >= 2 && $2 == want { found = 1; exit } END { exit !found }'
}

_adb_state_of() { # <serial> — prints the state column, empty when absent
  _adb_devices_output | awk -v serial="$1" 'NR > 1 && NF >= 2 && $1 == serial { print $2; exit }'
}

_boot_probe() { # <serial> — prints sys.boot_completed, never aborts
  "$ADB" -s "$1" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true
}

select_android_serial() {
  require_adb || return 1
  local explicit="${ANDROID_SERIAL:-}"
  if [ -n "$explicit" ]; then
    local state
    state="$(_adb_state_of "$explicit")"
    if [ "$state" != "device" ]; then
      echo "test-kadre-android-device: ANDROID_SERIAL=$explicit is not an Android device in 'device' state (adb devices reports: ${state:-absent})" >&2
      return 1
    fi
    export ANDROID_SERIAL="$explicit"
    return 0
  fi

  local serial boot_value state
  local -a eligible=() unbooted=()
  while IFS=" " read -r serial state; do
    [ -n "$serial" ] || continue
    boot_value="$(_boot_probe "$serial")"
    if [ "$boot_value" = "1" ]; then
      eligible+=("$serial")
    else
      unbooted+=("$serial")
    fi
  done <<<"$(_adb_device_lines_in_state device)"

  local count="${#eligible[@]}"
  if [ "$count" -eq 1 ]; then
    export ANDROID_SERIAL="${eligible[0]}"
    return 0
  fi
  if [ "$count" -eq 0 ]; then
    if [ "${#unbooted[@]}" -gt 0 ]; then
      echo "test-kadre-android-device: expected exactly one eligible Android device, found 0 (candidate(s) not booted: ${unbooted[*]})" >&2
    else
      echo "test-kadre-android-device: expected exactly one eligible Android device, found 0" >&2
    fi
    return 1
  fi
  echo "test-kadre-android-device: expected exactly one eligible Android device, found $count (ambiguous: ${eligible[*]})" >&2
  return 1
}

ensure_booted_device() { # <serial> [timeout-seconds]
  require_adb || return 1
  local serial="${1:?ensure_booted_device requires a serial}"
  local timeout_s="${2:-${KADRE_ANDROID_BOOT_TIMEOUT_S:-60}}"
  local poll_s="${KADRE_ANDROID_BOOT_POLL_S:-2}"
  local waited="0" boot_value
  while :; do
    boot_value="$(_boot_probe "$serial")"
    if [ "$boot_value" = "1" ]; then
      return 0
    fi
    if awk -v waited="$waited" -v timeout="$timeout_s" 'BEGIN { exit !(waited >= timeout) }'; then
      echo "test-kadre-android-device: device $serial did not report sys.boot_completed=1 within ${timeout_s}s (boot incomplete: '$boot_value')" >&2
      return 1
    fi
    sleep "$poll_s"
    waited="$(awk -v waited="$waited" -v poll="$poll_s" 'BEGIN { printf "%.3f", waited + poll }')"
  done
}

wait_for_emulator_serial() { # <launcher-pid> [timeout-seconds] — prints the serial
  local launcher_pid="${1:?wait_for_emulator_serial requires the emulator pid}"
  local timeout_s="${2:-${KADRE_ANDROID_EMULATOR_START_TIMEOUT_S:-120}}"
  local poll_s="${KADRE_ANDROID_BOOT_POLL_S:-2}"
  require_adb || return 1
  local waited="0" serial
  while :; do
    if ! kill -0 "$launcher_pid" 2>/dev/null; then
      echo "test-kadre-android-device: emulator process $launcher_pid exited before advertising a device (see /tmp/kadre-emulator.log)" >&2
      return 1
    fi
    serial="$(_adb_device_lines_in_state device | awk '$1 ~ /^emulator-/ { print $1; exit }')"
    if [ -n "$serial" ]; then
      printf '%s\n' "$serial"
      return 0
    fi
    if awk -v waited="$waited" -v timeout="$timeout_s" 'BEGIN { exit !(waited >= timeout) }'; then
      echo "test-kadre-android-device: no emulator advertised as 'device' within ${timeout_s}s" >&2
      return 1
    fi
    sleep "$poll_s"
    waited="$(awk -v waited="$waited" -v poll="$poll_s" 'BEGIN { printf "%.3f", waited + poll }')"
  done
}

# --- portable process-group watchdog -----------------------------------------

_watchdog_descendants() { # <pid> — prints descendant pids, breadth-first (pgrep -P walk)
  if ! command -v pgrep >/dev/null 2>&1; then
    echo "run_with_watchdog: pgrep unavailable; only the direct child can be signalled" >&2
    return 0
  fi
  local pid="$1" depth=0 child grandchild
  local -a frontier=("$pid") next=()
  while [ "${#frontier[@]}" -gt 0 ] && [ "$depth" -lt 32 ]; do
    next=()
    for child in "${frontier[@]}"; do
      while IFS= read -r grandchild; do
        [ -n "$grandchild" ] || continue
        printf '%s\n' "$grandchild"
        next+=("$grandchild")
      done <<<"$(pgrep -P "$child" 2>/dev/null || true)"
    done
    frontier=("${next[@]+${next[@]}}")
    depth=$((depth + 1))
  done
}

_watchdog_wait_pids_dead() { # <grace-seconds> <pid...> — 0 when all gone
  local grace="$1"
  shift
  local waited="0" alive pid
  while :; do
    alive=0
    for pid in "$@"; do
      if kill -0 -- "$pid" 2>/dev/null; then
        alive=1
      fi
    done
    if [ "$alive" -eq 0 ]; then
      return 0
    fi
    if awk -v waited="$waited" -v grace="$grace" 'BEGIN { exit !(waited >= grace) }'; then
      return 1
    fi
    sleep 0.1
    waited="$(awk -v waited="$waited" 'BEGIN { printf "%.3f", waited + 0.1 }')"
  done
}

_watchdog_kill_tree() { # <pid> <grouped-yes/no> — TERM then KILL, warns on survival
  local pid="$1" grouped="$2"
  local grace="${KADRE_WATCHDOG_KILL_GRACE_S:-10}"
  local signal target
  for signal in TERM KILL; do
    if [ "$grouped" = "yes" ]; then
      kill "-$signal" -- "-$pid" 2>/dev/null || kill "-$signal" -- "$pid" 2>/dev/null || true
      if _watchdog_wait_pids_dead "$grace" "-$pid"; then
        return 0
      fi
    else
      local -a targets=()
      while IFS= read -r target; do
        [ -n "$target" ] && targets+=("$target")
      done <<<"$(_watchdog_descendants "$pid"; printf '%s\n' "$pid")"
      for target in "${targets[@]}"; do
        kill "-$signal" -- "$target" 2>/dev/null || true
      done
      if _watchdog_wait_pids_dead "$grace" "${targets[@]}"; then
        return 0
      fi
    fi
  done
  echo "run_with_watchdog: process group of $pid survived the final KILL" >&2
  return 1
}

_watchdog_handle_signal() {
  local status="$2"
  trap - TERM INT HUP
  _watchdog_signal_child "${_WATCHDOG_CHILD_PID:-}" "${_WATCHDOG_GROUPED:-no}" "$1" 2>/dev/null || true
  exit "$status"
}

_watchdog_signal_child() { # <pid> <grouped-yes/no> <signal>
  local pid="$1" grouped="$2" signal="$3"
  if [ "$grouped" = "yes" ]; then
    kill "-$signal" -- "-$pid" 2>/dev/null || kill "-$signal" -- "$pid" 2>/dev/null || true
  else
    kill "-$signal" -- "$pid" 2>/dev/null || true
  fi
}

run_with_watchdog() { # <timeout-seconds> <command...>
  local timeout_s="${1:-}"
  if [ "$#" -gt 0 ]; then
    shift
  fi
  if [ -z "$timeout_s" ] || ! printf '%s' "$timeout_s" | grep -Eq '^[1-9][0-9]*$'; then
    echo "run_with_watchdog: timeout must be a positive integer" >&2
    return 2
  fi
  if [ "$#" -eq 0 ]; then
    echo "run_with_watchdog: missing command" >&2
    return 2
  fi

  local mode="${KADRE_WATCHDOG_MODE:-auto}"
  local child_pid grouped="no"
  case "$mode" in
    auto)
      if command -v setsid >/dev/null 2>&1; then
        # Non-interactive shells run background jobs without job control, so the
        # setsid process is not a group leader, calls setsid(2) successfully and
        # execs the command: $! is the command and its own process group.
        setsid "$@" &
        child_pid=$!
        grouped="yes"
      else
        # Stock macOS has no setsid: a backgrounded subshell execs the command
        # (same pid) and the whole descendant tree is signalled instead.
        (exec "$@") &
        child_pid=$!
      fi
      ;;
    setsid)
      if ! command -v setsid >/dev/null 2>&1; then
        echo "run_with_watchdog: setsid unavailable" >&2
        return 2
      fi
      setsid "$@" &
      child_pid=$!
      grouped="yes"
      ;;
    subshell)
      (exec "$@") &
      child_pid=$!
      ;;
    *)
      echo "run_with_watchdog: unknown KADRE_WATCHDOG_MODE '$mode' (auto|setsid|subshell)" >&2
      return 2
      ;;
  esac

  local poll_s="${KADRE_WATCHDOG_POLL_S:-1}"
  _WATCHDOG_CHILD_PID="$child_pid"
  _WATCHDOG_GROUPED="$grouped"
  trap '_watchdog_handle_signal TERM 143' TERM
  trap '_watchdog_handle_signal INT 130' INT
  trap '_watchdog_handle_signal HUP 129' HUP

  local waited="0"
  while kill -0 -- "$child_pid" 2>/dev/null; do
    if awk -v waited="$waited" -v timeout="$timeout_s" 'BEGIN { exit !(waited >= timeout) }'; then
      _watchdog_kill_tree "$child_pid" "$grouped" || true
      wait "$child_pid" 2>/dev/null || true
      echo "run_with_watchdog: command timed out after ${timeout_s}s (process group of $child_pid terminated)" >&2
      trap - TERM INT HUP
      return 124
    fi
    sleep "$poll_s"
    waited="$(awk -v waited="$waited" -v poll="$poll_s" 'BEGIN { printf "%.3f", waited + poll }')"
  done

  trap - TERM INT HUP
  local status=0
  wait "$child_pid" 2>/dev/null || status=$?
  return "$status"
}

# --- direct execution ---------------------------------------------------------

usage() {
  cat >&2 <<'EOF'
usage: test-kadre-android-device.sh [--serial <serial>] [--boot-timeout <s>]
                                    [--watchdog-timeout <s>] [-- <command...>]

Selects one Android device before anything else runs:
  - explicit ANDROID_SERIAL / --serial is validated against `adb devices`
    (state must be exactly "device"; offline/unauthorized/unknown are refused);
  - without a serial, auto-detection requires exactly one eligible device
    (eligible = state "device" AND sys.boot_completed=1) and refuses ambiguity;
  - the selected device must complete boot (sys.boot_completed=1) within the
    boot timeout — a bounded fast-fail, never an unbounded loop.
With `-- <command...>` the command then runs under the portable process-group
watchdog (exit 124 on timeout); without `--`, the resolved serial is printed.
EOF
}

main() {
  local serial="" boot_timeout="${KADRE_ANDROID_BOOT_TIMEOUT_S:-60}"
  local watchdog_timeout="${KADRE_ANDROID_WATCHDOG_TIMEOUT_S:-900}"
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --serial)
        [ "$#" -ge 2 ] || { usage; return 2; }
        serial="$2"
        shift 2
        ;;
      --serial=*)
        serial="${1#--serial=}"
        shift
        ;;
      --boot-timeout)
        [ "$#" -ge 2 ] || { usage; return 2; }
        boot_timeout="$2"
        shift 2
        ;;
      --boot-timeout=*)
        boot_timeout="${1#--boot-timeout=}"
        shift
        ;;
      --watchdog-timeout)
        [ "$#" -ge 2 ] || { usage; return 2; }
        watchdog_timeout="$2"
        shift 2
        ;;
      --watchdog-timeout=*)
        watchdog_timeout="${1#--watchdog-timeout=}"
        shift
        ;;
      -h | --help)
        usage
        return 0
        ;;
      --)
        shift
        break
        ;;
      *)
        usage
        return 2
        ;;
    esac
  done

  if [ -n "$serial" ]; then
    export ANDROID_SERIAL="$serial"
  fi
  require_adb || return 1
  select_android_serial || return 1
  ensure_booted_device "$ANDROID_SERIAL" "$boot_timeout" || return 1
  echo "test-kadre-android-device: selected ANDROID_SERIAL=$ANDROID_SERIAL (booted)" >&2
  if [ "$#" -eq 0 ]; then
    printf '%s\n' "$ANDROID_SERIAL"
    return 0
  fi
  run_with_watchdog "$watchdog_timeout" "$@"
}

if [ "${BASH_SOURCE[0]}" != "$0" ]; then
  :
else
  main "$@"
fi
