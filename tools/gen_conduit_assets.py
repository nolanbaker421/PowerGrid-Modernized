#!/usr/bin/env python3
"""Generates the conduit resources: the conduit box block, conduit and conductor item textures and
models, loot, recipes and Power Grid wire types.

Run from the repository root:  python tools/gen_conduit_assets.py

Geometry mirrors ConduitBoxGeometry.java; change both. North frame: the box sits against the wall
at z = 16 with its cover facing north; a viewer at the front has +x on their left.
"""
import json
import math
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, recipe_shapeless, shade, write_png

# id suffix, conductors, tube thickness (blocks), knockout colour (mirrored in ConduitSize.java)
SIZES = [
    ("half", 4, 0.09, 0xF2F2F2),
    ("three_quarter", 8, 0.125, 0xE8C800),
    ("one", 12, 0.16, 0xD03030),
    ("one_quarter", 12, 0.17, 0x2F6FD6),
    ("one_half", 12, 0.19, 0x3AA048),
    ("two", 12, 0.22, 0xF08020),
    ("two_half", 12, 0.25, 0x9050C0),
    ("three", 12, 0.28, 0x7A4A24),
    ("four", 12, 0.32, 0x202020),
]
# The ceiling tile Power Grid draws, copied from its model so the box on a tile matches the tiles beside it.
TILE_TEXTURE = "powergrid:block/ceiling_tile/ceiling_tile"
TILE_ELEMENTS = [
    {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
        "north": {"uv": [8, 0, 16, 1], "texture": "#tile"}, "east": {"uv": [8, 1, 16, 2], "texture": "#tile"},
        "south": {"uv": [8, 2, 16, 3], "texture": "#tile"}, "west": {"uv": [8, 3, 16, 4], "texture": "#tile"},
        "up": {"uv": [8, 8, 0, 0], "texture": "#tile"}, "down": {"uv": [8, 8, 0, 16], "texture": "#tile"}}},
    {"from": [1, 1, 1], "to": [15, 0, 15], "faces": {
        "north": {"uv": [8, 0, 15, 0.5], "texture": "#tile"}, "east": {"uv": [8, 0, 15, 0.5], "texture": "#tile"},
        "south": {"uv": [8, 0, 15, 0.5], "texture": "#tile"}, "west": {"uv": [8, 0, 15, 0.5], "texture": "#tile"},
        "up": {"uv": [8.5, 8.5, 15.5, 15.5], "texture": "#tile"}, "down": {"uv": [8.5, 8.5, 15.5, 15.5], "texture": "#tile"}}},
]
TILE_LIFT = 2
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
# Every cover state, and the cover terminals each shows as nubs; the light socket's lamp holder and bulb (ConduitBoxGeometry).
COVERS = ("open", "blank", "node", "light_off", "light_on", "receptacle", "smoke", "smoke_alarm", "horn_off", "horn_on", "heat", "heat_alarm", "pull", "pull_pulled")
NUBS = {"open": 0, "blank": 0, "node": 12, "light_off": 2, "light_on": 2, "receptacle": 2, "smoke": 2, "smoke_alarm": 2, "horn_off": 2, "horn_on": 2,
        "heat": 2, "heat_alarm": 2, "pull": 2, "pull_pulled": 2}
PULL_BODY, PULL_HANDLE = (6, 5, 11, 10, 11, 13), {"pull": (6.5, 8, 10.3, 9.5, 9, 11), "pull_pulled": (6.5, 5.5, 10.3, 9.5, 6.5, 11)}
# The smoke detector's puck and light, the horn strobe's body and lens (ConduitBoxGeometry); textures from gen_fire_alarm_assets.py.
SMOKE_PUCK, SMOKE_LED = (5, 5, 11.5, 11, 11, 13), (7.5, 6, 11.2, 8.5, 7, 11.5)
HORN_BODY, HORN_LENS = (6, 4.5, 11, 10, 11.5, 13), (7, 8.5, 10.5, 9, 10.5, 11)
FIRE_TEX = {"detector": "%s:block/smoke_detector" % MOD, "led_green": "%s:block/led_green" % MOD, "led_red": "%s:block/led_red" % MOD,
            "horn": "%s:block/horn_strobe" % MOD, "lens_off": "%s:block/lens_off" % MOD, "lens_on": "%s:block/lens_on" % MOD,
            "heat": "%s:block/heat_detector" % MOD, "pull": "%s:block/pull_station" % MOD, "handle": "%s:block/pull_handle" % MOD}


