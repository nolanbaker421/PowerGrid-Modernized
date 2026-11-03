#!/usr/bin/env python3
"""Generates the recloser's resources: textures, models, blockstate, loot and recipe.

Run from the repository root:  python tools/gen_switch_assets.py

Geometry mirrors RecloserBlock.java; change both. North frame, pixels: the recloser's back is
against the pole at z = 16. (Pole-top switches themselves are Create: Power Plant Grid's; see
gen_ppg_recipes.py.)
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, shade, write_png

TEX = os.path.join(ASSETS, "textures", "block")
FACINGS = (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}))
STEEL = "%s:block/cutout_steel" % MOD
LUG = "%s:block/cutout_lug" % MOD


def facing_variants(model_of, extra=()):
    """Blockstate variants over the four facings and any extra boolean properties, model_of(props) naming the model."""
    variants = {}
    combos = [{}]
    for prop in extra:
        combos = [dict(c, **{prop: v}) for c in combos for v in ("false", "true")]
    for props in combos:
        for facing, rot in FACINGS:
            v = {"model": "%s:block/%s" % (MOD, model_of(props))}
            v.update(rot)
            key = ",".join(["facing=%s" % facing] + ["%s=%s" % (p, props[p]) for p in extra])
            variants[key] = v
    return variants


def textures():
    tank = (0x70, 0x74, 0x7a)
    img = canvas(16, 16, tank + (255,))
    fill(img, 0, 0, 16, 1, shade(tank, 1.15))
    fill(img, 0, 15, 16, 16, shade(tank, 0.65))
    fill(img, 0, 7, 16, 8, shade(tank, 0.85))
    write_png(os.path.join(TEX, "recloser_tank.png"), img)
    for name, rgb in (("lamp_red", (0xe0, 0x30, 0x30)), ("lamp_green", (0x40, 0xe0, 0x60))):
        img = canvas(16, 16, rgb + (255,))
        write_png(os.path.join(TEX, name + ".png"), img)


def recloser():
    tex = {"tank": "%s:block/recloser_tank" % MOD, "lug": LUG, "steel": STEEL, "red": "%s:block/lamp_red" % MOD, "green": "%s:block/lamp_green" % MOD, "particle": "%s:block/recloser_tank" % MOD}
    for state in ("closed", "open", "lockout"):
        elements = [element(2.5, 2, 6, 13.5, 14, 16, "#tank")]
        for x in (4, 8, 12):
            elements.append(element(x - 0.75, 14, 9.5, x + 0.75, 15.5, 11.5, "#lug"))
            elements.append(element(x - 0.75, 0.5, 9.5, x + 0.75, 2, 11.5, "#lug"))
        # The lever on the viewer's right (-x), down when closed, up when open; the flag over it red on lockout.
        if state == "closed":
            elements.append(element(0.5, 3, 10, 2.5, 8, 12, "#steel"))
        else:
            elements.append(element(0.5, 8, 10, 2.5, 13, 12, "#steel"))
        elements.append(element(5, 4, 5.5, 11, 6, 6, "#red" if state == "lockout" else "#green"))
        dump(os.path.join(ASSETS, "models", "block", "recloser_%s.json" % state), {"parent": "block/block", "textures": tex, "elements": elements})
    model_of = lambda p: "recloser_lockout" if p["lockout"] == "true" else ("recloser_open" if p["open"] == "true" else "recloser_closed")
    dump(os.path.join(ASSETS, "blockstates", "recloser.json"), {"variants": facing_variants(model_of, ("lockout", "open"))})
    dump(os.path.join(ASSETS, "models", "item", "recloser.json"), {"parent": "%s:block/recloser_closed" % MOD})
    loot_table("recloser")


def data():
    plate, ingot = {"tag": "c:plates/iron"}, {"tag": "c:ingots/copper"}
    recipe("recloser", ["CBC", "PRP", "III"], {"C": ingot, "B": {"item": "%s:breaker_200a_3p" % MOD}, "P": plate, "R": {"item": "minecraft:redstone"}, "I": {"tag": "c:ingots/iron"}}, 1, {"items": "%s:breaker_200a_3p" % MOD})


def main():
    textures()
    recloser()
    data()
    print("recloser assets written")


if __name__ == "__main__":
    main()
