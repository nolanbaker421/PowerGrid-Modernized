#!/usr/bin/env python3
"""Generates the pole-line resources: the crossarm, the fused cutouts in three classes and one
to three poles (models, blockstates, textures, loot, recipes), the fuse links and the hot stick.

Run from the repository root:  python tools/gen_pole_assets.py

Geometry mirrors CutoutBlock.java and CrossarmBlock.java; change both. North frame, pixels: a
cutout's bracket straps are against the pole at z = 16, the tubes to the front; the crossarm's
bracket is against the pole, the arm running across x.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

TEX = os.path.join(ASSETS, "textures", "block")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
CLASSES = [("1kv", 10, (0xb8, 0xb4, 0xac)), ("15kv", 12, (0x8a, 0x6a, 0x4a)), ("38kv", 14, (0x6e, 0x7e, 0x8e))]
LINKS = [6, 10, 15, 25, 40, 65, 100, 140, 200]
FACINGS = (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}))


def centres(poles):
    return {1: [8], 2: [11, 5], 3: [13, 8, 3]}[poles]


def textures():
    steel = (0x4e, 0x52, 0x58)   # galvanized, weathered dark
    img = canvas(16, 16, steel + (255,))
    fill(img, 0, 0, 16, 1, shade(steel, 1.1))
    fill(img, 0, 15, 16, 16, shade(steel, 0.7))
    write_png(os.path.join(TEX, "cutout_steel.png"), img)
    copper = (0xb8, 0x73, 0x33)
    img = canvas(16, 16, copper + (255,))
    fill(img, 0, 0, 16, 2, shade(copper, 1.2))
    fill(img, 0, 14, 16, 16, shade(copper, 0.7))
    write_png(os.path.join(TEX, "cutout_lug.png"), img)
    for cid, _top, rgb in CLASSES:
        # Porcelain with sheds: a light band every four rows.
        img = canvas(16, 16, rgb + (255,))
        for y in range(1, 16, 4):
            fill(img, 0, y, 16, y + 1, shade(rgb, 1.25))
            fill(img, 0, y + 1, 16, y + 2, shade(rgb, 0.8))
        write_png(os.path.join(TEX, "cutout_porcelain_%s.png" % cid), img)
    # Item icons: a fuse link is a copper strip with as many ticks as its size has steps.
    for i, amps in enumerate(LINKS):
        img = canvas(16, 16)
        fill(img, 2, 7, 14, 9, copper)
        fill(img, 2, 7, 14, 8, shade(copper, 1.3))
        fill(img, 1, 5, 4, 11, shade(copper, 0.8))
        fill(img, 12, 5, 15, 11, shade(copper, 0.8))
        for t in range(i + 1):
            x = 2 + t * 1.5
            fill(img, int(x) + 1, 11, int(x) + 2, 13, (0x30, 0x30, 0x34))
        write_png(os.path.join(ITEM_TEX, "fuse_link_%da.png" % amps), img)
        dump(os.path.join(ASSETS, "models", "item", "fuse_link_%da.json" % amps), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/fuse_link_%da" % (MOD, amps)}})
    # The hot stick: an orange fibreglass rod, bottom left to top right, a grey hook on the end.
    img = canvas(16, 16)
    orange = (0xe0, 0x80, 0x20)
    for i in range(12):
        fill(img, 2 + i, 13 - i, 4 + i, 14 - i, orange)
    fill(img, 12, 1, 15, 2, (0x80, 0x84, 0x88))
    fill(img, 14, 1, 15, 4, (0x80, 0x84, 0x88))
    write_png(os.path.join(ITEM_TEX, "hot_stick.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "hot_stick.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/hot_stick" % MOD}})
    # The extension ladder: two orange fiberglass rails with a narrower fly section climbing past them.
    img = canvas(16, 16, (0, 0, 0, 0))
    rail, rung = (0xe8, 0x73, 0x1e), (0xc2, 0xc6, 0xcc)
    fill(img, 3, 6, 4, 16, rail)
    fill(img, 11, 6, 12, 16, rail)
    for y in (7, 10, 13):
        fill(img, 4, y, 11, y + 1, rung)
    fill(img, 5, 0, 6, 12, rail)
    fill(img, 9, 0, 10, 12, rail)
    for y in (1, 4, 7, 10):
        fill(img, 6, y, 9, y + 1, rung)
    write_png(os.path.join(ITEM_TEX, "extension_ladder.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "extension_ladder.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/extension_ladder" % MOD}})


def crossarm():
    tex = {"arm": "minecraft:block/spruce_planks", "bracket": "%s:block/cutout_steel" % MOD, "particle": "minecraft:block/spruce_planks"}
    # The arm close against the pole, held by a thin steel plate; extensions are the arm alone.
    arm = element(0, 9, 11, 16, 13, 15, "#arm")
    dump(os.path.join(ASSETS, "models", "block", "crossarm.json"), {"parent": "block/block", "textures": tex, "elements": [arm, element(5, 8, 15, 11, 14, 16, "#bracket")]})
    dump(os.path.join(ASSETS, "models", "block", "crossarm_extension.json"), {"parent": "block/block", "textures": tex, "elements": [arm]})
    dump(os.path.join(ASSETS, "models", "item", "crossarm.json"), {"parent": "%s:block/crossarm" % MOD})
    variants = {}
    for facing, rot in FACINGS:
        for bracket, model in (("true", "crossarm"), ("false", "crossarm_extension")):
            v = {"model": "%s:block/%s" % (MOD, model)}
            v.update(rot)
            variants["bracket=%s,facing=%s" % (bracket, facing)] = v
    dump(os.path.join(ASSETS, "blockstates", "crossarm.json"), {"variants": variants})
    loot_table("crossarm")


def cutouts():
    for cid, top, _rgb in CLASSES:
        name = "cutout_" + cid
        tex = {"steel": "%s:block/cutout_steel" % MOD, "lug": "%s:block/cutout_lug" % MOD,
               "porcelain": "%s:block/cutout_porcelain_%s" % (MOD, cid), "particle": "%s:block/cutout_steel" % MOD}
        variants = {}
        for poles in (1, 2, 3):
            xs = centres(poles)
            elements = []
            for x in xs:
                # One steel bracket per pole: a rail under the insulator and lug, a strap up the pole behind it.
                elements.append(element(x - 1.5, 0, 3, x + 1.5, 1, 16, "#steel"))
                elements.append(element(x - 1.5, 1, 15, x + 1.5, 6, 16, "#steel"))
                elements.append(element(x - 1, 1, 9, x + 1, top, 11, "#porcelain"))
                for y in (4, 7, 10):
                    if y < top - 1:
                        elements.append(element(x - 1.5, y, 8.5, x + 1.5, y + 0.5, 11.5, "#porcelain"))
                elements.append(element(x - 0.75, top, 8.5, x + 0.75, top + 1.5, 10.5, "#lug"))
                elements.append(element(x - 0.75, 1, 4, x + 0.75, 2.5, 6, "#lug"))
                elements.append(element(x - 1, 1, 4.5, x + 1, 2.5, 6.5, "#steel"))
                elements.append(element(x - 0.5, top - 0.5, 6, x + 0.5, top + 0.5, 9, "#lug"))   # the top contact the tube closes into
            model = "%s_%d" % (name, poles)
            dump(os.path.join(ASSETS, "models", "block", model + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
            for facing, rot in FACINGS:
                v = {"model": "%s:block/%s" % (MOD, model)}
                v.update(rot)
                variants["facing=%s,poles=%d" % (facing, poles)] = v
        dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s_1" % (MOD, name)})
        loot_table(name)


def data():
    plate, nugget, ingot = {"tag": "c:plates/iron"}, {"tag": "c:nuggets/copper"}, {"tag": "c:ingots/copper"}
    recipe("crossarm", ["PPP", " N "], {"P": {"tag": "minecraft:planks"}, "N": {"tag": "c:nuggets/iron"}}, 2, {"items": "%s:cutout_15kv" % MOD})
    recipe("extension_ladder", ["P P", "PPP", "P P"], {"P": plate}, 1, {"items": "%s:hot_stick" % MOD})
    recipe_shapeless("cutout_1kv", [plate, {"item": "minecraft:terracotta"}, nugget], 1, {"items": "%s:fuse_link_15a" % MOD})
    recipe_shapeless("cutout_15kv", [plate, {"item": "minecraft:brown_terracotta"}, nugget, nugget], 1, {"items": "%s:fuse_link_15a" % MOD})
    recipe_shapeless("cutout_38kv", [plate, {"item": "minecraft:light_blue_terracotta"}, nugget, nugget, nugget], 1, {"items": "%s:fuse_link_15a" % MOD})
    # Links: a paper and as many copper nuggets as the size has steps; the biggest takes an ingot.
    for i, amps in enumerate(LINKS):
        if amps == 200:
            recipe_shapeless("fuse_link_200a", [{"item": "minecraft:paper"}, ingot], 2, {"items": "%s:cutout_15kv" % MOD})
        else:
            recipe_shapeless("fuse_link_%da" % amps, [{"item": "minecraft:paper"}] + [nugget] * (i + 1), 2, {"items": "%s:cutout_15kv" % MOD})
    recipe("hot_stick", ["N", "S", "S"], {"N": {"tag": "c:nuggets/iron"}, "S": {"item": "minecraft:stick"}}, 1, {"items": "%s:cutout_15kv" % MOD})


def main():
    textures()
    crossarm()
    cutouts()
    data()
    print("pole assets written")


if __name__ == "__main__":
    main()
