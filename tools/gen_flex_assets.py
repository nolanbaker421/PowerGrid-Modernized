"""Assets for the 1" flexible conduit: a coil icon, its item model, Power Grid wire type and recipe.
Run from anywhere: python tools/gen_flex_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, fill, shade, write_png, dump
from gen_conduit_assets import shaped

FLEX = "conduit_flex_one"


def icon():
    grey = (0x8a, 0x8e, 0x92)
    img = canvas(16, 16)
    # A coiled whip: a ring with ribs.
    ring = [(5, 2), (6, 2), (7, 2), (8, 2), (9, 2), (10, 2), (4, 3), (11, 3), (3, 4), (12, 4), (2, 5), (13, 5), (2, 6), (13, 6),
            (2, 7), (13, 7), (2, 8), (13, 8), (2, 9), (13, 9), (2, 10), (13, 10), (3, 11), (12, 11), (4, 12), (11, 12),
            (5, 13), (6, 13), (7, 13), (8, 13), (9, 13), (10, 13)]
    for x, y in ring:
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                fill(img, x + dx, y + dy, x + dx + 1, y + dy + 1, shade(grey, 0.75 if (x + y) % 2 else 1.0))
    fill(img, 7, 7, 9, 9, shade(grey, 0.5))
    write_png(os.path.join(ASSETS, "textures", "item", FLEX + ".png"), img)
    dump(os.path.join(ASSETS, "models", "item", FLEX + ".json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, FLEX)}})


def wire_type():
    dump(os.path.join(DATA, "powergrid", "wire_types", FLEX + ".json"), {
        "colorable": True, "cord": False, "horizontalCoefficient": 1.0, "insulated": True,
        "itemsPerMeter": 1.0, "maximumCurrent": 1.0, "maximumLength": 16.0,
        "resistancePerItem": 0.001, "texture": "%s:textures/special/conduit.png" % MOD,
        "thermalMass": 1.0, "verticalCoefficient": 1.0, "wireThickness": 0.16,
    })


def recipe():
    shaped(FLEX, ["SCS"], {"S": {"item": "minecraft:string"}, "C": {"item": "%s:conduit_one" % MOD}}, 2,
           {"items": "%s:conduit_one" % MOD})


def main():
    icon()
    wire_type()
    recipe()
    print("flexible conduit assets written")


if __name__ == "__main__":
    main()
