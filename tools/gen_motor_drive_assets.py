#!/usr/bin/env python3
"""Generates the three-phase motor and drive resources: models, blockstates, textures, loot, recipes.

Run from the repository root:  python tools/gen_motor_drive_assets.py

Motor geometry mirrors ThreePhaseMotorBlock.java (north frame: shaft out of the north face,
terminal box at the south end). Drive geometry mirrors ThreePhaseDriveBlock.java in its
floor-mounted ("facing=down") frame; the wall model is the floor model with (x, y, z) mapped to
(16 - y, x, z), the same relation the DC VFD's two models have.
"""
import json
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

MOTOR = "three_phase_motor"
DRIVE = "three_phase_drive"


# ---------------------------------------------------------------- textures

def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    # Motor: dark blue-grey frame with cooling fins.
    frame = (0x3c, 0x4a, 0x5c)
    body = canvas(16, 16, frame + (255,))
    for y in range(1, 16, 2):
        fill(body, 0, y, 16, y + 1, shade(frame, 0.7))
    fill(body, 0, 0, 16, 1, shade(frame, 1.25))
    fill(body, 0, 15, 16, 16, shade(frame, 0.5))
    write_png(os.path.join(tex, MOTOR + "_body.png"), body)
    box = canvas(16, 16, shade(frame, 1.1) + (255,))
    fill(box, 0, 0, 16, 1, shade(frame, 1.4))
    fill(box, 0, 15, 16, 16, shade(frame, 0.6))
    fill(box, 0, 0, 1, 16, shade(frame, 0.8))
    fill(box, 15, 0, 16, 16, shade(frame, 0.8))
    fill(box, 5, 5, 11, 8, (0xd8, 0xc0, 0x30))                 # nameplate
    write_png(os.path.join(tex, MOTOR + "_box.png"), box)
    foot = canvas(16, 16, (0x2a, 0x2a, 0x2e, 255))
    fill(foot, 0, 0, 16, 1, (0x44, 0x44, 0x48))
    write_png(os.path.join(tex, MOTOR + "_foot.png"), foot)

    # Drive: light grey cabinet, a display strip on top, a keypad on the front.
    grey = (0xb4, 0xb8, 0xbe)
    edge = (0x6a, 0x6e, 0x74)
    side = canvas(16, 16, grey + (255,))
    fill(side, 0, 0, 16, 1, shade(grey, 1.08))
    fill(side, 0, 15, 16, 16, edge)
    fill(side, 0, 0, 1, 16, edge)
    fill(side, 15, 0, 16, 16, edge)
    for y in range(4, 12, 2):
        fill(side, 3, y, 13, y + 1, shade(grey, 0.75))         # vents
    write_png(os.path.join(tex, DRIVE + "_side.png"), side)
    top = canvas(16, 16, grey + (255,))
    fill(top, 0, 0, 16, 1, shade(grey, 1.08))
    fill(top, 0, 15, 16, 16, edge)
    fill(top, 0, 0, 1, 16, edge)
    fill(top, 15, 0, 16, 16, edge)
    fill(top, 3, 3, 13, 8, (0x10, 0x14, 0x10))                 # display
    fill(top, 4, 4, 12, 7, (0x28, 0x60, 0x30))
    fill(top, 4, 4, 8, 5, (0x60, 0xd8, 0x70))
    for x in (4, 7, 10):
        fill(top, x, 10, x + 2, 12, (0x40, 0x40, 0x44))        # keys
    write_png(os.path.join(tex, DRIVE + "_top.png"), top)


# ---------------------------------------------------------------- motor

