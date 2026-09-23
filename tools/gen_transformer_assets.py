#!/usr/bin/env python3
"""Generates the transformer resources: models, blockstates, item models, textures, loot and recipes.

Run from the repository root:  python tools/gen_transformer_assets.py

Geometry mirrors TransformerGeometry.java; change both. North frame like the breaker panel: the
back (wall or pole) is the south side, the front faces north, +x is on the viewer's left. The
ground units are two blocks tall in one model (y up to 32); the three-phase pole bank is three
cans across three blocks (x from -16 to 32). Sized after Create: PowerPlantGrid's transformers.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

HUB_U = [3.5, 6.5, 9.5, 12.5]
KINDS = ["split", "3ph"]

DRY_BODY = (2, 0, 4, 14, 30, 16)
PAD_SKID = (0, 0, 0, 16, 1.5, 16)
PAD_TANK = (1.5, 1.5, 1.5, 14.5, 26, 14.5)
PAD_LID = (0.5, 26, 0.5, 15.5, 28, 15.5)
CAN = (3.75, 0, 6, 12.25, 12, 16)
CAN_LID = (3.5, 12, 5.75, 12.5, 13, 16)
CAN_PRIMARIES = [(4.75, 13, 10.25, 6.75, 16, 12.25), (9.25, 13, 10.25, 11.25, 16, 12.25)]
CAN_SECONDARIES = [(4.5, 4, 5, 6, 5.5, 6), (7.25, 4, 5, 8.75, 5.5, 6), (10, 4, 5, 11.5, 5.5, 6)]
BANK_PRIMARIES = [(4.5, 13, 10.25, 6, 16, 12.25), (7.25, 13, 10.25, 8.75, 16, 12.25), (10, 13, 10.25, 11.5, 16, 12.25)]
BANK_SECONDARIES = [(4.5, 8, 5, 6, 9.5, 6), (7.25, 8, 5, 8.75, 9.5, 6), (10, 8, 5, 11.5, 9.5, 6), (7.25, 3.5, 5, 8.75, 5, 6)]


def name_of(mount, kind):
    return "transformer_%s_%s" % (mount, kind)


def hubs(mount):
    out = []
    if mount == "dry":
        for u in HUB_U:
            x = 16 - u
            out += [(x - 1, 0, 9, x + 1, 1, 11), (x - 1, 3, 3, x + 1, 5, 4)]
    elif mount == "pad":
        for u in HUB_U:
            x = 16 - u
            out += [(x - 1, 3, 0.5, x + 1, 5, 1.5), (x - 1, 8, 0.5, x + 1, 10, 1.5)]
    return out


def bushing(x1, y1, z1, x2, y2, z2, texture):
    """A porcelain bushing: stacked, tapering skirts up to a cap."""
    cx, cz = (x1 + x2) / 2, (z1 + z2) / 2
    w, d = (x2 - x1) / 2, (z2 - z1) / 2
    h = y2 - y1
    steps = 3
    els = []
    for i in range(steps):
        f = 1 - 0.18 * i
        ya, yb = y1 + h * i / steps, y1 + h * (i + 1) / steps
        els.append(element(cx - w * f, ya, cz - d * f, cx + w * f, yb, cz + d * f, texture))
    els.append(element(cx - w * 0.5, y2 - 0.75, cz - d * 0.5, cx + w * 0.5, y2, cz + d * 0.5, "#cap"))
    return els


# ---------------------------------------------------------------- textures

def textures():
    tex = os.path.join(ASSETS, "textures", "block")

    def plain(name, rgb, edge_f=0.6, top_f=1.12):
        img = canvas(16, 16, rgb + (255,))
        fill(img, 0, 0, 16, 1, shade(rgb, top_f))
        fill(img, 0, 15, 16, 16, shade(rgb, edge_f))
        fill(img, 0, 0, 1, 16, shade(rgb, 0.85))
        fill(img, 15, 0, 16, 16, shade(rgb, edge_f))
        write_png(os.path.join(tex, name + ".png"), img)
        return img

    grey = (0x9a, 0x9e, 0xa4)
    front = plain("transformer_dry_front", grey)
    for y in range(3, 13, 2):
        fill(front, 3, y, 13, y + 1, shade(grey, 0.6))      # vent slots
    fill(front, 6, 13, 10, 14, (0xd8, 0xc0, 0x30))            # nameplate
    write_png(os.path.join(tex, "transformer_dry_front.png"), front)
    plain("transformer_dry_side", grey)

    tank = (0x4c, 0x52, 0x50)
    plain("transformer_tank", tank, 0.55, 1.2)
    fin = canvas(16, 16, shade(tank, 0.8) + (255,))
    for y in range(0, 16, 2):
        fill(fin, 0, y, 16, y + 1, shade(tank, 1.05))
    write_png(os.path.join(tex, "transformer_fin.png"), fin)
    skid = plain("transformer_skid", (0x2e, 0x30, 0x34), 0.6, 1.3)
    lid = plain("transformer_lid", shade(tank, 1.15), 0.7, 1.1)

    drum = (0xd8, 0xdc, 0xdc)
    can = canvas(16, 16, drum + (255,))
    for x in range(16):
        fill(can, x, 0, x + 1, 16, shade(drum, 0.78 + 0.32 * (1 - abs(x - 7.5) / 7.5)))
    fill(can, 0, 0, 16, 1, shade(drum, 1.05))
    fill(can, 0, 15, 16, 16, shade(drum, 0.5))
    fill(can, 0, 5, 16, 6, shade(drum, 0.7))
    fill(can, 0, 11, 16, 12, shade(drum, 0.7))
    write_png(os.path.join(tex, "transformer_can.png"), can)
    plain("transformer_can_lid", shade(drum, 0.85), 0.6, 1.05)

    porcelain = (0x8a, 0x8e, 0x92)
    bush = canvas(16, 16, porcelain + (255,))
    fill(bush, 0, 0, 16, 1, shade(porcelain, 1.15))
    fill(bush, 0, 15, 16, 16, shade(porcelain, 0.55))
    for y in (4, 9):
        fill(bush, 0, y, 16, y + 1, shade(porcelain, 0.7))
    write_png(os.path.join(tex, "transformer_bushing.png"), bush)
    plain("transformer_cap", (0xb8, 0x73, 0x33), 0.6, 1.2)


# ---------------------------------------------------------------- models

def write_block(name, model, item_parent=None):
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), model)
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s" % (MOD, item_parent or name)})
    m = "%s:block/%s" % (MOD, name)
    dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
        "facing=north": {"model": m}, "facing=east": {"model": m, "y": 90},
        "facing=south": {"model": m, "y": 180}, "facing=west": {"model": m, "y": 270},
    }})
    loot_table(name)


TEX = {
    "front": "%s:block/transformer_dry_front" % MOD,
    "side": "%s:block/transformer_dry_side" % MOD,
    "tank": "%s:block/transformer_tank" % MOD,
    "fin": "%s:block/transformer_fin" % MOD,
    "skid": "%s:block/transformer_skid" % MOD,
    "lid": "%s:block/transformer_lid" % MOD,
    "can": "%s:block/transformer_can" % MOD,
    "canlid": "%s:block/transformer_can_lid" % MOD,
    "bushing": "%s:block/transformer_bushing" % MOD,
    "cap": "%s:block/transformer_cap" % MOD,
    "terminal": "%s:block/panel_terminal" % MOD,
}


def dry_model(kind):
    name = name_of("dry", kind)
    elements = [element(*DRY_BODY, "#side", cull_south=True)]
    elements[0]["faces"]["north"]["texture"] = "#front"
    for h in hubs("dry"):
        elements.append(element(*h, "#terminal"))
    # A vent band up top; the three-phase unit gets a second nameplate stripe.
    elements.append(element(3, 20, 3.75, 13, 27, 4, "#fin"))
    if kind == "3ph":
        elements.append(element(4, 17, 3.75, 12, 18, 4, "#cap"))
    write_block(name, {"parent": "block/block", "textures": dict(TEX, particle=TEX["side"]), "elements": elements})


def pad_model(kind):
    name = name_of("pad", kind)
    elements = [element(*PAD_SKID, "#skid"), element(*PAD_TANK, "#tank"), element(*PAD_LID, "#lid")]
    # Radiator fins down both sides of the tank.
    for z in (2.5, 4.5, 6.5, 8.5, 10.5, 12.5):
        elements.append(element(0, 4, z, 1.5, 22, z + 1, "#fin"))
        elements.append(element(14.5, 4, z, 16, 22, z + 1, "#fin"))
    for h in hubs("pad"):
        elements.append(element(*h, "#terminal"))
    # Bushings on the lid: HV stacks along the back, LV shorter ones along the front.
    hv = 3 if kind == "3ph" else 2
    lv = 4 if kind == "3ph" else 3
    for i in range(hv):
        x = 16 - (3.5 + 9 * i / max(1, hv - 1))
        elements += bushing(x - 1, 28, 11.5, x + 1, 32, 13.5, "#bushing")
    for i in range(lv):
        x = 16 - (3 + 10 * i / max(1, lv - 1))
        elements += bushing(x - 0.75, 28, 3, x + 0.75, 30.5, 4.5, "#bushing")
    elements.append(element(6, 10, 1.25, 10, 13, 1.5, "#cap"))     # nameplate
    write_block(name, {"parent": "block/block", "textures": dict(TEX, particle=TEX["tank"]), "elements": elements})


def can_elements(dx, primaries, secondaries):
    els = [element(CAN[0] + dx, CAN[1], CAN[2], CAN[3] + dx, CAN[4], CAN[5], "#can"),
           element(CAN_LID[0] + dx, CAN_LID[1], CAN_LID[2], CAN_LID[3] + dx, CAN_LID[4], CAN_LID[5], "#canlid"),
           element(7 + dx, 12, 15, 9, 14, 16.5, "#skid")]      # hanger bracket to the pole
    for b in primaries:
        els += bushing(b[0] + dx, b[1], b[2], b[3] + dx, b[4], b[5], "#bushing")
    for b in secondaries:
        els.append(element(b[0] + dx, b[1], b[2], b[3] + dx, b[4], b[5], "#cap"))
    return els


def pole_model(kind):
    name = name_of("pole", kind)
    if kind == "split":
        elements = can_elements(0, CAN_PRIMARIES, CAN_SECONDARIES)
    else:
        elements = []
        # Outer cans carry the bank's other two phases; only the middle can has terminals.
        for dx in (-16, 16):
            elements += can_elements(dx, [CAN_PRIMARIES[0], CAN_PRIMARIES[1]], [])
        elements += can_elements(0, BANK_PRIMARIES, BANK_SECONDARIES)
        elements.append(element(-14, 13, 14.5, 30, 14.5, 16, "#skid"))    # crossarm
    write_block(name, {"parent": "block/block", "textures": dict(TEX, particle=TEX["can"]), "elements": elements})


def filler():
    dump(os.path.join(ASSETS, "models", "block", "transformer_filler.json"), {"textures": {"particle": TEX["tank"]}})
    variants = {}
    for facing in ("north", "east", "south", "west"):
        for part in ("above", "left", "right"):
            variants["facing=%s,part=%s" % (facing, part)] = {"model": "%s:block/transformer_filler" % MOD}
    dump(os.path.join(ASSETS, "blockstates", "transformer_filler.json"), {"variants": variants})
    dump(os.path.join(DATA, "loot_table", "blocks", "transformer_filler.json"), {"type": "minecraft:block", "pools": []})


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
        dry_model(kind)
        pad_model(kind)
        pole_model(kind)
    filler()
    recipes()
    print("transformer assets written")


if __name__ == "__main__":
    main()
