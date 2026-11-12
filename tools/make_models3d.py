"""
Original ornate 3D sword item models (one design per element) + palette textures + preview renders.
Designed upright (y = up, 0..32), then laid on the 45-degree diagonal so vanilla sword hand-poses fit.
Glowing parts use element light_emission (fullbright).
Usage: python make_models3d.py <resourcepack_dir> [preview.png]
"""
import json, math, os, sys
from PIL import Image, ImageDraw, ImageFilter

# palette slots: 0 light, 1 mid, 2 dark, 3 metal, 4 metal-dark, 5 grip, 6 glow, 7 accent
PAL = {
 "flame":     [(255,236,150),(255,110,30),(170,35,10),(255,190,60),(150,80,20),(80,30,20),(255,230,90),(255,70,20)],
 "frost":     [(235,255,255),(120,215,250),(40,125,200),(220,240,255),(120,165,210),(30,55,100),(170,250,255),(255,255,255)],
 "life":      [(200,255,185),(70,210,85),(20,120,45),(255,205,85),(175,125,35),(95,60,35),(255,60,95),(40,170,60)],
 "silence":   [(205,175,255),(110,55,200),(45,15,95),(150,120,200),(70,50,110),(35,18,60),(215,150,255),(90,40,170)],
 "lightning": [(255,255,225),(255,210,40),(200,130,10),(80,175,255),(35,95,190),(55,55,70),(255,255,255),(140,215,255)],
 "velocity":  [(255,255,255),(210,220,245),(125,140,190),(110,220,255),(50,135,210),(170,175,200),(130,245,255),(230,250,255)],
 "water":     [(180,235,255),(45,140,250),(15,60,170),(50,210,195),(15,120,120),(25,50,105),(120,255,230),(200,250,255)],
}

def rot(px, py, cx, cy, deg):
    a = math.radians(deg); dx, dy = px - cx, py - cy
    return cx + dx*math.cos(a) - dy*math.sin(a), cy + dx*math.sin(a) + dy*math.cos(a)

class Part:
    def __init__(s, cx, cy, w, h, z0, z1, slot, angle=0.0, glow=False):
        s.cx, s.cy, s.w, s.h, s.z0, s.z1, s.slot, s.angle, s.glow = cx, cy, w, h, z0, z1, slot, angle, glow

def box(x0, y0, x1, y1, slot, z=(7.5, 8.5), glow=False):
    return Part((x0+x1)/2, (y0+y1)/2, x1-x0, y1-y0, z[0], z[1], slot, 0.0, glow)
def bar(cx, cy, length, thick, angle, slot, z=(7.5, 8.5), glow=False):
    return Part(cx, cy, length, thick, z[0], z[1], slot, angle, glow)          # length along local x
def vbar(cx, cy, length, thick, angle, slot, z=(7.5, 8.5), glow=False):
    return Part(cx, cy, thick, length, z[0], z[1], slot, angle, glow)          # length along local y
def dia(cx, cy, size, slot, z=(7.3, 8.7), glow=False):
    return Part(cx, cy, size, size, z[0], z[1], slot, 45.0, glow)
def cube(cx, cy, size, slot, glow=True):
    return Part(cx, cy, size, size, 8 - size/2, 8 + size/2, slot, 45.0, glow)
def ring(cx, cy, half, t, slot, z=(7.3, 8.7)):
    out = []
    for (bx, by), w, h in [((cx, cy+half-t/2), 2*half, t), ((cx, cy-half+t/2), 2*half, t),
                           ((cx-half+t/2, cy), t, 2*half-2*t), ((cx+half-t/2, cy), t, 2*half-2*t)]:
        rx, ry = rot(bx, by, cx, cy, 45.0)
        out.append(Part(rx, ry, w, h, z[0], z[1], slot, 45.0))
    return out
