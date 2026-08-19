#!/usr/bin/env python3
"""Reject CI workflow changes that weaken the cross-platform correctness gate."""

from __future__ import annotations

import argparse
import pathlib
import re
import sys


REQUIRED_JOBS = {
    "host-contracts": "scripts/test-workflow-contract.sh",
    "web-browser-contracts": "scripts/test-web-browsers.sh",
    "ios-simulator-contracts": "scripts/test-uikit-simulator.sh",
    "android-emulator-contracts": "scripts/android-emulator-test.sh",
    "linux-container-contracts": "scripts/test-linux-container.sh",
    "deterministic-captures": "scripts/verify-test-results.py",
}
AGGREGATE_JOB = "cross-platform-correctness"
MASKING = re.compile(r"continue-on-error\s*:|\|\|")


def job_blocks(text: str) -> dict[str, str]:
    match = re.search(r"(?m)^jobs:\s*$", text)
    if not match:
        return {}
    blocks: dict[str, str] = {}
    starts = list(re.finditer(r"(?m)^  ([A-Za-z0-9_-]+):\s*$", text[match.end() :]))
    for index, start in enumerate(starts):
        name = start.group(1)
        beginning = match.end() + start.start()
        end = match.end() + (starts[index + 1].start() if index + 1 < len(starts) else len(text[match.end() :]))
        blocks[name] = text[beginning:end]
    return blocks


def validate(path: pathlib.Path) -> list[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as error:
        return [f"cannot read workflow: {error}"]
    errors: list[str] = []
    blocks = job_blocks(text)
    if re.search(r"(?m)^\s+paths(?:-ignore)?:", text):
        errors.append("required workflow must not use path filters")
    for name, required_command in REQUIRED_JOBS.items():
        block = blocks.get(name)
        if block is None:
            errors.append(f"missing required matrix job: {name}")
            continue
        timeout = re.search(r"(?m)^    timeout-minutes:\s*(\d+)\s*$", block)
        if timeout is None:
            errors.append(f"{name}: missing timeout-minutes")
        elif int(timeout.group(1)) > 25:
            errors.append(f"{name}: timeout-minutes must be at most 25")
        if MASKING.search(block):
            errors.append(f"{name}: success masking is forbidden")
        if required_command not in block:
            errors.append(f"{name}: missing script-owned command {required_command}")

    aggregate = blocks.get(AGGREGATE_JOB)
    if aggregate is None:
        errors.append(f"missing blocking aggregate job: {AGGREGATE_JOB}")
    else:
        timeout = re.search(r"(?m)^    timeout-minutes:\s*(\d+)\s*$", aggregate)
        if timeout is None or int(timeout.group(1)) > 25:
            errors.append(f"{AGGREGATE_JOB}: timeout-minutes must be at most 25")
        if MASKING.search(aggregate):
            errors.append(f"{AGGREGATE_JOB}: success masking is forbidden")
        for name in REQUIRED_JOBS:
            if not re.search(rf"\b{name}\b", aggregate):
                errors.append(f"{AGGREGATE_JOB}: missing needs dependency {name}")
            if f"needs.{name}.result" not in aggregate:
                errors.append(f"{AGGREGATE_JOB}: missing explicit success check for {name}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workflow", type=pathlib.Path, help="workflow YAML to validate")
    args = parser.parse_args()
    errors = validate(args.workflow)
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {args.workflow} enforces the cross-platform workflow contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