def fire_elements(cover, transform=lambda b: b):
    """The device on a smoke detector or horn strobe cover, in the given frame."""
    if cover.startswith("smoke") or cover.startswith("heat"):
        puck = "#heat" if cover.startswith("heat") else "#detector"
        return [element(*transform(SMOKE_PUCK), puck), element(*transform(SMOKE_LED), "#led_red" if cover.endswith("alarm") else "#led_green")]
    if cover.startswith("horn"):
        return [element(*transform(HORN_BODY), "#horn"), element(*transform(HORN_LENS), "#lens_on" if cover == "horn_on" else "#lens_off")]
    if cover.startswith("pull"):
        return [element(*transform(PULL_BODY), "#pull"), element(*transform(PULL_HANDLE[cover]), "#handle")]
    return []
LAMP_HOLDER = (6.5, 6.5, 11, 9.5, 9.5, 13)
BULB = (6, 6, 8.5, 10, 10, 11)
RECEPTACLE_FACE = (6.5, 6, 11.5, 9.5, 10, 13)
ROWS_Y = [10, 8, 6]
NUB, NUB_Z1, NUB_Z2 = 0.6, 12.4, 13
# The facing points into the mount; the model's front (north in its frame) turns to the opposite way.
DIRECTIONAL = {
    "facing=south": {}, "facing=north": {"y": 180}, "facing=west": {"y": 90},
    "facing=east": {"y": 270}, "facing=down": {"x": 270}, "facing=up": {"x": 90},
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

    # The bulb of a light socket cover, dark and lit.
    for name, glass, core in (("lamp_off", (0xd8, 0xd6, 0xcc), (0xb0, 0xae, 0xa4)), ("lamp_on", (0xff, 0xf2, 0xb8), (0xff, 0xff, 0xe0))):
        bulb = canvas(16, 16, glass + (255,))
        fill(bulb, 4, 4, 12, 12, core)
        fill(bulb, 0, 0, 16, 1, shade(glass, 0.8))
        fill(bulb, 0, 15, 16, 16, shade(glass, 0.7))
        write_png(os.path.join(block, name + ".png"), bulb)

    item = os.path.join(ASSETS, "textures", "item")
    for i, (sid, _conductors, _thickness, _band) in enumerate(SIZES):
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
    # Knockout sheets: one flat colour per trade size, worn by every knockout that takes up to
    # that size. A knockout is a tiny element sampling a few pixels, so the colour fills the sheet.
    for sid, _conductors, _thickness, band in SIZES:
        ko = canvas(16, 16, rgb(band) + (255,))
        fill(ko, 0, 15, 16, 16, shade(rgb(band), 0.6))
        fill(ko, 0, 0, 1, 16, shade(rgb(band), 0.85))
        fill(ko, 15, 0, 16, 16, shade(rgb(band), 0.7))
        write_png(os.path.join(ASSETS, "textures", "block", "knockout_%s.png" % sid), ko)


# Luna-Terra's box: one element list per cover variant, imported by import_bbmodel.py from the
# .bbmodel, on a 32x32 texture. Body, nub grid and knockout positions match the geometry here.
LUNA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "conduit_box_luna.json")
LUNA_VARIANT = {"open": "open", "blank": "blank", "node": "node", "light_off": "light", "light_on": "light", "receptacle": "blank",
                "smoke": "blank", "smoke_alarm": "blank", "horn_off": "blank", "horn_on": "blank",
                "heat": "blank", "heat_alarm": "blank", "pull": "blank", "pull_pulled": "blank"}


