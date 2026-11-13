package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeType;
import com.elementalblades.combat.StatusManager;
import com.elementalblades.util.Keys;
import com.elementalblades.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Velocity Blade — moves in "frames".
 * A1 Frame Step: high speed, leaves afterimages that frame-lock enemies who touch them.
 * A2 Blink Barrage: flash-step behind nearby enemies, slashing each and leaving trap afterimages.
 * Ultimate Freeze Frame: frame-lock every enemy around you; your hits on them deal bonus damage.
 */
public final class VelocityBlade extends Blade {

    private static final Color WHITE = Color.fromRGB(232, 232, 255);
    private static final Color BLUE = Color.fromRGB(120, 200, 255);

    /** One afterimage: a mannequin copy of the player, or a particle silhouette. */
    private static final class Afterimage {
        final UUID owner;
        final Location loc;
        final Mannequin body;      // null = particle style
        final long born, expires;
        final Set<UUID> trapped = new HashSet<>();
        Afterimage(UUID owner, Location loc, Mannequin body, long born, long expires) {
            this.owner = owner; this.loc = loc; this.body = body; this.born = born; this.expires = expires;
        }
    }

    private final List<Afterimage> afterimages = new ArrayList<>();
    private final Map<UUID, BukkitTask> frameStep = new HashMap<>();
    private final Set<UUID> touchLockReady = new HashSet<>();
    private final Map<UUID, BukkitTask> barrage = new HashMap<>();
    private final Map<UUID, Long> freezeFrameUntil = new HashMap<>();
    private boolean mannequinsBroken; // set if the server refuses to spawn mannequins -> particles from then on

