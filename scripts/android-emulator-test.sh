#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

has_physical_vulkan_device() {
  local serial="$1"
  local vkjson

  if ! vkjson="$(adb -s "$serial" shell cmd gpu vkjson 2>&1)"; then
    printf 'FAIL: could not inspect Vulkan capability for %s: %s\n' "$serial" "$vkjson" >&2
    return 2
  fi

  python3 -c '
import json
import sys

try:
    document = json.load(sys.stdin)
except json.JSONDecodeError:
    raise SystemExit(1)

devices = document.get("devices")
if not isinstance(devices, list) or len(devices) != 1:
    raise SystemExit(1)

device = devices[0]
if not isinstance(device, dict):
    raise SystemExit(1)

properties = device.get("properties")
if isinstance(properties, dict) and isinstance(properties.get("deviceName"), str) and properties["deviceName"].strip():
    raise SystemExit(0)

raise SystemExit(1)
' <<<"$vkjson"
}

select_android_serial() {
  local serial
  local status
  local -a online_devices=()
  local -a vulkan_devices=()
  local -a inspection_failures=()

  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    if has_physical_vulkan_device "$ANDROID_SERIAL"; then
      return
    fi
    status=$?
    if [[ "$status" -eq 2 ]]; then
      exit "$status"
    fi
    printf 'FAIL: ANDROID_SERIAL=%s is not Vulkan-capable; cmd gpu vkjson must describe a physical Vulkan device\n' "$ANDROID_SERIAL" >&2
    exit 1
  fi

  while read -r serial status _; do
    if [[ "$status" == "device" ]]; then
      online_devices+=("$serial")
    fi
  done < <(adb devices)

  for serial in "${online_devices[@]}"; do
    if has_physical_vulkan_device "$serial"; then
      vulkan_devices+=("$serial")
    else
      status=$?
      if [[ "$status" -eq 2 ]]; then
        inspection_failures+=("$serial")
      fi
    fi
  done

  if [[ "${#inspection_failures[@]}" -ne 0 ]]; then
    printf 'FAIL: could not inspect Vulkan capability for online Android device(s): %s\n' "${inspection_failures[*]}" >&2
    exit 1
  fi

  if [[ "${#vulkan_devices[@]}" -ne 1 ]]; then
    printf 'FAIL: expected exactly one Vulkan-capable online Android device, found %d\n' "${#vulkan_devices[@]}" >&2
    exit 1
  fi

  export ANDROID_SERIAL="${vulkan_devices[0]}"
}

select_android_serial

./gradlew \
  :kadre-android:testAndroidHostTest \
  :samples:hello-triangle-android-capture:connectedDebugAndroidTest \
  --refresh-dependencies \
  --no-daemon --stacktrace --console=plain

result_dir="samples/hello-triangle-android-capture/build/outputs/androidTest-results/connected"
capture="$(find samples/hello-triangle-android-capture/build/outputs -type f -name 'hello-triangle-android.png' -print -quit)"
if [[ -z "$capture" ]]; then
  echo "FAIL: Android emulator run produced no hello-triangle-android.png capture" >&2
  exit 1
fi

python3 "$script_dir/verify-test-results.py" --junit "$result_dir" --png "$capture" --png-target android-triangle
