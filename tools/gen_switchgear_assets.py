#!/usr/bin/env python3
"""Generates the switchgear section resources: model, blockstate, textures, loot, recipes, bus bar item.

Run from the repository root:  python tools/gen_switchgear_assets.py

Geometry mirrors SwitchgearLayout.java; change both. North frame: back against z = 16, door at
z = 2, +x on the viewer's left.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

NAME = "switchgear"
BODY = (0, 1, 2, 16, 15, 16)
HUB_U = [3.5, 6.5, 9.5, 12.5]
COUPLER_Y = [3, 5.5, 8, 10.5]


def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    grey = (0x74, 0x7a, 0x82)
    edge = (0x3e, 0x42, 0x48)
    # Door: a handle, a nameplate and the breaker window (the breaker itself is drawn by the renderer).
    front = canvas(16, 16, grey + (255,))
    fill(front, 0, 0, 16, 1, shade(grey, 1.15))
    fill(front, 0, 15, 16, 16, edge)
    fill(front, 0, 0, 1, 16, edge)
    fill(front, 15, 0, 16, 16, edge)
    fill(front, 4, 5, 12, 10, shade(grey, 0.55))               # breaker window
    fill(front, 13, 7, 14, 10, (0xd0, 0xd4, 0xd8))              # handle
    fill(front, 5, 2, 11, 3, (0xd8, 0xc0, 0x30))                # nameplate
    fill(front, 4, 12, 12, 13, (0xc8, 0x28, 0x28))              # arc-flash label
    write_png(os.path.join(tex, NAME + "_front.png"), front)
    side = canvas(16, 16, grey + (255,))
    fill(side, 0, 0, 16, 1, shade(grey, 1.15))
    fill(side, 0, 15, 16, 16, edge)
    for y in COUPLER_Y:
        fill(side, 12, int(y), 13, int(y) + 1, (0xb8, 0x73, 0x33))   # bus bar stubs
    write_png(os.path.join(tex, NAME + "_side.png"), side)
    top = canvas(16, 16, grey + (255,))
    fill(top, 0, 0, 16, 1, shade(grey, 1.15))
    fill(top, 0, 15, 16, 16, edge)
    fill(top, 0, 0, 1, 16, edge)
    fill(top, 15, 0, 16, 16, edge)
    write_png(os.path.join(tex, NAME + "_top.png"), top)

    copper = (0xb8, 0x73, 0x33)
    bar = canvas(16, 16)
    fill(bar, 1, 6, 15, 10, copper)
    fill(bar, 1, 6, 15, 7, shade(copper, 1.3))
    fill(bar, 1, 9, 15, 10, shade(copper, 0.65))
    fill(bar, 4, 7, 5, 9, shade(copper, 0.5))
    fill(bar, 11, 7, 12, 9, shade(copper, 0.5))
    write_png(os.path.join(ASSETS, "textures", "item", "bus_bar.png"), bar)
    dump(os.path.join(ASSETS, "models", "item", "bus_bar.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/bus_bar" % MOD}})


def model():
    elements = [element(*BODY, "#side", cull_south=True)]
    elements[0]["faces"]["north"]["texture"] = "#front"
    elements[0]["faces"]["up"]["texture"] = "#top"
    for u in HUB_U:
        x = 16 - u
        elements.append(element(x - 1, 15, 8, x + 1, 16, 10, "#terminal"))
        elements.append(element(x - 1, 0, 8, x + 1, 1, 10, "#terminal"))
    tex = {
        "front": "%s:block/%s_front" % (MOD, NAME),
        "side": "%s:block/%s_side" % (MOD, NAME),
        "top": "%s:block/%s_top" % (MOD, NAME),
        "terminal": "%s:block/panel_terminal" % MOD,
        "particle": "%s:block/%s_side" % (MOD, NAME),
    }
    dump(os.path.join(ASSETS, "models", "block", NAME + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", NAME + ".json"), {"parent": "%s:block/%s" % (MOD, NAME)})
    m = "%s:block/%s" % (MOD, NAME)
    dump(os.path.join(ASSETS, "blockstates", NAME + ".json"), {"variants": {
        "facing=north": {"model": m}, "facing=east": {"model": m, "y": 90},
        "facing=south": {"model": m, "y": 180}, "facing=west": {"model": m, "y": 270},
    }})
    loot_table(NAME)


def recipes():
    bar = {"item": "%s:bus_bar" % MOD}
    recipe("bus_bar", ["CCC"], {"C": {"tag": "c:storage_blocks/copper"}}, 4, {"items": "minecraft:copper_block"})
    recipe(NAME, ["IBI", "BPB", "III"], {"I": {"tag": "c:plates/iron"}, "B": bar, "P": {"item": "%s:breaker_panel_400_3p" % MOD}}, 1,
           {"items": "%s:bus_bar" % MOD})


def main():
    textures()
    model()
    recipes()
    print("switchgear assets written")


if __name__ == "__main__":
    main()
