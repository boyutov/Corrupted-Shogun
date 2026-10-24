#!/usr/bin/env python3
"""
Generates the meteor's 64x64 face texture: dark basalt crust split by glowing
lava cracks. The same image is used on all six faces of the meteor cube.

Cracks come from a jittered Voronoi diagram: a pixel is on a crack when it is
almost equidistant from its two nearest cell points. Colours come from the
palette in build_model.py (red / gold / black ramps) plus two ember tones.

Usage:  python3 tools/build_meteor_texture.py
Writes: src/main/resources/assets/samuraiboss/textures/entity/meteor.png
"""

import math, os, random
from PIL import Image

random.seed(20261024)

TEX = 64
CELLS = 14
MODID = "samuraiboss"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", MODID, "textures", "entity", "meteor.png")

# Shared with build_model.py's palette.
P = {
    "red_hi":   (176, 48, 44),  "red":      (132, 28, 30),
    "red_lo":   (92, 18, 22),
    "black_hi": (52, 52, 64),   "black":    (32, 32, 41),
    "black_lo": (20, 20, 27),   "black_deep": (11, 11, 16),
    "gold_hi":  (252, 226, 150), "gold":    (212, 162, 60),
    # Meteor-only: hotter than anything on the samurai.
    "ember":    (255, 120, 32), "ember_hi": (255, 196, 92),
}


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(stops, t):
    """Piecewise-linear colour ramp; stops = [(t, colour), ...] ascending."""
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return mix(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0)
    return stops[-1][1]


MIN_SPACING = 11.0   # two points closer than this make a tiny all-crack cell


def torus_dist(a, b):
    dx = min(abs(a[0] - b[0]), TEX - abs(a[0] - b[0]))
    dy = min(abs(a[1] - b[1]), TEX - abs(a[1] - b[1]))
    return math.hypot(dx, dy)


points = []
while len(points) < CELLS:
    p = (random.uniform(0, TEX), random.uniform(0, TEX))
    if all(torus_dist(p, q) >= MIN_SPACING for q in points):
        points.append(p)
# Wrap the points so cells continue past the edges instead of bunching there.
wrapped = [(x + ox, y + oy) for x, y in points for ox in (-TEX, 0, TEX) for oy in (-TEX, 0, TEX)]

CRACK = [(0.0, P["gold_hi"]), (0.35, P["ember_hi"]), (0.7, P["ember"]), (1.0, P["red_hi"])]
GLOW = [(0.0, P["red_hi"]), (0.5, P["red"]), (1.0, P["red_lo"])]
CRUST = [(0.0, P["black_hi"]), (0.45, P["black"]), (0.8, P["black_lo"]), (1.0, P["black_deep"])]

img = Image.new("RGBA", (TEX, TEX))
for y in range(TEX):
    for x in range(TEX):
        px, py = x + 0.5, y + 0.5
        d = sorted(math.hypot(px - qx, py - qy) for qx, qy in wrapped)[:2]
        edge = d[1] - d[0]               # 0 on a crack, grows towards the cell centre
        if edge < 1.6:
            c = ramp(CRACK, edge / 1.6)
        elif edge < 3.4:
            c = ramp(GLOW, (edge - 1.6) / 1.8)
        else:
            depth = min(1.0, (edge - 3.4) / 9.0)
            c = ramp(CRUST, depth * 0.85 + random.uniform(0.0, 0.15))
            if random.random() < 0.04:   # stray embers in the crust
                c = P["red"]
        img.putpixel((x, y), c + (255,))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", os.path.relpath(OUT, ROOT))
