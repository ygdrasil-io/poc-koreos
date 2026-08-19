#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
checker="$script_dir/check-workflow-contract.py"
validator="$script_dir/verify-test-results.py"
fixture_workflow="$repo_root/.github/fixtures/workflow-contract-invalid.yml"
fixture_report="$script_dir/fixtures/cross-platform-correctness-report-18.md"

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
if [[ "$workflow_output" != *"required workflow must not use path filters"* ]] ||
  [[ "$workflow_output" != *"missing script-owned command scripts/test-workflow-contract.sh"* ]]; then
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

python3 "$checker" "$repo_root/.github/workflows/cross-platform-correctness.yml"
python3 "$validator" --report "$repo_root/docs/kadre/cross-platform-correctness-report.md"
echo "PASS: workflow and report contracts are enforced"
