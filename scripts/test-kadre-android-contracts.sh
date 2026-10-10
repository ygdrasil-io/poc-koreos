#!/usr/bin/env bash
set -euo pipefail
# Orchestration locale du gate Android (phase 0). Le branchement CI est la phase 10.
SERIAL="${2:-}"
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
timeout 900 ./gradlew :kadre:contracts:validator:androidContractsCheck --console=plain
