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


def rack_model():
    """Bar along x: base plate plus teeth standing up out of it."""
    elements = [{
        "from": [0, 0, 4], "to": [16, 3, 12],
        "faces": {
            "north": face([0, 13, 16, 16]), "south": face([0, 13, 16, 16]),
            "east": face([4, 13, 12, 16]), "west": face([4, 13, 12, 16]),
            "up": face([0, 4, 16, 12]), "down": face([0, 4, 16, 12]),
        },
    }]
    for x in range(0, 16, 4):
        elements.append({
            "from": [x, 3, 5], "to": [x + 2, 6, 11],
            "faces": {
                "north": face([x, 5, x + 2, 8]), "south": face([x, 5, x + 2, 8]),
                "east": face([5, 5, 11, 8]), "west": face([5, 5, 11, 8]),
                "up": face([x, 5, x + 2, 11]),
            },
        })
    return {
        "parent": "block/block",
        "textures": {"rack": "powergrid_modernized:block/rack", "particle": "powergrid_modernized:block/rack"},
        "elements": elements,
    }


def main():
    write_png(os.path.join(ROOT, "textures", "block", "rack.png"), rack_texture())
    dump(os.path.join(ROOT, "models", "block", "rack.json"), rack_model())
    dump(os.path.join(ROOT, "blockstates", "rack.json"), {"variants": {
        "axis=x": {"model": "powergrid_modernized:block/rack"},
        "axis=z": {"model": "powergrid_modernized:block/rack", "y": 90},
        "axis=y": {"model": "powergrid_modernized:block/rack", "x": 90, "y": 90},
    }})
    dump(os.path.join(ROOT, "models", "item", "rack.json"), {"parent": "powergrid_modernized:block/rack"})

    # The pinion is drawn with Create's own small cogwheel; the block entity renderer spins it.
    dump(os.path.join(ROOT, "models", "block", "pinion.json"), {"parent": "create:block/cogwheel"})
    dump(os.path.join(ROOT, "blockstates", "pinion.json"), {"variants": {
        "axis=x": {"model": "powergrid_modernized:block/pinion", "x": 90, "y": 90},
        "axis=y": {"model": "powergrid_modernized:block/pinion"},
        "axis=z": {"model": "powergrid_modernized:block/pinion", "x": 90, "y": 180},
    }})
    dump(os.path.join(ROOT, "models", "item", "pinion.json"), {"parent": "powergrid_modernized:block/pinion"})
    print("rack assets written")


if __name__ == "__main__":
    main()
