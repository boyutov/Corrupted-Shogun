#!/usr/bin/env python3
"""
Generates samurai_shogun.geo.json (Bedrock 1.12.0 / GeckoLib 4) with PER-FACE UV
at 2 texels per world unit, plus a detailed 256x256 texture and its glowmask.

Per-face UV (verified against GeckoLib 4's UVUnion/FaceUV/UVFaces on the 1.20.1
branch) lets each face carry its own uv + uv_size, so texel density is no longer
locked to 1 texel per block-unit the way box UV forces it.

Face directions follow Minecraft's Direction enum:
  north = -Z (entity front)   south = +Z (back)
  west  = -X (entity right)   east  = +X (entity left)
  up / down
"""

import json, os, math, random
from PIL import Image

random.seed(20261020)

D = 2                 # texels per world unit
TEX = 256
OUT_DIR = "samurai_shogun"
MODID = "modid"

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------
P = {
    "red_hi":   (176, 48, 44),  "red":      (132, 28, 30),
    "red_lo":   (92, 18, 22),   "red_deep": (58, 11, 15),

    "black_hi": (52, 52, 64),   "black":    (32, 32, 41),
    "black_lo": (20, 20, 27),   "black_deep": (11, 11, 16),

    "gold_hi":  (252, 226, 150), "gold":    (212, 162, 60),
    "gold_lo":  (152, 106, 30),  "gold_deep": (92, 62, 18),

    "purple_hi": (206, 156, 255), "purple": (142, 76, 216),
    "purple_lo": (78, 36, 128),

    "steel_hi": (232, 238, 248), "steel":   (170, 180, 198),
    "steel_lo": (104, 114, 134), "steel_deep": (62, 70, 86),

    "lace":     (206, 188, 150), "lace_lo": (142, 124, 94),
    "void":     (13, 11, 19),
    "cloth":    (66, 26, 30),    "cloth_lo": (42, 16, 20),
}

FACE_SHADE = {"up": 1.16, "down": 0.66, "north": 1.0, "south": 0.88, "west": 0.93, "east": 0.93}


