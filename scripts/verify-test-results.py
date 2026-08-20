#!/usr/bin/env python3
"""Validate deterministic JUnit, PNG, and closure-report evidence."""

from __future__ import annotations

import argparse
from collections import Counter
import pathlib
import re
import struct
import sys
import xml.etree.ElementTree as element_tree
import zlib


class ValidationError(Exception):
    """Raised when test evidence is incomplete or non-deterministic."""


def fail(message: str) -> None:
    raise ValidationError(message)


def collect_junit_files(entries: list[str]) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for entry in entries:
        path = pathlib.Path(entry)
        if path.is_dir():
            files.extend(sorted(path.rglob("TEST-*.xml")))
        elif path.is_file():
            files.append(path)
        else:
            fail(f"JUnit evidence path does not exist: {path}")
    if not files:
        fail("no TEST-*.xml JUnit evidence files were found")
    return files


def non_negative_integer(value: str | None, field: str, path: pathlib.Path) -> int:
    if value is None or not value.isdigit():
        fail(f"{path}: missing or invalid {field} attribute")
    return int(value)


def validate_junit(entries: list[str]) -> None:
    totals = {"tests": 0, "skipped": 0, "failures": 0, "errors": 0}
    for path in collect_junit_files(entries):
        try:
            root = element_tree.parse(path).getroot()
        except element_tree.ParseError as error:
            fail(f"{path}: invalid JUnit XML: {error}")
        if root.tag == "testsuite":
            top_level_suites = [root]
        elif root.tag == "testsuites":
            top_level_suites = [suite for suite in root if suite.tag == "testsuite"]
            if not top_level_suites:
                fail(f"{path}: testsuites root contains no testsuite")
            validate_declared_counts(root, path)
        else:
            fail(f"{path}: expected a testsuite or testsuites root, got {root.tag!r}")
        for suite in top_level_suites:
            validate_suite_counts(suite, path)
            for field in totals:
                totals[field] += non_negative_integer(suite.get(field), field, path)

    if totals["tests"] == 0:
        fail("JUnit evidence reported zero tests")
    for field in ("skipped", "failures", "errors"):
        if totals[field] != 0:
            fail(f"JUnit evidence reported {totals[field]} {field}")
    print("JUnit evidence: " + " ".join(f"{key}={value}" for key, value in totals.items()))


def validate_declared_counts(suite: element_tree.Element, path: pathlib.Path) -> None:
    observed = {
        "tests": sum(1 for _ in suite.iter("testcase")),
        "skipped": sum(1 for _ in suite.iter("skipped")),
        "failures": sum(1 for _ in suite.iter("failure")),
        "errors": sum(1 for _ in suite.iter("error")),
    }
    for field, count in observed.items():
        declared = non_negative_integer(suite.get(field), field, path)
        noun = "testcase" if field == "tests" else field[:-1] if field.endswith("s") else field
        if declared != count:
            fail(f"{path}: declared {field}={declared} does not match {noun} count={count}")


def validate_suite_counts(suite: element_tree.Element, path: pathlib.Path) -> None:
    validate_declared_counts(suite, path)
    for child in suite:
        if child.tag == "testsuite":
            validate_suite_counts(child, path)


