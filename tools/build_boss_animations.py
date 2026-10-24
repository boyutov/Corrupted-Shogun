#!/usr/bin/env python3
"""Author the skyfall and rage poses; bake smooth interpolation to portable vector keyframes."""
import json, math
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / 'src/main/resources/assets/samuraiboss/animations/samurai_shogun.animation.json'
doc = json.loads(PATH.read_text())
animations = doc['animations']
# The shogun wields the nodachi in the right hand only; the left arm is free (positive Z swings
# it outward). Hand-authored loops are rewritten here so neutral below starts one-handed too.
def free_left_arm(name, rotation):
    animations['animation.samurai_shogun.'+name]['bones']['left_arm'] = {
        'rotation': rotation, 'position': {'0.0': [0,0,0]}}
free_left_arm('idle', {'0.0':[-12,0,10], '2.0':[-9,0,12], '4.0':[-12,0,10]})
# Walking swings the arm against the same-side leg.
walk_leg = animations['animation.samurai_shogun.walk']['bones']['left_leg']['rotation']
free_left_arm('walk', {t:[round(-10-0.7*v[0]),0,10] for t,v in walk_leg.items()})
free_left_arm('defend_stance', {'0.0':[-55,-20,14], '1.0':[-52,-20,14], '2.0':[-55,-20,14]})
free_left_arm('phase_heal_guard', {'0.0':[-50,-25,10], '1.2':[-47,-25,10], '2.4':[-50,-25,10]})
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

EASES = {
    'smooth': lambda t: t*t*(3-2*t),
    'linear': lambda t: t,
    'in': lambda t: t*t*t,                   # accelerates: velocity peaks on the contact frame
    'out': lambda t: 1-(1-t)**3,             # decelerates: follow-through bleeding off energy
    'snap': lambda t: 1-(1-t)**4,            # a hard stop, used after a strike lands
}

def clip(frames, lag=None, rate=20):
    """Bake (time, pose[, ease]) frames; ease shapes the segment ending at that frame.
    lag delays a bone's motion by up to that many seconds (overlapping action), fading
    to zero over the last 0.2 s so the clip still ends exactly on its final pose."""
    frames = [f if len(f) == 3 else (*f, 'smooth') for f in frames]
    length = frames[-1][0]
    lag = lag or {}
    def value(bone, channel, t, default):
        for (start, a, _), (end, b, ease) in zip(frames, frames[1:]):
            if t <= end or end == length:
                va = a.get(bone, {}).get(channel, default)
                vb = b.get(bone, {}).get(channel, default)
                k = EASES[ease](min(1, max(0, (t-start)/(end-start))))
                return [va[j] + (vb[j]-va[j])*k for j in range(3)]
    times = set()
    for (start, *_), (end, *_) in zip(frames, frames[1:]):
        count = max(2 if end-start < 0.1 else 1, round((end-start) * rate))
        times.update(round(start + (end-start)*i/count, 3) for i in range(count+1))
    bones = {}
    names = set().union(*(p.keys() for _, p, _ in frames))
    for bone in sorted(names):
        channels = set().union(*(p.get(bone, {}).keys() for _, p, _ in frames))
        bones[bone] = {}
        for channel in sorted(channels):
            default = [1,1,1] if channel == 'scale' else [0,0,0]
            delay = lag.get(bone, 0)
            bones[bone][channel] = {str(t): [round(v, 3) for v in
                value(bone, channel, max(0, t - delay*min(1, (length-t)/0.2)), default)]
                for t in sorted(times)}
    return {'loop': False, 'animation_length': length, 'bones': bones}

