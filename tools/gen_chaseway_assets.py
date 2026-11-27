#!/usr/bin/env python3
"""Generates the chaseway's resources: the sleeve texture, one block model per axis (the two
sleeve-end plates; the block it goes through is drawn by the game from that block's own model),
the blockstate, the item icon and model, loot and recipe.

Run from the repository root:  python tools/gen_chaseway_assets.py

Geometry mirrors ChasewayBlock.java; change both. Pixels: a six-pixel plate round the hole on each
face the axis meets, a third of a pixel proud of the face.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

PLATE_MIN, PLATE_MAX, PROUD = 5, 11, 0.35
STEEL = (0x50, 0x54, 0x5A)
HOLE = (0x14, 0x16, 0x1A)


def textures():
    # The sleeve end as the plate elements sample it, pixels 5..11 of the sheet.
    img = canvas(16, 16, STEEL + (255,))
    fill(img, 5, 5, 11, 11, shade(STEEL, 1.25))   # the plate's rim
    fill(img, 6, 6, 10, 10, shade(STEEL, 0.8))    # the bushing
    fill(img, 7, 7, 9, 9, HOLE)                   # the hole
    write_png(os.path.join(ASSETS, "textures", "block", "chaseway_sleeve.png"), img)
    # Item icon: a short sleeve seen end-on.
    img = canvas(16, 16, (0, 0, 0, 0))
    fill(img, 3, 3, 13, 13, shade(STEEL, 1.25))
    fill(img, 4, 4, 12, 12, STEEL)
    fill(img, 5, 5, 11, 11, shade(STEEL, 0.8))
    fill(img, 6, 6, 10, 10, HOLE)
    write_png(os.path.join(ASSETS, "textures", "item", "chaseway.png"), img)


def plates(axis):
    lo, hi = PLATE_MIN, PLATE_MAX
    if axis == "x":
        return [element(16 - PROUD, lo, lo, 16 + PROUD, hi, hi, "#sleeve"), element(-PROUD, lo, lo, PROUD, hi, hi, "#sleeve")]
    if axis == "y":
        return [element(lo, 16 - PROUD, lo, hi, 16 + PROUD, hi, "#sleeve"), element(lo, -PROUD, lo, hi, PROUD, hi, "#sleeve")]
    return [element(lo, lo, 16 - PROUD, hi, hi, 16 + PROUD, "#sleeve"), element(lo, lo, -PROUD, hi, hi, PROUD, "#sleeve")]


def models():
    tex = {"sleeve": "%s:block/chaseway_sleeve" % MOD, "particle": "%s:block/chaseway_sleeve" % MOD}
    variants = {}
    for axis in ("x", "y", "z"):
        dump(os.path.join(ASSETS, "models", "block", "chaseway_%s.json" % axis), {"parent": "block/block", "textures": tex, "elements": plates(axis)})
        variants["axis=%s" % axis] = {"model": "%s:block/chaseway_%s" % (MOD, axis)}
    dump(os.path.join(ASSETS, "blockstates", "chaseway.json"), {"variants": variants})
    dump(os.path.join(ASSETS, "models", "item", "chaseway.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/chaseway" % MOD}})
    loot_table("chaseway")


def data():
    recipe("chaseway", ["NPN"], {"N": {"tag": "c:nuggets/iron"}, "P": {"tag": "c:plates/iron"}}, 2, {"items": "%s:conduit_bender" % MOD})


def main():
    textures()
    models()
    data()
    print("chaseway assets written")


if __name__ == "__main__":
    main()
