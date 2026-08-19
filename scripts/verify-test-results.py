#!/usr/bin/env python3
"""Validate deterministic JUnit, PNG, and closure-report evidence."""

from __future__ import annotations

import argparse
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
        if root.tag != "testsuite":
            fail(f"{path}: expected a testsuite root, got {root.tag!r}")
        for field in totals:
            totals[field] += non_negative_integer(root.get(field), field, path)

    if totals["tests"] == 0:
        fail("JUnit evidence reported zero tests")
    for field in ("skipped", "failures", "errors"):
        if totals[field] != 0:
            fail(f"JUnit evidence reported {totals[field]} {field}")
    print("JUnit evidence: " + " ".join(f"{key}={value}" for key, value in totals.items()))


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


def validate_png(entries: list[str]) -> None:
    if not entries:
        fail("at least one PNG path is required")
    for entry in entries:
        path = pathlib.Path(entry)
        width, height, rows, channels, color_type = png_rows(path)
        has_foreground = False
        for row in rows:
            for pixel_start in range(0, len(row), channels):
                pixel = row[pixel_start : pixel_start + channels]
                alpha = pixel[-1] if color_type in {4, 6} else 255
                color = pixel[:1] if color_type in {0, 4} else pixel[:3]
                if alpha != 0 and any(component != 0 for component in color):
                    has_foreground = True
                    break
            if has_foreground:
                break
        if not has_foreground:
            fail(f"{path}: PNG contains no non-background pixels")
        print(f"PNG evidence: {path} {width}x{height} decoded with foreground pixels")


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
    print(f"Report evidence: {path} contains 19 complete traceability rows")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", action="append", default=[], metavar="PATH", help="JUnit XML file or directory")
    parser.add_argument("--png", action="append", default=[], metavar="PATH", help="PNG capture to decode and inspect")
    parser.add_argument("--report", metavar="PATH", help="19-row cross-platform correctness report")
    args = parser.parse_args()
    if not args.junit and not args.png and not args.report:
        parser.error("provide --junit, --png, or --report")
    try:
        if args.junit:
            validate_junit(args.junit)
        if args.png:
            validate_png(args.png)
        if args.report:
            validate_report(args.report)
    except ValidationError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
