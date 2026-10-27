#!/usr/bin/env python3
"""Generates the fire alarm resources: the fire alarm panel's textures, model, blockstate, item,
loot and recipe; the textures the smoke detector and horn strobe covers are built from (their box
models come out of gen_conduit_assets.py); the two cover plate items; and sounds.json.

Run from the repository root:  python tools/gen_fire_alarm_assets.py

Geometry mirrors FireAlarmPanelBlock.java; change both. North frame: the body on the wall at
z = 11..16 with its door to the north; a viewer at the front has +x on their left.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, recipe, recipe_shapeless, shade, write_png

RED = (0xb3, 0x1b, 0x1b)
DOOR = (0xc4, 0x24, 0x24)
BODY = (3, 1, 11, 13, 15, 16)
HUB_U = [5.5, 10.5]
HUBS = []
for _u in HUB_U:
    _x = 16 - _u
    HUBS += [(_x - 1, 15, 12.5, _x + 1, 16, 14.5), (_x - 1, 0, 12.5, _x + 1, 1, 14.5)]
# Lights at x = 11, 8, 5 and y = 12.5 (FireAlarmPanelRenderer); buttons low on the door.
LIGHT_U = [16 - 11, 16 - 8, 16 - 5]
LIGHT_V = 16 - 12.5


def panel_textures():
    block = os.path.join(ASSETS, "textures", "block")
    front = canvas(16, 16, RED + (255,))
    # The door, inset a pixel inside the frame, with a lighter top edge and a darker bottom.
    fill(front, 4, 2, 12, 14, DOOR)
    fill(front, 4, 2, 12, 3, shade(DOOR, 1.2))
    fill(front, 4, 13, 12, 14, shade(DOOR, 0.7))
    # A white label strip across the top, and the holes the lights sit in.
    fill(front, 5, 1, 11, 2, (0xf0, 0xf0, 0xec))
    for u in LIGHT_U:
        fill(front, int(u) - 1, int(LIGHT_V) - 1, int(u) + 1, int(LIGHT_V) + 1, (0x30, 0x30, 0x30))
    # Silence on the viewer's left, Reset on the right: two square buttons.
    fill(front, 4, 9, 7, 12, (0xe0, 0xe0, 0xd8))
    fill(front, 4, 11, 7, 12, (0xa0, 0xa0, 0x98))
    fill(front, 9, 9, 12, 12, (0xe0, 0xe0, 0xd8))
    fill(front, 9, 11, 12, 12, (0xa0, 0xa0, 0x98))
    fill(front, 5, 10, 6, 11, (0xd0, 0xa0, 0x20))
    fill(front, 10, 10, 11, 11, (0x30, 0xb0, 0x40))
    write_png(os.path.join(block, "fire_alarm_panel_front.png"), front)
    side = canvas(16, 16, RED + (255,))
    fill(side, 0, 0, 16, 1, shade(RED, 1.15))
    fill(side, 0, 15, 16, 16, shade(RED, 0.6))
    fill(side, 0, 0, 1, 16, shade(RED, 0.85))
    fill(side, 15, 0, 16, 16, shade(RED, 0.7))
    write_png(os.path.join(block, "fire_alarm_panel_side.png"), side)


def panel_model():
    tex = {"front": "%s:block/fire_alarm_panel_front" % MOD, "side": "%s:block/fire_alarm_panel_side" % MOD,
           "knockout": "%s:block/knockout_one" % MOD, "particle": "%s:block/fire_alarm_panel_side" % MOD}
    body = element(*BODY, "#side")
    body["faces"]["north"]["texture"] = "#front"
    elements = [body] + [element(*hub, "#knockout") for hub in HUBS]
    dump(os.path.join(ASSETS, "models", "block", "fire_alarm_panel.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "fire_alarm_panel.json"), {"parent": "%s:block/fire_alarm_panel" % MOD})
    dump(os.path.join(ASSETS, "blockstates", "fire_alarm_panel.json"), {"variants": {
        "facing=north": {"model": "%s:block/fire_alarm_panel" % MOD},
        "facing=east": {"model": "%s:block/fire_alarm_panel" % MOD, "y": 90},
        "facing=south": {"model": "%s:block/fire_alarm_panel" % MOD, "y": 180},
        "facing=west": {"model": "%s:block/fire_alarm_panel" % MOD, "y": 270},
    }})
    dump(os.path.join(DATA, "loot_table", "blocks", "fire_alarm_panel.json"), {
        "type": "minecraft:block",
        "pools": [{"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "%s:fire_alarm_panel" % MOD}], "rolls": 1.0}],
        "random_sequence": "%s:blocks/fire_alarm_panel" % MOD,
    })
    recipe("fire_alarm_panel", ["PDP", "PRP", "PPP"], {"P": {"tag": "c:plates/iron"}, "D": {"tag": "c:dyes/red"}, "R": {"item": "minecraft:redstone"}}, 1,
           {"items": "%s:conduit_box" % MOD})


def cover_textures():
    """The faces of the smoke detector and the horn strobe on their cover plates, and their lights and lens."""
    block = os.path.join(ASSETS, "textures", "block")
    white = (0xf2, 0xf2, 0xee)
    puck = canvas(16, 16, white + (255,))
    for i in range(2, 14, 3):
        fill(puck, 2, i, 14, i + 1, shade(white, 0.85))
    fill(puck, 0, 15, 16, 16, shade(white, 0.7))
    write_png(os.path.join(block, "smoke_detector.png"), puck)
    for name, rgb in (("led_green", (0x30, 0xe0, 0x50)), ("led_red", (0xff, 0x20, 0x20))):
        write_png(os.path.join(block, name + ".png"), canvas(16, 16, rgb + (255,)))
    horn = canvas(16, 16, RED + (255,))
    for y in range(2, 14, 2):
        fill(horn, 2, y, 14, y + 1, shade(RED, 0.6))
    fill(horn, 0, 15, 16, 16, shade(RED, 0.6))
    write_png(os.path.join(block, "horn_strobe.png"), horn)
    write_png(os.path.join(block, "lens_off.png"), canvas(16, 16, (0xc8, 0xc8, 0xc0, 255)))
    write_png(os.path.join(block, "lens_on.png"), canvas(16, 16, (0xff, 0xff, 0xf0, 255)))
    # The heat detector: the same puck with a red ring; the pull station: red with a white handle.
    heat = canvas(16, 16, white + (255,))
    for i in range(2, 14, 3):
        fill(heat, 2, i, 14, i + 1, shade(white, 0.85))
    fill(heat, 1, 1, 15, 2, RED)
    fill(heat, 1, 14, 15, 15, RED)
    fill(heat, 1, 1, 2, 15, RED)
    fill(heat, 14, 1, 15, 15, RED)
    fill(heat, 0, 15, 16, 16, shade(white, 0.7))
    write_png(os.path.join(block, "heat_detector.png"), heat)
    pull = canvas(16, 16, RED + (255,))
    fill(pull, 0, 0, 16, 1, shade(RED, 1.2))
    fill(pull, 0, 15, 16, 16, shade(RED, 0.6))
    fill(pull, 3, 3, 13, 5, (0xf0, 0xf0, 0xec))
    write_png(os.path.join(block, "pull_station.png"), pull)
    write_png(os.path.join(block, "pull_handle.png"), canvas(16, 16, (0xf0, 0xf0, 0xec, 255)))


def cover_items():
    item = os.path.join(ASSETS, "textures", "item")
    plate = (0x86, 0x8a, 0x8e)
    for name in ("conduit_cover_smoke", "conduit_cover_horn", "conduit_cover_heat", "conduit_cover_pull"):
        img = canvas(16, 16)
        fill(img, 2, 2, 14, 14, plate)
        fill(img, 2, 2, 14, 3, shade(plate, 1.2))
        fill(img, 2, 13, 14, 14, shade(plate, 0.6))
        if name.endswith(("smoke", "heat")):
            fill(img, 5, 5, 11, 11, (0xf2, 0xf2, 0xee))
            fill(img, 6, 6, 10, 10, (0xd8, 0xd8, 0xd2))
            fill(img, 7, 7, 9, 9, (0xf2, 0xf2, 0xee))
            fill(img, 7, 10, 8, 11, (0x30, 0xe0, 0x50))
            if name.endswith("heat"):
                fill(img, 5, 5, 11, 6, RED)
                fill(img, 5, 10, 11, 11, RED)
                fill(img, 5, 5, 6, 11, RED)
                fill(img, 10, 5, 11, 11, RED)
        elif name.endswith("pull"):
            fill(img, 5, 4, 11, 12, RED)
            fill(img, 6, 7, 10, 8, (0xf0, 0xf0, 0xec))
            fill(img, 7, 8, 9, 11, (0xf0, 0xf0, 0xec))
        else:
            fill(img, 5, 4, 11, 12, RED)
            fill(img, 6, 5, 10, 8, (0xff, 0xff, 0xf0))
            for y in range(9, 12):
                fill(img, 6, y, 10, y + 1, shade(RED, 0.6) if y % 2 else RED)
        write_png(os.path.join(item, name + ".png"), img)
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, name)}})
    blank = {"item": "%s:conduit_cover_blank" % MOD}
    recipe_shapeless("conduit_cover_smoke", [blank, {"item": "minecraft:redstone"}, {"item": "minecraft:glass_pane"}, {"tag": "c:nuggets/copper"}], 1,
                     {"items": "%s:conduit_cover_blank" % MOD})
    recipe_shapeless("conduit_cover_horn", [blank, {"item": "minecraft:redstone"}, {"tag": "c:dyes/red"}, {"tag": "c:nuggets/copper"}], 1,
                     {"items": "%s:conduit_cover_blank" % MOD})
    recipe_shapeless("conduit_cover_heat", [blank, {"item": "minecraft:redstone"}, {"tag": "c:nuggets/copper"}, {"tag": "c:nuggets/iron"}], 1,
                     {"items": "%s:conduit_cover_blank" % MOD})
    recipe_shapeless("conduit_cover_pull", [blank, {"item": "minecraft:lever"}, {"tag": "c:dyes/red"}], 1,
                     {"items": "%s:conduit_cover_blank" % MOD})


def sounds():
    dump(os.path.join(ASSETS, "sounds.json"), {
        "fire_alarm_horn": {"subtitle": "powergrid.subtitle.fire_alarm_horn", "sounds": [{"name": "minecraft:note/bit", "pitch": 0.5, "volume": 1.0}]},
    })


def main():
    panel_textures()
    panel_model()
    cover_textures()
    cover_items()
    sounds()
    print("fire alarm assets written")


if __name__ == "__main__":
    main()
