#!/usr/bin/env python3
"""Generates the breaker panel resources (models, blockstates, textures, loot, recipes).

Run from the repository root:  python tools/gen_breaker_panel_assets.py

Geometry here mirrors PanelLayout.java; change both together. Everything is written in the
north-facing frame: the enclosure sits against the south side of the block (z 10..16) with its
open front facing north. A viewer looking at the front sees +x on their left, so viewer
coordinate u maps to model x = 16 - u.
"""
import json
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
MOD = "powergrid_modernized"
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data", MOD)

# Panel specs: id, rating, branch slots, lugs, enclosure colours (body, edge)
PANELS = [
    ("breaker_panel_200", 200, 8, 1, (0x9d, 0x9d, 0x9d), (0x6b, 0x6b, 0x6b)),
    ("breaker_panel_400", 400, 12, 1, (0x7f, 0x87, 0x94), (0x4f, 0x55, 0x63)),
    ("breaker_panel_800", 800, 12, 1, (0x5c, 0x5c, 0x5c), (0x33, 0x33, 0x33)),
    ("breaker_panel_200_2p", 200, 12, 2, (0xa8, 0xa4, 0x9c), (0x6e, 0x6a, 0x62)),
    ("breaker_panel_400_2p", 400, 12, 2, (0x86, 0x8c, 0x96), (0x52, 0x58, 0x64)),
    ("breaker_panel_400_3p", 400, 12, 3, (0x7a, 0x84, 0x7e), (0x48, 0x52, 0x4c)),
    ("breaker_panel_800_3p", 800, 12, 3, (0x55, 0x58, 0x5e), (0x2e, 0x30, 0x36)),
    ("breaker_panel_800_2p", 800, 12, 2, (0x60, 0x5e, 0x5a), (0x36, 0x34, 0x30)),
    ("breaker_panel_200_3p", 200, 12, 3, (0x9a, 0xa4, 0x9e), (0x62, 0x6c, 0x66)),
]
BREAKERS = [50, 200, 400, 800]
FRAMES = {50: "1-50", 200: "51-200", 400: "201-400", 800: "401-800"}
MULTI_POLES = [2, 3]

# Layout constants (pixels, north frame) - keep in sync with PanelLayout.java
BODY = (1, 1, 10, 15, 15, 16)
FRONT_Z = 10
HUB_U = [3.5, 6.5, 9.5, 12.5]
HUB_Z1, HUB_Z2 = 12.5, 14.5
SIDE_HUB_Y = [4, 9]
LEFT_U1, RIGHT_U1, COLUMN_WIDTH, ROW_TOP = 1.5, 8.5, 6, 11


def pitch(rows):
    return 9.0 / rows


def row_height(rows):
    return pitch(rows) - 0.25


def slot_rows(spec_slots, slot):
    rows = spec_slots // 2
    row, col = slot // 2, slot % 2
    vt = ROW_TOP - pitch(rows) * row
    vb = vt - row_height(rows)
    return col, vb, vt


# ---------------------------------------------------------------- PNG writer

def write_png(path, pixels):
    h = len(pixels)
    w = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def canvas(w, h, rgba=(0, 0, 0, 0)):
    return [[tuple(rgba) for _ in range(w)] for _ in range(h)]


def fill(img, x1, y1, x2, y2, rgb, a=255):
    for y in range(int(y1), int(y2)):
        for x in range(int(x1), int(x2)):
            if 0 <= y < len(img) and 0 <= x < len(img[0]):
                img[y][x] = (rgb[0], rgb[1], rgb[2], a)


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb)


FONT = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "8": ["111", "101", "111", "101", "111"],
    "P": ["111", "101", "111", "100", "100"],
}


def text(img, x, y, s, rgb):
    for ch in s:
        glyph = FONT[ch]
        for dy, row in enumerate(glyph):
            for dx, bit in enumerate(row):
                if bit == "1":
                    fill(img, x + dx, y + dy, x + dx + 1, y + dy + 1, rgb)
        x += 4


# ---------------------------------------------------------------- textures

