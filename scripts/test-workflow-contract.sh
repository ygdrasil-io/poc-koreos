#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
checker="$script_dir/check-workflow-contract.py"
validator="$script_dir/verify-test-results.py"
fixture_workflow="$repo_root/.github/fixtures/workflow-contract-invalid.yml"
fixture_report="$script_dir/fixtures/cross-platform-correctness-report-18.md"
fixture_junit="$script_dir/fixtures/junit-declared-without-testcase.xml"
fixture_junit_nested="$script_dir/fixtures/junit-nested-valid.xml"
fixture_junit_skipped="$script_dir/fixtures/junit-skipped-mismatch.xml"
fixture_generator="$script_dir/fixtures/generate-validation-fixtures.py"
fixture_dir="$(mktemp -d "${TMPDIR:-/tmp}/kadre-validation-fixtures.XXXXXX")"
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
  [[ "$workflow_output" != *"missing canonical script-owned command scripts/test-workflow-contract.sh"* ]]; then
  echo "FAIL: workflow fixture did not prove the path-filter and missing-command checks" >&2
  printf '%s\n' "$workflow_output" >&2
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

for fixture in \
  workflow-contract-comment-only.yml \
  workflow-contract-needs-empty.yml \
  workflow-contract-missing-always.yml \
  workflow-contract-command-true.yml \
  workflow-contract-command-if-false.yml \
  workflow-contract-assertion-if-false.yml
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
    workflow-contract-comment-only.yml | workflow-contract-command-true.yml | workflow-contract-command-if-false.yml)
      expected="host-contracts: missing canonical script-owned command scripts/test-workflow-contract.sh"
      ;;
    workflow-contract-needs-empty.yml)
      expected="cross-platform-correctness: needs must equal the required matrix jobs"
      ;;
    workflow-contract-missing-always.yml)
      expected="cross-platform-correctness: if must be always()"
      ;;
    workflow-contract-assertion-if-false.yml)
      expected="cross-platform-correctness: missing canonical success assertion for host-contracts"
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
