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

    @Override
    public String statusText(Player player) {
        return surges.containsKey(player.getUniqueId()) ? "❤ Surge active" : null;
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

    @Override
    public void cleanup(Player player) {
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
