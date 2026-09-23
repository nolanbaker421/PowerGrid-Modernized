#!/usr/bin/env python3
"""Generates the transformer resources: models, blockstates, item models, textures, loot and recipes.

Run from the repository root:  python tools/gen_transformer_assets.py

Geometry mirrors TransformerGeometry.java; change both. North frame like the breaker panel: the
back (wall or pole) is the south side, the front faces north, +x is on the viewer's left.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

HUB_U = [3.5, 6.5, 9.5, 12.5]
KINDS = ["split", "3ph"]

DRY_BODY = (2, 0, 8, 14, 14, 16)
PAD_BODY = (1, 0, 3, 15, 11, 15)
CAN = (5, 1, 9, 11, 13, 16)
CAN_PRIMARIES = [(6, 13, 11, 7.5, 15.5, 12.5), (8.5, 13, 11, 10, 15.5, 12.5)]
CAN_SECONDARIES = [(5.5, 8, 7.5, 7, 9.5, 9), (7.25, 5.5, 7.5, 8.75, 7, 9), (9, 8, 7.5, 10.5, 9.5, 9)]
BANK_CX = [12.75, 7.75, 2.75]


def hubs(mount):
    out = []
    if mount == "dry":
        for u in HUB_U:
            x = 16 - u
            out.append((x - 1, 14, 11, x + 1, 15, 13))
        out += [(14, 4, 11, 15, 6, 13), (14, 8, 11, 15, 10, 13), (1, 4, 11, 2, 6, 13), (1, 8, 11, 2, 10, 13)]
    elif mount == "pad":
        for u in HUB_U:
            x = 16 - u
            out.append((x - 1, 0, 8, x + 1, 1, 10))
        out += [(15, 3, 5, 16, 5, 7), (15, 3, 11, 16, 5, 13), (0, 3, 5, 1, 5, 7), (0, 3, 11, 1, 5, 13)]
    return out


def name_of(mount, kind):
    return "transformer_%s_%s" % (mount, kind)


# ---------------------------------------------------------------- textures

def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    # Dry-type: light grey cabinet with louvred vents on the front.
    grey = (0x9a, 0x9e, 0xa4)
    edge = (0x5e, 0x62, 0x68)
    front = canvas(16, 16, grey + (255,))
    fill(front, 0, 0, 16, 1, shade(grey, 1.1))
    fill(front, 0, 15, 16, 16, edge)
    fill(front, 0, 0, 1, 16, edge)
    fill(front, 15, 0, 16, 16, edge)
    for y in range(3, 13, 2):
        fill(front, 3, y, 13, y + 1, shade(grey, 0.6))      # vent slots
    fill(front, 6, 13, 10, 14, (0xd8, 0xc0, 0x30))            # nameplate
    write_png(os.path.join(tex, "transformer_dry_front.png"), front)
    side = canvas(16, 16, grey + (255,))
    fill(side, 0, 0, 16, 1, shade(grey, 1.1))
    fill(side, 0, 15, 16, 16, edge)
    fill(side, 0, 0, 1, 16, edge)
    fill(side, 15, 0, 16, 16, edge)
    write_png(os.path.join(tex, "transformer_dry_side.png"), side)

    # Pad-mount: utility green box with a seam for the doors.
    green = (0x4a, 0x6e, 0x4c)
    gedge = (0x2c, 0x42, 0x2e)
    pad = canvas(16, 16, green + (255,))
    fill(pad, 0, 0, 16, 1, shade(green, 1.15))
    fill(pad, 0, 15, 16, 16, gedge)
    fill(pad, 0, 0, 1, 16, gedge)
    fill(pad, 15, 0, 16, 16, gedge)
    fill(pad, 8, 2, 9, 14, gedge)                              # door seam
    fill(pad, 5, 6, 7, 7, (0xd8, 0xd8, 0xd8))                  # handles
    fill(pad, 10, 6, 12, 7, (0xd8, 0xd8, 0xd8))
    fill(pad, 3, 11, 13, 13, (0xe8, 0xd0, 0x30))               # warning label
    write_png(os.path.join(tex, "transformer_pad_front.png"), pad)
    pside = canvas(16, 16, green + (255,))
    fill(pside, 0, 0, 16, 1, shade(green, 1.15))
    fill(pside, 0, 15, 16, 16, gedge)
    fill(pside, 0, 0, 1, 16, gedge)
    fill(pside, 15, 0, 16, 16, gedge)
    write_png(os.path.join(tex, "transformer_pad_side.png"), pside)

    # Pole can: dark grey drum with shading bands to read as a cylinder, cooling fins on the sides.
    drum = (0x62, 0x66, 0x6c)
    can = canvas(16, 16, drum + (255,))
    for x in range(16):
        f = 0.75 + 0.5 * (1 - abs(x - 7.5) / 7.5)
        fill(can, x, 0, x + 1, 16, shade(drum, f))
    fill(can, 0, 0, 16, 1, shade(drum, 1.3))
    fill(can, 0, 15, 16, 16, shade(drum, 0.55))
    fill(can, 0, 4, 16, 5, shade(drum, 0.7))                   # bands
    fill(can, 0, 11, 16, 12, shade(drum, 0.7))
    write_png(os.path.join(tex, "transformer_can.png"), can)

    # Porcelain bushing.
    porcelain = (0xd8, 0xd2, 0xc4)
    bush = canvas(16, 16, porcelain + (255,))
    fill(bush, 0, 0, 16, 1, shade(porcelain, 1.08))
    fill(bush, 0, 15, 16, 16, shade(porcelain, 0.6))
    fill(bush, 0, 5, 16, 6, shade(porcelain, 0.75))
    fill(bush, 0, 10, 16, 11, shade(porcelain, 0.75))
    write_png(os.path.join(tex, "transformer_bushing.png"), bush)


# ---------------------------------------------------------------- models

def cabinet_model(mount, kind):
    name = name_of(mount, kind)
    body = DRY_BODY if mount == "dry" else PAD_BODY
    elements = [element(*body, "#side", cull_south=(mount == "dry"))]
    elements[0]["faces"]["north"]["texture"] = "#front"
    for h in hubs(mount):
        elements.append(element(*h, "#terminal"))
    if kind == "3ph":
        # A second nameplate stripe marks the three-phase unit.
        front_z = body[2]
        elements.append(element(4, body[4] - 3, front_z - 0.25, 12, body[4] - 2, front_z, "#terminal"))
    tex = {
        "front": "%s:block/transformer_%s_front" % (MOD, mount),
        "side": "%s:block/transformer_%s_side" % (MOD, mount),
        "terminal": "%s:block/panel_terminal" % MOD,
        "particle": "%s:block/transformer_%s_side" % (MOD, mount),
    }
    write_block(name, {"parent": "block/block", "textures": tex, "elements": elements})


def pole_model(kind):
    name = name_of("pole", kind)
    elements = []
    if kind == "split":
        elements.append(element(*CAN, "#can"))
        for b in CAN_PRIMARIES + CAN_SECONDARIES:
            elements.append(element(*b, "#bushing"))
        # Hanger bracket to the pole.
        elements.append(element(7, 12, 15, 9, 14, 16.5, "#can"))
    else:
        for cx in BANK_CX:
            elements.append(element(cx - 2.25, 2, 10, cx + 2.25, 13, 16, "#can"))
            elements.append(element(cx - 0.75, 13, 12.25, cx + 0.75, 15.5, 13.75, "#bushing"))
            elements.append(element(cx - 0.75, 8, 8.5, cx + 0.75, 9.5, 10, "#bushing"))
        elements.append(element(7, 4.5, 8.5, 8.5, 6, 10, "#bushing"))
        elements.append(element(0.5, 13, 14.5, 15.5, 14, 16, "#can"))   # crossarm hanger
    tex = {
        "can": "%s:block/transformer_can" % MOD,
        "bushing": "%s:block/transformer_bushing" % MOD,
        "particle": "%s:block/transformer_can" % MOD,
    }
    write_block(name, {"parent": "block/block", "textures": tex, "elements": elements})


def write_block(name, model):
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), model)
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s" % (MOD, name)})
    dump(os.path.join(ASSETS, "blockstates", name + ".json"), {
        "variants": {
            "facing=north": {"model": "%s:block/%s" % (MOD, name)},
            "facing=east": {"model": "%s:block/%s" % (MOD, name), "y": 90},
            "facing=south": {"model": "%s:block/%s" % (MOD, name), "y": 180},
            "facing=west": {"model": "%s:block/%s" % (MOD, name), "y": 270},
        }
    })
    loot_table(name)


# ---------------------------------------------------------------- data

def recipes():
    iron = {"tag": "c:plates/iron"}
    coil = {"item": "powergrid:copper_coil"}
    core = {"item": "powergrid:transformer_core"}
    stone = {"item": "minecraft:smooth_stone"}
    unlock = {"items": "powergrid:transformer_core"}
    recipe("transformer_dry_split", ["III", "CTC", "III"], {"I": iron, "C": coil, "T": core}, 1, unlock)
    recipe("transformer_dry_3ph", ["CTC", "CTC", "CTC"], {"C": coil, "T": core}, 1, unlock)
    recipe("transformer_pad_split", ["III", "CTC", "SSS"], {"I": iron, "C": coil, "T": core, "S": stone}, 1, unlock)
    recipe("transformer_pad_3ph", ["CTC", "CTC", "SSS"], {"C": coil, "T": core, "S": stone}, 1, unlock)
    recipe("transformer_pole_split", [" I ", "CTC", " I "], {"I": iron, "C": coil, "T": core}, 1, unlock)
    recipe_shapeless("transformer_pole_3ph", [{"item": "%s:transformer_pole_split" % MOD}] * 3 + [iron], 1,
                     {"items": "%s:transformer_pole_split" % MOD})


def main():
    textures()
    for kind in KINDS:
        cabinet_model("dry", kind)
        cabinet_model("pad", kind)
        pole_model(kind)
    recipes()
    print("transformer assets written")


if __name__ == "__main__":
    main()
