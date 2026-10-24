#!/usr/bin/env python3
"""
Generates 16x16 item textures for the magic items, in the samurai's palette
(build_model.py): a red leather grimoire with gold fittings and a purple gem.

Usage:  python3 tools/build_item_textures.py
Writes: src/main/resources/assets/samuraiboss/textures/item/spell_book.png
"""

import os
from PIL import Image

MODID = "samuraiboss"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", MODID, "textures", "item")

# Shared with build_model.py's palette.
C = {
    ".": None,
    "O": (11, 11, 16),      # black_deep: outline
    "s": (58, 11, 15),      # red_deep: spine / shadow
    "d": (92, 18, 22),      # red_lo
    "r": (132, 28, 30),     # red
    "h": (176, 48, 44),     # red_hi
    "g": (152, 106, 30),    # gold_lo
    "G": (212, 162, 60),    # gold
    "Y": (252, 226, 150),   # gold_hi
    "p": (78, 36, 128),     # purple_lo
    "P": (142, 76, 216),    # purple
    "L": (206, 156, 255),   # purple_hi
    "w": (206, 188, 150),   # lace: pages
    "W": (232, 220, 190),   # page highlight
    "k": (142, 124, 94),    # lace_lo: page shadow
}

# Front cover facing the viewer, spine on the left, page edges on the right.
SPELL_BOOK = [
    "................",
    ".OOOOOOOOOOOOO..",
    ".OsYGGGGGGGGYOO.",
    ".OsGhhrrrrrrdGWO",
    ".OgGhgggGgggdGwO",
    ".OsGrgdrYrdgdGWO",
    ".OsGrgrYPYrgdGwO",
    ".OgGgYPLPPYGdGWO",
    ".OsGrgrYPpYgdGwO",
    ".OsGrgdrYrdgdGWO",
    ".OsGrgggGgggdGwO",
    ".OgGrrrrrrdddGkO",
    ".OsYGGGGGGGGGYkO",
    ".OOsssssssssssOO",
    "..OOOOOOOOOOOOO.",
    "................",
]


def paint(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            c = C[ch]
            if c is not None:
                img.putpixel((x, y), c + (255,))
    return img


os.makedirs(OUT_DIR, exist_ok=True)
paint(SPELL_BOOK).save(os.path.join(OUT_DIR, "spell_book.png"))
print("wrote spell_book.png")
