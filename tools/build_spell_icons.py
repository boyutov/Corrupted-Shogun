#!/usr/bin/env python3
"""Original HUD pictograms. Small, deterministic pixel art; no downloaded assets."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/samuraiboss/textures/gui/spells'
out.mkdir(parents=True,exist_ok=True)
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
for x,y,w in [(29,2,7),(24,1,5),(31,9,5)]:
 d.polygon([(x,y),(x-w,y+15),(7,26),(5,17)],fill='#df462a')
 d.polygon([(x-2,y+4),(x-w,y+13),(9,24),(8,17)],fill='#ffad39')
d.polygon([(9,12),(17,14),(21,21),(17,28),(9,30),(3,24),(3,17)],fill='#292336')
d.line([(4,18),(9,13),(16,15),(19,21)],fill='#c16b58',width=2)
d.line([(9,14),(11,21),(7,26)],fill='#ffda75',width=2)
d.line([(11,21),(17,24),(17,27)],fill='#f46d30',width=1)
im.save(out/'meteor.png')
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.ellipse((4,3,28,28),fill='#34204e',outline='#8f4fd2',width=2)
d.ellipse((7,5,25,26),fill='#0b0818',outline='#e5b7ff',width=1)
for i in range(3):
 points=[]
 for k in range(110):
  t=k/109*math.tau;r=13-i
  points.append((16+math.cos(t)*r,16+math.sin(t)*r*.30-math.cos(t)*3))
 d.line(points,fill=['#7041b2','#b88af0','#f3d6ff'][i],width=1)
d.ellipse((11,9,21,21),fill='#080611')
im.save(out/'black_hole.png')
