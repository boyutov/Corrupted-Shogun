#!/usr/bin/env python3
"""
Generates 16x16 item textures for the magic items, in the samurai's palette
(build_model.py): a red leather grimoire with gold fittings and a purple gem.

Usage:  python3 tools/build_item_textures.py
Writes: src/main/resources/assets/samuraiboss/textures/item/spell_{book,scroll,scroll_seal}.png
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
    "k": (142, 124, 94),    # lace_lo: page shadow / ink
    "B": (122, 78, 44),     # scroll-only: roller wood
    "b": (84, 50, 28),      # roller wood, shadow
    # Seal greys; multiplied by the school colour in game.
    "1": (150, 150, 150),
    "2": (110, 110, 110),
    "3": (215, 215, 215),
    "4": (255, 255, 255),
}

# Portrait grimoire: spine on the left, page edges on the right, gold-framed cover with a gem.
SPELL_BOOK = [
    "...OOOOOOOOOO...",
    "...OsYGGGGGYWO..",
    "...OgGhhrrdGwO..",
    "...OsGhrrrdGWO..",
    "...OsGrrrrdGwO..",
    "...OsGrgYgdGWO..",
    "...OsGgYPYgGwO..",
    "...OgGYPLPYGWO..",
    "...OsGgYPpgGwO..",
    "...OsGrgYgdGWO..",
    "...OsGrrrrdGwO..",
    "...OsGrrrddGWO..",
    "...OgGrrdddGkO..",
    "...OsYGGGGGYkO..",
    "...OOsssssssOO..",
    "....OOOOOOOOO...",
]

# Parchment on two wooden rollers with gold knobs. The wax seal is a separate layer
# (SPELL_SCROLL_SEAL), painted in greys and tinted at runtime with the spell's school colour.
SPELL_SCROLL = [
    "................",
    "..OOOOOOOOOOOO..",
    ".OGbBBBBBBBBbGO.",
    "..OObbbbbbbbOO..",
    "...OWwwwwwwkO...",
    "...OWwkkkkwkO...",
    "...OWwwwwwwkO...",
    "...OWwkkkwwkO...",
    "...OWwwwwwwkO...",
    "...OWwwwwwwkO...",
    "...OWwwwwwwkO...",
    "...OWwwwwwwkO...",
    "..OObbbbbbbbOO..",
    ".OGbBBBBBBBBbGO.",
    "..OOOOOOOOOOOO..",
    "................",
]

SPELL_SCROLL_SEAL = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......12.......",
    "......1331......",
    "......3443......",
    ".......22.......",
    "................",
    "................",
    "................",
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
paint(SPELL_SCROLL).save(os.path.join(OUT_DIR, "spell_scroll.png"))
paint(SPELL_SCROLL_SEAL).save(os.path.join(OUT_DIR, "spell_scroll_seal.png"))
print("wrote spell_book.png, spell_scroll.png, spell_scroll_seal.png")
