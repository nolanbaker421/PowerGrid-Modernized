"""Assets for the radio: the base station block (a box with an antenna and a jack pin on the back)
and the handheld remote item (a yellow remote with black buttons). Run: python tools/gen_radio_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

BASE = "radio_base"
REMOTE = "radio_remote"


def base_model():
    els = [
        element(4, 0, 4, 12, 6, 12, "#side"),        # the box
        element(7, 6, 7, 9, 16, 9, "#rod"),          # the antenna
        element(7, 2, 12, 9, 4, 13, "#jack"),        # jack pin on the back
    ]
    dump(os.path.join(ASSETS, "models", "block", BASE + ".json"), {
        "parent": "block/block",
        "textures": {"side": "%s:block/vfd" % MOD, "rod": "%s:block/transformer_cap" % MOD,
                     "jack": "%s:block/jack_pin" % MOD, "particle": "%s:block/vfd" % MOD},
        "elements": els,
    })
    dump(os.path.join(ASSETS, "models", "item", BASE + ".json"), {"parent": "%s:block/%s" % (MOD, BASE)})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/%s" % (MOD, BASE)}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", BASE + ".json"), {"variants": variants})


def remote_icon():
    yellow = (0xe8, 0xc0, 0x20)
    black = (0x18, 0x18, 0x1a)
    img = canvas(16, 16)
    fill(img, 5, 1, 11, 15, yellow)                      # body
    fill(img, 5, 1, 11, 2, shade(yellow, 1.2))
    fill(img, 5, 14, 11, 15, shade(yellow, 0.7))
    fill(img, 5, 1, 6, 15, shade(yellow, 0.85))
    fill(img, 10, 1, 11, 15, shade(yellow, 0.85))
    fill(img, 6, 2, 8, 4, (0xd0, 0x20, 0x20))             # the E-stop
    fill(img, 8, 2, 10, 3, black)                          # the key switch
    for y in (5, 8, 11):
        fill(img, 6, y, 8, y + 2, black)
        fill(img, 8, y, 10, y + 2, black)
    fill(img, 7, 15, 9, 16, shade(yellow, 0.5))            # the lanyard loop
    write_png(os.path.join(ASSETS, "textures", "item", REMOTE + ".png"), img)
    dump(os.path.join(ASSETS, "models", "item", REMOTE + ".json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, REMOTE)}})


def data():
    loot_table(BASE)
    recipe(BASE, [" R ", "INI", "III"], {"R": {"item": "minecraft:lightning_rod"}, "I": {"tag": "c:plates/iron"},
                                         "N": {"item": "%s:network_jack" % MOD}}, 1, {"items": "%s:network_jack" % MOD})
    recipe(REMOTE, ["YBY", "YBY", "YRY"], {"Y": {"item": "minecraft:yellow_dye"}, "B": {"item": "minecraft:stone_button"},
                                           "R": {"item": "minecraft:redstone"}}, 1, {"items": "%s:%s" % (MOD, BASE)})


def main():
    base_model()
    remote_icon()
    data()
    print("radio assets written")


if __name__ == "__main__":
    main()