crouch = pose({
    'waist': [12, 0, 0], 'torso': [24, -10, 0], 'head': [-18, 10, 0],
    'right_arm': [-70,-15,4], 'left_arm': [25,0,25], 'katana':[142,0,0],
    'right_leg':[-55,0,-9], 'left_leg':[-48,0,9],
    'right_boot':[58,-6,0], 'left_boot':[51,6,0], 'kusazuri':[-20,0,0],
}, {'root':[0,-5,0]})
lift = pose({
    'waist':[-8,0,0], 'torso':[-14,-8,0], 'head':[-8,8,0],
    'right_arm':[-172,-6,0], 'left_arm':[-70,0,35], 'katana':[172,0,0],
    'right_leg':[25,0,-8], 'left_leg':[-38,0,8],
    'right_boot':[-24,-6,0], 'left_boot':[36,6,0], 'kusazuri':[18,0,0],
}, None)
apex = pose({
    'waist':[-12,0,0], 'torso':[-20,0,0], 'head':[2,0,0],
    'right_shoulder':[0,0,12], 'left_shoulder':[0,0,-12],
    'right_arm':[-174,-6,0], 'left_arm':[-40,0,60], 'katana':[175,0,0],
    'right_leg':[-38,0,-8], 'left_leg':[-24,0,8],
    'right_boot':[45,-6,0], 'left_boot':[32,6,0], 'kusazuri':[-14,0,0],
}, None)
fall = pose({
    'waist':[5,0,0], 'torso':[8,0,0], 'head':[5,0,0],
    'right_arm':[-130,-4,0], 'left_arm':[-90,0,40], 'katana':[168,0,0],
    'right_leg':[-12,0,-10], 'left_leg':[5,0,10],
    'right_boot':[10,-6,0], 'left_boot':[-8,6,0], 'kusazuri':[-18,0,0],
}, None)
impact = pose({
    'waist':[18,0,0], 'torso':[26,0,0], 'head':[8,0,0],
    'right_arm':[-18,0,0], 'left_arm':[25,0,40], 'katana':[143,0,0],
    'right_leg':[-42,0,-10], 'left_leg':[-18,0,10],
    'right_boot':[40,-6,0], 'left_boot':[15,6,0], 'kusazuri':[18,0,0],
}, {'root':[0,-2,0]})
settle = pose({
    'waist':[12,0,0], 'torso':[17,0,0], 'head':[-5,0,0],
    'right_arm':[-30,-6,2], 'left_arm':[8,0,22], 'katana':[148,0,0],
    'right_leg':[-20,0,-8], 'left_leg':[-8,0,8],
    'right_boot':[18,-6,0], 'left_boot':[7,6,0], 'kusazuri':[8,0,0],
}, {'root':[0,-1,0]})
for grounded in [crouch, impact, settle]:
    ground_pose(grounded)
sky = clip([(0,pose()), (0.55,crouch), (0.75,crouch), (0.95,lift),
            (1.35,apex,'out'), (1.65,apex), (1.9,fall,'in'), (2.0,impact,'linear'),
            (2.15,impact,'snap'), (2.45,settle), (3.0,pose())])
sky['sound_effects'] = {'0.55': {'effect':'samurai_raise'}, '1.90': {'effect':'samurai_slash'}}
# Landing VFX are fired by the server rather than a speculative animation frame.
sky['particle_effects'] = {'1.8': {'effect':'corruption_slash','locator':'blade_mid'}}
animations['animation.samurai_shogun.skyfall'] = sky

gather = pose({
    'waist':[8,0,0], 'torso':[18,0,0], 'head':[24,0,0],
    'right_arm':[-12,-12,18], 'left_arm':[-25,5,14], 'katana':[100,0,-10],
    'right_leg':[-16,0,-12], 'left_leg':[-16,0,12],
    'right_boot':[12,-6,0], 'left_boot':[12,6,0], 'kusazuri':[-8,0,0],
}, {'root':[0,-2,0]})
roar = pose({
    'waist':[-6,0,0], 'torso':[-16,0,0], 'head':[-28,0,0],
    'right_shoulder':[0,0,-10], 'left_shoulder':[0,0,10],
    'right_arm':[2,-12,-38], 'left_arm':[-8,10,52], 'katana':[110,0,-8],
    'right_leg':[-8,0,-12], 'left_leg':[-8,0,12],
    'right_boot':[14,-6,0], 'left_boot':[14,6,0], 'kusazuri':[12,0,0],
}, {'root':[0,0,0]})
recoil=deepcopy(roar)
recoil['torso']['rotation']=[-10,0,0]
recoil['head']['rotation']=[-20,0,0]
for grounded in [gather, roar, recoil]:
    ground_pose(grounded)
