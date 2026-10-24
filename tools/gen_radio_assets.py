#!/usr/bin/env python3
"""Generates the radio network resources: the radio and the meter collector (one small wall box),
the dish, sector and omni antennas, the cable hanger, the meter antenna and the coax item.

Run from the repository root:  python tools/gen_radio_assets.py

Geometry mirrors RadioBlock, AntennaBlock, CoaxHangerBlock and MeterAntennaBlock; change both.
Radio and collector: north frame, box against z = 16. Antennas: north frame, mast on the block's
floor. Hanger: wall frame (bracket against z = 16, clips along x), turned per facing with the same
rotations CoaxHangerBlock.toLocal applies. Meter antenna: the conduit box's directional frame.
"""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, loot_table, recipe, recipe_shapeless, shade, write_png

TEX = os.path.join(ASSETS, "textures", "block")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
FOUR = (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}))


def textures():
    grey = (0x74, 0x78, 0x80)
    box = canvas(16, 16, grey + (255,))
    fill(box, 0, 0, 16, 1, shade(grey, 1.2))
    fill(box, 0, 15, 16, 16, shade(grey, 0.6))
    write_png(os.path.join(TEX, "radio_side.png"), box)
    front = canvas(16, 16, grey + (255,))
    fill(front, 0, 0, 16, 1, shade(grey, 1.2))
    fill(front, 0, 15, 16, 16, shade(grey, 0.6))
    fill(front, 3, 3, 13, 6, (0x18, 0x1c, 0x22))               # status window
    fill(front, 4, 4, 6, 5, (0x40, 0xe0, 0x60))                 # link light
    fill(front, 7, 4, 9, 5, (0xe8, 0xc0, 0x20))
    write_png(os.path.join(TEX, "radio_front.png"), front)
    cfront = canvas(16, 16, grey + (255,))
    fill(cfront, 0, 0, 16, 1, shade(grey, 1.2))
    fill(cfront, 0, 15, 16, 16, shade(grey, 0.6))
    fill(cfront, 3, 3, 13, 8, (0x18, 0x1c, 0x22))
    for y in (4, 6):
        fill(cfront, 4, y, 12, y + 1, (0x60, 0xd8, 0x90))       # rows of readings
    write_png(os.path.join(TEX, "collector_front.png"), cfront)

    white = (0xe4, 0xe6, 0xe8)
    dish = canvas(16, 16, white + (255,))
    fill(dish, 0, 0, 16, 1, shade(white, 1.05))
    fill(dish, 0, 15, 16, 16, shade(white, 0.7))
    for r in (3, 6):
        fill(dish, 8 - r, 8 - r, 8 + r, 8 - r + 1, shade(white, 0.85))
        fill(dish, 8 - r, 8 + r - 1, 8 + r, 8 + r, shade(white, 0.85))
    write_png(os.path.join(TEX, "antenna_dish.png"), dish)
    mast = canvas(16, 16, (0x5a, 0x5e, 0x66, 255))
    fill(mast, 0, 0, 16, 1, (0x7a, 0x7e, 0x86))
    fill(mast, 0, 15, 16, 16, (0x3a, 0x3e, 0x46))
    write_png(os.path.join(TEX, "antenna_mast.png"), mast)
    panel = canvas(16, 16, (0xd8, 0xd8, 0xd4, 255))
    fill(panel, 0, 0, 16, 1, (0xe8, 0xe8, 0xe4))
    fill(panel, 0, 15, 16, 16, (0xa0, 0xa0, 0x9c))
    fill(panel, 2, 2, 14, 3, (0xb8, 0xb8, 0xb4))
    fill(panel, 2, 13, 14, 14, (0xb8, 0xb8, 0xb4))
    write_png(os.path.join(TEX, "antenna_panel.png"), panel)

    bracket = canvas(16, 16, (0x50, 0x54, 0x5c, 255))
    fill(bracket, 0, 0, 16, 1, (0x70, 0x74, 0x7c))
    fill(bracket, 0, 15, 16, 16, (0x30, 0x34, 0x3c))
    write_png(os.path.join(TEX, "coax_hanger.png"), bracket)
    clip = canvas(16, 16, (0x20, 0x20, 0x24, 255))
    fill(clip, 0, 0, 16, 1, (0x40, 0x40, 0x44))
    fill(clip, 4, 6, 12, 10, (0x90, 0x90, 0x98))
    write_png(os.path.join(TEX, "coax_clip.png"), clip)

    coax = canvas(16, 16)
    fill(coax, 2, 3, 14, 13, (0x1c, 0x1c, 0x1c))
    fill(coax, 4, 5, 12, 11, (0x3c, 0x3c, 0x3c))
    fill(coax, 6, 7, 10, 9, (0xb8, 0x73, 0x33))                 # the centre conductor
    fill(coax, 2, 3, 14, 4, (0x34, 0x34, 0x34))
    write_png(os.path.join(ITEM_TEX, "coax_cable.png"), coax)


