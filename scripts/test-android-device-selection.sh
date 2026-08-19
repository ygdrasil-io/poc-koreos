#!/usr/bin/env bash
set -euo pipefail

# Contract: android-emulator-test.sh must resolve one usable Vulkan device before
# Gradle starts. Removing that preflight, selecting an arbitrary device, or
# ignoring an explicit ANDROID_SERIAL makes one of the cases below fail.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/kadre-android-device-selection.XXXXXX")"
trap 'rm -rf "$fixture_dir"' EXIT

fake_repo="$fixture_dir/repo"
fake_bin="$fixture_dir/bin"
mkdir -p "$fake_repo/scripts" "$fake_bin"
ln -s "$repo_root/scripts/android-emulator-test.sh" "$fake_repo/scripts/android-emulator-test.sh"
ln -s "$repo_root/scripts/verify-test-results.py" "$fake_repo/scripts/verify-test-results.py"

cat >"$fake_bin/adb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

scenario="${FAKE_ADB_SCENARIO:?}"
if [[ "$#" -eq 1 && "$1" == "devices" ]]; then
  printf '%s\n' 'List of devices attached'
  case "$scenario" in
    unique | explicit)
      printf '%s\n' 'emulator-29 device product:fake model:api29 device:fake29'
      printf '%s\n' 'emulator-36 device product:fake model:api36 device:fake36'
      ;;
    zero)
      printf '%s\n' 'emulator-29 device product:fake model:api29 device:fake29'
      ;;
    ambiguous)
      printf '%s\n' 'emulator-36a device product:fake model:api36 device:fake36a'
      printf '%s\n' 'emulator-36b device product:fake model:api36 device:fake36b'
      ;;
    *)
      printf 'unsupported fake ADB scenario: %s\n' "$scenario" >&2
      exit 2
      ;;
  esac
  exit 0
fi

if [[ "$1" == "-s" && "$3" == "shell" && "$4" == "cmd" && "$5" == "gpu" && "$6" == "vkjson" ]]; then
  case "$2" in
    emulator-29)
      printf '%s\n' '{"devices":[{}]}'
      ;;
    emulator-36 | emulator-36a | emulator-36b)
      printf '%s\n' '{"devices":[{"properties":{"deviceName":"Fake Vulkan device"}}]}'
      ;;
    *)
      printf '%s\n' '{"devices":[]}'
      ;;
  esac
  exit 0
fi

printf 'unexpected adb invocation: %s\n' "$*" >&2
exit 2
EOF
chmod +x "$fake_bin/adb"

cat >"$fake_repo/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' "${ANDROID_SERIAL-}" >>"${FAKE_GRADLE_LOG:?}"
result_dir='samples/hello-triangle-android-capture/build/outputs/androidTest-results/connected/fake'
capture_dir='samples/hello-triangle-android-capture/build/outputs/fake'
mkdir -p "$result_dir" "$capture_dir"
printf '%s\n' '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="capture"/></testsuite>' >"$result_dir/TEST-fake.xml"
python3 - "$capture_dir/hello-triangle-android.png" <<'PY'
import pathlib
import struct
import sys
import zlib

path = pathlib.Path(sys.argv[1])
palette = [(255, 0, 0, 255), (0, 255, 0, 255), (0, 0, 255, 255), (255, 255, 0, 255), (255, 0, 255, 255), (0, 255, 255, 255), (128, 64, 32, 255), (32, 64, 128, 255)]
row = bytes([0]) + b''.join(bytes(palette[x % len(palette)]) for x in range(800))
raw = row * 600

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 800, 600, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
PY
EOF
chmod +x "$fake_repo/gradlew"

run_script() {
  local scenario="$1"
  local serial="$2"
  local output="$3"
  local gradle_log="$4"

  : >"$gradle_log"
  set +e
  (
    if [[ -n "$serial" ]]; then
      export ANDROID_SERIAL="$serial"
    else
      unset ANDROID_SERIAL
    fi
    export FAKE_ADB_SCENARIO="$scenario"
    export FAKE_GRADLE_LOG="$gradle_log"
    export PATH="$fake_bin:$PATH"
    cd "$fake_repo"
    scripts/android-emulator-test.sh
  ) >"$output" 2>&1
  local status=$?
  set -e
  return "$status"
}

expect_failure_before_gradle() {
  local scenario="$1"
  local serial="$2"
  local expected="$3"
  local output="$fixture_dir/$scenario-$serial.out"
  local gradle_log="$fixture_dir/$scenario-$serial.gradle"

  if run_script "$scenario" "$serial" "$output" "$gradle_log"; then
    echo "FAIL: $scenario unexpectedly reached a successful Gradle invocation" >&2
    exit 1
  fi
  if [[ -s "$gradle_log" ]]; then
    echo "FAIL: $scenario invoked Gradle before rejecting device selection" >&2
    exit 1
  fi
  if ! grep -Fq "$expected" "$output"; then
    echo "FAIL: $scenario did not explain the rejected selection" >&2
    cat "$output" >&2
    exit 1
  fi
}

unique_output="$fixture_dir/unique.out"
unique_gradle_log="$fixture_dir/unique.gradle"
if ! run_script unique '' "$unique_output" "$unique_gradle_log"; then
  echo 'FAIL: unique Vulkan device selection rejected a valid device' >&2
  cat "$unique_output" >&2
  exit 1
fi
if [[ "$(cat "$unique_gradle_log")" != 'emulator-36' ]]; then
  echo 'FAIL: unique Vulkan selection did not export emulator-36 to Gradle' >&2
  cat "$unique_gradle_log" >&2
  exit 1
fi

expect_failure_before_gradle zero '' 'expected exactly one Vulkan-capable online Android device, found 0'
expect_failure_before_gradle ambiguous '' 'expected exactly one Vulkan-capable online Android device, found 2'

explicit_output="$fixture_dir/explicit.out"
explicit_gradle_log="$fixture_dir/explicit.gradle"
if ! run_script explicit emulator-36 "$explicit_output" "$explicit_gradle_log"; then
  echo 'FAIL: explicit compatible ANDROID_SERIAL was rejected' >&2
  cat "$explicit_output" >&2
  exit 1
fi
if [[ "$(cat "$explicit_gradle_log")" != 'emulator-36' ]]; then
  echo 'FAIL: explicit ANDROID_SERIAL was not preserved for Gradle' >&2
  cat "$explicit_gradle_log" >&2
  exit 1
fi

expect_failure_before_gradle explicit emulator-29 'ANDROID_SERIAL=emulator-29 is not Vulkan-capable'

echo 'PASS: Android device selection is explicit, unique, and preconditioned'