def hring(cy, half, t, slot, glow=False):   # horizontal ring around the blade (XZ plane)
    c = 8.0
    return [Part(c, cy, 2*half, t, c-half, c-half+t, slot, 0, glow), Part(c, cy, 2*half, t, c+half-t, c+half, slot, 0, glow),
            Part(c-half+t/2, cy, t, t, c-half+t, c+half-t, slot, 0, glow), Part(c+half-t/2, cy, t, t, c-half+t, c+half-t, slot, 0, glow)]

def grip_and_pommel(gem_glow=True, wraps=(3.4, 5.4)):
    p = [box(7.25, 2.2, 8.75, 8.4, 5, z=(7.25, 8.75))]
    for y in wraps: p.append(box(7.0, y, 9.0, y + 0.6, 4, z=(7.0, 9.0)))
    p.append(dia(8, 1.3, 2.3, 3, z=(7.0, 9.0)))
    p.append(dia(8, 1.3, 1.1, 6, z=(6.8, 9.2), glow=gem_glow))
    return p

# ------------------------------------------------------------------ designs
def flame():
    p = grip_and_pommel()
    p.append(box(7.3, 9.6, 8.7, 28.0, 1))                                   # blade
    p.append(box(7.8, 10.5, 8.2, 27.0, 6, z=(7.42, 8.58), glow=True))       # molten core
    p.append(box(7.0, 11.0, 7.3, 26.5, 0, z=(7.6, 8.4)))
    p.append(box(8.7, 11.0, 9.0, 26.5, 2, z=(7.6, 8.4)))
    p.append(dia(8, 28.2, 1.9, 0)); p.append(dia(8, 29.7, 1.0, 6, glow=True))
    for i, y in enumerate((12.5, 16.0, 19.5, 23.0, 26.0)):                  # flame licks
        left = i % 2 == 0
        x = 6.3 if left else 9.7
        p.append(vbar(x, y, 2.6 - 0.25*i, 0.7, 22.5 if left else -22.5, 7 if i % 2 else 1))
        tx, ty = rot(x, y + 1.4, x, y, 22.5 if left else -22.5)
        p.append(cube(tx, ty, 0.55, 6))
    p.append(bar(5.0, 10.3, 5.0, 1.0, -22.5, 3, z=(7.2, 8.8)))              # fire wings
    p.append(bar(11.0, 10.3, 5.0, 1.0, 22.5, 3, z=(7.2, 8.8)))
    p.append(bar(4.6, 11.4, 2.6, 0.6, -45, 7)); p.append(bar(11.4, 11.4, 2.6, 0.6, 45, 7))
    p.append(cube(2.7, 11.6, 0.8, 6)); p.append(cube(13.3, 11.6, 0.8, 6))
    p += ring(8, 9.6, 1.5, 0.45, 3); p.append(dia(8, 9.6, 1.3, 7, z=(6.9, 9.1), glow=True))
    for (x, y, s) in ((5.2, 24.5, 0.55), (10.9, 21.0, 0.5), (6.0, 30.0, 0.45), (10.3, 28.2, 0.55), (4.6, 17.5, 0.4)):
        p.append(cube(x, y, s, 6))                                          # embers
    return p

