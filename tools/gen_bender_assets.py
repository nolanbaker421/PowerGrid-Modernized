"""Conduit Bender: item texture (a hand bender, shoe up, rubber grip down), item model and recipe."""
import os

from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, fill, recipe, shade, write_png

STEEL = (0xB4, 0xB8, 0xBE)
HANDLE = (0x5A, 0x5E, 0x66)
GRIP = (0xC6, 0x2A, 0x1E)


def texture():
    img = canvas(16, 16)
    # Handle: a two-pixel diagonal from the bottom left up to the shoe.
    for k in range(10):
        x, y = 2 + k, 13 - k
        fill(img, x, y, x + 1, y + 1, HANDLE)
        fill(img, x + 1, y, x + 2, y + 1, shade(HANDLE, 1.3))
    # Grip on the bottom third.
    for k in range(4):
        x, y = 2 + k, 13 - k
        fill(img, x, y, x + 2, y + 1, GRIP)
    fill(img, 2, 13, 3, 14, shade(GRIP, 0.7))
    # Shoe: a hooked quarter circle at the top right, with its groove.
    shoe = [(11, 3), (12, 3), (13, 3), (10, 4), (13, 4), (14, 4), (10, 5), (14, 5), (10, 6), (14, 6), (11, 7), (14, 7), (12, 8), (13, 8)]
    for x, y in shoe:
        fill(img, x, y, x + 1, y + 1, STEEL)
    for x, y in [(11, 2), (12, 2), (13, 2), (14, 3)]:
        fill(img, x, y, x + 1, y + 1, shade(STEEL, 1.2))
    for x, y in [(11, 8), (14, 8), (12, 9), (13, 9)]:
        fill(img, x, y, x + 1, y + 1, shade(STEEL, 0.6))
    # Foot pedal.
    fill(img, 13, 9, 16, 10, shade(STEEL, 0.8))
    item = os.path.join(ASSETS, "textures", "item")
    write_png(os.path.join(item, "conduit_bender.png"), img)
    dump(os.path.join(ASSETS, "models", "item", "conduit_bender.json"),
         {"parent": "minecraft:item/handheld", "textures": {"layer0": "%s:item/conduit_bender" % MOD}})


def main():
    texture()
    recipe("conduit_bender", ["  P", " I ", "I  "], {"P": {"tag": "c:plates/iron"}, "I": {"item": "minecraft:iron_ingot"}}, 1,
           {"items": "%s:conduit_half" % MOD})


if __name__ == "__main__":
    main()