def small_box(name, front):
    """The radio's box: body, two lugs low on the face, the jack high on the right, the coax stub on top."""
    body = element(3, 2, 10, 13, 14, 16, "#side", cull_south=True)
    body["faces"]["north"]["texture"] = "#front"
    elements = [body,
                element(9.5, 3, 9, 11.5, 5, 10, "#terminal"), element(4.5, 3, 9, 6.5, 5, 10, "#terminal"),
                element(10, 10.5, 9, 12.5, 12.5, 10, "#jack"),
                element(4.5, 14, 12, 6.5, 15.5, 14, "#clip"),
                element(8.5, 14, 12, 10.5, 16, 14, "#knockout")]
    dump(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "parent": "block/block",
        "textures": {"front": front, "side": "%s:block/radio_side" % MOD, "terminal": "%s:block/panel_terminal" % MOD,
                     "jack": "%s:block/jack_pin" % MOD, "clip": "%s:block/coax_clip" % MOD, "knockout": "%s:block/knockout_one" % MOD,
                     "particle": "%s:block/radio_side" % MOD},
        "elements": elements,
    })
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "%s:block/%s" % (MOD, name)})
    variants = {}
    for facing, rot in FOUR:
        v = {"model": "%s:block/%s" % (MOD, name)}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
    loot_table(name)


MOUNTS = {
    # Mount boxes and the coax port per attach face, north frame, as AntennaBlock.mountBoxes / portInFrame.
    "floor": ([(6, 0, 6, 10, 10, 10)], (8, 2, 10)),
    "wall": ([(6, 6, 14, 10, 10, 16), (7, 7, 9, 9, 9, 14)], (8, 6.5, 14.5)),
    "ceiling": ([(6, 6, 6, 10, 16, 10)], (8, 14, 10)),
}
PIVOTS = {"floor": (8, 10, 8), "wall": (8, 8, 9), "ceiling": (8, 6, 8)}


def head_elements(kind, face):
    """The head at rest for the omni (part of the model) and for the item icons; dish and panel heads are drawn by the renderer in play."""
    px, py, pz = PIVOTS[face]
    if kind == "omni_antenna":
        return [element(px - 1, py - 13, pz - 1, px + 1, py, pz + 1, "#panel")] if face == "ceiling" else [element(px - 1, py, pz - 1, px + 1, py + 13, pz + 1, "#panel")]
    if kind == "dish_antenna":
        return [element(px - 7, py - 6, pz - 2, px + 7, py + 6, pz - 1, "#dish"), element(px - 0.5, py - 4, pz - 5, px + 0.5, py - 2, pz - 2, "#mast"),
                element(px - 1, py - 1, pz - 6, px + 1, py + 1, pz - 4.5, "#mast")]
    return [element(px - 5, py - 7, pz - 3, px + 5, py + 7, pz - 1, "#panel")]


