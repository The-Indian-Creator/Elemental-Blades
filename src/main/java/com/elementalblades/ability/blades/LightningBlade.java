package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LightningBlade extends Blade {

    private static final Color YELLOW = Color.fromRGB(255, 229, 59);
    private final Map<UUID, Long> stormUntil = new HashMap<>();
    private final Map<UUID, Long> lastStrike = new HashMap<>();
    private final Map<UUID, Integer> stormHits = new HashMap<>();
    private final Map<UUID, BukkitTask> thunderGod = new HashMap<>();

    public LightningBlade(ElementalBlades plugin) {
        super(plugin, BladeType.LIGHTNING);
    }

    /** Ability 1 — Static Shockwave: strip potion effects, abilities and weapons. */
    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ability1.radius", 6.0);
        double seconds = ctx.d("ability1.disable-seconds", 5);
        boolean clear = ctx.b("ability1.clear-potion-effects", true);
        Location c = p.getLocation();
        World w = p.getWorld();
        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
        w.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 2f);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            w.strikeLightningEffect(c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
        Fx.repeat(plugin, 1, 10, t -> {
            double r = radius * (t + 1) / 10.0;
            Fx.ring(c.clone().add(0, 0.3, 0), r, Particle.ELECTRIC_SPARK, (int) (r * 16), null);
            Fx.dustRing(c.clone().add(0, 1.0, 0), r, YELLOW, 1.4f, (int) (r * 8));
        }, null);
        int hit = 0;
        for (LivingEntity t : plugin.combat().targetsNear(p, c.clone().add(0, 1, 0), radius, type)) {
            hit++;
            if (t instanceof Player victim) {
                plugin.status().shock(victim, seconds, clear);
                Text.send(victim, "<yellow>⚡ Shocked by " + Text.esc(p.getName()) + "! <gray>Potions, abilities and weapons disabled for "
                        + (int) seconds + "s.");
            } else if (clear) {
                for (PotionEffect pe : new ArrayList<>(t.getActivePotionEffects())) t.removePotionEffect(pe.getType());
            }
            w.spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
        }
        if (hit == 0) p.sendActionBar(net.kyori.adventure.text.Component.text("Shockwave hit nobody",
                net.kyori.adventure.text.format.NamedTextColor.GRAY));
        return true;
    }

    /** Ability 2 — Storm Strikes (timed) or always-on, depending on config. */
    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        if (alwaysOn()) {
            Text.send(p, "<yellow>Storm Strikes is always active on this server.");
            return false;
        }
        long duration = ctx.ticks("ability2.duration", 10);
        stormUntil.put(p.getUniqueId(), System.currentTimeMillis() + duration * 50);
        stormHits.remove(p.getUniqueId());
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.2f);
        Fx.repeat(plugin, 4, duration, t -> {
            if (p.isOnline()) p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.05);
        }, null);
        Text.send(p, "<yellow>⚡ Storm Strikes active for " + duration / 20 + "s!");
        return true;
    }

    private boolean alwaysOn() {
        return "ALWAYS".equalsIgnoreCase(plugin.getConfig().getString("blades.lightning.ability2.mode", "TIMED"));
    }

    public boolean stormActive(Player p) {
        if (alwaysOn()) return true;
        Long until = stormUntil.get(p.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    @Override
    public void onMeleeHit(AbilityContext ctx, LivingEntity victim, EntityDamageByEntityEvent event) {
        Player p = ctx.player();
        if (!stormActive(p)) return;
        if (!plugin.combat().canAffect(p, victim, type)) return;
        if ("CHANCE".equalsIgnoreCase(ctx.s("ability2.trigger", "EVERY_N"))) {
            long now = System.currentTimeMillis();
            long icd = ctx.i("ability2.internal-cooldown-ticks", 10) * 50L;
            Long last = lastStrike.get(p.getUniqueId());
            if (last != null && now - last < icd) return;
            if (ThreadLocalRandom.current().nextDouble() >= ctx.d("ability2.chance", 0.9)) return;
            lastStrike.put(p.getUniqueId(), now);
        } else {
            int every = Math.max(1, ctx.i("ability2.every-hits", 3));
            int n = stormHits.merge(p.getUniqueId(), 1, Integer::sum);
            if (n < every) {
                // charge-up feedback: little sparks on the blade, growing each hit
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 6 * n, 0.3, 0.5, 0.3, 0.1);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f + 0.3f * n);
                return;
            }
            stormHits.put(p.getUniqueId(), 0);
        }
        double fraction = Math.min(0.95, ctx.d("ability2.health-fraction", 0.35) * ctx.damageMultiplier());
        double minLeft = Math.max(0.5, ctx.d("ability2.min-health-left", 1.0));
        // Apply next tick so the normal sword damage resolves first; armour is ignored and this never kills.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isValid() || victim.isDead()) return;
            victim.getWorld().strikeLightningEffect(victim.getLocation());
            double h = victim.getHealth();
            if (h <= minLeft) return;
            double newHealth = Math.max(minLeft, h - h * fraction);
            victim.setHealth(newHealth);
            victim.playHurtAnimation(0f);
            victim.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, victim.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.2);
        });
    }

    @Override
    public void passiveTick(AbilityContext ctx) {
        Player p = ctx.player();
        World w = p.getWorld();
        int level = 0;
        if (w.isThundering()) level = ctx.i("passive.thunder-strength", 2);
        else if (w.hasStorm()) level = ctx.i("passive.rain-strength", 1);
        if (level > 0) plugin.abilities().passiveEffect(p, PotionEffectType.STRENGTH, level - 1);
        else plugin.abilities().removePassiveEffect(p, PotionEffectType.STRENGTH);
    }

    @Override
    public String statusText(Player player) {
        if (alwaysOn()) return null;
        Long until = stormUntil.get(player.getUniqueId());
        if (until == null || until <= System.currentTimeMillis()) return null;
        int n = stormHits.getOrDefault(player.getUniqueId(), 0);
        int every = Math.max(1, plugin.getConfig().getInt("blades.lightning.ability2.every-hits", 3));
        String pips = "CHANCE".equalsIgnoreCase(plugin.getConfig().getString("blades.lightning.ability2.trigger", "EVERY_N"))
                ? "" : " " + "⚡".repeat(n) + "·".repeat(Math.max(0, every - 1 - n));
        return "⚡ Storm " + Text.seconds(until - System.currentTimeMillis()) + pips;
    }

    // ============================================================ Ultimate — Thunder God

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ultimate.radius", 16);
        if (plugin.combat().targetsNear(p, p.getLocation(), radius, type).isEmpty()) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("No enemies in range for Thunder God",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
            return false;
        }
        stopUlt(p);
        long duration = ctx.ticks("ultimate.duration", 8);
        int targets = ctx.i("ultimate.targets-per-strike", 3);
        double damage = ctx.dmg("ultimate.damage", 6.0);
        long interval = Math.max(5, ctx.ticks("ultimate.interval-seconds", 1.0));
        int speed = ctx.i("ultimate.speed-level", 2), strength = ctx.i("ultimate.strength-level", 1);
        if (speed > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) duration, speed - 1, false, true, true));
        if (strength > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, (int) duration, strength - 1, false, true, true));
        World w = p.getWorld();
        w.strikeLightningEffect(p.getLocation());
        w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 0.7f);
        thunderGod.put(p.getUniqueId(), Fx.repeat(plugin, 1, duration, t -> {
            if (!p.isOnline() || p.isDead()) { stopUlt(p); return; }
            // crackling aura
            Fx.dustRing(p.getLocation().add(0, 0.2 + (t % 20) / 10.0, 0), 1.0, YELLOW, 1.2f, 10);
            if (t % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 8, 0.5, 0.9, 0.5, 0.15);
            if (t % interval != 0) return;
            List<LivingEntity> near = plugin.combat().targetsNear(p, p.getLocation(), radius, type);
            near.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
            for (LivingEntity e : near.subList(0, Math.min(targets, near.size()))) {
                w.strikeLightningEffect(e.getLocation());
                w.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 40, 0.3, 0.8, 0.3, 0.3);
                plugin.combat().trueDamage(p, e, damage);
            }
        }, () -> thunderGod.remove(p.getUniqueId())));
        return true;
    }

    private void stopUlt(Player p) {
        BukkitTask t = thunderGod.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    @Override
    public void cleanup(Player player) {
        stormUntil.remove(player.getUniqueId());
        lastStrike.remove(player.getUniqueId());
        stormHits.remove(player.getUniqueId());
        stopUlt(player);
    }

    @Override
    public void shutdown() {
        thunderGod.values().forEach(BukkitTask::cancel);
        thunderGod.clear();
    }
}
