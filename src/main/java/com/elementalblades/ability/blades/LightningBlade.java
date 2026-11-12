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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LightningBlade extends Blade {

    private static final Color YELLOW = Color.fromRGB(255, 229, 59);
    private final Map<UUID, Long> stormUntil = new HashMap<>();
    private final Map<UUID, Long> lastStrike = new HashMap<>();

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
        long now = System.currentTimeMillis();
        long icd = ctx.i("ability2.internal-cooldown-ticks", 10) * 50L;
        Long last = lastStrike.get(p.getUniqueId());
        if (last != null && now - last < icd) return;
        if (ThreadLocalRandom.current().nextDouble() >= ctx.d("ability2.chance", 0.9)) return;
        if (!plugin.combat().canAffect(p, victim, type)) return;
        lastStrike.put(p.getUniqueId(), now);
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
        return "⚡ Storm " + Text.seconds(until - System.currentTimeMillis());
    }

    @Override
    public void cleanup(Player player) {
        stormUntil.remove(player.getUniqueId());
        lastStrike.remove(player.getUniqueId());
    }
}
