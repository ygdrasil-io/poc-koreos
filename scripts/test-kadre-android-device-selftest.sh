#!/usr/bin/env bash
set -euo pipefail

# Contract self-test for scripts/test-kadre-android-device.sh (Android phase 10,
# task 1). Pattern: kadre-old/scripts/test-android-device-selection.sh — a fake
# `adb` (KADRE_ANDROID_ADB) plus a fake `gradlew` prove that:
#   - exactly one eligible (booted, state "device") Android device is required;
#     an explicit ANDROID_SERIAL/--serial is honored when valid and refused
#     before Gradle otherwise (offline, unauthorized, unknown);
#   - a device that does not report sys.boot_completed=1 is a bounded fast-fail,
#     never the unbounded phase-0 hang;
#   - run_with_watchdog kills the whole child process group (parent + spawned
#     children) on timeout with exit 124, and passes child statuses through;
#   - guard: the phase-10 CI workflow (once Task 2 lands) must not use
#     continue-on-error, `|| true`, or a masquerading `if: always()` gate-allow.
#     The file is expected to be absent at task-1 time; the guard then passes
#     with an explicit SKIP note and asserts automatically once it appears.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEVICE_SCRIPT="$SCRIPT_DIR/test-kadre-android-device.sh"

fail() {
  echo "FAIL: $1" >&2
  if [ -n "${2:-}" ]; then
    printf '%s\n' "$2" >&2
  fi
  return 1
}

if [ ! -x "$DEVICE_SCRIPT" ]; then
  fail "self-test requires the executable $DEVICE_SCRIPT (not created yet)"
  exit 1
fi

FIXTURE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/kadre-android-device-selftest.XXXXXX")"
cleanup() { rm -rf "$FIXTURE_DIR"; }
trap cleanup EXIT

FAKE_BIN="$FIXTURE_DIR/bin"
FAKE_REPO="$FIXTURE_DIR/repo"
mkdir -p "$FAKE_BIN" "$FAKE_REPO"

cat > "$FAKE_BIN/adb" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

scenario="${FAKE_ADB_SCENARIO:?}"

if [ "${1:-}" = "devices" ]; then
  printf '%s\n' 'List of devices attached'
  case "$scenario" in
    unique | explicit)
      printf '%s\t%s\n' emulator-5554 device
      ;;
    multi)
      printf '%s\t%s\n' emulator-1 device
      printf '%s\t%s\n' emulator-2 device
      ;;
    mixed-boot)
      printf '%s\t%s\n' emulator-1 device
      printf '%s\t%s\n' emulator-2 device
      ;;
    zero)
      : ;;
    offline)
      printf '%s\t%s\n' emulator-5554 offline
      ;;
    unauthorized)
      printf '%s\t%s\n' emulator-5554 unauthorized
      ;;
    boot0)
      printf '%s\t%s\n' emulator-5554 device
      ;;
    *)
      printf 'unsupported fake adb scenario: %s\n' "$scenario" >&2
      exit 2
      ;;
  esac
  exit 0
fi

if [ "${1:-}" = "-s" ] && [ "${3:-}" = "shell" ] && [ "${4:-}" = "getprop" ] && [ "${5:-}" = "sys.boot_completed" ]; then
  case "$scenario" in
    boot0)
      printf '%s\n' 0
      ;;
    mixed-boot)
      if [ "$2" = "emulator-1" ]; then printf '%s\n' 0; else printf '%s\n' 1; fi
      ;;
    *)
      printf '%s\n' 1
      ;;
  esac
  exit 0
fi

printf 'unexpected fake adb invocation: %s\n' "$*" >&2
exit 2
EOF
chmod +x "$FAKE_BIN/adb"

cat > "$FAKE_REPO/gradlew" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "${ANDROID_SERIAL-}" >>"${FAKE_GRADLE_LOG:?}"
exit "${FAKE_GRADLE_STATUS:-0}"
EOF
chmod +x "$FAKE_REPO/gradlew"