def box_model():
    tex = {"box": "%s:block/conduit_box_detail" % MOD, "knockout": "%s:block/knockout_one_half" % MOD,
           "particle": "%s:block/conduit_box" % MOD, "plate": "%s:block/conduit_box" % MOD,
           "terminal": "%s:block/panel_terminal" % MOD, "receptacle": "%s:block/receptacle" % MOD}
    tex.update(FIRE_TEX)
    with open(LUNA, encoding="utf-8") as f:
        luna = json.load(f)["variants"]
    for cover in COVERS:
        elements = [dict(e) for e in luna[LUNA_VARIANT[cover]]]
        # Knockouts come from the terminal geometry, so a run meets its mark dead centre.
        for hub in HUBS:
            elements.append(element(*hub, "#knockout"))
        if cover == "receptacle":
            elements.append(receptacle_face(RECEPTACLE_FACE))
        elements += fire_elements(cover)
        if cover == "receptacle" or cover.startswith(("light", "smoke", "horn", "heat", "pull")):
            for screw in SCREWS:
                elements.append(element(*screw, "#terminal"))
        # Cutout: the detail texture has transparent pixels where the plate is open.
        dump(os.path.join(ASSETS, "models", "block", "conduit_box_%s.json" % cover),
             {"parent": "block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "conduit_box.json"), {"parent": "%s:block/conduit_box_open" % MOD})
    ceiling_box_models()
    variants = {}
    for key, rot in DIRECTIONAL.items():
        for cover in COVERS:
            v = {"model": "%s:block/conduit_box_%s" % (MOD, cover)}
            v.update(rot)
            variants["%s,cover=%s" % (key, cover)] = v
    dump(os.path.join(ASSETS, "blockstates", "conduit_box.json"), {"variants": variants})
    # Cover plate items.
    item = os.path.join(ASSETS, "textures", "item")
    plate = (0x86, 0x8a, 0x8e)
    for name, nubs in (("conduit_cover_blank", False), ("conduit_cover_node", True), ("conduit_cover_light", False), ("conduit_cover_receptacle", False)):
        img = canvas(16, 16)
        fill(img, 2, 2, 14, 14, plate)
        if name.endswith("light"):
            fill(img, 6, 4, 10, 9, (0xff, 0xf2, 0xb8))
            fill(img, 7, 9, 9, 12, shade(plate, 0.5))
        if name.endswith("receptacle"):
            fill(img, 5, 4, 11, 12, (0xe8, 0xe6, 0xdc))
            fill(img, 6, 6, 7, 9, (0x22, 0x22, 0x26))
            fill(img, 9, 6, 10, 9, (0x22, 0x22, 0x26))
            fill(img, 7, 10, 9, 11, (0x22, 0x22, 0x26))
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
    # The receptacle's face: an ivory block with two slots and a ground hole, drawn over the whole 16 px.
    face = canvas(16, 16, (0xe8, 0xe6, 0xdc, 255))
    fill(face, 0, 0, 16, 1, (0xf4, 0xf2, 0xea))
    fill(face, 0, 15, 16, 16, (0xb8, 0xb6, 0xac))
    fill(face, 4, 3, 6, 9, (0x22, 0x22, 0x26))
    fill(face, 10, 3, 12, 9, (0x22, 0x22, 0x26))
    fill(face, 7, 10, 9, 13, (0x22, 0x22, 0x26))
    write_png(os.path.join(ASSETS, "textures", "block", "receptacle.png"), face)


def receptacle_face(box):
    """The face element, textured with the receptacle on its front and the plate colour elsewhere."""
    e = element(*box, "#plate")
    e["faces"]["north"] = {"uv": [0, 0, 16, 16], "texture": "#receptacle"}
    return e


# The screw terminals beside a light fixture or a receptacle; mirrors ConduitBoxGeometry.SCREW_LINE / SCREW_NEUTRAL.
SCREWS = [(10, 7, 12, 11, 9, 13), (5, 7, 12, 6, 9, 13)]


# Floor frame (FACING = down): body on the floor, socket face up, knockout on the north edge.
SOCKET_BODY = (5, 0, 5, 11, 3, 11)
SOCKET_HUB = (7, 0.5, 4, 9, 2.5, 5)
SOCKET_HUB2 = (7, 0.5, 11, 9, 2.5, 12)
SOCKET_FACE = (6.5, 3, 6.5, 9.5, 4, 9.5)
# Same rotation table Power Grid's Rotation4 blocks use (copied from the line ammeter): block_v for
# floor and ceiling, block_h = (16 - y, x, z) of block_v for walls.
ROTATION4 = {k: ("v" if v["model"] == "v" else "h", {a: v[a] for a in ("x", "y") if v[a]})
             for k, v in __import__("json").load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "rotation4.json"))).items()}


