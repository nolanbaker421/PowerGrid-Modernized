"""Assets and data for the electrical inspector, the license, copper scrap, the push broom and the
village Electrical Training Center. Run from anywhere: python tools/gen_inspector_assets.py"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, DATA, MOD, canvas, dump, fill, shade, write_png
import nbt_mini as nbt

TEX = os.path.join(ASSETS, "textures")


# ---------------------------------------------------------------- items

def license_card():
    img = canvas(16, 16)
    white, blue, gray, skin, gold = (245, 245, 240), (47, 111, 191), (150, 150, 150), (222, 184, 135), (255, 200, 40)
    fill(img, 1, 3, 15, 13, shade(white, 0.7))          # edge
    fill(img, 2, 4, 14, 12, white)
    fill(img, 2, 4, 14, 6, blue)                        # header stripe
    fill(img, 3, 7, 6, 11, skin)                        # photo
    fill(img, 3, 7, 6, 8, (90, 60, 40))                 # hair
    for y in (7, 9, 11):
        fill(img, 7, y, 13, y + 1, gray)                # text lines
    fill(img, 11, 4, 12, 5, gold); fill(img, 12, 5, 13, 6, gold)   # tiny bolt
    write_png(os.path.join(TEX, "item", "electrical_license.png"), img)


def copper_scrap():
    img = canvas(16, 16)
    cu, dark, bright = (196, 110, 60), (140, 70, 35), (230, 150, 90)
    strokes = [(2, 11, 6, 12), (5, 9, 9, 10), (8, 6, 12, 7), (3, 4, 6, 5), (10, 10, 14, 11), (7, 12, 11, 13), (11, 3, 13, 4)]
    for x1, y1, x2, y2 in strokes:
        fill(img, x1, y1, x2, y2, cu)
        fill(img, x1, y1, x1 + 1, y2, bright)
        fill(img, x2 - 1, y1, x2, y2, dark)
    for x, y in ((4, 8), (9, 8), (12, 5), (6, 3)):
        fill(img, x, y, x + 1, y + 1, dark)
    write_png(os.path.join(TEX, "item", "copper_scrap.png"), img)


def item_models():
    for name in ("electrical_license", "copper_scrap"):
        dump(os.path.join(ASSETS, "models", "item", name + ".json"),
             {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, name)}})
    for name in ("electrical_inspector_spawn_egg", "push_broom_spawn_egg"):
        dump(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/template_spawn_egg"})


# ---------------------------------------------------------------- the inspector's vest and hard hat (villager overlay, 64x64)

def inspector_overlay():
    img = canvas(64, 64)
    orange, yellow, white, grey = (255, 120, 20), (255, 230, 40), (240, 240, 240), (200, 200, 200)
    # Body faces (villager UV 16,20: 8 wide, 12 tall, 6 deep): sides 16..22 and 30..36, front 22..30, back 36..44, rows 26..38.
    fill(img, 16, 26, 44, 38, orange)
    fill(img, 16, 30, 44, 32, yellow)                   # reflective band
    fill(img, 16, 35, 44, 36, yellow)
    fill(img, 23, 26, 25, 38, yellow)                   # straps on the front
    fill(img, 27, 26, 29, 38, yellow)
    fill(img, 24, 32, 28, 34, white)                    # badge
    # Hat overlay (32,0: 8x10x8): top face 40..48 x 0..8, sides rows 8..18 across 32..64. A hard hat covers the top.
    fill(img, 40, 0, 48, 8, white)
    fill(img, 32, 8, 64, 12, white)
    fill(img, 32, 12, 64, 13, grey)                     # brim
    fill(img, 43, 8, 45, 12, yellow)                    # front stripe
    write_png(os.path.join(TEX, "entity", "electrical_inspector.png"), img)


# ---------------------------------------------------------------- the push broom (64x64, see PushBroomModel)

def push_broom_texture():
    img = canvas(64, 64)
    wood, wood_dark, straw, straw_dark, band = (170, 125, 70), (120, 85, 45), (222, 190, 90), (180, 145, 60), (60, 60, 60)
    # handle 2x24x2 at (0,0): faces 2 wide, top/bottom 2x2 at y 0..2, sides rows 2..26 across x 0..8
    fill(img, 2, 0, 6, 2, wood)
    fill(img, 0, 2, 8, 26, wood)
    for y in range(3, 26, 4):
        fill(img, 0, y, 8, y + 1, wood_dark)            # grain
    # head 16x3x4 at (0,30): top/bottom at x 4..36 rows 30..34, sides rows 34..37 across 0..40
    fill(img, 4, 30, 36, 34, wood)
    fill(img, 0, 34, 40, 37, wood_dark)
    fill(img, 0, 35, 40, 36, band)
    # bristles 16x4x4 at (0,40): top/bottom x 4..36 rows 40..44, sides rows 44..48 across 0..40
    fill(img, 4, 40, 36, 44, straw)
    fill(img, 0, 44, 40, 48, straw)
    for x in range(0, 40, 2):
        fill(img, x, 44, x + 1, 48, straw_dark)
    write_png(os.path.join(TEX, "entity", "push_broom.png"), img)


# ---------------------------------------------------------------- data

def loot():
    dump(os.path.join(DATA, "loot_table", "chests", "training_center.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s:electrical_license" % MOD}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
                {"type": "minecraft:item", "name": "%s:wire_12awg" % MOD, "weight": 4,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                {"type": "minecraft:item", "name": "%s:wire_14awg" % MOD, "weight": 4,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                {"type": "minecraft:item", "name": "%s:cat6_cable" % MOD, "weight": 2,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:item", "name": "%s:breaker_blank" % MOD, "weight": 2,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                {"type": "minecraft:item", "name": "%s:copper_scrap" % MOD, "weight": 3,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
                {"type": "minecraft:item", "name": "minecraft:book", "weight": 3},
                {"type": "minecraft:item", "name": "minecraft:paper", "weight": 3,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 1,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]},
            ]},
        ],
        "random_sequence": "%s:chests/training_center" % MOD,
    })
    dump(os.path.join(DATA, "loot_table", "entities", "push_broom.json"), {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:stick",
                                      "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 0, "max": 2}},
                                                    {"function": "minecraft:looting_enchant", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]}]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.25}],
             "entries": [{"type": "minecraft:item", "name": "%s:copper_scrap" % MOD}]},
        ],
        "random_sequence": "%s:entities/push_broom" % MOD,
    })
    # The annex templates pad the space west of the building with structure void, which placement
    # would otherwise write into the world as real blocks over the village centre.
    dump(os.path.join(DATA, "worldgen", "processor_list", "training_center_annex.json"), {
        # block_ignore takes block states, not ids: each entry is a {"Name": ...} object.
        "processors": [{"processor_type": "minecraft:block_ignore", "blocks": [{"Name": "minecraft:structure_void"}]}],
    })
    dump(os.path.join(DATA, "damage_type", "inspector.json"),
         {"message_id": "%s.inspector" % MOD, "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"})
    dump(os.path.join(DATA, "neoforge", "biome_modifier", "push_broom.json"), {
        "type": "neoforge:add_spawns",
        "biomes": "#minecraft:is_overworld",
        "spawners": {"type": "%s:push_broom" % MOD, "weight": 20, "minCount": 1, "maxCount": 2},
    })


# ---------------------------------------------------------------- the Electrical Training Center

SIZE = 9   # x and z; the door is on the west face (x = 0) at z = 4, as vanilla houses have it
HEIGHT = 9


def stair(facing, shape="straight"):
    return ("minecraft:spruce_stairs", {"facing": facing, "half": "bottom", "shape": shape, "waterlogged": "false"})


def roof_ring(inset):
    """Stairs around the ring at this inset, facing inward, with proper outer corners."""
    lo, hi = inset, SIZE - 1 - inset
    out = {}
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            on_x, on_z = x in (lo, hi), z in (lo, hi)
            if not (on_x or on_z):
                continue
            if on_x and on_z:
                if x == lo and z == lo: out[(x, z)] = stair("south", "outer_left")
                elif x == hi and z == lo: out[(x, z)] = stair("south", "outer_right")
                elif x == lo: out[(x, z)] = stair("north", "outer_right")
                else: out[(x, z)] = stair("north", "outer_left")
            elif x == lo: out[(x, z)] = stair("east")
            elif x == hi: out[(x, z)] = stair("west")
            elif z == lo: out[(x, z)] = stair("south")
            else: out[(x, z)] = stair("north")
    return out


def pane(axis):
    p = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
    if axis == "x":
        p["east"] = p["west"] = "true"
    else:
        p["north"] = p["south"] = "true"
    return ("minecraft:glass_pane", p)


def sign_text(lines):
    return {"has_glowing_text": nbt.Byte(0), "color": nbt.String("black"),
            "messages": nbt.TagList(nbt.TAG_STRING, [nbt.String(json.dumps({"text": t})) for t in lines])}


def training_center():
    blocks = {}   # (x, y, z) -> (name, props) or (name, props, nbt)

    def put(x, y, z, name, props=None, extra=None):
        blocks[(x, y, z)] = (name, props or {}, extra)

    last = SIZE - 1
    # Foundation and floor.
    for x in range(SIZE):
        for z in range(SIZE):
            interior = 0 < x < last and 0 < z < last
            put(x, 0, z, "minecraft:smooth_stone" if interior else "minecraft:cobblestone")
    put(0, 0, 4, "minecraft:jigsaw", {"orientation": "west_up"}, {
        "id": nbt.String("minecraft:jigsaw"), "name": nbt.String("minecraft:building_entrance"),
        "target": nbt.String("minecraft:building_entrance"), "pool": nbt.String("minecraft:empty"),
        "joint": nbt.String("aligned"),
        "final_state": nbt.String("minecraft:cobblestone_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
    })
    # Walls with log corners.
    for y in (1, 2, 3):
        for x in range(SIZE):
            for z in range(SIZE):
                if x in (0, last) or z in (0, last):
                    corner = x in (0, last) and z in (0, last)
                    put(x, y, z, "minecraft:stripped_spruce_log" if corner else "minecraft:stone_bricks", {"axis": "y"} if corner else None)
    put(0, 1, 4, "minecraft:oak_door", {"facing": "east", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    put(0, 2, 4, "minecraft:oak_door", {"facing": "east", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    for x, z, axis in ((4, 0, "x"), (4, last, "x"), (last, 2, "z"), (last, 6, "z"), (0, 2, "z"), (0, 6, "z")):
        put(x, 2, z, *pane(axis))
    put(4, 3, 0, "minecraft:yellow_terracotta")          # safety yellow over the windows
    put(4, 3, last, "minecraft:yellow_terracotta")
    put(last, 3, 4, "minecraft:yellow_terracotta")
    # Inside: shelves along the back, a lectern, benches, the chest with the licenses.
    for x in range(1, last):
        put(x, 1, 7, "minecraft:bookshelf")
        put(x, 2, 7, "minecraft:bookshelf")
    put(4, 1, 5, "minecraft:lectern", {"facing": "north", "has_book": "false", "powered": "false"})
    for z in (2, 3):
        put(2, 1, z, *stair("east"))
        put(4, 1, z, *stair("east"))
    put(1, 1, 1, "minecraft:crafting_table")
    put(7, 1, 4, "minecraft:chest", {"facing": "west", "type": "single", "waterlogged": "false"},
        {"id": nbt.String("minecraft:chest"), "LootTable": nbt.String("%s:chests/training_center" % MOD)})
    put(7, 1, 1, "minecraft:copper_block")
    put(7, 1, 2, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    put(1, 2, 2, "minecraft:wall_torch", {"facing": "east"})
    put(1, 2, 6, "minecraft:wall_torch", {"facing": "east"})
    put(7, 2, 6, "minecraft:wall_torch", {"facing": "west"})
    put(4, 2, 1, "minecraft:wall_torch", {"facing": "south"})
    put(1, 3, 4, "minecraft:oak_wall_sign", {"facing": "east", "waterlogged": "false"}, {
        "id": nbt.String("minecraft:sign"), "is_waxed": nbt.Byte(1),
        "front_text": sign_text(["Electrical", "Training", "Center", "Licenses inside"]),
        "back_text": sign_text(["", "", "", ""]),
    })
    # Roof: rings of stairs stepping in, planks between, a lightning rod on the peak.
    for inset, y in ((0, 4), (1, 5), (2, 6), (3, 7)):
        ring = roof_ring(inset)
        for x in range(inset, SIZE - inset):
            for z in range(inset, SIZE - inset):
                if (x, z) in ring:
                    put(x, y, z, *ring[(x, z)])
                else:
                    put(x, y, z, "minecraft:spruce_planks")
    put(4, 8, 4, "minecraft:lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})

    # Palette and block list; every cell in the box is written, air included, as vanilla does.
    palette = []
    index = {}

    def state(name, props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {"Name": nbt.String(name)}
            if props:
                entry["Properties"] = {k: nbt.String(v) for k, v in sorted(props.items())}
            palette.append(entry)
        return index[key]

    def write(name, offset):
        """The building shifted east by `offset`; everything west of it is structure void, so a village
        centre placed at the same origin (see VillageInjector) is left untouched."""
        out = nbt.TagList(nbt.TAG_COMPOUND)
        for x in range(SIZE + offset):
            for y in range(HEIGHT):
                for z in range(SIZE):
                    if x < offset:
                        name_, props, extra = "minecraft:structure_void", {}, None
                    else:
                        name_, props, extra = blocks.get((x - offset, y, z), ("minecraft:air", {}, None))
                    entry = {"pos": nbt.TagList(nbt.TAG_INT, [nbt.Int(x), nbt.Int(y), nbt.Int(z)]), "state": nbt.Int(state(name_, props))}
                    if extra:
                        entry["nbt"] = extra
                    out.append(entry)
        root = {
            "size": nbt.TagList(nbt.TAG_INT, [nbt.Int(SIZE + offset), nbt.Int(HEIGHT), nbt.Int(SIZE)]),
            "entities": nbt.TagList(nbt.TAG_END),
            "blocks": out,
            "palette": nbt.TagList(nbt.TAG_COMPOUND, list(palette)),
            "DataVersion": nbt.Int(3955),
        }
        path = os.path.join(DATA, "structure", "village", name + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.save(path, root)

    write("training_center", 0)
    # Annexes: two blocks clear of the widest centre template of each village type.
    for village, widest in (("plains", 11), ("desert", 17), ("savanna", 14), ("snowy", 12), ("taiga", 22)):
        write("training_center_annex_" + village, widest + 2)


def main():
    license_card()
    copper_scrap()
    item_models()
    inspector_overlay()
    push_broom_texture()
    loot()
    training_center()
    print("inspector assets written")


if __name__ == "__main__":
    main()
