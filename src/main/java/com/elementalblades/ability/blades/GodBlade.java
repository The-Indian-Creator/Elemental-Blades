package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

/** The God Blade — the most overpowered blade in the game. Everything is cranked to the max. */
public final class GodBlade extends Blade {

    private static final Color GOLD = Color.fromRGB(255, 215, 0);
    private final Map<UUID, Long> invulnUntil = new java.util.HashMap<>();

    public GodBlade(ElementalBlades plugin) {
        super(plugin, BladeType.GOD);
    }

    // ---------------------------------------------- passive: permanent god buffs

    @Override
    public void passiveTick(AbilityContext ctx) {
        Player p = ctx.player();
        passive(p, PotionEffectType.STRENGTH, ctx.i("passive.strength-level", 2));
        passive(p, PotionEffectType.RESISTANCE, ctx.i("passive.resistance-level", 2));
        passive(p, PotionEffectType.REGENERATION, ctx.i("passive.regeneration-level", 2));
        passive(p, PotionEffectType.SPEED, ctx.i("passive.speed-level", 2));
        passive(p, PotionEffectType.HASTE, ctx.i("passive.haste-level", 2));
        if (ctx.b("passive.fire-resistance", true)) passive(p, PotionEffectType.FIRE_RESISTANCE, 1);
        if (ctx.b("passive.saturation", true)) passive(p, PotionEffectType.SATURATION, 1);
        if (ctx.b("passive.night-vision", true)) passive(p, PotionEffectType.NIGHT_VISION, 1);
        if (ctx.b("passive.jump-boost", true)) passive(p, PotionEffectType.JUMP_BOOST, 2);
    }

    private void passive(Player p, PotionEffectType type, int level) {
        if (level > 0) plugin.abilities().passiveEffect(p, type, level - 1);
    }

    // ---------------------------------------------- A1: Obliterate

    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ability1.radius", 15);
        double damage = ctx.dmg("ability1.damage", 50);
        double kb = ctx.d("ability1.knockback", 2.5);
        World w = p.getWorld();
        Location c = p.getLocation();
        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.6f);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
        Fx.repeat(plugin, 1, 12, t -> {
            double r = radius * (t + 1) / 12.0;
            Fx.dustRing(c.clone().add(0, 0.3, 0), r, GOLD, 2f, (int) (r * 10));
            w.spawnParticle(Particle.EXPLOSION, c.clone().add((Math.random() - 0.5) * radius, 1, (Math.random() - 0.5) * radius), 1);
        }, null);
        for (LivingEntity e : plugin.combat().targetsNear(p, c, radius, type)) {
            w.strikeLightningEffect(e.getLocation());
            w.spawnParticle(Particle.EXPLOSION_EMITTER, e.getLocation(), 1);
            plugin.combat().trueDamage(p, e, damage);
            Vector up = e.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (up.lengthSquared() < 1e-4) up = new Vector(0, 1, 0);
            e.setVelocity(up.normalize().multiply(kb).setY(1.2));
            e.setFireTicks(200);
        }
        return true;
    }

    // ---------------------------------------------- A2: Divinity

    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (max != null) p.setHealth(max.getValue());
        p.setFireTicks(0);
        for (var pe : new java.util.ArrayList<>(p.getActivePotionEffects()))
            if (isBad(pe.getType())) p.removePotionEffect(pe.getType());
        int dur = (int) ctx.ticks("ability2.buff-seconds", 10);
        p.setAbsorptionAmount(ctx.d("ability2.absorption-hearts", 10) * 2);
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, dur, 4, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, ctx.i("ability2.resistance-level", 4) - 1, false, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, ctx.i("ability2.strength-level", 3) - 1, false, true, true));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.5f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 0.8f);
        Location c = p.getLocation();
        Fx.repeat(plugin, 1, 20, t -> Fx.dustRing(c.clone().add(0, 0.2 + t * 0.12, 0), 1.5, GOLD, 2f, 20), null);
        double igniteR = ctx.d("ability2.ignite-radius", 10);
        for (LivingEntity e : plugin.combat().targetsNear(p, c, igniteR, type)) e.setFireTicks(160);
        return true;
    }

    private boolean isBad(PotionEffectType t) {
        String k = t.getKey().getKey();
        return k.contains("poison") || k.contains("wither") || k.contains("slow") || k.contains("weak")
                || k.contains("blind") || k.contains("darkness") || k.contains("nausea") || k.contains("hunger")
                || k.contains("mining_fatigue") || k.contains("levitation");
    }

    // ---------------------------------------------- Ultimate: Apocalypse

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ultimate.radius", 20);
        long duration = ctx.ticks("ultimate.duration", 8);
        double dps = ctx.dmg("ultimate.damage-per-wave", 20);
        World w = p.getWorld();
        Location c = p.getLocation().clone();
        invulnUntil.put(p.getUniqueId(), System.currentTimeMillis() + duration * 50 + 500);
        plugin.status().noFall(p, duration * 50 + 1000);
        w.playSound(c, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.5f);
        w.playSound(c, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);
        trackUlt(p, Fx.repeat(plugin, 10, duration, t -> {
            if (!p.isOnline() || p.isDead()) { stopUltimate(p.getUniqueId()); return; }
            Location centre = p.getLocation();
            for (int i = 0; i < 6; i++) {
                Location strike = centre.clone().add((Math.random() - 0.5) * radius * 2, 0, (Math.random() - 0.5) * radius * 2);
                w.strikeLightningEffect(strike);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, strike, 1);
            }
            Fx.dustRing(centre.clone().add(0, 0.3, 0), radius, GOLD, 2f, (int) (radius * 8));
            for (LivingEntity e : plugin.combat().targetsNear(p, centre, radius, type)) {
                plugin.combat().trueDamage(p, e, dps);
                e.setFireTicks(100);
                e.getWorld().spawnParticle(Particle.FLAME, e.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.1);
            }
        }, () -> invulnUntil.put(p.getUniqueId(), System.currentTimeMillis() + 300)));
        return true;
    }

    /** True while Apocalypse is running — the holder can't be hurt. */
    public boolean isInvulnerable(Player p) {
        Long u = invulnUntil.get(p.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    @Override
    public void cleanup(Player player) {
        invulnUntil.remove(player.getUniqueId());
        stopUltimate(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        stopAllUltimates();
        invulnUntil.clear();
    }
}
