#!/usr/bin/env python3
"""Generates the conduit resources: the conduit box block, conduit and conductor item textures and
models, loot, recipes and Power Grid wire types.

Run from the repository root:  python tools/gen_conduit_assets.py

Geometry mirrors ConduitBoxGeometry.java; change both. North frame: the box sits against the wall
at z = 16 with its cover facing north; a viewer at the front has +x on their left.
"""
import math
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, shade, write_png

# id suffix, conductors, tube thickness (blocks)
SIZES = [
    ("half", 4, 0.09),
    ("three_quarter", 8, 0.125),
    ("one", 12, 0.16),
    ("one_quarter", 12, 0.19),
    ("one_half", 12, 0.22),
    ("two", 12, 0.26),
    ("two_half", 12, 0.30),
    ("three", 12, 0.34),
    ("four", 12, 0.40),
]
COLORS = [0x1a1a1a, 0xc62828, 0x1e5bc6, 0xf0f0f0, 0x2e8b3a, 0xf07f1a,
          0x6b3f1f, 0xe8c800, 0x8a8a8a, 0x7b3fa0, 0xf08fb0, 0xc9a97a]

BODY = (4, 4, 13, 12, 12, 16)
HUB_ALONG = [5.5, 8, 10.5]
HUBS = []
for _a in HUB_ALONG:
    _x = 16 - _a
    HUBS += [(_x - 1, 12, 13.5, _x + 1, 13, 15.5), (3, _x - 1, 13.5, 4, _x + 1, 15.5),
             (_x - 1, 3, 13.5, _x + 1, 4, 15.5), (12, _x - 1, 13.5, 13, _x + 1, 15.5)]
COLUMNS_U = [5, 7, 9, 11]
ROWS_Y = [10, 8, 6]
NUB, NUB_Z1, NUB_Z2 = 0.6, 12.4, 13
DIRECTIONAL = {
    "facing=north": {}, "facing=south": {"y": 180}, "facing=east": {"y": 90},
    "facing=west": {"y": 270}, "facing=up": {"x": 270}, "facing=down": {"x": 90},
}


def rgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def textures():
    block = os.path.join(ASSETS, "textures", "block")
    box = (0x86, 0x8a, 0x8e)
    body = canvas(16, 16, box + (255,))
    fill(body, 0, 0, 16, 1, shade(box, 1.15))
    fill(body, 0, 15, 16, 16, shade(box, 0.6))
    fill(body, 0, 0, 1, 16, shade(box, 0.85))
    fill(body, 15, 0, 16, 16, shade(box, 0.6))
    fill(body, 5, 5, 6, 6, shade(box, 0.5))
    fill(body, 10, 10, 11, 11, shade(box, 0.5))
    write_png(os.path.join(block, "conduit_box.png"), body)

    # Tube texture used by Power Grid's block wire renderer for the runs: galvanized steel.
    steel = (0x8c, 0x90, 0x94)
    tube = canvas(16, 16, steel + (255,))
    for y in range(0, 16, 4):
        fill(tube, 0, y, 16, y + 1, shade(steel, 1.1))
        fill(tube, 0, y + 2, 16, y + 3, shade(steel, 0.88))
    write_png(os.path.join(ASSETS, "textures", "special", "conduit.png"), tube)

    item = os.path.join(ASSETS, "textures", "item")
    for i, (sid, _conductors, _thickness) in enumerate(SIZES):
        # Conduit item: a straight stick of tube, thicker for bigger sizes, with a coupling.
        img = canvas(16, 16)
        t = min(2 + i, 9)
        y0 = 8 - t // 2
        for x in range(1, 15):
            fill(img, x, y0, x + 1, y0 + t, steel)
            fill(img, x, y0, x + 1, y0 + 1, shade(steel, 1.25))
            fill(img, x, y0 + t - 1, x + 1, y0 + t, shade(steel, 0.7))
        fill(img, 7, y0 - 1, 9, y0 + t + 1, shade(steel, 0.8))
        write_png(os.path.join(item, "conduit_%s.png" % sid), img)


