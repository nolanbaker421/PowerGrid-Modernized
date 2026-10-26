"""Wire trough: the gutter body, a knockout-mark model per face pattern, multipart blockstates for the
head and the extension, the item model, recipe and loot. Geometry mirrors TroughGeometry.java."""
import os

from gen_breaker_panel_assets import ASSETS, DATA, MOD, dump, element, recipe

BODY = (0, 4, 10, 16, 12, 16)
SIX_U = [2.5, 4.7, 6.9, 9.1, 11.3, 13.5]   # a breaker panel's six, so a trough over a panel lines up
PATTERNS = {
    "two": ("four", [4, 12], [13], 2.0),
    "four": ("three", [2.75, 6.25, 9.75, 13.25], [13], 1.5),
    "six": ("two", SIX_U, [13], 1.0),
    "twelve": ("three_quarter", SIX_U, [11.5, 14.5], 0.75),
}
FACINGS = (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}))


def marks(pattern, top):
    size, across, rows, half = PATTERNS[pattern]
    elements = []
    for z in rows:
        for u in across:
            x = 16 - u
            y1, y2 = (12, 13) if top else (3, 4)
            e = element(x - half, y1, z - half, x + half, y2, z + half, "#knockout")
            for face in e["faces"].values():
                face["texture"] = "#knockout"
            elements.append(e)
    return elements, size


def main():
    side = "%s:block/pull_box_side_h" % MOD
    body_tex = {"side": side, "front": "%s:block/pull_box_front" % MOD, "knockout": "%s:block/knockout_four" % MOD, "particle": side}
    body = element(*BODY, "#side")
    body["faces"]["north"]["texture"] = "#front"
    ends = [element(15, 6.5, 11.5, 16, 9.5, 14.5, "#knockout"), element(0, 6.5, 11.5, 1, 9.5, 14.5, "#knockout")]
    dump(os.path.join(ASSETS, "models", "block", "wire_trough", "body.json"),
         {"parent": "block/block", "textures": body_tex, "elements": [body] + ends})
    for pattern in PATTERNS:
        for top in (True, False):
            elements, size = marks(pattern, top)
            name = "%s_%s" % ("top" if top else "bottom", pattern)
            dump(os.path.join(ASSETS, "models", "block", "wire_trough", name + ".json"),
                 {"parent": "block/block", "textures": {"knockout": "%s:block/knockout_%s" % (MOD, size), "particle": side}, "elements": elements})
    multipart = []
    for facing, rot in FACINGS:
        apply = dict(rot)
        apply["model"] = "%s:block/wire_trough/body" % MOD
        multipart.append({"when": {"facing": facing}, "apply": apply})
        for pattern in PATTERNS:
            for face in ("top", "bottom"):
                apply = dict(rot)
                apply["model"] = "%s:block/wire_trough/%s_%s" % (MOD, face, pattern)
                multipart.append({"when": {"facing": facing, face: pattern}, "apply": apply})
    for block in ("wire_trough", "wire_trough_extension"):
        dump(os.path.join(ASSETS, "blockstates", block + ".json"), {"multipart": multipart})
    dump(os.path.join(ASSETS, "models", "item", "wire_trough.json"), {"parent": "%s:block/wire_trough/body" % MOD})
    recipe("wire_trough", ["PPP", "P P"], {"P": {"tag": "c:plates/iron"}}, 2, {"items": "%s:pull_box" % MOD})
    for block in ("wire_trough", "wire_trough_extension"):
        dump(os.path.join(DATA, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block",
            "pools": [{"bonus_rolls": 0.0, "conditions": [{"condition": "minecraft:survives_explosion"}],
                       "entries": [{"type": "minecraft:item", "name": "%s:wire_trough" % MOD}], "rolls": 1.0}],
            "random_sequence": "%s:blocks/%s" % (MOD, block),
        })


if __name__ == "__main__":
    main()
