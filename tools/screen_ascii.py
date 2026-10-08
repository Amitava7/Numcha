#!/usr/bin/env python3
"""Turn a raw `adb exec-out screencap` dump into ASCII and colour statistics.

CI cannot show pictures, so this prints the screen as text: the calendar
squares are recognisable, and the percentages say whether each colour drew at
all. Used by tools/smoke_test.sh to assert that a coloured day really shows on
the mosaic rather than a blank view.

  adb exec-out screencap > screen.raw
  python3 tools/screen_ascii.py screen.raw --top 0.15 --bottom 0.70
"""

import argparse
import struct
import sys

# The app's palette, from Ui.java. Anything far from all of these is "other".
PALETTE = [
    ("bg", (0x16, 0x1B, 0x22), " "),
    ("panel", (0x1F, 0x26, 0x30), ","),
    ("button", (0x2D, 0x36, 0x42), ";"),
    ("empty", (0x2A, 0x32, 0x3D), "."),
    ("plain", (0x56, 0x60, 0x6D), "o"),
    ("text", (0xE6, 0xED, 0xF3), "T"),
    ("dim", (0x8B, 0x94, 0x9E), "t"),
    ("accent", (0x58, 0xA6, 0xFF), "B"),
    ("green", (0x2F, 0xBF, 0x71), "G"),
    ("yellow", (0xF2, 0xC9, 0x4C), "Y"),
    ("red", (0xE5, 0x54, 0x4B), "R"),
    ("black", (0x00, 0x00, 0x00), "K"),
]


def read_screencap(path):
    """screencap writes width, height, format, [colourspace], then RGBA."""
    with open(path, "rb") as fh:
        data = fh.read()
    w, h, _fmt = struct.unpack("<III", data[:12])
    for header in (12, 16):
        if len(data) - header == w * h * 4:
            return w, h, header, data
    sys.exit("not a screencap dump: %dx%d does not match %d bytes"
             % (w, h, len(data)))


def classify(r, g, b):
    best = None
    best_d = 1 << 30
    for name, (pr, pg, pb), ch in PALETTE:
        d = (r - pr) ** 2 + (g - pg) ** 2 + (b - pb) ** 2
        if d < best_d:
            best_d = d
            best = (name, ch)
    # far from everything in the palette: call it what it is
    if best_d > 2600:
        return "other", "?"
    return best


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dump")
    ap.add_argument("--cols", type=int, default=100)
    ap.add_argument("--rows", type=int, default=44)
    ap.add_argument("--top", type=float, default=0.0)
    ap.add_argument("--bottom", type=float, default=1.0)
    a = ap.parse_args()

    w, h, off, data = read_screencap(a.dump)
    y0, y1 = int(h * a.top), int(h * a.bottom)
    counts = {}
    lines = []
    for row in range(a.rows):
        y = y0 + (y1 - y0) * row // a.rows
        line = []
        for col in range(a.cols):
            x = w * col // a.cols
            i = off + (y * w + x) * 4
            name, ch = classify(data[i], data[i + 1], data[i + 2])
            counts[name] = counts.get(name, 0) + 1
            line.append(ch)
        lines.append("".join(line))

    print("screen %dx%d, sampled rows %d-%d" % (w, h, y0, y1))
    print("\n".join(lines))
    total = float(sum(counts.values()))
    parts = sorted(counts.items(), key=lambda kv: -kv[1])
    print(" ".join("%s=%.1f%%" % (k, 100 * v / total) for k, v in parts))
    # machine readable, for the shell to assert on
    for k, v in parts:
        print("PCT_%s %.1f" % (k.upper(), 100 * v / total))


if __name__ == "__main__":
    main()
