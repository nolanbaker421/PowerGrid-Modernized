#!/usr/bin/env python3
"""Generates the strip light's resources: textures, the channel model with its end knockout, the
blockstate, item models (the fixture, the HV bulb on Power Grid's bulb model), the glow block's
empty model, loot and recipes.

Run from the repository root:  python tools/gen_light_assets.py

Geometry mirrors StripLightBlock.java; change both. North frame, pixels: the channel hangs from the
ceiling (y 12..16) running north-south, the knockout on the north end cap.
"""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

TEX = os.path.join(ASSETS, "textures", "block")


def textures():
    enamel = (0xF2, 0xF2, 0xEE)
    img = canvas(16, 16, enamel + (255,))
    fill(img, 0, 0, 16, 1, shade(enamel, 1.03))
    fill(img, 0, 15, 16, 16, shade(enamel, 0.82))
    write_png(os.path.join(TEX, "strip_light_enamel.png"), img)
    socket = (0xD8, 0xD8, 0xD4)
    img = canvas(16, 16, socket + (255,))
    fill(img, 0, 7, 16, 9, shade(socket, 0.8))
    write_png(os.path.join(TEX, "strip_light_socket.png"), img)
    canopy = (0xB8, 0xBC, 0xC2)
    img = canvas(16, 16, canopy + (255,))
    fill(img, 0, 0, 16, 1, shade(canopy, 1.08))
    fill(img, 0, 15, 16, 16, shade(canopy, 0.85))
    write_png(os.path.join(TEX, "fixture_canopy.png"), img)


