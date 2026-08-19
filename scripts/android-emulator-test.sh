#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
cd "$repo_root"

./gradlew \
  :kadre-android:testAndroidHostTest \
  :samples:hello-triangle-android-capture:connectedDebugAndroidTest \
  --no-daemon --stacktrace --console=plain

result_dir="samples/hello-triangle-android-capture/build/outputs/androidTest-results/connected"
capture="$(find samples/hello-triangle-android-capture/build/outputs -type f -name 'hello-triangle-android.png' -print -quit)"
if [[ -z "$capture" ]]; then
  echo "FAIL: Android emulator run produced no hello-triangle-android.png capture" >&2
  exit 1
fi

python3 "$script_dir/verify-test-results.py" --junit "$result_dir" --png "$capture"
