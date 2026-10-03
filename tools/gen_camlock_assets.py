"""Assets and data for the two cam-lock connector boxes. Run from anywhere: python tools/gen_camlock_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, fill, loot_table, recipe, shade, write_png
from gen_cable_chain_assets import element, rotation4_models

TEX = os.path.join(ASSETS, "textures", "block")
BOXES = {"cam_lock_box_100": (0x2a, 0x2c, 0x30), "cam_lock_box_400": (0x3a, 0x3c, 0x44)}
POLES = [(0xd0, 0x30, 0x30), (0xe0, 0xa0, 0x20), (0x30, 0x60, 0xd0), (0xe8, 0xe8, 0xe8), (0x30, 0xb0, 0x40)]


def textures():
    for name, rgb in BOXES.items():
        side = canvas(16, 16, rgb + (255,))
        fill(side, 0, 0, 16, 1, shade(rgb, 1.5))
        fill(side, 0, 15, 16, 16, shade(rgb, 0.6))
        fill(side, 1, 1, 2, 15, shade(rgb, 1.2))
        fill(side, 14, 1, 15, 15, shade(rgb, 0.8))
        if name.endswith("400"):
            fill(side, 2, 6, 14, 8, (0xd0, 0xa0, 0x20))   # a yellow band on the big one
        write_png(os.path.join(TEX, name + ".png"), side)
    # One receptacle texture per pole colour: a ring around a dark socket.
    for k, rgb in enumerate(POLES):
        face = canvas(16, 16, rgb + (255,))
        fill(face, 4, 4, 12, 12, (0x18, 0x18, 0x1c))
        fill(face, 6, 6, 10, 10, shade(rgb, 0.5))
        write_png(os.path.join(TEX, "cam_lock_pole_%d.png" % k), face)


def models():
    for name in BOXES:
        textures_ = {"box": "%s:block/%s" % (MOD, name), "hub": "%s:block/panel_terminal" % MOD, "particle": "%s:block/%s" % (MOD, name)}
        for k in range(5):
            textures_["pole%d" % k] = "%s:block/cam_lock_pole_%d" % (MOD, k)
        elements = [element(1, 0, 1, 15, 8, 15, "#box"), element(0, 1, 5, 1, 4, 8, "#hub"), element(15, 1, 5, 16, 4, 8, "#hub")]
        for k, x in enumerate((1.5, 4, 6.5, 9, 11.5)):
            elements.append(element(x, 8, 5, x + 2, 10, 7, "#pole%d" % k))
        rotation4_models(name, elements, textures_)


def data():
    iron, copper = {"tag": "c:plates/iron"}, {"tag": "c:ingots/copper"}
    for name in BOXES:
        loot_table(name)
    recipe("cam_lock_box_100", ["ICI", "CCC", "ICI"], {"I": iron, "C": copper}, 1, {"items": "powergrid:copper_coil"})
    recipe("cam_lock_box_400", ["III", "KXK", "III"], {"I": iron, "K": {"tag": "c:storage_blocks/copper"}, "X": {"item": "%s:cam_lock_box_100" % MOD}}, 1,
           {"items": "%s:cam_lock_box_100" % MOD})


def main():
    textures()
    models()
    data()
    print("cam-lock assets written")


if __name__ == "__main__":
    main()