def sh(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# ---------------------------------------------------------------------------
# Model definition.  Each cube: (name, origin, size, material)
# `faces` overrides the material for specific directions.
# ---------------------------------------------------------------------------
BONES = [
    dict(name="root", parent=None, pivot=[0, 0, 0], cubes=[]),
    # Empty marker bones. GeckoLib parses `locators` but never bakes them into
    # bones, so a locator cannot report a world position. Bones can, via
    # GeoBone.getWorldPosition(), which is what the VFX code needs.
    dict(name="ground_impact", parent="root", pivot=[0, 0, -14], cubes=[]),
    dict(name="aura", parent="root", pivot=[0, 24, 0], cubes=[]),

    dict(name="waist", parent="root", pivot=[0, 20, 0], cubes=[
        ("pelvis", [-7, 18, -4], [14, 4, 8], "armor_black"),
    ]),

    dict(name="kusazuri", parent="waist", pivot=[0, 20, 0], cubes=[
        ("kusazuri", [-9, 12, -5], [18, 8, 10], "skirt"),
    ]),

    dict(name="torso", parent="waist", pivot=[0, 20, 0], cubes=[
        ("dou", [-7, 20, -5], [14, 16, 10], "lamellar", {"north": "dou_front", "south": "dou_back"}),
        ("collar", [-5, 34, -4], [10, 3, 8], "armor_black"),
    ]),

    dict(name="head", parent="torso", pivot=[0, 35, 0], cubes=[
        ("head", [-5, 35, -5], [10, 10, 10], "void_head", {"north": "face"}),
    ]),

    dict(name="menpo", parent="head", pivot=[0, 39, -5], cubes=[
        # 4 units tall, topping out at y=40: the kabuto's brow starts at y=42,
        # so y=40..42 stays open as the eye slit band. A 6-unit menpo met the
        # helmet edge-to-edge and sealed the face off completely.
        ("menpo", [-5, 36, -6], [10, 4, 2], "iron_mask", {"north": "menpo_front"}),
    ]),

    dict(name="kabuto", parent="head", pivot=[0, 44, 0], cubes=[
        ("kabuto_bowl", [-6.5, 42, -6.5], [13, 4, 13], "kabuto", {"up": "kabuto_top_plate"}),
        ("kabuto_top", [-4, 46, -4], [8, 2, 8], "gold_cap"),
        ("shikoro", [-8, 38, -3], [16, 5, 10], "neck_guard"),
    ]),

    # Kuwagata: two-segment prongs. Z-rotation sign decides which way they splay —
    # for the RIGHT horn (at -X) the tip moves outward only when rz is POSITIVE,
    # because x' = -dy*sin(rz). The previous single-segment horns had the signs
    # swapped, so both prongs leaned inward across the helmet.
    dict(name="horn_right", parent="kabuto", pivot=[-3, 43, -4], rotation=[-12, 0, 56], cubes=[
        ("horn_r1", [-4.5, 43, -5], [3, 5, 2], "horn"),
    ]),
    dict(name="horn_right_tip", parent="horn_right", pivot=[-3, 48, -4], rotation=[0, 0, 28], cubes=[
        ("horn_r2", [-4, 48, -5], [2, 5, 2], "horn"),
    ]),
    dict(name="horn_left", parent="kabuto", pivot=[3, 43, -4], rotation=[-12, 0, -56], cubes=[
        ("horn_l1", [1.5, 43, -5], [3, 5, 2], "horn"),
    ]),
    dict(name="horn_left_tip", parent="horn_left", pivot=[3, 48, -4], rotation=[0, 0, -28], cubes=[
        ("horn_l2", [2, 48, -5], [2, 5, 2], "horn"),
    ]),

    dict(name="right_shoulder", parent="torso", pivot=[-7, 34, 0], cubes=[
        ("sode_r", [-12, 24, -6], [5, 11, 12], "lamellar", {"west": "sode_outer"}),
    ]),
    dict(name="right_arm", parent="right_shoulder", pivot=[-8, 33, 0], cubes=[
        ("arm_r", [-11, 19, -3], [6, 14, 6], "kote"),
    ]),
    # Hilt furniture lives on the katana bone; the blade is split into three
    # segments, each tilted a few degrees about Z, which builds the sori (curve).
    # Edge is the -X side, spine the +X side, so the blade must bow toward +X:
    # x' = -dy*sin(rz), so rz is NEGATIVE.
    dict(name="katana", parent="right_arm", pivot=[-8, 22, 0], cubes=[
        ("kashira", [-10.5, 12, -2.5], [5, 2, 5], "gold_solid"),
        ("tsuka", [-10, 14, -2], [4, 16, 4], "tsuka"),
        ("fuchi", [-10.5, 30, -2.5], [5, 1, 5], "gold_solid"),
        ("tsuba", [-11.5, 31, -3.5], [7, 1, 7], "tsuba_guard"),
        ("habaki", [-10, 32, -1.5], [4, 2, 3], "gold_solid"),
    ]),
    dict(name="blade_1", parent="katana", pivot=[-8, 34, 0], rotation=[0, 0, -3], cubes=[
        ("blade_a", [-10, 34, -1], [4, 10, 2], "blade"),
    ]),
    dict(name="blade_base", parent="katana", pivot=[-8, 34, 0], cubes=[]),
    dict(name="blade_2", parent="blade_1", pivot=[-8, 44, 0], rotation=[0, 0, -4], cubes=[
        ("blade_b", [-10, 44, -1], [3.5, 9, 2], "blade"),
    ]),
    dict(name="blade_mid", parent="blade_2", pivot=[-8, 48, 0], cubes=[]),
    dict(name="blade_3", parent="blade_2", pivot=[-8, 53, 0], rotation=[0, 0, -4], cubes=[
        ("blade_c", [-10, 53, -1], [3, 7, 2], "blade"),
        ("kissaki", [-10, 60, -1], [3, 4, 2], "blade_tip"),
    ]),
    dict(name="blade_tip", parent="blade_3", pivot=[-8, 64, 0], cubes=[]),

    dict(name="left_shoulder", parent="torso", pivot=[7, 34, 0], cubes=[
        ("sode_l", [7, 24, -6], [5, 11, 12], "lamellar", {"east": "sode_outer"}),
    ]),
    dict(name="left_arm", parent="left_shoulder", pivot=[8, 33, 0], cubes=[
        ("arm_l", [5, 19, -3], [6, 14, 6], "kote"),
    ]),

    dict(name="right_leg", parent="waist", pivot=[-4, 20, 0], cubes=[
        ("leg_r", [-7.5, 6, -3.5], [7, 14, 7], "haidate"),
    ]),
    # Greave (suneate) and foot are now separate bones, so the foot can plant and
    # roll independently of the shin. Existing clips animate *_boot only; the
    # feet inherit from it, so nothing breaks if a clip ignores them.
    dict(name="right_boot", parent="right_leg", pivot=[-4, 6, 0], cubes=[
        ("boot_r", [-8, 2, -4.5], [8, 5, 9], "boot"),
    ]),
    dict(name="right_foot", parent="right_boot", pivot=[-4, 2, -2], cubes=[
        ("foot_r", [-7.5, 0, -7], [7, 3, 9], "foot", {"up": "foot_top", "north": "foot_toe"}),
    ]),
    dict(name="left_leg", parent="waist", pivot=[4, 20, 0], cubes=[
        ("leg_l", [0.5, 6, -3.5], [7, 14, 7], "haidate"),
    ]),
    dict(name="left_boot", parent="left_leg", pivot=[4, 6, 0], cubes=[
        ("boot_l", [0, 2, -4.5], [8, 5, 9], "boot"),
    ]),
    dict(name="left_foot", parent="left_boot", pivot=[4, 2, -2], cubes=[
        ("foot_l", [0.5, 0, -7], [7, 3, 9], "foot", {"up": "foot_top", "north": "foot_toe"}),
    ]),
]

DIRS = ["north", "south", "west", "east", "up", "down"]


def face_world_size(size, d):
    w, h, dp = size
    if d in ("north", "south"):
        return w, h
    if d in ("west", "east"):
        return dp, h
    return w, dp


# ---------------------------------------------------------------------------
# Painting engine — each face painted on its own small canvas, then packed
# ---------------------------------------------------------------------------
class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
        self.glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.gx = self.glow.load()

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (c[0], c[1], c[2], 255)

    def glowset(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.gx[x, y] = (c[0], c[1], c[2], 255)

    def fill(self, c):
        for y in range(self.h):
            for x in range(self.w):
                self.set(x, y, c)

    def rect(self, x0, y0, w, h, c):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.set(x, y, c)

    def hline(self, y, c, x0=0, x1=None, step=1):
        x1 = self.w if x1 is None else x1
        for x in range(x0, x1, step):
            self.set(x, y, c)

    def vline(self, x, c, y0=0, y1=None, step=1):
        y1 = self.h if y1 is None else y1
        for y in range(y0, y1, step):
            self.set(x, y, c)

    def noise(self, base, amt=10, density=0.3):
        for y in range(self.h):
            for x in range(self.w):
                if random.random() < density:
                    d = random.randint(-amt, amt)
                    self.set(x, y, tuple(max(0, min(255, v + d)) for v in base))

    def vgrad(self, top, bottom):
        for y in range(self.h):
            t = y / max(1, self.h - 1)
            c = mix(top, bottom, t)
            for x in range(self.w):
                self.set(x, y, c)

    def edge_wear(self, c, density=0.22):
        """Chip the outer border so silhouettes read as battered metal."""
        for x in range(self.w):
            for y in (0, self.h - 1):
                if random.random() < density:
                    self.set(x, y, c)
        for y in range(self.h):
            for x in (0, self.w - 1):
                if random.random() < density:
                    self.set(x, y, c)

    def fissure(self, n=1):
        """
        Corruption cracks that start at an edge and run inward, so they read as
        the lacquer splitting rather than as scattered marks. Each crack is a
        one-pixel purple core with a dark halo beside it; only the core glows.
        """
        if n <= 0 or self.w < 5 or self.h < 5:
            return
        for _ in range(n):
            from_top = random.random() < 0.5
            cx = random.randrange(1, self.w - 1)
            cy = 0 if from_top else self.h - 1
            step = 1 if from_top else -1
            length = int(self.h * random.uniform(0.40, 0.75))
            drift = 0.0
            for i in range(length):
                ix, iy = int(cx), int(cy)
                if not (0 <= ix < self.w and 0 <= iy < self.h):
                    break
                core = P["purple_hi"] if i % 4 == 0 else P["purple"]
                # dark halo first so the crack looks recessed
                self.set(ix - 1, iy, P["black_deep"])
                self.set(ix + 1, iy, P["purple_lo"])
                self.set(ix, iy, core)
                self.glowset(ix, iy, core)
                cy += step
                drift += random.uniform(-0.55, 0.55)
                drift = max(-1.0, min(1.0, drift))
                cx += drift
                if random.random() < 0.10:   # short side branch
                    bx = int(cx) + random.choice([-1, 1])
                    for k in range(random.randint(1, 3)):
                        self.set(bx, int(cy) + k * step, P["purple_lo"])
                        self.glowset(bx, int(cy) + k * step, P["purple_lo"])

    def rivet(self, x, y, c=None, hi=None):
        c = c or P["gold_lo"]
        hi = hi or P["gold_hi"]
        self.set(x, y, c)
        self.set(x, y - 1, hi)


def lamellar(cv, f, base, rows=3, trim=True, lacing=True, corrupt_n=0):
    """Rows of overlapping armour plates with silk lacing and rivets."""
    cv.vgrad(sh(mix(base, (255, 255, 255), 0.10), f), sh(mix(base, (0, 0, 0), 0.22), f))
    cv.noise(sh(base, f), 8, 0.26)
    rh = rows * D
    for y0 in range(0, cv.h, rh):
        # plate highlight on top edge, shadow in the seam below
        cv.hline(y0, sh(mix(base, (255, 255, 255), 0.26), f), 0, cv.w)
        seam = min(cv.h - 1, y0 + rh - 1)
        cv.hline(seam, sh(P["black_deep"], f), 0, cv.w)
        if lacing:
            for x in range(1, cv.w, 4 * D):
                for yy in range(y0 + 1, seam):
                    cv.set(x, yy, sh(P["lace"], f))
                    cv.set(x + 1, yy, sh(P["lace_lo"], f))
        for x in range(2 * D, cv.w, 5 * D):
            cv.rivet(x, min(cv.h - 1, y0 + 2), sh(P["gold_lo"], f), sh(P["gold_hi"], f))
    if trim:
        cv.hline(0, sh(P["gold"], f))
        cv.hline(1, sh(P["gold_lo"], f))
        cv.hline(cv.h - 1, sh(P["gold_deep"], f))
    cv.edge_wear(sh(mix(base, (255, 255, 255), 0.35), f), 0.16)
    cv.fissure(corrupt_n)


def paint_face(mat, d, wu, hu):
    """Returns a Canvas for one face. wu/hu are the face size in world units."""
    f = FACE_SHADE[d]
    cv = Canvas(max(1, int(wu * D)), max(1, int(hu * D)))

    if mat == "lamellar":
        lamellar(cv, f, P["red"], corrupt_n=0)

    elif mat == "dou_front":
        lamellar(cv, f, P["red"], corrupt_n=0)
        # gold kamon (clan crest) in the centre of the chest
        cx, cy = cv.w // 2, cv.h // 2 - D
        r = max(3, min(cv.w, cv.h) // 5)
        for y in range(-r, r + 1):
            for x in range(-r, r + 1):
                dist = math.hypot(x, y)
                if dist <= r:
                    cv.set(cx + x, cy + y, sh(P["gold_deep"], f))
                if r - 1.4 <= dist <= r:
                    cv.set(cx + x, cy + y, sh(P["gold_hi"], f))
        for y in range(-r + 2, r - 1):
            cv.set(cx, cy + y, sh(P["gold_hi"], f))
        for x in range(-r + 2, r - 1):
            cv.set(cx + x, cy, sh(P["gold_hi"], f))

    elif mat == "dou_back":
        lamellar(cv, f, P["red"], corrupt_n=1)
        # sashimono mount strap down the spine
        for x in range(cv.w // 2 - 1, cv.w // 2 + 1):
            cv.vline(x, sh(P["black_lo"], f))

    elif mat == "sode_outer":
        lamellar(cv, f, P["red_hi"], rows=3, corrupt_n=0)
        cv.hline(cv.h - 2, sh(P["gold"], f))

    elif mat == "skirt":
        # kusazuri: vertical plate columns hanging in strips
        lamellar(cv, f, P["red_lo"], rows=2, corrupt_n=1)
        for x in range(0, cv.w, 5 * D):
            cv.vline(x, sh(P["black_deep"], f))

    elif mat == "neck_guard":
        lamellar(cv, f, P["red_lo"], rows=2, trim=True, corrupt_n=0)

    elif mat == "haidate":
        lamellar(cv, f, P["red_lo"], rows=4, lacing=False, corrupt_n=1)

    elif mat == "armor_black":
        cv.vgrad(sh(P["black_hi"], f), sh(P["black_deep"], f))
        cv.noise(sh(P["black"], f), 7, 0.25)
        cv.hline(cv.h - 1, sh(P["gold_lo"], f))
        cv.fissure(0)

    elif mat == "kabuto":
        # lacquered bowl with radiating ribs and a gold brow rim
        cv.vgrad(sh(P["black_hi"], f), sh(P["black_deep"], f))
        cv.noise(sh(P["black_lo"], f), 7, 0.22)
        for x in range(0, cv.w, 3 * D):
            cv.vline(x, sh(P["black_deep"], f))
            cv.vline(x + 1, sh(mix(P["black_hi"], (255, 255, 255), 0.12), f))
        cv.hline(cv.h - 1, sh(P["gold"], f))
        cv.hline(cv.h - 2, sh(P["gold_lo"], f))
        cv.hline(0, sh(P["gold_deep"], f))
        cv.fissure(1)

    elif mat == "kabuto_top_plate":
        cv.fill(sh(P["black_lo"], f))
        cv.noise(sh(P["black_lo"], f), 6, 0.3)
        cx, cy = cv.w // 2, cv.h // 2
        for y in range(cv.h):
            for x in range(cv.w):
                if abs(math.hypot(x - cx, y - cy) - min(cx, cy) * 0.55) < 0.9:
                    cv.set(x, y, sh(P["gold"], f))

    elif mat == "gold_cap":
        cv.vgrad(sh(P["gold_hi"], f), sh(P["gold_lo"], f))
        cv.noise(sh(P["gold"], f), 14, 0.3)
        cv.hline(cv.h - 1, sh(P["gold_deep"], f))
        cv.fissure(0)

    elif mat == "gold_solid":
        cv.vgrad(sh(P["gold_hi"], f), sh(P["gold_lo"], f))
        cv.noise(sh(P["gold"], f), 12, 0.3)
        cv.vline(0, sh(P["gold_hi"], f))
        cv.hline(cv.h - 1, sh(P["gold_deep"], f))

    elif mat == "horn":
        # Gilded prong: bright bevel down one side, dark groove down the middle.
        cv.vgrad(sh(P["gold_hi"], f), sh(P["gold"], f))
        cv.noise(sh(P["gold"], f), 10, 0.22)
        cv.vline(0, sh(P["gold_hi"], f))
        cv.vline(cv.w - 1, sh(P["gold_deep"], f))
        if cv.w >= 4:
            cv.vline(cv.w // 2, sh(P["gold_lo"], f))
        for y in range(0, cv.h, 3):          # chased ridges
            cv.hline(y, sh(P["gold_lo"], f), 1, cv.w - 1, 2)
        cv.hline(cv.h - 1, sh(P["gold_deep"], f))

    elif mat == "tsuba_guard":
        # Pierced iron guard with a gold rim.
        cv.fill(sh(P["black_lo"], f))
        cv.noise(sh(P["black"], f), 8, 0.3)
        cx, cy = cv.w / 2 - 0.5, cv.h / 2 - 0.5
        for y in range(cv.h):
            for x in range(cv.w):
                dist = math.hypot(x - cx, y - cy)
                if dist > min(cx, cy) * 0.86:
                    cv.set(x, y, sh(P["gold"], f))
                elif dist > min(cx, cy) * 0.68:
                    cv.set(x, y, sh(P["gold_deep"], f))
                elif abs(x - cx) < 0.9 or abs(y - cy) < 0.9:
                    cv.set(x, y, sh(P["black_deep"], f))   # pierced slots

    elif mat == "foot":
        cv.vgrad(sh(P["black"], f), sh(P["black_deep"], f))
        cv.noise(sh(P["black_lo"], f), 7, 0.28)
        cv.hline(0, sh(P["gold_lo"], f))
        if d == "down":                        # sole: straw-sandal weave
            for y in range(cv.h):
                for x in range(cv.w):
                    if (x // 2 + y // 2) % 2 == 0:
                        cv.set(x, y, sh(P["lace_lo"], f))
                    elif (x + y) % 3 == 0:
                        cv.set(x, y, sh(P["cloth_lo"], f))

    elif mat == "foot_top":
        cv.vgrad(sh(P["black_hi"], f), sh(P["black_lo"], f))
        cv.noise(sh(P["black"], f), 7, 0.28)
        # three overlapping instep plates, front-to-back
        for y0 in range(0, cv.h, max(2, cv.h // 3)):
            cv.hline(y0, sh(P["steel_lo"], f))
            cv.hline(min(cv.h - 1, y0 + 1), sh(P["black_deep"], f))
        cv.vline(0, sh(P["gold_lo"], f))
        cv.vline(cv.w - 1, sh(P["gold_lo"], f))
        cv.fissure(0)

    elif mat == "foot_toe":
        # Dark toe plate with a gold rim — a solid gold face read as a brick.
        cv.vgrad(sh(P["black_hi"], f), sh(P["black_deep"], f))
        cv.noise(sh(P["black"], f), 8, 0.3)
        cv.hline(0, sh(P["gold"], f))
        cv.vline(0, sh(P["gold_lo"], f))
        cv.vline(cv.w - 1, sh(P["gold_lo"], f))
        for x in range(2, cv.w - 1, 3 * D):
            cv.rivet(x, cv.h - 2, sh(P["gold_lo"], f), sh(P["gold_hi"], f))

    elif mat == "void_head":
        cv.vgrad(sh(P["void"], f), sh((4, 3, 7), f))
        cv.noise(sh(P["void"], f), 5, 0.2)
        cv.fissure(1)

    elif mat == "face":
        cv.vgrad(sh(P["void"], f), sh((5, 4, 9), f))
        cv.noise(sh(P["void"], f), 5, 0.25)
        # Burning eye slits. The head cube spans world y 35..45 and the exposed
        # band is y 40..42, which is (45-42)*D rows down from the top of the face.
        ey = int((45 - 42) * D)
        slit_h = max(2, int(1.5 * D))
        slit_w = max(3, int(3 * D))
        for side in (0, 1):
            x0 = 1 + side * (cv.w - slit_w - 2)
            for i in range(slit_w):
                taper = 0 if (0 < i < slit_w - 1) else 1   # pointed inner corners
                for j in range(taper, slit_h):
                    c = P["purple_hi"] if j < slit_h - 1 else P["purple"]
                    cv.set(x0 + i, ey + j, c)
                    cv.glowset(x0 + i, ey + j, c)
            # faint bleed below each eye
            for i in range(1, slit_w - 1):
                cv.set(x0 + i, ey + slit_h, P["purple_lo"])
                cv.glowset(x0 + i, ey + slit_h, P["purple_lo"])
        cv.fissure(0)

    elif mat == "iron_mask":
        cv.vgrad(sh(P["black_hi"], f), sh(P["black_lo"], f))
        cv.noise(sh(P["black"], f), 8, 0.28)
        cv.fissure(0)

    elif mat == "menpo_front":
        cv.vgrad(sh(P["black_hi"], f), sh(P["black_lo"], f))
        cv.noise(sh(P["black"], f), 9, 0.3)
        # cheek wrinkles
        for y in range(1, cv.h - 2, 2):
            cv.hline(y, sh(P["black_deep"], f), 1, cv.w - 1, 2)
        # snarling mouth with gold fangs
        my = cv.h - 3 * D
        if my > 0:
            cv.rect(2, my, max(1, cv.w - 4), max(1, D), sh(P["red_deep"], f))
            for i in range(3, cv.w - 3, 3):
                cv.set(i, my + D, sh(P["gold_hi"], f))
                cv.set(i, my + D + 1, sh(P["gold_lo"], f))
        # gold lip line
        cv.hline(max(0, my - 1), sh(P["gold_lo"], f), 1, cv.w - 1)
        cv.fissure(1)

    elif mat == "kote":
        # red sleeve at the top, splinted plates and mail below
        cv.vgrad(sh(P["cloth"], f), sh(P["black"], f))
        cv.noise(sh(P["black"], f), 8, 0.3)
        sleeve = max(1, int(cv.h * 0.28))
        for y in range(sleeve):
            for x in range(cv.w):
                cv.set(x, y, sh(mix(P["cloth"], P["cloth_lo"], y / max(1, sleeve)), f))
        for y in range(sleeve, cv.h):
            for x in range(cv.w):
                if (x + y) % 3 == 0:          # mail base
                    cv.set(x, y, sh(P["black_hi"], f))
        for x in range(1, cv.w, 3 * D):       # vertical splints
            cv.vline(x, sh(P["black_deep"], f), sleeve, cv.h)
            cv.vline(x + 1, sh(P["steel_deep"], f), sleeve, cv.h)
        # banded plates down the forearm so the limb isn't a flat black column
        band = max(2 * D, (cv.h - sleeve) // 3)
        for y0 in range(sleeve + 1, cv.h, band):
            cv.hline(y0, sh(P["steel_lo"], f))
            cv.hline(min(cv.h - 1, y0 + 1), sh(P["black_hi"], f))
            cv.hline(min(cv.h - 1, y0 + band - 1), sh(P["black_deep"], f))
            for x in range(D, cv.w, 4 * D):
                cv.rivet(x, min(cv.h - 1, y0 + 2), sh(P["gold_lo"], f), sh(P["gold_hi"], f))
        cv.hline(sleeve, sh(P["gold_lo"], f))
        cv.hline(max(0, sleeve - 1), sh(P["gold"], f))
        cv.fissure(1)

    elif mat == "boot":
        cv.vgrad(sh(P["black"], f), sh(P["black_deep"], f))
        cv.noise(sh(P["black_lo"], f), 7, 0.3)
        for y in range(0, cv.h, 2 * D):
            cv.hline(y, sh(P["black_deep"], f))
        cv.hline(0, sh(P["gold"], f))
        cv.hline(1, sh(P["gold_deep"], f))
        cv.fissure(0)

    elif mat == "tsuka":
        # ito wrap: alternating diamonds over black same, with a menuki stud
        cv.fill(sh(P["black_deep"], f))
        period = 3 * D
        for y in range(cv.h):
            for x in range(cv.w):
                a = (x + y) % period
                b = (x - y) % period
                if a < 2:
                    cv.set(x, y, sh(P["red_lo"], f))
                elif b < 2:
                    cv.set(x, y, sh(P["gold_deep"], f))
        cv.rivet(cv.w // 2, cv.h // 2, sh(P["gold"], f), sh(P["gold_hi"], f))

    elif mat in ("blade", "blade_tip"):
        if d in ("north", "south"):
            # Flat of the blade. x=0 is the cutting edge, x=w-1 the spine (mune).
            # Dark body near the spine, bright hardened steel along the edge, with
            # the hamon as the wavy boundary between them.
            for y in range(cv.h):
                for x in range(cv.w):
                    t = x / max(1, cv.w - 1)
                    cv.set(x, y, sh(mix(P["steel"], P["steel_deep"], t), f))
            for y in range(cv.h):
                wave = 1.0 + 0.9 * math.sin(y * 0.42) + 0.5 * math.sin(y * 0.17 + 1.3)
                edge = max(1, int(round(wave)))
                for x in range(0, min(cv.w, edge)):
                    cv.set(x, y, sh(P["steel_hi"], f))
                if edge < cv.w:                       # nioi: misty hamon border
                    cv.set(edge, y, sh(mix(P["steel_hi"], P["steel"], 0.45), f))
            # corrupted cutting edge, the only part that glows
            for y in range(cv.h):
                cv.set(0, y, P["purple_hi"] if y % 3 else P["purple"])
                cv.glowset(0, y, P["purple_hi"])
            for y in range(2, cv.h, 9):               # sparks licking up the steel
                cv.set(1, y, P["purple"])
                cv.glowset(1, y, P["purple"])
        else:
            cv.vgrad(sh(P["steel_lo"], f), sh(P["steel_deep"], f))
            cv.noise(sh(P["steel_lo"], f), 9, 0.25)
            cv.vline(0, P["purple"])
            for y in range(cv.h):
                cv.glowset(0, y, P["purple"])
        if mat == "blade_tip" and d in ("north", "south"):
            # Kissaki: diagonal yokote line, steel kept — only the edge corrupts,
            # so the tip reads as a point rather than a broken-off purple stub.
            for y in range(cv.h):
                for x in range(cv.w):
                    if x > cv.w - 1 - (y * cv.w) // max(1, cv.h):
                        cv.set(x, y, sh(P["steel_deep"], f))
            for y in range(cv.h):
                cv.set(0, y, P["purple_hi"])
                cv.glowset(0, y, P["purple_hi"])

    else:
        cv.fill(sh(P["black"], f))

    return cv


# ---------------------------------------------------------------------------
# Build faces, pack them, emit geometry + atlas
# ---------------------------------------------------------------------------
def build():
    faces = []   # (cube_name, dir, canvas)
    for b in BONES:
        for c in b["cubes"]:
            name, origin, size, mat = c[0], c[1], c[2], c[3]
            overrides = c[4] if len(c) > 4 else {}
            for d in DIRS:
                wu, hu = face_world_size(size, d)
                faces.append((name, d, paint_face(overrides.get(d, mat), d, wu, hu)))

    # shelf pack, tallest first
    order = sorted(range(len(faces)), key=lambda i: (-faces[i][2].h, -faces[i][2].w))
    place = {}
    shelf_y = shelf_h = cur_x = 0
    for i in order:
        cv = faces[i][2]
        if cur_x + cv.w > TEX:
            shelf_y += shelf_h
            shelf_h = cur_x = 0
        place[i] = (cur_x, shelf_y)
        cur_x += cv.w
        shelf_h = max(shelf_h, cv.h)
    used = shelf_y + shelf_h

    area = sum(f[2].w * f[2].h for f in faces)
    print(f"Per-face UV: {len(faces)} faces, {area} texels used of {TEX*TEX} "
          f"({100*area/(TEX*TEX):.0f}% fill), atlas rows used {used}/{TEX}")
    if used > TEX:
        raise SystemExit(f"ERROR: atlas overflow — needs {used} rows, have {TEX}")

    atlas = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    glowatlas = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    uv_of = {}
    for i, (cname, d, cv) in enumerate(faces):
        x, y = place[i]
        atlas.paste(cv.img, (x, y))
        glowatlas.paste(cv.glow, (x, y))
        uv_of[(cname, d)] = (x, y, cv.w, cv.h)

    # geometry
    bones_json = []
    for b in BONES:
        entry = {"name": b["name"]}
        if b["parent"]:
            entry["parent"] = b["parent"]
        entry["pivot"] = b["pivot"]
        if b.get("rotation"):
            entry["rotation"] = b["rotation"]
        cubes = []
        for c in b["cubes"]:
            name, origin, size = c[0], c[1], c[2]
            uv = {}
            for d in DIRS:
                x, y, w, h = uv_of[(name, d)]
                uv[d] = {"uv": [x, y], "uv_size": [w, h]}
            cubes.append({"origin": origin, "size": size, "uv": uv})
        if cubes:
            entry["cubes"] = cubes
        if b.get("locators"):
            entry["locators"] = b["locators"]
        bones_json.append(entry)

    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.samurai_shogun",
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 6,
                "visible_bounds_height": 7,
                "visible_bounds_offset": [0, 3, 0],
            },
            "bones": bones_json,
        }],
    }

    os.makedirs(f"{OUT_DIR}/assets/{MODID}/geo", exist_ok=True)
    os.makedirs(f"{OUT_DIR}/assets/{MODID}/textures/entity", exist_ok=True)
    with open(f"{OUT_DIR}/assets/{MODID}/geo/samurai_shogun.geo.json", "w") as f:
        json.dump(geo, f, indent=2)

    atlas.save(f"{OUT_DIR}/assets/{MODID}/textures/entity/samurai_shogun.png")
    glowatlas.save(f"{OUT_DIR}/assets/{MODID}/textures/entity/samurai_shogun_glowmask.png")
    atlas.resize((TEX * 3, TEX * 3), Image.NEAREST).save(f"{OUT_DIR}/texture_preview_x3.png")

    print("Wrote geo.json (per-face UV), texture, glowmask.")


if __name__ == "__main__":
    build()
