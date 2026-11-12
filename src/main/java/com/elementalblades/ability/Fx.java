package com.elementalblades.ability;

import com.elementalblades.ElementalBlades;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Particle shapes and the shared dash movement. */
public final class Fx {

    private Fx() {}

    public static void ring(Location center, double radius, Particle particle, int points, Object data) {
        World w = center.getWorld();
        if (w == null) return;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location l = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (data == null) w.spawnParticle(particle, l, 1, 0, 0, 0, 0);
            else w.spawnParticle(particle, l, 1, 0, 0, 0, 0, data);
        }
    }

    public static void dustRing(Location center, double radius, Color color, float size, int points) {
        ring(center, radius, Particle.DUST, points, new Particle.DustOptions(color, size));
    }

    /** Fibonacci sphere of dust particles. */
    public static void sphere(Location center, double radius, Color color, float size, int points) {
        World w = center.getWorld();
        if (w == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double theta = golden * i;
            w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(theta) * r * radius, y * radius, Math.sin(theta) * r * radius),
                    1, 0, 0, 0, 0, dust);
        }
    }

    /**
     * Dash forward in the look direction. Every tick, any valid target within hitRadius is passed to onHit once.
     *
     * @param power      roughly blocks travelled per tick x 2
     * @param ticks      dash duration
     */
    public static BukkitTask dash(ElementalBlades plugin, Player p, double power, int ticks, double hitRadius,
                                  Predicate<LivingEntity> valid, Consumer<LivingEntity> onHit, Color trail) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        final Vector step = dir.multiply(power * 0.55);
        Set<UUID> hit = new HashSet<>();
        plugin.status().noFall(p, (ticks + 40) * 50L);
        p.setVelocity(step.clone().setY(0.2));
        return new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || t++ >= ticks) { cancel(); return; }
                Vector v = p.getVelocity();
                p.setVelocity(step.clone().setY(Math.max(-0.3, Math.min(0.25, v.getY()))));
                Location c = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.DUST, c, 8, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(trail, 1.6f));
                for (var e : p.getWorld().getNearbyEntities(c, hitRadius, hitRadius + 0.5, hitRadius)) {
                    if (e instanceof LivingEntity le && !hit.contains(le.getUniqueId()) && valid.test(le)) {
                        hit.add(le.getUniqueId());
                        onHit.accept(le);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Runs a repeating task for duration ticks, then onEnd. */
    public static BukkitTask repeat(ElementalBlades plugin, long period, long duration, Consumer<Long> each, Runnable onEnd) {
        return new org.bukkit.scheduler.BukkitRunnable() {
            long elapsed = 0;
            @Override public void run() {
                if (elapsed >= duration) {
                    cancel();
                    if (onEnd != null) onEnd.run();
                    return;
                }
                each.accept(elapsed);
                elapsed += period;
            }
        }.runTaskTimer(plugin, 0L, period);
    }

    public static void later(ElementalBlades plugin, long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }
}