def antenna(name):
    tex = {"dish": "%s:block/antenna_dish" % MOD, "mast": "%s:block/antenna_mast" % MOD, "panel": "%s:block/antenna_panel" % MOD,
           "clip": "%s:block/coax_clip" % MOD, "particle": "%s:block/antenna_mast" % MOD}
    variants = {}
    for face, (boxes, port) in MOUNTS.items():
        elements = [element(*box, "#mast") for box in boxes]
        x, y, z = port
        elements.append(element(x - 0.75, y - 0.75, z - 0.75, x + 0.75, y + 0.75, z + 0.75, "#clip"))
        if name == "omni_antenna":
            elements += head_elements(name, face)
        model = "%s_%s" % (name, face)
        dump(os.path.join(ASSETS, "models", "block", model + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
        for facing, rot in FOUR:
            v = {"model": "%s:block/%s" % (MOD, model)}
            v.update(rot)
            variants["face=%s,facing=%s" % (face, facing)] = v
    dump(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
    # The icon: the floor mount with its head on.
    icon = [element(*box, "#mast") for box in MOUNTS["floor"][0]] + head_elements(name, "floor")
    dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "block/block", "textures": tex, "elements": icon})
    loot_table(name)


def antennas():
    for name in ("dish_antenna", "sector_antenna", "omni_antenna"):
        antenna(name)


def hanger():
    """Eight clips on a bar in the wall frame; the blockstate turns it as CoaxHangerBlock.toLocal does."""
    elements = [element(0.5, 7.5, 15, 15.5, 8.5, 16, "#bar")]
    for k in range(8):
        cx = 1.875 + 1.75 * k
        elements.append(element(cx - 0.6, 6.5, 13.5, cx + 0.6, 9.5, 15, "#clip"))
    tex = {"bar": "%s:block/coax_hanger" % MOD, "clip": "%s:block/coax_clip" % MOD, "particle": "%s:block/coax_hanger" % MOD}
    dump(os.path.join(ASSETS, "models", "block", "coax_hanger.json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", "coax_hanger.json"), {"parent": "%s:block/coax_hanger" % MOD})
    model = "%s:block/coax_hanger" % MOD
    variants = {}
    yrot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for horizontal in yrot:
        for facing, y in yrot.items():
            variants["facing=%s,horizontal=%s" % (facing, horizontal)] = {"model": model, "y": y} if y else {"model": model}
        variants["facing=up,horizontal=%s" % horizontal] = {"model": model, "x": 270, "y": yrot[horizontal]}
        variants["facing=down,horizontal=%s" % horizontal] = {"model": model, "x": 90, "y": yrot[horizontal]}
    dump(os.path.join(ASSETS, "blockstates", "coax_hanger.json"), {"variants": variants})
    loot_table("coax_hanger")


def meter_antenna():
    """The module as an item: a small box with a whip, plus the cabinet's second model with the whip on its door."""
    img = canvas(16, 16)
    fill(img, 4, 9, 12, 15, (0x74, 0x78, 0x80))
    fill(img, 4, 9, 12, 10, (0x90, 0x94, 0x9c))
    fill(img, 7, 1, 9, 9, (0xd8, 0xd8, 0xd4))
    fill(img, 7, 1, 9, 2, (0xe8, 0xe8, 0xe4))
    fill(img, 5, 11, 7, 13, (0x40, 0xe0, 0x60))
    write_png(os.path.join(ITEM_TEX, "meter_antenna.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "meter_antenna.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/meter_antenna" % MOD}})


def coax_item():
    dump(os.path.join(ASSETS, "models", "item", "coax_cable.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/coax_cable" % MOD}})
    # The scope: a short brass telescope with a clip under it.
    scope = canvas(16, 16)
    brass = (0xc8, 0x9c, 0x3c)
    fill(scope, 2, 6, 14, 10, brass)
    fill(scope, 2, 6, 14, 7, shade(brass, 1.3))
    fill(scope, 2, 9, 14, 10, shade(brass, 0.6))
    fill(scope, 11, 5, 14, 11, shade(brass, 0.8))           # the objective bell
    fill(scope, 12, 6, 13, 10, (0x40, 0x70, 0xa0))           # the lens
    fill(scope, 2, 7, 3, 9, (0x20, 0x20, 0x24))              # the eyepiece
    fill(scope, 6, 10, 10, 13, (0x3a, 0x3e, 0x46))           # the clip
    write_png(os.path.join(ITEM_TEX, "alignment_scope.png"), scope)
    dump(os.path.join(ASSETS, "models", "item", "alignment_scope.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/alignment_scope" % MOD}})


def cell_card_item():
    # A SIM: cream card with a gold contact pad and a clipped corner.
    card = canvas(16, 16)
    body = (0xe8, 0xe4, 0xd8)
    fill(card, 3, 2, 13, 14, body)
    fill(card, 3, 2, 4, 3, (0, 0, 0, 0))                     # the clipped corner
    fill(card, 3, 13, 13, 14, shade(body, 0.8))
    fill(card, 12, 2, 13, 14, shade(body, 0.85))
    gold = (0xd8, 0xa8, 0x30)
    fill(card, 5, 6, 10, 11, gold)
    fill(card, 5, 8, 10, 9, shade(gold, 0.7))
    fill(card, 7, 6, 8, 11, shade(gold, 0.7))
    fill(card, 5, 3, 11, 4, (0x30, 0x70, 0xb0))              # the carrier stripe
    write_png(os.path.join(ITEM_TEX, "cell_card.png"), card)
    dump(os.path.join(ASSETS, "models", "item", "cell_card.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/cell_card" % MOD}})