def motor():
    elements = [
        element(3, 3, 0.5, 13, 13, 13.5, "#body"),
        element(2.5, 2.5, 10.5, 13.5, 13.5, 15.5, "#box"),
        element(0.5, 0, 3, 15.5, 3, 13, "#foot"),
        element(3.5, 13.5, 14, 5.5, 14.5, 16, "#terminal"),
        element(7, 13.5, 14, 9, 14.5, 16, "#terminal"),
        element(10.5, 13.5, 14, 12.5, 14.5, 16, "#terminal"),
    ]
    tex = {
        "body": "%s:block/%s_body" % (MOD, MOTOR),
        "box": "%s:block/%s_box" % (MOD, MOTOR),
        "foot": "%s:block/%s_foot" % (MOD, MOTOR),
        "terminal": "%s:block/panel_terminal" % MOD,
        "particle": "%s:block/%s_body" % (MOD, MOTOR),
    }
    dump(os.path.join(ASSETS, "models", "block", MOTOR + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", MOTOR + ".json"), {"parent": "%s:block/%s" % (MOD, MOTOR)})
    model = "%s:block/%s" % (MOD, MOTOR)
    dump(os.path.join(ASSETS, "blockstates", MOTOR + ".json"), {
        "variants": {
            "facing=north": {"model": model},
            "facing=east": {"model": model, "y": 90},
            "facing=south": {"model": model, "y": 180},
            "facing=west": {"model": model, "y": 270},
            "facing=up": {"model": model, "x": 270},
            "facing=down": {"model": model, "x": 90},
        }
    })
    loot_table(MOTOR)


# ---------------------------------------------------------------- drive

def drive_elements():
    boxes = [
        ((1, 0, 1, 15, 9, 15), "#side"),
        ((3, 9, 3, 13, 9.25, 13), "#top"),
        ((2.5, 0, 0, 4.5, 2, 2), "#terminal"),
        ((7, 0, 0, 9, 2, 2), "#terminal"),
        ((11.5, 0, 0, 13.5, 2, 2), "#terminal"),
        ((2.5, 0, 14, 4.5, 2, 16), "#terminal"),
        ((7, 0, 14, 9, 2, 16), "#terminal"),
        ((11.5, 0, 14, 13.5, 2, 16), "#terminal"),
        ((14, 0, 7, 16, 2, 9), "#jack"),
        ((1, 1, 3.5, 2, 3, 5.5), "#hub"),
        ((1, 1, 10.5, 2, 3, 12.5), "#hub"),
    ]
    return boxes


def drive():
    tex = {
        "side": "%s:block/%s_side" % (MOD, DRIVE),
        "top": "%s:block/%s_top" % (MOD, DRIVE),
        "terminal": "%s:block/panel_terminal" % MOD,
        "jack": "%s:block/jack_pin" % MOD,
        "hub": "%s:block/panel_terminal" % MOD,
        "particle": "%s:block/%s_side" % (MOD, DRIVE),
    }
    vertical = []
    horizontal = []
    for (x1, y1, z1, x2, y2, z2), t in drive_elements():
        e = element(x1, y1, z1, x2, y2, z2, t)
        if t == "#side":
            e["faces"]["up"]["texture"] = "#top"
        vertical.append(e)
        h = element(16 - y2, x1, z1, 16 - y1, x2, z2, t)
        if t == "#side":
            h["faces"]["west"]["texture"] = "#top"
        horizontal.append(h)
    dump(os.path.join(ASSETS, "models", "block", DRIVE, "block_v.json"), {"parent": "block/block", "textures": tex, "elements": vertical})
    dump(os.path.join(ASSETS, "models", "block", DRIVE, "block_h.json"), {"parent": "block/block", "textures": tex, "elements": horizontal})
    dump(os.path.join(ASSETS, "models", "item", DRIVE + ".json"), {"parent": "%s:block/%s/block_v" % (MOD, DRIVE)})
    # Same rotation table as the DC VFD, with the model names swapped.
    with open(os.path.join(ASSETS, "blockstates", "vfd.json")) as f:
        states = json.load(f)
    for variant in states["variants"].values():
        variant["model"] = variant["model"].replace("block/vfd/", "block/%s/" % DRIVE)
    dump(os.path.join(ASSETS, "blockstates", DRIVE + ".json"), states)
    loot_table(DRIVE)


# ---------------------------------------------------------------- data

def recipes():
    coil = {"item": "powergrid:copper_coil"}
    iron = {"tag": "c:plates/iron"}
    recipe(MOTOR, ["CCC", "ISI", "CCC"], {"C": coil, "I": iron, "S": {"item": "create:shaft"}}, 1, {"items": "powergrid:copper_coil"})
    recipe(DRIVE, ["III", "RVR", "III"], {"I": iron, "R": {"item": "minecraft:comparator"}, "V": {"item": "%s:vfd" % MOD}}, 1,
           {"items": "%s:vfd" % MOD})


def main():
    textures()
    motor()
    drive()
    recipes()
    print("motor and drive assets written")


if __name__ == "__main__":
    main()
