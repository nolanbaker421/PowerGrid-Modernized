"""Assets for the Control Station: a small box on the wall with four device cells on its face and
two jack pins under it. Built facing north (face on the north side) and turned by the blockstate.
Run: python tools/gen_station_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

STATION = "control_station"


def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    body = (0x7c, 0x80, 0x86)
    edge = (0x4a, 0x4d, 0x52)
    # The face texture covers the whole block; the box uses x 3..13, y 2..14 of it.
    front = canvas(16, 16, body + (255,))
    fill(front, 3, 2, 13, 14, shade(body, 0.85))
    fill(front, 3, 2, 13, 3, edge)
    fill(front, 3, 13, 13, 14, edge)
    fill(front, 3, 2, 4, 14, edge)
    fill(front, 12, 2, 13, 14, edge)
    for cx in (4, 8):
        for cy in (4, 8):
            fill(front, cx, cy, cx + 4, cy + 4, shade(body, 0.78))
            fill(front, cx, cy, cx + 4, cy + 1, shade(body, 0.95))
    write_png(os.path.join(tex, STATION + "_front.png"), front)
    side = canvas(16, 16, body + (255,))
    fill(side, 0, 0, 16, 1, shade(body, 1.12))
    fill(side, 0, 15, 16, 16, edge)
    write_png(os.path.join(tex, STATION + "_side.png"), side)


def model():
    box = element(3, 2, 13, 13, 14, 16, "#side", cull_south=True)
    box["faces"]["north"]["texture"] = "#front"
    els = [box, element(9, 1, 13.5, 11, 2, 15.5, "#jack"), element(5, 1, 13.5, 7, 2, 15.5, "#jack")]
    dump(os.path.join(ASSETS, "models", "block", STATION + ".json"), {
        "parent": "block/block",
        "textures": {"front": "%s:block/%s_front" % (MOD, STATION), "side": "%s:block/%s_side" % (MOD, STATION),
                     "jack": "%s:block/jack_pin" % MOD, "particle": "%s:block/%s_side" % (MOD, STATION)},
        "elements": els,
    })
    dump(os.path.join(ASSETS, "models", "item", STATION + ".json"), {"parent": "%s:block/%s" % (MOD, STATION)})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/%s" % (MOD, STATION)}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", STATION + ".json"), {"variants": variants})


def data():
    loot_table(STATION)
    recipe(STATION, ["III", "IJI", "NNN"], {"I": {"tag": "c:plates/iron"}, "J": {"item": "%s:network_jack" % MOD},
                                             "N": {"tag": "c:nuggets/iron"}}, 1, {"items": "%s:network_jack" % MOD})


def main():
    textures()
    model()
    data()
    print("control station assets written")


if __name__ == "__main__":
    main()
