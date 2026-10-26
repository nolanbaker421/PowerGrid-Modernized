"""Writes the Ponder plots under assets/powergrid_modernized/ponder/ as vanilla structure files.

Each plot is a small list of blocks on a checkered floor. Ponder's camera looks in from the north
side, so fronts face north and the nearer things sit at low z. Block entities carry no data here: the
scenes install breakers, set aims and IDs from the script, so a plot is only geometry. Run from the
repo root: python tools/gen_ponder_schematics.py
"""
import gzip
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "powergrid_modernized", "ponder")
MOD = "powergrid_modernized"
DATA_VERSION = 3955  # 1.21.1

# ---- a minimal NBT writer ----

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY = range(12)


class Int:
    def __init__(self, v):
        self.v = v


def _tag_type(v):
    if isinstance(v, Int):
        return INT
    if isinstance(v, str):
        return STRING
    if isinstance(v, dict):
        return COMPOUND
    if isinstance(v, list):
        return LIST
    raise TypeError(type(v))


def _payload(out, t, v):
    if t == INT:
        out.append(struct.pack(">i", v.v))
    elif t == STRING:
        b = v.encode("utf-8")
        out.append(struct.pack(">H", len(b)))
        out.append(b)
    elif t == LIST:
        et = _tag_type(v[0]) if v else END
        out.append(struct.pack(">bi", et, len(v)))
        for x in v:
            _payload(out, et, x)
    elif t == COMPOUND:
        for k, x in v.items():
            xt = _tag_type(x)
            kb = k.encode("utf-8")
            out.append(struct.pack(">bH", xt, len(kb)))
            out.append(kb)
            _payload(out, xt, x)
        out.append(b"\x00")
    else:
        raise TypeError(t)


def write_nbt(path, root):
    out = [struct.pack(">bH", COMPOUND, 0)]
    _payload(out, COMPOUND, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(b"".join(out))


# ---- plots ----

class Plot:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.palette = []
        self.index = {}
        self.blocks = {}
        for x in range(sx):
            for z in range(sz):
                self.set(x, 0, z, "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:snow_block")

    def state(self, name, props=None):
        key = (name, tuple(sorted((props or {}).items())))
        if key not in self.index:
            self.index[key] = len(self.palette)
            entry = {"Name": name}
            if props:
                entry["Properties"] = {k: str(v) for k, v in sorted(props.items())}
            self.palette.append(entry)
        return self.index[key]

    def set(self, x, y, z, name, **props):
        self.blocks[(x, y, z)] = self.state(name, props)

    def fill(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, **props)

    def write(self, name):
        root = {
            "size": [Int(v) for v in self.size],
            "entities": [],
            "blocks": [{"pos": [Int(x), Int(y), Int(z)], "state": Int(s)} for (x, y, z), s in sorted(self.blocks.items())],
            "palette": self.palette,
            "DataVersion": Int(DATA_VERSION),
        }
        write_nbt(os.path.join(OUT, name + ".nbt"), root)
        print("wrote", name, "%d blocks, %d states" % (len(self.blocks), len(self.palette)))


def m(name):
    return MOD + ":" + name


def breaker_panel():
    p = Plot(5, 4, 5)
    p.fill(0, 1, 4, 4, 3, 4, "minecraft:light_gray_concrete")
    p.set(2, 2, 3, m("breaker_panel_200_2p"), facing="north", sections=1)
    p.set(0, 1, 1, m("conduit_box"), facing="up", cover="light_off")
    p.write("breaker_panel")


def radio_dish_link():
    p = Plot(7, 4, 7)
    p.fill(1, 1, 3, 1, 2, 3, "minecraft:stone_bricks")
    p.fill(5, 1, 3, 5, 2, 3, "minecraft:stone_bricks")
    p.set(1, 3, 3, m("dish_antenna"), face="floor", facing="east")
    p.set(5, 3, 3, m("dish_antenna"), face="floor", facing="west")
    p.set(1, 1, 1, m("radio"), facing="north")
    p.set(5, 1, 1, m("radio"), facing="north")
    p.write("radio_dish_link")


def radio_carrier():
    p = Plot(7, 5, 7)
    p.fill(3, 1, 3, 3, 3, 3, "minecraft:stone_bricks")
    p.set(3, 4, 3, m("sector_antenna"), face="floor", facing="north")
    p.set(1, 1, 3, m("radio"), facing="north")
    p.set(5, 1, 1, m("omni_antenna"), face="floor", facing="north")
    p.set(6, 1, 1, m("radio"), facing="north")
    p.write("radio_carrier")


def conduit():
    p = Plot(5, 4, 5)
    p.fill(0, 1, 4, 4, 3, 4, "minecraft:light_gray_concrete")
    for x in (1, 3):
        for y in (1, 2):
            p.set(x, y, 3, m("conduit_box"), facing="north", cover="blank")
    p.write("conduit")


if __name__ == "__main__":
    breaker_panel()
    radio_dish_link()
    radio_carrier()
    conduit()
