"""Assets for the Helm: a pedestal, a console and a wheel, with a jack pin on the back. Built
facing north (wheel on the north side) and turned by the blockstate. Run: python tools/gen_helm_assets.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_breaker_panel_assets import ASSETS, MOD, element, dump, loot_table, recipe

HELM = "helm"


def model():
    els = [
        element(5, 0, 5, 11, 9, 11, "#side"),            # pedestal
        element(2, 9, 4, 14, 14, 13, "#side"),           # console
        element(7, 11, 13, 9, 13, 14, "#jack"),          # jack pin on the back
        # The wheel: a rim of four bars, a hub and two spokes, standing on the front.
        element(4, 15, 2, 12, 16, 4, "#wheel"),
        element(4, 8, 2, 12, 9, 4, "#wheel"),
        element(3, 9, 2, 4, 15, 4, "#wheel"),
        element(12, 9, 2, 13, 15, 4, "#wheel"),
        element(7, 11, 2, 9, 13, 4, "#wheel"),
        element(7.5, 9, 2.5, 8.5, 15, 3.5, "#wheel"),
        element(4, 11.5, 2.5, 12, 12.5, 3.5, "#wheel"),
    ]
    # The console top shows the regulator's screen panel.
    els[1]["faces"]["up"] = {"uv": [0, 0, 12, 12], "texture": "#side"}
    dump(os.path.join(ASSETS, "models", "block", HELM + ".json"), {
        "parent": "block/block",
        "textures": {"side": "%s:block/vfd" % MOD, "wheel": "%s:block/transformer_cap" % MOD,
                     "jack": "%s:block/jack_pin" % MOD, "particle": "%s:block/vfd" % MOD},
        "elements": els,
    })
    dump(os.path.join(ASSETS, "models", "item", HELM + ".json"), {"parent": "%s:block/%s" % (MOD, HELM)})
    variants = {}
    for facing, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        v = {"model": "%s:block/%s" % (MOD, HELM)}
        v.update(rot)
        variants["facing=%s" % facing] = v
    dump(os.path.join(ASSETS, "blockstates", HELM + ".json"), {"variants": variants})


def data():
    loot_table(HELM)
    recipe(HELM, [" W ", "INI", " I "], {"W": {"item": "create:cogwheel"}, "I": {"tag": "c:plates/iron"},
                                         "N": {"item": "%s:network_jack" % MOD}}, 1, {"items": "%s:network_jack" % MOD})


def main():
    model()
    data()
    print("helm assets written")


if __name__ == "__main__":
    main()
