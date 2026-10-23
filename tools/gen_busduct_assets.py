#!/usr/bin/env python3
"""Generates the bus duct resources: the duct segment, the end feed and the plug-in disconnect.

Run from the repository root:  python tools/gen_busduct_assets.py

Geometry mirrors BusDuctBlock, BusDuctFeedBlock and BusDuctPlugInBlock; change both. The duct is
drawn in the north frame running along z; the feed in the north frame with its lugs facing north;
the plug-in in the floor frame standing on the duct below it, handle on +x.
"""
import json
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png
from gen_cable_chain_assets import horizontal

TEX = os.path.join(ASSETS, "textures", "block")

DUCT_HOUSING = (3, 4, 0, 13, 12, 16)
DUCT_FLANGES = [(2.5, 3.5, 0, 13.5, 12.5, 1), (2.5, 3.5, 15, 13.5, 12.5, 16)]

FEED_BODY = (2, 1, 6, 14, 15, 16)
FEED_HUBS = [(7, 15, 7, 9, 16, 9), (7, 0, 7, 9, 1, 9)]
FEED_LUG_X = [4, 6.5, 9, 11.5]

PLUG_BODY = (2, 0, 2, 14, 8, 14)
PLUG_HANDLE = {False: (14, 1, 6, 15.5, 4.5, 8), True: (14, 4, 6, 15.5, 7.5, 8)}
PLUG_HANDLE_TRIPPED = (14, 2.5, 6, 15.5, 6, 8)
PLUG_PIVOT = (14, 4, 6.2, 15.2, 5, 7.8)
PLUG_LOCK = (13.8, 0.2, 5.8, 15.7, 1.6, 8.2)
PLUG_HUBS = [(4, 8, 3, 6, 9, 5), (10, 8, 3, 12, 9, 5)]
PLUG_LUG_X = [4, 6.5, 9, 11.5]


def textures():
    steel = (0x6a, 0x70, 0x78)
    housing = canvas(16, 16, steel + (255,))
    fill(housing, 0, 0, 16, 1, shade(steel, 1.15))
    fill(housing, 0, 15, 16, 16, shade(steel, 0.65))
    fill(housing, 0, 7, 16, 9, (0xb8, 0x73, 0x33))        # the copper stripe that says bus duct
    fill(housing, 0, 7, 16, 8, (0xd0, 0x8a, 0x45))
    write_png(os.path.join(TEX, "bus_duct.png"), housing)
    flange = canvas(16, 16, shade(steel, 0.75) + (255,))
    fill(flange, 0, 0, 16, 1, shade(steel, 0.95))
    fill(flange, 0, 15, 16, 16, shade(steel, 0.5))
    for x in (2, 7, 12):
        fill(flange, x, 3, x + 2, 5, shade(steel, 0.4))     # bolt heads
        fill(flange, x, 11, x + 2, 13, shade(steel, 0.4))
    write_png(os.path.join(TEX, "bus_duct_flange.png"), flange)

    grey = (0x7c, 0x80, 0x86)
    box = canvas(16, 16, grey + (255,))
    fill(box, 0, 0, 16, 1, shade(grey, 1.12))
    fill(box, 0, 15, 16, 16, shade(grey, 0.62))
    fill(box, 0, 0, 1, 16, shade(grey, 0.85))
    fill(box, 15, 0, 16, 16, shade(grey, 0.7))
    write_png(os.path.join(TEX, "bus_duct_feed.png"), box)
    front = canvas(16, 16, grey + (255,))
    fill(front, 2, 1, 14, 15, shade(grey, 1.0))
    fill(front, 2, 1, 14, 2, shade(grey, 1.15))
    fill(front, 2, 14, 14, 15, shade(grey, 0.6))
    fill(front, 3, 2, 13, 7, shade(grey, 0.45))              # the lug compartment window
    fill(front, 4, 10, 12, 12, (0xd8, 0xc0, 0x20))           # danger sticker
    fill(front, 4, 10, 12, 11, (0xc0, 0x28, 0x28))
    write_png(os.path.join(TEX, "bus_duct_feed_front.png"), front)

    dark = (0x4a, 0x4e, 0x56)
    plug = canvas(16, 16, dark + (255,))
    fill(plug, 0, 0, 16, 1, shade(dark, 1.3))
    fill(plug, 0, 15, 16, 16, shade(dark, 0.6))
    fill(plug, 4, 5, 12, 8, (0xe8, 0xe8, 0xe4))              # rating label
    fill(plug, 5, 6, 11, 7, (0x50, 0x50, 0x58))
    write_png(os.path.join(TEX, "bus_duct_plug_in.png"), plug)
    breaker = canvas(16, 16, dark + (255,))
    fill(breaker, 0, 0, 16, 1, shade(dark, 1.3))
    fill(breaker, 0, 15, 16, 16, shade(dark, 0.6))
    fill(breaker, 3, 4, 13, 9, (0x1c, 0x1c, 0x1c))              # the breaker's face
    fill(breaker, 3, 4, 13, 5, (0x32, 0x32, 0x32))
    fill(breaker, 4, 10, 12, 12, (0xe8, 0xe8, 0xe4))            # rating label
    fill(breaker, 5, 11, 11, 12, (0x50, 0x50, 0x58))
    write_png(os.path.join(TEX, "bus_duct_plug_in_breaker.png"), breaker)