# Non-cooperative process tree used to prove the watchdog kills the whole group:
# the "gradle" parent spawns a sleeping child; neither installs handlers.
cat > "$FIXTURE_DIR/watchdog-tree.sh" <<'EOF'
#!/usr/bin/env bash
set -u
dir="${1:?}"
printf '%s\n' "$$" >"$dir/parent-pid"
sleep 300 &
printf '%s\n' "$!" >"$dir/child-pid"
wait
EOF
chmod +x "$FIXTURE_DIR/watchdog-tree.sh"

device_case() { # <output-file> <gradle-log> <device-script args...>
  local output="$1" gradle_log="$2"
  shift 2
  : >"$gradle_log"
  local status
  set +e
  (
    cd "$FAKE_REPO" || exit 97
    export PATH="$FAKE_BIN:$PATH"
    export KADRE_ANDROID_ADB="$FAKE_BIN/adb"
    export KADRE_ANDROID_BOOT_POLL_S=0.2
    export FAKE_ADB_SCENARIO="${CASE_SCENARIO:?CASE_SCENARIO required}"
    export FAKE_GRADLE_LOG="$gradle_log"
    if [ -n "${CASE_SERIAL:-}" ]; then
      export ANDROID_SERIAL="$CASE_SERIAL"
    else
      unset ANDROID_SERIAL
    fi
    "$DEVICE_SCRIPT" "$@"
  ) >"$output" 2>&1
  status=$?
  set -e
  return "$status"
}

expect_gradle_serial() { # <label> <expected-serial> <scenario> [device-script args...]
  local label="$1" serial="$2" scenario="$3"
  shift 3
  local gradle_log="$FIXTURE_DIR/gradle-$label.log"
  local output="$FIXTURE_DIR/out-$label.txt"
  CASE_SCENARIO="$scenario" CASE_SERIAL="" device_case "$output" "$gradle_log" "$@" ||
    { fail "$label: device script rejected a valid selection" "$(cat "$output")"; return 1; }
  local logged
  logged="$(cat "$gradle_log")"
  if [ "$logged" != "$serial" ]; then
    fail "$label: Gradle observed ANDROID_SERIAL='$logged' instead of '$serial'" "$(cat "$output")"
    return 1
  fi
  if ! grep -Fq "selected ANDROID_SERIAL=$serial" "$output"; then
    fail "$label: selection was not reported" "$(cat "$output")"
    return 1
  fi
  echo "PASS: $label"
}

expect_refusal() { # <label> <expected-substring> <scenario> [device-script args...]
  local label="$1" expected="$2" scenario="$3"
  shift 3
  local gradle_log="$FIXTURE_DIR/gradle-$label.log"
  local output="$FIXTURE_DIR/out-$label.txt"
  CASE_SERIAL="" CASE_SCENARIO="$scenario" device_case "$output" "$gradle_log" "$@" && {
    fail "$label: device script exited 0 although a refusal was expected"
    return 1
  }
  if [ -s "$gradle_log" ]; then
    fail "$label: Gradle was invoked before the device selection refusal"
    return 1
  fi
  if ! grep -Fq "$expected" "$output"; then
    fail "$label: output does not explain the refusal (missing '$expected')" "$(cat "$output")"
    return 1
  fi
  echo "PASS: $label"
}

expect_refusal_with_env_serial() { # <label> <expected-substring> <scenario> <serial>
  local label="$1" expected="$2" scenario="$3" serial="$4"
  local gradle_log="$FIXTURE_DIR/gradle-$label.log"
  local output="$FIXTURE_DIR/out-$label.txt"
  CASE_SERIAL="$serial" CASE_SCENARIO="$scenario" device_case "$output" "$gradle_log" && {
    fail "$label: device script exited 0 although a refusal was expected"
    return 1
  }
  if [ -s "$gradle_log" ]; then
    fail "$label: Gradle was invoked before the device selection refusal"
    return 1
  fi
  if ! grep -Fq "$expected" "$output"; then
    fail "$label: output does not explain the refusal (missing '$expected')" "$(cat "$output")"
    return 1
  fi
  echo "PASS: $label"
}

