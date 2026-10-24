"""Mirrors the Ponder scene text into the lang file.

Ponder looks every card up by key: <modid>.ponder.<scene>.header for the title and
<modid>.ponder.<scene>.text_N for the N-th card, counted from 1 in the order the scene's script
shows them; tags use <modid>.ponder.tag.<tag> and .description. This reads the scene sources,
numbers the .text("...") calls after each scene.title("id", "Title"), and rewrites those keys in
en_us.json. Keep one .text(...) per card, in play order. Run from the repo root.
"""
import json
import os
import re
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD = "powergrid_modernized"
SCENES = os.path.join(ROOT, "src", "main", "java", "com", "nolanbaker", "pgmodernized", "ponder")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", MOD, "lang", "en_us.json")

STRING = r'"((?:[^"\\]|\\.)*)"'
TITLE = re.compile(r'scene\.title\(\s*' + STRING + r'\s*,\s*' + STRING + r'\s*\)')
TEXT = re.compile(r'\.text\(\s*' + STRING + r'\s*\)')
TAG = re.compile(r'registerTag\(\s*(\w+)\s*\)\s*\.title\(\s*' + STRING + r'\s*\)\s*\.description\(\s*' + STRING + r'\s*\)', re.S)
TAG_ID = re.compile(r'(\w+)\s*=\s*id\(\s*' + STRING + r'\s*\)')


def unescape(s):
    return s.encode("utf-8").decode("unicode_escape").encode("latin-1").decode("utf-8") if "\\" in s else s


def collect():
    entries = OrderedDict()
    for name in sorted(os.listdir(SCENES)):
        if not name.endswith(".java"):
            continue
        src = open(os.path.join(SCENES, name), encoding="utf-8").read()
        # Tags
        ids = {k: unescape(v) for k, v in TAG_ID.findall(src)}
        for field, title, desc in TAG.findall(src):
            tag = ids.get(field, field)
            entries["%s.ponder.tag.%s" % (MOD, tag)] = unescape(title)
            entries["%s.ponder.tag.%s.description" % (MOD, tag)] = unescape(desc)
        # Scenes: each title starts a scene; its texts run to the next title.
        titles = list(TITLE.finditer(src))
        for i, t in enumerate(titles):
            scene_id, title = unescape(t.group(1)), unescape(t.group(2))
            end = titles[i + 1].start() if i + 1 < len(titles) else len(src)
            body = src[t.end():end]
            entries["%s.ponder.%s.header" % (MOD, scene_id)] = title
            for n, tm in enumerate(TEXT.finditer(body), start=1):
                entries["%s.ponder.%s.text_%d" % (MOD, scene_id, n)] = unescape(tm.group(1))
    return entries


def main():
    entries = collect()
    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f, object_pairs_hook=OrderedDict)
    prefix = MOD + ".ponder."
    kept = OrderedDict((k, v) for k, v in lang.items() if not k.startswith(prefix))
    kept.update(entries)
    with open(LANG, "w", encoding="utf-8", newline="\n") as f:
        json.dump(kept, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("ponder lang entries:", len(entries))


if __name__ == "__main__":
    main()