rage = clip([(0,pose()), (0.65,gather), (0.9,gather), (1.0,roar),
             (1.15,recoil), (1.3,roar), (1.55,recoil), (2.2,pose())])
rage['sound_effects']={'0.45':{'effect':'samurai_raise'}}
animations['animation.samurai_shogun.enrage_roar']=rage
# All attack timings match SamuraiBossEntity. Anticipation decelerates into a held coil, the
# strike accelerates through contact, and armour plates lag the hands (SECONDARY_LAG).
def grounded(rot=None, pos=None):
    p=pose(rot,pos)
    ground_pose(p)
    return p

slash_ready=grounded({'waist':[2,-18,-3],'torso':[-7,-28,-4],'head':[3,25,2],
    'right_arm':[-30,-60,12],'left_arm':[-35,-10,18],'katana':[140,0,-30],
    'right_leg':[18,0,-7],'left_leg':[-18,0,8],'right_boot':[-14,-6,0],'left_boot':[18,6,0],
    'right_shoulder':[-5,0,-8],'left_shoulder':[4,0,12],'kusazuri':[5,10,0]},
    {'root':[0,0,1]})
# Triggered attacks enter through the main controller's 5-tick blend before clip time 0, so
# the clips start already in the chamber pose and every clip time is (server tick - 5) / 20.
# Slash: server impact tick 8 -> clip 0.15; 20 busy ticks -> 0.75 s. Heavy: 20 -> 0.75; 36 -> 1.55.
slash_chamber=grounded({'waist':[-4,-22,-2],'torso':[-10,-30,-6],'head':[4,30,2],
    'right_arm':[-107,-49,-21],'left_arm':[-75,-20,12],'katana':[129,0,0],
    'right_leg':[20,0,-8],'left_leg':[-22,0,9],'right_boot':[-16,-6,0],'left_boot':[20,6,0],
    'right_shoulder':[-12,0,-14],'left_shoulder':[6,0,14],'kusazuri':[6,12,0]},
    {'root':[0,0,1]})
# The last instant of anticipation: coil a little further and sink onto the back leg.
slash_load=deepcopy(slash_chamber)
slash_load['waist']['rotation']=[-6,-27,-3]
slash_load['torso']['rotation']=[-14,-37,-8]
slash_load['katana']['rotation']=[138,0,4]
slash_load['right_leg']['rotation']=[12,0,-8]
slash_load['left_leg']['rotation']=[-30,0,9]
slash_load['left_boot']['rotation']=[30,6,0]
ground_pose(slash_load)
# Contact: the hips have already unwound and the blade crosses the front of the body.
slash_contact=grounded({'waist':[10,24,3],'torso':[14,30,6],'head':[-4,-30,-3],
    'right_arm':[-42,-35,-13],'left_arm':[35,0,30],'katana':[72,2,-22],
    'right_leg':[-28,0,-9],'left_leg':[18,0,8],'right_boot':[24,-6,0],'left_boot':[-14,6,0],
    'right_shoulder':[10,-6,14],'left_shoulder':[-8,6,-16],'kusazuri':[-8,-10,2]},
    {'root':[0,0,-3]})
slash_follow=grounded({'waist':[8,38,4],'torso':[12,46,8],'head':[-4,-40,-4],
    'right_arm':[-76,-50,-44],'left_arm':[45,0,35],'katana':[117,-22,-63],
    'right_leg':[-30,0,-9],'left_leg':[20,0,8],'right_boot':[26,-6,0],'left_boot':[-16,6,0],
    'right_shoulder':[14,-8,18],'left_shoulder':[-10,8,-20],'kusazuri':[-6,-18,3]},
    {'root':[0,0,-3]})
