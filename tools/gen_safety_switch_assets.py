#!/usr/bin/env python3
"""Generates the safety switch (disconnect) resources: textures, block and item models, blockstate,
loot table and recipe.

Run from the repository root:  python tools/gen_safety_switch_assets.py

Geometry mirrors SafetySwitchBlock.java; change both. North frame: the box is against the wall at
z = 16 with the door facing north; a viewer at the front has +x on their left, so the handle at
x = 1.5..3 is on their right.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

BODY = (3, 1, 11, 13, 15, 16)
HUBS = [(4, 15, 13.5, 6, 16, 15.5), (10, 15, 13.5, 12, 16, 15.5), (4, 0, 13.5, 6, 1, 15.5), (10, 0, 13.5, 12, 1, 15.5)]
LUG_X = [5.5, 8, 10.5]
HANDLE = {False: (1.5, 4, 12, 3, 9, 13.5), True: (1.5, 8, 12, 3, 13, 13.5)}
PIVOT = (2, 8, 12.2, 3, 9, 13.3)
LOCK = (1.2, 2.2, 12.1, 3, 4, 13.4)
BLOCK_TEX = os.path.join(ASSETS, "textures", "block")


def textures():
    grey = (0x80, 0x84, 0x8a)
    # Plain cabinet steel for the sides, top, bottom and back.
    body = canvas(16, 16, grey + (255,))
    fill(body, 0, 0, 16, 1, shade(grey, 1.12))
    fill(body, 0, 15, 16, 16, shade(grey, 0.62))
    fill(body, 0, 0, 1, 16, shade(grey, 0.85))
    fill(body, 15, 0, 16, 16, shade(grey, 0.7))
    write_png(os.path.join(BLOCK_TEX, "safety_switch.png"), body)

    # The door, drawn where the front face samples it: block x 3..13 maps to u 3..13, block y 1..15
    # to v 1..15 (v runs down). Hinge seam on the viewer's left (u 3), latch on the right.
    front = canvas(16, 16, grey + (255,))
    fill(front, 3, 1, 13, 15, shade(grey, 1.0))
    fill(front, 3, 1, 13, 2, shade(grey, 1.15))
    fill(front, 3, 14, 13, 15, shade(grey, 0.6))
    fill(front, 3, 1, 4, 15, shade(grey, 0.75))
    fill(front, 12, 1, 13, 15, shade(grey, 0.8))
    fill(front, 4, 2, 12, 14, shade(grey, 1.04))
    # Rating label, white with a line of print.
    fill(front, 5, 4, 11, 8, (0xe8, 0xe8, 0xe4))
    fill(front, 6, 5, 10, 6, (0x50, 0x50, 0x58))
    fill(front, 6, 7, 9, 8, (0x50, 0x50, 0x58))
    # Danger sticker: red over yellow.
    fill(front, 5, 9, 11, 11, (0xd8, 0xc0, 0x20))
    fill(front, 5, 9, 11, 10, (0xc0, 0x28, 0x28))
    # Latch at the lower right.
    fill(front, 11, 12, 12, 13, shade(grey, 0.45))
    write_png(os.path.join(BLOCK_TEX, "safety_switch_front.png"), front)

    red = (0xc8, 0x22, 0x22)
    handle = canvas(16, 16, red + (255,))
    for y in range(0, 16, 4):
        fill(handle, 0, y, 16, y + 1, shade(red, 1.2))
        fill(handle, 0, y + 3, 16, y + 4, shade(red, 0.75))
    write_png(os.path.join(BLOCK_TEX, "safety_switch_handle.png"), handle)

    lock = canvas(16, 16, (0x30, 0x30, 0x34, 255))
    fill(lock, 0, 0, 16, 2, (0x90, 0x90, 0x98))
    fill(lock, 4, 6, 12, 10, (0xd0, 0xb0, 0x20))
    write_png(os.path.join(BLOCK_TEX, "safety_switch_lock.png"), lock)


def models():
    tex = {"body": "%s:block/safety_switch" % MOD, "front": "%s:block/safety_switch_front" % MOD,
           "handle": "%s:block/safety_switch_handle" % MOD, "lock": "%s:block/safety_switch_lock" % MOD,
           "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_two" % MOD,
           "particle": "%s:block/safety_switch" % MOD}
    for name, on, locked in (("off", False, False), ("on", True, False), ("off_locked", False, True)):
        body = element(*BODY, "#body", cull_south=True)
        body["faces"]["north"]["texture"] = "#front"
        elements = [body]
        for hub in HUBS:
            elements.append(element(*hub, "#knockout"))
        for x in LUG_X:
            elements.append(element(x - 0.5, 15, 11.5, x + 0.5, 15.8, 12.5, "#terminal"))
            elements.append(element(x - 0.5, 0.2, 11.5, x + 0.5, 1, 12.5, "#terminal"))
        elements.append(element(*PIVOT, "#body"))
        elements.append(element(*HANDLE[on], "#handle"))
        if locked:
            elements.append(element(*LOCK, "#lock"))
        dump(os.path.join(ASSETS, "models", "block", "safety_switch", name + ".json"),
             {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "safety_switch.json"), {"parent": "%s:block/safety_switch/off" % MOD})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        for on in (False, True):
            for locked in (False, True):
                name = "on" if on else ("off_locked" if locked else "off")
                v = {"model": "%s:block/safety_switch/%s" % (MOD, name)}
                v.update(rot)
                variants["facing=%s,locked=%s,on=%s" % (facing, str(locked).lower(), str(on).lower())] = v
    dump(os.path.join(ASSETS, "blockstates", "safety_switch.json"), {"variants": variants})


def data():
    loot_table("safety_switch")
    recipe("safety_switch", ["PPP", "PLP", "PCP"],
           {"P": {"tag": "c:plates/iron"}, "L": {"item": "minecraft:lever"}, "C": {"tag": "c:ingots/copper"}},
           1, {"items": "minecraft:lever"})


def main():
    textures()
    models()
    data()
    print("safety switch assets written")


if __name__ == "__main__":
    main()
