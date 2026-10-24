#!/usr/bin/env python3
"""Author the skyfall and rage poses; bake smooth interpolation to portable vector keyframes."""
import json, math
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / 'src/main/resources/assets/samuraiboss/animations/samurai_shogun.animation.json'
doc = json.loads(PATH.read_text())
animations = doc['animations']
idle = animations['animation.samurai_shogun.idle']['bones']
neutral = {bone: {ch: list(next(iter(keys.values()))) for ch, keys in channels.items()}
           for bone, channels in idle.items()}

def pose(rotations=None, positions=None):
    p = deepcopy(neutral)
    for bone, value in (rotations or {}).items():
        p.setdefault(bone, {})['rotation'] = value
    for bone, value in (positions or {}).items():
        p.setdefault(bone, {})['position'] = value
    return p

geometry = json.loads((ROOT / 'src/main/resources/assets/samuraiboss/geo/samurai_shogun.geo.json').read_text())
geo_bones = {b['name']: b for b in geometry['minecraft:geometry'][0]['bones']}

def min_height(pose, names):
    ys=[]
    for name in names:
        bone=geo_bones[name]
        for cube in bone.get('cubes', []):
            o,s=cube['origin'],cube['size']
            for dx in (0,1):
                for dy in (0,1):
                    for dz in (0,1):
                        point=[o[0]+dx*s[0], o[1]+dy*s[1], o[2]+dz*s[2]]
                        cur=bone
                        while cur:
                            animation=pose.get(cur['name'],{})
                            rotation=[a+b for a,b in zip(cur.get('rotation',[0,0,0]),animation.get('rotation',[0,0,0]))]
                            pivot=cur['pivot']
                            x,y,z=[point[i]-pivot[i] for i in range(3)]
                            rx,ry,rz=[math.radians(v) for v in (-rotation[0],-rotation[1],rotation[2])]
                            x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
                            x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
                            y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
                            pos=animation.get('position',[0,0,0])
                            point=[v+p+t for v,p,t in zip((x,y,z),pivot,pos)]
                            cur=geo_bones.get(cur.get('parent'))
                        ys.append(point[1])
    return min(ys)

def ground_pose(p):
    p.setdefault('root',{}).setdefault('position',[0,0,0])[1] -= min_height(p,['right_foot','left_foot'])
    sword=['katana','blade_1','blade_2','blade_3']
    if min_height(p,sword) < -0.2:
        original=p['katana']['rotation'][0]
        # Bend the wrist only as far as necessary to put the long nodachi on the surface.
        for delta in range(1,121):
            p['katana']['rotation'][0]=original-delta
            if min_height(p,sword)>=0.15:
                break

def clip(frames):
    bones = {}
    names = set().union(*(p.keys() for _, p in frames))
    for bone in sorted(names):
        channels = set().union(*(p.get(bone, {}).keys() for _, p in frames))
        bones[bone] = {}
        for channel in sorted(channels):
            keys = {}
            default = [1,1,1] if channel == 'scale' else [0,0,0]
            for (start, a), (end, b) in zip(frames, frames[1:]):
                va = a.get(bone, {}).get(channel, default)
                vb = b.get(bone, {}).get(channel, default)
                count = max(1, round((end-start) * 20))
                for i in range(count+1):
                    t = i / count
                    ease = t*t*(3-2*t)
                    time = round(start + (end-start)*t, 3)
                    keys[str(time)] = [round(va[j] + (vb[j]-va[j])*ease, 3) for j in range(3)]
            bones[bone][channel] = keys
    return {'loop': False, 'animation_length': frames[-1][0], 'bones': bones}

