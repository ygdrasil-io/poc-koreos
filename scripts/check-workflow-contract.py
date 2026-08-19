#!/usr/bin/env python3
"""Reject CI workflow changes that weaken the cross-platform correctness gate."""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

import yaml


REQUIRED_JOBS = {
    "host-contracts": "scripts/test-workflow-contract.sh",
    "web-browser-contracts": "scripts/test-web-browsers.sh",
    "ios-simulator-contracts": "scripts/test-uikit-simulator.sh",
    "android-emulator-contracts": "scripts/android-emulator-test.sh",
    "linux-container-contracts": "scripts/test-linux-container.sh",
    "deterministic-captures": "scripts/verify-test-results.py",
}
AGGREGATE_JOB = "cross-platform-correctness"
REQUIRED_JOB_NAMES = list(REQUIRED_JOBS)


def load_workflow(path: pathlib.Path) -> object:
    """Load YAML structurally while preserving GitHub's unquoted `on` key."""
    try:
        with path.open(encoding="utf-8") as source:
            return yaml.load(source, Loader=yaml.BaseLoader)
    except (OSError, yaml.YAMLError) as error:
        raise ValueError(f"cannot parse workflow: {error}") from error


def is_mapping(value: object) -> bool:
    return isinstance(value, dict)


def is_sequence(value: object) -> bool:
    return isinstance(value, list)


def has_pull_request_without_paths_filter(trigger: object) -> bool:
    if is_mapping(trigger):
        if "pull_request" not in trigger:
            return False
        pull_request = trigger["pull_request"]
        return not (is_mapping(pull_request) and {"paths", "paths-ignore"}.intersection(pull_request))
    if is_sequence(trigger):
        return "pull_request" in trigger
    return trigger == "pull_request"


def has_forbidden_masking(value: object) -> bool:
    if is_mapping(value):
        for key, nested in value.items():
            if key == "continue-on-error":
                return True
            if key in {"run", "script"} and isinstance(nested, str) and "||" in nested:
                return True
            if has_forbidden_masking(nested):
                return True
    elif is_sequence(value):
        return any(has_forbidden_masking(nested) for nested in value)
    return False


def executable_steps(job: dict[str, object]) -> list[str]:
    steps = job.get("steps")
    if not is_sequence(steps):
        return []
    commands: list[str] = []
    for step in steps:
        if not is_mapping(step):
            continue
        run = step.get("run")
        if isinstance(run, str):
            commands.append(run)
        with_values = step.get("with")
        if is_mapping(with_values) and isinstance(with_values.get("script"), str):
            commands.append(with_values["script"])
    return commands


def runs_required_command(job: dict[str, object], command: str) -> bool:
    escaped = re.escape(command)
    command_pattern = re.compile(
        rf"(?:^|[;|&]\s*|\n\s*)(?:(?:[A-Za-z_][A-Za-z0-9_]*=[^\s]+)\s+)*(?:(?:python3|bash|sh)\s+)?{escaped}(?=\s|$)"
    )
    return any(command_pattern.search(run) for run in executable_steps(job))


def has_success_assertion(runs: list[str], job_name: str) -> bool:
    escaped = re.escape(job_name)
    assertion = re.compile(
        rf"^\s*\[\[\s*\"\$\{{\{{\s*needs\.{escaped}\.result\s*\}}\}}\"\s*==\s*\"success\"\s*\]\]\s*$",
        re.MULTILINE,
    )
    return any(assertion.search(run) for run in runs)


def validate(path: pathlib.Path) -> list[str]:
    try:
        workflow = load_workflow(path)
    except ValueError as error:
        return [str(error)]
    if not is_mapping(workflow):
        return ["workflow root must be a mapping"]

    errors: list[str] = []
    if not has_pull_request_without_paths_filter(workflow.get("on")):
        errors.append("workflow must trigger pull_request without a paths or paths-ignore filter")

    jobs = workflow.get("jobs")
    if not is_mapping(jobs):
        return errors + ["workflow must define a jobs mapping"]

    for name, required_command in REQUIRED_JOBS.items():
        job = jobs.get(name)
        if not is_mapping(job):
            errors.append(f"missing required matrix job: {name}")
            continue
        timeout = job.get("timeout-minutes")
        if not isinstance(timeout, str) or not timeout.isdigit():
            errors.append(f"{name}: missing timeout-minutes")
        elif int(timeout) > 25:
            errors.append(f"{name}: timeout-minutes must be at most 25")
        if has_forbidden_masking(job):
            errors.append(f"{name}: success masking is forbidden")
        if not runs_required_command(job, required_command):
            errors.append(f"{name}: missing executable script-owned command {required_command}")

    aggregate = jobs.get(AGGREGATE_JOB)
    if not is_mapping(aggregate):
        errors.append(f"missing blocking aggregate job: {AGGREGATE_JOB}")
        return errors
    timeout = aggregate.get("timeout-minutes")
    if not isinstance(timeout, str) or not timeout.isdigit() or int(timeout) > 25:
        errors.append(f"{AGGREGATE_JOB}: timeout-minutes must be at most 25")
    if aggregate.get("if", "").strip() != "always()":
        errors.append(f"{AGGREGATE_JOB}: if must be always()")
    if has_forbidden_masking(aggregate):
        errors.append(f"{AGGREGATE_JOB}: success masking is forbidden")
    needs = aggregate.get("needs")
    if not is_sequence(needs) or needs != REQUIRED_JOB_NAMES:
        errors.append(f"{AGGREGATE_JOB}: needs must equal the required matrix jobs")
    aggregate_runs = executable_steps(aggregate)
    for name in REQUIRED_JOB_NAMES:
        if not has_success_assertion(aggregate_runs, name):
            errors.append(f"{AGGREGATE_JOB}: missing executable success assertion for {name}")
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