def to_h(box):
    x1, y1, z1, x2, y2, z2 = box
    return (16 - y2, x1, z1, 16 - y1, x2, z2)


def on_tile(box):
    """North frame to the tile frame, as CeilingConduitBoxBlock.ON_TILE: the back wall becomes the tile's top."""
    x1, y1, z1, x2, y2, z2 = box
    return (x1, 16 + TILE_LIFT - z2, y1, x2, 16 + TILE_LIFT - z1, y2)


def ceiling_box_models():
    """The box on a ceiling tile: the tile, then the box in the tile frame, one model per cover."""
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD,
           "open": "%s:block/conduit_box_open" % MOD, "tile": TILE_TEXTURE, "knockout": "%s:block/knockout_one_half" % MOD,
           "particle": "%s:block/conduit_box" % MOD, "lamp_off": "%s:block/lamp_off" % MOD, "lamp_on": "%s:block/lamp_on" % MOD,
           "receptacle": "%s:block/receptacle" % MOD}
    tex.update(FIRE_TEX)
    for cover in COVERS:
        elements = [dict(e) for e in TILE_ELEMENTS]
        if cover == "open":
            x1, y1, z1, x2, y2, z2 = BODY
            body = element(*on_tile((x1, y1, z1 + 0.5, x2, y2, z2)), "#box")
            body["faces"]["up"]["texture"] = "#open"
        else:
            body = element(*on_tile(BODY), "#box")
        elements.append(body)
        for hub in HUBS:
            elements.append(element(*on_tile(hub), "#knockout"))
        for k in range(NUBS[cover]):
            x = 16 - COLUMNS_U[k % 4]
            y = ROWS_Y[k // 4]
            elements.append(element(*on_tile((x - NUB, y - NUB, NUB_Z1, x + NUB, y + NUB, NUB_Z2)), "#terminal"))
        if cover.startswith("light"):
            elements.append(element(*on_tile(LAMP_HOLDER), "#box"))
        if cover == "receptacle":
            e = element(*on_tile(RECEPTACLE_FACE), "#box")
            e["faces"]["up"] = {"uv": [0, 0, 16, 16], "texture": "#receptacle"}
            elements.append(e)
        elements += fire_elements(cover, on_tile)
        dump(os.path.join(ASSETS, "models", "block", "ceiling_tile_conduit_box_%s.json" % cover),
             {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "blockstates", "ceiling_tile_conduit_box.json"),
         {"variants": {"cover=%s" % cover: {"model": "%s:block/ceiling_tile_conduit_box_%s" % (MOD, cover)} for cover in COVERS}})
    # Breaking it drops the tile and the box.
    dump(os.path.join(DATA, "loot_table", "blocks", "ceiling_tile_conduit_box.json"), {
        "type": "minecraft:block",
        "pools": [{
            "bonus_rolls": 0.0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "powergrid:ceiling_tile"}, {"type": "minecraft:item", "name": "%s:conduit_box" % MOD}],
            "rolls": 1.0,
        }],
        "random_sequence": "%s:blocks/ceiling_tile_conduit_box" % MOD,
    })


def socket_model():
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_one" % MOD, "particle": "%s:block/conduit_box" % MOD}
    boxes = [(SOCKET_BODY, "#box"), (SOCKET_HUB, "#knockout"), (SOCKET_HUB2, "#knockout"), (SOCKET_FACE, "#terminal")]
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
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_one" % MOD, "particle": "%s:block/conduit_box" % MOD}
    for on in (False, True):
        boxes = [(SOCKET_BODY, "#box"), (SOCKET_HUB, "#knockout"), (SOCKET_HUB2, "#knockout"), (SWITCH_PLATE, "#box"), (SWITCH_TOGGLE[on], "#terminal")]
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


