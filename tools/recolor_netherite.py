"""
Recolour YOUR OWN copy of the vanilla netherite sword texture into the 7 ElementalBlades colours.

The real texture can't be shipped inside the plugin (it belongs to Mojang), but you own the game, so this
script reads it from your Minecraft client jar and writes the 7 recoloured swords for you.

Usage (needs Python 3 + Pillow:  pip install pillow):
    python recolor_netherite.py                         # auto-finds .minecraft/versions/26.x/26.x.jar
    python recolor_netherite.py path/to/26.3.jar        # or point at the jar
    python recolor_netherite.py netherite_sword.png     # or at the png itself
Options:
    --out <folder>   where to write the 7 pngs (default: ./elementalblades-textures)
    --keep-handle    keep the original brown handle (default: handle gets a darker element tint)

Then copy the 7 pngs into  plugins/ElementalBlades/resourcepack/assets/elementalblades/textures/item/
(overwrite) and run /blades reload in game.
"""
import colorsys, glob, io, os, re, sys, zipfile
from PIL import Image

TEXTURE = "assets/minecraft/textures/item/netherite_sword.png"

# dark, mid, light stops per element (blade), plus handle tint
PALETTES = {
    "flame":     [(70, 12, 4),   (230, 70, 20),  (255, 235, 140)],
    "frost":     [(12, 40, 90),  (90, 190, 245), (240, 255, 255)],
    "life":      [(8, 50, 20),   (60, 205, 80),  (210, 255, 195)],
    "silence":   [(20, 6, 40),   (125, 70, 215), (225, 200, 255)],
    "lightning": [(80, 50, 4),   (245, 195, 30), (255, 255, 220)],
    "velocity":  [(40, 46, 80),  (185, 195, 230), (255, 255, 255)],
    "water":     [(6, 24, 80),   (40, 125, 245), (185, 235, 255)],
}

def find_jar():
    homes = [os.environ.get("APPDATA", ""), os.path.expanduser("~"),
             os.path.expanduser("~/Library/Application Support")]
    cands = []
    for h in homes:
        for d in (".minecraft", "minecraft"):
            cands += glob.glob(os.path.join(h, d, "versions", "26.*", "26.*.jar"))
    def key(p):
        return [int(x) for x in re.findall(r"\d+", os.path.basename(p))]
    cands = sorted(set(cands), key=key)
    return cands[-1] if cands else None

def load_source(arg):
    if arg and arg.lower().endswith(".png"):
        return Image.open(arg).convert("RGBA")
    jar = arg or find_jar()
    if not jar or not os.path.exists(jar):
        sys.exit("Couldn't find a Minecraft 26.x client jar. Launch 26.3 once in the official launcher, "
                 "or pass the jar/png path as an argument.")
    print("Reading", jar)
    with zipfile.ZipFile(jar) as z:
        return Image.open(io.BytesIO(z.read(TEXTURE))).convert("RGBA")

def gradient(stops, t):
    t = max(0.0, min(1.0, t))
    if t < 0.5:
        a, b, k = stops[0], stops[1], t / 0.5
    else:
        a, b, k = stops[1], stops[2], (t - 0.5) / 0.5
    return tuple(int(round(a[i] + (b[i] - a[i]) * k)) for i in range(3))

def recolor(src, stops, keep_handle):
    w, h = src.size
    px = src.load()
    # Classify: low-saturation pixels = blade/guard (netherite grey), saturated warm = wooden handle
    blade_l, info = [], {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            handle = s > 0.30 and 0.02 < hh < 0.17   # brown/orange hues
            info[(x, y)] = (l, handle, a)
            if not handle:
                blade_l.append(l)
    lo, hi = (min(blade_l), max(blade_l)) if blade_l else (0, 1)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    op = out.load()
    for (x, y), (l, handle, a) in info.items():
        if handle:
            if keep_handle:
                op[x, y] = px[x, y]
            else:  # darker element tint, keeps the wood shading
                t = 0.15 + 0.45 * l
                op[x, y] = gradient(stops, t) + (a,)
        else:
            t = (l - lo) / (hi - lo) if hi > lo else 0.5
            t = t ** 0.85  # lift mid-tones a bit so the colour pops
            op[x, y] = gradient(stops, t) + (a,)
    return out

def main():
    args = [a for a in sys.argv[1:]]
    out_dir = "elementalblades-textures"
    keep = "--keep-handle" in args
    if "--keep-handle" in args:
        args.remove("--keep-handle")
    if "--out" in args:
        i = args.index("--out"); out_dir = args[i + 1]; del args[i:i + 2]
    src = load_source(args[0] if args else None)
    os.makedirs(out_dir, exist_ok=True)
    for name, stops in PALETTES.items():
        path = os.path.join(out_dir, f"{name}_blade.png")
        recolor(src, stops, keep).save(path)
        print("wrote", path)
    print("\nCopy these into plugins/ElementalBlades/resourcepack/assets/elementalblades/textures/item/ and run /blades reload.")

if __name__ == "__main__":
    main()
