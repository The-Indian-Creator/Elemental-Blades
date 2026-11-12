package com.elementalblades.pack;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Builds the 2D blade textures:
 *  1. recolour(): takes a 16x16 sword (e.g. YOUR copy of the vanilla netherite sword) and recolours it per element,
 *     keeping every pixel's shading, so the shape stays exactly one-to-one.
 *  2. decorate(): doubles it to 32x32 (still pixel-identical) and adds element details around it
 *     (fire swooshes, ice crystals, vines, void wisps, a lightning bolt, wind streaks, trident prongs).
 * Pure Java (no Bukkit) so it can be tested on its own.
 */
public final class TextureForge {

    private TextureForge() {}

    // ------------------------------------------------------------------ palettes (dark, mid, light)
    private static final Map<String, int[][]> BLADE = Map.of(
            "flame",     new int[][]{{70, 12, 4}, {230, 70, 20}, {255, 235, 140}},
            "frost",     new int[][]{{12, 40, 90}, {90, 190, 245}, {240, 255, 255}},
            "life",      new int[][]{{8, 50, 20}, {60, 205, 80}, {210, 255, 195}},
            "silence",   new int[][]{{20, 6, 40}, {125, 70, 215}, {225, 200, 255}},
            "lightning", new int[][]{{80, 50, 4}, {245, 195, 30}, {255, 255, 220}},
            "velocity",  new int[][]{{40, 46, 80}, {185, 195, 230}, {255, 255, 255}},
            "water",     new int[][]{{6, 24, 80}, {40, 125, 245}, {185, 235, 255}});

    // ------------------------------------------------------------------ 1. recolour

    public static BufferedImage recolour(BufferedImage src, String blade, boolean keepHandle) {
        int[][] stops = BLADE.getOrDefault(blade, BLADE.get("velocity"));
        int w = src.getWidth(), h = src.getHeight();
        float[] hsb = new float[3];
        double lo = 1, hi = 0;
        boolean[][] handle = new boolean[w][h];
        double[][] lum = new double[w][h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = src.getRGB(x, y);
            if ((argb >>> 24) == 0) continue;
            int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
            java.awt.Color.RGBtoHSB(r, g, b, hsb);
            double l = (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 510.0;
            lum[x][y] = l;
            handle[x][y] = hsb[1] > 0.30f && hsb[0] > 0.02f && hsb[0] < 0.17f;   // warm brown = wooden handle
            if (!handle[x][y]) { lo = Math.min(lo, l); hi = Math.max(hi, l); }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int argb = src.getRGB(x, y);
            int a = argb >>> 24;
            if (a == 0) continue;
            if (handle[x][y]) {
                out.setRGB(x, y, keepHandle ? argb : (a << 24) | gradient(stops, 0.12 + 0.45 * lum[x][y]));
            } else {
                double t = hi > lo ? (lum[x][y] - lo) / (hi - lo) : 0.5;
                out.setRGB(x, y, (a << 24) | gradient(stops, Math.pow(t, 0.85)));
            }
        }
        return out;
    }

    private static int gradient(int[][] s, double t) {
        t = Math.max(0, Math.min(1, t));
        int[] a, b; double k;
        if (t < 0.5) { a = s[0]; b = s[1]; k = t / 0.5; } else { a = s[1]; b = s[2]; k = (t - 0.5) / 0.5; }
        int r = (int) Math.round(a[0] + (b[0] - a[0]) * k), g = (int) Math.round(a[1] + (b[1] - a[1]) * k),
            bl = (int) Math.round(a[2] + (b[2] - a[2]) * k);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ 2. decorate

    /** Sword geometry in diagonal coordinates: a = x - y (along the blade), s = x + y (across). */
    record Geometry(int tip, int base, int guard, double centre, double halfWidth, double guardHalf) {}

    static Geometry analyse(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        Map<Integer, List<Integer>> byA = new TreeMap<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
            if ((img.getRGB(x, y) >>> 24) != 0) byA.computeIfAbsent(x - y, k -> new ArrayList<>()).add(x + y);
        if (byA.isEmpty()) return new Geometry(13, -13, -4, 15, 2, 5);
        int amin = ((TreeMap<Integer, List<Integer>>) byA).firstKey(), amax = ((TreeMap<Integer, List<Integer>>) byA).lastKey();
        int guard = amin; double best = -1;
        for (var e : byA.entrySet()) {
            int a = e.getKey();
            if (a > amin + (amax - amin) * 0.5) break;
            List<Integer> ss = e.getValue();
            double span = Collections.max(ss) - Collections.min(ss);
            if (span >= best) { best = span; guard = a; }
        }
        double sum = 0, n = 0, hw = 0, hn = 0;
        for (var e : byA.entrySet()) {
            if (e.getKey() <= guard + 2) continue;
            List<Integer> ss = e.getValue();
            for (int s : ss) { sum += s; n++; }
            hw += (Collections.max(ss) - Collections.min(ss)) / 2.0; hn++;
        }
        double centre = n > 0 ? sum / n : 15;
        double half = hn > 0 ? hw / hn : 2;
        List<Integer> gs = byA.get(guard);
        double guardHalf = (Collections.max(gs) - Collections.min(gs)) / 2.0;
        return new Geometry(amax, amin, guard, centre, half, guardHalf);
    }

    private static final Map<String, Integer> AURA = Map.of(
            "flame", 0xFF7A1A, "frost", 0x7FE3FF, "life", 0x5CFF7A, "silence", 0xA060FF,
            "lightning", 0xFFE53B, "velocity", 0xD8F4FF, "water", 0x3AA8FF);

    public static BufferedImage decorate(BufferedImage src16, String blade) { return decorate(src16, blade, true); }

    /** 16x16 sword -> 32x32 with element decorations (the sword itself stays pixel-identical, just doubled). */
    public static BufferedImage decorate(BufferedImage src16, String blade, boolean aura) {
        Geometry g = analyse(src16);
        Canvas c = new Canvas(src16);
        if (aura) c.aura(AURA.getOrDefault(blade, 0xFFFFFF), 0x8C);
        switch (blade) {
            case "flame" -> flame(c, g);
            case "frost" -> frost(c, g);
            case "life" -> life(c, g);
            case "silence" -> silence(c, g);
            case "lightning" -> lightning(c, g);
            case "velocity" -> velocity(c, g);
            case "water" -> water(c, g);
            default -> { }
        }
        return c.img;
    }

    /** 32x32 drawing surface addressed in the sword's diagonal coordinates. */
    static final class Canvas {
        final BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        final boolean[][] sword = new boolean[32][32];

        Canvas(BufferedImage src) {
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int p = src.getRGB(Math.min(x / 2, src.getWidth() - 1), Math.min(y / 2, src.getHeight() - 1));
                img.setRGB(x, y, p);
                sword[x][y] = (p >>> 24) != 0;
            }
        }

        /** Soft 1px glow outline around the sword silhouette. */
        void aura(int rgb, int alpha) {
            boolean[][] mark = new boolean[32][32];
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                if (sword[x][y]) continue;
                for (int dy = -1; dy <= 1 && !mark[x][y]; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < 32 && ny < 32 && sword[nx][ny]) { mark[x][y] = true; break; }
                }
            }
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
                if (mark[x][y]) img.setRGB(x, y, (alpha << 24) | rgb);
        }

