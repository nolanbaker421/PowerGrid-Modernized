#!/usr/bin/env python3
"""Generates the custom switchgear resources: the switchgear cell block and the component items
that go in its slots (neutral bar, lug block, switch unit, contactor, fuse sets).

Run from the repository root:  python tools/gen_gear_assets.py

Geometry mirrors GearLayout.java; change both. North frame: the cabinet fills the block from
z = 2 back, open at the front; a 3 x 3 grid of 5 px slots sits behind the opening, column 0 on
the viewer's left (+x), row 0 at the top. Knockouts: three on top, three underneath, two a side.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, text, write_png

NAME = "gear_cell"
BODY = (0, 0, 2, 16, 16, 16)
PITCH, EDGE = 5, 15.5
SIDE_HUB_Y = [4.5, 11.5]
TEX = os.path.join(ASSETS, "textures", "block")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
COPPER = (0xb8, 0x73, 0x33)
SILVER = (0xc8, 0xcc, 0xd0)
GREEN = (0x3a, 0xaa, 0x3a)
DARK = (0x33, 0x36, 0x3c)


def hubs():
    boxes = []
    for c in range(3):
        cx = EDGE - PITCH * c - PITCH / 2
        boxes.append((cx - 1, 15, 8, cx + 1, 16, 10))
    for c in range(3):
        cx = EDGE - PITCH * c - PITCH / 2
        boxes.append((cx - 1, 0, 8, cx + 1, 1, 10))
    for y in SIDE_HUB_Y:
        boxes.append((15, y - 1, 8, 16, y + 1, 10))
    for y in SIDE_HUB_Y:
        boxes.append((0, y - 1, 8, 1, y + 1, 10))
    return boxes


def textures():
    grey = (0x6e, 0x74, 0x7c)
    edge = (0x3a, 0x3e, 0x44)
    # The front: a steel frame around nine dark openings, one per slot (u = 16 - x, v = 16 - y).
    front = canvas(16, 16, grey + (255,))
    for u in range(3):
        for v in range(3):
            fill(front, 1 + 5 * u, 1 + 5 * v, 5 + 5 * u, 5 + 5 * v, (0x16, 0x17, 0x1a))
            fill(front, 1 + 5 * u, 1 + 5 * v, 5 + 5 * u, 2 + 5 * v, (0x0c, 0x0d, 0x10))   # shadow under the frame
    for k in range(4):
        fill(front, 5 * k, 0, 5 * k + 1, 16, shade(grey, 0.8))
        fill(front, 0, 5 * k, 16, 5 * k + 1, shade(grey, 0.8))
    fill(front, 0, 0, 16, 1, shade(grey, 1.15))
    fill(front, 0, 15, 16, 16, edge)
    write_png(os.path.join(TEX, NAME + "_front.png"), front)
    side = canvas(16, 16, grey + (255,))
    fill(side, 0, 0, 16, 1, shade(grey, 1.15))
    fill(side, 0, 15, 16, 16, edge)
    fill(side, 0, 0, 1, 16, edge)
    fill(side, 15, 0, 16, 16, edge)
    fill(side, 3, 3, 13, 4, shade(grey, 0.85))        # louvre lines
    fill(side, 3, 6, 13, 7, shade(grey, 0.85))
    fill(side, 3, 9, 13, 10, shade(grey, 0.85))
    fill(side, 3, 12, 13, 13, shade(grey, 0.85))
    write_png(os.path.join(TEX, NAME + "_side.png"), side)
    top = canvas(16, 16, grey + (255,))
    fill(top, 0, 0, 16, 1, shade(grey, 1.15))
    fill(top, 0, 15, 16, 16, edge)
    fill(top, 0, 0, 1, 16, edge)
    fill(top, 15, 0, 16, 16, edge)
    fill(top, 5, 6, 11, 8, (0xd8, 0xc0, 0x30))         # nameplate
    write_png(os.path.join(TEX, NAME + "_top.png"), top)

    # Items.
    bar = canvas(16, 16)
    fill(bar, 1, 6, 15, 10, SILVER)
    fill(bar, 1, 6, 15, 7, shade(SILVER, 1.15))
    fill(bar, 1, 9, 15, 10, shade(SILVER, 0.65))
    fill(bar, 4, 7, 5, 9, shade(SILVER, 0.5))
    fill(bar, 11, 7, 12, 9, shade(SILVER, 0.5))
    write_png(os.path.join(ITEM_TEX, "neutral_bar.png"), bar)

    ground = canvas(16, 16)
    fill(ground, 1, 6, 15, 10, GREEN)
    fill(ground, 1, 6, 15, 7, shade(GREEN, 1.2))
    fill(ground, 1, 9, 15, 10, shade(GREEN, 0.6))
    for x in (3, 6, 9, 12):
        fill(ground, x, 7, x + 1, 9, shade(GREEN, 0.45))
    write_png(os.path.join(ITEM_TEX, "ground_bar.png"), ground)

    lugs = canvas(16, 16)
    fill(lugs, 1, 8, 15, 14, DARK)
    fill(lugs, 1, 8, 15, 9, shade(DARK, 1.6))
    for k in range(5):
        x = 1.5 + k * 2.8
        fill(lugs, int(x), 4, int(x) + 2, 10, (0xb8, 0xbc, 0xc0))
        fill(lugs, int(x), 4, int(x) + 2, 5, (0xe0, 0xe4, 0xe8))
    write_png(os.path.join(ITEM_TEX, "gear_lugs.png"), lugs)

    switch = canvas(16, 16)
    fill(switch, 2, 1, 14, 15, DARK)
    fill(switch, 2, 1, 14, 2, shade(DARK, 1.6))
    fill(switch, 3, 3, 13, 13, shade(DARK, 0.8))
    fill(switch, 10, 3, 13, 8, (0xc8, 0x22, 0x22))        # the handle, up
    fill(switch, 4, 10, 9, 12, (0xe8, 0xe8, 0xe4))        # label
    write_png(os.path.join(ITEM_TEX, "gear_switch.png"), switch)

    body = (0x50, 0x54, 0x5c)
    contactor = canvas(16, 16)
    fill(contactor, 2, 1, 14, 15, body)
    fill(contactor, 2, 1, 14, 2, shade(body, 1.5))
    fill(contactor, 3, 4, 13, 12, DARK)
    fill(contactor, 10, 2, 13, 4, (0x40, 0xe0, 0x60))      # coil light
    for x in (4, 7, 10):
        fill(contactor, x, 12, x + 2, 15, COPPER)
        fill(contactor, x, 1, x + 2, 4, COPPER)
    write_png(os.path.join(ITEM_TEX, "gear_contactor.png"), contactor)

    starter = canvas(16, 16)
    fill(starter, 2, 1, 14, 8, (0x1c, 0x1c, 0x1c))             # the breaker on top
    fill(starter, 2, 1, 14, 2, (0x32, 0x32, 0x32))
    fill(starter, 10, 2, 13, 7, (0x11, 0x11, 0x11))
    fill(starter, 11, 3, 12, 6, (0xe8, 0xe8, 0xe8))
    fill(starter, 2, 8, 14, 15, body)                           # the contactor under it
    fill(starter, 3, 9, 9, 14, DARK)
    fill(starter, 10, 9, 13, 11, (0x40, 0xe0, 0x60))            # coil light
    fill(starter, 10, 12, 13, 14, (0xc8, 0x22, 0x22))           # overload reset
    write_png(os.path.join(ITEM_TEX, "gear_starter.png"), starter)

    meter = canvas(16, 16)
    fill(meter, 2, 1, 14, 15, DARK)
    fill(meter, 2, 1, 14, 2, shade(DARK, 1.6))
    fill(meter, 3, 3, 13, 8, (0x60, 0xd8, 0x90))                # the display
    fill(meter, 4, 4, 8, 5, (0x1e, 0x30, 0x28))
    fill(meter, 4, 6, 11, 7, (0x1e, 0x30, 0x28))
    for x in (4, 7, 10):
        fill(meter, x, 10, x + 2, 14, COPPER)
    write_png(os.path.join(ITEM_TEX, "gear_meter.png"), meter)

    for rating in (100, 200, 400, 800):
        fuse = canvas(16, 16)
        for x in (1, 6, 11):
            fill(fuse, x, 1, x + 4, 15, (0xd8, 0xd4, 0xc8))
            fill(fuse, x, 1, x + 4, 3, shade(COPPER, 0.8))
            fill(fuse, x, 13, x + 4, 15, shade(COPPER, 0.8))
            fill(fuse, x + 3, 3, x + 4, 13, (0xb0, 0xac, 0xa0))
        text(fuse, 1 if rating < 1000 else 0, 6, str(rating), (0x30, 0x30, 0x38))
        write_png(os.path.join(ITEM_TEX, "gear_fuse_%d.png" % rating), fuse)


def models():
    body = element(*BODY, "#side", cull_south=True)
    body["faces"]["north"]["texture"] = "#front"
    body["faces"]["up"]["texture"] = "#top"
    body["faces"]["down"]["texture"] = "#top"
    elements = [body]
    for hub in hubs():
        elements.append(element(*hub, "#knockout"))
    tex = {
        "front": "%s:block/%s_front" % (MOD, NAME),
        "side": "%s:block/%s_side" % (MOD, NAME),
        "top": "%s:block/%s_top" % (MOD, NAME),
        "knockout": "%s:block/knockout_four" % MOD,
        "particle": "%s:block/%s_side" % (MOD, NAME),
    }
    dump(os.path.join(ASSETS, "models", "block", NAME + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", NAME + ".json"), {"parent": "%s:block/%s" % (MOD, NAME)})
    m = "%s:block/%s" % (MOD, NAME)
    dump(os.path.join(ASSETS, "blockstates", NAME + ".json"), {"variants": {
        "facing=north": {"model": m}, "facing=east": {"model": m, "y": 90},
        "facing=south": {"model": m, "y": 180}, "facing=west": {"model": m, "y": 270},
    }})
    loot_table(NAME)
    for item in ("neutral_bar", "ground_bar", "gear_lugs", "gear_switch", "gear_contactor", "gear_starter", "gear_meter",
                 "gear_fuse_100", "gear_fuse_200", "gear_fuse_400", "gear_fuse_800"):
        dump(os.path.join(ASSETS, "models", "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, item)}})


def recipes():
    plate = {"tag": "c:plates/iron"}
    copper = {"tag": "c:ingots/copper"}
    bar = {"item": "%s:bus_bar" % MOD}
    recipe(NAME, ["PBP", "PGP", "PBP"], {"P": plate, "B": bar, "G": {"item": "minecraft:iron_bars"}}, 1, {"items": "%s:bus_bar" % MOD})
    recipe("neutral_bar", ["III"], {"I": {"tag": "c:ingots/iron"}}, 2, {"items": "%s:bus_bar" % MOD})
    recipe("ground_bar", ["CCC"], {"C": copper}, 2, {"items": "%s:bus_bar" % MOD})
    recipe_shapeless("gear_starter", [{"item": "%s:breaker_50a_3p" % MOD}, {"item": "%s:gear_contactor" % MOD}, plate], 1, {"items": "%s:gear_contactor" % MOD})
    recipe("gear_meter", ["PGP", "CCC", "PPP"], {"P": plate, "G": {"tag": "c:glass_panes"}, "C": copper}, 1, {"items": "%s:gear_cell" % MOD})
    recipe("gear_lugs", ["C C", "PPP"], {"C": copper, "P": plate}, 1, {"items": "%s:gear_cell" % MOD})
    recipe("gear_switch", ["PLP", "CCC", "PPP"], {"P": plate, "L": {"item": "minecraft:lever"}, "C": copper}, 1, {"items": "%s:gear_cell" % MOD})
    recipe("gear_contactor", ["PRP", "CCC", "PPP"], {"P": plate, "R": {"tag": "c:dusts/redstone"}, "C": copper}, 1, {"items": "%s:gear_cell" % MOD})
    links = {100: {"tag": "c:nuggets/copper"}, 200: copper, 400: {"tag": "c:plates/copper"}, 800: bar}
    for rating, link in links.items():
        recipe("gear_fuse_%d" % rating, ["LGL", "LGL", "LGL"], {"L": link, "G": {"tag": "c:glass_panes"}}, 1, {"items": "%s:gear_cell" % MOD})


def main():
    textures()
    models()
    recipes()
    print("switchgear cell assets written")


if __name__ == "__main__":
    main()