# Zanshin: a held finish with the blade low on the off side before returning to guard.
slash_zanshin=grounded({'waist':[6,26,3],'torso':[8,30,5],'head':[-2,-26,-2],
    'right_arm':[-76,-59,-43],'left_arm':[22,0,26],'katana':[153,-22,-61],
    'right_leg':[-22,0,-8],'left_leg':[12,0,8],'right_boot':[20,-6,0],'left_boot':[-10,6,0],
    'right_shoulder':[6,-4,10],'left_shoulder':[-6,4,-12],'kusazuri':[-4,-12,2]},
    {'root':[0,0,-2]})
slash_settle=deepcopy(slash_zanshin)
slash_settle['torso']['rotation']=[6,27,4]
slash_settle['left_arm']['rotation']=[15,0,20]
slash_settle['katana']['rotation']=[158,-20,-56]
SECONDARY_LAG={'kusazuri':0.06,'right_shoulder':0.035,'left_shoulder':0.035,'head':0.03}
slash=clip([(0,slash_chamber),(.08,slash_load,'out'),(.15,slash_contact,'in'),
            (.22,slash_follow,'snap'),(.32,slash_zanshin),(.45,slash_settle),(.75,pose())],
           lag=SECONDARY_LAG)
slash['sound_effects']={'.08':{'effect':'samurai_slash'}}
slash['particle_effects']={'.12':{'effect':'corruption_slash','locator':'blade_mid'},
                           '.17':{'effect':'corruption_slash','locator':'blade_tip'}}
animations['animation.samurai_shogun.attack_slash']=slash

# Gathering the weight: hands rise to the chest as the knees bend.
heavy_gather=grounded({'waist':[6,-4,0],'torso':[6,-8,-2],'head':[-6,8,0],
    'right_arm':[-100,4,9],'left_arm':[-60,-10,15],'katana':[113,0,0],
    'right_leg':[-14,0,-10],'left_leg':[-8,0,10],'right_boot':[16,-6,0],'left_boot':[10,6,0],
    'right_shoulder':[-4,0,8],'left_shoulder':[-4,0,-8],'kusazuri':[-6,0,0]},
    None)
heavy_load=grounded({'waist':[-10,0,0],'torso':[-18,-6,-2],'head':[-8,6,0],
    'right_arm':[-170,-8,-4],'left_arm':[-95,-10,25],'katana':[174,0,0],
    'right_leg':[16,0,-9],'left_leg':[-20,0,10],'right_boot':[-14,-6,0],'left_boot':[18,6,0],
    'right_shoulder':[-10,0,14],'left_shoulder':[-10,0,-14],'kusazuri':[10,0,0]},
    None)
heavy_pull=deepcopy(heavy_load)
heavy_pull['waist']['rotation']=[-13,0,0]
heavy_pull['torso']['rotation']=[-25,-4,0]
heavy_pull['katana']['rotation']=[184,0,0]
# A slight tremor at the top sells the weight before the drop.
heavy_tense=deepcopy(heavy_pull)
heavy_tense['torso']['rotation']=[-23,-3,1]
heavy_tense['katana']['rotation']=[181,0,0]
heavy_hit=deepcopy(impact)
heavy_hit['root']['position'][2]=-3
heavy_hit['right_shoulder']['rotation']=[14,0,12]
heavy_hit['left_shoulder']['rotation']=[14,0,-12]
heavy_hit['kusazuri']['rotation']=[20,0,0]
heavy_crush=deepcopy(heavy_hit)
heavy_crush['right_leg']['rotation']=[-50,0,-10]
heavy_crush['left_leg']['rotation']=[-26,0,10]
heavy_crush['right_boot']['rotation']=[50,-6,0]
heavy_crush['left_boot']['rotation']=[24,6,0]
heavy_crush['torso']['rotation']=[30,0,0]
heavy_crush['head']['rotation']=[12,0,0]
heavy_rebound=deepcopy(heavy_hit)
heavy_rebound['torso']['rotation']=[20,0,0]
heavy_rebound['head']['rotation']=[-2,0,0]
heavy_rebound['katana']['rotation'][0]+=6
# Shoulder and spine tweaks above moved the blade; keep it resting on the surface.
for p in [heavy_hit,heavy_crush,heavy_rebound]:
    ground_pose(p)