def strip_light():
    tex = {"enamel": "%s:block/strip_light_enamel" % MOD, "socket": "%s:block/strip_light_socket" % MOD,
           "knockout12": "%s:block/knockout_half" % MOD, "particle": "%s:block/strip_light_enamel" % MOD}
    elements = [
        element(4, 13, 0, 12, 16, 16, "#enamel"),        # the channel against the ceiling
        element(4, 12, 0, 5, 13, 16, "#enamel"),         # its two lips
        element(11, 12, 0, 12, 13, 16, "#enamel"),
        element(4, 12, 0, 12, 13, 1, "#enamel"),         # end caps
        element(4, 12, 15, 12, 13, 16, "#enamel"),
        element(5, 11, 0, 8, 13, 1.5, "#socket"),        # the tube holders at each end, two per end
        element(8, 11, 0, 11, 13, 1.5, "#socket"),
        element(5, 11, 14.5, 8, 13, 16, "#socket"),
        element(8, 11, 14.5, 11, 13, 16, "#socket"),
        element(7, 13.5, -0.35, 9, 15.5, 0, "#knockout12"),  # a knockout proud of each end cap
        element(7, 13.5, 16, 9, 15.5, 16.35, "#knockout12"),
    ]
    dump(os.path.join(ASSETS, "models", "block", "strip_light.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "strip_light.json"), {"parent": "%s:block/strip_light" % MOD})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/strip_light" % MOD}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", "strip_light.json"), {"variants": variants})
    loot_table("strip_light")
    # The glow: a block with nothing to draw.
    dump(os.path.join(ASSETS, "models", "block", "strip_light_glow.json"), {"textures": {"particle": "minecraft:block/glass"}})
    dump(os.path.join(ASSETS, "blockstates", "strip_light_glow.json"), {"variants": {"": {"model": "%s:block/strip_light_glow" % MOD}}})
    # The custom fixture draws its design itself; the block model is the canopy plate at its mount with its
    # three knockouts, one model per mount and box position: the box sits on a three by three grid across the
    # mounting face, four pixels a step. Mirrors CustomFixtureBlock.canopy(), hubs() and boxOffset(); change both.
    canopy_tex = {"canopy": "%s:block/fixture_canopy" % MOD, "knockout12": "%s:block/knockout_half" % MOD, "particle": "%s:block/fixture_canopy" % MOD}
    mounts = {
        "ceiling": ((4, 13, 4, 12, 16, 12), [(7, 15.65, 7, 9, 16, 9), (12, 13.5, 7, 12.35, 15.5, 9), (3.65, 13.5, 7, 4, 15.5, 9)]),
        "wall": ((4, 4, 13, 12, 12, 16), [(7, 7, 15.65, 9, 9, 16), (7, 12, 13.5, 9, 12.35, 15.5), (7, 3.65, 13.5, 9, 4, 15.5)]),
        "floor": (None, [(7, 0, 7, 9, 0.35, 9), (12, 0, 7, 12.35, 2, 9), (3.65, 0, 7, 4, 2, 9)]),   # no plate: the design's base is the canopy
    }
    parts = []
    for mount, (canopy, kos) in mounts.items():
        stale = os.path.join(ASSETS, "models", "block", "custom_fixture_%s.json" % mount)
        if os.path.exists(stale):
            os.remove(stale)
        for box in range(9):
            u, v = (box // 3 - 1) * 4, (box % 3 - 1) * 4
            dx, dy, dz = (u, v, 0) if mount == "wall" else (u, 0, v)
            moved = lambda b: (b[0] + dx, b[1] + dy, b[2] + dz, b[3] + dx, b[4] + dy, b[5] + dz)
            elements = ([element(*moved(canopy), "#canopy")] if canopy else []) + [element(*moved(ko), "#knockout12") for ko in kos]
            name = "custom_fixture_%s_%d" % (mount, box)
            dump(os.path.join(ASSETS, "models", "block", name + ".json"), {"parent": "block/block", "textures": canopy_tex, "elements": elements})
            for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
                apply = {"model": "%s:block/%s" % (MOD, name)}
                apply.update(rot)
                parts.append({"when": {"mount": mount, "facing": facing, "box": str(box)}, "apply": apply})
    dump(os.path.join(ASSETS, "blockstates", "custom_fixture.json"), {"multipart": parts})
    ITEM_TEX = os.path.join(ASSETS, "textures", "item")
    # Custom fixture icon: a pendant, chain and shade with a lit bulb.
    img = canvas(16, 16, (0, 0, 0, 0))
    fill(img, 6, 0, 10, 1, (0xb8, 0xbc, 0xc2))
    fill(img, 7, 1, 9, 6, (0x8e, 0x92, 0x98))
    fill(img, 5, 6, 11, 8, (0x2e, 0x7d, 0x6b))
    fill(img, 3, 8, 13, 10, (0x2e, 0x7d, 0x6b))
    fill(img, 6, 10, 10, 14, (0xff, 0xf0, 0xa0))
    fill(img, 7, 14, 9, 15, (0xe8, 0xc8, 0x40))
    write_png(os.path.join(ITEM_TEX, "custom_fixture.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "custom_fixture.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/custom_fixture" % MOD}})
    # Designer icon: a drafting tablet with a sketched lamp.
    img = canvas(16, 16, (0, 0, 0, 0))
    fill(img, 1, 1, 15, 15, (0x3a, 0x3c, 0x40))
    fill(img, 2, 2, 14, 14, (0xe8, 0xe8, 0xe0))
    fill(img, 7, 3, 9, 6, (0x50, 0x50, 0x58))
    fill(img, 4, 6, 12, 8, (0x50, 0x50, 0x58))
    fill(img, 6, 8, 10, 11, (0xe8, 0xc2, 0x1e))
    write_png(os.path.join(ITEM_TEX, "fixture_designer.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "fixture_designer.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/fixture_designer" % MOD}})
    # Blank icon: a bare canopy with a socket.
    img = canvas(16, 16, (0, 0, 0, 0))
    fill(img, 3, 2, 13, 5, (0xb8, 0xbc, 0xc2))
    fill(img, 6, 5, 10, 9, (0x8e, 0x92, 0x98))
    fill(img, 5, 9, 11, 12, (0x3a, 0x3c, 0x40))
    write_png(os.path.join(ITEM_TEX, "fixture_blank.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "fixture_blank.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/fixture_blank" % MOD}})
    # The HV bulb wears Power Grid's standard bulb model.
    dump(os.path.join(ASSETS, "models", "item", "hv_light_bulb.json"), {"parent": "powergrid:block/lamps/light_bulb"})


def data():
    plate, wire, nugget = {"tag": "c:plates/iron"}, {"item": "powergrid:wire"}, {"tag": "c:nuggets/iron"}
    recipe("strip_light", ["PPP", "WNW"], {"P": plate, "W": wire, "N": nugget}, 2, {"items": "powergrid:light_bulb"})
    recipe("fixture_designer", ["PPP", "PBP", "PRP"], {"P": {"item": "minecraft:paper"}, "B": {"item": "minecraft:black_dye"}, "R": {"item": "minecraft:redstone"}}, 1, {"items": "powergrid:light_bulb"})
    recipe("fixture_blank", ["I I", "WSW"], {"I": plate, "W": wire, "S": {"tag": "c:nuggets/iron"}}, 2, {"items": "%s:fixture_designer" % MOD})
    recipe_shapeless("hv_light_bulb", [{"item": "powergrid:light_bulb"}, {"item": "minecraft:glass"}, {"tag": "c:nuggets/copper"}], 1, {"items": "powergrid:light_bulb"})


def main():
    textures()
    strip_light()
    data()
    print("light assets written")


if __name__ == "__main__":
    main()