def panel_textures(name, body, edge, lugs):
    tex = os.path.join(ASSETS, "textures", "block")
    # Front: enclosure interior. Drawn as the viewer sees it (u left->right, v top->bottom).
    front = canvas(16, 16, body + (255,))
    interior = shade(body, 0.82)
    fill(front, 1, 0, 15, 16, interior)
    fill(front, 1, 0, 15, 1, edge)
    fill(front, 1, 15, 15, 16, edge)
    fill(front, 1, 0, 2, 16, edge)
    fill(front, 14, 0, 15, 16, edge)
    # Vertical bus bars behind the breaker columns (copper), one per lug, and the mounting rails.
    copper = (0xb8, 0x73, 0x33)
    bars = {1: [(7, 9)], 2: [(6, 7), (9, 10)], 3: [(5, 6), (7, 8), (9, 10)]}[lugs]
    for x1, x2 in bars:
        fill(front, x1, 2, x2, 14, copper)
        fill(front, x2 - 1, 2, x2, 14, shade(copper, 0.8))
    rail = shade(interior, 0.7)
    fill(front, 2, 5, 7, 6, rail)
    fill(front, 9, 5, 14, 6, rail)
    fill(front, 2, 13, 7, 14, rail)
    fill(front, 9, 13, 14, 14, rail)
    write_png(os.path.join(tex, name + "_front.png"), front)

    # Sides, back, top and bottom: plain enclosure metal with a lighter seam.
    side = canvas(16, 16, body + (255,))
    fill(side, 0, 0, 16, 1, shade(body, 1.12))
    fill(side, 0, 15, 16, 16, edge)
    fill(side, 0, 0, 1, 16, edge)
    fill(side, 15, 0, 16, 16, edge)
    write_png(os.path.join(tex, name + "_side.png"), side)


def shared_textures():
    tex = os.path.join(ASSETS, "textures", "block")
    silver = (0xd0, 0xd4, 0xd8)
    lug = canvas(16, 16, silver + (255,))
    fill(lug, 0, 0, 16, 1, shade(silver, 1.08))
    fill(lug, 0, 15, 16, 16, shade(silver, 0.7))
    fill(lug, 0, 0, 1, 16, shade(silver, 0.85))
    fill(lug, 15, 0, 16, 16, shade(silver, 0.7))
    write_png(os.path.join(tex, "panel_terminal.png"), lug)

    # QO-style breaker: black molded body with a recessed toggle channel down the middle. The
    # body texture is squeezed onto a 5.5 x 2 px face, so details are single pixels: a green mark
    # toward the outer end (visible when OFF) and a red mark toward the bus end (visible when ON),
    # plus the red trip window that only the tripped texture shows.
    black = (0x1c, 0x1c, 0x1c)
    for state in ("on", "off", "tripped"):
        body = canvas(16, 16, black + (255,))
        fill(body, 0, 0, 16, 1, shade(black, 1.8))
        fill(body, 0, 15, 16, 16, shade(black, 0.5))
        fill(body, 0, 0, 1, 16, shade(black, 1.4))
        fill(body, 15, 0, 16, 16, shade(black, 0.7))
        fill(body, 1, 4, 15, 12, (0x2e, 0x2e, 0x2e))          # toggle channel
        if state == "off":
            fill(body, 12, 6, 15, 10, (0x2f, 0xa8, 0x3a))      # OFF mark uncovered at the bus end
        if state == "on":
            fill(body, 1, 6, 4, 10, (0xc8, 0x28, 0x28))        # ON mark uncovered at the outer end
        if state == "tripped":
            fill(body, 1, 6, 4, 10, (0xc8, 0x28, 0x28))
            fill(body, 12, 6, 15, 10, (0xd0, 0x30, 0x30))      # Visi-Trip window
        write_png(os.path.join(tex, "breaker_body_%s.png" % state), body)

    handle = canvas(16, 16, (0x11, 0x11, 0x11, 255))
    fill(handle, 0, 0, 16, 1, (0x3c, 0x3c, 0x3c))
    fill(handle, 0, 15, 16, 16, (0x06, 0x06, 0x06))
    fill(handle, 7, 5, 9, 11, (0xe8, 0xe8, 0xe8))              # grip stripe / rating stamp
    write_png(os.path.join(tex, "breaker_handle.png"), handle)


