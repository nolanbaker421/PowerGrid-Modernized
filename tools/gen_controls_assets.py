#!/usr/bin/env python3
"""Generates the controls cabinet resources: block model, blockstate, textures, the module and
device item icons and models, loot and recipes. Geometry mirrors ControlsCabinetBlock.java.
Run from the repository root:  python tools/gen_controls_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, MOD, canvas, dump, element, fill, shade, write_png, loot_table, recipe

NAME = "controls_cabinet"
BODY = (2, 1, 10, 14, 15, 16)
HUB_U = [3.5, 6.5, 9.5, 12.5]
HUB_Z1, HUB_Z2 = 12.5, 14.5
JACK = (14, 7, 12.5, 15, 9, 14.5)
MODULES = {"power_supply_module": (0x40, 0xC0, 0x50), "digital_in_module": (0x40, 0x80, 0xE0),
           "digital_out_module": (0xE0, 0x90, 0x30), "relay_module": (0xE0, 0xD0, 0x40), "vfd_control_module": (0xA0, 0x50, 0xE0),
           "plc_module": (0x30, 0xC0, 0xC0), "analog_in_module": (0x60, 0xB0, 0x70), "analog_out_module": (0xC0, 0x80, 0x40)}
DEVICES = ["estop_button", "toggle_button", "momentary_button", "selector_switch", "pilot_light", "number_display", "speed_dial"]


def textures():
    tex = os.path.join(ASSETS, "textures", "block")
    body = (0x7c, 0x80, 0x86)
    edge = (0x4a, 0x4d, 0x52)
    front = canvas(16, 16, body + (255,))
    fill(front, 2, 1, 14, 15, shade(body, 0.85))
    fill(front, 2, 1, 14, 2, edge)
    fill(front, 2, 14, 14, 15, edge)
    fill(front, 2, 1, 3, 15, edge)
    fill(front, 13, 1, 14, 15, edge)
    # Six door cells, lightly outlined.
    for cx in (3, 6, 9):
        for cy in (2, 8):
            fill(front, cx, cy, cx + 3, cy + 6, shade(body, 0.78))
            fill(front, cx, cy, cx + 3, cy + 1, shade(body, 0.95))
    fill(front, 3, 7, 12, 8, edge)
    write_png(os.path.join(tex, NAME + "_front.png"), front)
    side = canvas(16, 16, body + (255,))
    fill(side, 0, 0, 16, 1, shade(body, 1.12))
    fill(side, 0, 15, 16, 16, edge)
    fill(side, 0, 0, 1, 16, edge)
    fill(side, 15, 0, 16, 16, edge)
    write_png(os.path.join(tex, NAME + "_side.png"), side)


def model():
    tex = {"front": "%s:block/%s_front" % (MOD, NAME), "side": "%s:block/%s_side" % (MOD, NAME),
           "terminal": "%s:block/panel_terminal" % MOD, "jack": "%s:block/jack_pin" % MOD, "particle": "%s:block/%s_side" % (MOD, NAME)}
    elements = [element(*BODY, "#side", cull_south=True)]
    elements[0]["faces"]["north"]["texture"] = "#front"
    for u in HUB_U:
        x = 16 - u
        elements.append(element(x - 1, 15, HUB_Z1, x + 1, 16, HUB_Z2, "#terminal"))
        elements.append(element(x - 1, 0, HUB_Z1, x + 1, 1, HUB_Z2, "#terminal"))
    for y in (3, 11):
        elements.append(element(14, y, HUB_Z1, 15, y + 2, HUB_Z2, "#terminal"))
        elements.append(element(1, y, HUB_Z1, 2, y + 2, HUB_Z2, "#terminal"))
    elements.append(element(*JACK, "#jack"))
    elements.append(element(1, 7, 12.5, 2, 9, 14.5, "#jack"))   # the PLC's external port
    dump(os.path.join(ASSETS, "models", "block", NAME + ".json"), {"parent": "block/block", "textures": tex, "elements": elements})
    dump(os.path.join(ASSETS, "models", "item", NAME + ".json"), {"parent": "%s:block/%s" % (MOD, NAME)})
    dump(os.path.join(ASSETS, "blockstates", NAME + ".json"), {"variants": {
        "facing=north": {"model": "%s:block/%s" % (MOD, NAME)},
        "facing=east": {"model": "%s:block/%s" % (MOD, NAME), "y": 90},
        "facing=south": {"model": "%s:block/%s" % (MOD, NAME), "y": 180},
        "facing=west": {"model": "%s:block/%s" % (MOD, NAME), "y": 270},
    }})


def item_icon(name, draw):
    img = canvas(16, 16)
    draw(img)
    write_png(os.path.join(ASSETS, "textures", "item", name + ".png"), img)
    dump(os.path.join(ASSETS, "models", "item", name + ".json"),
         {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (MOD, name)}})


def icons():
    grey = (0xb0, 0xb4, 0xb8)
    dark = (0x30, 0x32, 0x36)
    for name, stripe in MODULES.items():
        def draw(img, stripe=stripe):
            fill(img, 4, 1, 12, 15, grey)                    # the module body
            fill(img, 4, 1, 12, 2, shade(grey, 1.15))
            fill(img, 4, 14, 12, 15, shade(grey, 0.6))
            fill(img, 5, 3, 11, 5, stripe)                   # its colour band
            for y in (7, 9, 11):                             # terminals
                fill(img, 5, y, 7, y + 1, dark)
                fill(img, 9, y, 11, y + 1, dark)
            fill(img, 2, 7, 4, 9, shade(grey, 0.5))          # the rail clip
            fill(img, 12, 7, 14, 9, shade(grey, 0.5))
        item_icon(name, draw)

    def estop(img):
        fill(img, 2, 2, 14, 14, (0xe8, 0xd0, 0x20))
        fill(img, 4, 4, 12, 12, (0xd0, 0x20, 0x20))
        fill(img, 5, 5, 8, 7, (0xf0, 0x60, 0x60))

    def toggle(img):
        fill(img, 3, 3, 13, 13, dark)
        fill(img, 5, 4, 11, 8, (0x40, 0xd0, 0x50))
        fill(img, 5, 8, 11, 12, (0x80, 0x84, 0x88))

    def momentary(img):
        fill(img, 3, 3, 13, 13, dark)
        fill(img, 5, 5, 11, 11, (0x30, 0xc0, 0x40))
        fill(img, 6, 6, 8, 7, (0x80, 0xf0, 0x90))

    def selector(img):
        fill(img, 3, 3, 13, 13, dark)
        fill(img, 4, 4, 12, 12, (0x40, 0x44, 0x48))
        fill(img, 7, 4, 9, 12, (0xf0, 0xf0, 0xf0))

    def led(img):
        fill(img, 3, 3, 13, 13, dark)
        fill(img, 5, 5, 11, 11, (0x30, 0xe0, 0x40))
        fill(img, 6, 6, 8, 8, (0xa0, 0xff, 0xa0))

    def display(img):
        fill(img, 1, 4, 15, 12, (0x10, 0x12, 0x14))
        for x in (3, 6, 9, 12):
            fill(img, x, 6, x + 2, 10, (0x40, 0xff, 0x60))
            fill(img, x, 7, x + 1, 9, (0x10, 0x12, 0x14))

    def dial(img):
        fill(img, 3, 3, 13, 13, dark)
        fill(img, 4, 4, 12, 12, (0x40, 0x44, 0x48))
        fill(img, 7, 4, 9, 8, (0xf0, 0xf0, 0xf0))
        for x in (3, 7, 12):
            fill(img, x, 13, x + 1, 14, (0xa0, 0xa4, 0xa8))

    for name, draw in zip(DEVICES, (estop, toggle, momentary, selector, led, display, dial)):
        item_icon(name, draw)


def data():
    loot_table(NAME)
    plate = {"tag": "c:plates/iron"}
    nugget = {"tag": "c:nuggets/iron"}
    jack = {"item": "%s:network_jack" % MOD}
    recipe(NAME, ["III", "IJI", "III"], {"I": plate, "J": jack}, 1, {"items": "%s:network_jack" % MOD})
    coil = {"item": "powergrid:copper_coil"}
    pins = {"item": "powergrid:pins"}
    recipe("power_supply_module", ["NCN", "NRN"], {"N": nugget, "C": coil, "R": {"item": "minecraft:redstone"}}, 1, {"items": "powergrid:copper_coil"})
    recipe("digital_in_module", ["NPN", "NRN"], {"N": nugget, "P": pins, "R": {"item": "minecraft:redstone"}}, 1, {"items": "powergrid:pins"})
    recipe("digital_out_module", ["NPN", "NTN"], {"N": nugget, "P": pins, "T": {"item": "minecraft:redstone_torch"}}, 1, {"items": "powergrid:pins"})
    recipe("relay_module", ["NPN", "NLN"], {"N": nugget, "P": pins, "L": {"item": "minecraft:lever"}}, 1, {"items": "powergrid:pins"})
    recipe("vfd_control_module", ["NPN", "NCN"], {"N": nugget, "P": pins, "C": {"item": "minecraft:comparator"}}, 1, {"items": "powergrid:pins"})
    recipe("plc_module", ["PCP", "NJN"], {"N": nugget, "P": pins, "C": {"item": "minecraft:comparator"}, "J": jack}, 1, {"items": "%s:network_jack" % MOD})
    recipe("analog_in_module", ["NPN", "NDN"], {"N": nugget, "P": pins, "D": {"item": "minecraft:daylight_detector"}}, 1, {"items": "powergrid:pins"})
    recipe("analog_out_module", ["NPN", "NRN"], {"N": nugget, "P": pins, "R": {"item": "minecraft:repeater"}}, 1, {"items": "powergrid:pins"})
    recipe("speed_dial", [" R ", "NNN"], {"R": {"item": "minecraft:repeater"}, "N": nugget}, 2, {"items": "%s:network_jack" % MOD})
    recipe("estop_button", [" R ", "YNY"], {"R": {"item": "minecraft:red_dye"}, "Y": {"item": "minecraft:yellow_dye"}, "N": nugget}, 1, {"items": "%s:network_jack" % MOD})
    recipe("toggle_button", [" L ", "NNN"], {"L": {"item": "minecraft:lever"}, "N": nugget}, 2, {"items": "%s:network_jack" % MOD})
    recipe("momentary_button", [" B ", "NNN"], {"B": {"item": "minecraft:stone_button"}, "N": nugget}, 2, {"items": "%s:network_jack" % MOD})
    recipe("selector_switch", [" C ", "NNN"], {"C": {"item": "minecraft:comparator"}, "N": nugget}, 2, {"items": "%s:network_jack" % MOD})
    recipe("pilot_light", [" G ", "NNN"], {"G": {"item": "minecraft:glowstone_dust"}, "N": nugget}, 2, {"items": "%s:network_jack" % MOD})
    recipe("number_display", ["GGG", "NRN"], {"G": {"tag": "c:glass_panes"}, "N": nugget, "R": {"item": "minecraft:redstone"}}, 1, {"items": "%s:network_jack" % MOD})


def main():
    textures()
    model()
    icons()
    data()
    print("controls cabinet assets written")


if __name__ == "__main__":
    main()
