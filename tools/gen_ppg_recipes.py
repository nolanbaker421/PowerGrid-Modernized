#!/usr/bin/env python3
"""Generates crafting recipes for Create: Power Plant Grid (modid powerplantgrid), which ships
without any. Every recipe and its unlock advancement carries a neoforge:mod_loaded condition, so
nothing here exists unless that mod is installed.

Run from the repository root:  python tools/gen_ppg_recipes.py

Output: data/powergrid_modernized/recipe/ppg/<name>.json and the matching advancement under
advancement/recipes/misc/ppg/. The item list mirrors powerplantgrid 0.1.1's registry; the scheme
is described in docs/USER_GUIDE.md under "Power Plant Grid recipes".
"""
import os
import shutil

from gen_breaker_panel_assets import DATA, MOD, dump

PPG = "powerplantgrid"
COND = [{"type": "neoforge:mod_loaded", "modid": PPG}]
RECIPES = os.path.join(DATA, "recipe", "ppg")
ADVANCEMENTS = os.path.join(DATA, "advancement", "recipes", "misc", "ppg")

IRON_PLATE = {"tag": "c:plates/iron"}
COPPER_PLATE = {"tag": "c:plates/copper"}
IRON_NUGGET = {"tag": "c:nuggets/iron"}
IRON_INGOT = {"tag": "c:ingots/iron"}
IRON_BLOCK = {"tag": "c:storage_blocks/iron"}
PORCELAIN = {"item": "minecraft:brown_terracotta"}
WHITE = {"item": "minecraft:white_terracotta"}
GLASS = {"item": "minecraft:glass"}
KELP = {"item": "minecraft:dried_kelp"}
COIL = {"item": "powergrid:copper_coil"}
CONNECTOR = {"item": "powergrid:wire_connector"}
HEAVY = {"item": "powergrid:heavy_wire_connector"}


def ppg(name):
    return {"item": "%s:%s" % (PPG, name)}


def _advancement(name, unlock):
    recipe_id = "%s:ppg/%s" % (MOD, name)
    dump(os.path.join(ADVANCEMENTS, name + ".json"), {
        "neoforge:conditions": COND,
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"conditions": {"items": [{"items": unlock["item"] if "item" in unlock else "#" + unlock["tag"]}]}, "trigger": "minecraft:inventory_changed"},
            "has_the_recipe": {"conditions": {"recipe": recipe_id}, "trigger": "minecraft:recipe_unlocked"},
        },
        "requirements": [["has_the_recipe", "has_item"]],
        "rewards": {"recipes": [recipe_id]},
    })


def shaped(name, pattern, key, count, unlock, result=None):
    dump(os.path.join(RECIPES, name + ".json"), {
        "neoforge:conditions": COND,
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "key": key,
        "pattern": pattern,
        "result": {"count": count, "id": "%s:%s" % (PPG, result or name)},
    })
    _advancement(name, unlock)


def shapeless(name, ingredients, count, unlock, result=None):
    dump(os.path.join(RECIPES, name + ".json"), {
        "neoforge:conditions": COND,
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ingredients,
        "result": {"count": count, "id": "%s:%s" % (PPG, result or name)},
    })
    _advancement(name, unlock)


def convert(a, b):
    """Two shapes of the same thing: either crafts alone into the other."""
    shapeless("%s_to_%s" % (a, b), [ppg(a)], 1, ppg(a), result=b)
    shapeless("%s_to_%s" % (b, a), [ppg(b)], 1, ppg(b), result=a)


# ---------------------------------------------------------------- gauged wire, from Power Grid's wire of the same metal
BASE_WIRE = {"copper": {"item": "powergrid:wire"}, "iron": {"item": "powergrid:iron_wire"}, "golden": {"item": "powergrid:golden_wire"}}
# gauge -> (base wires in, gauged wires out)
GAUGES = {"24awg": (1, 4), "20awg": (1, 3), "16awg": (1, 2), "8awg": (3, 2), "4awg": (3, 1), "2awg": (4, 1), "1awg": (5, 1), "0awg": (6, 1)}


