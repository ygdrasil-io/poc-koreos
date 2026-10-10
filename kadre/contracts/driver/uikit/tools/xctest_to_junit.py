#!/usr/bin/env python3
"""Export XCTest xcresult to JUnit XML preserving names, failures, skips and duration.

Usage: xctest_to_junit.py <xcresult> <output-dir>
Reads `xcrun xcresulttool get test-results tests --format json` and writes one
TEST-<suite>.xml per test class into <output-dir>. Schema adapter lives in
`parse_nodes` — re-verify against the local Xcode's output when it drifts.
"""
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def parse_nodes(document):
    """Adaptateur de schéma xcresulttool (Xcode 27.0 / 27A266a). Retourne [(classname, name, time, status, message)].

    Schéma réellement observé (kadre/contracts/driver/uikit/build/xcresult/{ios,tvos}.xcresult) :
    la racine porte `testNodes` ; chaque nœud a `nodeType` ∈ {"Test Plan", "Unit test bundle",
    "Test Suite", "Test Case"}, `name`, `result` ∈ {"Passed", "Failed", "Skipped"} (capitalisé),
    `children`, et sur les Test Case uniquement : `duration` — chaîne LOCALISÉE ("0,006s",
    virgule décimale : ne jamais parser) — et `durationInSeconds`, float indépendant de la
    locale, qui est la seule source de temps utilisée.

    status ∈ {"passed", "failed", "skipped"} ; message = texte d'échec/skip ou "".
    """
    cases = []

    def messages_of(node):
        texts = []
        for failure in node.get("failures") or []:
            if isinstance(failure, dict):
                text = failure.get("failureText") or failure.get("name") or ""
                if text:
                    texts.append(text)
        for child in node.get("children") or []:
            if child.get("nodeType") in ("Failure Message", "Skipped Message"):
                text = child.get("name") or ""
                if text:
                    texts.append(text)
        return "; ".join(texts)

    def walk(node, suite):
        node_type = node.get("nodeType", "")
        name = node.get("name", "")
        if node_type == "Test Case":
            raw_result = node.get("result", "")
            result = raw_result.lower()
            if result not in ("passed", "failed", "skipped"):
                raise ValueError(
                    f"résultat XCTest inattendu « {raw_result} » pour le cas « {name} » — "
                    "refus de le compter comme Passed (politique never-false-green)"
                )
            seconds = node.get("durationInSeconds")
            duration = f"{float(seconds):.6f}" if seconds is not None else "0.000000"
            message = messages_of(node) if result in ("failed", "skipped") else ""
            cases.append((suite, name, duration, result, message))
            return
        child_suite = name if node_type == "Test Suite" else suite
        for child in node.get("children", []):
            walk(child, child_suite)

    for top in document.get("testNodes", []):
        walk(top, "")
    return cases


def write_junit(cases, output_dir):
    by_class = {}
    for classname, name, duration, status, message in cases:
        by_class.setdefault(classname, []).append((name, duration, status, message))
    output_dir.mkdir(parents=True, exist_ok=True)
    for classname, items in by_class.items():
        total_seconds = sum(float(duration) for _, duration, _, _ in items)
        suite = ET.Element("testsuite", {
            "name": classname,
            "tests": str(len(items)),
            "failures": str(sum(1 for _, _, s, _ in items if s == "failed")),
            "errors": "0",
            "skipped": str(sum(1 for _, _, s, _ in items if s == "skipped")),
            "time": f"{total_seconds:.6f}",
        })
        for name, duration, status, message in items:
            case = ET.SubElement(suite, "testcase", {"classname": classname, "name": name, "time": duration})
            if status == "failed":
                ET.SubElement(case, "failure", {"message": message, "type": "XCTestFailure"})
            elif status == "skipped":
                ET.SubElement(case, "skipped", {"message": message})
        ET.ElementTree(suite).write(output_dir / f"TEST-{classname}.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    xcresult, output = Path(sys.argv[1]), Path(sys.argv[2])
    raw = subprocess.run(
        ["xcrun", "xcresulttool", "get", "test-results", "tests", "--path", str(xcresult), "--format", "json"],
        check=True, capture_output=True, text=True,
    ).stdout
    write_junit(parse_nodes(json.loads(raw)), output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
