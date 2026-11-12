package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeType;
import com.elementalblades.combat.StatusManager;
import com.elementalblades.util.Keys;
import com.elementalblades.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class VelocityBlade extends Blade {

    private static final Color WHITE = Color.fromRGB(232, 232, 255);
    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public VelocityBlade(ElementalBlades plugin) {
        super(plugin, BladeType.VELOCITY);
    }

    /** Ability 1 — Lightspeed: 2x speed, invisible except flashes of lightning. */
    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        end(p);
        double mult = ctx.d("ability1.speed-multiplier", 2.0);
        long duration = ctx.ticks("ability1.duration", 20);
        int glimpseEvery = ctx.i("ability1.glimpse-every-ticks", 40);
        int glimpseLen = Math.max(1, ctx.i("ability1.glimpse-length-ticks", 3));
        StatusManager.addModifier(p, Attribute.MOVEMENT_SPEED, Keys.MOD_VELOCITY_SPEED, mult - 1.0, AttributeModifier.Operation.ADD_SCALAR);
        hideFromEnemies(p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 2f);
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 60, 0.4, 0.9, 0.4, 0.3);
        Text.send(p, "<white>Lightspeed! <gray>You're invisible for " + duration / 20 + "s.");
        active.put(p.getUniqueId(), Fx.repeat(plugin, 1, duration, t -> {
            if (!p.isOnline() || p.isDead()) { end(p); return; }
            if (t % 2 == 0) {
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.8, 0), 3, 0.25, 0.5, 0.25, 0.02);
            }
            if (glimpseEvery > 0) {
                long phase = t % glimpseEvery;
                if (phase == 0) {
                    showToAll(p);
                    p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.05);
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 40, 0.3, 1.0, 0.3, 0.4);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.35f, 2f);
                } else if (phase == glimpseLen) {
                    hideFromEnemies(p);
                }
            }
        }, () -> finish(p)));
        return true;
    }

    public boolean isActive(Player p) { return active.containsKey(p.getUniqueId()); }

    private boolean seesThrough(Player viewer, Player hidden) {
        return viewer.equals(hidden)
                || (plugin.getConfig().getBoolean("blades.velocity.ability1.teammates-can-see", true)
                    && plugin.combat().sameTeam(viewer.getUniqueId(), hidden.getUniqueId()));
    }

    private void hideFromEnemies(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!seesThrough(other, p)) other.hidePlayer(plugin, p);
        }
    }

    private void showToAll(Player p) {
        for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, p);
    }

    /** New joiners must not see active speedsters. */
    public void onJoin(Player joiner) {
        for (UUID id : active.keySet()) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !seesThrough(joiner, hidden)) joiner.hidePlayer(plugin, hidden);
        }
    }

    /** Called when the speedster attacks (optional reveal). */
    public void onAttack(Player p) {
        if (isActive(p) && plugin.getConfig().getBoolean("blades.velocity.ability1.reveal-on-attack", false)) {
            end(p);
            Text.send(p, "<gray>You attacked and became visible.");
        }
    }

    private void end(Player p) {
        BukkitTask t = active.remove(p.getUniqueId());
        if (t != null) t.cancel();
        finish(p);
    }

    private void finish(Player p) {
        active.remove(p.getUniqueId());
        StatusManager.removeModifier(p, Attribute.MOVEMENT_SPEED, Keys.MOD_VELOCITY_SPEED);
        showToAll(p);
    }

    /** Ability 2 — Sonic Slam: blast everyone around you away. */
    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ability2.radius", 7.0);
        double damage = ctx.dmg("ability2.damage", 6.0);
        double kb = ctx.d("ability2.knockback", 1.6);
        int slowTicks = (int) ctx.ticks("ability2.slowness-seconds", 3);
        Location c = p.getLocation();
        p.getWorld().playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        p.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
        p.getWorld().spawnParticle(Particle.SONIC_BOOM, c.clone().add(0, 1, 0), 1);
        Fx.repeat(plugin, 1, 8, t -> {
            double r = radius * (t + 1) / 8.0;
            Fx.ring(c.clone().add(0, 0.2, 0), r, Particle.CLOUD, (int) (r * 10), null);
            Fx.dustRing(c.clone().add(0, 0.8, 0), r, WHITE, 1.3f, (int) (r * 8));
        }, null);
        for (LivingEntity t : plugin.combat().targetsNear(p, c.clone().add(0, 1, 0), radius, type)) {
            Vector away = t.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (away.lengthSquared() < 1e-4) away = new Vector(0, 0, 1);
            double falloff = 1.0 - Math.min(0.6, t.getLocation().distance(c) / radius * 0.6);
            plugin.combat().armouredDamage(p, t, damage);
            t.setVelocity(away.normalize().multiply(kb * falloff).setY(0.55 * falloff + 0.2));
            if (slowTicks > 0) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1));
        }
        return true;
    }

    @Override
    public void passiveTick(AbilityContext ctx) {
        int lvl = ctx.i("passive.speed-level", 1);
        if (lvl > 0) plugin.abilities().passiveEffect(ctx.player(), PotionEffectType.SPEED, lvl - 1);
    }

    @Override
    public String statusText(Player player) {
        return isActive(player) ? "⚡ Lightspeed" : null;
    }

    @Override
    public void cleanup(Player player) { end(player); }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p);
        }
    }
}