heavy=clip([(0,heavy_gather),(.32,heavy_load,'out'),(.46,heavy_pull),(.56,heavy_tense),
            (.62,heavy_pull),(.75,heavy_hit,'in'),(.79,heavy_crush,'snap'),
            (.86,heavy_hit),(.98,heavy_rebound),(1.18,settle),(1.55,pose())],
           lag=SECONDARY_LAG)
heavy['sound_effects']={'.15':{'effect':'samurai_raise'},'.75':{'effect':'samurai_slam'}}
heavy['particle_effects']={'.75':{'effect':'shockwave','locator':'ground_impact'},'.81':{'effect':'corruption_burst','locator':'ground_impact'}}
animations['animation.samurai_shogun.attack_heavy']=heavy

coil=grounded({'waist':[14,-12,0],'torso':[20,-20,-5],'head':[-14,25,0],
    'right_arm':[-55,-32,10],'left_arm':[-55,-15,14],'katana':[135,0,-10],
    'right_leg':[-36,0,-10],'left_leg':[24,0,10],'right_boot':[34,-6,0],'left_boot':[-18,6,0],
    'kusazuri':[-14,12,0]}, None)
dash_pose=pose({'waist':[14,0,0],'torso':[25,-8,0],'head':[-18,8,0],
    'right_arm':[-76,-20,8],'left_arm':[40,0,20],'katana':[121,0,0],
    'right_leg':[-42,0,-6],'left_leg':[40,0,6],'right_boot':[38,-6,0],'left_boot':[-22,6,0],
    'right_shoulder':[-8,0,15],'left_shoulder':[-8,0,-15],'kusazuri':[-18,0,0]}, None)
windup=clip([(0,pose()),(.3,slash_ready),(.65,coil,'out'),(.85,coil),(1,dash_pose,'in')])
windup['sound_effects']={'.30':{'effect':'samurai_raise'}}
windup['particle_effects']={'.65':{'effect':'corruption_burst','locator':'aura'}}
animations['animation.samurai_shogun.phase_charge_windup']=windup
stride=deepcopy(dash_pose)
stride['right_leg']['rotation']=[40,0,-6];stride['left_leg']['rotation']=[-42,0,6]
stride['right_boot']['rotation']=[-22,-6,0];stride['left_boot']['rotation']=[38,6,0]
stride['torso']['rotation']=[22,-2,0];stride['left_arm']['rotation']=[-15,0,18];stride['kusazuri']['rotation']=[-12,3,0]
mid=deepcopy(dash_pose)
mid['right_leg']['rotation']=[-6,0,-6];mid['left_leg']['rotation']=[-6,0,6]
mid['right_boot']['rotation']=[22,-6,0];mid['left_boot']['rotation']=[22,6,0]
mid['root']['position']=[0,.8,0]
run=clip([(0,dash_pose),(.125,mid),(.25,stride),(.375,mid),(.5,dash_pose)])
run['loop']=True
run['sound_effects']={'0':{'effect':'samurai_step'},'.25':{'effect':'samurai_step'}}
run['particle_effects']={'0':{'effect':'corruption_slash','locator':'blade_tip'},'.25':{'effect':'corruption_slash','locator':'blade_mid'}}
animations['animation.samurai_shogun.phase_charge_run']=run

# A defeated warrior: stagger, lose the weapon, drop to one knee, then bow and sag.
reel=pose({'waist':[-8,0,4],'torso':[-14,-8,8],'head':[-22,5,-6],
    'right_arm':[-28,-15,16],'left_arm':[-20,12,30],'katana':[140,0,-16],
    'right_leg':[12,0,-6],'left_leg':[-14,0,8],'right_boot':[8,-6,0],'left_boot':[16,6,0]},
    None)
