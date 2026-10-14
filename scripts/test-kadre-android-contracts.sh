#!/usr/bin/env bash
set -euo pipefail
# Orchestration locale du gate Android (phases 0-9). Le branchement CI est la phase 10.
#
# Sélection et checks délégués à scripts/test-kadre-android-device.sh (sourcé) :
#   - serial explicite (forme documentée `--serial <serial>` — roadmap — ou
#     positionnel, README du driver ; un ANDROID_SERIAL exporté est aussi honoré)
#     validé contre `adb devices` : état exactement "device", sinon refus ;
#   - sans serial : auto-détection refusant toute ambiguïté (exactement un device
#     éligible ; éligible = booté) ;
#   - boot incomplet (sys.boot_completed != 1) : attente bornée puis fast-fail —
#     la boucle sans fin de la phase 0 (boot-incomplet = hang) est supprimée, et
#     l'attente de l'émulateur lancé par le script est bornée elle aussi.
# L'invocation Gradle passe par run_with_watchdog : le client tourne dans son
# propre process group (setsid sur Linux CI, sous-shell d'arrière-plan + marche
# pgrep -P sinon) ; à l'expiration (900 s, code 124 — même sémantique que GNU
# `timeout`, absent du macOS stock) le groupe reçoit TERM puis KILL. Le daemon
# Gradle et ses descendants ne survivent plus au client tué : la note
# « daemon survit » du watchdog de la phase 0 (76229d58) est obsolète.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
# shellcheck source=test-kadre-android-device.sh
source "$script_dir/test-kadre-android-device.sh"

if [ "${1:-}" = "--serial" ]; then
  SERIAL="${2:-}"
else
  SERIAL="${1:-}"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
require_adb

boot_timeout="${KADRE_ANDROID_BOOT_TIMEOUT_S:-300}"

if [ -z "$SERIAL" ] && [ -n "${ANDROID_SERIAL:-}" ]; then
  SERIAL="$ANDROID_SERIAL"
fi

if [ -z "$SERIAL" ]; then
  if ! _adb_has_device_in_state device; then
    "$ANDROID_HOME/emulator/emulator" -avd Kadre_API_35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > /tmp/kadre-emulator.log 2>&1 &
    emulator_pid=$!
    SERIAL="$(wait_for_emulator_serial "$emulator_pid" "${KADRE_ANDROID_EMULATOR_START_TIMEOUT_S:-120}")"
  fi
fi

if [ -n "$SERIAL" ]; then
  export ANDROID_SERIAL="$SERIAL"
fi

select_android_serial
ensure_booted_device "$ANDROID_SERIAL" "$boot_timeout"

# Phase 11 (AND-011) : l'écran simulé — le provisioning AVANT la gate, le retrait
# APRÈS (trap : toute issue de la gate, succès comme échec, rend l'écran au système).
# Les scénarios instrumentés pollent l'inventaire DisplayManager eux-mêmes (≤ 10 s) :
# le script ne dort jamais sur la création asynchrone du display overlay.
provision_overlay_display
trap 'remove_overlay_display' EXIT INT TERM

cd "$repo_root"
gradle_status=0
run_with_watchdog "${KADRE_ANDROID_WATCHDOG_TIMEOUT_S:-900}" \
  ./gradlew :kadre:contracts:validator:androidContractsCheck --console=plain || gradle_status=$?
exit "$gradle_status"
