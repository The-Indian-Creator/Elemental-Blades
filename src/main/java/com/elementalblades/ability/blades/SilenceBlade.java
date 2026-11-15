package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class SilenceBlade extends Blade {

    private static final Color VOID = Color.fromRGB(155, 107, 255);

    // ---------------- sphere
    private final Map<UUID, Long> shieldUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> shieldTasks = new HashMap<>();

    // ---------------- dome
    public static final class Dome {
        final UUID owner;
        final World world;
        final double cx, cy, cz;
        final double radius;
        final Map<Block, BlockData> original = new LinkedHashMap<>();
        BukkitTask task;
        final long started = System.currentTimeMillis();

        Dome(UUID owner, Location center, double radius) {
            this.owner = owner;
            this.world = center.getWorld();
            this.cx = center.getBlockX() + 0.5;
            this.cy = center.getBlockY() + 0.5;
            this.cz = center.getBlockZ() + 0.5;
            this.radius = radius;
        }

        /** Inside the walls (strictly within radius - 0.5). */
        public boolean inside(Location l) {
            if (l.getWorld() == null || !l.getWorld().equals(world)) return false;
            double dx = l.getX() - cx, dy = l.getY() - cy, dz = l.getZ() - cz;
            return dx * dx + dy * dy + dz * dz < (radius - 0.5) * (radius - 0.5);
        }

        public UUID owner() { return owner; }
    }

    private final Map<UUID, Dome> domes = new HashMap<>();
    private final Map<Block, Dome> domeBlocks = new HashMap<>();

    public SilenceBlade(ElementalBlades plugin) {
        super(plugin, BladeType.SILENCE);
    }

    // ============================================================ Ability 1 — Void Sphere

    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        long duration = ctx.ticks("ability1.duration", 5);
        shieldUntil.put(p.getUniqueId(), System.currentTimeMillis() + duration * 50);
        BukkitTask old = shieldTasks.remove(p.getUniqueId());
        if (old != null) old.cancel();
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        shieldTasks.put(p.getUniqueId(), Fx.repeat(plugin, 3, duration, t -> {
            if (!p.isOnline() || p.isDead()) return;
            Fx.sphere(p.getLocation().add(0, 1, 0), 1.7, VOID, 0.9f, 70);
        }, () -> {
            shieldTasks.remove(p.getUniqueId());
            shieldUntil.remove(p.getUniqueId());
            if (p.isOnline()) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.8f);
        }));
        return true;
    }

    public boolean isShielded(Player p) {
        Long until = shieldUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    // ============================================================ Ability 2 — Silent Dome

    @Override
    public boolean bypassCooldown(Player player, int slot) {
        return slot == 2 && domes.containsKey(player.getUniqueId()); // can always turn the dome OFF
    }

    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        if (domes.containsKey(p.getUniqueId())) {
            endDome(p.getUniqueId(), "<gray>You lowered your dome.");
            return false;
        }
        int radius = Math.max(3, ctx.i("ability2.radius", 15));
        Material mat = Material.matchMaterial(ctx.s("ability2.material", "BLACK_STAINED_GLASS"));
        if (mat == null || !mat.isBlock()) mat = Material.BLACK_STAINED_GLASS;
        boolean onlyPassable = ctx.b("ability2.only-replace-passable", true);

        Dome dome = new Dome(p.getUniqueId(), p.getLocation(), radius);
        BlockData shell = mat.createBlockData();
        int cx = p.getLocation().getBlockX(), cy = p.getLocation().getBlockY(), cz = p.getLocation().getBlockZ();
        World w = p.getWorld();
        double inner = (radius - 0.5) * (radius - 0.5), outer = (radius + 0.5) * (radius + 0.5);
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int y = -radius - 1; y <= radius + 1; y++) {
                int by = cy + y;
                if (by < w.getMinHeight() || by >= w.getMaxHeight()) continue;
                for (int z = -radius - 1; z <= radius + 1; z++) {
                    double d = x * x + y * y + z * z;
                    if (d < inner || d >= outer) continue;
                    Block b = w.getBlockAt(cx + x, by, cz + z);
                    if (domeBlocks.containsKey(b)) continue;
                    if (onlyPassable && !(b.isPassable() || b.isLiquid() || b.isReplaceable())) continue;
                    if (b.getType() == Material.BEDROCK || b.getType() == Material.END_PORTAL_FRAME) continue;
                    dome.original.put(b, b.getBlockData());
                    b.setBlockData(shell, false);
                    domeBlocks.put(b, dome);
                }
            }
        }
        domes.put(p.getUniqueId(), dome);
        saveRecovery(dome);
        Location center = new Location(w, dome.cx, dome.cy, dome.cz);
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.6f);
        w.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);

        int trapped = 0;
        for (Player other : w.getPlayers()) {
            if (!other.equals(p) && dome.inside(other.getLocation()) && plugin.combat().canAffect(p, other, type)) {
                trapped++;
                Text.send(other, "<dark_purple>You are trapped in " + Text.esc(p.getName()) + "'s Silent Dome!");
            }
        }
        Text.send(p, "<light_purple>Silent Dome raised" + (trapped > 0 ? " — trapped <white>" + trapped + "</white> player(s)." : ".")
                + " <gray>Use again to lower it.");

        long maxTicks = ctx.ticks("ability2.max-duration", 0);
        dome.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player owner = Bukkit.getPlayer(dome.owner);
            if (owner == null || owner.isDead()) { endDome(dome.owner, null); return; }
            if (maxTicks > 0 && System.currentTimeMillis() - dome.started > maxTicks * 50) {
                endDome(dome.owner, "<gray>Your dome faded.");
                return;
            }
            // Ambient particles on the inside
            w.spawnParticle(Particle.REVERSE_PORTAL, center, 40, radius / 2.0, radius / 3.0, radius / 2.0, 0.02);
        }, 20L, 20L);
        return true;
    }

    public void endDome(UUID owner, String message) {
        Dome dome = domes.remove(owner);
        if (dome == null) return;
        if (dome.task != null) dome.task.cancel();
        for (Map.Entry<Block, BlockData> e : dome.original.entrySet()) {
            domeBlocks.remove(e.getKey());
            e.getKey().setBlockData(e.getValue(), false);
        }
        deleteRecovery(owner);
        dome.world.playSound(new Location(dome.world, dome.cx, dome.cy, dome.cz), Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
        Player p = Bukkit.getPlayer(owner);
        if (p != null && message != null) Text.send(p, message);
    }

    public boolean isDomeBlock(Block b) { return domeBlocks.containsKey(b); }

    public Collection<Dome> domes() { return domes.values(); }

    /** Whether the player is allowed to cross this dome's wall. */
    public boolean canPass(Player p, Dome dome) {
        if (p.getUniqueId().equals(dome.owner))
            return plugin.getConfig().getBoolean("blades.silence.ability2.owner-can-pass", true);
        if (p.getGameMode() == GameMode.SPECTATOR || p.hasPermission("elementalblades.admin") && p.getGameMode() == GameMode.CREATIVE)
            return true;
        Player owner = Bukkit.getPlayer(dome.owner);
        return owner != null && !plugin.combat().canAffect(owner, p, type);
    }

    /** Returns the dome whose wall this movement crosses (and the player can't pass), or null. */
    public Dome blockingDome(Player p, Location from, Location to) {
        if (domes.isEmpty() || to == null) return null;
        for (Dome d : domes.values()) {
            if (d.inside(from) != d.inside(to) && !canPass(p, d)) return d;
        }
        return null;
    }

    @Override
    public String statusText(Player player) {
        if (domes.containsKey(player.getUniqueId())) return "◯ Dome up";
        if (isShielded(player)) return "◯ Shielded";
        return null;
    }

    // ============================================================ Passive — pearl on kill

    @Override
    public void onKill(AbilityContext ctx, LivingEntity victim) {
        if (!(victim instanceof Player) && !plugin.getConfig().getBoolean("count-mob-kills", false)) return;
        Player p = ctx.player();
        p.getInventory().addItem(BladeItems.silencePearl(ctx.i("passive.pearl-uses", 5)))
                .values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        Text.send(p, "<light_purple>+1 Silent Pearl <gray>(" + ctx.i("passive.pearl-uses", 5) + " uses)");
    }

    // ============================================================ crash recovery
    // Each live dome is written to plugins/ElementalBlades/domes/<owner>.txt so a crash never leaves a permanent dome.

    private java.io.File recoveryFile(UUID owner) {
        return new java.io.File(new java.io.File(plugin.getDataFolder(), "domes"), owner + ".txt");
    }

    private void saveRecovery(Dome dome) {
        java.io.File f = recoveryFile(dome.owner);
        f.getParentFile().mkdirs();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Block, BlockData> e : dome.original.entrySet()) {
            Block b = e.getKey();
            sb.append(b.getWorld().getName()).append(';').append(b.getX()).append(';').append(b.getY()).append(';')
                    .append(b.getZ()).append(';').append(e.getValue().getAsString()).append('\n');
        }
        try {
            java.nio.file.Files.writeString(f.toPath(), sb.toString());
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Could not write dome recovery file: " + ex.getMessage());
        }
    }

    private void deleteRecovery(UUID owner) {
        java.io.File f = recoveryFile(owner);
        if (f.exists() && !f.delete()) f.deleteOnExit();
    }

    /** Restores blocks of domes left behind by a crash. Call on enable. */
    public void recoverDomes() {
        java.io.File dir = new java.io.File(plugin.getDataFolder(), "domes");
        java.io.File[] files = dir.listFiles((d, n) -> n.endsWith(".txt"));
        if (files == null) return;
        for (java.io.File f : files) {
            int restored = 0;
            try {
                for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                    String[] parts = line.split(";", 5);
                    if (parts.length < 5) continue;
                    World w = Bukkit.getWorld(parts[0]);
                    if (w == null) continue;
                    Block b = w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    b.setBlockData(Bukkit.createBlockData(parts[4]), false);
                    restored++;
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Dome recovery failed for " + f.getName() + ": " + ex.getMessage());
            }
            plugin.getLogger().info("Restored " + restored + " blocks from a leftover Silent Dome (" + f.getName() + ").");
            f.delete();
        }
    }

    // ============================================================ lifecycle

    // ============================================================ Ultimate — Void Collapse

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double pullRadius = ctx.d("ultimate.pull-radius", 12);
        if (plugin.combat().targetsNear(p, p.getLocation(), pullRadius, type).isEmpty()) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("No enemies in range for Void Collapse",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return false;
        }
        long pullTicks = ctx.ticks("ultimate.pull-seconds", 3);
        double strength = ctx.d("ultimate.pull-strength", 0.45);
        double collapseRadius = ctx.d("ultimate.collapse-radius", 6);
        double damage = ctx.dmg("ultimate.damage", 6.0);
        double shock = ctx.d("ultimate.silence-seconds", 4);
        int blind = (int) ctx.ticks("ultimate.blindness-seconds", 3);
        int dark = (int) ctx.ticks("ultimate.darkness-seconds", 5);
        Location c = p.getLocation().add(0, 1, 0);
        World w = p.getWorld();
        w.playSound(c, Sound.BLOCK_PORTAL_TRIGGER, 1f, 0.6f);
        w.playSound(c, Sound.ENTITY_WARDEN_EMERGE, 1f, 1.2f);
        trackUlt(p, Fx.repeat(plugin, 2, pullTicks, t -> {
            if (!p.isOnline() || p.isDead()) { stopUltimate(p.getUniqueId()); return; }
            double progress = t / (double) pullTicks;
            Fx.sphere(c, 0.6 + progress * 1.2, VOID, 1.4f, 40);
            for (int arm = 0; arm < 4; arm++) {
                double a = t * 0.3 + arm * Math.PI / 2;
                double r = pullRadius * (1 - progress);
                w.spawnParticle(Particle.REVERSE_PORTAL, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 6, 0.2, 0.4, 0.2, 0.02);
            }
            w.spawnParticle(Particle.SQUID_INK, c, 4, 0.3, 0.3, 0.3, 0.01);
            for (LivingEntity e : plugin.combat().targetsNear(p, c, pullRadius, type)) {
                org.bukkit.util.Vector in = c.toVector().subtract(e.getLocation().add(0, 1, 0).toVector());
                if (in.lengthSquared() < 1.0) { e.setVelocity(new org.bukkit.util.Vector()); continue; }
                e.setVelocity(in.normalize().multiply(strength).setY(Math.max(-0.2, Math.min(0.25, in.getY() * 0.2))));
            }
        }, () -> {
            if (!p.isOnline()) return;
            w.spawnParticle(Particle.SONIC_BOOM, c, 1);
            w.spawnParticle(Particle.REVERSE_PORTAL, c, 200, collapseRadius * 0.4, 1, collapseRadius * 0.4, 0.4);
            w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.6f);
            w.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.5f);
            for (LivingEntity e : plugin.combat().targetsNear(p, c, collapseRadius, type)) {
                plugin.combat().trueDamage(p, e, damage);
                if (blind > 0) e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, blind, 0));
                if (dark > 0) e.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS, dark, 0));
                if (e instanceof Player victim && shock > 0) {
                    plugin.status().shock(victim, shock, false);
                    Text.send(victim, "<dark_purple>The void silenced you! <gray>Abilities disabled for " + (int) shock + "s.");
                }
            }
        }));
        return true;
    }

    @Override
    public void cleanup(Player player) {
        endDome(player.getUniqueId(), null);
        BukkitTask t = shieldTasks.remove(player.getUniqueId());
        if (t != null) t.cancel();
        shieldUntil.remove(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(domes.keySet())) endDome(id, null);
        shieldTasks.values().forEach(BukkitTask::cancel);
        shieldTasks.clear();
        shieldUntil.clear();
    }
}
