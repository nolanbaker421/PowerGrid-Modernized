"""Strut channel: twelve models (six mount faces, two axes each) built straight from the same
geometry as StrutBlock.bar, a blockstate naming each, the item model, recipe and loot table."""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, dump, element, recipe

AXES = {"x": 0, "y": 1, "z": 2}
# Slot patterns along the rail, in pixels, with the knockout colour of the size they take; mirrors StrutBlock.Slots.
SLOTS = {
    "two": ("four", [4, 12]),
    "four": ("three", [2.75, 6.25, 9.75, 13.25]),
    "six": ("two", [2.5, 4.7, 6.9, 9.1, 11.3, 13.5]),
    "eight": ("three_quarter", [1.5, 3.5, 5.5, 7.5, 9.5, 11.5, 13.5, 15.5]),
}
STRAP = 0.3
FACINGS = {"down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1), "west": (0, -1), "east": (0, 1)}
THICK, WIDTH, LIP = 3.0, 3.0, 0.5
TIER = 8.0   # the upper rail's distance from the mount; mirrors StrutBlock.TIER


def axis_for(facing, axis):
    m = FACINGS[facing][0]
    if AXES[axis] != m:
        return axis
    return "x" if m == 1 else "y"


def channel(facing, axis, offset=0.0):
    """The channel as boxes: a base plate on the mount face and two lips along the rail, open away from the mount."""
    m, sign = FACINGS[facing]
    a = AXES[axis_for(facing, axis)]
    across = 3 - m - a
    boxes = []

    def box(lo_m, hi_m, lo_c, hi_c):
        lo, hi = [0.0, 0.0, 0.0], [16.0, 16.0, 16.0]
        if sign < 0:
            lo[m], hi[m] = lo_m, hi_m
        else:
            lo[m], hi[m] = 16 - hi_m, 16 - lo_m
        lo[across], hi[across] = lo_c, hi_c
        return (lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])

    boxes.append(box(offset, offset + LIP, 8 - WIDTH / 2, 8 + WIDTH / 2))                        # base plate
    boxes.append(box(offset + LIP, offset + THICK, 8 - WIDTH / 2, 8 - WIDTH / 2 + LIP))          # lip
    boxes.append(box(offset + LIP, offset + THICK, 8 + WIDTH / 2 - LIP, 8 + WIDTH / 2))          # lip
    return boxes


def post(facing, axis, lo_m, hi_m):
    """A square post between rails, or from the mount to a raised rail, at the rail's centre line."""
    m, sign = FACINGS[facing]
    a = AXES[axis_for(facing, axis)]
    across = 3 - m - a
    lo, hi = [0.0, 0.0, 0.0], [16.0, 16.0, 16.0]
    if sign < 0:
        lo[m], hi[m] = lo_m, hi_m
    else:
        lo[m], hi[m] = 16 - hi_m, 16 - lo_m
    lo[across], hi[across] = 8 - 0.75, 8 + 0.75
    lo[a], hi[a] = 7.25, 8.75
    return [(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])]


def straps(facing, axis, slots, offset=0.0):
    """A strap across the rail's lips at every slot, in the colour of the size the pattern takes."""
    m, sign = FACINGS[facing][0], FACINGS[facing][1]
    a = AXES[axis_for(facing, axis)]
    across = 3 - m - a
    boxes = []
    for u in SLOTS[slots][1]:
        lo, hi = [0.0, 0.0, 0.0], [16.0, 16.0, 16.0]
        lo[a], hi[a] = u - 0.5, u + 0.5
        lo[across], hi[across] = 8 - WIDTH / 2, 8 + WIDTH / 2
        if sign < 0:
            lo[m], hi[m] = offset + THICK, offset + THICK + STRAP
        else:
            lo[m], hi[m] = 16 - offset - THICK - STRAP, 16 - offset - THICK
        boxes.append((lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]))
    return boxes


def main():
    tex = {"steel": "%s:block/bus_duct" % MOD, "particle": "%s:block/bus_duct" % MOD}
    multipart = []
    for facing in FACINGS:
        for axis in AXES:
            real = axis_for(facing, axis)
            name = "strut_%s_%s" % (facing, real)
            if axis == real:
                dump(os.path.join(ASSETS, "models", "block", "strut", name + ".json"),
                     {"parent": "block/block", "textures": tex, "elements": [element(*b, "#steel") for b in channel(facing, real)]})
                dump(os.path.join(ASSETS, "models", "block", "strut", name + "_upper.json"),
                     {"parent": "block/block", "textures": tex, "elements": [element(*b, "#steel") for b in channel(facing, real, TIER)]})
                dump(os.path.join(ASSETS, "models", "block", "strut", name + "_post_double.json"),
                     {"parent": "block/block", "textures": tex, "elements": [element(*b, "#steel") for b in post(facing, real, THICK, TIER)]})
                dump(os.path.join(ASSETS, "models", "block", "strut", name + "_post_raised.json"),
                     {"parent": "block/block", "textures": tex, "elements": [element(*b, "#steel") for b in post(facing, real, 0, TIER)]})
                for slots, (size, _) in SLOTS.items():
                    strap_tex = {"strap": "%s:block/knockout_%s" % (MOD, size), "particle": "%s:block/bus_duct" % MOD}
                    dump(os.path.join(ASSETS, "models", "block", "strut", "%s_%s.json" % (name, slots)),
                         {"parent": "block/block", "textures": strap_tex, "elements": [element(*b, "#strap") for b in straps(facing, real, slots)]})
                    dump(os.path.join(ASSETS, "models", "block", "strut", "%s_%s_upper.json" % (name, slots)),
                         {"parent": "block/block", "textures": strap_tex, "elements": [element(*b, "#strap") for b in straps(facing, real, slots, TIER)]})
            base = {"facing": facing, "axis": axis}
            model = "%s:block/strut/%s" % (MOD, name)
            multipart.append({"when": dict(base, stack="single|double"), "apply": {"model": model}})
            multipart.append({"when": dict(base, stack="double|raised"), "apply": {"model": model + "_upper"}})
            multipart.append({"when": dict(base, stack="double"), "apply": {"model": model + "_post_double"}})
            multipart.append({"when": dict(base, stack="raised"), "apply": {"model": model + "_post_raised"}})
            for slots in SLOTS:
                multipart.append({"when": dict(base, slots=slots, stack="single|double"), "apply": {"model": "%s_%s" % (model, slots)}})
                multipart.append({"when": dict(base, slots=slots, stack="double|raised"), "apply": {"model": "%s_%s_upper" % (model, slots)}})
    dump(os.path.join(ASSETS, "blockstates", "strut.json"), {"multipart": multipart})
    dump(os.path.join(ASSETS, "models", "item", "strut.json"), {"parent": "%s:block/strut/strut_down_x" % MOD})
    recipe("strut", ["P P", "PPP"], {"P": {"tag": "c:plates/iron"}}, 6, {"items": "%s:conduit_box" % MOD})
    dump(os.path.join(DATA, "loot_table", "blocks", "strut.json"), {
        "type": "minecraft:block",
        "pools": [{"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "%s:strut" % MOD}], "rolls": 1.0}],
        "random_sequence": "%s:blocks/strut" % MOD,
    })
    print("strut assets written")


if __name__ == "__main__":
    main()
