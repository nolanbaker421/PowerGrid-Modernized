#!/usr/bin/env python3
"""Generates the transformer resources: models, blockstates, item models, textures, loot and recipes.

Run from the repository root:  python tools/gen_transformer_assets.py

Geometry mirrors TransformerGeometry.java (sizes) and TransformerSpec.java (nameplates); change
both. North frame: the back (wall or pole) is the south side, the front faces north, +x on the
viewer's left. Models reach into neighbouring cells (-16..32 px), which the game allows; the
matching filler blocks carry the collision there. Sized after Create: PowerPlantGrid.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

HUB_U = [3.5, 6.5, 9.5, 12.5]

# id, kind (2 or 3 phases), size
SPECS = [
    ("pole_480v_240v", 2, "POLE_S"), ("pole_1kv_240v", 2, "POLE_M"), ("pole_10kv_240v", 2, "POLE_M"), ("pole_35kv_240v", 2, "POLE_L"),
    ("pad_1kv_240v", 2, "PAD"), ("pad_10kv_240v", 2, "PAD"), ("pad_1kv_208v", 3, "PAD"), ("pad_10kv_208v", 3, "PAD"), ("pad_10kv_480v", 3, "PAD"),
    ("sub_35kv_480v", 3, "POWER_S"), ("sub_35kv_10kv", 3, "POWER_S"), ("sub_100kv_35kv", 3, "POWER_L"),
    ("dry_480v_240v", 2, "DRY"), ("dry_480v_208v", 3, "DRY"),
]
POLES = {"POLE_S", "POLE_M", "POLE_L"}

BODY = {
    "POLE_S": (4.5, 0, 3.5, 11.5, 10.5, 11.5), "POLE_M": (3.75, 0, 2.75, 12.25, 12, 12.25), "POLE_L": (2.5, 0, 1.5, 13.5, 18, 13.5),
    "PAD": (-3, 2, -1, 19, 18, 17), "POWER_S": (-7.5, 3.5, -2.5, 23.5, 24.5, 18.5), "POWER_L": (-10, 3.5, -4.5, 26, 24.5, 20.5),
    "DRY": (2, 0, 4, 14, 30, 16),
}
LID = {
    "POLE_S": (4.25, 10.5, 3.25, 11.75, 11.5, 11.75), "POLE_M": (3.5, 12, 2.5, 12.5, 13, 12.5), "POLE_L": (2.25, 18, 1.25, 13.75, 19.5, 13.75),
    "PAD": (-4, 18, -2, 20, 19.5, 18), "POWER_S": (-9, 24.5, -4, 25, 27, 20), "POWER_L": (-11.5, 24.5, -6, 27.5, 27, 22), "DRY": None,
}
SKID = {"PAD": (-4, 0, -2, 20, 2, 18), "POWER_S": (-9, 0, -4, 25, 3.5, 20), "POWER_L": (-11.5, 0, -6, 27.5, 3.5, 22)}
HUNG_SHIFT = {"POLE_S": (0, 2, 4.5), "POLE_M": (0, 0, 3.75), "POLE_L": (0, 9, 2.5)}
HV_X = {"POLE_S": [10.25, 5.75], "POLE_M": [10.25, 5.75], "POLE_L": [11.5, 4.5],
        ("PAD", 2): [12, 4], ("PAD", 3): [14, 8, 2], "POWER_S": [18, 8, -2], "POWER_L": [20, 8, -4]}
LV_X = {"POLE_S": [10.25, 8, 5.75], "POLE_M": [10.75, 8, 5.25], "POLE_L": [11.5, 8, 4.5],
        ("PAD", 3): [14, 8, 2], ("PAD", 4): [15.5, 10.5, 5.5, 0.5], "POWER_S": [19, 11.67, 4.33, -3], "POWER_L": [21, 12.33, 3.67, -5]}


def hv_x(size, count):
    return HV_X[(size, count)] if (size, count) in HV_X else HV_X[size]


def lv_x(size, count):
    return LV_X[(size, count)] if (size, count) in LV_X else LV_X[size]


def hv_bushing(size, i, count):
    x = hv_x(size, count)[i]
    top = LID[size][4]
    return {
        "POLE_S": (x - 0.75, top, 8, x + 0.75, top + 2, 9.5), "POLE_M": (x - 1, top, 8.75, x + 1, top + 3, 10.75),
        "POLE_L": (x - 1, top, 9.5, x + 1, top + 3, 11.5), "PAD": (x - 2.5, top, 10.5, x + 2.5, 28, 15.5),
        "POWER_S": (x - 2.5, top, 12.5, x + 2.5, 32, 17.5), "POWER_L": (x - 2.5, top, 14.5, x + 2.5, 32, 19.5),
    }[size]


def lv_bushing(size, j, count):
    x = lv_x(size, count)[j]
    z1 = BODY[size][2]
    return {
        "POLE_S": (x - 0.75, 3.5, z1 - 1, x + 0.75, 4.75, z1), "POLE_M": (x - 0.75, 4, z1 - 1, x + 0.75, 5.5, z1),
        "POLE_L": (x - 0.75, 6, z1 - 1, x + 0.75, 7.5, z1), "PAD": (x - 1.5, 19.5, 0.5, x + 1.5, 24, 3.5),
        "POWER_S": (x - 1.5, 27, -1.5, x + 1.5, 30, 1.5), "POWER_L": (x - 1.5, 27, -3.5, x + 1.5, 30, -0.5),
    }[size]


def shift(box, by):
    return (box[0] + by[0], box[1] + by[1], box[2] + by[2], box[3] + by[0], box[4] + by[1], box[5] + by[2])


def stack(box, texture, steps=3, cap=1.5):
    """A porcelain bushing: tapering skirts up to a brass cap."""
    x1, y1, z1, x2, y2, z2 = box
    cx, cz = (x1 + x2) / 2, (z1 + z2) / 2
    w, d = (x2 - x1) / 2, (z2 - z1) / 2
    h = (y2 - cap) - y1
    els = []
    for i in range(steps):
        f = 1 - 0.15 * i
        ya, yb = y1 + h * i / steps, y1 + h * (i + 1) / steps
        els.append(element(cx - w * f, ya, cz - d * f, cx + w * f, yb, cz + d * f, texture))
    els.append(element(cx - w * 0.55, y2 - cap, cz - d * 0.55, cx + w * 0.55, y2, cz + d * 0.55, "#cap"))
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
        fill(front, 3, y, 13, y + 1, shade(grey, 0.6))
    fill(front, 6, 13, 10, 14, (0xd8, 0xc0, 0x30))
    write_png(os.path.join(tex, "transformer_dry_front.png"), front)
    plain("transformer_dry_side", grey)

    tank = (0x56, 0x5c, 0x5e)
    plain("transformer_tank", tank, 0.55, 1.2)
    dark = (0x3e, 0x42, 0x46)
    plain("transformer_tank_dark", dark, 0.55, 1.25)
    fin = canvas(16, 16, shade(tank, 0.8) + (255,))
    for y in range(0, 16, 2):
        fill(fin, 0, y, 16, y + 1, shade(tank, 1.05))
    write_png(os.path.join(tex, "transformer_fin.png"), fin)
    plain("transformer_skid", (0x2a, 0x2c, 0x30), 0.6, 1.3)
    plain("transformer_lid", shade(tank, 1.15), 0.7, 1.1)

    white = (0xe4, 0xe6, 0xe4)
    can = canvas(16, 16, white + (255,))
    for x in range(16):
        fill(can, x, 0, x + 1, 16, shade(white, 0.72 + 0.3 * (1 - abs(x - 7.5) / 7.5)))
    fill(can, 0, 0, 16, 1, shade(white, 1.02))
    fill(can, 0, 15, 16, 16, shade(white, 0.5))
    fill(can, 0, 5, 16, 6, shade(white, 0.72))
    fill(can, 0, 11, 16, 12, shade(white, 0.72))
    write_png(os.path.join(tex, "transformer_can.png"), can)
    plain("transformer_can_lid", shade(white, 0.8), 0.6, 1.05)

    porcelain = (0x8a, 0x8e, 0x92)
    bush = canvas(16, 16, porcelain + (255,))
    fill(bush, 0, 0, 16, 1, shade(porcelain, 1.15))
    fill(bush, 0, 15, 16, 16, shade(porcelain, 0.55))
    for y in (4, 9):
        fill(bush, 0, y, 16, y + 1, shade(porcelain, 0.7))
    write_png(os.path.join(tex, "transformer_bushing.png"), bush)
    plain("transformer_cap", (0xb8, 0x73, 0x33), 0.6, 1.2)


TEX = {
    "front": "%s:block/transformer_dry_front" % MOD, "side": "%s:block/transformer_dry_side" % MOD,
    "tank": "%s:block/transformer_tank" % MOD, "dark": "%s:block/transformer_tank_dark" % MOD,
    "fin": "%s:block/transformer_fin" % MOD, "skid": "%s:block/transformer_skid" % MOD, "lid": "%s:block/transformer_lid" % MOD,
    "can": "%s:block/transformer_can" % MOD, "canlid": "%s:block/transformer_can_lid" % MOD,
    "bushing": "%s:block/transformer_bushing" % MOD, "cap": "%s:block/transformer_cap" % MOD,
    "terminal": "%s:block/panel_terminal" % MOD,
}


# ---------------------------------------------------------------- models

def unit_elements(size, phases, hung):
    by = HUNG_SHIFT[size] if hung and size in HUNG_SHIFT else (0, 0, 0)
    primaries = phases
    secondaries = 3 if phases == 2 else 4
    els = []
    if size == "DRY":
        e = element(*BODY[size], "#side", cull_south=True)
        e["faces"]["north"]["texture"] = "#front"
        els.append(e)
        els.append(element(3, 20, 3.75, 13, 27, 4, "#fin"))
        if phases == 3:
            els.append(element(4, 17, 3.75, 12, 18, 4, "#cap"))
        for u in HUB_U:
            x = 16 - u
            els.append(element(x - 1, 0, 9, x + 1, 1, 11, "#terminal"))
            els.append(element(x - 1, 3, 3, x + 1, 5, 4, "#terminal"))
        return els
    is_pole = size in POLES
    body_tex = "#can" if is_pole else ("#dark" if size == "POWER_L" else "#tank")
    lid_tex = "#canlid" if is_pole else "#lid"
    if size in SKID:
        els.append(element(*SKID[size], "#skid"))
    els.append(element(*shift(BODY[size], by), body_tex))
    els.append(element(*shift(LID[size], by), lid_tex))
    if size in ("POWER_S", "POWER_L"):
        # Radiator banks down both sides.
        x1, _, z1, x2, _, z2 = SKID[size]
        for z in range(int(z1) + 2, int(z2) - 2, 3):
            els.append(element(x1, 7, z, BODY[size][0], 21, z + 1.5, "#fin"))
            els.append(element(BODY[size][3], 7, z, x2, 21, z + 1.5, "#fin"))
    for i in range(primaries):
        els += stack(shift(hv_bushing(size, i, primaries), by), "#bushing", cap=1 if is_pole else 2)
    for j in range(secondaries):
        b = shift(lv_bushing(size, j, secondaries), by)
        if is_pole:
            els.append(element(*b, "#cap"))                       # a stud on the front
        else:
            els += stack(b, "#bushing", steps=2, cap=1.5)
    if is_pole:
        # Hanger bracket to the pole.
        top = LID[size][4] + by[1]
        els.append(element(7, top - 2, 15, 9, top, 16.5, "#skid"))
    else:
        els.append(element(6, 10, BODY[size][2] - 0.25, 10, 13, BODY[size][2], "#cap"))   # nameplate
    return els


def unit(spec_id, phases, size):
    name = "transformer_" + spec_id
    standing = {"parent": "block/block", "textures": dict(TEX, particle=TEX["can"] if size in POLES else TEX["tank"]), "elements": unit_elements(size, phases, False)}
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), standing)
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s" % (MOD, name),
         "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}})
    models = {False: "%s:block/%s" % (MOD, name)}
    if size in POLES:
        hung = dict(standing, elements=unit_elements(size, phases, True))
        dump(os.path.join(ASSETS, "models", "block", name + "_hung.json"), hung)
        models[True] = "%s:block/%s_hung" % (MOD, name)
    else:
        models[True] = models[False]
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        for hung in (False, True):
            v = {"model": models[hung]}
            v.update(rot)
            variants["facing=%s,hung=%s" % (facing, "true" if hung else "false")] = v
    dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
    loot_table(name)


def filler():
    dump(os.path.join(ASSETS, "models", "block", "transformer_filler.json"), {"textures": {"particle": TEX["tank"]}})
    variants = {}
    for ox in range(3):
        for oy in range(2):
            for oz in range(3):
                variants["ox=%d,oy=%d,oz=%d" % (ox, oy, oz)] = {"model": "%s:block/transformer_filler" % MOD}
    dump(os.path.join(ASSETS, "blockstates", "transformer_filler.json"), {"variants": variants})
    dump(os.path.join(DATA, "loot_table", "blocks", "transformer_filler.json"), {"type": "minecraft:block", "pools": []})


# ---------------------------------------------------------------- data

def recipes():
    iron = {"tag": "c:plates/iron"}
    coil = {"item": "powergrid:copper_coil"}
    core = {"item": "powergrid:transformer_core"}
    stone = {"item": "minecraft:smooth_stone"}
    copper = {"tag": "c:storage_blocks/copper"}
    unlock = {"items": "powergrid:transformer_core"}
    by_size = {
        "POLE_S": (["I", "C", "T"], {"I": iron, "C": coil, "T": core}),
        "POLE_M": ([" I ", "CTC", " I "], {"I": iron, "C": coil, "T": core}),
        "POLE_L": (["III", "CTC", "CTC"], {"I": iron, "C": coil, "T": core}),
        "PAD": (["III", "CTC", "SSS"], {"I": iron, "C": coil, "T": core, "S": stone}),
        "POWER_S": (["CTC", "CTC", "SSS"], {"C": coil, "T": core, "S": stone}),
        "POWER_L": (["CTC", "TTT", "SSS"], {"C": coil, "T": core, "S": stone}),
        "DRY": (["III", "CTC", "III"], {"I": iron, "C": coil, "T": core}),
    }
    for spec_id, phases, size in SPECS:
        pattern, key = by_size[size]
        if phases == 3 and size != "POWER_L":
            pattern = [row.replace("I", "C", 1) if "I" in row else row for row in pattern]
        recipe("transformer_" + spec_id, pattern, key, 1, unlock)


def main():
    textures()
    for spec_id, phases, size in SPECS:
        unit(spec_id, phases, size)
    filler()
    recipes()
    print("transformer assets written")


if __name__ == "__main__":
    main()