        /** (a, s) diagonal coordinates -> 32px grid. */
        static int X(double a, double s) { return (int) Math.round(a + s + 1); }
        static int Y(double a, double s) { return (int) Math.round(s - a + 1); }

        void px(int x, int y, int rgb, boolean over) {
            if (x < 0 || y < 0 || x >= 32 || y >= 32) return;
            if (!over && sword[x][y]) return;           // decorations sit behind the sword (they may cover the aura)
            img.setRGB(x, y, 0xFF000000 | rgb);
        }

        /** Draws a small pixel stamp; anchor = bottom-centre. Chars map to colours, '.' = skip. */
        void stamp(int ax, int ay, String[] rows, Map<Character, Integer> pal, boolean over, boolean flipX) {
            int hgt = rows.length, wid = rows[0].length();
            for (int r = 0; r < hgt; r++) for (int col = 0; col < wid; col++) {
                char ch = rows[r].charAt(flipX ? wid - 1 - col : col);
                Integer rgb = pal.get(ch);
                if (rgb != null) px(ax - wid / 2 + col, ay - (hgt - 1) + r, rgb, over);
            }
        }

        void line(double a0, double s0, double a1, double s1, int rgb, boolean over) {
            int x0 = X(a0, s0), y0 = Y(a0, s0), x1 = X(a1, s1), y1 = Y(a1, s1);
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int i = 0; i <= steps; i++) {
                double t = steps == 0 ? 0 : i / (double) steps;
                px((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), rgb, over);
            }
        }
    }

    private static Map<Character, Integer> pal(Object... kv) {
        Map<Character, Integer> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    /** Evenly spaced positions along the blade (between guard and tip). */
    private static double[] along(Geometry g, int count, double startPad, double endPad) {
        double[] out = new double[count];
        double from = g.guard() + startPad, to = g.tip() - endPad;
        for (int i = 0; i < count; i++) out[i] = from + (to - from) * (count == 1 ? 0.5 : i / (double) (count - 1));
        return out;
    }

    // ---------------- Flame: fire swooshes licking up both sides + a fire burst at the guard
    static final String[] FLAME_BIG = {"..Y..", "..Y..", ".YOY.", ".YOO.", "OORO.", "ORRRO", ".RRR."};
    static final String[] FLAME_SMALL = {".Y.", ".Y.", "YOY", "ORO", ".R."};
    static void flame(Canvas c, Geometry g) {
        var p = pal('Y', 0xFFE36A, 'O', 0xFF8A1E, 'R', 0xD9300E);
        double off = g.halfWidth() + 1.2;
        double[] pos = along(g, 6, 2, 2);
        for (int i = 0; i < pos.length; i++) {
            boolean upper = i % 2 == 0;
            double s = g.centre() + (upper ? -off : off);
            c.stamp(Canvas.X(pos[i], s), Canvas.Y(pos[i], s), i % 3 == 0 ? FLAME_BIG : FLAME_SMALL, p, false, i % 2 == 1);
        }
        for (int sg : new int[]{-1, 1}) {
            double s = g.centre() + sg * (g.guardHalf() + 1);
            c.stamp(Canvas.X(g.guard(), s), Canvas.Y(g.guard(), s), FLAME_BIG, p, false, sg > 0);
        }
        // embers above the tip
        c.px(Canvas.X(g.tip() + 1, g.centre() - 3), Canvas.Y(g.tip() + 1, g.centre() - 3), 0xFFE36A, false);
        c.px(Canvas.X(g.tip() - 3, g.centre() - 6), Canvas.Y(g.tip() - 3, g.centre() - 6), 0xFF8A1E, false);
    }

    // ---------------- Frost: ice crystals growing off the blade, icicles under the guard, sparkles
    static final String[] CRYSTAL = {"..W..", ".WCW.", "WCCCW", ".CBC.", ".CBC.", "..B.."};
    static final String[] SPARKLE = {".W.", "WWW", ".W."};
    static void frost(Canvas c, Geometry g) {
        var p = pal('W', 0xFFFFFF, 'C', 0xA8ECFF, 'B', 0x3E9AE0);
        double off = g.halfWidth() + 1.0;
        double[] pos = along(g, 5, 2, 2);
        for (int i = 0; i < pos.length; i++) {
            double s = g.centre() + (i % 2 == 0 ? -off : off);
            c.stamp(Canvas.X(pos[i], s), Canvas.Y(pos[i], s), CRYSTAL, p, false, false);
        }
        for (int sg : new int[]{-1, 1}) {                        // icicles hanging from both guard ends
            double s = g.centre() + sg * g.guardHalf();
            int x = Canvas.X(g.guard(), s), y = Canvas.Y(g.guard(), s) + 2;
            for (int k = 0; k < 4; k++) c.px(x, y + k, k == 0 ? 0xFFFFFF : (k < 3 ? 0xA8ECFF : 0x3E9AE0), false);
            for (int k = 0; k < 2; k++) c.px(x + 1, y + k, 0xA8ECFF, false);
        }
        c.stamp(Canvas.X(g.tip() - 2, g.centre() - 5), Canvas.Y(g.tip() - 2, g.centre() - 5), SPARKLE, p, false, false);
        c.stamp(Canvas.X(g.tip() - 7, g.centre() + 6), Canvas.Y(g.tip() - 7, g.centre() + 6), SPARKLE, p, false, false);
    }

    // ---------------- Life: a vine winding up the grip and blade with leaves, heart gem on the guard
    static final String[] LEAF = {".GL", "GGG", "D.."};
    static final String[] HEART = {"R.R", "RRR", ".R."};
    static void life(Canvas c, Geometry g) {
        var p = pal('G', 0x3CC850, 'L', 0xA6F59A, 'D', 0x1E7A30, 'R', 0xFF3D5E);
        double a0 = g.base() + 1, a1 = g.guard() + (g.tip() - g.guard()) * 0.75;
        double prevA = a0, prevS = g.centre();
        for (double a = a0; a <= a1; a += 0.5) {               // vine (over the sword so it "wraps" it)
            double s = g.centre() + Math.sin(a * 0.9) * (g.halfWidth() + 0.6);
            c.line(prevA, prevS, a, s, (int) a % 2 == 0 ? 0x2FA845 : 0x1E7A30, true);
            prevA = a; prevS = s;
        }
        double[] pos = along(g, 5, -3, 4);
        for (int i = 0; i < pos.length; i++) {
            double s = g.centre() + (i % 2 == 0 ? -1 : 1) * (g.halfWidth() + 1.4);
            c.stamp(Canvas.X(pos[i], s), Canvas.Y(pos[i], s), LEAF, p, false, i % 2 == 1);
        }
        c.stamp(Canvas.X(g.guard(), g.centre()), Canvas.Y(g.guard(), g.centre()) + 1, HEART, p, true, false);
    }

    // ---------------- Silence: void wisps curling around the blade + floating shards
    static final String[] WISP = {"..LPP.", ".P...P", "P..PP.", "P.D...", ".DD..."};
    static final String[] SHARD = {".L.", "LPL", ".D."};
    static void silence(Canvas c, Geometry g) {
        var p = pal('P', 0x9B5BFF, 'D', 0x3A1470, 'L', 0xE0C8FF);
        double off = g.halfWidth() + 1.3;
        double[] pos = along(g, 6, 0, 0);
        for (int i = 0; i < pos.length; i++) {
            double s = g.centre() + (i % 2 == 0 ? -off : off);
            c.stamp(Canvas.X(pos[i], s), Canvas.Y(pos[i], s), WISP, p, false, i % 2 == 1);
        }
        for (int sg : new int[]{-1, 1}) {
            double s = g.centre() + sg * (g.guardHalf() + 1.5);
            c.stamp(Canvas.X(g.guard() + 2, s), Canvas.Y(g.guard() + 2, s), SHARD, p, false, false);
        }
        c.stamp(Canvas.X(g.tip() + 1, g.centre() - 4), Canvas.Y(g.tip() + 1, g.centre() - 4), SHARD, p, false, false);
    }

    // ---------------- Lightning: a bright bolt running down the blade + sparks
    static final String[] SPARK = {"Y.Y", ".W.", "Y.Y"};
    static void lightning(Canvas c, Geometry g) {
        var p = pal('Y', 0xFFE83B, 'W', 0xFFFFFF);
        double a = g.tip() - 1, s = g.centre();
        int k = 0;
        while (a > g.guard() + 1) {                             // zig-zag drawn over the blade
            double na = a - 2.5, ns = g.centre() + ((k++ % 2 == 0) ? 1.2 : -1.2);
            c.line(a, s, na, ns, 0xFFFFFF, true);
            c.line(a, s + 0.6, na, ns + 0.6, 0x7FD4FF, true);
            a = na; s = ns;
        }
        double off = g.halfWidth() + 2;
        double[] pos = along(g, 4, 1, 1);
        for (int i = 0; i < pos.length; i++) {
            double ss = g.centre() + (i % 2 == 0 ? -off : off);
            c.stamp(Canvas.X(pos[i], ss), Canvas.Y(pos[i], ss) + 1, SPARK, p, false, false);
        }
    }

    // ---------------- Velocity: wind streaks trailing the blade + feather wings on the guard
    static final String[] FEATHER = {"WW..", "WCW.", ".CCW", "..CC"};
    static void velocity(Canvas c, Geometry g) {
        var p = pal('W', 0xFFFFFF, 'C', 0x8FE0FF);
        for (int row = 0; row < 3; row++) {                     // streaks behind (lower side of the blade)
            double s = g.centre() + g.halfWidth() + 1.5 + row * 1.6;
            double start = g.guard() + 2 + row * 2, len = 6 - row * 1.5;
            for (double a = start; a < g.tip() - 2; a += len + 2.5)
                c.line(a, s, Math.min(a + len, g.tip() - 2), s, row == 0 ? 0xFFFFFF : 0xBDEBFF, false);
        }
        for (int sg : new int[]{-1, 1}) {
            double s = g.centre() + sg * (g.guardHalf() + 1);
            c.stamp(Canvas.X(g.guard(), s), Canvas.Y(g.guard(), s), FEATHER, p, false, sg > 0);
        }
    }

    // ---------------- Water: the guard grows two trident prongs + bubbles
    static final String[] BUBBLE = {".B.", "B.B", ".B."};
    static void water(Canvas c, Geometry g) {
        var p = pal('B', 0x9FE8FF);
        int teal = 0x2ECFC0, dark = 0x0E7A78, light = 0xBFFFF6;
        for (int sg : new int[]{-1, 1}) {
            double s = g.centre() + sg * g.guardHalf();
            double a0 = g.guard(), a1 = g.guard() + 6;
            c.line(a0, s, a1, s, teal, false);                    // prong shaft, parallel to the blade
            c.line(a0, s + 0.6 * sg, a1, s + 0.6 * sg, dark, false);
            c.line(a1, s, a1 + 1.5, s, light, false);             // prong point
            c.line(a1 - 1, s + 1.5 * sg, a1 + 0.5, s, light, false); // barb
            c.line(a1 - 1, s - 1.2 * sg, a1 + 0.5, s, teal, false);
        }
        double[] pos = along(g, 3, 4, 1);
        for (int i = 0; i < pos.length; i++) {
            double s = g.centre() + (i % 2 == 0 ? -1 : 1) * (g.halfWidth() + 3.5);
            c.stamp(Canvas.X(pos[i], s), Canvas.Y(pos[i], s), BUBBLE, p, false, false);
        }
    }
}