def frost():
    p = grip_and_pommel()
    p.append(box(7.5, 9.6, 8.5, 27.5, 0))
    for i, y in enumerate((12.0, 15.4, 18.8, 22.2, 25.4)):                  # stacked crystals
        p.append(dia(8, y, 2.3 - 0.12*i, 1 if i % 2 else 0, z=(7.25, 8.75)))
        p.append(dia(8, y, 0.8, 6, z=(7.1, 8.9), glow=True))
    p.append(vbar(8, 28.6, 2.6, 1.0, 0, 7)); p.append(dia(8, 30.0, 1.0, 7, glow=True))
    for y, left in ((13.7, True), (17.1, False), (20.5, True), (23.8, False)):   # ice shards
        p.append(vbar(6.2 if left else 9.8, y + 0.3, 1.9, 0.5, 45 if left else -45, 0))
    p.append(bar(5.2, 9.6, 5.2, 1.0, 22.5, 3, z=(7.2, 8.8)))                # drooping guard
    p.append(bar(10.8, 9.6, 5.2, 1.0, -22.5, 3, z=(7.2, 8.8)))
    for x, l in ((3.4, 1.8), (5.0, 1.3), (11.0, 1.3), (12.6, 1.8)):          # icicles
        yb = 9.6 - (abs(x - 8) - 2.8) * 0.414 - 0.35          # underside of the drooping guard
        p.append(box(x - 0.25, yb - l, x + 0.25, yb + 0.1, 7, z=(7.6, 8.4)))
    p += ring(8, 9.8, 1.6, 0.45, 3); p.append(dia(8, 9.8, 1.4, 6, z=(6.9, 9.1), glow=True))
    for (x, y) in ((5.4, 26.0), (10.6, 19.5), (5.6, 15.5)):
        p.append(cube(x, y, 0.45, 7))
    return p

def life():
    p = grip_and_pommel(wraps=(3.2, 4.6, 6.0))
    p.append(box(7.3, 9.6, 8.7, 27.2, 1))
    p.append(box(7.0, 10.8, 7.3, 26.4, 0, z=(7.6, 8.4)))
    p.append(box(7.85, 11.0, 8.15, 25.5, 2, z=(7.42, 8.58)))
    p.append(dia(8, 27.4, 1.9, 0)); p.append(dia(8, 28.8, 0.9, 0))
    for i, y in enumerate((11.8, 14.2, 16.6, 19.0, 21.4, 23.8)):             # climbing vine + leaves
        left = i % 2 == 0
        p.append(vbar(7.2 if left else 8.8, y, 1.6, 0.35, -22.5 if left else 22.5, 7, z=(7.2, 8.8)))
        p.append(dia(6.2 if left else 9.8, y + 0.6, 1.0, 2 if i % 2 else 7, z=(7.4, 8.6)))
    p.append(bar(5.0, 10.4, 4.6, 0.9, -22.5, 3, z=(7.2, 8.8)))               # golden leaf guard
    p.append(bar(11.0, 10.4, 4.6, 0.9, 22.5, 3, z=(7.2, 8.8)))
    p.append(dia(2.9, 11.3, 1.5, 7)); p.append(dia(13.1, 11.3, 1.5, 7))
    p.append(cube(7.45, 10.3, 0.95, 6)); p.append(cube(8.55, 10.3, 0.95, 6))  # heart
    p.append(dia(8, 9.5, 1.25, 6, z=(7.0, 9.0), glow=True))
    return p

def silence():
    p = grip_and_pommel()
    p.append(box(7.5, 9.0, 8.5, 27.2, 2))                                    # thin void shaft
    p.append(box(7.85, 12.8, 8.15, 26.6, 6, z=(7.42, 8.58), glow=True))
    p += ring(8, 29.0, 1.7, 0.45, 3); p.append(dia(8, 29.0, 1.1, 6, glow=True)); p.append(dia(8, 31.3, 0.8, 1))
    p += ring(8, 10.6, 2.8, 0.55, 3); p += ring(8, 10.6, 1.7, 0.4, 1)         # double diamond frame
    p.append(dia(8, 10.6, 1.2, 6, z=(6.9, 9.1), glow=True))
    for sgn in (-1, 1):                                                        # crescent wings
        p.append(bar(8 + sgn*4.3, 11.4, 2.4, 0.5, sgn*22.5, 4))
        p.append(bar(8 + sgn*5.6, 12.6, 1.8, 0.5, sgn*67.5, 4))
        p.append(cube(8 + sgn*5.9, 13.7, 0.5, 6))
    p.append(dia(8, 17.6, 1.1, 1)); p.append(dia(8, 21.6, 0.9, 1))
    for (x, y, s) in ((5.6, 19.5, 0.6), (10.5, 23.5, 0.5), (6.0, 25.8, 0.4), (10.2, 16.0, 0.45)):
        p.append(cube(x, y, s, 6))                                           # void shards
    return p

