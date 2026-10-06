"""Assets for the HMI Panel: a flat screen two pixels deep on the wall with a Cat6 socket flush in its
right corner. Built facing north (screen on the north face) and turned by the blockstate.
Run: python tools/gen_hmi_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

HMI = "hmi_panel"


def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    bezel = (0x2a, 0x2c, 0x30)
    glass = (0x0c, 0x10, 0x14)
    screen = canvas(16, 16, bezel + (255,))
    fill(screen, 1, 1, 15, 15, glass)
    fill(screen, 1, 1, 15, 2, shade(glass, 1.6))
    fill(screen, 0, 0, 16, 1, shade(bezel, 1.25))
    write_png(os.path.join(tex, HMI + "_screen.png"), screen)
    back = canvas(16, 16, bezel + (255,))
    fill(back, 0, 0, 16, 1, shade(bezel, 1.2))
    fill(back, 0, 15, 16, 16, shade(bezel, 0.7))
    fill(back, 6, 6, 10, 10, shade(bezel, 0.8))
    write_png(os.path.join(tex, HMI + "_back.png"), back)
    # The bottom edge: the slab's down face is 16 by 2 texels (x by z), with the socket at x 13..15.
    bottom = canvas(16, 16, bezel + (255,))
    fill(bottom, 13, 14, 15, 16, (0x10, 0x10, 0x12))
    fill(bottom, 13, 14, 14, 15, (0xb0, 0xb4, 0xba))
    write_png(os.path.join(tex, HMI + "_bottom.png"), bottom)


def model():
    slab = element(0, 0, 14, 16, 16, 16, "#back", cull_south=True)
    slab["faces"]["north"]["texture"] = "#screen"
    slab["faces"]["down"]["texture"] = "#bottom"
    els = [slab]
    dump(os.path.join(ASSETS, "models", "block", HMI + ".json"), {
        "parent": "block/block",
        "textures": {"screen": "%s:block/%s_screen" % (MOD, HMI), "back": "%s:block/%s_back" % (MOD, HMI),
                     "bottom": "%s:block/%s_bottom" % (MOD, HMI), "particle": "%s:block/%s_back" % (MOD, HMI)},
        "elements": els,
    })
    dump(os.path.join(ASSETS, "models", "item", HMI + ".json"), {"parent": "%s:block/%s" % (MOD, HMI)})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/%s" % (MOD, HMI)}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", HMI + ".json"), {"variants": variants})


def data():
    loot_table(HMI)
    recipe(HMI, ["GGG", "GNG", "III"], {"G": {"tag": "c:glass_panes"}, "I": {"tag": "c:plates/iron"},
                                        "N": {"item": "%s:network_jack" % MOD}}, 1, {"items": "%s:network_jack" % MOD})


def main():
    textures()
    model()
    data()
    print("hmi assets written")


if __name__ == "__main__":
    main()