def wires():
    for metal, base in BASE_WIRE.items():
        for gauge, (n, out) in GAUGES.items():
            bare = "%s_wire_%s" % (metal, gauge)
            shapeless(bare, [base] * n, out, base)
            shapeless("insulated_" + bare, [ppg(bare), KELP], 1, ppg(bare))
        if metal != "copper":   # the 12 AWG insulated iron and golden wires have no bare counterpart
            shapeless("insulated_%s_wire_12awg" % metal, [base, KELP], 1, base)


# ---------------------------------------------------------------- insulators: each class from the one below it and more porcelain
def insulators():
    shapeless("insulator_600v_a", [PORCELAIN, IRON_NUGGET], 4, PORCELAIN)
    shapeless("insulator_10kv_a", [PORCELAIN, PORCELAIN, IRON_NUGGET], 2, PORCELAIN)
    shapeless("insulator_10kv_b", [WHITE, WHITE, IRON_NUGGET], 2, WHITE)
    shapeless("insulator_10kv_c", [GLASS, GLASS, IRON_NUGGET], 2, GLASS)
    shapeless("insulator_35kv_a", [ppg("insulator_10kv_a")] + [PORCELAIN] * 2, 1, ppg("insulator_10kv_a"))
    shapeless("insulator_65kv_a", [ppg("insulator_35kv_a")] + [PORCELAIN] * 2, 1, ppg("insulator_35kv_a"))
    shapeless("insulator_65kv_b", [ppg("insulator_10kv_c")] + [GLASS] * 4, 1, ppg("insulator_10kv_c"))
    convert("insulator_65kv_b", "insulator_65kv_b_thin")
    shapeless("insulator_100kv_a", [ppg("insulator_65kv_a")] + [PORCELAIN] * 2 + [IRON_INGOT], 1, ppg("insulator_65kv_a"))
    shapeless("insulator_100kv_b", [ppg("insulator_65kv_b")] + [GLASS] * 2 + [IRON_INGOT], 1, ppg("insulator_65kv_b"))
    convert("insulator_100kv_b", "insulator_100kv_b_thin")
    shapeless("insulator_275kv_a", [ppg("insulator_100kv_a")] + [PORCELAIN] * 3, 1, ppg("insulator_100kv_a"))
    convert("insulator_275kv_a", "insulator_275kv_a_thin")
    shapeless("insulator_360kv_a", [ppg("insulator_275kv_a")] + [PORCELAIN] * 2, 1, ppg("insulator_275kv_a"))
    convert("insulator_360kv_a", "insulator_360kv_a_thin")
    shapeless("insulator_360kv_b", [ppg("insulator_275kv_a")] + [GLASS] * 3, 1, ppg("insulator_275kv_a"))
    shapeless("insulator_500kv_a", [ppg("insulator_360kv_a")] + [PORCELAIN] * 3, 1, ppg("insulator_360kv_a"))
    shapeless("insulator_750kv_a", [ppg("insulator_500kv_a")] + [PORCELAIN] * 4, 1, ppg("insulator_500kv_a"))
    shapeless("insulator_1150kv_a", [ppg("insulator_750kv_a")] + [PORCELAIN] * 5 + [IRON_INGOT], 1, ppg("insulator_750kv_a"))
    chain = {"item": "minecraft:chain"}
    shapeless("insulator_stack_65kv", [PORCELAIN] * 4 + [IRON_NUGGET, chain], 1, PORCELAIN)
    shapeless("insulator_stack_65kv_white", [WHITE] * 4 + [IRON_NUGGET, chain], 1, WHITE)


# ---------------------------------------------------------------- breakers: a column per pole, copper plate over the class insulator over an iron plate
TIER_INSULATOR = {"": "insulator_10kv_a", "_white": "insulator_10kv_b", "_35kv": "insulator_35kv_a", "_65kv": "insulator_65kv_a", "_100kv": "insulator_100kv_a"}


def breakers():
    for tier, insulator in TIER_INSULATOR.items():
        for poles, n in (("", 1), ("_2p", 2), ("_3p", 3)):
            upright = "test_breaker%s%s" % (tier, poles)
            shaped(upright, ["C" * n, "N" * n, "P" * n], {"C": COPPER_PLATE, "N": ppg(insulator), "P": IRON_PLATE}, 1, ppg(insulator))
            convert(upright, upright + "_h")