def duct_models():
    elements = [element(*DUCT_HOUSING, "#housing")]
    for flange in DUCT_FLANGES:
        elements.append(element(*flange, "#flange"))
    tex = {"housing": "%s:block/bus_duct" % MOD, "flange": "%s:block/bus_duct_flange" % MOD, "particle": "%s:block/bus_duct" % MOD}
    dump(os.path.join(ASSETS, "models", "block", "bus_duct.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "bus_duct.json"), {"parent": "%s:block/bus_duct" % MOD})
    m = "%s:block/bus_duct" % MOD
    dump(os.path.join(ASSETS, "blockstates", "bus_duct.json"), {"variants": {
        "facing=north": {"model": m}, "facing=south": {"model": m},
        "facing=east": {"model": m, "y": 90}, "facing=west": {"model": m, "y": 90},
        "facing=up": {"model": m, "x": 90}, "facing=down": {"model": m, "x": 90},
    }})
    loot_table("bus_duct")


def feed_models():
    body = element(*FEED_BODY, "#body", cull_south=True)
    body["faces"]["north"]["texture"] = "#front"
    elements = [body]
    for hub in FEED_HUBS:
        elements.append(element(*hub, "#knockout"))
    for x in FEED_LUG_X:
        elements.append(element(x - 0.5, 7.5, 5, x + 0.5, 8.5, 6, "#terminal"))
    tex = {"body": "%s:block/bus_duct_feed" % MOD, "front": "%s:block/bus_duct_feed_front" % MOD,
           "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_four" % MOD,
           "particle": "%s:block/bus_duct_feed" % MOD}
    dump(os.path.join(ASSETS, "models", "block", "bus_duct_feed.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "bus_duct_feed.json"), {"parent": "%s:block/bus_duct_feed" % MOD})
    m = "%s:block/bus_duct_feed" % MOD
    dump(os.path.join(ASSETS, "blockstates", "bus_duct_feed.json"), {"variants": {
        "facing=north": {"model": m}, "facing=east": {"model": m, "y": 90},
        "facing=south": {"model": m, "y": 180}, "facing=west": {"model": m, "y": 270},
    }})
    loot_table("bus_duct_feed")


def plug_in_models():
    plug_in_unit("bus_duct_plug_in", False)
    plug_in_unit("bus_duct_plug_in_breaker", True)


def plug_in_unit(block, breaker):
    """The disconnect, or the breaker: the same unit with a third handle position (tripped, half way) and a tripped blockstate."""
    tex = {"body": "%s:block/%s" % (MOD, block), "handle": "%s:block/disconnect_handle" % MOD,
           "lock": "%s:block/disconnect_lock" % MOD, "terminal": "%s:block/panel_terminal" % MOD,
           "knockout": "%s:block/knockout_two" % MOD, "particle": "%s:block/%s" % (MOD, block)}
    states = [("off", False, False, False), ("on", True, False, False), ("off_locked", False, True, False)]
    if breaker:
        states += [("tripped", False, False, True), ("tripped_locked", False, True, True)]
    names = {}
    for name, on, locked, tripped in states:
        elements = [element(*PLUG_BODY, "#body")]
        for hub in PLUG_HUBS:
            elements.append(element(*hub, "#knockout"))
        for x in PLUG_LUG_X:
            elements.append(element(x - 0.5, 8, 11, x + 0.5, 9, 12, "#terminal"))
        elements.append(element(*PLUG_PIVOT, "#body"))
        elements.append(element(*(PLUG_HANDLE_TRIPPED if tripped else PLUG_HANDLE[on]), "#handle"))
        if locked:
            elements.append(element(*PLUG_LOCK, "#lock"))
        dump(os.path.join(ASSETS, "models", "block", block, name + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
        dump(os.path.join(ASSETS, "models", "block", block, name + "_h.json"), {"parent": "block/block", "textures": tex, "elements": horizontal(elements)})
        names[(on, locked, tripped)] = name
    dump(os.path.join(ASSETS, "models", "item", block + ".json"), {"parent": "%s:block/%s/off" % (MOD, block)})
    # Power Grid's four-rotation placement: the regulator's blockstate says which model and rotation
    # each facing and rotation take; the same mapping serves every such block.
    template = json.load(open(os.path.join(ASSETS, "blockstates", "vfd.json")))
    variants = {}
    for key, variant in template["variants"].items():
        facing, rotation = key.split(",")
        wall = variant["model"].endswith("block_h")
        for on in (False, True):
            for locked in (False, True):
                for tripped in ((False, True) if breaker else (False,)):
                    name = names.get((on, locked, tripped), names.get((False, locked, tripped), names[(False, False, False)]))
                    model = "%s:block/%s/%s%s" % (MOD, block, name, "_h" if wall else "")
                    v = dict(variant, model=model)
                    state = "%s,locked=%s,on=%s,%s" % (facing, str(locked).lower(), str(on).lower(), rotation)
                    if breaker:
                        state += ",tripped=%s" % str(tripped).lower()
                    variants[state] = v
    dump(os.path.join(ASSETS, "blockstates", block + ".json"), {"variants": variants})
    loot_table(block)


def recipes():
    plate = {"tag": "c:plates/iron"}
    bar = {"item": "%s:bus_bar" % MOD}
    recipe("bus_duct", ["PPP", "BBB", "PPP"], {"P": plate, "B": bar}, 4, {"items": "%s:bus_bar" % MOD})
    recipe("bus_duct_feed", ["PPP", "BBB", "PCP"], {"P": plate, "B": bar, "C": {"tag": "c:ingots/copper"}}, 1, {"items": "%s:bus_duct" % MOD})
    recipe("bus_duct_plug_in", ["PLP", "BBB", "PCP"], {"P": plate, "B": bar, "L": {"item": "minecraft:lever"}, "C": {"tag": "c:ingots/copper"}}, 1,
           {"items": "%s:bus_duct" % MOD})
    recipe("bus_duct_plug_in_breaker", ["PKP", "BBB", "PCP"], {"P": plate, "B": bar, "K": {"item": "%s:breaker_800a_3p" % MOD}, "C": {"tag": "c:ingots/copper"}}, 1,
           {"items": "%s:bus_duct_plug_in" % MOD})


def main():
    textures()
    duct_models()
    feed_models()
    plug_in_models()
    recipes()
    print("bus duct assets written")


if __name__ == "__main__":
    main()