def box_model():
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD,
           "open": "%s:block/conduit_box_open" % MOD, "particle": "%s:block/conduit_box" % MOD}
    for cover in ("open", "blank", "node"):
        if cover == "open":
            # No front: the body is set back and its open face shows the interior.
            x1, y1, z1, x2, y2, z2 = BODY
            elements = [element(x1, y1, z1 + 0.5, x2, y2, z2, "#box", cull_south=True)]
            elements[0]["faces"]["north"]["texture"] = "#open"
        else:
            elements = [element(*BODY, "#box", cull_south=True)]
        for hub in HUBS:
            elements.append(element(*hub, "#terminal"))
        if cover == "node":
            for k in range(12):
                x = 16 - COLUMNS_U[k % 4]
                y = ROWS_Y[k // 4]
                elements.append(element(x - NUB, y - NUB, NUB_Z1, x + NUB, y + NUB, NUB_Z2, "#terminal"))
        dump(os.path.join(ASSETS, "models", "block", "conduit_box_%s.json" % cover), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "conduit_box.json"), {"parent": "%s:block/conduit_box_open" % MOD})
    variants = {}
    for key, rot in DIRECTIONAL.items():
        for cover in ("open", "blank", "node"):
            v = {"model": "%s:block/conduit_box_%s" % (MOD, cover)}
            v.update(rot)
            variants["%s,cover=%s" % (key, cover)] = v
    dump(os.path.join(ASSETS, "blockstates", "conduit_box.json"), {"variants": variants})
    # Cover plate items.
    item = os.path.join(ASSETS, "textures", "item")
    plate = (0x86, 0x8a, 0x8e)
    for name, nubs in (("conduit_cover_blank", False), ("conduit_cover_node", True)):
        img = canvas(16, 16)
        fill(img, 2, 2, 14, 14, plate)
        fill(img, 2, 2, 14, 3, shade(plate, 1.2))
        fill(img, 2, 13, 14, 14, shade(plate, 0.6))
        fill(img, 3, 3, 4, 4, shade(plate, 0.5))
        fill(img, 12, 12, 13, 13, shade(plate, 0.5))
        if nubs:
            for k in range(12):
                x = 4 + (k % 4) * 2 + 1
                y = 5 + (k // 4) * 2 + 1
                fill(img, x, y, x + 1, y + 1, (0xd0, 0xd4, 0xd8))
        write_png(os.path.join(item, name + ".png"), img)
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, name)}})
    open_tex = canvas(16, 16, shade(plate, 0.45) + (255,))
    fill(open_tex, 0, 0, 16, 1, shade(plate, 0.7))
    fill(open_tex, 0, 15, 16, 16, shade(plate, 0.3))
    write_png(os.path.join(ASSETS, "textures", "block", "conduit_box_open.png"), open_tex)


# Floor frame (FACING = down): body on the floor, socket face up, knockout on the north edge.
SOCKET_BODY = (5, 0, 5, 11, 3, 11)
SOCKET_HUB = (7, 0.5, 4, 9, 2.5, 5)
SOCKET_FACE = (6.5, 3, 6.5, 9.5, 4, 9.5)
# Same rotation table Power Grid's Rotation4 blocks use (copied from the line ammeter): block_v for
# floor and ceiling, block_h = (16 - y, x, z) of block_v for walls.
ROTATION4 = {k: ("v" if v["model"] == "v" else "h", {a: v[a] for a in ("x", "y") if v[a]})
             for k, v in __import__("json").load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "rotation4.json"))).items()}


def to_h(box):
    x1, y1, z1, x2, y2, z2 = box
    return (16 - y2, x1, z1, 16 - y1, x2, z2)


def socket_model():
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD, "particle": "%s:block/conduit_box" % MOD}
    boxes = [(SOCKET_BODY, "#box"), (SOCKET_HUB, "#terminal"), (SOCKET_FACE, "#terminal")]
    for suffix, transform in (("v", lambda b: b), ("h", to_h)):
        elements = [element(*transform(box), texture) for box, texture in boxes]
        dump(os.path.join(ASSETS, "models", "block", "conduit_socket", "block_%s.json" % suffix),
             {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "conduit_socket.json"), {"parent": "%s:block/conduit_socket/block_v" % MOD})
    variants = {}
    for key, (suffix, rot) in ROTATION4.items():
        v = {"model": "%s:block/conduit_socket/block_%s" % (MOD, suffix)}
        v.update(rot)
        variants[key] = v
    dump(os.path.join(ASSETS, "blockstates", "conduit_socket.json"), {"variants": variants})


SWITCH_PLATE = (6, 3, 6, 10, 3.5, 10)
SWITCH_TOGGLE = {False: (7, 3.5, 8.5, 9, 5.5, 10), True: (7, 3.5, 6, 9, 5.5, 7.5)}


