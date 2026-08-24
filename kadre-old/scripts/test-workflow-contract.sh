#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
checker="$script_dir/check-workflow-contract.py"
validator="$script_dir/verify-test-results.py"
fixture_workflow="$repo_root/.github/fixtures/workflow-contract-invalid.yml"
fixture_android_selection="$repo_root/.github/fixtures/workflow-contract-android-device-selection-missing.yml"
fixture_report="$script_dir/fixtures/cross-platform-correctness-report-18.md"
fixture_empty_report="$script_dir/fixtures/cross-platform-correctness-report-empty-fields.md"
fixture_junit="$script_dir/fixtures/junit-declared-without-testcase.xml"
fixture_junit_nested="$script_dir/fixtures/junit-nested-valid.xml"
fixture_junit_skipped="$script_dir/fixtures/junit-skipped-mismatch.xml"
fixture_generator="$script_dir/fixtures/generate-validation-fixtures.py"
fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/kadre-validation-fixtures.XXXXXX")"
excluded_musl_workflow="$fixture_dir/cross-platform-correctness-excluded-musl.yml"
trap 'rm -rf "$fixture_dir"' EXIT

missing=0
for tool in "$checker" "$validator"; do
  if [[ ! -f "$tool" ]]; then
    echo "missing required checker: $tool" >&2
    missing=1
  fi
done
if [[ "$missing" -ne 0 ]]; then
  exit 1
fi

set +e
workflow_output="$(python3 "$checker" "$fixture_workflow" 2>&1)"
workflow_status=$?
report_output="$(python3 "$validator" --report "$fixture_report" 2>&1)"
report_status=$?
set -e

if [[ "$workflow_status" -eq 0 ]]; then
  echo "FAIL: invalid workflow fixture was accepted" >&2
  exit 1
fi
if [[ "$workflow_output" != *"success masking is forbidden"* ]]; then
  echo "FAIL: workflow fixture did not fail for the required masking violation" >&2
  printf '%s\n' "$workflow_output" >&2
  exit 1
fi
if [[ "$workflow_output" != *"workflow must trigger pull_request without a paths or paths-ignore filter"* ]] ||
  [[ "$workflow_output" != *"missing canonical script-owned command scripts/test-workflow-contract.sh"* ]] ||
  [[ "$workflow_output" != *"linux-container-contracts: libc matrix must equal [glibc, musl]"* ]]; then
  echo "FAIL: workflow fixture did not prove the path-filter, missing-command, and libc-matrix checks" >&2
  printf '%s\n' "$workflow_output" >&2
  exit 1
fi

set +e
android_selection_output="$(python3 "$checker" "$fixture_android_selection" 2>&1)"
android_selection_status=$?
set -e
if [[ "$android_selection_status" -eq 0 ]] ||
  [[ "$android_selection_output" != *"android-emulator-contracts: missing canonical script-owned command scripts/test-android-device-selection.sh"* ]]; then
  echo "FAIL: Android fixture did not require the device-selection regression" >&2
  printf '%s\n' "$android_selection_output" >&2
  exit 1
fi

cp "$repo_root/.github/workflows/cross-platform-correctness.yml" "$excluded_musl_workflow"
python3 - "$excluded_musl_workflow" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
workflow = path.read_text(encoding="utf-8")
needle = "      matrix:\n        libc: [glibc, musl]\n"
replacement = "      matrix:\n        libc: [glibc, musl]\n        exclude:\n          - libc: musl\n"
if workflow.count(needle) != 1:
    raise SystemExit("FAIL: could not inject the musl exclusion into the real workflow copy")
path.write_text(workflow.replace(needle, replacement), encoding="utf-8")
PY

set +e
excluded_musl_output="$(python3 "$checker" "$excluded_musl_workflow" 2>&1)"
excluded_musl_status=$?
set -e
if [[ "$excluded_musl_status" -eq 0 ]] ||
  [[ "$excluded_musl_output" != *"linux-container-contracts: libc matrix must not define exclude"* ]]; then
  echo "FAIL: workflow copy excluding musl was accepted or reported the wrong violation" >&2
  printf '%s\n' "$excluded_musl_output" >&2
  exit 1
fi

if [[ "$report_status" -eq 0 ]]; then
  echo "FAIL: 18-row report fixture was accepted" >&2
  exit 1
fi
if [[ "$report_output" != *"expected exactly 19 numbered traceability rows, found 18"* ]]; then
  echo "FAIL: report fixture did not fail for its missing nineteenth row" >&2
  printf '%s\n' "$report_output" >&2
  exit 1
fi