def recipes():
    iron = {"tag": "c:plates/iron"}
    copper = {"tag": "c:wires/copper"}
    nugget = {"tag": "c:nuggets/iron"}
    recipe("coax_cable", ["SCS", "CWC", "SCS"], {"S": {"item": "minecraft:string"}, "C": {"item": "minecraft:black_dye"}, "W": copper}, 8, {"items": "%s:cat6_cable" % MOD})
    recipe("radio", ["III", "RCR", "III"], {"I": iron, "R": {"item": "minecraft:comparator"}, "C": {"item": "%s:cat6_cable" % MOD}}, 1, {"items": "%s:cat6_cable" % MOD})
    recipe("collector", ["III", "RCR", "IMI"], {"I": iron, "R": {"item": "minecraft:comparator"}, "C": {"item": "%s:cat6_cable" % MOD}, "M": {"item": "%s:ct_cabinet" % MOD}}, 1,
           {"items": "%s:ct_cabinet" % MOD})
    recipe("dish_antenna", ["III", "ICI", " N "], {"I": iron, "C": copper, "N": nugget}, 1, {"items": "%s:radio" % MOD})
    recipe("sector_antenna", ["ICI", "ICI", " N "], {"I": iron, "C": copper, "N": nugget}, 1, {"items": "%s:radio" % MOD})
    recipe("omni_antenna", [" C ", " C ", " N "], {"C": copper, "N": nugget}, 2, {"items": "%s:radio" % MOD})
    recipe("coax_hanger", ["N N", "NNN"], {"N": nugget}, 4, {"items": "%s:coax_cable" % MOD})
    recipe_shapeless("meter_antenna", [{"item": "%s:omni_antenna" % MOD}, {"item": "minecraft:comparator"}, nugget], 1, {"items": "%s:ct_cabinet" % MOD})
    recipe_shapeless("alignment_scope", [{"item": "minecraft:spyglass"}, {"item": "minecraft:comparator"}, nugget], 1, {"items": "%s:dish_antenna" % MOD})
    recipe_shapeless("cell_card", [{"item": "minecraft:paper"}, {"item": "minecraft:comparator"}, {"tag": "c:nuggets/gold"}], 1, {"items": "%s:omni_antenna" % MOD})


def main():
    textures()
    small_box("radio", "%s:block/radio_front" % MOD)
    small_box("collector", "%s:block/collector_front" % MOD)
    antennas()
    hanger()
    meter_antenna()
    coax_item()
    cell_card_item()
    recipes()
    print("radio assets written")


if __name__ == "__main__":
    main()
