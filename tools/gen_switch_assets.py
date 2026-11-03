#!/usr/bin/env python3
"""Generates the pole-top switch and recloser resources: the insulator post, the contact head, the
handle and servo operators, and the recloser (models, blockstates, loot, recipes).

Run from the repository root:  python tools/gen_switch_assets.py

Geometry mirrors SwitchContactBlock.java, SwitchOperatorBlock.java and RecloserBlock.java; change
both. North frame, pixels: a head's load end faces north; a recloser's back is against the pole
at z = 16.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

TEX = os.path.join(ASSETS, "textures", "block")
FACINGS = (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}))
STEEL = "%s:block/cutout_steel" % MOD
LUG = "%s:block/cutout_lug" % MOD
PORCELAIN = "%s:block/cutout_porcelain_15kv" % MOD


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
    motor = (0x3a, 0x3c, 0x44)
    img = canvas(16, 16, motor + (255,))
    fill(img, 0, 0, 16, 1, shade(motor, 1.3))
    fill(img, 2, 6, 14, 10, shade(motor, 0.8))
    write_png(os.path.join(TEX, "servo_motor.png"), img)
    for name, rgb in (("lamp_red", (0xe0, 0x30, 0x30)), ("lamp_green", (0x40, 0xe0, 0x60))):
        img = canvas(16, 16, rgb + (255,))
        write_png(os.path.join(TEX, name + ".png"), img)


def insulator():
    tex = {"porcelain": PORCELAIN, "particle": PORCELAIN}
    elements = [element(5, 0, 5, 11, 16, 11, "#porcelain")]
    for y in (2, 6, 10, 14):
        elements.append(element(4, y, 4, 12, y + 1, 12, "#porcelain"))
    dump(os.path.join(ASSETS, "models", "block", "switch_insulator.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "switch_insulator.json"), {"parent": "%s:block/switch_insulator" % MOD})
    dump(os.path.join(ASSETS, "blockstates", "switch_insulator.json"), {"variants": {"": {"model": "%s:block/switch_insulator" % MOD}}})
    loot_table("switch_insulator")


def contact():
    tex = {"steel": STEEL, "lug": LUG, "porcelain": PORCELAIN, "particle": STEEL}
    elements = [element(4, 0, 2, 12, 2, 14, "#steel"), element(6.5, 2, 3, 9.5, 6, 6, "#steel"), element(6.5, 2, 10, 9.5, 9, 13, "#porcelain"),
                element(7.25, 9, 11, 8.75, 10.5, 13, "#lug"), element(7.25, 2, 2.5, 8.75, 3.5, 4.5, "#lug"),
                element(7, 4.5, 10.5, 9, 6, 12.5, "#lug")]   # the jaw the blade closes into
    dump(os.path.join(ASSETS, "models", "block", "switch_contact.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "switch_contact.json"), {"parent": "%s:block/switch_contact" % MOD})
    dump(os.path.join(ASSETS, "blockstates", "switch_contact.json"), {"variants": facing_variants(lambda p: "switch_contact")})
    loot_table("switch_contact")


def operators():
    base = [element(3, 0, 3, 13, 6, 13, "#steel")]
    tex = {"steel": STEEL, "motor": "%s:block/servo_motor" % MOD, "red": "%s:block/lamp_red" % MOD, "green": "%s:block/lamp_green" % MOD, "particle": STEEL}
    # The handle: a lever on a post, back when off, forward when on; a hasp on the post when locked.
    for on in ("false", "true"):
        for locked in ("false", "true"):
            elements = list(base) + [element(7, 6, 7, 9, 9, 9, "#steel")]
            if on == "true":
                elements.append(element(7.25, 8, 2, 8.75, 10, 8, "#steel"))
            else:
                elements.append(element(7.25, 8, 8, 8.75, 10, 14, "#steel"))
            if locked == "true":
                elements.append(element(5.5, 6, 5.5, 10.5, 7.5, 10.5, "#red"))
            dump(os.path.join(ASSETS, "models", "block", "switch_handle_%s_%s.json" % (on, locked)), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "blockstates", "switch_handle.json"), {"variants": facing_variants(lambda p: "switch_handle_%s_%s" % (p["powered"], p["locked"]), ("powered", "locked"))})
    dump(os.path.join(ASSETS, "models", "item", "switch_handle.json"), {"parent": "%s:block/switch_handle_false_false" % MOD})
    loot_table("switch_handle")
    # The servo: a motor box with a lamp, green closed, red open.
    for on in ("false", "true"):
        elements = list(base) + [element(4, 6, 5, 12, 12, 11, "#motor"), element(6, 12, 7, 10, 13, 9, "#green" if on == "true" else "#red")]
        dump(os.path.join(ASSETS, "models", "block", "switch_servo_%s.json" % on), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "blockstates", "switch_servo.json"), {"variants": facing_variants(lambda p: "switch_servo_%s" % p["powered"], ("powered",))})
    dump(os.path.join(ASSETS, "models", "item", "switch_servo.json"), {"parent": "%s:block/switch_servo_false" % MOD})
    loot_table("switch_servo")


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
    plate, nugget, ingot = {"tag": "c:plates/iron"}, {"tag": "c:nuggets/copper"}, {"tag": "c:ingots/copper"}
    recipe_shapeless("switch_insulator", [{"item": "minecraft:brown_terracotta"}, {"item": "minecraft:brown_terracotta"}], 2, {"items": "%s:switch_contact" % MOD})
    recipe_shapeless("switch_contact", [plate, ingot, nugget], 1, {"items": "%s:switch_insulator" % MOD})
    recipe("switch_handle", [" L ", "PIP"], {"L": {"item": "minecraft:lever"}, "P": plate, "I": {"tag": "c:ingots/iron"}}, 1, {"items": "%s:switch_contact" % MOD})
    recipe("switch_servo", [" R ", "PIP"], {"R": {"item": "minecraft:redstone"}, "P": plate, "I": {"item": "create:electron_tube"}}, 1, {"items": "%s:switch_contact" % MOD})
    recipe("recloser", ["CBC", "PRP", "III"], {"C": ingot, "B": {"item": "%s:breaker_200a_3p" % MOD}, "P": plate, "R": {"item": "minecraft:redstone"}, "I": {"tag": "c:ingots/iron"}}, 1, {"items": "%s:switch_contact" % MOD})


def main():
    textures()
    insulator()
    contact()
    operators()
    recloser()
    data()
    print("switch assets written")


if __name__ == "__main__":
    main()
