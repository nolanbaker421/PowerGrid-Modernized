"""Strut channel: twelve models (six mount faces, two axes each) built straight from the same
geometry as StrutBlock.bar, a blockstate naming each, the item model, recipe and loot table."""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, dump, element, recipe

AXES = {"x": 0, "y": 1, "z": 2}
FACINGS = {"down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1), "west": (0, -1), "east": (0, 1)}
THICK, WIDTH, LIP = 3.0, 3.0, 0.5


def axis_for(facing, axis):
    m = FACINGS[facing][0]
    if AXES[axis] != m:
        return axis
    return "x" if m == 1 else "y"


def channel(facing, axis):
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

    boxes.append(box(0, LIP, 8 - WIDTH / 2, 8 + WIDTH / 2))                        # base plate
    boxes.append(box(LIP, THICK, 8 - WIDTH / 2, 8 - WIDTH / 2 + LIP))              # lip
    boxes.append(box(LIP, THICK, 8 + WIDTH / 2 - LIP, 8 + WIDTH / 2))              # lip
    return boxes


def main():
    tex = {"steel": "%s:block/bus_duct" % MOD, "particle": "%s:block/bus_duct" % MOD}
    variants = {}
    for facing in FACINGS:
        for axis in AXES:
            real = axis_for(facing, axis)
            name = "strut_%s_%s" % (facing, real)
            if axis == real:
                dump(os.path.join(ASSETS, "models", "block", "strut", name + ".json"),
                     {"parent": "block/block", "textures": tex, "elements": [element(*b, "#steel") for b in channel(facing, real)]})
            variants["axis=%s,facing=%s" % (axis, facing)] = {"model": "%s:block/strut/%s" % (MOD, name)}
    dump(os.path.join(ASSETS, "blockstates", "strut.json"), {"variants": variants})
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
