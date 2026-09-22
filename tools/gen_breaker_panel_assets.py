#!/usr/bin/env python3
"""Generates the breaker panel resources (models, blockstates, textures, loot, recipes).

Run from the repository root:  python tools/gen_breaker_panel_assets.py

Geometry here mirrors PanelLayout.java; change both together. Everything is written in the
north-facing frame: the enclosure sits against the south side of the block (z 10..16) with its
open front facing north. A viewer looking at the front sees +x on their left, so viewer
coordinate u maps to model x = 16 - u.
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
MOD = "powergrid_modernized"
ASSETS = os.path.join(ROOT, "assets", MOD)
DATA = os.path.join(ROOT, "data", MOD)

# Panel specs: id, rating, branch slots, enclosure colours (body, edge)
PANELS = [
    ("breaker_panel_200", 200, 8, (0x9d, 0x9d, 0x9d), (0x6b, 0x6b, 0x6b)),
    ("breaker_panel_400", 400, 12, (0x7f, 0x87, 0x94), (0x4f, 0x55, 0x63)),
    ("breaker_panel_800", 800, 12, (0x5c, 0x5c, 0x5c), (0x33, 0x33, 0x33)),
]
BREAKERS = [10, 20, 50, 60, 100, 200, 400, 800]

# Layout constants (pixels, north frame) - keep in sync with PanelLayout.java
BODY = (1, 1, 10, 15, 15, 16)
FRONT_Z = 10
HUB_U = [3.5, 6.5, 9.5, 12.5]
HUB_Z1, HUB_Z2 = 12.5, 14.5
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
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "8": ["111", "101", "111", "101", "111"],
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

def panel_textures(name, body, edge):
    tex = os.path.join(ASSETS, "textures", "block")
    # Front: enclosure interior. Drawn as the viewer sees it (u left->right, v top->bottom).
    front = canvas(16, 16, body + (255,))
    interior = shade(body, 0.82)
    fill(front, 1, 0, 15, 16, interior)
    fill(front, 1, 0, 15, 1, edge)
    fill(front, 1, 15, 15, 16, edge)
    fill(front, 1, 0, 2, 16, edge)
    fill(front, 14, 0, 15, 16, edge)
    # Vertical bus bars behind the breaker columns (copper), and the mounting rails.
    copper = (0xb8, 0x73, 0x33)
    fill(front, 7, 2, 8, 14, copper)
    fill(front, 8, 2, 9, 14, shade(copper, 0.8))
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


def breaker_item_texture(rating):
    img = canvas(16, 16)
    black = (0x1c, 0x1c, 0x1c)
    fill(img, 0, 3, 16, 13, black)
    fill(img, 0, 3, 16, 4, shade(black, 1.8))
    fill(img, 0, 12, 16, 13, shade(black, 0.5))
    fill(img, 1, 5, 15, 11, (0x2e, 0x2e, 0x2e))
    fill(img, 11, 4, 15, 12, (0x11, 0x11, 0x11))
    fill(img, 12, 6, 14, 10, (0x3c, 0x3c, 0x3c))
    fill(img, 12, 7, 13, 9, (0xe8, 0xe8, 0xe8))
    text(img, 1, 6, str(rating), (0xe8, 0xe8, 0xe8))
    write_png(os.path.join(ASSETS, "textures", "item", "breaker_%da.png" % rating), img)


# ---------------------------------------------------------------- models

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
        f = {"uv": uv, "texture": texture}
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
    for rating in BREAKERS:
        dump(os.path.join(ASSETS, "models", "item", "breaker_%da.json" % rating), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "%s:item/breaker_%da" % (MOD, rating)},
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


def recipe(name, pattern, key, count, unlock_item):
    dump(os.path.join(DATA, "recipe", "crafting", name + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": key,
        "pattern": pattern,
        "result": {"count": count, "id": "%s:%s" % (MOD, name)},
    })
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

    # Breakers: a column of iron, redstone and copper. The material tier sets the rating and the
    # double-width version of each tier is the next rating up.
    tiers = {
        10: ({"tag": "c:nuggets/iron"}, {"tag": "c:nuggets/copper"}, False, 2),
        20: ({"tag": "c:nuggets/iron"}, {"tag": "c:nuggets/copper"}, True, 2),
        50: ({"tag": "c:ingots/iron"}, {"tag": "c:ingots/copper"}, False, 1),
        60: ({"tag": "c:ingots/iron"}, {"tag": "c:ingots/copper"}, True, 1),
        100: (iron_plate, copper_plate, False, 1),
        200: (iron_plate, copper_plate, True, 1),
        400: ({"tag": "c:storage_blocks/iron"}, {"tag": "c:storage_blocks/copper"}, False, 1),
        800: ({"tag": "c:storage_blocks/iron"}, {"tag": "c:storage_blocks/copper"}, True, 1),
    }
    for rating, (iron, copper, wide, count) in tiers.items():
        pattern = ["II", "RR", "CC"] if wide else ["I", "R", "C"]
        recipe("breaker_%da" % rating, pattern, {"I": iron, "R": {"item": "minecraft:redstone"}, "C": copper},
               count, {"items": "minecraft:redstone"})


def main():
    shared_textures()
    breaker_models()
    breaker_item_models()
    blank_and_lock()
    for rating in BREAKERS:
        breaker_item_texture(rating)
    for name, rating, slots, body, edge in PANELS:
        panel_textures(name, body, edge)
        panel_model(name, slots)
        loot_table(name)
    recipes()
    print("breaker panel assets written under", os.path.abspath(ROOT))


if __name__ == "__main__":
    main()