sink=pose({'waist':[8,0,6],'torso':[14,-6,8],'head':[8,10,4],
    'right_arm':[-8,-6,12],'left_arm':[-35,5,12],'katana':[96,0,-30],
    'right_leg':[-40,0,-8],'left_leg':[-18,0,10],'right_boot':[100,-6,0],'left_boot':[32,6,0],
    'right_shoulder':[10,0,5],'left_shoulder':[-6,0,-12],'kusazuri':[-20,0,0]},
    {'katana':[-2,-9,-12]})
kneel=pose({'waist':[14,0,4],'torso':[24,-8,6],'head':[26,5,4],
    'right_arm':[12,-4,8],'left_arm':[-30,8,10],'katana':[70,0,-46],
    'right_leg':[-55,0,-10],'left_leg':[-40,0,10],'right_boot':[140,-6,0],'left_boot':[75,6,0],
    'right_shoulder':[12,0,12],'left_shoulder':[8,0,-8],'kusazuri':[-30,0,0]},
    {'katana':[-3,-15,-19]})
bow=deepcopy(kneel)
bow['waist']['rotation']=[24,0,4];bow['torso']['rotation']=[40,-12,5];bow['head']['rotation']=[38,8,4]
bow['right_arm']['rotation']=[18,-4,12];bow['left_arm']['rotation']=[12,8,14]
end=deepcopy(bow)
end['torso']['rotation']=[46,-12,7];end['head']['rotation']=[42,8,5]
end['right_shoulder']['rotation']=[18,0,16];end['left_shoulder']['rotation']=[15,0,-12]
for p in [reel,sink,kneel,bow,end]:
    # Ground the actual limbs, allowing the knees to carry the body as it sinks.
    p.setdefault('root',{})['position']=[0,0,0]
    p['root']['position'][1]=-min_height(p,['right_leg','left_leg','right_boot','left_boot','right_foot','left_foot'])
death=clip([(0,pose()),(.25,reel),(.45,reel),(.85,sink),(1.2,kneel),(1.4,kneel),(2.1,bow),(2.65,end),(3.2,end)])
death['loop']='hold_on_last_frame'
death['bones']['katana']['scale']={'0':[1,1,1],'.95':[1,1,1],'1.05':[0,0,0],'3.2':[0,0,0]}
death['sound_effects']={'.35':{'effect':'katana_drop'},'1.20':{'effect':'armor_crash'},'2.40':{'effect':'armor_break'}}
death['particle_effects']={'1.0':{'effect':'katana_plant','locator':'ground_impact'},'1.04':{'effect':'corruption_burst','locator':'ground_impact'},
    '2.1':{'effect':'armor_break','locator':'aura'},'2.6':{'effect':'armor_break','locator':'aura'},'3.0':{'effect':'corruption_burst','locator':'aura'}}
animations['animation.samurai_shogun.death']=death

# Shoulder plates lag behind the airborne overhead swing; a visible recoil after landing.
for when,angles in {'0.55':[8,0,14],'0.95':[-16,0,20],'1.35':[-10,0,16],
                    '1.9':[-18,0,22],'2.0':[18,0,14],'2.15':[26,0,10],'2.45':[8,0,6],'3.0':[0,0,0]}.items():
    sky['bones']['right_shoulder']['rotation'][when]=angles
    sky['bones']['left_shoulder']['rotation'][when]=[angles[0],0,-angles[2]]
# Sort after adding secondary keyframes for deterministic animation import.
for name in ['right_shoulder','left_shoulder']:
    for channel,keys in sky['bones'][name].items():
        sky['bones'][name][channel]=dict(sorted(keys.items(),key=lambda kv:float(kv[0])))

PATH.write_text(json.dumps(doc, indent=2)+'\n')
print('Wrote slash, heavy, charge, skyfall, rage and death animations')
