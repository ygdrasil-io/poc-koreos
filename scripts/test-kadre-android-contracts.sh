#!/usr/bin/env bash
set -euo pipefail
# Orchestration locale du gate Android (phase 0). Le branchement CI est la phase 10.
# Serial : forme documentée `--serial <serial>` (roadmap) ou argument positionnel
# (README du driver) ; sans argument, l'auto-détection adb reste inchangée.
if [ "${1:-}" = "--serial" ]; then
  SERIAL="${2:-}"
else
  SERIAL="${1:-}"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"

if [ -z "$SERIAL" ]; then
  if ! "$ADB" devices | grep -q "device$"; then
    "$ANDROID_HOME/emulator/emulator" -avd Kadre_API_35 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect > /tmp/kadre-emulator.log 2>&1 &
    "$ADB" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
  fi
else
  "$ADB" -s "$SERIAL" wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
fi

cd "$(dirname "$0")/.."
# `timeout` est GNU-only (absent du macOS stock) : watchdog portable — gradle en arrière-plan,
# attente bornée à 900 s, SIGTERM à l'expiration (code 124, même sémantique que GNU timeout ;
# le daemon Gradle, comme sous GNU timeout, survit au client tué).
./gradlew :kadre:contracts:validator:androidContractsCheck --console=plain &
gradle_pid=$!
elapsed=0
while kill -0 "$gradle_pid" 2>/dev/null; do
  if [ "$elapsed" -ge 900 ]; then
    kill "$gradle_pid" 2>/dev/null
    wait "$gradle_pid" 2>/dev/null || true
    echo "test-kadre-android-contracts: gradle timed out after 900s" >&2
    exit 124
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done
wait "$gradle_pid"
