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


def strip_light():
    tex = {"enamel": "%s:block/strip_light_enamel" % MOD, "socket": "%s:block/strip_light_socket" % MOD,
           "knockout12": "%s:block/knockout_half" % MOD, "particle": "%s:block/strip_light_enamel" % MOD}
    elements = [
        element(4, 13, 0, 12, 16, 16, "#enamel"),        # the channel against the ceiling
        element(4, 12, 0, 5, 13, 16, "#enamel"),         # its two lips
        element(11, 12, 0, 12, 13, 16, "#enamel"),
        element(4, 12, 0, 12, 13, 1, "#enamel"),         # end caps
        element(4, 12, 15, 12, 13, 16, "#enamel"),
        element(6.5, 11, 6.5, 9.5, 13, 9.5, "#socket"),  # the lamp holder under the channel
        element(7, 13.5, -0.35, 9, 15.5, 0, "#knockout12"),  # the knockout, proud of the north cap
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
    # The HV bulb wears Power Grid's standard bulb model.
    dump(os.path.join(ASSETS, "models", "item", "hv_light_bulb.json"), {"parent": "powergrid:block/lamps/light_bulb"})


def data():
    plate, wire, nugget = {"tag": "c:plates/iron"}, {"item": "powergrid:wire"}, {"tag": "c:nuggets/iron"}
    recipe("strip_light", ["PPP", "WNW"], {"P": plate, "W": wire, "N": nugget}, 2, {"items": "powergrid:light_bulb"})
    recipe_shapeless("hv_light_bulb", [{"item": "powergrid:light_bulb"}, {"item": "minecraft:glass"}, {"tag": "c:nuggets/copper"}], 1, {"items": "powergrid:light_bulb"})


def main():
    textures()
    strip_light()
    data()
    print("light assets written")


if __name__ == "__main__":
    main()
