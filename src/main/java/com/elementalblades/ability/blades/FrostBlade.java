package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeType;
import com.elementalblades.combat.StatusManager.FreezeSource;
import com.elementalblades.util.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public final class FrostBlade extends Blade {

    private static final Color ICE = Color.fromRGB(127, 219, 255);

    public FrostBlade(ElementalBlades plugin) {
        super(plugin, BladeType.FROST);
    }

    /** Ability 1 — Glacial Dash: freeze everyone you pass through. */
    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        double seconds = ctx.d("ability1.freeze-seconds", 1.0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.5f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.6f);
        Fx.dash(plugin, p, ctx.d("ability1.dash-power", 1.9), ctx.i("ability1.dash-ticks", 10), ctx.d("ability1.hit-radius", 1.6),
                le -> plugin.combat().canAffect(p, le, type),
                le -> plugin.status().freeze(le, seconds, FreezeSource.FROST_DASH),
                ICE);
        return true;
    }

    /** Ability 2 — Frost Nova: freeze + frost damage within radius. */
    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ability2.radius", 5.0);
        double seconds = ctx.d("ability2.freeze-seconds", 2.5);
        double damage = ctx.dmg("ability2.damage", 6.0);
        Location c = p.getLocation();
        p.getWorld().playSound(c, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);
        p.getWorld().playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.8f);
        // expanding ring
        Fx.repeat(plugin, 1, 8, t -> {
            double r = radius * (t + 1) / 8.0;
            Fx.ring(c.clone().add(0, 0.2, 0), r, Particle.SNOWFLAKE, (int) (r * 14), null);
            Fx.dustRing(c.clone().add(0, 1.0, 0), r, ICE, 1.5f, (int) (r * 10));
        }, null);
        p.getWorld().spawnParticle(Particle.BLOCK, c.clone().add(0, 1, 0), 80, radius / 2, 0.8, radius / 2, 0,
                Material.BLUE_ICE.createBlockData());
        for (LivingEntity t : plugin.combat().targetsNear(p, c.clone().add(0, 1, 0), radius, type)) {
            plugin.combat().damage(p, t, damage, DamageType.FREEZE);
            plugin.status().freeze(t, seconds, FreezeSource.FROST_NOVA);
        }
        return true;
    }

    @Override
    public void passiveTick(AbilityContext ctx) {
        if (ctx.b("passive.dolphins-grace", true))
            plugin.abilities().passiveEffect(ctx.player(), PotionEffectType.DOLPHINS_GRACE, 0);
    }

    /** Every 10th hit: 25% chance freeze 0.5s. Every 20th hit: 0.5% chance freeze 3s. */
    @Override
    public void onMeleeHit(AbilityContext ctx, LivingEntity victim, EntityDamageByEntityEvent event) {
        int hits = plugin.abilities().countHit(ctx.player(), type);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (hits % 20 == 0 && r.nextDouble() < ctx.d("passive.twentieth-hit-chance", 0.005)) {
            if (plugin.combat().canAffect(ctx.player(), victim, type))
                plugin.status().freeze(victim, ctx.d("passive.twentieth-hit-freeze-seconds", 3.0), FreezeSource.FROST_PASSIVE);
            return;
        }
        if (hits % 10 == 0 && r.nextDouble() < ctx.d("passive.tenth-hit-chance", 0.25)) {
            if (plugin.combat().canAffect(ctx.player(), victim, type))
                plugin.status().freeze(victim, ctx.d("passive.tenth-hit-freeze-seconds", 0.5), FreezeSource.FROST_PASSIVE);
        }
    }

    // ------------------------------------------------------------ frost walker (called from move listener)

    public static boolean frostWalkerEnabled(Player p) {
        return p.getPersistentDataContainer().getOrDefault(Keys.FROST_WALKER, PersistentDataType.BOOLEAN, true);
    }

    // ============================================================ Ultimate — Blizzard

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ultimate.radius", 8);
        long duration = ctx.ticks("ultimate.duration", 6);
        double damage = ctx.dmg("ultimate.damage-per-second", 3.0);
        int slowLevel = ctx.i("ultimate.slowness-level", 3);
        double endFreeze = ctx.d("ultimate.final-freeze-seconds", 2.0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 0.5f);
        p.getWorld().playSound(p.getLocation(), Sound.WEATHER_RAIN_ABOVE, 1.5f, 0.5f);
        java.util.Set<LivingEntity> caught = new java.util.HashSet<>();
        trackUlt(p, Fx.repeat(plugin, 2, duration, t -> {
            if (!p.isOnline() || p.isDead()) { stopUltimate(p.getUniqueId()); return; }
            Location c = p.getLocation();
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 40; i++) {
                double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * radius;
                Location pt = c.clone().add(Math.cos(a) * d, r.nextDouble(0, 4), Math.sin(a) * d);
                p.getWorld().spawnParticle(i % 4 == 0 ? Particle.CLOUD : Particle.SNOWFLAKE, pt, 1, 0.3, 0, 0.3, 0.05);
            }
            Fx.dustRing(c.clone().add(0, 0.2, 0), radius, ICE, 1.4f, (int) (radius * 6));
            for (LivingEntity e : plugin.combat().targetsNear(p, c, radius, type)) {
                caught.add(e);
                e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, 30, Math.max(0, slowLevel - 1)));
                e.setFreezeTicks(Math.min(e.getMaxFreezeTicks(), e.getFreezeTicks() + 10));
                if (t % 20 == 0) plugin.combat().damage(p, e, damage, DamageType.FREEZE);
            }
        }, () -> {
            if (!p.isOnline() || endFreeze <= 0) return;
            for (LivingEntity e : caught) {
                if (!e.isValid() || e.isDead() || !e.getWorld().equals(p.getWorld())
                        || e.getLocation().distanceSquared(p.getLocation()) > radius * radius * 1.5) continue;
                if (!plugin.combat().canAffect(p, e, type)) continue;
                plugin.status().freeze(e, endFreeze, FreezeSource.OTHER);
                e.getWorld().spawnParticle(Particle.BLOCK, e.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0, Material.ICE.createBlockData());
            }
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        }));
        return true;
    }

    public static void setFrostWalker(Player p, boolean on) {
        p.getPersistentDataContainer().set(Keys.FROST_WALKER, PersistentDataType.BOOLEAN, on);
    }

    public void frostWalk(Player p) {
        if (!frostWalkerEnabled(p) || p.isFlying() || p.isInsideVehicle()) return;
        if (!p.isOnGround() && p.getVelocity().getY() > 0.1) return;
        int radius = plugin.getConfig().getInt("blades.frost.passive.frost-walker-radius", 3);
        Block feet = p.getLocation().getBlock();
        Block below = feet.getRelative(0, -1, 0);
        if (feet.getType() == Material.WATER) return; // swimming — don't trap the player in ice
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Block b = below.getRelative(dx, 0, dz);
                if (b.getType() != Material.WATER) continue;
                if (!(b.getBlockData() instanceof Levelled lv) || lv.getLevel() != 0) continue;
                if (!b.getRelative(0, 1, 0).getType().isAir()) continue;
                b.setType(Material.FROSTED_ICE);
            }
        }
    }
}
