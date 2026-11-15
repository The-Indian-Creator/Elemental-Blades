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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LifeBlade extends Blade {

    private static final Color GREEN = Color.fromRGB(76, 255, 106);
    private final Map<UUID, BukkitTask> surges = new HashMap<>();
    private final Set<UUID> bonusAbsorption = new HashSet<>();
    /** Sanctuary cheat-death window (epoch millis). */
    private final Map<UUID, Long> undyingUntil = new HashMap<>();

    public LifeBlade(ElementalBlades plugin) {
        super(plugin, BladeType.LIFE);
        // Remove the bonus absorption cap once the absorption hearts are used up.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Iterator<UUID> it = bonusAbsorption.iterator(); it.hasNext(); ) {
                Player p = Bukkit.getPlayer(it.next());
                if (p == null) { it.remove(); continue; }
                if (p.getAbsorptionAmount() <= 0.01) {
                    StatusManager.removeModifier(p, Attribute.MAX_ABSORPTION, Keys.MOD_LIFE_ABSORB);
                    it.remove();
                }
            }
        }, 20L, 20L);
    }

    /** Ability 1 — Draining Dash. */
    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        double drain = ctx.dmg("ability1.drain", 2.0);
        double chance = ctx.d("ability1.absorption-chance", 0.5);
        double amount = ctx.d("ability1.absorption-amount", 2.0);
        double cap = ctx.d("ability1.max-bonus-absorption", 10.0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1.6f);
        Fx.dash(plugin, p, ctx.d("ability1.dash-power", 1.9), ctx.i("ability1.dash-ticks", 10), ctx.d("ability1.hit-radius", 1.6),
                le -> plugin.combat().canAffect(p, le, type),
                le -> {
                    plugin.combat().trueDamage(p, le, drain);
                    le.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, le.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.1);
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0);
                    if (ThreadLocalRandom.current().nextDouble() < chance) addAbsorption(p, amount, cap);
                }, GREEN);
        return true;
    }

    private void addAbsorption(Player p, double amount, double cap) {
        AttributeInstance inst = p.getAttribute(Attribute.MAX_ABSORPTION);
        if (inst == null) return;
        double target = Math.min(p.getAbsorptionAmount() + amount, Math.max(cap, p.getAbsorptionAmount()));
        AttributeModifier existing = inst.getModifier(Keys.MOD_LIFE_ABSORB);
        double baseWithoutOurs = inst.getValue() - (existing == null ? 0 : existing.getAmount());
        double needed = Math.max(0, target - baseWithoutOurs);
        StatusManager.addModifier(p, Attribute.MAX_ABSORPTION, Keys.MOD_LIFE_ABSORB, needed, AttributeModifier.Operation.ADD_NUMBER);
        p.setAbsorptionAmount(Math.min(target, inst.getValue()));
        bonusAbsorption.add(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2f);
    }

    /** Ability 2 — Vitality Surge: max health raised to 15 hearts for a few seconds. */
    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) return false;
        endSurge(p);
        double target = ctx.d("ability2.max-health", 30.0);
        double extra = Math.max(0, target - inst.getValue());
        StatusManager.addModifier(p, Attribute.MAX_HEALTH, Keys.MOD_LIFE_HEALTH, extra, AttributeModifier.Operation.ADD_NUMBER);
        if (ctx.b("ability2.heal-to-full", true)) p.setHealth(inst.getValue());
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.4f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
        long duration = ctx.ticks("ability2.duration", 5);
        surges.put(p.getUniqueId(), Fx.repeat(plugin, 5, duration, t ->
                        Fx.dustRing(p.getLocation().add(0, 0.2 + (t % 40) / 20.0, 0), 0.8, GREEN, 1.2f, 12),
                () -> { surges.remove(p.getUniqueId()); removeSurgeModifier(p); }));
        Text.send(p, "<green>Vitality Surge! <gray>15 hearts for " + (duration / 20) + "s.");
        return true;
    }

    private void endSurge(Player p) {
        BukkitTask t = surges.remove(p.getUniqueId());
        if (t != null) t.cancel();
        removeSurgeModifier(p);
    }

    private void removeSurgeModifier(Player p) {
        StatusManager.removeModifier(p, Attribute.MAX_HEALTH, Keys.MOD_LIFE_HEALTH);
        AttributeInstance inst = p.getAttribute(Attribute.MAX_HEALTH);
        if (inst != null && !p.isDead() && p.getHealth() > inst.getValue()) p.setHealth(inst.getValue());
    }


    // ------------------------------------------------------------ passive

    public boolean isImmune(PotionEffectType type) {
        List<String> list = plugin.getConfig().getStringList("blades.life.passive.immune-effects");
        if (list.isEmpty()) list = List.of("WITHER", "POISON", "INSTANT_DAMAGE");
        String key = type.getKey().getKey().toUpperCase(Locale.ROOT);
        return list.stream().anyMatch(s -> s.equalsIgnoreCase(key));
    }

    @Override
    public void passiveTick(AbilityContext ctx) {
        Player p = ctx.player();
        // Strip immune effects that slipped through (e.g. applied before switching to the blade)
        for (var pe : new ArrayList<>(p.getActivePotionEffects())) {
            if (isImmune(pe.getType())) p.removePotionEffect(pe.getType());
        }
    }

    /** Every N hits grants a Healing or Poison potion. */
    @Override
    public void onMeleeHit(AbilityContext ctx, LivingEntity victim, EntityDamageByEntityEvent event) {
        int every = Math.max(1, ctx.i("passive.hits-per-potion", 10));
        int hits = plugin.abilities().countHit(ctx.player(), type);
        if (hits % every != 0) return;
        boolean healing = ThreadLocalRandom.current().nextDouble() < ctx.d("passive.healing-chance", 0.5);
        ItemStack potion = ItemStack.of(ctx.b("passive.splash", true) ? Material.SPLASH_POTION : Material.POTION);
        potion.editMeta(PotionMeta.class, m -> m.setBasePotionType(healing ? PotionType.STRONG_HEALING : PotionType.STRONG_POISON));
        Player p = ctx.player();
        p.getInventory().addItem(potion).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        Text.send(p, healing ? "<green>Your Life Blade granted a Healing potion." : "<dark_green>Your Life Blade granted a Poison potion.");
    }

    // ============================================================ Ultimate — Sanctuary

    private static final List<String> NEGATIVE = List.of("POISON", "WITHER", "SLOWNESS", "WEAKNESS", "BLINDNESS",
            "DARKNESS", "NAUSEA", "MINING_FATIGUE", "HUNGER", "LEVITATION", "INSTANT_DAMAGE", "BAD_OMEN", "UNLUCK");

    @Override
    public boolean ultimate(AbilityContext ctx) {
        Player p = ctx.player();
        double radius = ctx.d("ultimate.radius", 10);
        int regenTicks = (int) ctx.ticks("ultimate.regeneration-seconds", 8);
        int regenLevel = ctx.i("ultimate.regeneration-level", 2);
        List<Player> healed = new ArrayList<>();
        healed.add(p);
        if (ctx.b("ultimate.heal-teammates", true)) {
            for (Player other : p.getWorld().getPlayers()) {
                if (!other.equals(p) && other.getLocation().distanceSquared(p.getLocation()) <= radius * radius
                        && plugin.combat().sameTeam(p.getUniqueId(), other.getUniqueId())) healed.add(other);
            }
        }
        for (Player h : healed) {
            AttributeInstance max = h.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) h.setHealth(max.getValue());
            h.setFireTicks(0);
            if (ctx.b("ultimate.cleanse", true)) {
                for (var pe : new ArrayList<>(h.getActivePotionEffects())) {
                    if (NEGATIVE.contains(pe.getType().getKey().getKey().toUpperCase(Locale.ROOT))) h.removePotionEffect(pe.getType());
                }
            }
            if (regenLevel > 0) h.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.REGENERATION, regenTicks, regenLevel - 1));
            h.getWorld().spawnParticle(Particle.HEART, h.getLocation().add(0, 2, 0), 8, 0.5, 0.3, 0.5, 0);
            if (!h.equals(p)) Text.send(h, "<green>❤ " + Text.esc(p.getName()) + "'s Sanctuary healed you!");
        }
        long undying = (long) (ctx.d("ultimate.cheat-death-seconds", 10) * 1000);
        if (undying > 0) undyingUntil.put(p.getUniqueId(), System.currentTimeMillis() + undying);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        org.bukkit.Location c = p.getLocation();
        Fx.repeat(plugin, 1, 20, t -> {
            double r = radius * (t + 1) / 20.0;
            Fx.dustRing(c.clone().add(0, 0.2, 0), r, GREEN, 1.5f, (int) (r * 8));
            Fx.ring(c.clone().add(0, 0.4 + t * 0.15, 0), 1.2, Particle.HAPPY_VILLAGER, 8, null);
        }, null);
        if (undying > 0) trackUlt(p, Fx.repeat(plugin, 5, undying / 50, t -> {
            if (p.isOnline() && undyingUntil.containsKey(p.getUniqueId()))
                Fx.dustRing(p.getLocation().add(0, 2.3, 0), 0.4, Color.fromRGB(255, 215, 80), 0.9f, 8); // halo
        }, null));
        return true;
    }

    /** Called when the player would die. @return true if Sanctuary saved them. */
    public boolean cheatDeath(Player p) {
        Long until = undyingUntil.remove(p.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return false;
        stopUltimate(p.getUniqueId());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double hp = plugin.getConfig().getDouble("blades.life.ultimate.cheat-death-health", 10);
        p.setHealth(Math.max(1, Math.min(max != null ? max.getValue() : 20, hp)));
        p.setFireTicks(0);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.4);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        Text.send(p, "<green><bold>Sanctuary saved you from death!");
        return true;
    }

    @Override
    public String statusText(Player player) {
        Long until = undyingUntil.get(player.getUniqueId());
        String surge = surges.containsKey(player.getUniqueId()) ? "❤ Surge active" : null;
        if (until == null || until < System.currentTimeMillis()) return surge;
        String sanct = "✚ Undying " + Text.seconds(until - System.currentTimeMillis());
        return surge == null ? sanct : surge + "  " + sanct;
    }

    @Override
    public void cleanup(Player player) {
        undyingUntil.remove(player.getUniqueId());
        endSurge(player);
        StatusManager.removeModifier(player, Attribute.MAX_ABSORPTION, Keys.MOD_LIFE_ABSORB);
        bonusAbsorption.remove(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(surges.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) endSurge(p);
        }
        for (UUID id : bonusAbsorption) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) StatusManager.removeModifier(p, Attribute.MAX_ABSORPTION, Keys.MOD_LIFE_ABSORB);
        }
    }
}
