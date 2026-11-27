#!/usr/bin/env python3
"""Generates the chaseway's resources: the sleeve texture, an empty base block model (the block
it goes through is drawn by the game from that block's own model), one standalone model per
sleeve slot (the two plates of that slot), the blockstate, the item icon and model, loot and recipe.

Run from the repository root:  python tools/gen_chaseway_assets.py

Geometry mirrors ChasewayBlock.java; change both. Pixels: slots are a three by three grid on each
pair of opposite faces, plate centres at 3, 8 and 13, plates four pixels square and a third of a
pixel proud of the face.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

GRID, CENTRES, HALF, PROUD = 3, (3, 8, 13), 2, 0.35
STEEL = (0x50, 0x54, 0x5A)
HOLE = (0x14, 0x16, 0x1A)
RING_UV = [6, 6, 10, 10]   # the plate samples this 4x4 patch of the sheet whatever its position


def textures():
    img = canvas(16, 16, STEEL + (255,))
    fill(img, 6, 6, 10, 10, shade(STEEL, 1.25))   # the plate's rim
    fill(img, 7, 7, 9, 9, HOLE)                   # the hole
    write_png(os.path.join(ASSETS, "textures", "block", "chaseway_sleeve.png"), img)
    # Item icon: a short sleeve seen end-on.
    img = canvas(16, 16, (0, 0, 0, 0))
    fill(img, 3, 3, 13, 13, shade(STEEL, 1.25))
    fill(img, 4, 4, 12, 12, STEEL)
    fill(img, 5, 5, 11, 11, shade(STEEL, 0.8))
    fill(img, 6, 6, 10, 10, HOLE)
    write_png(os.path.join(ASSETS, "textures", "item", "chaseway.png"), img)


def plate(axis, ca, cb, positive):
    lo_a, hi_a, lo_b, hi_b = ca - HALF, ca + HALF, cb - HALF, cb + HALF
    n0, n1 = (16 - PROUD, 16 + PROUD) if positive else (-PROUD, PROUD)
    if axis == "x":
        el = element(n0, lo_a, lo_b, n1, hi_a, hi_b, "#sleeve")      # a = y, b = z
    elif axis == "y":
        el = element(lo_a, n0, lo_b, hi_a, n1, hi_b, "#sleeve")      # a = x, b = z
    else:
        el = element(lo_a, lo_b, n0, hi_a, hi_b, n1, "#sleeve")      # a = x, b = y
    for face in el["faces"].values():
        face["uv"] = list(RING_UV)
    return el


def models():
    tex = {"sleeve": "%s:block/chaseway_sleeve" % MOD, "particle": "%s:block/chaseway_sleeve" % MOD}
    dump(os.path.join(ASSETS, "models", "block", "chaseway.json"), {"textures": {"particle": "%s:block/chaseway_sleeve" % MOD}})
    dump(os.path.join(ASSETS, "blockstates", "chaseway.json"), {"variants": {"": {"model": "%s:block/chaseway" % MOD}}})
    slot = 0
    for axis in ("x", "y", "z"):
        for a in range(GRID):
            for b in range(GRID):
                elements = [plate(axis, CENTRES[a], CENTRES[b], True), plate(axis, CENTRES[a], CENTRES[b], False)]
                dump(os.path.join(ASSETS, "models", "block", "chaseway_slot_%d.json" % slot), {"textures": tex, "elements": elements})
                slot += 1
    dump(os.path.join(ASSETS, "models", "item", "chaseway.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/chaseway" % MOD}})
    loot_table("chaseway")
    for stale in ("chaseway_x.json", "chaseway_y.json", "chaseway_z.json"):
        p = os.path.join(ASSETS, "models", "block", stale)
        if os.path.exists(p):
            os.remove(p)


def data():
    recipe("chaseway", ["NPN"], {"N": {"tag": "c:nuggets/iron"}, "P": {"tag": "c:plates/iron"}}, 2, {"items": "%s:conduit_bender" % MOD})


def main():
    textures()
    models()
    data()
    print("chaseway assets written")


if __name__ == "__main__":
    main()