def switch_model():
    """The conduit switch: the socket's body and knockout with a toggle plate on top; the toggle leans back when off, forward when on."""
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD, "particle": "%s:block/conduit_box" % MOD}
    for on in (False, True):
        boxes = [(SOCKET_BODY, "#box"), (SOCKET_HUB, "#terminal"), (SWITCH_PLATE, "#box"), (SWITCH_TOGGLE[on], "#terminal")]
        for suffix, transform in (("v", lambda b: b), ("h", to_h)):
            elements = [element(*transform(box), texture) for box, texture in boxes]
            dump(os.path.join(ASSETS, "models", "block", "conduit_switch", "block_%s_%s.json" % (suffix, "on" if on else "off")),
                 {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "conduit_switch.json"), {"parent": "%s:block/conduit_switch/block_v_off" % MOD})
    variants = {}
    for key, (suffix, rot) in ROTATION4.items():
        for on in (False, True):
            v = {"model": "%s:block/conduit_switch/block_%s_%s" % (MOD, suffix, "on" if on else "off")}
            v.update(rot)
            variants[key + ",on=" + ("true" if on else "false")] = v
    dump(os.path.join(ASSETS, "blockstates", "conduit_switch.json"), {"variants": variants})


def item_models():
    for sid, *_ in SIZES:
        dump(os.path.join(ASSETS, "models", "item", "conduit_%s.json" % sid), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/conduit_%s" % (MOD, sid)},
        })


def loot_table(name):
    dump(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (MOD, name)}],
            "rolls": 1.0,
        }],
        "random_sequence": "%s:blocks/%s" % (MOD, name),
    })


def advancement(name, unlock_item):
    dump(os.path.join(DATA, "advancement", "recipes", "misc", "crafting", name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [unlock_item]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipe": "%s:crafting/%s" % (MOD, name)}, "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": ["%s:crafting/%s" % (MOD, name)]},
    })


def shaped(name, pattern, key, count, unlock):
    dump(os.path.join(DATA, "recipe", "crafting", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "key": key, "pattern": pattern,
        "result": {"count": count, "id": "%s:%s" % (MOD, name)},
    })
    advancement(name, unlock)


def recipes():
    nugget = {"tag": "c:nuggets/iron"}
    ingot = {"tag": "c:ingots/iron"}
    wire = {"item": "powergrid:wire"}
    # A metre of conduit per item; the craft yields a coil of eight.
    shaped("conduit_half", ["NNN", "WWW"], {"N": nugget, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_three_quarter", ["III", "WWW"], {"I": ingot, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_one", ["III", "WWW", "III"], {"I": ingot, "W": wire}, 8, {"items": "powergrid:wire"})
    block = {"tag": "c:storage_blocks/iron"}
    shaped("conduit_one_quarter", ["III", "W W", "III"], {"I": ingot, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_one_half", ["III", "WIW", "III"], {"I": ingot, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_two", ["IWI", "W W", "IWI"], {"I": ingot, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_two_half", ["IWI", "WIW", "IWI"], {"I": ingot, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_three", ["BWB", "W W", "BWB"], {"B": block, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_four", ["BWB", "WBW", "BWB"], {"B": block, "W": wire}, 8, {"items": "powergrid:wire"})
    shaped("conduit_box", ["NNN", "N N", "NNN"], {"N": nugget}, 2, {"items": "powergrid:wire"})
    shaped("conduit_socket", ["NCN", " N "], {"N": nugget, "C": {"tag": "c:nuggets/copper"}}, 2, {"items": "powergrid:wire"})
    shaped("conduit_switch", ["NLN", " N "], {"N": nugget, "L": {"item": "minecraft:lever"}}, 2, {"items": "powergrid:wire"})


def wire_types():
    for sid, _conductors, thickness in SIZES:
        dump(os.path.join(DATA, "powergrid", "wire_types", "conduit_%s.json" % sid), {
            "colorable": True, "cord": False, "horizontalCoefficient": 1.0, "insulated": True,
            "itemsPerMeter": 1.0, "maximumCurrent": 1.0, "maximumLength": 64.0,
            "resistancePerItem": 0.001, "texture": "%s:textures/special/conduit.png" % MOD,
            "thermalMass": 1.0, "verticalCoefficient": 1.0, "wireThickness": thickness,
        })


def main():
    textures()
    box_model()
    socket_model()
    switch_model()
    item_models()
    loot_table("conduit_box")
    loot_table("conduit_socket")
    loot_table("conduit_switch")
    recipes()
    wire_types()
    print("conduit assets written")


if __name__ == "__main__":
    main()