wait_pid_gone() { # <pid> <timeout-seconds>
  local pid="$1" timeout="$2" iterations=0
  local max_iterations=$((timeout * 10))
  while kill -0 "$pid" 2>/dev/null; do
    if [ "$iterations" -ge "$max_iterations" ]; then
      return 1
    fi
    sleep 0.1
    iterations=$((iterations + 1))
  done
  return 0
}

SCENARIOS=0

record() {
  SCENARIOS=$((SCENARIOS + 1))
}

# --- selection scenarios ------------------------------------------------------

expect_gradle_serial unique-auto-select emulator-5554 unique -- ./gradlew --console=plain
record
expect_gradle_serial explicit-serial-honored emulator-5554 explicit --serial emulator-5554 -- ./gradlew
record
expect_gradle_serial explicit-env-serial-honored emulator-5554 explicit -- ./gradlew --console=plain
record
expect_gradle_serial auto-selects-only-booted-device emulator-2 mixed-boot -- ./gradlew
record

expect_refusal explicit-offline-refused "ANDROID_SERIAL=emulator-5554 is not an Android device in 'device' state" offline --serial emulator-5554 -- ./gradlew
record
expect_refusal explicit-unauthorized-refused "is not an Android device in 'device' state" unauthorized --serial emulator-5554 -- ./gradlew
record
expect_refusal explicit-unknown-refused "ANDROID_SERIAL=emulator-9999 is not an Android device in 'device' state" zero --serial emulator-9999 -- ./gradlew
record
expect_refusal_with_env_serial explicit-env-offline-refused "is not an Android device in 'device' state" offline emulator-5554
record

expect_refusal multi-ambiguous-refused "expected exactly one eligible Android device, found 2" multi -- ./gradlew
record
expect_refusal zero-devices-refused "expected exactly one eligible Android device, found 0" zero -- ./gradlew
record
expect_refusal offline-auto-refused "expected exactly one eligible Android device, found 0" offline -- ./gradlew
record

expect_refusal boot-incomplete-explicit-refused "did not report sys.boot_completed=1 within" boot0 --serial emulator-5554 --boot-timeout 2 -- ./gradlew
record
expect_refusal boot-incomplete-auto-refused "candidate(s) not booted" boot0 -- ./gradlew
record

# --- watchdog scenarios -------------------------------------------------------

watchdog_case() { # <label> <KADRE_WATCHDOG_MODE (empty = auto)>
  local label="$1" mode="$2"
  local dir="$FIXTURE_DIR/watchdog-$label"
  mkdir -p "$dir"
  local status
  set +e
  (
    set -u
    # shellcheck source=test-kadre-android-device.sh
    source "$DEVICE_SCRIPT"
    if [ -n "$mode" ]; then
      export KADRE_WATCHDOG_MODE="$mode"
    fi
    export KADRE_WATCHDOG_POLL_S=0.2
    set +e
    run_with_watchdog 2 "$FIXTURE_DIR/watchdog-tree.sh" "$dir"
    status=$?
    exit "$status"
  ) >"$dir/output" 2>&1
  status=$?
  set -e
  if [ "$status" -ne 124 ]; then
    fail "watchdog-$label: expected exit 124, got $status" "$(cat "$dir/output")"
    return 1
  fi
  if ! grep -Fq "timed out after 2s" "$dir/output"; then
    fail "watchdog-$label: timeout was not reported" "$(cat "$dir/output")"
    return 1
  fi
  local parent_pid child_pid
  parent_pid="$(cat "$dir/parent-pid")"
  child_pid="$(cat "$dir/child-pid")"
  if ! wait_pid_gone "$parent_pid" 10; then
    fail "watchdog-$label: parent process $parent_pid survived the watchdog kill"
    return 1
  fi
  if ! wait_pid_gone "$child_pid" 10; then
    fail "watchdog-$label: child process $child_pid survived the watchdog kill"
    return 1
  fi
  echo "PASS: watchdog-$label timed out with 124 and killed parent $parent_pid and its child $child_pid"
}

