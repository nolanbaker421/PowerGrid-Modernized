#!/usr/bin/env python3
"""Generates the few crafting recipes Create: Power Plant Grid (modid powerplantgrid) leaves out.
Since its 0.1.4 that mod has a crafting progression of its own (fired porcelain, drawn wire,
wound coils, assembled insulator strings and breakers), so everything this once supplied for it
is gone; only its synchroscope and alternator parts, which still have no recipe there, remain.
Every recipe and its unlock advancement carries a neoforge:mod_loaded condition, so nothing here
exists unless that mod is installed.

Run from the repository root:  python tools/gen_ppg_recipes.py

Output: data/powergrid_modernized/recipe/ppg/<name>.json and the matching advancement under
advancement/recipes/misc/ppg/. Described in docs/USER_GUIDE.md under "Power Plant Grid recipes".
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


# ---------------------------------------------------------------- what Power Plant Grid 0.1.4 still has no recipe for
def misc():
    gauge = {"item": "powergrid:voltage_gauge"}
    shapeless("ac_synchroscope", [gauge, gauge, {"item": "minecraft:compass"}, IRON_PLATE], 1, gauge)
    shapeless("alternator_clutch", [{"item": "powergrid:generator_clutch"}, {"item": "create:cogwheel"}, IRON_PLATE], 1, {"item": "powergrid:generator_clutch"})
    shaped("alternator_out", ["HHH", "PCP"], {"H": HEAVY, "P": IRON_PLATE, "C": {"item": "powergrid:conductive_casing"}}, 1, HEAVY)


def main():
    for d in (RECIPES, ADVANCEMENTS):
        shutil.rmtree(d, ignore_errors=True)
    misc()
    print("%d Power Plant Grid recipes written" % len(os.listdir(RECIPES)))


if __name__ == "__main__":
    main()
