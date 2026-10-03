"""Marks the AC-only data files with the powergrid_modernized:ac_fork condition, and makes their
pickaxe tag entries optional, so stock Power Grid skips them cleanly. Run after any generator that
rewrites them (gen_motor_drive_assets does so itself)."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import DATA, MOD, ROOT

AC_BLOCKS = ["three_phase_motor", "three_phase_drive", "synchroscope", "creative_ac_source", "fe_inverter"]
AC_RECIPES = ["three_phase_motor", "three_phase_drive", "synchroscope", "fe_inverter"]
CONDITION = [{"type": "%s:ac_fork" % MOD}]


def mark(path):
    if not os.path.exists(path):
        print("missing", path)
        return
    obj = json.load(open(path, encoding="utf-8"))
    obj["neoforge:conditions"] = CONDITION
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    for name in AC_RECIPES:
        mark(os.path.join(DATA, "recipe", "crafting", name + ".json"))
        mark(os.path.join(DATA, "advancement", "recipes", "misc", "crafting", name + ".json"))
    for name in AC_BLOCKS:
        mark(os.path.join(DATA, "loot_table", "blocks", name + ".json"))
    tag = os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json")
    obj = json.load(open(tag, encoding="utf-8"))
    values = []
    for entry in obj["values"]:
        ident = entry["id"] if isinstance(entry, dict) else entry
        if ident.split(":")[-1] in AC_BLOCKS:
            values.append({"id": ident, "required": False})
        else:
            values.append(entry)
    obj["values"] = values
    with open(tag, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")
    print("ac conditions applied")


if __name__ == "__main__":
    main()