set +e
empty_report_output="$(python3 "$validator" --report "$fixture_empty_report" 2>&1)"
empty_report_status=$?
set -e
if [[ "$empty_report_status" -eq 0 ]] ||
  [[ "$empty_report_output" != *"row 1 has empty required fields"* ]]; then
  echo "FAIL: empty report fixture was accepted or reported the wrong violation" >&2
  printf '%s\n' "$empty_report_output" >&2
  exit 1
fi

for fixture in \
  workflow-contract-comment-only.yml \
  workflow-contract-needs-empty.yml \
  workflow-contract-missing-always.yml \
  workflow-contract-command-true.yml \
  workflow-contract-command-if-false.yml \
  workflow-contract-assertion-if-false.yml \
  workflow-contract-android-noop-action.yml \
  workflow-contract-command-step-if.yml \
  workflow-contract-assertion-step-if.yml \
  workflow-contract-android-runner-step-if.yml \
  workflow-contract-command-step-shell.yml \
  workflow-contract-assertion-step-shell.yml \
  workflow-contract-command-working-directory.yml \
  workflow-contract-android-runner-working-directory.yml
do
  set +e
  fixture_output="$(python3 "$checker" "$repo_root/.github/fixtures/$fixture" 2>&1)"
  fixture_status=$?
  set -e
  if [[ "$fixture_status" -eq 0 ]]; then
    echo "FAIL: structured workflow fixture was accepted: $fixture" >&2
    exit 1
  fi
  case "$fixture" in
    workflow-contract-comment-only.yml | workflow-contract-command-true.yml | workflow-contract-command-if-false.yml | workflow-contract-command-step-if.yml | workflow-contract-command-step-shell.yml | workflow-contract-command-working-directory.yml)
      expected="host-contracts: missing canonical script-owned command scripts/test-workflow-contract.sh"
      ;;
    workflow-contract-needs-empty.yml)
      expected="cross-platform-correctness: needs must equal the required matrix jobs"
      ;;
    workflow-contract-missing-always.yml)
      expected="cross-platform-correctness: if must be always()"
      ;;
    workflow-contract-assertion-if-false.yml | workflow-contract-assertion-step-if.yml | workflow-contract-assertion-step-shell.yml)
      expected="cross-platform-correctness: missing canonical success assertion for host-contracts"
      ;;
    workflow-contract-android-noop-action.yml | workflow-contract-android-runner-step-if.yml | workflow-contract-android-runner-working-directory.yml)
      expected="android-emulator-contracts: missing canonical Android emulator runner step"
      ;;
  esac
  if [[ "$fixture_output" != *"$expected"* ]]; then
    echo "FAIL: $fixture did not fail for its structured contract violation" >&2
    printf '%s\n' "$fixture_output" >&2
    exit 1
  fi
done

python3 "$fixture_generator" "$fixture_dir"
for fixture in tiny-red.png solid-800x600.png single-pixel-800x600.png; do
  set +e
  png_output="$(python3 "$validator" --png "$fixture_dir/$fixture" --png-target compose-raster 2>&1)"
  png_status=$?
  set -e
  if [[ "$png_status" -eq 0 ]]; then
    echo "FAIL: invalid PNG fixture was accepted: $fixture" >&2
    exit 1
  fi
  case "$fixture" in
    tiny-red.png) expected_png="dimensions must equal 800x600" ;;
    solid-800x600.png) expected_png="must contain at least 8 distinct visible colors" ;;
    single-pixel-800x600.png) expected_png="non-background visible pixels" ;;
  esac
  if [[ "$png_output" != *"$expected_png"* ]]; then
    echo "FAIL: $fixture did not fail for its target-specific PNG violation" >&2
    printf '%s\n' "$png_output" >&2
    exit 1
  fi
done

set +e
junit_output="$(python3 "$validator" --junit "$fixture_junit" 2>&1)"
junit_status=$?
set -e
if [[ "$junit_status" -eq 0 ]] || [[ "$junit_output" != *"declared tests=1 does not match testcase count=0"* ]]; then
  echo "FAIL: JUnit fixture did not reject a declared test without a testcase" >&2
  printf '%s\n' "$junit_output" >&2
  exit 1
fi

python3 "$validator" --junit "$fixture_junit_nested"

set +e
junit_skipped_output="$(python3 "$validator" --junit "$fixture_junit_skipped" 2>&1)"
junit_skipped_status=$?
set -e
if [[ "$junit_skipped_status" -eq 0 ]] || [[ "$junit_skipped_output" != *"declared skipped=0 does not match skipped count=1"* ]]; then
  echo "FAIL: JUnit fixture did not reject a skipped-element counter mismatch" >&2
  printf '%s\n' "$junit_skipped_output" >&2
  exit 1
fi

python3 "$checker" "$repo_root/.github/workflows/cross-platform-correctness.yml"
python3 "$validator" --report "$repo_root/docs/kadre/cross-platform-correctness-report.md"
echo "PASS: workflow and report contracts are enforced"