def lightning():
    p = grip_and_pommel()
    segs = [(7.6, 12.0, 22.5), (8.4, 15.6, -22.5), (7.6, 19.2, 22.5), (8.4, 22.8, -22.5), (7.7, 26.2, 22.5)]
    for x, y, a in segs:                                                     # zig-zag bolt blade
        p.append(vbar(x, y, 4.2, 1.6, a, 1))
        p.append(vbar(x, y, 3.6, 0.45, a, 6, z=(7.4, 8.6), glow=True))
    p.append(dia(8.1, 28.4, 1.6, 0)); p.append(dia(8.2, 29.8, 0.9, 6, glow=True))
    p.append(box(5.0, 9.2, 11.0, 10.2, 3, z=(7.2, 8.8)))                     # storm guard with horns
    p.append(bar(4.3, 10.7, 2.6, 0.8, 45, 4)); p.append(bar(11.7, 10.7, 2.6, 0.8, -45, 4))
    p.append(cube(3.6, 11.6, 0.6, 7)); p.append(cube(12.4, 11.6, 0.6, 7))
    p += ring(8, 9.7, 1.4, 0.4, 4); p.append(dia(8, 9.7, 1.2, 7, z=(6.9, 9.1), glow=True))
    for (x, y) in ((5.4, 18.0), (10.6, 21.0), (5.8, 25.5), (10.4, 14.0), (10.1, 28.0)):
        p.append(cube(x, y, 0.45, 6))                                        # sparks
    return p

def velocity():
    p = grip_and_pommel()
    p.append(box(7.6, 9.6, 8.4, 28.8, 0))                                    # rapier blade
    p.append(box(8.15, 10.5, 8.4, 28.0, 2, z=(7.45, 8.55)))
    p.append(dia(8, 29.1, 1.1, 0)); p.append(dia(8, 30.2, 0.6, 7))
    p += hring(16.5, 1.5, 0.3, 6, glow=True); p += hring(22.5, 1.2, 0.3, 6, glow=True)   # wind rings
    for sgn in (-1, 1):                                                        # feather wings
        for k, (ln, ang) in enumerate(((4.4, 0), (3.8, 22.5), (3.0, 45))):
            a = ang * sgn * -1 if sgn < 0 else ang
            cx, cy = 8 + sgn*(1.0 + ln/2), 10.0
            cx, cy = rot(cx, cy, 8 + sgn*1.0, 10.0, -a if sgn < 0 else a)
            p.append(bar(cx, cy, ln, 0.75, -a if sgn < 0 else a, 3 if k != 1 else 7))
    p.append(dia(8, 9.9, 1.3, 6, z=(6.9, 9.1), glow=True))
    for (x, y) in ((5.6, 20.0), (10.4, 25.5), (10.6, 13.5)):
        p.append(cube(x, y, 0.4, 6))
    return p

def water():
    p = grip_and_pommel()
    p.append(box(7.2, 9.8, 8.8, 25.5, 1))                                    # central prong / blade
    p.append(box(6.9, 10.6, 7.2, 24.5, 0, z=(7.6, 8.4)))
    p.append(box(7.85, 11.0, 8.15, 24.0, 6, z=(7.42, 8.58), glow=True))
    p.append(dia(8, 26.0, 2.2, 0)); p.append(dia(8, 27.6, 1.0, 0))
    p.append(box(3.9, 8.9, 12.1, 10.1, 3, z=(7.2, 8.8)))                     # trident crossbar
    p.append(bar(3.6, 9.7, 1.6, 0.8, 45, 4)); p.append(bar(12.4, 9.7, 1.6, 0.8, -45, 4))
    for sgn in (-1, 1):                                                        # side prongs
        x = 8 + sgn*3.7
        p.append(box(x - 0.45, 10.0, x + 0.45, 17.0, 3))
        p.append(dia(x, 17.6, 1.4, 0))
        p.append(bar(x - sgn*0.75, 16.2, 1.2, 0.4, -sgn*45, 0))              # barbs
    p += ring(8, 9.5, 1.3, 0.4, 4); p.append(dia(8, 9.5, 1.15, 6, z=(6.9, 9.1), glow=True))
    for (x, y, s) in ((5.3, 21.0, 0.6), (10.8, 23.5, 0.5), (6.0, 27.0, 0.4), (10.2, 19.0, 0.4)):
        p.append(cube(x, y, s, 6))                                           # bubbles
    return p

