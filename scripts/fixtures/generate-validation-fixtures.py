#!/usr/bin/env python3
"""Generate small deterministic PNG fixtures without external dependencies."""

from __future__ import annotations

import pathlib
import struct
import sys
import zlib


def write_png(path: pathlib.Path, width: int, height: int, pixel) -> None:
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend(pixel(x, y))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    payload = b"\x89PNG\r\n\x1a\n"
    payload += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    payload += chunk(b"IDAT", zlib.compress(bytes(rows), 9))
    payload += chunk(b"IEND", b"")
    path.write_bytes(payload)


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <output-directory>", file=sys.stderr)
        return 2
    output = pathlib.Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)
    write_png(output / "tiny-red.png", 1, 1, lambda _x, _y: (255, 0, 0, 255))
    write_png(output / "solid-800x600.png", 800, 600, lambda _x, _y: (255, 255, 255, 255))
    write_png(
        output / "single-pixel-800x600.png",
        800,
        600,
        lambda x, y: ((x % 8) * 31, 255 - (x % 8) * 31, (x % 8) * 17, 255)
        if y == 300 and 400 <= x < 408
        else (255, 255, 255, 255),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
