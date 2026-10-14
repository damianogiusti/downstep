"""Packs PNG files into a Windows .ico (PNG-compressed entries, Vista and later).

Usage: make-ico.py output.ico icon_16.png icon_32.png ... (each square, at most 256 px).
"""
import struct
import sys


def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    return struct.unpack(">II", data[16:24])


def main(output, inputs):
    images = []
    for path in inputs:
        with open(path, "rb") as f:
            data = f.read()
        width, height = png_size(data)
        if width != height or width > 256:
            raise ValueError(f"{path}: {width}x{height}, need square and at most 256 px")
        images.append((width, data))
    images.sort()
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = len(header) + 16 * len(images)
    entries, payload = b"", b""
    for size, data in images:
        # 0 means 256 in the one-byte width and height fields.
        entries += struct.pack("<BBBBHHII", size % 256, size % 256, 0, 0, 1, 32, len(data), offset + len(payload))
        payload += data
    with open(output, "wb") as f:
        f.write(header + entries + payload)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
