"""Original chunky 16x16 sword textures (2D style). Outline is a dark shade of each blade's own colour."""
import sys, os
from PIL import Image

def lerp(a, b, t): return tuple(int(round(a[i] + (b[i]-a[i])*t)) for i in range(3))
def shade(c, f): return tuple(max(0, min(255, int(v*f))) for v in c)

P = {
 "flame":     dict(light=(255,245,170), mid=(255,130,40),  dark=(200,50,20),  guard=(255,195,70), gdark=(185,100,25), grip=(120,55,40),  gem=(255,70,30)),
 "frost":     dict(light=(235,255,255), mid=(110,215,250), dark=(35,135,200), guard=(200,235,255),gdark=(90,150,200), grip=(40,75,115),  gem=(160,250,255)),
 "life":      dict(light=(220,255,205), mid=(85,225,95),   dark=(25,150,60),  guard=(255,210,90), gdark=(190,135,40), grip=(105,70,40),  gem=(255,60,90)),
 "silence":   dict(light=(230,205,255), mid=(150,90,235),  dark=(80,35,155),  guard=(105,95,130), gdark=(55,45,75),  grip=(70,40,105),  gem=(215,170,255)),
 "lightning": dict(light=(255,255,220), mid=(255,215,45),  dark=(210,140,15), guard=(80,175,255), gdark=(35,100,190), grip=(70,70,85),   gem=(255,255,255)),
 "velocity":  dict(light=(255,255,255), mid=(205,215,245), dark=(120,135,190),guard=(95,215,255), gdark=(45,130,205), grip=(150,155,180),gem=(95,235,255)),
 "water":     dict(light=(185,240,255), mid=(55,145,255),  dark=(20,75,195),  guard=(45,205,195), gdark=(20,125,125), grip=(35,65,120),  gem=(130,255,235)),
}

def xy(s, u):
    if (s + u) % 2: return None
    x, y = (s + u)//2, (s - u)//2
    return (x, y) if 0 <= x < 16 and 0 <= y < 16 else None

def draw(pal):
    px = {}
    def put(s, u, c, force=True):
        p = xy(s, u)
        if p and (force or p not in px): px[p] = c
    out_b = shade(pal["dark"], 0.5)        # same-hue outline
    out_g = shade(pal["gdark"], 0.55)
    out_h = shade(pal["grip"], 0.5)
    U_TIP, U_BASE = 15, -2
    # blade: interior s 14..17 (4 wide) + same-hue outline
    for u in range(U_BASE, U_TIP + 1):
        t = (U_TIP - u) / (U_TIP - U_BASE)
        lo, hi = 14, 17
        if u >= U_TIP - 1: lo, hi = 15, 16
        elif u >= U_TIP - 3: lo, hi = 14, 16
        put(lo - 1, u, out_b); put(hi + 1, u, out_b)
        for s in range(lo, hi + 1):
            k = (s - lo) / max(1, hi - lo)              # 0 = lit edge, 1 = shadow edge
            top = lerp(pal["light"], pal["mid"], min(1, t * 1.6))
            col = lerp(top, pal["dark"], k * 0.85)
            if s == lo and t < 0.55: col = lerp(pal["light"], col, t)   # bright leading edge near the tip
            put(s, u, col)
    put(15, U_TIP + 1, out_b); put(16, U_TIP + 1, out_b); put(16, U_TIP + 2, out_b)
    # crossguard: 2 rows thick
    for s in range(10, 22): put(s, -3, out_g, False); put(s, -6, out_g)
    for s in range(10, 22): put(s, -4, pal["guard"]); put(s, -5, pal["gdark"])
    put(15, -4, pal["gem"]); put(16, -5, shade(pal["gem"], 0.75))
    # grip: 2 wide
    for u in range(-11, -6):
        put(14, u, out_h); put(17, u, out_h)
        put(15, u, pal["grip"] if u % 3 else shade(pal["grip"], 0.7))
        put(16, u, shade(pal["grip"], 0.7) if u % 3 else pal["grip"])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), c in px.items(): img.putpixel((x, y), c + (255,))
    # square knobs on guard ends and a square pommel (pixel space)
    def block(x0, y0, fill, edge):
        for dx in range(3):
            for dy in range(3):
                x, y = x0 + dx, y0 + dy
                if 0 <= x < 16 and 0 <= y < 16:
                    img.putpixel((x, y), (edge if dx in (0, 2) or dy in (0, 2) else fill) + (255,))
    # guard end positions
    a, b = xy(10, -4) or xy(11, -5), xy(21, -5) or xy(20, -4)
    if a: block(a[0] - 1, a[1] - 1, pal["guard"], out_g)
    if b: block(b[0] - 1, b[1] - 1, pal["guard"], out_g)
    pm = xy(15, -13) or xy(16, -12)
    if pm: block(pm[0] - 1, pm[1] - 1, pal["guard"], out_g)
    img.putpixel((pm[0], pm[1]), pal["gem"] + (255,))
    return img

def main(out_dir, preview=None):
    os.makedirs(out_dir, exist_ok=True)
    tiles = []
    for name, pal in P.items():
        im = draw(pal); im.save(os.path.join(out_dir, f"{name}_blade.png")); tiles.append(im)
    if preview:
        S = 16
        strip = Image.new("RGBA", (len(tiles)*(16*S+16)+16, 16*S + 32), (16, 16, 20, 255))
        for i, im in enumerate(tiles):
            big = im.resize((16*S, 16*S), Image.NEAREST); strip.paste(big, (16 + i*(16*S+16), 16), big)
        strip.save(preview)

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