    public VelocityBlade(ElementalBlades plugin) {
        super(plugin, BladeType.VELOCITY);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAfterimages, 1L, 1L);
    }

    private double cfgD(String path, double def) { return plugin.getConfig().getDouble("blades.velocity." + path, def); }

    // ============================================================ A1 — Frame Step

    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        endFrameStep(p);
        long duration = ctx.ticks("ability1.duration", 10);
        double mult = ctx.d("ability1.speed-multiplier", 2.0);
        int every = Math.max(2, ctx.i("ability1.afterimage-every-ticks", 4));
        int jump = ctx.i("ability1.jump-boost-level", 2);
        StatusManager.addModifier(p, Attribute.MOVEMENT_SPEED, Keys.MOD_VELOCITY_SPEED, mult - 1.0, AttributeModifier.Operation.ADD_SCALAR);
        if (jump > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (int) duration, jump - 1, false, false, true));
        if (ctx.b("ability1.touch-lock", true)) touchLockReady.add(p.getUniqueId());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.6f);
        p.getWorld().playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 2f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.3, 0.7, 0.3, 0.1);
        Text.send(p, "<white>Frame Step! <gray>Your afterimages trap anyone who touches them for " + duration / 20 + "s.");
        final Location[] last = {p.getLocation()};
        frameStep.put(p.getUniqueId(), Fx.repeat(plugin, 1, duration, t -> {
            if (!p.isOnline() || p.isDead()) { endFrameStep(p); return; }
            if (t % 2 == 0) p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.9, 0), 2, 0.2, 0.4, 0.2, 0.02);
            // only leave a frame if we actually moved (no stack of copies when standing still)
            if (t % every == 0 && p.getLocation().getWorld().equals(last[0].getWorld())
                    && p.getLocation().distanceSquared(last[0]) > 0.6) {
                spawnAfterimage(p, ctx.d("ability1.afterimage-seconds", 2.0));
                last[0] = p.getLocation();
            }
        }, () -> finishFrameStep(p)));
        return true;
    }

    public boolean frameStepping(Player p) { return frameStep.containsKey(p.getUniqueId()); }

    private void endFrameStep(Player p) {
        BukkitTask t = frameStep.remove(p.getUniqueId());
        if (t != null) t.cancel();
        finishFrameStep(p);
    }

    private void finishFrameStep(Player p) {
        frameStep.remove(p.getUniqueId());
        touchLockReady.remove(p.getUniqueId());
        StatusManager.removeModifier(p, Attribute.MOVEMENT_SPEED, Keys.MOD_VELOCITY_SPEED);
        PotionEffect jb = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jb != null && !jb.hasParticles() && !jb.isInfinite()) p.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    // ------------------------------------------------------------ afterimages

    private void spawnAfterimage(Player p, double seconds) {
        long now = System.currentTimeMillis();
        int max = plugin.getConfig().getInt("blades.velocity.frame-lock.max-afterimages-per-player", 12);
        List<Afterimage> mine = new ArrayList<>();
        for (Afterimage a : afterimages) if (a.owner.equals(p.getUniqueId())) mine.add(a);
        while (mine.size() >= max) { remove(mine.remove(0)); }

        Location loc = p.getLocation().clone();
        Mannequin body = null;
        boolean useMannequin = !mannequinsBroken
                && !"PARTICLES".equalsIgnoreCase(plugin.getConfig().getString("blades.velocity.frame-lock.afterimage-style", "MANNEQUIN"));
        if (useMannequin) {
            try {
                body = p.getWorld().spawn(loc, Mannequin.class, m -> {
                    m.setPersistent(false);
                    m.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
                    m.setImmovable(true);
                    m.setAI(false);
                    m.setGravity(false);
                    m.setInvulnerable(true);
                    m.setSilent(true);
                    m.setCollidable(false);
                    m.setDescription(Component.empty());
                    m.getPersistentDataContainer().set(Keys.AFTERIMAGE, PersistentDataType.BYTE, (byte) 1);
                    EntityEquipment eq = m.getEquipment();
                    var inv = p.getInventory();
                    if (inv.getHelmet() != null) eq.setHelmet(inv.getHelmet().clone());
                    if (inv.getChestplate() != null) eq.setChestplate(inv.getChestplate().clone());
                    if (inv.getLeggings() != null) eq.setLeggings(inv.getLeggings().clone());
                    if (inv.getBoots() != null) eq.setBoots(inv.getBoots().clone());
                    eq.setItemInMainHand(inv.getItemInMainHand().clone());
                });
            } catch (Throwable ex) {
                mannequinsBroken = true;
                plugin.getLogger().warning("Couldn't spawn mannequin afterimages (" + ex + ") — using particle afterimages instead.");
                body = null;
            }
        }
        afterimages.add(new Afterimage(p.getUniqueId(), loc, body, now, now + (long) (seconds * 1000)));
        p.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 3, 0.2, 0.5, 0.2, 0.01);
    }

    private void remove(Afterimage a) {
        afterimages.remove(a);
        if (a.body != null && a.body.isValid()) {
            a.body.getWorld().spawnParticle(Particle.CLOUD, a.body.getLocation().add(0, 1, 0), 6, 0.2, 0.5, 0.2, 0.01);
            a.body.remove();
        }
    }

    /** Somebody hit an afterimage: it shatters. */
    public void shatter(Entity body) {
        for (Afterimage a : new ArrayList<>(afterimages)) {
            if (a.body != null && a.body.equals(body)) {
                body.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, body.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.2);
                body.getWorld().playSound(body.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 2f);
                remove(a);
                return;
            }
        }
        if (body.getPersistentDataContainer().has(Keys.AFTERIMAGE)) body.remove(); // orphan
    }

    private void tickAfterimages() {
        if (afterimages.isEmpty()) return;
        long now = System.currentTimeMillis();
        double lockSeconds = cfgD("frame-lock.seconds", 1.0);
        for (Afterimage a : new ArrayList<>(afterimages)) {
            Player owner = Bukkit.getPlayer(a.owner);
            if (owner == null || now >= a.expires || a.loc.getWorld() == null) { remove(a); continue; }
            double life = (a.expires - now) / (double) (a.expires - a.born);
            if (a.body == null) silhouette(a.loc, life);
            else if (owner.getTicksLived() % 4 == 0)
                a.loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, a.loc.clone().add(0, 1, 0), 1, 0.25, 0.6, 0.25, 0);
            // the trap
            for (Entity e : a.loc.getWorld().getNearbyEntities(a.loc.clone().add(0, 1, 0), 0.7, 1.1, 0.7)) {
                if (!(e instanceof LivingEntity le) || e.getPersistentDataContainer().has(Keys.AFTERIMAGE)) continue;
                if (!a.trapped.add(le.getUniqueId())) continue;
                if (!plugin.combat().canAffect(owner, le, type)) continue;
                if (plugin.status().frameLock(le, lockSeconds, false)) {
                    if (le instanceof Player victim)
                        Text.send(victim, "<white>▣ You touched " + Text.esc(owner.getName()) + "'s afterimage and got frame-locked!");
                    owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 2f);
                    if (plugin.getConfig().getBoolean("blades.velocity.frame-lock.consume-afterimage", true)) { remove(a); break; }
                }
            }
        }
    }

    /** Particle silhouette of a standing player, fading with life (1 -> 0). */
    private void silhouette(Location base, double life) {
        Vector f = base.getDirection().setY(0);
        if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
        f.normalize();
        Vector r = new Vector(-f.getZ(), 0, f.getX());
        Particle.DustOptions dust = new Particle.DustOptions(life > 0.5 ? WHITE : BLUE, (float) (0.5 + 0.5 * life));
        var w = base.getWorld();
        // legs
        for (double y = 0.1; y <= 0.75; y += 0.2) {
            w.spawnParticle(Particle.DUST, base.clone().add(r.clone().multiply(0.13)).add(0, y, 0), 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, base.clone().add(r.clone().multiply(-0.13)).add(0, y, 0), 1, 0, 0, 0, 0, dust);
        }
        // body + arms
        for (double y = 0.8; y <= 1.45; y += 0.2) {
            w.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0.08, 0, 0.08, 0, dust);
            w.spawnParticle(Particle.DUST, base.clone().add(r.clone().multiply(0.38)).add(0, y, 0), 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, base.clone().add(r.clone().multiply(-0.38)).add(0, y, 0), 1, 0, 0, 0, 0, dust);
        }
        // head
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.2, 1.72, Math.sin(a) * 0.2), 1, 0, 0, 0, 0, dust);
        }
    }

    // ============================================================ A2 — Blink Barrage

    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ability2.radius", 16);
        int max = ctx.i("ability2.max-targets", 6);
        List<LivingEntity> targets = plugin.combat().targetsNear(p, p.getLocation(), radius, type);
        if (targets.isEmpty()) {
            p.sendActionBar(Component.text("No enemies in range for Blink Barrage", NamedTextColor.GRAY));
            return false;
        }
        targets.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
        List<LivingEntity> order = new ArrayList<>(targets.subList(0, Math.min(max, targets.size())));
        double damage = ctx.dmg("ability2.damage", 8.0);
        long gap = Math.max(2, ctx.i("ability2.ticks-between", 5));
        boolean returnToStart = ctx.b("ability2.return-to-start", true);
        boolean leaveImages = ctx.b("ability2.leave-afterimages", true);
        double imageSeconds = ctx.d("ability2.afterimage-seconds", 3.0);
        Location start = p.getLocation();
        stopBarrage(p);
        plugin.status().noFall(p, (order.size() * gap + 60) * 50L);
        p.getWorld().playSound(start, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2f, 2f);
        if (leaveImages) spawnAfterimage(p, imageSeconds);
        final int[] idx = {0};
        barrage.put(p.getUniqueId(), Fx.repeat(plugin, gap, order.size() * gap, t -> {
            if (!p.isOnline() || p.isDead()) { stopBarrage(p); return; }
            LivingEntity e = order.get(idx[0]++);
            if (!e.isValid() || e.isDead() || !e.getWorld().equals(p.getWorld())
                    || !plugin.combat().canAffect(p, e, type)) return;
            Location from = p.getLocation().add(0, 1, 0);
            Vector behind = e.getLocation().getDirection().setY(0);
            if (behind.lengthSquared() < 1e-4) behind = new Vector(0, 0, 1);
            Location to = e.getLocation().subtract(behind.normalize().multiply(1.3));
            to.setDirection(e.getLocation().toVector().subtract(to.toVector()));
            if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable())
                to = e.getLocation().clone().setDirection(p.getLocation().getDirection());
            Vector line = to.clone().add(0, 1, 0).toVector().subtract(from.toVector());
            double len = line.length();
            if (len > 0.1) {
                line.normalize();
                for (double d = 0; d < len; d += 0.5)
                    p.getWorld().spawnParticle(Particle.END_ROD, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0);
            }
            p.teleport(to);
            if (leaveImages) spawnAfterimage(p, imageSeconds);
            plugin.combat().armouredDamage(p, e, damage);
            e.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.3);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.6f);
        }, () -> {
            barrage.remove(p.getUniqueId());
            if (returnToStart && p.isOnline() && !p.isDead() && p.getWorld().equals(start.getWorld())) {
                p.teleport(start);
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, start.clone().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.3);
            }
        }));
        return true;
    }

    /** True while blinking: the user can't be hurt. */
    public boolean inBarrage(Player p) {
        return barrage.containsKey(p.getUniqueId()) && plugin.getConfig().getBoolean("blades.velocity.ability2.invulnerable", true);
    }

    private void stopBarrage(Player p) {
        BukkitTask t = barrage.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    // ============================================================ Ultimate — Freeze Frame

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ultimate.radius", 12);
        List<LivingEntity> targets = plugin.combat().targetsNear(p, p.getLocation(), radius, type);
        if (targets.isEmpty()) {
            p.sendActionBar(Component.text("No enemies in range for Freeze Frame", NamedTextColor.GRAY));
            return false;
        }
        double seconds = ctx.d("ultimate.duration", 3);
        long ticks = Math.round(seconds * 20);
        Location c = p.getLocation();
        p.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 80, 1.5, 1, 1.5, 0.3);
        p.getWorld().playSound(c, Sound.UI_BUTTON_CLICK, 2f, 0.5f);
        p.getWorld().playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 2f);
        Fx.repeat(plugin, 1, 8, t -> {
            double r = radius * (t + 1) / 8.0;
            Fx.dustRing(c.clone().add(0, 1, 0), r, Color.WHITE, 1.6f, (int) (r * 8));
        }, null);
        for (LivingEntity e : targets) {
            plugin.status().frameLock(e, seconds, true);
            if (e instanceof Player victim) Text.send(victim, "<white>▣ " + Text.esc(p.getName()) + " stopped you in a frame!");
        }
        int speed = ctx.i("ultimate.speed-level", 3);
        if (speed > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) ticks, speed - 1, false, true, true));
        freezeFrameUntil.put(p.getUniqueId(), System.currentTimeMillis() + (long) (seconds * 1000));
        trackUlt(p, Fx.repeat(plugin, 2, ticks, t -> {
            if (p.isOnline()) p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 2, 0.3, 0.6, 0.3, 0.01);
        }, () -> freezeFrameUntil.remove(p.getUniqueId())));
        return true;
    }

    // ============================================================ on-hit & passive

    @Override
    public void onMeleeHit(AbilityContext ctx, LivingEntity victim, EntityDamageByEntityEvent event) {
        Player p = ctx.player();
        if (!plugin.combat().canAffect(p, victim, type)) return;
        // Freeze Frame: bonus damage against frame-locked targets
        Long ff = freezeFrameUntil.get(p.getUniqueId());
        if (ff != null && ff > System.currentTimeMillis() && plugin.status().isFrameLocked(victim)) {
            event.setDamage(event.getDamage() * ctx.d("ultimate.locked-damage-multiplier", 1.5));
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0), 15, 0.3, 0.5, 0.3, 0.3);
        }
        // Frame Step: first hit frame-locks
        if (frameStepping(p) && touchLockReady.remove(p.getUniqueId())) {
            if (plugin.status().frameLock(victim, ctx.d("ability1.touch-lock-seconds", 1.0), false))
                p.sendActionBar(Component.text("▣ Frame-locked " + victim.getName(), NamedTextColor.WHITE));
        }
    }

    /** Passive dodge (leaves an afterimage). @return true if the hit should be cancelled. */
    public boolean tryDodge(Player p) {
        double chance = cfgD("passive.dodge-chance", 0.15);
        if (chance <= 0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) return false;
        if (plugin.getConfig().getBoolean("blades.velocity.passive.dodge-afterimage", true))
            spawnAfterimage(p, cfgD("ability1.afterimage-seconds", 2.0));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.6f);
        p.sendActionBar(Component.text("Dodged!", NamedTextColor.WHITE));
        return true;
    }

    @Override
    public void passiveTick(AbilityContext ctx) {
        int lvl = ctx.i("passive.speed-level", 1);
        if (lvl > 0) plugin.abilities().passiveEffect(ctx.player(), PotionEffectType.SPEED, lvl - 1);
    }

    @Override
    public String statusText(Player player) {
        Long ff = freezeFrameUntil.get(player.getUniqueId());
        if (ff != null && ff > System.currentTimeMillis()) return "▣ Freeze Frame " + Text.seconds(ff - System.currentTimeMillis());
        return frameStepping(player) ? "▣ Frame Step" : null;
    }

    // ============================================================ lifecycle

    private void clearImages(UUID owner) {
        for (Afterimage a : new ArrayList<>(afterimages)) if (a.owner.equals(owner)) remove(a);
    }

    @Override
    public void cleanup(Player player) {
        endFrameStep(player);
        stopBarrage(player);
        freezeFrameUntil.remove(player.getUniqueId());
        clearImages(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(frameStep.keySet())) { Player p = Bukkit.getPlayer(id); if (p != null) endFrameStep(p); }
        for (UUID id : new ArrayList<>(barrage.keySet())) { Player p = Bukkit.getPlayer(id); if (p != null) stopBarrage(p); }
        for (Afterimage a : new ArrayList<>(afterimages)) remove(a);
    }
}
