#!/usr/bin/env python3
"""
Orthographic preview that samples the real .animation.json at given timestamps
and renders the posed geometry with a painter's algorithm.

Rotation convention matches Blockbench / GeckoLib: X and Y are negated relative
to a plain right-handed rotation, so negative X swings a hanging limb forward.
"""
import json, math, re, sys
from PIL import Image, ImageDraw

GEO = "samurai_shogun/assets/modid/geo/samurai_shogun.geo.json"
ANM = "samurai_shogun/assets/modid/animations/samurai_shogun.animation.json"

geo = json.load(open(GEO))
anm = json.load(open(ANM))["animations"]
bones = geo["minecraft:geometry"][0]["bones"]
byname = {b["name"]: b for b in bones}

COL = {
    "pelvis": (34, 34, 44), "kusazuri": (120, 26, 28), "dou": (132, 28, 30),
    "collar": (34, 34, 44), "head": (16, 14, 24), "menpo": (52, 52, 64),
    "kabuto_bowl": (26, 26, 34), "kabuto_top": (214, 164, 62), "shikoro": (112, 24, 26),
    "sode_r": (150, 34, 36), "sode_l": (150, 34, 36),
    "arm_r": (36, 36, 46), "arm_l": (36, 36, 46),
    "tsuka": (22, 20, 28), "tsuba": (150, 120, 60),
    "kashira": (214, 164, 62), "fuchi": (214, 164, 62), "habaki": (236, 196, 96),
    "blade_a": (190, 199, 215), "blade_b": (190, 199, 215), "blade_c": (190, 199, 215),
    "horn_r1": (230, 180, 70), "horn_r2": (244, 206, 118),
    "horn_l1": (230, 180, 70), "horn_l2": (244, 206, 118),
    "foot_r": (30, 30, 38), "foot_l": (30, 30, 38),
    "blade": (190, 199, 215), "kissaki": (165, 125, 235),
    "leg_r": (104, 22, 24), "leg_l": (104, 22, 24),
    "boot_r": (26, 26, 34), "boot_l": (26, 26, 34),
}

src = open("build_model.py").read()
defs = re.findall(r'\("([a-z_0-9]+)", (\[[^\]]+\]), (\[[^\]]+\]), "', src)
lookup = {(tuple(json.loads(o)), tuple(json.loads(s))): n for n, o, s in defs}


def sample(clip_name, bone_name, channel, t, default):
    clip = anm[f"animation.samurai_shogun.{clip_name}"]
    chans = clip.get("bones", {}).get(bone_name)
    if not chans or channel not in chans:
        return default
    kfs = sorted(((float(k), v) for k, v in chans[channel].items()), key=lambda x: x[0])
    if t <= kfs[0][0]:
        return kfs[0][1]
    if t >= kfs[-1][0]:
        return kfs[-1][1]
    for i in range(len(kfs) - 1):
        t0, v0 = kfs[i]
        t1, v1 = kfs[i + 1]
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [v0[j] + (v1[j] - v0[j]) * f for j in range(3)]
    return default


def rot_pt(p, pivot, rot):
    x, y, z = p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]
    rx, ry, rz = [math.radians(a) for a in (-rot[0], -rot[1], rot[2])]
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]


def chain(bone, clip, t):
    out, cur = [], bone
    while cur:
        base = list(cur.get("rotation", [0, 0, 0]))
        anim = sample(clip, cur["name"], "rotation", t, [0, 0, 0])
        rot = [base[i] + anim[i] for i in range(3)]
        pos = sample(clip, cur["name"], "position", t, [0, 0, 0])
        scl = sample(clip, cur["name"], "scale", t, [1, 1, 1])
        out.append((cur["pivot"], rot, pos, scl))
        cur = byname.get(cur.get("parent"))
    return out


FACES = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
SHADE = [1.0, 0.86, 0.70, 1.14, 0.93, 0.93]


def build(clip, t):
    faces = []
    for b in bones:
        tf = chain(b, clip, t)
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            name = lookup.get((tuple(o), tuple(s)), "")
            col = COL.get(name, (120, 120, 120))
            pts = [[o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]]
                   for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            dead = False
            for pivot, rot, pos, scl in tf:
                if min(scl) <= 0.001:
                    dead = True
                    break
                pts = [[pivot[0] + (p[0] - pivot[0]) * scl[0],
                        pivot[1] + (p[1] - pivot[1]) * scl[1],
                        pivot[2] + (p[2] - pivot[2]) * scl[2]] for p in pts]
                if any(rot):
                    pts = [rot_pt(p, pivot, rot) for p in pts]
                if any(pos):
                    pts = [[p[0] + pos[0], p[1] + pos[1], p[2] + pos[2]] for p in pts]
            if dead:
                continue
            for fi, idx in enumerate(FACES):
                quad = [pts[i] for i in idx]
                faces.append((quad, tuple(max(0, min(255, int(v * SHADE[fi]))) for v in col)))
    return faces


SHOTS = [
    ("idle", 0.0),
    ("phase_charge_windup", 0.3), ("phase_charge_windup", 1.0),
    ("phase_charge_run", 0.0), ("phase_charge_run", 0.25),
    ("phase_heal_guard", 0.0),
    ("attack_heavy", 0.55), ("death", 1.2),
]
VIEWS = [("SIDE", lambda p: (-p[2], p[1]), lambda q: max(c[0] for c in q)),
         ("FRONT", lambda p: (-p[0], p[1]), lambda q: max(c[2] for c in q))]

SCALE = 5.6
PANEL = 250
ROW = int(58 * SCALE) + 40
W = PANEL * len(SHOTS)
H = ROW * len(VIEWS)
img = Image.new("RGB", (W, H), (240, 238, 234))
d = ImageDraw.Draw(img)

for vi, (vname, proj, depth) in enumerate(VIEWS):
    gy = ROW * (vi + 1) - 22
    for blk in range(1, 4):
        y = gy - blk * 16 * SCALE
        d.line([(0, y), (W, y)], fill=(222, 170, 170), width=1)
    d.line([(0, gy), (W, gy)], fill=(130, 128, 122), width=2)
    for si, (clip, t) in enumerate(SHOTS):
        ox = si * PANEL + PANEL // 2
        d.text((ox - 70, ROW * vi + 6), f"{vname}  {clip} @ {t}s", fill=(70, 70, 70))
        faces = build(clip, t)
        for quad, col in sorted(faces, key=lambda f: -depth(f[0])):
            pts = [(ox + proj(p)[0] * SCALE, gy - proj(p)[1] * SCALE) for p in quad]
            d.polygon(pts, fill=col, outline=(0, 0, 0))
        d.line([(si * PANEL, ROW * vi), (si * PANEL, ROW * (vi + 1))], fill=(210, 208, 202))

img.save("samurai_shogun/pose_sheet.png")
print("wrote pose_sheet.png", img.size)

# report sword tip height per shot, to catch ground clipping
for clip, t in SHOTS:
    faces = build(clip, t)
    ys = [c[1] for q, _ in faces for c in q]
    print(f"  {clip:14s} @{t:<5} lowest y = {min(ys):7.1f}   highest y = {max(ys):6.1f}")