DESIGNS = {"flame": flame, "frost": frost, "life": life, "silence": silence,
           "lightning": lightning, "velocity": velocity, "water": water}

# ------------------------------------------------------------------ export
CV = (8.0, 13.0)
DIAG = -45.0
LEGAL = (-45.0, -22.5, 0.0, 22.5, 45.0)

def to_diag(part):
    nx, ny = rot(part.cx, part.cy, CV[0], CV[1], DIAG)
    nx, ny = nx - CV[0] + 8, ny - CV[1] + 8
    a, w, h = part.angle, part.w, part.h
    net = a + DIAG
    # normalise into the legal range by turning the box 90 degrees (swap w/h)
    for _ in range(4):
        n = ((net + 180) % 360) - 180
        if any(abs(n - l) < 1e-6 for l in LEGAL): net = n; break
        net += 90; w, h = h, w
    else:
        raise ValueError(f"angle {a} can't be expressed")
    return Part(nx, ny, w, h, part.z0, part.z1, part.slot, net, part.glow)

def uv(slot):
    u0, v0 = (slot % 4) * 4, (slot // 4) * 4
    return [u0 + 0.75, v0 + 0.75, u0 + 3.25, v0 + 3.25]

def element(part):
    r = lambda v: round(v, 4)
    e = {"from": [r(part.cx - part.w/2), r(part.cy - part.h/2), r(part.z0)],
         "to":   [r(part.cx + part.w/2), r(part.cy + part.h/2), r(part.z1)],
         "faces": {f: {"uv": uv(part.slot), "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")}}
    if abs(part.angle) > 1e-6:
        e["rotation"] = {"angle": part.angle, "axis": "z", "origin": [r(part.cx), r(part.cy), 8]}
    if part.glow: e["light_emission"] = 15
    return e

def bbox(parts):
    xs, ys = [], []
    for p in parts:
        for dx in (-p.w/2, p.w/2):
            for dy in (-p.h/2, p.h/2):
                x, y = rot(p.cx + dx, p.cy + dy, p.cx, p.cy, p.angle); xs.append(x); ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)

def display(parts):
    x0, y0, x1, y1 = bbox(parts)
    span = max(x1 - x0, y1 - y0)
    g = round(min(1.0, 15.0 / span), 3)
    cx, cy = (x0 + x1) / 2 - 8, (y0 + y1) / 2 - 8
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.68, 0.68, 0.68]},
        "thirdperson_lefthand":  {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.68, 0.68, 0.68]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.55, 0.55, 0.55]},
        "firstperson_lefthand":  {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.55, 0.55, 0.55]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "gui":    {"translation": [round(-cx*g, 2), round(-cy*g, 2), 0], "scale": [g, g, g]},
        "fixed":  {"rotation": [0, 180, 0], "translation": [round(cx*g, 2), round(-cy*g, 2), 0], "scale": [g, g, g]},
        "head":   {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }

