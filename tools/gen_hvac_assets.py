#!/usr/bin/env python3
"""Generates the split-system air conditioning resources: the condenser and air handler blocks
(models, blockstates, textures, loot, recipes) and the two refrigerant line items (icons, models,
recipes, Power Grid wire types).

Run from the repository root:  python tools/gen_hvac_assets.py

Geometry mirrors CondenserBlock.java and AirHandlerBlock.java; change both. North frame, pixels,
the front facing north, +x on the viewer's left: line ports on the +x side, knockouts on -x.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

TEX = os.path.join(ASSETS, "textures", "block")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
COPPER = "minecraft:block/copper_block"
INSULATION = "minecraft:block/black_concrete"

CONDENSER_BODY = (1, 0, 1, 15, 13, 15)
CONDENSER_HUBS = [((0, 3, 6, 1, 5, 8), "#knockout34"), ((0, 8, 6, 1, 10, 8), "#knockout12"),
                  ((15, 3, 5, 16, 5, 7), "#copper"), ((15, 3, 9, 16, 6, 12), "#insulation")]
HANDLER_BODY = (2, 0, 2, 14, 16, 14)
HANDLER_HUBS = [((1, 3, 6, 2, 5, 8), "#knockout34"), ((1, 8, 6, 2, 10, 8), "#knockout12"), ((1, 12, 6, 2, 14, 8), "#knockout12"),
                ((14, 3, 5, 15, 5, 7), "#copper"), ((14, 3, 9, 15, 6, 12), "#insulation")]


def textures():
    grey = (0x9a, 0x9e, 0xa4)
    dark = (0x55, 0x58, 0x5c)
    # Condenser sides: louvres, a dark line every other pixel row over the coil.
    side = canvas(16, 16, grey + (255,))
    for y in range(2, 13, 2):
        fill(side, 2, y, 14, y + 1, shade(grey, 0.7))
    fill(side, 0, 0, 16, 1, shade(grey, 1.1))
    fill(side, 0, 15, 16, 16, dark)
    fill(side, 0, 0, 1, 16, dark)
    fill(side, 15, 0, 16, 16, dark)
    write_png(os.path.join(TEX, "condenser_side.png"), side)
    # Condenser top: the grille, a dark well with light wire across it, the fan turning underneath.
    top = canvas(16, 16, (0x30, 0x32, 0x36, 255))
    for i in range(1, 16, 2):
        fill(top, i, 1, i + 1, 15, shade(grey, 0.9))
    fill(top, 0, 0, 16, 1, grey)
    fill(top, 0, 15, 16, 16, grey)
    fill(top, 0, 0, 1, 16, grey)
    fill(top, 15, 0, 16, 16, grey)
    write_png(os.path.join(TEX, "condenser_top.png"), top)
    # Air handler sides: plain cabinet with a seam.
    cab = (0xb0, 0xb3, 0xb8)
    hs = canvas(16, 16, cab + (255,))
    fill(hs, 0, 0, 16, 1, shade(cab, 1.08))
    fill(hs, 0, 15, 16, 16, dark)
    fill(hs, 0, 0, 1, 16, shade(cab, 0.8))
    fill(hs, 15, 0, 16, 16, shade(cab, 0.8))
    fill(hs, 1, 9, 15, 10, shade(cab, 0.75))
    write_png(os.path.join(TEX, "air_handler_side.png"), hs)
    # Air handler front: a filter door low down with a handle, and the blower's louvre high up.
    hf = canvas(16, 16, cab + (255,))
    fill(hf, 0, 0, 16, 1, shade(cab, 1.08))
    fill(hf, 0, 15, 16, 16, dark)
    fill(hf, 0, 0, 1, 16, shade(cab, 0.8))
    fill(hf, 15, 0, 16, 16, shade(cab, 0.8))
    fill(hf, 3, 2, 13, 6, shade(cab, 0.6))
    for y in range(2, 6):
        fill(hf, 3, y, 13, y + 1, shade(cab, 0.55 if y % 2 else 0.75))
    fill(hf, 3, 9, 13, 14, shade(cab, 0.9))
    fill(hf, 3, 9, 13, 10, shade(cab, 0.7))
    fill(hf, 11, 11, 12, 13, dark)
    write_png(os.path.join(TEX, "air_handler_front.png"), hf)
    # Item icons: a coil of bare copper tube, and a coil of black insulated tube.
    for name, rgb in (("line_set_liquid", (0xb8, 0x73, 0x33)), ("line_set_suction", (0x26, 0x28, 0x2c))):
        img = canvas(16, 16)
        for (x1, y1, x2, y2) in ((3, 2, 13, 4), (3, 12, 13, 14), (3, 4, 5, 12), (11, 4, 13, 12)):
            fill(img, x1, y1, x2, y2, rgb)
        fill(img, 3, 2, 13, 3, shade(rgb, 1.3))
        fill(img, 3, 13, 13, 14, shade(rgb, 0.7))
        fill(img, 5, 7, 11, 9, rgb)
        fill(img, 5, 7, 11, 8, shade(rgb, 1.3))
        write_png(os.path.join(ITEM_TEX, name + ".png"), img)
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, name)}})


def block(name, body, hubs, front_tex, side_tex, top_tex=None):
    tex = {
        "side": "%s:block/%s" % (MOD, side_tex), "front": "%s:block/%s" % (MOD, front_tex),
        "knockout34": "%s:block/knockout_three_quarter" % MOD, "knockout12": "%s:block/knockout_half" % MOD,
        "copper": COPPER, "insulation": INSULATION,
        "particle": "%s:block/%s" % (MOD, side_tex),
    }
    if top_tex:
        tex["top"] = "%s:block/%s" % (MOD, top_tex)
    elements = [element(*body, "#side")]
    elements[0]["faces"]["north"]["texture"] = "#front"
    if top_tex:
        elements[0]["faces"]["up"]["texture"] = "#top"
    for box, texture in hubs:
        elements.append(element(*box, texture))
    if name == "condenser":
        # The rim round the fan well, above the grille, so the blades turn inside something.
        for rim in ((1, 13, 1, 15, 14.5, 2), (1, 13, 14, 15, 14.5, 15), (1, 13, 2, 2, 14.5, 14), (14, 13, 2, 15, 14.5, 14)):
            elements.append(element(*rim, "#side"))
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s" % (MOD, name)})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/%s" % (MOD, name)}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
    loot_table(name)


def wire_type(name, thickness):
    dump(os.path.join(DATA, "powergrid", "wire_types", name + ".json"), {
        "colorable": True, "cord": False, "horizontalCoefficient": 1.0, "insulated": True,
        "itemsPerMeter": 1.0, "maximumCurrent": 1.0, "maximumLength": 64.0,
        "resistancePerItem": 0.001, "texture": "%s:textures/special/conduit.png" % MOD,
        "thermalMass": 1.0, "verticalCoefficient": 1.0, "wireThickness": thickness,
    })


def data():
    plate, copper, nugget = {"tag": "c:plates/iron"}, {"tag": "c:ingots/copper"}, {"tag": "c:nuggets/copper"}
    recipe("condenser", ["IPI", "CFC", "III"], {"I": plate, "P": {"item": "create:propeller"}, "C": copper, "F": {"item": "powergrid:wire"}}, 1, {"items": "powergrid:wire"})
    recipe("air_handler", ["IPI", "CBC", "III"], {"I": plate, "P": {"item": "create:propeller"}, "C": copper, "B": {"item": "minecraft:iron_bars"}}, 1, {"items": "%s:condenser" % MOD})
    recipe("line_set_liquid", ["NNN"], {"N": nugget}, 4, {"items": "%s:condenser" % MOD})
    recipe("line_set_suction", ["NNN", "WWW"], {"N": nugget, "W": {"item": "minecraft:black_wool"}}, 4, {"items": "%s:condenser" % MOD})
    wire_type("line_set_liquid", 0.09)
    wire_type("line_set_suction", 0.125)


def main():
    textures()
    block("condenser", CONDENSER_BODY, CONDENSER_HUBS, "condenser_side", "condenser_side", "condenser_top")
    block("air_handler", HANDLER_BODY, HANDLER_HUBS, "air_handler_front", "air_handler_side")
    data()
    print("hvac assets written")


if __name__ == "__main__":
    main()
