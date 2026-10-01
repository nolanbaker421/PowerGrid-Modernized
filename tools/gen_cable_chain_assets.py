"""Assets and data for the cable chain: the anchor block, the chain item, the link texture and the
wire type. Run from anywhere: python tools/gen_cable_chain_assets.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, fill, loot_table, recipe, shade, write_png

TEX = os.path.join(ASSETS, "textures")


def textures():
    steel, dark, brass = (120, 124, 130), (58, 60, 66), (190, 150, 70)
    plate = canvas(16, 16, steel + (255,))
    for x in range(0, 16, 4):
        fill(plate, x, 0, x + 1, 16, shade(steel, 0.9))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        fill(plate, x, y, x + 1, y + 1, shade(steel, 0.6))
    write_png(os.path.join(TEX, "block", "cable_chain_anchor.png"), plate)
    post = canvas(16, 16, dark + (255,))
    fill(post, 0, 0, 16, 1, shade(dark, 1.5))
    fill(post, 0, 15, 16, 16, shade(dark, 0.6))
    fill(post, 6, 4, 10, 12, shade(dark, 0.8))
    write_png(os.path.join(TEX, "block", "cable_chain_post.png"), post)
    # The link: dark steel with a worn highlight along one edge.
    link = canvas(16, 16, dark + (255,))
    fill(link, 0, 0, 16, 2, shade(dark, 1.6))
    fill(link, 0, 14, 16, 16, shade(dark, 0.5))
    fill(link, 7, 2, 9, 14, shade(dark, 0.75))
    write_png(os.path.join(TEX, "entity", "cable_chain.png"), link)
    # The item: a short run of chain with a bend.
    item = canvas(16, 16)
    for x in range(1, 9):
        fill(item, x, 11, x + 1, 13, dark if x % 2 else shade(dark, 1.4))
    for x in range(6, 15):
        fill(item, x, 3, x + 1, 5, dark if x % 2 else shade(dark, 1.4))
    for y in range(4, 12):
        fill(item, 12 if y < 8 else 11, y, 14 if y < 8 else 13, y + 1, dark if y % 2 else shade(dark, 1.4))
    fill(item, 1, 10, 3, 14, brass)
    fill(item, 13, 2, 15, 6, brass)
    write_png(os.path.join(TEX, "item", "cable_chain.png"), item)


def _wrap(a, b):
    """A UV span moved into 0..16: elements may sit outside the block, UVs may not."""
    span = min(16, abs(b - a))
    start = a % 16
    if start + span > 16:
        start = 16 - span
    return start, start + span


def element(x1, y1, z1, x2, y2, z2, texture):
    faces = {}
    for face, (u1, v1, u2, v2) in {
        "down": (x1, z1, x2, z2), "up": (x1, z1, x2, z2), "north": (x1, 16 - y2, x2, 16 - y1),
        "south": (x1, 16 - y2, x2, 16 - y1), "west": (z1, 16 - y2, z2, 16 - y1), "east": (z1, 16 - y2, z2, 16 - y1),
    }.items():
        u1, u2 = _wrap(u1, u2)
        v1, v2 = _wrap(v1, v2)
        faces[face] = {"uv": [u1, v1, u2, v2], "texture": texture}
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}


def models():
    elements = [
        element(2, 0, 2, 14, 2, 14, "#plate"),
        element(5, 2, 2, 11, 8, 6, "#post"),
        element(4, 4, 6, 12, 6, 7, "#post"),            # the saddle the chain lies on
        element(14, 0, 7, 16, 2, 9, "#jack"),
        element(0, 0, 6, 2, 2, 10, "#stud"),            # the conduit knockout
    ]
    for x in (2, 5, 9, 12):
        elements.append(element(x, 2, 11, x + 2, 4, 13, "#stud"))
    dump(os.path.join(ASSETS, "models", "block", "cable_chain_anchor.json"), {
        "parent": "block/block",
        "textures": {"plate": "%s:block/cable_chain_anchor" % MOD, "post": "%s:block/cable_chain_post" % MOD,
                     "stud": "%s:block/panel_terminal" % MOD, "jack": "%s:block/jack_pin" % MOD,
                     "particle": "%s:block/cable_chain_anchor" % MOD},
        "elements": elements,
    })
    # Same facing and rotation variants as the regulator, one model for both mountings.
    state = json.load(open(os.path.join(ASSETS, "blockstates", "vfd.json")))
    for variant in state["variants"].values():
        variant["model"] = "%s:block/cable_chain_anchor" % MOD
    dump(os.path.join(ASSETS, "blockstates", "cable_chain_anchor.json"), state)
    dump(os.path.join(ASSETS, "models", "item", "cable_chain_anchor.json"), {"parent": "%s:block/cable_chain_anchor" % MOD})
    dump(os.path.join(ASSETS, "models", "item", "cable_chain.json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/cable_chain" % MOD}})


def data():
    loot_table("cable_chain_anchor")
    recipe("cable_chain_anchor", ["I I", "PCP", "III"], {"I": {"tag": "c:plates/iron"}, "P": {"item": "powergrid:pins"},
                                                       "C": {"item": "powergrid:conductive_casing"}}, 2, {"items": "powergrid:conductive_casing"})
    recipe("cable_chain", ["C C", "ICI", "C C"], {"C": {"item": "minecraft:chain"}, "I": {"tag": "c:plates/iron"}}, 4, {"items": "minecraft:chain"})
    dump(os.path.join(DATA, "powergrid", "wire_types", "cable_chain.json"), {
        "colorable": False, "cord": False, "insulated": True,
        "horizontalCoefficient": 1.0, "verticalCoefficient": 1.0,
        "itemsPerMeter": 0.5, "maximumLength": 128.0, "maximumCurrent": 400.0,
        "resistancePerItem": 0.0005, "thermalMass": 50.0, "wireThickness": 0.2,
        "texture": "%s:textures/special/conduit.png" % MOD,
    })


def main():
    textures()
    models()
    data()
    print("cable chain assets written")


if __name__ == "__main__":
    main()
