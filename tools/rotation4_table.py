#!/usr/bin/env python3
"""Derives the blockstate rotation table for Power Grid's four-way (Rotation4) blocks and rewrites
the affected blockstates so the models line up with the terminal boxes in every orientation.

Run from the repository root:  python tools/rotation4_table.py

Power Grid rotates a block's terminals from the floor ("facing=down") frame with its own
90-degree steps; Minecraft rotates a model with the blockstate's x then y. Both turn out to be the
same clockwise steps about each axis, so every state's terminal transform can be reproduced by
choosing the floor model (block_v) or the wall model (block_h, which is block_v turned a quarter
turn about z) and an x, y pair. This script searches those and writes tools/rotation4.json, which
the asset generators read, then rewrites every blockstate that has a rotation property.
"""
import itertools
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BLOCKSTATES = os.path.join(ROOT, "src", "main", "resources", "assets", "powergrid_modernized", "blockstates")
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "powergrid_modernized", "models", "block")
TABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "rotation4.json")


def cw(axis, p):
    x, y, z = p
    if axis == "x":
        return (x, z, 1 - y)
    if axis == "y":
        return (1 - z, y, x)
    return (y, 1 - x, z)


def rot(axis, angle, p):
    for _ in range((angle // 90) % 4):
        p = cw(axis, p)
    return p


def powergrid(facing, r, p):
    """TerminalBoundingBox transform Rotation4ElectricBlock applies for a state, from the floor frame."""
    if facing == "down":
        return rot("y", 90 * r - 90, p)
    if facing == "up":
        return rot("y", 90 * r - 90, rot("x", 180, p))
    if facing == "east":
        return rot("x", -(90 * r - 90), rot("y", 180, rot("z", 90, p)))
    if facing == "west":
        return rot("x", 90 * r - 90, rot("z", 90, p))
    if facing == "north":
        return rot("z", 90 * r - 90, rot("y", 90, rot("z", 90, p)))
    if facing == "south":
        return rot("z", -(90 * r - 90), rot("y", -90, rot("z", 90, p)))
    raise ValueError(facing)


def minecraft(model, x, y, p):
    """A blockstate variant: block_h is block_v turned -90 about z; then x, then y."""
    if model == "h":
        p = rot("z", -90, p)
    return rot("y", y, rot("x", x, p))


POINTS = [(0.1, 0.2, 0.3), (0.7, 0.15, 0.4), (0.3, 0.6, 0.85)]


def close(a, b):
    return all(abs(u - v) < 1e-9 for u, v in zip(a, b))


def derive():
    table = {}
    for facing in ("down", "up", "north", "south", "east", "west"):
        for r in range(4):
            want = [powergrid(facing, r, p) for p in POINTS]
            found = None
            for model, x, y in itertools.product(("v", "h"), (0, 90, 180, 270), (0, 90, 180, 270)):
                got = [minecraft(model, x, y, p) for p in POINTS]
                if all(close(a, b) for a, b in zip(got, want)):
                    found = (model, x, y)
                    break
            if found is None:
                raise RuntimeError("no model rotation reproduces %s rotation=%d" % (facing, r))
            table["facing=%s,rotation=%d" % (facing, r)] = {"model": found[0], "x": found[1], "y": found[2]}
    return table


def h_of_v(box):
    x1, y1, z1, x2, y2, z2 = box
    return (16 - y2, x1, z1, 16 - y1, x2, z2)


def check_models(base):
    """The wall model must be the floor model turned a quarter turn about z, or the table does not apply."""
    v = json.load(open(os.path.join(MODELS, base, "block_v.json")))
    h = json.load(open(os.path.join(MODELS, base, "block_h.json")))
    boxes_v = {tuple(e["from"] + e["to"]) for e in v["elements"]}
    boxes_h = {tuple(e["from"] + e["to"]) for e in h["elements"]}
    expected = {h_of_v(b) for b in boxes_v}
    return {tuple(round(c, 4) for c in b) for b in boxes_h} == {tuple(round(c, 4) for c in b) for b in expected}


def rewrite(table):
    for name in sorted(os.listdir(BLOCKSTATES)):
        path = os.path.join(BLOCKSTATES, name)
        states = json.load(open(path))
        variants = states.get("variants", {})
        if not any("rotation=" in key for key in variants):
            continue
        sample = next(iter(variants.values()))["model"]
        base = sample.split(":block/")[1].rsplit("/", 1)[0]
        if not check_models(base):
            print("SKIP %s: block_h is not block_v turned about z" % name)
            continue
        for key, entry in table.items():
            variant = {"model": "powergrid_modernized:block/%s/block_%s" % (base, entry["model"])}
            if entry["x"]:
                variant["x"] = entry["x"]
            if entry["y"]:
                variant["y"] = entry["y"]
            variants[key] = variant
        with open(path, "w", newline="\n") as f:
            json.dump({"variants": variants}, f, indent=2)
            f.write("\n")
        print("rewrote", name)


def main():
    table = derive()
    with open(TABLE, "w", newline="\n") as f:
        json.dump(table, f, indent=2)
        f.write("\n")
    for key, entry in table.items():
        print("%-26s %s x=%-3d y=%d" % (key, entry["model"], entry["x"], entry["y"]))
    rewrite(table)


if __name__ == "__main__":
    main()