def png_rows(path: pathlib.Path) -> tuple[int, int, list[bytes], int, int]:
    try:
        payload = path.read_bytes()
    except OSError as error:
        fail(f"cannot read PNG {path}: {error}")
    if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
        fail(f"{path}: not a PNG file")

    cursor = 8
    width = height = bit_depth = color_type = None
    compressed = bytearray()
    while cursor < len(payload):
        if cursor + 12 > len(payload):
            fail(f"{path}: truncated PNG chunk")
        length = struct.unpack(">I", payload[cursor : cursor + 4])[0]
        kind = payload[cursor + 4 : cursor + 8]
        end = cursor + 12 + length
        if end > len(payload):
            fail(f"{path}: truncated {kind.decode('ascii', 'replace')} chunk")
        data = payload[cursor + 8 : cursor + 8 + length]
        expected_crc = struct.unpack(">I", payload[cursor + 8 + length : end])[0]
        if zlib.crc32(kind + data) & 0xFFFFFFFF != expected_crc:
            fail(f"{path}: corrupt {kind.decode('ascii', 'replace')} chunk CRC")
        if kind == b"IHDR":
            if len(data) != 13 or width is not None:
                fail(f"{path}: invalid IHDR")
            width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack(">IIBBBBB", data)
            if not width or not height:
                fail(f"{path}: PNG dimensions must be non-zero")
            if bit_depth != 8 or color_type not in {0, 2, 4, 6}:
                fail(f"{path}: requires an 8-bit grayscale/RGB/RGBA PNG")
            if compression != 0 or filtering != 0 or interlace != 0:
                fail(f"{path}: requires a non-interlaced standard PNG")
        elif kind == b"IDAT":
            compressed.extend(data)
        elif kind == b"IEND":
            if end != len(payload):
                fail(f"{path}: trailing bytes after IEND")
            break
        cursor = end
    else:
        fail(f"{path}: missing IEND")

    if width is None or height is None or not compressed:
        fail(f"{path}: missing IHDR or IDAT evidence")
    try:
        decoded = zlib.decompress(compressed)
    except zlib.error as error:
        fail(f"{path}: cannot decode IDAT: {error}")

    channels = {0: 1, 2: 3, 4: 2, 6: 4}[color_type]
    stride = width * channels
    if len(decoded) != height * (stride + 1):
        fail(f"{path}: decoded PNG size does not match dimensions")
    rows: list[bytes] = []
    offset = 0
    for _ in range(height):
        filter_type = decoded[offset]
        current = bytearray(decoded[offset + 1 : offset + 1 + stride])
        previous = rows[-1] if rows else bytes(stride)
        for index in range(stride):
            left = current[index - channels] if index >= channels else 0
            up = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                current[index] = (current[index] + left) & 0xFF
            elif filter_type == 2:
                current[index] = (current[index] + up) & 0xFF
            elif filter_type == 3:
                current[index] = (current[index] + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                predictor = left + up - upper_left
                distances = (abs(predictor - left), abs(predictor - up), abs(predictor - upper_left))
                current[index] = (current[index] + (left, up, upper_left)[distances.index(min(distances))]) & 0xFF
            elif filter_type != 0:
                fail(f"{path}: unsupported PNG filter {filter_type}")
        rows.append(bytes(current))
        offset += stride + 1
    return width, height, rows, channels, color_type


PNG_TARGETS = {
    "android-triangle": {"width": 800, "height": 600, "min_distinct_colors": 8, "min_non_background_pixels": 24_000},
    "compose-raster": {"width": 800, "height": 600, "min_distinct_colors": 8, "min_non_background_pixels": 4_800},
    "hello-triangle-baseline": {"width": 800, "height": 600, "min_distinct_colors": 4, "min_non_background_pixels": 4_800},
}


def visible_rgba_pixels(rows: list[bytes], channels: int, color_type: int):
    for row in rows:
        for pixel_start in range(0, len(row), channels):
            pixel = row[pixel_start : pixel_start + channels]
            if color_type == 0:
                red = green = blue = pixel[0]
                alpha = 255
            elif color_type == 2:
                red, green, blue = pixel
                alpha = 255
            elif color_type == 4:
                red = green = blue = pixel[0]
                alpha = pixel[1]
            else:
                red, green, blue, alpha = pixel
            yield (red, green, blue, alpha) if alpha else (0, 0, 0, 0)


def validate_png(entries: list[str], target_name: str) -> None:
    if not entries:
        fail("at least one PNG path is required")
    target = PNG_TARGETS[target_name]
    for entry in entries:
        path = pathlib.Path(entry)
        width, height, rows, channels, color_type = png_rows(path)
        if (width, height) != (target["width"], target["height"]):
            fail(f"{path}: dimensions must equal {target['width']}x{target['height']} for {target_name}")
        pixels = Counter(visible_rgba_pixels(rows, channels, color_type))
        visible_colors = {pixel for pixel in pixels if pixel[3] != 0}
        if len(visible_colors) < target["min_distinct_colors"]:
            fail(f"{path}: must contain at least {target['min_distinct_colors']} distinct visible colors for {target_name}")
        background, background_count = pixels.most_common(1)[0]
        non_background_pixels = sum(count for pixel, count in pixels.items() if pixel != background and pixel[3] != 0)
        if non_background_pixels < target["min_non_background_pixels"]:
            fail(
                f"{path}: non-background visible pixels={non_background_pixels} is below "
                f"{target['min_non_background_pixels']} for {target_name} (background={background_count})"
            )
        print(
            f"PNG evidence: {path} {width}x{height} target={target_name} "
            f"colors={len(visible_colors)} non-background={non_background_pixels}"
        )


REPORT_FIELDS = ("Finding:", "Test/command:", "Environment:", "Result:", "Proof path:")


def validate_report(entry: str) -> None:
    path = pathlib.Path(entry)
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        fail(f"cannot read report {path}: {error}")
    rows = [(int(match.group(1)), line) for line in lines if (match := re.match(r"^(\d+)\.\s+", line))]
    if len(rows) != 19:
        fail(f"{path}: expected exactly 19 numbered traceability rows, found {len(rows)}")
    if [number for number, _ in rows] != list(range(1, 20)):
        fail(f"{path}: traceability rows must be numbered consecutively from 1 to 19")
    for number, line in rows:
        missing = [field for field in REPORT_FIELDS if field not in line]
        if missing:
            fail(f"{path}: row {number} is missing required fields: {', '.join(missing)}")
        matches = list(re.finditer(r"Finding:|Test/command:|Environment:|Result:|Proof path:", line))
        values = {
            match.group(0): line[match.end() : matches[index + 1].start() if index + 1 < len(matches) else None].strip(" |")
            for index, match in enumerate(matches)
        }
        empty = [field for field in REPORT_FIELDS if not values.get(field)]
        if empty:
            fail(f"{path}: row {number} has empty required fields: {', '.join(empty)}")
    print(f"Report evidence: {path} contains 19 complete traceability rows")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", action="append", default=[], metavar="PATH", help="JUnit XML file or directory")
    parser.add_argument("--png", action="append", default=[], metavar="PATH", help="PNG capture to decode and inspect")
    parser.add_argument("--png-target", choices=sorted(PNG_TARGETS), help="required capture contract for every --png")
    parser.add_argument("--report", metavar="PATH", help="19-row cross-platform correctness report")
    args = parser.parse_args()
    if not args.junit and not args.png and not args.report:
        parser.error("provide --junit, --png, or --report")
    if args.png and not args.png_target:
        parser.error("--png-target is required with --png")
    try:
        if args.junit:
            validate_junit(args.junit)
        if args.png:
            validate_png(args.png, args.png_target)
        if args.report:
            validate_report(args.report)
    except ValidationError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
