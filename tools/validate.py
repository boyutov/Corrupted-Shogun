#!/usr/bin/env python3
import argparse, json, math, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--geo', type=Path, default=ROOT / 'src/main/resources/assets/samuraiboss/geo/samurai_shogun.geo.json')
parser.add_argument('--animations', type=Path, default=ROOT / 'src/main/resources/assets/samuraiboss/animations/samurai_shogun.animation.json')
args = parser.parse_args()
GEO, ANM = args.geo, args.animations

errors, warns = [], []

geo = json.load(open(GEO))
anm = json.load(open(ANM))

g = geo["minecraft:geometry"][0]
tw = g["description"]["texture_width"]
th = g["description"]["texture_height"]
bones = g["bones"]

names = [b["name"] for b in bones]
if len(names) != len(set(names)):
    errors.append("duplicate bone names")

# parents resolve, no cycles
byname = {b["name"]: b for b in bones}
for b in bones:
    p = b.get("parent")
    if p and p not in byname:
        errors.append(f"bone {b['name']}: unknown parent {p}")
for b in bones:
    seen, cur = set(), b
    while cur.get("parent"):
        if cur["name"] in seen:
            errors.append(f"cycle at {b['name']}")
            break
        seen.add(cur["name"])
        cur = byname[cur["parent"]]

# parent declared before child (Blockbench tolerates either, but keep it tidy)
order = {n: i for i, n in enumerate(names)}
for b in bones:
    p = b.get("parent")
    if p and order[p] > order[b["name"]]:
        warns.append(f"bone {b['name']} declared before its parent {p}")

# UV bounds (per-face UV: each face carries its own uv + uv_size)
ncubes = 0
occupied = []
for b in bones:
    for c in b.get("cubes", []):
        ncubes += 1
        uv = c["uv"]
        if not isinstance(uv, dict):
            errors.append(f"cube in {b['name']}: expected per-face UV object, got {type(uv).__name__}")
            continue
        for face in ("north", "south", "east", "west", "up", "down"):
            if face not in uv:
                errors.append(f"cube in {b['name']}: missing '{face}' face UV")
                continue
            fu = uv[face]
            u, v = fu["uv"]
            fw, fh = fu["uv_size"]
            if fw <= 0 or fh <= 0:
                errors.append(f"cube in {b['name']} face {face}: non-positive uv_size {fw}x{fh}")
            if u < 0 or v < 0 or u + fw > tw or v + fh > th:
                errors.append(f"cube in {b['name']} face {face}: UV {u},{v} + {fw}x{fh} exceeds {tw}x{th}")
            occupied.append((u, v, fw, fh, f"{b['name']}.{face}"))

# overlap check
for i in range(len(occupied)):
    ax, ay, aw, ah, an = occupied[i]
    for j in range(i + 1, len(occupied)):
        bx, by, bw, bh, bn = occupied[j]
        if ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah:
            errors.append(f"UV overlap: {an} vs {bn}")

# height — static bone rotations must be applied, or rotated horns read as
# taller than they actually are
SWORD = {"katana", "blade_1", "blade_2", "blade_3"}

def rot_pt(p, pivot, rot):
    x, y, z = p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]
    rx, ry, rz = [math.radians(a) for a in (-rot[0], -rot[1], rot[2])]
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]

def posed_corners(cube, bone):
    o, s = cube["origin"], cube["size"]
    pts = [[o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]]
           for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    cur = bone
    while cur:
        if cur.get("rotation"):
            pts = [rot_pt(p, cur["pivot"], cur["rotation"]) for p in pts]
        cur = byname.get(cur.get("parent"))
    return pts

ys, xs = [], []
for b in bones:
    if b["name"] in SWORD:
        continue
    for c in b.get("cubes", []):
        for p in posed_corners(c, b):
            ys.append(p[1]); xs.append(p[0])
if ys:
    height = max(ys) - min(ys)
    print(f"Body spans y={min(ys):.2f} .. y={max(ys):.2f}  -> {height:.2f} units = {height/16:.2f} blocks")
    print(f"Body width {max(xs)-min(xs):.2f} units = {(max(xs)-min(xs))/16:.2f} blocks")
    if abs(min(ys)) > 0.01:
        errors.append(f"model does not sit on the ground: lowest y = {min(ys):.2f}")
    if height > 48.01:
        errors.append(f"body height is {height:.2f}, exceeds the 48-unit (3 block) limit")
    elif height < 47.5:
        errors.append(f"body height is only {height:.2f}, short of 48 units")

# animations reference real bones
expected = {"idle", "walk", "attack_slash", "attack_heavy", "defend_stance", "death",
            "phase_charge_windup", "phase_charge_run", "phase_heal_guard", "skyfall", "enrage_roar"}
found = set()
for key, clip in anm["animations"].items():
    short = key.split(".")[-1]
    found.add(short)
    if not isinstance(clip.get("animation_length"), (int, float)):
        errors.append(f"{key}: missing animation_length")
    for bone, chans in clip.get("bones", {}).items():
        if bone not in byname:
            errors.append(f"{key}: references unknown bone '{bone}'")
        for chan, kfs in chans.items():
            if chan not in ("rotation", "position", "scale"):
                errors.append(f"{key}/{bone}: unknown channel {chan}")
            times = []
            for t, val in kfs.items():
                try:
                    times.append(float(t))
                except ValueError:
                    errors.append(f"{key}/{bone}/{chan}: bad timestamp '{t}'")
                if not (isinstance(val, list) and len(val) == 3):
                    errors.append(f"{key}/{bone}/{chan}@{t}: value must be [x,y,z]")
            if times and max(times) > clip["animation_length"] + 1e-6:
                errors.append(f"{key}/{bone}/{chan}: keyframe {max(times)} past length {clip['animation_length']}")
    # Effect "locator" strings are resolved against BONE names: GeckoLib never
    # bakes `locators` into bones, so only a bone can report a world position.
    locs = set(byname.keys())
    for b in bones:
        locs.update(b.get("locators", {}).keys())
    for t, eff in clip.get("particle_effects", {}).items():
        if "locator" in eff and eff["locator"] not in locs:
            errors.append(f"{key}@{t}: unknown locator '{eff['locator']}'")

missing = expected - found
if missing:
    errors.append(f"missing animation clips: {sorted(missing)}")

print(f"Bones: {len(bones)}   Cubes: {ncubes}   Clips: {len(anm['animations'])}")
print(f"Texture: {tw}x{th}")
for w in warns:
    print("WARN :", w)
for e in errors:
    print("ERROR:", e)
print("\nRESULT:", "FAIL" if errors else "OK")
sys.exit(1 if errors else 0)