watchdog_case auto ""
record
watchdog_case subshell "subshell"
record
if command -v setsid >/dev/null 2>&1; then
  watchdog_case setsid "setsid"
else
  echo "SKIP: watchdog setsid mode (no setsid on this host; the auto case already covers the portable fallback)"
fi
record

watchdog_status_passthrough() { # <expected-status> <command...>
  local expected="$1"
  shift
  local observed
  set +e
  (
    set -u
    # shellcheck source=test-kadre-android-device.sh
    source "$DEVICE_SCRIPT"
    set +e
    run_with_watchdog 5 "$@"
    observed=$?
    exit "$observed"
  ) >/dev/null 2>&1
  observed=$?
  set -e
  if [ "$observed" -ne "$expected" ]; then
    fail "watchdog status passthrough: expected $expected, got $observed"
    return 1
  fi
  echo "PASS: watchdog passes child status $expected through unchanged"
}

watchdog_status_passthrough 7 /bin/sh -c 'exit 7'
record
watchdog_status_passthrough 0 true
record

# --- phase-10 workflow guard --------------------------------------------------
# Chosen guard strategy (task-1 ruling): the real workflow file is asserted as
# soon as it exists; while Task 2 has not landed the guard passes with an
# explicit SKIP so the self-test stays green at the task-1 commit.

workflow_guard() {
  local workflow="$REPO_ROOT/.github/workflows/kadre-android-contracts.yml"
  if [ ! -f "$workflow" ]; then
    echo "SKIP: workflow guard — .github/workflows/kadre-android-contracts.yml is not present yet; the guard asserts it automatically once Task 2 lands"
    return 0
  fi
  local offenders
  offenders="$(grep -nE 'continue-on-error|\|\| true' "$workflow" || true)"
  if [ -n "$offenders" ]; then
    fail "workflow guard: forbidden masking found in kadre-android-contracts.yml" "$offenders"
    return 1
  fi
  # Split the YAML into step blocks (a block starts at a "- name/uses/run/if/id:"
  # item) and refuse any `if: ... always()` step that is not a diagnostic,
  # evidence or artifact step (a masquerading gate-allow).
  local blocks
  blocks="$FIXTURE_DIR/workflow-steps.txt"
  awk '
    /^[[:space:]]*-[[:space:]]+(name|uses|run|if|id):[[:space:]]*/ {
      if (block != "") { blocks[++count] = block }
      block = $0
      next
    }
    { block = block "\n" $0 }
    END {
      if (block != "") { blocks[++count] = block }
      for (i = 1; i <= count; i++) { print blocks[i] "\n---STEP---" }
    }
  ' "$workflow" >"$blocks"
  local masquerades
  masquerades="$(awk '
    /---STEP---/ {
      if (has_always && !has_purpose) { print previous }
      has_always = 0; has_purpose = 0; previous = ""
      next
    }
    {
      if (previous != "") previous = previous "\n" $0; else previous = $0
      if ($0 ~ /if:[[:space:]]*.*always\(\)/) { has_always = 1 }
      if ($0 ~ /[Uu]pload|[Aa]rtifact|[Cc]ollect|[Dd]iagnost|[Ii]nspect|[Ee]vidence|[Rr]eport|[Ll]og/) { has_purpose = 1 }
    }
  ' "$blocks")"
  if [ -n "$masquerades" ]; then
    fail "workflow guard: if: always() used as a masquerading gate-allow in kadre-android-contracts.yml" "$masquerades"
    return 1
  fi
  if ! grep -Fq "scripts/test-kadre-android-contracts.sh" "$workflow"; then
    fail "workflow guard: kadre-android-contracts.yml does not invoke the android gate script"
    return 1
  fi
  echo "PASS: workflow guard — no continue-on-error, no '|| true', no masquerading if: always() gate-allow"
}
workflow_guard
record

echo "PASS: android device selection + portable watchdog contract holds ($SCENARIOS scenario checks)"