PULL_BOX_KNOCKOUTS = {"h": [(5, 8), (11, 8)], "v": [(8, 5), (8, 11)]}


BUTTON_CAP = {False: (7, 3.5, 7, 9, 5, 9), True: (7, 3.5, 7, 9, 4.2, 9)}


def button_model():
    """The conduit button: the switch's body with a round cap on the plate that sits down while pressed."""
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_one" % MOD, "particle": "%s:block/conduit_box" % MOD}
    for on in (False, True):
        boxes = [(SOCKET_BODY, "#box"), (SOCKET_HUB, "#knockout"), (SOCKET_HUB2, "#knockout"), (SWITCH_PLATE, "#box"), (BUTTON_CAP[on], "#terminal")]
        for suffix, transform in (("v", lambda b: b), ("h", to_h)):
            elements = [element(*transform(box), texture) for box, texture in boxes]
            dump(os.path.join(ASSETS, "models", "block", "conduit_button", "block_%s_%s.json" % (suffix, "on" if on else "off")),
                 {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "conduit_button.json"), {"parent": "%s:block/conduit_button/block_v_off" % MOD})
    variants = {}
    for key, (suffix, rot) in ROTATION4.items():
        for on in (False, True):
            v = {"model": "%s:block/conduit_button/block_%s_%s" % (MOD, suffix, "on" if on else "off")}
            v.update(rot)
            variants[key + ",on=" + ("true" if on else "false")] = v
    dump(os.path.join(ASSETS, "blockstates", "conduit_button.json"), {"variants": variants})


THREE_WAY_MARK = (9.2, 3.5, 6.2, 9.8, 3.7, 9.8)


def three_way_model():
    """The 3-way switch: the switch with a stripe down the plate's edge so the two tell apart; the toggle shows which traveller."""
    tex = {"box": "%s:block/conduit_box" % MOD, "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_one" % MOD,
           "mark": "%s:block/knockout_one" % MOD, "particle": "%s:block/conduit_box" % MOD}
    for on in (False, True):
        boxes = [(SOCKET_BODY, "#box"), (SOCKET_HUB, "#knockout"), (SOCKET_HUB2, "#knockout"), (SWITCH_PLATE, "#box"), (THREE_WAY_MARK, "#mark"), (SWITCH_TOGGLE[on], "#terminal")]
        for suffix, transform in (("v", lambda b: b), ("h", to_h)):
            elements = [element(*transform(box), texture) for box, texture in boxes]
            dump(os.path.join(ASSETS, "models", "block", "conduit_switch_3way", "block_%s_%s.json" % (suffix, "on" if on else "off")),
                 {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "conduit_switch_3way.json"), {"parent": "%s:block/conduit_switch_3way/block_v_off" % MOD})
    variants = {}
    for key, (suffix, rot) in ROTATION4.items():
        for on in (False, True):
            v = {"model": "%s:block/conduit_switch_3way/block_%s_%s" % (MOD, suffix, "on" if on else "off")}
            v.update(rot)
            variants[key + ",on=" + ("true" if on else "false")] = v
    dump(os.path.join(ASSETS, "blockstates", "conduit_switch_3way.json"), {"variants": variants})


ITEM_TEX_DIR = os.path.join(ASSETS, "textures", "item")


def gang_hubs(gangs):
    left = 8 - 2 * gangs
    hubs = [(7, 0.5, 4, 9, 2.5, 5), (7, 0.5, 11, 9, 2.5, 12)]
    if gangs == 3:
        hubs += [(left - 1, 0.5, 7, left, 2.5, 9), (16 - left, 0.5, 7, 17 - left, 2.5, 9)]
    return hubs


