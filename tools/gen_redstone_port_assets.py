#!/usr/bin/env python3
"""Generates the redstone port resources: the network jack plate with a lamp in one corner, lit
while the port puts out a signal; blockstate, item model, loot and recipe.

Run from the repository root:  python tools/gen_redstone_port_assets.py
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

NAME = "redstone_port"
LAMP = (9.5, 3, 9.5, 10.5, 3.6, 10.5)


def plate():
    """The jack plate exactly as the network jack draws it, the port in the middle of the top face."""
    return {
        "from": [5, 0, 5], "to": [11, 3, 11],
        "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#jack"},
            "down": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "down"},
            "north": {"uv": [0, 13, 16, 16], "texture": "#side"},
            "south": {"uv": [0, 13, 16, 16], "texture": "#side"},
            "east": {"uv": [0, 13, 16, 16], "texture": "#side"},
            "west": {"uv": [0, 13, 16, 16], "texture": "#side"},
        },
    }


def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    for suffix, rgb in (("off", (0x5a, 0x12, 0x12)), ("on", (0xff, 0x38, 0x30))):
        img = canvas(16, 16, rgb + (255,))
        fill(img, 0, 0, 16, 1, shade(rgb, 1.2))
        fill(img, 0, 15, 16, 16, shade(rgb, 0.6))
        write_png(os.path.join(tex, "%s_lamp_%s.png" % (NAME, suffix)), img)


def models():
    for lit in (False, True):
        suffix = "on" if lit else "off"
        model = {
            "parent": "minecraft:block/block",
            "textures": {"particle": "%s:block/network_jack" % MOD, "jack": "%s:block/network_jack" % MOD,
                         "side": "%s:block/vfd" % MOD, "lamp": "%s:block/%s_lamp_%s" % (MOD, NAME, suffix)},
            "elements": [plate(), element(*LAMP, "#lamp")],
        }
        dump(os.path.join(ASSETS, "models", "block", NAME, suffix + ".json"), model)
    dump(os.path.join(ASSETS, "models", "item", NAME + ".json"), {"parent": "%s:block/%s/off" % (MOD, NAME)})
    variants = {}
    for facing, rot in (("up", {}), ("down", {"x": 180}), ("north", {"x": 90}), ("east", {"x": 90, "y": 90}),
                        ("south", {"x": 90, "y": 180}), ("west", {"x": 90, "y": 270})):
        for lit in (False, True):
            v = {"model": "%s:block/%s/%s" % (MOD, NAME, "on" if lit else "off")}
            v.update(rot)
            variants["facing=%s,lit=%s" % (facing, str(lit).lower())] = v
    dump(os.path.join(ASSETS, "blockstates", NAME + ".json"), {"variants": variants})


def data():
    loot_table(NAME)
    recipe(NAME, [" R ", "NJN"], {"R": {"item": "minecraft:redstone"}, "N": {"tag": "c:nuggets/iron"}, "J": {"item": "%s:network_jack" % MOD}},
           1, {"items": "%s:network_jack" % MOD})


def main():
    textures()
    models()
    data()
    print("redstone port assets written")


if __name__ == "__main__":
    main()
