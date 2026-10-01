"""Assets and data for the conductor rail, its feed box, the collector and the shoe.
Run from anywhere: python tools/gen_rail_assets.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, fill, loot_table, recipe, shade, write_png
from gen_cable_chain_assets import element, horizontal, rotation4_models

TEX = os.path.join(ASSETS, "textures")
BAR_X = ((1, 3), (5, 7), (9, 11), (13, 15))   # the four bars, y 2..4, running along z in the base model


def textures():
    orange, steel, dark = (232, 112, 24), (120, 124, 130), (58, 60, 66)
    bar = canvas(16, 16, orange + (255,))
    fill(bar, 0, 0, 16, 2, shade(orange, 1.3))
    fill(bar, 0, 14, 16, 16, shade(orange, 0.6))
    for x in range(0, 16, 8):
        fill(bar, x, 6, x + 1, 10, (240, 240, 240))    # insulator stripe
    write_png(os.path.join(TEX, "block", "rail_bar.png"), bar)
    bracket = canvas(16, 16, steel + (255,))
    fill(bracket, 0, 0, 16, 1, shade(steel, 1.3))
    fill(bracket, 0, 15, 16, 16, shade(steel, 0.6))
    for x in (3, 12):
        fill(bracket, x, 7, x + 1, 9, shade(steel, 0.5))
    write_png(os.path.join(TEX, "block", "rail_bracket.png"), bracket)
    box = canvas(16, 16, dark + (255,))
    fill(box, 0, 0, 16, 1, shade(dark, 1.5))
    fill(box, 0, 15, 16, 16, shade(dark, 0.6))
    fill(box, 3, 3, 13, 5, orange)                      # warning band
    write_png(os.path.join(TEX, "block", "rail_box.png"), box)
    copper = canvas(16, 16, (200, 120, 60, 255))        # the shoe pads on the collector arm
    fill(copper, 0, 0, 16, 2, (230, 160, 90))
    fill(copper, 0, 14, 16, 16, (140, 80, 40))
    write_png(os.path.join(TEX, "block", "rail_shoe.png"), copper)
    shoe = canvas(16, 16)
    fill(shoe, 3, 10, 13, 13, (200, 120, 60))           # copper shoe
    fill(shoe, 3, 10, 13, 11, (230, 160, 90))
    fill(shoe, 7, 4, 9, 10, steel)                      # spring arm
    fill(shoe, 6, 2, 10, 4, dark)
    write_png(os.path.join(TEX, "item", "rail_shoe.png"), shoe)


def bars(z1, z2):
    return [element(x1, 2, z1, x2, 4, z2, "#bar") for x1, x2 in BAR_X]


def models():
    common = {"bar": "%s:block/rail_bar" % MOD, "bracket": "%s:block/rail_bracket" % MOD, "box": "%s:block/rail_box" % MOD,
              "stud": "%s:block/panel_terminal" % MOD, "jack": "%s:block/jack_pin" % MOD, "shoe": "%s:block/rail_shoe" % MOD}
    rail = [element(6, 0, 0, 10, 2, 16, "#bracket"), element(0, 0, 7, 16, 2, 9, "#bracket")] + bars(0, 16)
    rotation4_models("conductor_rail", rail, dict(common, particle=common["bracket"]))
    feed = [element(2, 0, 2, 14, 6, 14, "#box"), element(14, 0, 3, 16, 2, 7, "#stud"), element(14, 0, 9, 16, 2, 11, "#jack")]
    feed += bars(0, 2) + bars(14, 16)
    for x1, x2 in ((3, 5), (6, 8), (9, 11), (12, 14)):
        feed.append(element(x1, 6, 3, x2, 8, 5, "#stud"))
    rotation4_models("rail_feed", feed, dict(common, particle=common["box"]))
    # The collector's arm reaches 1, 2 or 3 blocks out (blockstate "reach"); the shoes sit on the
    # bars of the rail block it found, at the bars' height for a rail mounted on the far face.
    textures = dict(common, particle=common["box"])
    state = json.load(open(os.path.join(ASSETS, "blockstates", "vfd.json")))
    variants = {}
    for reach in (1, 2, 3):
        far = -16 * reach            # the near face of the block `reach` blocks out
        collector = [element(2, 0, 2, 14, 6, 14, "#box"), element(14, 0, 3, 16, 2, 7, "#stud"), element(14, 0, 9, 16, 2, 11, "#jack"),
                     element(6, 2, far + 8, 10, 4, 2, "#bracket")]          # the spring arm
        for x1, x2 in BAR_X:
            collector.append(element(x1, 1.5, far + 6, x2, 4.5, far + 8, "#shoe"))   # a copper shoe on each bar
        for x1, x2 in ((3, 5), (6, 8), (9, 11), (12, 14)):
            collector.append(element(x1, 6, 9, x2, 8, 11, "#stud"))
        name = "rail_collector_r%d" % reach
        dump(os.path.join(ASSETS, "models", "block", name + ".json"), {"parent": "block/block", "textures": textures, "elements": collector})
        dump(os.path.join(ASSETS, "models", "block", name + "_h.json"), {"parent": "block/block", "textures": textures, "elements": horizontal(collector)})
        for key, variant in state["variants"].items():
            model = "%s:block/%s" % (MOD, name + ("_h" if variant["model"].endswith("block_h") else ""))
            variants[key + ",reach=%d" % reach] = dict(variant, model=model)
    dump(os.path.join(ASSETS, "blockstates", "rail_collector.json"), {"variants": variants})
    dump(os.path.join(ASSETS, "models", "item", "rail_collector.json"), {"parent": "%s:block/rail_collector_r1" % MOD})
    dump(os.path.join(ASSETS, "models", "item", "rail_shoe.json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/rail_shoe" % MOD}})


def data():
    iron, copper, pins, casing = {"tag": "c:plates/iron"}, {"tag": "c:plates/copper"}, {"item": "powergrid:pins"}, {"item": "powergrid:conductive_casing"}
    for name in ("conductor_rail", "rail_feed", "rail_collector"):
        loot_table(name)
    recipe("conductor_rail", ["CCC", "III"], {"C": copper, "I": iron}, 6, {"items": "powergrid:copper_coil"})
    recipe("rail_feed", ["CCC", "PKP", "III"], {"C": copper, "P": pins, "K": casing, "I": iron}, 1, {"items": "powergrid:conductive_casing"})
    recipe("rail_shoe", ["C", "I"], {"C": copper, "I": {"tag": "c:nuggets/iron"}}, 4, {"items": "powergrid:copper_coil"})
    recipe("rail_collector", ["SSS", "PKP", "III"], {"S": {"item": "%s:rail_shoe" % MOD}, "P": pins, "K": casing, "I": iron}, 1,
           {"items": "%s:rail_shoe" % MOD})
    dump(os.path.join(DATA, "powergrid", "wire_types", "rail_shoe.json"), {
        "colorable": False, "cord": False, "insulated": True,
        "horizontalCoefficient": 1.0, "verticalCoefficient": 1.0,
        "itemsPerMeter": 0.1, "maximumLength": 512.0, "maximumCurrent": 400.0,
        "resistancePerItem": 0.0002, "thermalMass": 20.0, "wireThickness": 0.1,
        "texture": "%s:textures/special/conduit.png" % MOD,
    })


def main():
    textures()
    models()
    data()
    print("rail assets written")


if __name__ == "__main__":
    main()
