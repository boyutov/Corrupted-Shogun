#!/usr/bin/env python3
"""
Generates the black hole's accretion disk texture, black_hole_disk.png (256x32).

The map is polar: u runs once around the disk, v runs from the inner edge (v=0,
hottest) to the outer edge (v=1). It is drawn additively in game, so black means
"no light": the outer edge fades to black instead of to transparent. Turbulent
streaks run along u, the way gas smears out as it orbits. Colours follow the
purples in build_model.py, heating to white at the inner edge.

Usage:  python3 tools/build_black_hole_texture.py
Writes: src/main/resources/assets/samuraiboss/textures/entity/black_hole_disk.png
"""

import math, os, random
from PIL import Image

random.seed(20261024)

W, H = 256, 32
MODID = "samuraiboss"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", MODID, "textures", "entity", "black_hole_disk.png")

# build_model.py purples, plus white-hot for the inner edge.
RAMP = [
    (0.00, (0, 0, 0)),
    (0.18, (40, 16, 70)),      # deeper than purple_lo, for the faint outskirts
    (0.40, (78, 36, 128)),     # purple_lo
    (0.62, (142, 76, 216)),    # purple
    (0.82, (206, 156, 255)),   # purple_hi
    (1.00, (255, 244, 255)),   # white-hot
]


def ramp(t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(RAMP, RAMP[1:]):
        if t <= t1:
            k = (t - t0) / (t1 - t0)
            return tuple(int(c0[i] + (c1[i] - c0[i]) * k) for i in range(3))
    return RAMP[-1][1]


# 1D value noise along u (wrapping), one lattice per band of the disk.
BANDS = 12
LATTICE = [[random.random() for _ in range(64)] for _ in range(BANDS + 1)]


def streak(u, band, freq):
    x = u * freq
    i = int(x) % 64
    f = x - int(x)
    f = f * f * (3 - 2 * f)
    a, b = LATTICE[band][i], LATTICE[band][(i + 1) % 64]
    return a + (b - a) * f


img = Image.new("RGB", (W, H))
for y in range(H):
    v = (y + 0.5) / H
    # Bright just inside the inner edge, then a long falloff outwards.
    profile = math.exp(-((v - 0.08) ** 2) / 0.003) * 0.45 + math.exp(-v * 2.6) * 0.8
    # A couple of dark lanes where the gas is thinner.
    profile *= 1 - 0.55 * math.exp(-((v - 0.34) ** 2) / 0.0012) - 0.4 * math.exp(-((v - 0.58) ** 2) / 0.0018)
    band = min(BANDS - 1, int(v * BANDS))
    frac = v * BANDS - band
    for x in range(W):
        u = x / W
        # Blend neighbouring bands so streaks don't step at band borders.
        n = streak(u, band, 24) * (1 - frac) + streak(u, band + 1, 24) * frac
        fine = streak(u * 3.7 + v, (band + 5) % BANDS, 40)
        intensity = profile * (0.25 + 0.75 * n * n + 0.3 * fine)
        img.putpixel((x, y), ramp(intensity))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", os.path.relpath(OUT, ROOT))