def breaker_item_texture(rating, poles=1):
    img = canvas(16, 16)
    black = (0x1c, 0x1c, 0x1c)
    if poles == 1:
        fill(img, 0, 3, 16, 13, black)
        fill(img, 0, 3, 16, 4, shade(black, 1.8))
        fill(img, 0, 12, 16, 13, shade(black, 0.5))
        fill(img, 1, 5, 15, 11, (0x2e, 0x2e, 0x2e))
        fill(img, 11, 4, 15, 12, (0x11, 0x11, 0x11))
        fill(img, 12, 6, 14, 10, (0x3c, 0x3c, 0x3c))
        fill(img, 12, 7, 13, 9, (0xe8, 0xe8, 0xe8))
        text(img, 1, 6, str(rating), (0xe8, 0xe8, 0xe8))
        name = "breaker_%da" % rating
    else:
        # Stacked bodies under one handle tie; the pole count is stamped in the corner.
        height = 14 // poles
        for p in range(poles):
            y1 = 1 + p * height
            fill(img, 0, y1, 16, y1 + height, black)
            fill(img, 0, y1, 16, y1 + 1, shade(black, 1.8))
            fill(img, 0, y1 + height - 1, 16, y1 + height, shade(black, 0.5))
            fill(img, 1, y1 + 1, 10, y1 + height - 1, (0x2e, 0x2e, 0x2e))
        fill(img, 11, 1, 15, 15, (0x11, 0x11, 0x11))           # handle tie down the right
        fill(img, 12, 3, 14, 13, (0x3c, 0x3c, 0x3c))
        fill(img, 12, 7, 13, 9, (0xe8, 0xe8, 0xe8))
        text(img, 1, 2, str(rating), (0xe8, 0xe8, 0xe8))
        text(img, 1, 9, "%dP" % poles, (0xe8, 0xc8, 0x60))
        name = "breaker_%da_%dp" % (rating, poles)
    write_png(os.path.join(ASSETS, "textures", "item", name + ".png"), img)
    return name


# ---------------------------------------------------------------- models

def _wrap(a, b):
    """A UV span moved into the 16 px texture: whole tiles shifted, oversize spans stretched."""
    if b - a >= 16:
        return 0, 16
    k = math.floor(a / 16)
    a, b = a - 16 * k, b - 16 * k
    if b > 16:
        a, b = 16 - (b - a), 16
    return a, b


def element(x1, y1, z1, x2, y2, z2, texture, cull_south=False):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        if face == "north":
            uv = [16 - x2, 16 - y2, 16 - x1, 16 - y1]
        elif face == "south":
            uv = [x1, 16 - y2, x2, 16 - y1]
        elif face == "east":
            uv = [16 - z2, 16 - y2, 16 - z1, 16 - y1]
        elif face == "west":
            uv = [z1, 16 - y2, z2, 16 - y1]
        elif face == "up":
            uv = [x1, z1, x2, z2]
        else:
            uv = [x1, 16 - z2, x2, 16 - z1]
        u1, v1, u2, v2 = uv
        u1, u2 = _wrap(u1, u2)
        v1, v2 = _wrap(v1, v2)
        f = {"uv": [u1, v1, u2, v2], "texture": texture}
        if face == "south" and cull_south:
            f["cullface"] = "south"
        faces[face] = f
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def panel_model(name, slots):
    elements = [element(*BODY, "#side", cull_south=True)]
    elements[0]["faces"]["north"]["texture"] = "#front"
    # Conduit knockouts: four across the top edge, four across the bottom.
    for u in HUB_U:
        x = 16 - u
        elements.append(element(x - 1, 15, HUB_Z1, x + 1, 16, HUB_Z2, "#terminal"))
        elements.append(element(x - 1, 0, HUB_Z1, x + 1, 1, HUB_Z2, "#terminal"))
    # Two more on each side (viewer's left is +x).
    for y in SIDE_HUB_Y:
        elements.append(element(15, y, HUB_Z1, 16, y + 2, HUB_Z2, "#terminal"))
        elements.append(element(0, y, HUB_Z1, 1, y + 2, HUB_Z2, "#terminal"))
    model = {
        "parent": "block/block",
        "textures": {
            "front": "%s:block/%s_front" % (MOD, name),
            "side": "%s:block/%s_side" % (MOD, name),
            "terminal": "%s:block/panel_terminal" % MOD,
            "particle": "%s:block/%s_side" % (MOD, name),
        },
        "elements": elements,
    }
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


def breaker_models():
    # Unit-cube breaker, scaled by the renderer to the slot size. Front is -z; the handle sticks
    # out of the front. Handle position encodes the state: right = on, left = off, middle = tripped.
    handles = {"on": (10, 15), "off": (1, 6), "tripped": (5.5, 10.5)}
    for state, (hx1, hx2) in handles.items():
        model = {
            "textures": {
                "body": "%s:block/breaker_body_%s" % (MOD, state),
                "handle": "%s:block/breaker_handle" % MOD,
                "particle": "%s:block/breaker_body_%s" % (MOD, state),
            },
            "elements": [
                element(0, 0, 4, 16, 16, 16, "#body"),
                element(hx1, 3, 0, hx2, 13, 8, "#handle"),
            ],
        }
        dump(os.path.join(ASSETS, "models", "block", "breaker", state + ".json"), model)