# ---------------------------------------------------------------- the handle, the shaft and the drives under a breaker
def operators():
    shapeless("breaker_handle", [{"item": "minecraft:lever"}, IRON_PLATE, IRON_INGOT], 1, IRON_PLATE)
    shapeless("breaker_shaft", [{"item": "create:shaft"}, IRON_PLATE], 2, IRON_PLATE)
    shapeless("rotary_drive", [{"item": "create:cogwheel"}, {"item": "create:shaft"}, IRON_PLATE], 1, IRON_PLATE)
    supply = {"12v": 1, "24v": 2, "110v": 3, "230v": 4}
    signal = {"5vsig": 1, "12vsig": 2, "24vsig": 3}
    for volts, coils in supply.items():
        shapeless("solenoid_drive_" + volts, [COIL] * coils + [IRON_PLATE, CONNECTOR], 1, COIL)
        for sig, resistors in signal.items():
            shapeless("servo_drive_%s_%s" % (volts, sig), [{"item": "powergrid:servo"}] + [COIL] * coils + [{"item": "powergrid:resistor"}] * resistors + [IRON_PLATE], 1, COIL)


# ---------------------------------------------------------------- transformers: HV bushings on top, coils round an iron core, LV terminals below
HV = {"1kv": ppg("insulator_600v_a"), "6900v": ppg("insulator_10kv_a"), "10kv": ppg("insulator_10kv_a"), "35kv": ppg("insulator_35kv_a"), "65kv": ppg("insulator_65kv_a"), "100kv": ppg("insulator_100kv_a")}
LV = {"115v": CONNECTOR, "240v": HEAVY, "1kv": ppg("insulator_600v_a"), "10kv": ppg("insulator_10kv_a"), "35kv": ppg("insulator_35kv_a"), "65kv": ppg("insulator_65kv_a")}
TRANSFORMERS = (("1kv", "115v", True), ("1kv", "240v", True), ("6900v", "1kv", True), ("10kv", "115v", True), ("10kv", "240v", True),
                ("35kv", "10kv", True), ("35kv", "115v", True), ("35kv", "240v", True), ("65kv", "10kv", False), ("65kv", "35kv", False),
                ("100kv", "35kv", False), ("100kv", "65kv", False))


def transformers():
    for hv, lv, has_b in TRANSFORMERS:
        a = "dc_transformer_%s_%s_a" % (hv, lv)
        shaped(a, ["H H", "CKC", "LPL"], {"H": HV[hv], "C": COIL, "K": IRON_BLOCK, "L": LV[lv], "P": IRON_PLATE}, 1, COIL)
        if has_b:
            convert(a, "dc_transformer_%s_%s_b" % (hv, lv))


def misc():
    shapeless("wire_clamp", [IRON_NUGGET, IRON_NUGGET, COPPER_PLATE], 2, COPPER_PLATE)
    shapeless("wire_clamp_open", [IRON_PLATE, COPPER_PLATE, IRON_NUGGET], 2, COPPER_PLATE)
    gauge = {"item": "powergrid:voltage_gauge"}
    shapeless("ac_synchroscope", [gauge, gauge, {"item": "minecraft:compass"}, IRON_PLATE], 1, gauge)
    shapeless("alternator_clutch", [{"item": "powergrid:generator_clutch"}, {"item": "create:cogwheel"}, IRON_PLATE], 1, {"item": "powergrid:generator_clutch"})
    shaped("alternator_out", ["HHH", "PCP"], {"H": HEAVY, "P": IRON_PLATE, "C": {"item": "powergrid:conductive_casing"}}, 1, HEAVY)


def main():
    for d in (RECIPES, ADVANCEMENTS):
        shutil.rmtree(d, ignore_errors=True)
    wires()
    insulators()
    breakers()
    operators()
    transformers()
    misc()
    print("%d Power Plant Grid recipes written" % len(os.listdir(RECIPES)))


if __name__ == "__main__":
    main()