crouch = pose({
    'waist': [12, 0, 0], 'torso': [24, -10, 0], 'head': [-18, 10, 0],
    'right_arm': [-70,-15,4], 'left_arm': [-80,20,-8], 'katana':[142,0,0],
    'right_leg':[-55,0,-9], 'left_leg':[-48,0,9],
    'right_boot':[58,-6,0], 'left_boot':[51,6,0], 'kusazuri':[-20,0,0],
}, {'root':[0,-5,0], 'left_arm':[-13,-1,-6]})
lift = pose({
    'waist':[-8,0,0], 'torso':[-14,-8,0], 'head':[-8,8,0],
    'right_arm':[-172,-6,0], 'left_arm':[-176,10,0], 'katana':[172,0,0],
    'right_leg':[25,0,-8], 'left_leg':[-38,0,8],
    'right_boot':[-24,-6,0], 'left_boot':[36,6,0], 'kusazuri':[18,0,0],
}, {'left_arm':[-13,1,-2]})
apex = pose({
    'waist':[-12,0,0], 'torso':[-20,0,0], 'head':[2,0,0],
    'right_shoulder':[0,0,12], 'left_shoulder':[0,0,-12],
    'right_arm':[-174,-6,0], 'left_arm':[-178,10,0], 'katana':[175,0,0],
    'right_leg':[-38,0,-8], 'left_leg':[-24,0,8],
    'right_boot':[45,-6,0], 'left_boot':[32,6,0], 'kusazuri':[-14,0,0],
}, {'left_arm':[-13,1,-2]})
fall = pose({
    'waist':[5,0,0], 'torso':[8,0,0], 'head':[5,0,0],
    'right_arm':[-130,-4,0], 'left_arm':[-138,10,0], 'katana':[168,0,0],
    'right_leg':[-12,0,-10], 'left_leg':[5,0,10],
    'right_boot':[10,-6,0], 'left_boot':[-8,6,0], 'kusazuri':[-18,0,0],
}, {'left_arm':[-13,0,-4]})
impact = pose({
    'waist':[18,0,0], 'torso':[26,0,0], 'head':[8,0,0],
    'right_arm':[-18,0,0], 'left_arm':[-22,8,0], 'katana':[143,0,0],
    'right_leg':[-42,0,-10], 'left_leg':[-18,0,10],
    'right_boot':[40,-6,0], 'left_boot':[15,6,0], 'kusazuri':[18,0,0],
}, {'root':[0,-2,0], 'left_arm':[-13,-3,-8]})
settle = pose({
    'waist':[12,0,0], 'torso':[17,0,0], 'head':[-5,0,0],
    'right_arm':[-30,-6,2], 'left_arm':[-34,18,-8], 'katana':[148,0,0],
    'right_leg':[-20,0,-8], 'left_leg':[-8,0,8],
    'right_boot':[18,-6,0], 'left_boot':[7,6,0], 'kusazuri':[8,0,0],
}, {'root':[0,-1,0], 'left_arm':[-13,-2,-6]})
for grounded in [crouch, impact, settle]:
    ground_pose(grounded)
sky = clip([(0,pose()), (0.55,crouch), (0.75,crouch), (0.95,lift),
            (1.35,apex), (1.65,apex), (1.9,fall), (2.0,impact),
            (2.15,impact), (2.45,settle), (3.0,pose())])
sky['sound_effects'] = {'0.55': {'effect':'samurai_raise'}, '1.90': {'effect':'samurai_slash'}}
# Landing VFX are fired by the server rather than a speculative animation frame.
sky['particle_effects'] = {'1.8': {'effect':'corruption_slash','locator':'blade_mid'}}
animations['animation.samurai_shogun.skyfall'] = sky

gather = pose({
    'waist':[8,0,0], 'torso':[18,0,0], 'head':[24,0,0],
    'right_arm':[-12,-12,18], 'left_arm':[-25,5,-18], 'katana':[100,0,-10],
    'right_leg':[-16,0,-12], 'left_leg':[-16,0,12],
    'right_boot':[12,-6,0], 'left_boot':[12,6,0], 'kusazuri':[-8,0,0],
}, {'root':[0,-2,0], 'left_arm':[0,0,0]})
roar = pose({
    'waist':[-6,0,0], 'torso':[-16,0,0], 'head':[-28,0,0],
    'right_shoulder':[0,0,-10], 'left_shoulder':[0,0,10],
    'right_arm':[2,-12,-38], 'left_arm':[-8,10,52], 'katana':[110,0,-8],
    'right_leg':[-8,0,-12], 'left_leg':[-8,0,12],
    'right_boot':[14,-6,0], 'left_boot':[14,6,0], 'kusazuri':[12,0,0],
}, {'root':[0,0,0], 'left_arm':[0,0,0]})
recoil=deepcopy(roar)
recoil['torso']['rotation']=[-10,0,0]
recoil['head']['rotation']=[-20,0,0]
for grounded in [gather, roar, recoil]:
    ground_pose(grounded)
rage = clip([(0,pose()), (0.65,gather), (0.9,gather), (1.0,roar),
             (1.15,recoil), (1.3,roar), (1.55,recoil), (2.2,pose())])
rage['sound_effects']={'0.45':{'effect':'samurai_raise'}}
animations['animation.samurai_shogun.enrage_roar']=rage
PATH.write_text(json.dumps(doc, indent=2)+'\n')
print('Wrote skyfall (3.0s) and enrage_roar (2.2s)')
