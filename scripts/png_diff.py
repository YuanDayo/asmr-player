#!/usr/bin/env python3
"""比较两张截图的像素差异，输出「应用区域」的差异比例。

为什么要自己解 PNG：这台机器没装 Pillow，也不一定联得上网。截图是
screencap 直接产出的非隔行 PNG（8 位 RGB/RGBA），用 zlib + 逐行反滤波
三十行就能解，比装依赖可靠。

状态栏里的时间每次截图都不同，整图 hash 永远不相等，所以默认跳过顶部
`--skip-top` 行，只比应用内容区。

用法：python scripts/png_diff.py a.png b.png [--skip-top 96] [--tol 2]
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib


def read_png(path: str) -> tuple[int, int, int, bytes]:
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("不是 PNG")
    pos = 8
    width = height = bitdepth = colortype = 0
    idat = b""
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height, bitdepth, colortype = struct.unpack(">IIBB", chunk[:10])
        elif ctype == b"IDAT":
            idat += chunk
        elif ctype == b"IEND":
            break
    if bitdepth != 8:
        raise ValueError(f"只支持 8 位，实际 {bitdepth}")
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[colortype]
    raw = zlib.decompress(idat)
    stride = width * channels
    out = bytearray(height * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:      # Sub
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif f == 2:    # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:    # Average
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:    # Paeth
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return width, height, channels, bytes(out)


def diff(a: str, b: str, skip_top: int, tol: int) -> int:
    wa, ha, ca, da = read_png(a)
    wb, hb, cb, db = read_png(b)
    if (wa, ha) != (wb, hb):
        print(f"{a} 与 {b} 尺寸不同：{wa}x{ha} vs {wb}x{hb}")
        return 2
    if ca != cb:
        print(f"通道数不同：{ca} vs {cb}")
        return 2

    diff_px = 0
    max_delta = 0
    total = 0
    x0 = y0 = 10 ** 9
    x1 = y1 = -1
    rows: dict[int, int] = {}
    for y in range(skip_top, ha):
        row = y * wa * ca
        row_diff = 0
        for x in range(wa):
            i = row + x * ca
            d = 0
            for c in range(min(ca, 3)):
                d = max(d, abs(da[i + c] - db[i + c]))
            if d > tol:
                diff_px += 1
                row_diff += 1
                max_delta = max(max_delta, d)
                x0 = min(x0, x); x1 = max(x1, x)
                y0 = min(y0, y); y1 = max(y1, y)
            total += 1
        if row_diff:
            rows[y] = row_diff
    pct = diff_px * 100.0 / max(total, 1)
    print(f"{pct:6.3f}%  差异像素 {diff_px}/{total}  最大通道差 {max_delta}   {a.split('/')[-1]}")
    if diff_px:
        print(f"        包围盒 x[{x0}-{x1}] y[{y0}-{y1}]")
        worst = sorted(rows.items(), key=lambda kv: -kv[1])[:3]
        for y, cnt in worst:
            print(f"        y={y:4d} 该行差异像素 {cnt}")
    return 1 if diff_px else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("a")
    ap.add_argument("b")
    ap.add_argument("--skip-top", type=int, default=96, help="跳过顶部若干行（状态栏时钟）")
    ap.add_argument("--tol", type=int, default=2, help="单通道差 ≤ 该值视为相同（抗压缩抖动）")
    args = ap.parse_args()
    return diff(args.a, args.b, args.skip_top, args.tol)


if __name__ == "__main__":
    sys.exit(main())