def blank_and_lock():
    tex = os.path.join(ASSETS, "textures", "block")
    plate = (0x2a, 0x2a, 0x2a)
    img = canvas(16, 16, plate + (255,))
    fill(img, 0, 0, 16, 1, shade(plate, 1.6))
    fill(img, 0, 15, 16, 16, shade(plate, 0.6))
    fill(img, 0, 0, 1, 16, shade(plate, 1.3))
    fill(img, 15, 0, 16, 16, shade(plate, 0.7))
    write_png(os.path.join(tex, "breaker_blank.png"), img)
    red = (0xc8, 0x28, 0x28)
    lock = canvas(16, 16, red + (255,))
    fill(lock, 0, 0, 16, 1, shade(red, 1.3))
    fill(lock, 0, 15, 16, 16, shade(red, 0.6))
    fill(lock, 6, 5, 10, 11, (0xe8, 0xe8, 0xe8))                # hasp
    fill(lock, 7, 6, 9, 10, red)
    write_png(os.path.join(tex, "breaker_lock.png"), lock)

    # Blank: a flat plate the size of the breaker face. Lock: a red hasp across the middle, in front of the handle.
    dump(os.path.join(ASSETS, "models", "block", "breaker", "blank.json"), {
        "textures": {"plate": "%s:block/breaker_blank" % MOD, "particle": "%s:block/breaker_blank" % MOD},
        "elements": [element(0, 0, 8, 16, 16, 16, "#plate")],
    })
    dump(os.path.join(ASSETS, "models", "block", "breaker", "lock.json"), {
        "textures": {"lock": "%s:block/breaker_lock" % MOD, "particle": "%s:block/breaker_lock" % MOD},
        "elements": [element(4, 1, -3, 12, 15, 0, "#lock")],
    })

    # Item icons.
    item = os.path.join(ASSETS, "textures", "item")
    img = canvas(16, 16)
    fill(img, 0, 3, 16, 13, plate)
    fill(img, 0, 3, 16, 4, shade(plate, 1.6))
    fill(img, 0, 12, 16, 13, shade(plate, 0.6))
    write_png(os.path.join(item, "breaker_blank.png"), img)
    img = canvas(16, 16)
    fill(img, 4, 7, 12, 15, red)                                # body
    fill(img, 4, 7, 12, 8, shade(red, 1.3))
    fill(img, 5, 2, 7, 8, (0xc0, 0xc0, 0xc0))                   # shackle
    fill(img, 9, 2, 11, 8, (0xc0, 0xc0, 0xc0))
    fill(img, 5, 2, 11, 4, (0xc0, 0xc0, 0xc0))
    fill(img, 7, 10, 9, 12, (0x30, 0x10, 0x10))                 # keyhole
    write_png(os.path.join(item, "breaker_lock.png"), img)
    for name in ("breaker_blank", "breaker_lock"):
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (MOD, name)},
        })
    recipe("breaker_blank", ["II"], {"I": {"tag": "c:nuggets/iron"}}, 4, {"items": "minecraft:iron_nugget"})
    recipe("breaker_lock", [" N ", "NIN", "NNN"], {"N": {"tag": "c:nuggets/iron"}, "I": {"tag": "c:ingots/iron"}}, 1, {"items": "minecraft:iron_ingot"})


def breaker_item_models():
    names = ["breaker_%da" % rating for rating in BREAKERS]
    names += ["breaker_%da_%dp" % (rating, poles) for poles in MULTI_POLES for rating in BREAKERS]
    for name in names:
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/%s" % (MOD, name)},
        })


# ---------------------------------------------------------------- data

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
            "has_item": {
                "conditions": {"items": [unlock_item]},
                "trigger": "minecraft:inventory_changed",
            },
            "has_the_recipe": {
                "conditions": {"recipe": "%s:crafting/%s" % (MOD, name)},
                "trigger": "minecraft:recipe_unlocked",
            },
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": ["%s:crafting/%s" % (MOD, name)]},
    })


def recipe(name, pattern, key, count, unlock_item):
    dump(os.path.join(DATA, "recipe", "crafting", name + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": key,
        "pattern": pattern,
        "result": {"count": count, "id": "%s:%s" % (MOD, name)},
    })
    advancement(name, unlock_item)


def recipe_shapeless(name, ingredients, count, unlock_item):
    dump(os.path.join(DATA, "recipe", "crafting", name + ".json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ingredients,
        "result": {"count": count, "id": "%s:%s" % (MOD, name)},
    })
    advancement(name, unlock_item)


