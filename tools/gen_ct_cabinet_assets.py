#!/usr/bin/env python3
"""Generates the CT cabinet resources: model, blockstate, item model, textures, loot and recipe.

Run from the repository root:  python tools/gen_ct_cabinet_assets.py

Geometry mirrors CtCabinetBlock.java; change both. North frame like the breaker panel: enclosure
against the south wall (z 10..16), front facing north, +x on the viewer's left.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, shade, text, write_png

BODY = (2, 1, 10, 14, 15, 16)
HUB_U = [3.5, 6.5, 9.5, 12.5]
HUB_Z1, HUB_Z2 = 12.5, 14.5
JACK = (14, 7, 12.5, 15, 9, 14.5)
NAME = "ct_cabinet"


def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    body = (0x8a, 0x8d, 0x90)
    edge = (0x55, 0x58, 0x5c)
    front = canvas(16, 16, body + (255,))
    fill(front, 2, 1, 14, 15, shade(body, 0.9))
    fill(front, 2, 1, 14, 2, edge)
    fill(front, 2, 14, 14, 15, edge)
    fill(front, 2, 1, 3, 15, edge)
    fill(front, 13, 1, 14, 15, edge)
    # Meter window with a green readout.
    fill(front, 4, 3, 12, 7, (0x10, 0x14, 0x10))
    fill(front, 5, 4, 11, 6, (0x28, 0x60, 0x30))
    fill(front, 5, 4, 8, 5, (0x60, 0xd8, 0x70))
    # Four CT rings below the window.
    for x in (4, 6, 8, 10):
        fill(front, x, 9, x + 2, 12, (0x3a, 0x3a, 0x3a))
        fill(front, x, 9, x + 2, 10, (0x60, 0x60, 0x60))
    write_png(os.path.join(tex, NAME + "_front.png"), front)

    side = canvas(16, 16, body + (255,))
    fill(side, 0, 0, 16, 1, shade(body, 1.12))
    fill(side, 0, 15, 16, 16, edge)
    fill(side, 0, 0, 1, 16, edge)
    fill(side, 15, 0, 16, 16, edge)
    write_png(os.path.join(tex, NAME + "_side.png"), side)


def model():
    tex = {
        "front": "%s:block/%s_front" % (MOD, NAME),
        "side": "%s:block/%s_side" % (MOD, NAME),
        "terminal": "%s:block/panel_terminal" % MOD,
        "jack": "%s:block/jack_pin" % MOD,
        "particle": "%s:block/%s_side" % (MOD, NAME),
    }
    elements = [element(*BODY, "#side", cull_south=True)]
    elements[0]["faces"]["north"]["texture"] = "#front"
    for u in HUB_U:
        x = 16 - u
        elements.append(element(x - 1, 15, HUB_Z1, x + 1, 16, HUB_Z2, "#terminal"))
        elements.append(element(x - 1, 0, HUB_Z1, x + 1, 1, HUB_Z2, "#terminal"))
    elements.append(element(*JACK, "#jack"))
    dump(os.path.join(ASSETS, "models", "block", NAME + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", NAME + ".json"), {"parent": "%s:block/%s" % (MOD, NAME)})
    dump(os.path.join(ASSETS, "blockstates", NAME + ".json"), {
        "variants": {
            "facing=north": {"model": "%s:block/%s" % (MOD, NAME)},
            "facing=east": {"model": "%s:block/%s" % (MOD, NAME), "y": 90},
            "facing=south": {"model": "%s:block/%s" % (MOD, NAME), "y": 180},
            "facing=west": {"model": "%s:block/%s" % (MOD, NAME), "y": 270},
        }
    })


def data():
    dump(os.path.join(DATA, "loot_table", "blocks", NAME + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (MOD, NAME)}],
            "rolls": 1.0,
        }],
        "random_sequence": "%s:blocks/%s" % (MOD, NAME),
    })
    dump(os.path.join(DATA, "recipe", "crafting", NAME + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {
            "P": {"item": "powergrid:pins"},
            "C": {"item": "minecraft:comparator"},
            "A": {"item": "powergrid:conductive_casing"},
            "I": {"tag": "c:plates/iron"},
            "W": {"item": "powergrid:wire"},
        },
        "pattern": ["PCP", "IAI", "WIW"],
        "result": {"count": 1, "id": "%s:%s" % (MOD, NAME)},
    })
    dump(os.path.join(DATA, "advancement", "recipes", "misc", "crafting", NAME + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": "powergrid:conductive_casing"}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipe": "%s:crafting/%s" % (MOD, NAME)}, "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": ["%s:crafting/%s" % (MOD, NAME)]},
    })


def main():
    textures()
    model()
    data()
    print("ct cabinet assets written")


if __name__ == "__main__":
    main()
