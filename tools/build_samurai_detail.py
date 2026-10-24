#!/usr/bin/env python3
"""Paint an original 512px armour atlas in the existing UV layout. Geometry stays unchanged."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, math, random
import build_model as base
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/samuraiboss'
geo=json.loads((ASSETS/'geo/samurai_shogun.geo.json').read_text())['minecraft:geometry'][0]
base.D=4
random.seed(20261024)
size=geo['description']['texture_width']*2
atlas=Image.new('RGBA',(size,size)); glow=Image.new('RGBA',(size,size))
definitions={b['name']:b for b in base.BONES}
plate={'lamellar','dou_front','dou_back','sode_outer','skirt','neck_guard','haidate'}
for bone in geo['bones']:
 for i,cube in enumerate(bone.get('cubes',[])):
  desc=definitions[bone['name']]['cubes'][i]
  mat=desc[3]; overrides=desc[4] if len(desc)>4 else {}
  for face,uv in cube['uv'].items():
   material=overrides.get(face,mat)
   wu,hu=base.face_world_size(cube['size'],face)
   cv=base.paint_face(material,face,wu,hu)
   w,h=[round(n*2) for n in uv['uv_size']]
   tile=cv.img.resize((w,h),Image.Resampling.NEAREST);light=cv.glow.resize((w,h),Image.Resampling.NEAREST)
   d=ImageDraw.Draw(tile);gd=ImageDraw.Draw(light)
   f=base.FACE_SHADE[face];gold=base.sh(base.P['gold'],f);dark=base.sh(base.P['gold_deep'],f)
   if material in plate and w>=12 and h>=8:
    # Layered gold piping and etched wave motifs; silk X-laces between the plates.
    for edge in [1,w-3]:
     d.line((edge,1,edge,h-2),fill=dark)
     d.line((edge+1,1,edge+1,h-2),fill=gold)
    for y in range(6,h-2,12):
     for x in range(6,w-3,12):
      d.line([(x-2,y-2),(x+2,y+2)],fill='#e2ccb0',width=1)
      d.line([(x+2,y-2),(x-2,y+2)],fill='#927555',width=1)
      d.point((x,y-3),fill='#faf0cf')
    for x in range(4,w-4):
     y=h-5-round(math.sin(x*.48)*1.5)
     d.point((x,y),fill=gold)
    # Deliberate, sparse exposed-steel nicks rather than uniform noise.
    for k in range(max(1,w*h//450)):
     x=random.randrange(3,max(4,w-3));y=random.randrange(2,max(3,h-2))
     d.line((x,y,min(w-2,x+3),y-1),fill='#b78677')
   if material=='dou_front':
    cx,cy=w//2,h//2-4;r=max(7,min(w,h)//5)
    d.ellipse((cx-r-2,cy-r-2,cx+r+2,cy+r+2),fill='#31161d',outline=gold,width=2)
    # Three crescent-shaped tomoe around a small void-purple core.
    for k in range(3):
     a=k*math.tau/3-math.pi/2
     px=cx+math.cos(a)*r*.46;py=cy+math.sin(a)*r*.46
     rr=r*.42
     d.ellipse((px-rr,py-rr,px+rr,py+rr),fill='#fce296')
     d.ellipse((px-rr+2,py-rr-1,px+rr+2,py+rr-1),fill='#31161d')
    d.ellipse((cx-1,cy-1,cx+1,cy+1),fill='#ce9cff');gd.point((cx,cy),fill='#8e4cd8')
   if material in {'kabuto','armor_black','iron_mask','boot'} and w>=10 and h>=8:
    # Engraved fine diamond borders on lacquer, with bright bevels.
    for x in range(4,w-2,8):
     d.line([(x-2,3),(x,1),(x+2,3),(x,5),(x-2,3)],fill='#887447')
     d.point((x,1),fill='#d4a23c')
   if material=='menpo_front' and w>12:
    cx=w//2
    d.line([(cx-2,1),(cx-3,h//2),(cx,h//2+1),(cx+3,h//2),(cx+2,1)],fill='#b39872',width=1)
    for sign in [-1,1]:
     d.line([(cx+sign*5,2),(cx+sign*9,5),(cx+sign*12,4)],fill='#7c6c77',width=1)
     x=cx+sign*(w//3)
     d.polygon([(x-2,h-7),(x+2,h-7),(x,h-2)],fill='#fce296')
   if material in {'gold_solid','gold_cap','horn'} and w>=4:
    for y in range(3,h-2,6):
     d.line((1,y,w-2,y+1),fill='#977025')
     d.point((1,y-1),fill='#fff0bb')
   if material in {'blade','blade_tip'} and face in {'north','south'} and w>=5:
    for y in range(1,h-1):
     x=min(w-2,2+round((math.sin(y*.32)+1)*.8))
     d.point((x,y),fill='#e8eef8')
   x,y=[round(n*2) for n in uv['uv']]
   atlas.paste(tile,(x,y));glow.paste(light,(x,y))
atlas.save(ASSETS/'textures/entity/samurai_shogun.png')
glow.save(ASSETS/'textures/entity/samurai_shogun_glowmask.png')
print('Painted 512x512 detailed armour and matched glowmask in existing UV layout')
