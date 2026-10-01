"""Blockstates, models and the texture for the rack and the pinion."""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "powergrid_modernized")


def write_png(path, pixels, size=16):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *pixels[y][x]) for x in range(size)) for y in range(size))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def dump(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def rack_texture():
    """Dark steel with a lighter tooth band and a few rivet-like highlights."""
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = 88 + ((x * 7 + y * 13) % 5) * 3
            if 5 <= y <= 10:
                shade += 24
            if y in (5, 10):
                shade -= 10
            if (x % 4 == 1) and y in (2, 13):
                shade += 36
            row.append((shade, shade + 2, shade + 6, 255))
        px.append(row)
    return px


def face(uv, tex="#rack"):
    return {"uv": uv, "texture": tex}


def element(frm, to):
    """A box textured with the rack steel on every face, UVs taken from its footprint."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    return {"from": frm, "to": to, "faces": {
        "north": face([x1, 16 - y2, x2, 16 - y1]), "south": face([x1, 16 - y2, x2, 16 - y1]),
        "east": face([z1, 16 - y2, z2, 16 - y1]), "west": face([z1, 16 - y2, z2, 16 - y1]),
        "up": face([x1, z1, x2, z2]), "down": face([x1, z1, x2, z2]),
    }}


def rack_model():
    """Bar along x with the teeth pointing up: a tall base so a cog in the block above meshes."""
    elements = [element([0, 0, 4], [16, 11, 12])]
    for x in range(0, 16, 4):
        elements.append(element([x, 11, 5], [x + 2, 16, 11]))
    return {
        "parent": "block/block",
        "textures": {"rack": "powergrid_modernized:block/rack", "particle": "powergrid_modernized:block/rack"},
        "elements": elements,
    }


def rack_v_model():
    """Bar along y on a wall behind it (south), teeth pointing north."""
    elements = [element([4, 0, 5], [12, 16, 16])]
    for y in range(0, 16, 4):
        elements.append(element([5, y, 0], [11, y + 2, 5]))
    return {
        "parent": "block/block",
        "textures": {"rack": "powergrid_modernized:block/rack", "particle": "powergrid_modernized:block/rack"},
        "elements": elements,
    }


def variant(model, x=0, y=0):
    v = {"model": "powergrid_modernized:block/" + model}
    if x:
        v["x"] = x
    if y:
        v["y"] = y
    return v


def main():
    write_png(os.path.join(ROOT, "textures", "block", "rack.png"), rack_texture())
    dump(os.path.join(ROOT, "models", "block", "rack.json"), rack_model())
    dump(os.path.join(ROOT, "models", "block", "rack_v.json"), rack_v_model())
    # x rotates the teeth-up model: 90 points them north, 180 down, 270 south; y then turns the bar.
    dump(os.path.join(ROOT, "blockstates", "rack.json"), {"variants": {
        "facing=up,axis=x": variant("rack"),
        "facing=up,axis=z": variant("rack", y=90),
        "facing=down,axis=x": variant("rack", x=180),
        "facing=down,axis=z": variant("rack", x=180, y=90),
        "facing=north,axis=x": variant("rack", x=90),
        "facing=south,axis=x": variant("rack", x=270),
        "facing=east,axis=z": variant("rack", x=90, y=90),
        "facing=west,axis=z": variant("rack", x=90, y=270),
        "facing=north,axis=y": variant("rack_v"),
        "facing=east,axis=y": variant("rack_v", y=90),
        "facing=south,axis=y": variant("rack_v", y=180),
        "facing=west,axis=y": variant("rack_v", y=270),
        # Impossible pairs (bar along its own facing) still need a model to load cleanly.
        "facing=up,axis=y": variant("rack"),
        "facing=down,axis=y": variant("rack", x=180),
        "facing=north,axis=z": variant("rack", x=90),
        "facing=south,axis=z": variant("rack", x=270),
        "facing=east,axis=x": variant("rack", x=90, y=90),
        "facing=west,axis=x": variant("rack", x=90, y=270),
    }})
    dump(os.path.join(ROOT, "models", "item", "rack.json"), {"parent": "powergrid_modernized:block/rack"})

    # The pinion is drawn with Create's own small cogwheel; the visual or block entity renderer spins it.
    dump(os.path.join(ROOT, "models", "block", "pinion.json"), {"parent": "create:block/cogwheel"})
    dump(os.path.join(ROOT, "blockstates", "pinion.json"), {"variants": {
        "axis=x": variant("pinion", x=90, y=90),
        "axis=y": variant("pinion"),
        "axis=z": variant("pinion", x=90, y=180),
    }})
    dump(os.path.join(ROOT, "models", "item", "pinion.json"), {"parent": "powergrid_modernized:block/pinion"})
    print("rack assets written")


if __name__ == "__main__":
    main()
