#!/usr/bin/env python3
"""
Textured orthographic render of the per-face-UV model in T-pose.
Each cube face projects to an axis-aligned rectangle in front/side/back views,
so the face's texture region is resized onto it with painter ordering.
"""
import json, math
from PIL import Image

GEO = "samurai_shogun/assets/modid/geo/samurai_shogun.geo.json"
TEXP = "samurai_shogun/assets/modid/textures/entity/samurai_shogun.png"

geo = json.load(open(GEO))
g = geo["minecraft:geometry"][0]
bones = g["bones"]
byname = {b["name"]: b for b in bones}
tex = Image.open(TEXP).convert("RGBA")

S = 11          # output pixels per world unit
MARGIN = 20


def rot_pt(p, pivot, rot):
    x, y, z = p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]
    rx, ry, rz = [math.radians(a) for a in (-rot[0], -rot[1], rot[2])]
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]


def world_bounds(cube, bone):
    o, s = cube["origin"], cube["size"]
    pts = [[o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]]
           for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    cur = bone
    while cur:
        r = cur.get("rotation")
        if r:
            pts = [rot_pt(p, cur["pivot"], r) for p in pts]
        cur = byname.get(cur.get("parent"))
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]; zs = [p[2] for p in pts]
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


VIEWS = {
    # name: (face shown, horizontal axis getter, depth key, flip_h)
    "FRONT": ("north", "x", "z", True),
    "BACK":  ("south", "x", "z", False),
    "RIGHT": ("west",  "z", "x", True),
    "LEFT":  ("east",  "z", "x", False),
}

panels = []
for vname, (face, haxis, daxis, fliph) in VIEWS.items():
    items = []
    for b in bones:
        for c in b.get("cubes", []):
            x0, x1, y0, y1, z0, z1 = world_bounds(c, b)
            if haxis == "x":
                h0, h1 = x0, x1
            else:
                h0, h1 = z0, z1
            if daxis == "z":
                depth = z0 if face == "north" else -z1
            else:
                depth = x0 if face == "west" else -x1
            fu = c["uv"][face]
            items.append((depth, h0, h1, y0, y1, fu))
    # far first
    items.sort(key=lambda t: -t[0])

    allh = [v for it in items for v in (it[1], it[2])]
    ally = [v for it in items for v in (it[3], it[4])]
    W = int((max(allh) - min(allh)) * S) + MARGIN * 2
    H = int((max(ally) - min(ally)) * S) + MARGIN * 2
    panel = Image.new("RGBA", (W, H), (239, 237, 233, 255))
    hmin, ymax = min(allh), max(ally)

    for depth, h0, h1, y0, y1, fu in items:
        ux, uy = fu["uv"]; uw, uh = fu["uv_size"]
        src = tex.crop((int(ux), int(uy), int(ux + uw), int(uy + uh)))
        pw = max(1, int(round((h1 - h0) * S)))
        ph = max(1, int(round((y1 - y0) * S)))
        src = src.resize((pw, ph), Image.NEAREST)
        if fliph:
            src = src.transpose(Image.FLIP_LEFT_RIGHT)
        px = int(round((h0 - hmin) * S)) + MARGIN
        py = int(round((ymax - y1) * S)) + MARGIN
        if fliph:
            px = W - MARGIN - int(round((h1 - hmin) * S))
        panel.alpha_composite(src, (px, py))
    panels.append((vname, panel))

gap = 16
W = sum(p.width for _, p in panels) + gap * (len(panels) + 1)
H = max(p.height for _, p in panels) + 30
out = Image.new("RGBA", (W, H), (239, 237, 233, 255))
x = gap
from PIL import ImageDraw
d = ImageDraw.Draw(out)
for vname, p in panels:
    out.alpha_composite(p, (x, 24))
    d.text((x + 6, 8), vname, fill=(70, 70, 70))
    x += p.width + gap

out.convert("RGB").save("samurai_shogun/textured_render.png")
print("wrote textured_render.png", out.size)
