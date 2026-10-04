"""Assets for the 8 kV Digital Voltage Regulator: the 2 kV model's models and blockstate with a
recoloured texture. Run from anywhere: python tools/gen_vfd_assets.py"""
import json
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, DATA, MOD, loot_table, recipe, write_png, dump

BLOCK_TEX = os.path.join(ASSETS, "textures", "block")


def read_png(path):
    """8-bit RGBA, non-interlaced PNG -> rows of (r, g, b, a). Enough for our own textures."""
    data = open(path, "rb").read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    idat = b""
    w = h = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            w, h, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert depth == 8 and colour == 6 and interlace == 0, "expected 8-bit RGBA"
        elif tag == b"IDAT":
            idat += body
        pos += 12 + length
    raw = zlib.decompress(idat)
    stride = w * 4
    rows = []
    prev = bytearray(stride)
    i = 0
    for _ in range(h):
        f = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if pa <= pb and pa <= pc else b if pb <= pc else c
                line[x] = (line[x] + pred) & 255
        rows.append([tuple(line[x:x + 4]) for x in range(0, stride, 4)])
        prev = line
    return rows


def recolour(px):
    """Grey housing stays; anything with a blue/teal tint turns amber so the high-voltage unit reads at a glance."""
    r, g, b, a = px
    if a == 0:
        return px
    if b > r + 20 and b > g - 10:
        lum = (r + g + b) // 3
        return (min(255, lum + 90), min(255, lum + 40), max(0, lum - 50), a)
    return (min(255, int(r * 1.05)), g, max(0, int(b * 0.9)), a)


def texture():
    rows = read_png(os.path.join(BLOCK_TEX, "vfd.png"))
    # The housing is the left 12 columns; the top-right corner holds the red and blue terminal
    # patches (columns 12-15, rows 0-1) that the models sample, and they stay as they are.
    out = [[px if (x >= 12 and y < 2) else recolour(px) for x, px in enumerate(row)] for y, row in enumerate(rows)]
    # A warning stripe across the top row of the face, the housing only.
    for x in range(0, 12, 2):
        out[1][x] = (250, 200, 30, 255)
        out[1][x + 1] = (30, 30, 30, 255)
    write_png(os.path.join(BLOCK_TEX, "vfd_8kv.png"), out)


def models():
    for part in ("block_v", "block_h"):
        src = json.load(open(os.path.join(ASSETS, "models", "block", "vfd", part + ".json")))
        src["textures"] = {k: (v.replace("block/vfd", "block/vfd_8kv") if v.endswith("block/vfd") else v) for k, v in src["textures"].items()}
        dump(os.path.join(ASSETS, "models", "block", "vfd_8kv", part + ".json"), src)
    state = json.load(open(os.path.join(ASSETS, "blockstates", "vfd.json")))
    for variant in state["variants"].values():
        variant["model"] = variant["model"].replace("block/vfd/", "block/vfd_8kv/")
    dump(os.path.join(ASSETS, "blockstates", "vfd_8kv.json"), state)
    dump(os.path.join(ASSETS, "models", "item", "vfd_8kv.json"), {"parent": "%s:block/vfd_8kv/block_v" % MOD})


def data():
    loot_table("vfd_8kv")
    recipe("vfd_8kv", [" M ", "MVM", " T "], {"M": {"tag": "c:copper_coils"}, "V": {"item": "%s:vfd" % MOD},
                                              "T": {"item": "powergrid:transformer_core"}}, 1, {"items": "%s:vfd" % MOD})


def main():
    texture()
    models()
    data()
    print("8 kV regulator assets written")


if __name__ == "__main__":
    main()