def gang_box_models():
    """Gang boxes: a body four pixels a gang wide, a plate on top, knockouts north and south (east and west on the 3-gang)."""
    tex = {"box": "%s:block/conduit_box" % MOD, "knockout": "%s:block/knockout_one" % MOD, "particle": "%s:block/conduit_box" % MOD}
    for gangs in range(1, 5):
        left = 8 - 2 * gangs
        body = (left, 0, 5, 16 - left, 3, 11)
        plate = (left + 0.5, 3, 5.5, 16 - left - 0.5, 3.5, 10.5)
        boxes = [(body, "#box"), (plate, "#box")] + [(hub, "#knockout") for hub in gang_hubs(gangs)]
        name = "gang_box_%d" % gangs
        for suffix, transform in (("v", lambda b: b), ("h", to_h)):
            elements = [element(*transform(box), texture) for box, texture in boxes]
            dump(os.path.join(ASSETS, "models", "block", name, "block_%s.json" % suffix), {"parent": "block/block", "textures": tex, "elements": elements})
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s/block_v" % (MOD, name)})
        variants = {}
        for key, (suffix, rot) in ROTATION4.items():
            v = {"model": "%s:block/%s/block_%s" % (MOD, name, suffix)}
            v.update(rot)
            variants[key] = v
        dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def gang_device_items():
    """Item icons for the gang devices: a plate with the device on it."""
    plate = (0xd8, 0xd8, 0xd8)
    dark = (0x2a, 0x2a, 0x2a)
    for device in ("blank", "switch", "switch_3way", "switch_4way", "button", "receptacle", "pull_station", "horn_strobe", "monitor_module", "annunciator"):
        img = canvas(16, 16)
        fill(img, 3, 1, 13, 15, plate)
        fill(img, 3, 14, 13, 15, shade(plate, 0.7))
        fill(img, 12, 1, 13, 15, shade(plate, 0.8))
        if device.startswith("switch"):
            fill(img, 6, 4, 10, 12, (0xa0, 0xa0, 0xa0))
            fill(img, 7, 5, 9, 8, (0xf4, 0xf4, 0xf4))        # the toggle, up
            marks = {"switch": 0, "switch_3way": 3, "switch_4way": 4}[device]
            for i in range(marks):
                fill(img, 4 + 2 * i, 13, 5 + 2 * i, 14, dark)
        elif device == "button":
            fill(img, 5, 5, 11, 11, (0x40, 0x40, 0x40))
            fill(img, 6, 6, 10, 10, (0xc8, 0x30, 0x30))
            fill(img, 6, 6, 10, 7, (0xe0, 0x50, 0x50))
        elif device == "receptacle":
            face = (0xe8, 0xe4, 0xda)
            fill(img, 5, 3, 11, 13, face)
            for y in (4, 9):
                fill(img, 6, y, 7, y + 2, dark)
                fill(img, 9, y, 10, y + 2, dark)
                fill(img, 7, y + 3, 9, y + 4, dark)
        elif device == "pull_station":
            fill(img, 5, 3, 11, 13, (0xb3, 0x1b, 0x1b))
            fill(img, 6, 6, 10, 7, (0xf0, 0xf0, 0xec))
            fill(img, 7, 7, 9, 11, (0xf0, 0xf0, 0xec))
        elif device == "horn_strobe":
            fill(img, 5, 3, 11, 13, (0xb3, 0x1b, 0x1b))
            fill(img, 6, 4, 10, 7, (0xff, 0xff, 0xf0))
            for y in range(8, 12, 2):
                fill(img, 6, y, 10, y + 1, (0x60, 0x10, 0x10))
        elif device == "monitor_module":
            fill(img, 5, 3, 11, 13, (0x9a, 0x9a, 0x9a))
            fill(img, 7, 4, 9, 6, (0x30, 0xe0, 0x50))        # the status light
            fill(img, 6, 9, 7, 11, (0xc8, 0xa0, 0x00))        # the two contact screws
            fill(img, 9, 9, 10, 11, (0xc8, 0xa0, 0x00))
        elif device == "annunciator":
            fill(img, 2, 3, 14, 13, (0x30, 0x30, 0x34))
            for x, c in ((4, (0x30, 0xe0, 0x50)), (7, (0x60, 0x20, 0x20)), (10, (0x60, 0x50, 0x20))):
                fill(img, x, 4, x + 2, 5, c)
            fill(img, 3, 7, 13, 10, (0x10, 0x14, 0x18))
            fill(img, 4, 8, 10, 9, (0x40, 0xe0, 0x60))        # a line of text
            fill(img, 3, 11, 7, 12, (0xc8, 0xa0, 0x00))        # the two keys
            fill(img, 9, 11, 13, 12, (0x30, 0xa0, 0x50))
        write_png(os.path.join(ITEM_TEX_DIR, "gang_%s.png" % device), img)
        dump(os.path.join(ASSETS, "models", "item", "gang_%s.json" % device), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/gang_%s" % (MOD, device)}})


def pull_box_assets():
    """A full-block grey cabinet: a door with a handle on the front, two flush knockouts on every other face."""
    block = os.path.join(ASSETS, "textures", "block")
    body = (0x78, 0x7c, 0x82)

    def base():
        img = canvas(16, 16, body + (255,))
        fill(img, 0, 0, 16, 1, shade(body, 1.15))
        fill(img, 0, 15, 16, 16, shade(body, 0.6))
        fill(img, 0, 0, 1, 16, shade(body, 0.85))
        fill(img, 15, 0, 16, 16, shade(body, 0.6))
        return img

    for suffix, spots in PULL_BOX_KNOCKOUTS.items():
        img = base()
        for cx, cy in spots:
            fill(img, cx - 1, cy - 1, cx + 2, cy + 2, shade(body, 0.45))
            fill(img, cx, cy, cx + 1, cy + 1, shade(body, 0.8))
        write_png(os.path.join(block, "pull_box_side_%s.png" % suffix), img)
    front = base()
    fill(front, 2, 2, 14, 14, shade(body, 1.05))
    fill(front, 2, 2, 3, 14, shade(body, 0.7))
    fill(front, 11, 7, 12, 10, shade(body, 0.4))
    fill(front, 12, 7, 13, 10, shade(body, 0.55))
    write_png(os.path.join(block, "pull_box_front.png"), front)
    side_h = "%s:block/pull_box_side_h" % MOD
    side_v = "%s:block/pull_box_side_v" % MOD
    dump(os.path.join(ASSETS, "models", "block", "pull_box.json"), {
        "parent": "block/cube",
        "textures": {"north": "%s:block/pull_box_front" % MOD, "south": side_h, "up": side_h, "down": side_h,
                     "east": side_v, "west": side_v, "particle": side_h},
    })
    dump(os.path.join(ASSETS, "models", "item", "pull_box.json"), {"parent": "%s:block/pull_box" % MOD})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/pull_box" % MOD}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", "pull_box.json"), {"variants": variants})

    # Terminal cabinet: the same cabinet with twelve lugs on the door for big wire to land on.
    elements = [element(0, 0, 0, 16, 16, 16, "#side")]
    elements[0]["faces"]["north"]["texture"] = "#front"
    elements[0]["faces"]["east"]["texture"] = "#side_v"
    elements[0]["faces"]["west"]["texture"] = "#side_v"
    for u in (3.5, 6.5, 9.5, 12.5):
        x = 16 - u
        for y in (11.5, 8, 4.5):
            elements.append(element(x - 1, y - 1, -1, x + 1, y + 1, 0, "#knockout"))
    dump(os.path.join(ASSETS, "models", "block", "terminal_cabinet.json"), {
        "parent": "block/block",
        "textures": {"front": "%s:block/pull_box_front" % MOD, "side": side_h, "side_v": side_v,
                     "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_four" % MOD, "particle": side_h},
        "elements": elements,
    })
    dump(os.path.join(ASSETS, "models", "item", "terminal_cabinet.json"), {"parent": "%s:block/terminal_cabinet" % MOD})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/terminal_cabinet" % MOD}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", "terminal_cabinet.json"), {"variants": variants})


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
    shaped("conduit_button", ["NBN", " N "], {"N": nugget, "B": {"item": "minecraft:stone_button"}}, 2, {"items": "powergrid:wire"})
    # Gang boxes: an iron plate a gang on a nugget frame.
    plate = {"tag": "c:plates/iron"}
    shaped("gang_box_1", ["NPN"], {"N": nugget, "P": plate}, 1, {"items": "powergrid:wire"})
    shaped("gang_box_2", ["PP", "NN"], {"N": nugget, "P": plate}, 1, {"items": "%s:gang_box_1" % MOD})
    shaped("gang_box_3", ["PPP", "NNN"], {"N": nugget, "P": plate}, 1, {"items": "%s:gang_box_1" % MOD})
    shaped("gang_box_4", ["PPP", "PNN"], {"N": nugget, "P": plate}, 1, {"items": "%s:gang_box_1" % MOD})
    # Devices.
    lever = {"item": "minecraft:lever"}
    recipe_shapeless("gang_blank", [nugget, nugget], 2, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_switch", [lever, nugget], 1, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_switch_3way", [lever, lever, nugget], 1, {"items": "%s:gang_switch" % MOD})
    recipe_shapeless("gang_switch_4way", [lever, lever, lever, nugget], 1, {"items": "%s:gang_switch_3way" % MOD})
    recipe_shapeless("gang_button", [{"item": "minecraft:stone_button"}, nugget], 1, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_receptacle", [{"tag": "c:ingots/copper"}, nugget, nugget], 1, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_pull_station", [nugget, {"item": "minecraft:redstone"}, {"tag": "c:dyes/red"}], 1, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_horn_strobe", [nugget, {"item": "minecraft:redstone"}, {"tag": "c:dyes/red"}, {"tag": "c:nuggets/copper"}], 1, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_monitor_module", [nugget, {"item": "minecraft:redstone"}, {"tag": "c:dyes/red"}, {"item": "minecraft:redstone_torch"}], 1, {"items": "%s:gang_box_1" % MOD})
    recipe_shapeless("gang_annunciator", [nugget, nugget, {"item": "minecraft:redstone"}, {"tag": "c:dyes/red"}, {"tag": "c:glass_panes"}, {"tag": "c:nuggets/copper"}], 1, {"items": "%s:gang_box_3" % MOD})
    # The old single blocks turn into their device.
    for old, device in (("conduit_switch", "gang_switch"), ("conduit_button", "gang_button"), ("conduit_switch_3way", "gang_switch_3way"), ("conduit_socket", "gang_receptacle")):
        dump(os.path.join(DATA, "recipe", "crafting", device + "_from_" + old + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "misc",
            "ingredients": [{"item": "%s:%s" % (MOD, old)}],
            "result": {"count": 1, "id": "%s:%s" % (MOD, device)},
        })
    shaped("conduit_switch_3way", ["NLN", "LNL"], {"N": nugget, "L": {"item": "minecraft:lever"}}, 2, {"items": "%s:conduit_switch" % MOD})
    shaped("pull_box", ["PPP", "P P", "PPP"], {"P": {"tag": "c:plates/iron"}}, 1, {"items": "powergrid:wire"})
    shaped("terminal_cabinet", ["NNN", "NBN", "NNN"], {"N": {"tag": "c:nuggets/copper"}, "B": {"item": "%s:pull_box" % MOD}}, 1,
           {"items": "%s:pull_box" % MOD})
    recipe_shapeless("conduit_cover_light", [{"item": "%s:conduit_cover_blank" % MOD}, {"tag": "c:nuggets/copper"}, {"item": "minecraft:glass_pane"}], 1,
                     {"items": "%s:conduit_cover_blank" % MOD})
    recipe_shapeless("conduit_cover_receptacle", [{"item": "%s:conduit_cover_blank" % MOD}, {"tag": "c:nuggets/copper"}, {"tag": "c:nuggets/copper"}], 1,
                     {"items": "%s:conduit_cover_blank" % MOD})


def wire_types():
    for sid, _conductors, thickness, _band in SIZES:
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
    button_model()
    three_way_model()
    gang_box_models()
    gang_device_items()
    pull_box_assets()
    item_models()
    loot_table("conduit_box")
    loot_table("conduit_socket")
    loot_table("conduit_switch")
    loot_table("conduit_button")
    loot_table("conduit_switch_3way")
    for gangs in range(1, 5):
        loot_table("gang_box_%d" % gangs)
    loot_table("pull_box")
    loot_table("terminal_cabinet")
    recipes()
    wire_types()
    print("conduit assets written")


if __name__ == "__main__":
    main()