def palette_png(colors):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i, c in enumerate(colors):
        u0, v0 = (i % 4) * 4, (i // 4) * 4
        for x in range(4):
            for y in range(4):
                f = 1.07 if (x + y) < 3 else (0.93 if (x + y) > 4 else 1.0)
                im.putpixel((u0 + x, v0 + y), tuple(max(0, min(255, int(v * f))) for v in c) + (255,))
    return im

# ------------------------------------------------------------------ preview renderer (upright showcase)
def render(parts, colors, w=260, h=560, yaw=24.0, pitch=-8.0, bg=(12, 12, 18)):
    ya, pa = math.radians(yaw), math.radians(pitch)
    def view(x, y, z):
        x, y, z = x - 8, y - 16, z - 8
        x, z = x*math.cos(ya) + z*math.sin(ya), -x*math.sin(ya) + z*math.cos(ya)
        y, z = y*math.cos(pa) - z*math.sin(pa), y*math.sin(pa) + z*math.cos(pa)
        return x, y, z
    sc = h / 35.0
    L = (-0.45, 0.6, 0.66)
    polys = []
    for p in parts:
        hw, hh = p.w/2, p.h/2
        cs = []
        for z in (p.z0, p.z1):
            for dx, dy in ((-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)):
                x, y = rot(p.cx + dx, p.cy + dy, p.cx, p.cy, p.angle); cs.append(view(x, y, z))
        for q in ((0,1,2,3),(4,5,6,7),(0,1,5,4),(2,3,7,6),(1,2,6,5),(0,3,7,4)):
            v = [cs[i] for i in q]
            a = [v[1][i]-v[0][i] for i in range(3)]; b = [v[3][i]-v[0][i] for i in range(3)]
            n = (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
            ln = math.sqrt(sum(c*c for c in n)) or 1; n = [c/ln for c in n]
            if n[2] < 0: n = [-c for c in n]
            lit = 0.5 + 0.5*max(0, sum(n[i]*L[i] for i in range(3)))
            polys.append((sum(c[2] for c in v)/4, v, p.slot, p.glow, lit))
    polys.sort(key=lambda t: t[0])
    sword = Image.new("RGBA", (w, h), (0, 0, 0, 0)); glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ds, dg = ImageDraw.Draw(sword), ImageDraw.Draw(glow)
    for _, v, slot, gl, lit in polys:
        pts = [(w/2 + c[0]*sc, h/2 - c[1]*sc) for c in v]
        col = colors[slot] if gl else tuple(int(ch*lit) for ch in colors[slot])
        ds.polygon(pts, fill=col + (255,), outline=tuple(int(ch*0.6) for ch in col) + (255,))
        if gl: dg.polygon(pts, fill=colors[slot] + (255,))
    out = Image.new("RGBA", (w, h), bg + (255,))
    halo = glow.filter(ImageFilter.GaussianBlur(9))
    out.alpha_composite(halo); out.alpha_composite(halo)
    out.alpha_composite(sword)
    out.alpha_composite(glow.filter(ImageFilter.GaussianBlur(3)))
    return out

def main(pack_dir, preview=None):
    tex = os.path.join(pack_dir, "assets/elementalblades/textures/item")
    mdl = os.path.join(pack_dir, "assets/elementalblades/models/item")
    os.makedirs(tex, exist_ok=True); os.makedirs(mdl, exist_ok=True)
    shots = []
    for name, fn in DESIGNS.items():
        upright = fn()
        diag = [to_diag(p) for p in upright]
        m = {"credit": "ElementalBlades (original model)", "texture_size": [16, 16], "gui_light": "front",
             "textures": {"0": f"elementalblades:item/{name}_blade_3d", "particle": f"elementalblades:item/{name}_blade_3d"},
             "elements": [element(p) for p in diag], "display": display(diag)}
        for e in m["elements"]:
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (name, v)
        json.dump(m, open(os.path.join(mdl, f"{name}_blade_3d.json"), "w"), indent=1)
        palette_png(PAL[name]).save(os.path.join(tex, f"{name}_blade_3d.png"))
        shots.append(render(upright, PAL[name]))
        print(f"{name}: {len(diag)} elements")
    if preview:
        W, H = shots[0].size
        sheet = Image.new("RGBA", (W*len(shots), H), (12, 12, 18, 255))
        for i, s in enumerate(shots): sheet.paste(s, (i*W, 0))
        sheet.save(preview)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