def recipes():
    iron_plate = {"tag": "c:plates/iron"}
    copper_plate = {"tag": "c:plates/copper"}
    brass_plate = {"tag": "c:plates/brass"}
    casing = {"item": "powergrid:conductive_casing"}
    heavy = {"item": "powergrid:heavy_wire_connector"}
    recipe("breaker_panel_200", ["III", "PCP", "IHI"],
           {"I": iron_plate, "P": copper_plate, "C": casing, "H": heavy}, 1, {"items": "powergrid:conductive_casing"})
    recipe("breaker_panel_400", ["PBP", "P2P", "PBP"],
           {"P": copper_plate, "B": brass_plate, "2": {"item": "%s:breaker_panel_200" % MOD}}, 1,
           {"items": "%s:breaker_panel_200" % MOD})
    recipe("breaker_panel_800", ["BIB", "I4I", "BIB"],
           {"B": brass_plate, "I": iron_plate, "4": {"item": "%s:breaker_panel_400" % MOD}}, 1,
           {"items": "%s:breaker_panel_400" % MOD})
    # Multi-lug panels: the single-lug panel of the same rating with a lug and bus bar per extra
    # line conductor (a heavy connector and a copper plate each).
    recipe("breaker_panel_200_2p", ["H2H", " P "],
           {"H": heavy, "P": copper_plate, "2": {"item": "%s:breaker_panel_200" % MOD}}, 1,
           {"items": "%s:breaker_panel_200" % MOD})
    recipe("breaker_panel_400_2p", ["H4H", " P "],
           {"H": heavy, "P": copper_plate, "4": {"item": "%s:breaker_panel_400" % MOD}}, 1,
           {"items": "%s:breaker_panel_400" % MOD})
    recipe("breaker_panel_400_3p", ["H4H", "PHP"],
           {"H": heavy, "P": copper_plate, "4": {"item": "%s:breaker_panel_400" % MOD}}, 1,
           {"items": "%s:breaker_panel_400" % MOD})
    recipe("breaker_panel_800_3p", ["H8H", "PHP"],
           {"H": heavy, "P": copper_plate, "8": {"item": "%s:breaker_panel_800" % MOD}}, 1,
           {"items": "%s:breaker_panel_800" % MOD})
    recipe("breaker_panel_800_2p", ["H8H", " P "],
           {"H": heavy, "P": copper_plate, "8": {"item": "%s:breaker_panel_800" % MOD}}, 1,
           {"items": "%s:breaker_panel_800" % MOD})
    recipe("breaker_panel_200_3p", ["H2H", "PHP"],
           {"H": heavy, "P": copper_plate, "2": {"item": "%s:breaker_panel_200" % MOD}}, 1,
           {"items": "%s:breaker_panel_200" % MOD})

    # Breakers: a column of iron, redstone and copper. The material tier sets the rating and the
    # double-width version of each tier is the next rating up.
    tiers = {
        50: ({"tag": "c:ingots/iron"}, {"tag": "c:ingots/copper"}, False, 2),
        200: (iron_plate, copper_plate, False, 1),
        400: (iron_plate, copper_plate, True, 1),
        800: ({"tag": "c:storage_blocks/iron"}, {"tag": "c:storage_blocks/copper"}, True, 1),
    }
    for rating, (iron, copper, wide, count) in tiers.items():
        pattern = ["II", "RR", "CC"] if wide else ["I", "R", "C"]
        recipe("breaker_%da" % rating, pattern, {"I": iron, "R": {"item": "minecraft:redstone"}, "C": copper},
               count, {"items": "minecraft:redstone"})
    # Multi-pole breakers: that many single-pole breakers tied together with an iron nugget.
    for poles in MULTI_POLES:
        for rating in BREAKERS:
            single = {"item": "%s:breaker_%da" % (MOD, rating)}
            recipe_shapeless("breaker_%da_%dp" % (rating, poles), [single] * poles + [{"tag": "c:nuggets/iron"}], 1,
                             {"items": "%s:breaker_%da" % (MOD, rating)})


def main():
    shared_textures()
    breaker_models()
    breaker_item_models()
    blank_and_lock()
    for rating in BREAKERS:
        breaker_item_texture(rating)
        for poles in MULTI_POLES:
            breaker_item_texture(rating, poles)
    for name, rating, slots, lugs, body, edge in PANELS:
        panel_textures(name, body, edge, lugs)
        panel_model(name, slots)
        loot_table(name)
    recipes()
    print("breaker panel assets written under", os.path.abspath(ROOT))


if __name__ == "__main__":
    main()
