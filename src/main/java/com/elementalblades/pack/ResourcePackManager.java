package com.elementalblades.pack;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the sword-texture resource pack from plugins/ElementalBlades/resourcepack/ (editable!),
 * optionally serves it over HTTP, and sends it to players on join.
 */
public final class ResourcePackManager implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("ElementalBlades-pack".getBytes(StandardCharsets.UTF_8));

    private final ElementalBlades plugin;
    private final File packDir;
    private final File zipFile;
    private byte[] zipBytes;
    private String sha1;
    private HttpServer server;

    public ResourcePackManager(ElementalBlades plugin) {
        this.plugin = plugin;
        this.packDir = new File(plugin.getDataFolder(), "resourcepack");
        this.zipFile = new File(plugin.getDataFolder(), "ElementalBlades-pack.zip");
    }

    /** All files bundled in the jar under resourcepack/. */
    public static List<String> bundledFiles() {
        List<String> files = new ArrayList<>(List.of("pack.mcmeta", "pack.png"));
        for (BladeType t : BladeType.values()) {
            String n = t.id() + "_blade";
            files.add("assets/elementalblades/items/" + n + ".json");
            files.add("assets/elementalblades/models/item/" + n + ".json");
            files.add("assets/elementalblades/models/item/" + n + "_3d.json");
            files.add("assets/elementalblades/textures/item/" + n + "_3d.png");
        }
        return files;
    }

    public void start() {
        stopServer();
        try {
            extractDefaults();
            generateFlatTextures();
            writeItemDefinitions();
            build();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not build resource pack: " + e.getMessage());
            return;
        }
        String mode = mode();
        if (mode.equals("SELF_HOST")) startServer();
        plugin.getLogger().info("Resource pack ready (" + zipBytes.length / 1024 + " KB, sha1 " + sha1 + ", mode " + mode + ")"
                + (url() != null ? " -> " + url() : ""));
    }

    private String mode() {
        return plugin.getConfig().getString("resource-pack.mode", "SELF_HOST").toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ 2D textures

    /**
     * Writes textures/item/<blade>_blade.png (32x32): the source sword recoloured per element + decorations.
     * Source = your own vanilla netherite_sword.png if present (resource-pack.netherite-texture), else the built-in art.
     */
    private void generateFlatTextures() throws IOException {
        var cfg = plugin.getConfig();
        String sourceMode = cfg.getString("resource-pack.flat-source", "AUTO").toUpperCase(Locale.ROOT);
        java.awt.image.BufferedImage vanilla = sourceMode.equals("BUILTIN") ? null : loadVanillaSword();
        if (vanilla == null && sourceMode.equals("VANILLA"))
            plugin.getLogger().warning("resource-pack.flat-source is VANILLA but no netherite_sword.png was found — using built-in art.");
        boolean decorate = cfg.getBoolean("resource-pack.flat-decorations", true);
        boolean glow = cfg.getBoolean("resource-pack.flat-glow-outline", true);
        boolean keepHandle = cfg.getBoolean("resource-pack.keep-vanilla-handle", false);
        File texDir = new File(packDir, "assets/elementalblades/textures/item");
        texDir.mkdirs();
        for (BladeType t : BladeType.values()) {
            java.awt.image.BufferedImage base;
            if (vanilla != null) {
                base = TextureForge.recolour(vanilla, t.id(), keepHandle);
            } else {
                File custom = new File(plugin.getDataFolder(), "textures-src/" + t.id() + "_blade.png");
                base = javax.imageio.ImageIO.read(custom);
            }
            if (base == null) continue;
            java.awt.image.BufferedImage out = decorate ? TextureForge.decorate(base, t.id(), glow) : base;
            javax.imageio.ImageIO.write(out, "png", new File(texDir, t.id() + "_blade.png"));
        }
        plugin.getLogger().info("2D textures built from " + (vanilla != null ? "your vanilla netherite sword" : "built-in art")
                + (decorate ? " with element decorations" : "") + ".");
    }

    /** Reads netherite_sword.png from a png file or a Minecraft client jar (path relative to the plugin folder). */
    private java.awt.image.BufferedImage loadVanillaSword() {
        String path = plugin.getConfig().getString("resource-pack.netherite-texture", "netherite_sword.png");
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        if (!f.isAbsolute()) f = new File(plugin.getDataFolder(), path);
        if (!f.exists()) {
            plugin.getLogger().info("Tip: put your netherite_sword.png in plugins/ElementalBlades/ to get recoloured vanilla netherite swords.");
            return null;
        }
        try {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".jar") || f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(f)) {
                    var entry = zip.getEntry("assets/minecraft/textures/item/netherite_sword.png");
                    if (entry == null) { plugin.getLogger().warning(f.getName() + " has no netherite_sword.png"); return null; }
                    try (InputStream in = zip.getInputStream(entry)) { return javax.imageio.ImageIO.read(in); }
                }
            }
            return javax.imageio.ImageIO.read(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't read " + f + ": " + e.getMessage());
            return null;
        }
    }

    private void extractDefaults() throws IOException {
        for (BladeType t : BladeType.values()) {
            File out = new File(plugin.getDataFolder(), "textures-src/" + t.id() + "_blade.png");
            if (out.exists()) continue;
            try (InputStream in = plugin.getResource("textures-src/" + t.id() + "_blade.png")) {
                if (in == null) continue;
                out.getParentFile().mkdirs();
                Files.copy(in, out.toPath());
            }
        }
        for (String f : bundledFiles()) {
            File out = new File(packDir, f);
            if (out.exists()) continue;
            try (InputStream in = plugin.getResource("resourcepack/" + f)) {
                if (in == null) { plugin.getLogger().warning("Missing bundled pack file " + f); continue; }
                out.getParentFile().mkdirs();
                Files.copy(in, out.toPath());
            }
        }
    }

    /**
     * resource-pack.style:
     *   FLAT     = chunky 2D sprite everywhere
     *   MODEL_3D = 3D model everywhere
     *   HYBRID   = 3D in hand / on players, 2D icon in inventories, item frames and on the ground
     */
    private void writeItemDefinitions() throws IOException {
        String style = plugin.getConfig().getString("resource-pack.style", "HYBRID").toUpperCase(Locale.ROOT);
        for (BladeType t : BladeType.values()) {
            String flat = "{\"type\": \"minecraft:model\", \"model\": \"elementalblades:item/" + t.id() + "_blade\"}";
            String model3d = "{\"type\": \"minecraft:model\", \"model\": \"elementalblades:item/" + t.id() + "_blade_3d\"}";
            String model = switch (style) {
                case "FLAT" -> flat;
                case "MODEL_3D", "3D" -> model3d;
                default -> "{\"type\": \"minecraft:select\", \"property\": \"minecraft:display_context\", "
                        + "\"cases\": [{\"when\": [\"gui\", \"ground\", \"fixed\"], \"model\": " + flat + "}], "
                        + "\"fallback\": " + model3d + "}";
            };
            File f = new File(packDir, "assets/elementalblades/items/" + t.id() + "_blade.json");
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), "{\n  \"model\": " + model + "\n}\n");
        }
    }

    private void build() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Path root = packDir.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(bos); Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path p : files) {
                String name = root.relativize(p).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L); // stable hash between restarts
                zos.putNextEntry(entry);
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
        zipBytes = bos.toByteArray();
        Files.write(zipFile.toPath(), zipBytes);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            sha1 = HexFormat.of().formatHex(md.digest(zipBytes));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private void startServer() {
        int port = plugin.getConfig().getInt("resource-pack.self-host.port", 8163);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", ex -> {
                byte[] body = zipBytes;
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(body); }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "ElementalBlades-PackServer");
                t.setDaemon(true);
                return t;
            }));
            server.start();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not start resource pack web server on port " + port + ": " + e.getMessage()
                    + " — set resource-pack.mode to URL or NONE, or pick another port.");
            server = null;
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void shutdown() { stopServer(); }

    /** The URL players download from, or null. */
    public String url() {
        String mode = mode();
        if (mode.equals("URL")) {
            String u = plugin.getConfig().getString("resource-pack.url", "");
            return u == null || u.isBlank() ? null : u;
        }
        if (mode.equals("SELF_HOST") && server != null) {
            String addr = plugin.getConfig().getString("resource-pack.self-host.public-address", "");
            if (addr == null || addr.isBlank()) addr = Bukkit.getIp();
            if (addr == null || addr.isBlank()) return null;
            int port = plugin.getConfig().getInt("resource-pack.self-host.port", 8163);
            return "http://" + addr + ":" + port + "/ElementalBlades.zip?v=" + sha1.substring(0, 8);
        }
        return null;
    }

    public void send(Player p, boolean manual) {
        String url = url();
        if (url == null) {
            if (manual || p.hasPermission("elementalblades.admin")) {
                Text.send(p, mode().equals("SELF_HOST")
                        ? "<yellow>Resource pack: set <white>resource-pack.self-host.public-address</white> in config.yml (your server's address), then /blades reload."
                        : "<yellow>No resource pack URL set. Install <white>ElementalBlades-pack.zip</white> from the plugin folder manually, or set resource-pack.url.");
            }
            return;
        }
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), sha1);
            p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(plugin.getConfig().getBoolean("resource-pack.required", false))
                    .prompt(Text.mm(plugin.getConfig().getString("resource-pack.prompt", "Sword textures")))
                    .replace(false)
                    .build());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not send resource pack: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (mode().equals("NONE")) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) send(p, false); }, 20L);
    }
}
