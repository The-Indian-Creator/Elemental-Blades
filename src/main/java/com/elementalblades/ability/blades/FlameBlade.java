package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class FlameBlade extends Blade {

    private final Map<UUID, BukkitTask> rings = new HashMap<>();
    private final Map<UUID, BukkitTask> streams = new HashMap<>();

    public FlameBlade(ElementalBlades plugin) {
        super(plugin, BladeType.FLAME);
    }

    /** Ability 1 — Ring of Fire. */
    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        stop(rings, p.getUniqueId());
        double radius = ctx.d("ability1.radius", 3.5);
        double damage = ctx.dmg("ability1.damage", 10.0);
        long interval = Math.max(1, ctx.i("ability1.hit-interval-ticks", 20));
        int fireTicks = ctx.i("ability1.set-on-fire-ticks", 40);
        long duration = ctx.ticks("ability1.duration", 5);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
        rings.put(p.getUniqueId(), Fx.repeat(plugin, 2, duration, elapsed -> {
            if (!p.isOnline() || p.isDead()) { stop(rings, p.getUniqueId()); return; }
            Location base = p.getLocation();
            Fx.ring(base.clone().add(0, 0.15, 0), radius, Particle.FLAME, 48, null);
            Fx.ring(base.clone().add(0, 0.9, 0), radius, Particle.FLAME, 36, null);
            Fx.ring(base.clone().add(0, 0.5, 0), radius - 0.2, Particle.SOUL_FIRE_FLAME, 16, null);
            if (elapsed % interval == 0) {
                for (LivingEntity t : plugin.combat().targetsNear(p, base.clone().add(0, 1, 0), radius + 0.5, type)) {
                    plugin.combat().armouredDamage(p, t, damage);
                    if (fireTicks > 0) t.setFireTicks(Math.max(t.getFireTicks(), fireTicks));
                    t.getWorld().spawnParticle(Particle.LAVA, t.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0);
                }
                p.getWorld().playSound(base, Sound.BLOCK_FIRE_AMBIENT, 1.4f, 1.0f);
            }
        }, () -> rings.remove(p.getUniqueId())));
        return true;
    }

    /** Ability 2 — Fireball Stream. */
    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        stop(streams, p.getUniqueId());
        long interval = Math.max(1, ctx.i("ability2.shot-interval-ticks", 4));
        double speed = ctx.d("ability2.fireball-speed", 1.6);
        boolean ignite = ctx.b("ability2.ignite-blocks", false);
        long duration = ctx.ticks("ability2.duration", 5);
        String tag = type.id() + ":" + ctx.level();
        streams.put(p.getUniqueId(), Fx.repeat(plugin, interval, duration, elapsed -> {
            if (!p.isOnline() || p.isDead() || plugin.status().isShocked(p)) { stop(streams, p.getUniqueId()); return; }
            Vector dir = p.getEyeLocation().getDirection().normalize();
            SmallFireball fb = p.launchProjectile(SmallFireball.class, dir.clone().multiply(speed));
            fb.setIsIncendiary(ignite);
            fb.setYield(0f);
            fb.getPersistentDataContainer().set(Keys.PROJECTILE_BLADE, PersistentDataType.STRING, tag);
            fb.getPersistentDataContainer().set(Keys.PROJECTILE_OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.2f);
        }, () -> streams.remove(p.getUniqueId())));
        return true;
    }

    @Override
    public void passiveTick(AbilityContext ctx) {
        if (BladeItems.FLAME_FIRE_RES.equals(BladeItems.getFlameChoice(ctx.item()))) {
            plugin.abilities().passiveEffect(ctx.player(), PotionEffectType.FIRE_RESISTANCE, 0);
        }
        if (ctx.player().getTicksLived() % 40 < 10) {
            ctx.player().getWorld().spawnParticle(Particle.DUST, ctx.player().getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 90, 31), 1.0f));
        }
    }

    @Override
    public String statusText(Player player) {
        if (rings.containsKey(player.getUniqueId())) return "🔥 Ring active";
        if (streams.containsKey(player.getUniqueId())) return "🔥 Firing";
        return null;
    }

    private static void stop(Map<UUID, BukkitTask> map, UUID id) {
        BukkitTask t = map.remove(id);
        if (t != null) t.cancel();
    }

    // ============================================================ Ultimate — Meteor Storm

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ultimate.radius", 12);
        long duration = ctx.ticks("ultimate.duration", 6);
        long interval = Math.max(4, ctx.i("ultimate.interval-ticks", 12));
        int perWave = ctx.i("ultimate.meteors-per-wave", 3);
        double impact = ctx.d("ultimate.impact-radius", 3.5);
        double damage = ctx.dmg("ultimate.damage", 7.0);
        int fire = (int) ctx.ticks("ultimate.fire-seconds", 4);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.6f);
        trackUlt(p, Fx.repeat(plugin, interval, duration, t -> {
            if (!p.isOnline() || p.isDead()) { stopUltimate(p.getUniqueId()); return; }
            List<LivingEntity> enemies = plugin.combat().targetsNear(p, p.getLocation(), radius, type);
            Collections.shuffle(enemies);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < perWave; i++) {
                Location target;
                if (i < enemies.size()) target = enemies.get(i).getLocation().add(r.nextDouble(-1, 1), 0, r.nextDouble(-1, 1));
                else target = p.getLocation().add(r.nextDouble(-radius, radius), 0, r.nextDouble(-radius, radius));
                target = p.getWorld().getHighestBlockAt(target).getLocation().add(0.5, 1, 0.5);
                if (Math.abs(target.getY() - p.getLocation().getY()) > 12) target.setY(p.getLocation().getY());
                meteor(p, target, impact, damage, fire);
            }
        }, null));
        return true;
    }

    /** A falling meteor (particles only — no block damage), exploding on impact. */
    private void meteor(Player p, Location target, double impact, double damage, int fire) {
        final int fall = 10;
        Location sky = target.clone().add(-4, 14, -4);
        Vector step = target.toVector().subtract(sky.toVector()).multiply(1.0 / fall);
        Fx.repeat(plugin, 1, fall, t -> {
            Location at = sky.clone().add(step.clone().multiply(t));
            at.getWorld().spawnParticle(Particle.FLAME, at, 12, 0.35, 0.35, 0.35, 0.02);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 4, 0.3, 0.3, 0.3, 0.01);
            at.getWorld().spawnParticle(Particle.LAVA, at, 1, 0.2, 0.2, 0.2, 0);
            Fx.dustRing(target.clone().add(0, 0.1, 0), impact * 0.6, Color.fromRGB(255, 90, 31), 1.0f, 12);
        }, () -> {
            if (!p.isOnline()) return;
            target.getWorld().spawnParticle(Particle.EXPLOSION, target, 2, 0.5, 0.3, 0.5, 0);
            target.getWorld().spawnParticle(Particle.FLAME, target, 60, impact * 0.4, 0.3, impact * 0.4, 0.12);
            target.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
            for (LivingEntity e : plugin.combat().targetsNear(p, target.clone().add(0, 0.5, 0), impact, type)) {
                plugin.combat().armouredDamage(p, e, damage);
                if (fire > 0) e.setFireTicks(Math.max(e.getFireTicks(), fire));
                Vector up = e.getLocation().toVector().subtract(target.toVector()).setY(0);
                if (up.lengthSquared() > 1e-4) e.setVelocity(up.normalize().multiply(0.5).setY(0.45));
            }
        });
    }

    @Override
    public void cleanup(Player player) {
        stop(rings, player.getUniqueId());
        stop(streams, player.getUniqueId());
    }

    @Override
    public void shutdown() {
        rings.values().forEach(BukkitTask::cancel);
        streams.values().forEach(BukkitTask::cancel);
        rings.clear();
        streams.clear();
    }
}
