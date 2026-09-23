#!/usr/bin/env python3
"""Generates the THHN building wire resources: item textures and models, Power Grid wire types,
recipes and the c:wires tag entry.

Run from the repository root:  python tools/gen_wire_assets.py

The gauge table mirrors WireGauge.java; change both.
"""
import json
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, advancement, canvas, dump, fill, shade, write_png

# id, label text, ohms/km, ampacity, thickness (blocks), copper cost (nuggets, ingots, blocks)
GAUGES = [
    ("14awg", "14", 8.28, 20, 0.04, (2, 0, 0)),
    ("12awg", "12", 5.21, 25, 0.045, (3, 0, 0)),
    ("10awg", "10", 3.28, 35, 0.05, (4, 0, 0)),
    ("8awg", "8", 2.06, 50, 0.06, (0, 1, 0)),
    ("6awg", "6", 1.30, 65, 0.07, (0, 2, 0)),
    ("4awg", "4", 0.815, 85, 0.08, (0, 3, 0)),
    ("2awg", "2", 0.513, 115, 0.09, (0, 4, 0)),
    ("1_0awg", "1/0", 0.322, 150, 0.10, (0, 5, 0)),
    ("2_0awg", "2/0", 0.256, 175, 0.11, (0, 6, 0)),
    ("4_0awg", "4/0", 0.161, 230, 0.12, (0, 8, 0)),
    ("250kcmil", "250", 0.136, 255, 0.13, (0, 0, 1)),
    ("350kcmil", "350", 0.0971, 310, 0.14, (0, 3, 1)),
    ("500kcmil", "500", 0.068, 380, 0.15, (0, 0, 2)),
]

FONT = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "8": ["111", "101", "111", "101", "111"],
    "/": ["001", "001", "010", "100", "100"],
}


def text(img, x, y, s, rgb):
    for ch in s:
        for dy, row in enumerate(FONT[ch]):
            for dx, bit in enumerate(row):
                if bit == "1":
                    fill(img, x + dx, y + dy, x + dx + 1, y + dy + 1, rgb)
        x += 4


def textures():
    item = os.path.join(ASSETS, "textures", "item")
    jacket = (0xe6, 0xe6, 0xe0)
    copper = (0xc8, 0x7a, 0x3c)
    for i, (gid, label, _r, _a, _t, _cost) in enumerate(GAUGES):
        # A coil of white THHN seen from the side, thicker for bigger gauges, the gauge printed on it.
        img = canvas(16, 16)
        t = 2 + i // 3
        for x in range(1, 15):
            for y0 in (2, 2 + t + 1):
                fill(img, x, y0, x + 1, y0 + t, jacket)
                fill(img, x, y0, x + 1, y0 + 1, shade(jacket, 1.05))
                fill(img, x, y0 + t - 1, x + 1, y0 + t, shade(jacket, 0.7))
        fill(img, 1, 2, 2, 2 * t + 4, shade(jacket, 0.8))
        fill(img, 14, 2, 15, 2 * t + 4, shade(jacket, 0.8))
        # Stripped copper end.
        fill(img, 12, 3, 15, 3 + max(1, t - 1), copper)
        # Label along the bottom.
        text(img, 16 - 4 * len(label) - (1 if len(label) < 3 else 0), 10, label, (0x30, 0x30, 0x30))
        write_png(os.path.join(item, "wire_%s.png" % gid), img)
        dump(os.path.join(ASSETS, "models", "item", "wire_%s.json" % gid), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/wire_%s" % (MOD, gid)},
        })


def wire_types():
    for gid, _label, ohms_per_km, amps, thickness, _cost in GAUGES:
        dump(os.path.join(DATA, "powergrid", "wire_types", "wire_%s.json" % gid), {
            "colorable": True, "cord": False, "horizontalCoefficient": 1.01, "insulated": True,
            "itemsPerMeter": 1.0, "maximumCurrent": float(amps), "maximumLength": 64.0,
            "resistancePerItem": round(ohms_per_km / 1000, 7), "texture": "powergrid:textures/special/insulated_wire.png",
            "thermalMass": round(1.0 + thickness * 10, 2), "verticalCoefficient": 1.2, "wireThickness": thickness,
        })


def recipes():
    for gid, _label, _r, _a, _t, (nuggets, ingots, blocks) in GAUGES:
        ingredients = [{"item": "minecraft:dried_kelp"}]
        ingredients += [{"tag": "c:nuggets/copper"}] * nuggets
        ingredients += [{"tag": "c:ingots/copper"}] * ingots
        ingredients += [{"tag": "c:storage_blocks/copper"}] * blocks
        name = "wire_%s" % gid
        dump(os.path.join(DATA, "recipe", "crafting", name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
            "result": {"count": 8, "id": "%s:%s" % (MOD, name)},
        })
        advancement(name, {"items": "minecraft:copper_ingot"})


def tags():
    path = os.path.join(os.path.dirname(DATA), "c", "tags", "item", "wires.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    values = ["%s:wire_%s" % (MOD, gid) for gid, *_ in GAUGES]
    with open(path, "w", newline="\n") as f:
        json.dump({"replace": False, "values": values}, f, indent=2)
        f.write("\n")


def main():
    textures()
    wire_types()
    recipes()
    tags()
    print("wire assets written")


if __name__ == "__main__":
    main()
