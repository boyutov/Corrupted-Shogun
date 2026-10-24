#!/usr/bin/env python3
"""
Generates the meteor's textures:

  meteor.png       256x128 equirectangular map for the meteor sphere: dark basalt
                   crust split by glowing lava cracks, pitted with small craters,
                   hotter (brighter, more cracks) on the leading face.
  meteor_glow.png  64x64 soft fire halo for the translucent shell around it.

Cracks come from a jittered Voronoi diagram: a pixel is on a crack when it is
almost equidistant from its two nearest cell points. The map wraps left-right
(longitude) seamlessly. Colours come from the palette in build_model.py (red /
gold / black ramps) plus two ember tones.

Usage:  python3 tools/build_meteor_texture.py
Writes: src/main/resources/assets/samuraiboss/textures/entity/meteor*.png
"""

import math, os, random
from PIL import Image

random.seed(20261024)

W, H = 256, 128
CELLS = 46
CRATERS = 26
MODID = "samuraiboss"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", MODID, "textures", "entity")

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


def wrap_dx(a, b):
    d = abs(a - b)
    return min(d, W - d)


# Value noise for rock grain, wrapping horizontally.
GRID = 16
LATTICE = [[random.random() for _ in range(GRID + 1)] for _ in range(GRID + 1)]


def grain(x, y, scale):
    gx = (x / W * scale) % GRID
    gy = (y / H * scale / 2) % GRID
    x0, y0 = int(gx), int(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    x1, y1 = (x0 + 1) % GRID, (y0 + 1) % GRID
    a = LATTICE[y0][x0] + (LATTICE[y0][x1] - LATTICE[y0][x0]) * fx
    b = LATTICE[y1][x0] + (LATTICE[y1][x1] - LATTICE[y1][x0]) * fx
    return a + (b - a) * fy


def fbm(x, y):
    return 0.55 * grain(x, y, 4) + 0.3 * grain(x, y, 9) + 0.15 * grain(x, y, 19)


MIN_SPACING = 17.0   # two points closer than this make a tiny all-crack cell
points = []
while len(points) < CELLS:
    p = (random.uniform(0, W), random.uniform(6, H - 6))
    if all(math.hypot(wrap_dx(p[0], q[0]), p[1] - q[1]) >= MIN_SPACING for q in points):
        points.append(p)
# Extra random points just past the top and bottom edges close the cells there. (Mirroring
# them would put a crack exactly along each edge.)
beyond = []
while len(beyond) < CELLS // 4:
    edge_y = random.choice((-random.uniform(MIN_SPACING / 2, 24), H + random.uniform(MIN_SPACING / 2, 24)))
    p = (random.uniform(0, W), edge_y)
    if all(math.hypot(wrap_dx(p[0], q[0]), p[1] - q[1]) >= MIN_SPACING for q in points + beyond):
        beyond.append(p)
wrapped = [(x + ox, y) for x, y in points + beyond for ox in (-W, 0, W)]

# A second, finer diagram for thin dim cracks inside the crust plates.
fine = [(random.uniform(0, W), random.uniform(-10, H + 10)) for _ in range(CELLS * 4)]
fine_wrapped = [(x + ox, y) for x, y in fine for ox in (-W, 0, W)]

craters = [(random.uniform(0, W), random.uniform(12, H - 12), random.uniform(3, 8)) for _ in range(CRATERS)]

CRACK = [(0.0, P["gold_hi"]), (0.3, P["ember_hi"]), (0.65, P["ember"]), (1.0, P["red_hi"])]
GLOW = [(0.0, P["red_hi"]), (0.5, P["red"]), (1.0, P["red_lo"])]
CRUST = [(0.0, P["black_hi"]), (0.45, P["black"]), (0.8, P["black_lo"]), (1.0, P["black_deep"])]

img = Image.new("RGBA", (W, H))
for y in range(H):
    for x in range(W):
        px, py = x + 0.5, y + 0.5
        # Leading face (u around 0.5) runs hotter: wider cracks, warmer crust.
        heat = 0.5 + 0.5 * math.cos((px / W - 0.5) * 2 * math.pi)
        d = sorted(math.hypot(px - qx, py - qy) for qx, qy in wrapped)[:2]
        edge = d[1] - d[0]
        crack_w = 1.3 + 1.4 * heat
        n = fbm(px, py)
        if edge < crack_w:
            c = ramp(CRACK, edge / crack_w)
        elif edge < crack_w + 2.4:
            c = ramp(GLOW, (edge - crack_w) / 2.4)
        else:
            depth = min(1.0, (edge - crack_w - 2.4) / 12.0)
            c = ramp(CRUST, depth * 0.45 + (1 - n) * 0.55)
            c = mix(c, P["red_lo"], heat * 0.3 * (1 - depth))
            fd = sorted(math.hypot(px - qx, py - qy) for qx, qy in fine_wrapped)[:2]
            if fd[1] - fd[0] < 0.9:                     # hairline cracks, cooler than the main ones
                c = mix(c, P["red"], 0.55 + 0.35 * heat)
            for cx, cy, r in craters:
                dist = math.hypot(wrap_dx(px, cx), py - cy)
                if dist < r:
                    rim = dist / r
                    if rim < 0.7:
                        c = mix(c, P["black_deep"], 0.75 * (1 - rim / 0.7))
                    elif rim < 0.85:
                        c = mix(c, P["red_lo"], 0.5 * heat)  # warm inner wall
                    else:
                        c = mix(c, P["black_hi"], 0.7)       # lit rim
            g = grain(px * 3.1, py * 3.1, 23)
            if g > 0.82:
                c = mix(c, P["black_hi"], 0.6)              # glints on the rock
            if random.random() < 0.02 + 0.03 * heat:     # stray embers in the crust
                c = P["red"]
        img.putpixel((x, y), c + (255,))

GW = 64
glow = Image.new("RGBA", (GW, GW))
for y in range(GW):
    for x in range(GW):
        n = fbm(x * W / GW, y * H / GW)
        c = ramp([(0.0, P["gold_hi"]), (0.5, P["ember"]), (1.0, P["red_hi"])], n)
        alpha = int(90 + 140 * n)
        glow.putpixel((x, y), c + (alpha,))

os.makedirs(OUT_DIR, exist_ok=True)
img.save(os.path.join(OUT_DIR, "meteor.png"))
glow.save(os.path.join(OUT_DIR, "meteor_glow.png"))
print("wrote meteor.png", img.size, "and meteor_glow.png", glow.size)
