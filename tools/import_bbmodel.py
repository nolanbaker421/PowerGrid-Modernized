"""Imports Luna-Terra's Blockbench conduit box (a java_block .bbmodel, 32x32 texture) into the
pieces the asset generator builds the wall box models from: tools/conduit_box_luna.json holds one
element list per cover variant, converted to Minecraft's 0..16 UV space and pointed at the #box
texture, and the embedded texture is written to the block textures as conduit_box_detail.png.
Knockouts are not imported: the generator draws those from the terminal geometry, so a run meets
its knockout dead centre. Run:  python tools/import_bbmodel.py path/to/conduit_box_open.bbmodel
"""
import base64
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "powergrid_modernized")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "conduit_box_luna.json")
TEXTURE = os.path.join(ASSETS, "textures", "block", "conduit_box_detail.png")

# The outliner's unnamed groups, in order: node plate, open box, (blank cover is a loose element),
# socket plate, lamp fixture, knockouts.
GROUPS = {"node": 0, "open": 1, "light": 3}
BLANK_NAME = "blank cover"
BODY_TEXTURE = 0


def convert(element, scale):
    faces = {}
    for name, face in element["faces"].items():
        if face.get("texture") != BODY_TEXTURE:
            continue
        out = {"uv": [round(c * scale, 4) for c in face["uv"]], "texture": "#box"}
        if face.get("rotation"):
            out["rotation"] = face["rotation"]
        faces[name] = out
    out = {"from": element["from"], "to": element["to"], "faces": faces}
    if element.get("name"):
        out["name"] = element["name"]
    return out


def main(path):
    with open(path, encoding="utf-8") as f:
        model = json.load(f)
    scale = 16 / model["resolution"]["width"]
    elements = {e["uuid"]: e for e in model["elements"]}
    groups = [item for item in model["outliner"] if isinstance(item, dict)]
    loose = [elements[item] for item in model["outliner"] if not isinstance(item, dict)]
    variants = {}
    for key, index in GROUPS.items():
        variants[key] = [convert(elements[child], scale) for child in groups[index]["children"] if not isinstance(child, dict)]
    variants["blank"] = [convert(e, scale) for e in loose if e.get("name") == BLANK_NAME]
    for key, items in variants.items():
        assert items, "no elements for " + key
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": os.path.basename(path), "author": "Luna-Terra", "variants": variants}, f, indent=2)
        f.write("\n")
    texture = model["textures"][BODY_TEXTURE]["source"]
    prefix = "data:image/png;base64,"
    assert texture.startswith(prefix)
    with open(TEXTURE, "wb") as f:
        f.write(base64.b64decode(texture[len(prefix):]))
    print("imported", {k: len(v) for k, v in variants.items()}, "texture", model["textures"][BODY_TEXTURE]["name"])


if __name__ == "__main__":
    main(sys.argv[1])
