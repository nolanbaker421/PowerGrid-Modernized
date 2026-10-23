#!/usr/bin/env python3
"""Generates the rectifier, the capacitor bank, the three-phase pump and the pump stage: textures,
models, blockstates, loot and recipes.

Run from the repository root:  python tools/gen_power_quality_assets.py

The rectifier and the bank are the load bank's wall cabinet (RectifierBlock, CapacitorBankBlock:
door north, cabinet against z = 16, lugs low on the door, two knockouts top and bottom). The pump
mirrors ThreePhasePumpBlock in the north frame (axis along z, front at z = 0) and borrows the
three-phase motor's textures; the stage is a ring of the same around the axis.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

RECTIFIER = "rectifier"
BANK = "capacitor_bank"
PUMP = "three_phase_pump"
STAGE = "pump_stage"
TEX = os.path.join(ASSETS, "textures", "block")

CABINET = (2, 1, 10, 14, 15, 16)
LOW_LUGS = [(11.5, 2, 9, 13.5, 4, 10), (8.5, 2, 9, 10.5, 4, 10), (5.5, 2, 9, 7.5, 4, 10)]
DC_LUGS = [(11.5, 12, 9, 13.5, 14, 10), (2.5, 12, 9, 4.5, 14, 10)]
HANDLE = {True: (2.5, 10, 9, 4.5, 14, 10), False: (2.5, 5, 9, 4.5, 9, 10)}
HUBS = [(4, 15, 12.5, 6, 16, 14.5), (10, 15, 12.5, 12, 16, 14.5), (4, 0, 12.5, 6, 1, 14.5), (10, 0, 12.5, 12, 1, 14.5)]

PUMP_BODY = (2, 0, 3, 14, 14, 13)
PUMP_STUBS = [(2, 2, 0, 14, 14, 3), (2, 2, 13, 14, 14, 16)]
PUMP_BOX = (3, 13, 4, 13, 15, 12)
PUMP_LUGS = [(9.5, 15, 7, 11.5, 16, 9), (7, 15, 7, 9, 16, 9), (4.5, 15, 7, 6.5, 16, 9)]
PUMP_HUBS = [(2, 13.5, 7, 3, 14.5, 9), (13, 13.5, 7, 14, 14.5, 9)]
STAGE_RING = (1, 1, 2, 15, 15, 14)
STAGE_HUB = (5, 5, 0, 11, 11, 16)


def textures():
    # Rectifier: a dark cabinet with a heat sink on the door between the lug rows.
    grey = (0x50, 0x54, 0x5a)
    side = canvas(16, 16, grey + (255,))
    fill(side, 0, 0, 16, 1, shade(grey, 1.3))
    fill(side, 0, 15, 16, 16, shade(grey, 0.6))
    write_png(os.path.join(TEX, RECTIFIER + "_side.png"), side)
    front = canvas(16, 16, grey + (255,))
    fill(front, 0, 0, 16, 1, shade(grey, 1.3))
    fill(front, 0, 15, 16, 16, shade(grey, 0.6))
    for x in range(3, 13, 2):
        fill(front, x, 5, x + 1, 11, (0x22, 0x24, 0x28))         # heat sink fins
    fill(front, 1, 3, 3, 4, (0xd0, 0x40, 0x40))                   # + and - marks beside the DC lugs
    fill(front, 13, 3, 15, 4, (0x40, 0x70, 0xd0))
    write_png(os.path.join(TEX, RECTIFIER + "_front.png"), front)

    # Capacitor bank: a lighter cabinet with three cans showing through a window.
    beige = (0x9c, 0x98, 0x8c)
    side = canvas(16, 16, beige + (255,))
    fill(side, 0, 0, 16, 1, shade(beige, 1.2))
    fill(side, 0, 15, 16, 16, shade(beige, 0.6))
    write_png(os.path.join(TEX, BANK + "_side.png"), side)
    front = canvas(16, 16, beige + (255,))
    fill(front, 0, 0, 16, 1, shade(beige, 1.2))
    fill(front, 0, 15, 16, 16, shade(beige, 0.6))
    fill(front, 4, 2, 12, 11, (0x24, 0x26, 0x2a))                 # the window
    for x in (5, 7.5, 10):
        fill(front, int(x), 3, int(x) + 2, 10, (0x30, 0x60, 0xa8))  # the cans
        fill(front, int(x), 3, int(x) + 2, 4, (0xc8, 0xc8, 0xc0))
    fill(front, 4, 12, 12, 13, (0xd8, 0xc0, 0x20))                # warning stripe
    write_png(os.path.join(TEX, BANK + "_front.png"), front)

    # Pump body: the motor's fins with a flow arrow pointing up the texture (towards v = 0).
    frame = (0x3c, 0x4a, 0x5c)
    body = canvas(16, 16, frame + (255,))
    for y in range(1, 16, 2):
        fill(body, 0, y, 16, y + 1, shade(frame, 0.7))
    fill(body, 0, 0, 16, 1, shade(frame, 1.25))
    fill(body, 0, 15, 16, 16, shade(frame, 0.5))
    arrow = (0xe8, 0xc8, 0x20)
    fill(body, 7, 5, 9, 13, arrow)                                # the shaft
    for i, w in enumerate((1, 2, 3, 4)):                          # the head, point at the top
        fill(body, 8 - w, 2 + i, 8 + w, 3 + i, arrow)
    write_png(os.path.join(TEX, PUMP + "_body.png"), body)


def cabinet(name, lugs, extras, front, side, state=None):
    body = element(*CABINET, "#side", cull_south=True)
    body["faces"]["north"]["texture"] = "#front"
    elements = [body] + extras
    for lug in lugs:
        elements.append(element(*lug, "#terminal"))
    for hub in HUBS:
        elements.append(element(*hub, "#knockout"))
    model = name if state is None else "%s/%s" % (name, state)
    dump(os.path.join(ASSETS, "models", "block", model + ".json"), {
        "parent": "block/block",
        "textures": {"front": front, "side": side, "handle": "%s:block/disconnect_handle" % MOD,
                     "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_four" % MOD, "particle": side},
        "elements": elements,
    })
    return "%s:block/%s" % (MOD, model)


def facings(model_for):
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        for key, model in model_for.items():
            v = {"model": model}
            v.update(rot)
            variants[("facing=%s" % facing) + key] = v
    return variants


def rectifier():
    front, side = "%s:block/%s_front" % (MOD, RECTIFIER), "%s:block/%s_side" % (MOD, RECTIFIER)
    model = cabinet(RECTIFIER, LOW_LUGS + DC_LUGS, [], front, side)
    dump(os.path.join(ASSETS, "models", "item", RECTIFIER + ".json"), {"parent": model})
    dump(os.path.join(ASSETS, "blockstates", RECTIFIER + ".json"), {"variants": facings({"": model})})
    loot_table(RECTIFIER)


def capacitor_bank():
    front, side = "%s:block/%s_front" % (MOD, BANK), "%s:block/%s_side" % (MOD, BANK)
    models = {}
    for on in (False, True):
        models[",on=%s" % str(on).lower()] = cabinet(BANK, LOW_LUGS, [element(*HANDLE[on], "#handle")], front, side, "on" if on else "off")
    dump(os.path.join(ASSETS, "models", "item", BANK + ".json"), {"parent": models[",on=false"]})
    dump(os.path.join(ASSETS, "blockstates", BANK + ".json"), {"variants": facings(models)})
    loot_table(BANK)


def pump():
    motor = "three_phase_motor"
    tex = {"body": "%s:block/%s_body" % (MOD, motor), "arrow": "%s:block/%s_body" % (MOD, PUMP), "box": "%s:block/%s_box" % (MOD, motor),
           "stub": "%s:block/%s_foot" % (MOD, motor), "terminal": "%s:block/panel_terminal" % MOD, "knockout": "%s:block/knockout_one_half" % MOD,
           "particle": "%s:block/%s_body" % (MOD, motor)}
    # The arrow texture points to v = 0. On the up face that is north (the front); on the east face
    # north is to the right (rotation 90), on the west face to the left (270). Reversed turns each half round.
    for name, turn in ((PUMP, 0), (PUMP + "_reversed", 180)):
        body = element(*PUMP_BODY, "#body")
        for face, rot in (("up", 0), ("east", 90), ("west", 270)):
            body["faces"][face] = {"uv": [0, 0, 16, 16], "texture": "#arrow", "rotation": (rot + turn) % 360}
        elements = [body]
        for stub in PUMP_STUBS:
            elements.append(element(*stub, "#stub"))
        elements.append(element(*PUMP_BOX, "#box"))
        for lug in PUMP_LUGS:
            elements.append(element(*lug, "#terminal"))
        for hub in PUMP_HUBS:
            elements.append(element(*hub, "#knockout"))
        dump(os.path.join(ASSETS, "models", "block", name + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", PUMP + ".json"), {"parent": "%s:block/%s" % (MOD, PUMP)})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})):
        for reversed_ in (False, True):
            v = {"model": "%s:block/%s%s" % (MOD, PUMP, "_reversed" if reversed_ else "")}
            v.update(rot)
            variants["facing=%s,reversed=%s" % (facing, str(reversed_).lower())] = v
    dump(os.path.join(ASSETS, "blockstates", PUMP + ".json"), {"variants": variants})
    loot_table(PUMP)

    # The stage: a volute ring around the axis, with the motor's finned body texture.
    ring = [element(*STAGE_RING, "#body"), element(*STAGE_HUB, "#stub")]
    dump(os.path.join(ASSETS, "models", "block", STAGE + ".json"), {"parent": "block/block", "textures": tex, "elements": ring})
    dump(os.path.join(ASSETS, "models", "item", STAGE + ".json"), {"parent": "%s:block/%s" % (MOD, STAGE)})
    stage = "%s:block/%s" % (MOD, STAGE)
    dump(os.path.join(ASSETS, "blockstates", STAGE + ".json"), {"variants": {
        "axis=z": {"model": stage}, "axis=x": {"model": stage, "y": 90}, "axis=y": {"model": stage, "x": 90},
    }})
    loot_table(STAGE)


def recipes():
    iron = {"tag": "c:plates/iron"}
    copper = {"tag": "c:plates/copper"}
    recipe(RECTIFIER, ["ICI", "RRR", "ICI"], {"I": iron, "C": copper, "R": {"tag": "c:dusts/redstone"}}, 1, {"items": "minecraft:redstone"})
    recipe(BANK, ["ICI", "PCP", "ICI"], {"I": iron, "C": copper, "P": {"item": "minecraft:paper"}}, 1, {"items": "minecraft:paper"})
    recipe_shapeless(PUMP, [{"item": "%s:three_phase_motor" % MOD}, {"item": "create:fluid_pipe"}], 1, {"items": "%s:three_phase_motor" % MOD})
    recipe(STAGE, [" I ", "ICI", " I "], {"I": iron, "C": {"tag": "c:ingots/copper"}}, 2, {"items": "%s:three_phase_pump" % MOD})


def main():
    textures()
    rectifier()
    capacitor_bank()
    pump()
    recipes()
    import ac_conditions
    ac_conditions.main()
    print("power quality assets written")


if __name__ == "__main__":
    main()
