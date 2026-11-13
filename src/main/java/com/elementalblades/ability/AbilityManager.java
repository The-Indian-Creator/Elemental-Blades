package com.elementalblades.ability;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.blades.*;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Activates abilities, tracks cooldowns, runs passives and the action bar. */
public final class AbilityManager {

    private static final int PASSIVE_DURATION = 60; // ticks; refreshed every 10 ticks

    private final ElementalBlades plugin;
    private final Map<BladeType, Blade> blades = new EnumMap<>(BladeType.class);
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BladeType> lastHeld = new HashMap<>();
    private final Map<UUID, Map<PotionEffectType, Integer>> passiveEffects = new HashMap<>();
    private final Map<UUID, EnumMap<BladeType, Integer>> hitCounters = new HashMap<>();
    /** Ultimate charge, 0..1 (CHARGE mode). Shared across blades. */
    private final Map<UUID, Double> ultCharge = new HashMap<>();
    /** No charge is gained until this time (while/just after an ultimate runs). */
    private final Map<UUID, Long> ultLockout = new HashMap<>();

    public AbilityManager(ElementalBlades plugin) {
        this.plugin = plugin;
        register(new FlameBlade(plugin));
        register(new FrostBlade(plugin));
        register(new LifeBlade(plugin));
        register(new SilenceBlade(plugin));
        register(new LightningBlade(plugin));
        register(new VelocityBlade(plugin));
        register(new WaterBlade(plugin));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void register(Blade b) { blades.put(b.type(), b); }

    public Blade blade(BladeType t) { return blades.get(t); }

    @SuppressWarnings("unchecked")
    public <T extends Blade> T blade(Class<T> cls) {
        for (Blade b : blades.values()) if (cls.isInstance(b)) return (T) b;
        throw new IllegalArgumentException(cls.getName());
    }

    public AbilityContext context(Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        BladeType t = BladeItems.getType(item);
        return t == null ? null : new AbilityContext(plugin, p, item, t, BladeItems.getLevel(item));
    }

    public boolean worldDisabled(Player p) {
        return plugin.getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName());
    }

    // ------------------------------------------------------------ activation

    /** @param slot 1 or 2 */
    public void activate(Player p, int slot) {
        AbilityContext ctx = context(p);
        if (ctx == null) return;
        if (!p.hasPermission("elementalblades.use")) return;
        if (worldDisabled(p)) { p.sendActionBar(Component.text("Blade abilities are disabled in this world", NamedTextColor.RED)); return; }
        if (plugin.status().isShocked(p)) {
            p.sendActionBar(Component.text("⚡ You are shocked! Abilities disabled for "
                    + Text.seconds(plugin.status().shockRemaining(p)), NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        if (plugin.status().isFrameLocked(p)) {
            p.sendActionBar(Component.text("▣ You're frame-locked!", NamedTextColor.WHITE));
            return;
        }
        Blade blade = blades.get(ctx.type());
        long remaining = cooldownRemaining(p, ctx.type(), slot);
        if (remaining > 0 && !blade.bypassCooldown(p, slot)) {
            p.sendActionBar(Component.text((slot == 1 ? ctx.type().ability1Name() : ctx.type().ability2Name())
                    + " ready in " + Text.seconds(remaining), NamedTextColor.RED));
            return;
        }
        boolean fired;
        try {
            fired = slot == 1 ? blade.ability1(ctx) : blade.ability2(ctx);
        } catch (Exception ex) {
            plugin.getLogger().severe("Ability error (" + ctx.type() + " #" + slot + "): " + ex);
            ex.printStackTrace();
            fired = false;
        }
        if (fired) setCooldown(p, ctx, slot);
    }

    // ------------------------------------------------------------ ultimates

    private org.bukkit.configuration.file.FileConfiguration cfg() { return plugin.getConfig(); }

    public boolean ultimatesEnabled() { return cfg().getBoolean("ultimate.enabled", true); }

    private boolean cooldownMode() { return "COOLDOWN".equalsIgnoreCase(cfg().getString("ultimate.mode", "CHARGE")); }

    public double ultCharge(Player p) { return ultCharge.getOrDefault(p.getUniqueId(), 0.0); }

    public boolean ultReady(Player p, BladeType t) {
        if (p.hasPermission("elementalblades.nocooldown")) return true;
        return cooldownMode() ? cooldownRemaining(p, t, 3) <= 0 : ultCharge(p) >= 1.0;
    }

    /** Adds charge for damage the player dealt while holding a blade. */
    public void addUltCharge(Player p, double damage, boolean victimIsPlayer) {
        if (!ultimatesEnabled() || cooldownMode() || damage <= 0) return;
        Long lock = ultLockout.get(p.getUniqueId());
        if (lock != null && lock > System.currentTimeMillis()) return;
        double required = Math.max(1, cfg().getDouble("ultimate.charge-required", 40));
        double mult = victimIsPlayer ? 1.0 : cfg().getDouble("ultimate.mob-damage-multiplier", 0.5);
        addRawCharge(p, damage * mult / required);
    }

    /** Kill bonus (fraction of a full bar). */
    public void addKillCharge(Player p, boolean victimIsPlayer) {
        if (!ultimatesEnabled() || cooldownMode()) return;
        double bonus = cfg().getDouble(victimIsPlayer ? "ultimate.kill-bonus" : "ultimate.mob-kill-bonus", victimIsPlayer ? 0.30 : 0.0);
        if (bonus > 0) addRawCharge(p, bonus);
    }

    private void addRawCharge(Player p, double amount) {
        double before = ultCharge(p);
        double after = Math.min(1.0, before + amount);
        ultCharge.put(p.getUniqueId(), after);
        if (before < 1.0 && after >= 1.0) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.6f);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.8f);
            BladeType held = BladeItems.held(p);
            Text.send(p, "<gold><bold>ULTIMATE READY!</bold> <gray>Press <white>[" + ultKeyName() + "]</white>"
                    + (held != null ? " to use " + held.ultimateName() : "") + ".");
        }
    }

    public void resetUltCharge(Player p) { ultCharge.remove(p.getUniqueId()); }

    /** Text for the ultimate key under the current control scheme. */
    public String ultKeyName() {
        return "SNEAK_SWAP".equalsIgnoreCase(cfg().getString("controls.ultimate-key", "SWAP_HAND")) ? "Sneak + F" : "F";
    }

    public void activateUltimate(Player p) {
        AbilityContext ctx = context(p);
        if (ctx == null || !p.hasPermission("elementalblades.use")) return;
        if (!ultimatesEnabled()) { p.sendActionBar(Component.text("Ultimates are disabled on this server", NamedTextColor.RED)); return; }
        if (worldDisabled(p)) { p.sendActionBar(Component.text("Blade abilities are disabled in this world", NamedTextColor.RED)); return; }
        if (plugin.status().isShocked(p)) {
            p.sendActionBar(Component.text("⚡ You are shocked! Abilities disabled", NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        if (plugin.status().isFrameLocked(p)) {
            p.sendActionBar(Component.text("▣ You're frame-locked!", NamedTextColor.WHITE));
            return;
        }
        if (!ultReady(p, ctx.type())) {
            String msg = cooldownMode()
                    ? ctx.type().ultimateName() + " ready in " + Text.seconds(cooldownRemaining(p, ctx.type(), 3))
                    : ctx.type().ultimateName() + " charging: " + (int) (ultCharge(p) * 100) + "% — deal damage to charge it";
            p.sendActionBar(Component.text(msg, NamedTextColor.RED));
            return;
        }
        boolean fired;
        try {
            fired = blades.get(ctx.type()).ultimate(ctx);
        } catch (Exception ex) {
            plugin.getLogger().severe("Ultimate error (" + ctx.type() + "): " + ex);
            ex.printStackTrace();
            fired = false;
        }
        if (!fired) return;
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.4f);
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(ctx.type().ultimateName().toUpperCase(), ctx.type().textColor()),
                Component.text("ULTIMATE", NamedTextColor.GOLD)));
        if (cooldownMode()) {
            if (!p.hasPermission("elementalblades.nocooldown")) {
                long ms = (long) (cfg().getDouble("ultimate.cooldown", 120) * ctx.cooldownMultiplier() * 1000);
                cooldowns.put(key(p.getUniqueId(), ctx.type(), 3), System.currentTimeMillis() + ms);
            }
        } else if (!p.hasPermission("elementalblades.nocooldown")) {
            ultCharge.put(p.getUniqueId(), 0.0);
        }
        double lockSec = ctx.d("ultimate.duration", 6) + cfg().getDouble("ultimate.lockout-seconds", 3);
        ultLockout.put(p.getUniqueId(), System.currentTimeMillis() + (long) (lockSec * 1000));
    }

    private Component ultStatus(Player p, AbilityContext ctx) {
        if (cooldownMode()) {
            long rem = cooldownRemaining(p, ctx.type(), 3);
            return rem <= 0 ? Component.text("ULT ✔", NamedTextColor.GOLD)
                    : Component.text("ULT " + Text.seconds(rem), NamedTextColor.DARK_RED);
        }
        double c = ultCharge(p);
        if (c >= 1.0 || p.hasPermission("elementalblades.nocooldown")) {
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            return Component.text("✦ ULT READY [" + ultKeyName() + "]", blink ? NamedTextColor.GOLD : NamedTextColor.YELLOW);
        }
        return Component.text("ULT " + (int) (c * 100) + "%", NamedTextColor.DARK_AQUA);
    }

    // ------------------------------------------------------------ cooldowns

    private String key(UUID id, BladeType t, int slot) { return id + ":" + t.id() + ":" + slot; }

    public long cooldownRemaining(Player p, BladeType t, int slot) {
        Long until = cooldowns.get(key(p.getUniqueId(), t, slot));
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void setCooldown(Player p, AbilityContext ctx, int slot) {
        if (p.hasPermission("elementalblades.nocooldown")) return;
        double base = ctx.d("ability" + slot + ".cooldown", 30);
        long ms = (long) (base * ctx.cooldownMultiplier() * 1000);
        cooldowns.put(key(p.getUniqueId(), ctx.type(), slot), System.currentTimeMillis() + ms);
    }

    public void resetCooldowns(UUID id) {
        cooldowns.keySet().removeIf(k -> k.startsWith(id.toString()));
    }

    // ------------------------------------------------------------ hit counters

    /** Increments and returns the holder's hit count for this blade. */
    public int countHit(Player p, BladeType t) {
        return hitCounters.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(BladeType.class)).merge(t, 1, Integer::sum);
    }

    // ------------------------------------------------------------ passive potion effects

    /** Gives a short, constantly refreshed passive potion effect (removed shortly after unholding). */
    public void passiveEffect(Player p, PotionEffectType type, int amplifier) {
        PotionEffect current = p.getPotionEffect(type);
        // Don't override a stronger or longer effect from a real potion/beacon
        if (current != null && (current.getAmplifier() > amplifier || current.isInfinite()
                || current.getDuration() > PASSIVE_DURATION)) return;
        p.addPotionEffect(new PotionEffect(type, PASSIVE_DURATION, amplifier, true, false, true));
        passiveEffects.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(type, amplifier);
    }

    public void removePassiveEffects(Player p) {
        Map<PotionEffectType, Integer> applied = passiveEffects.remove(p.getUniqueId());
        if (applied == null) return;
        applied.forEach((type, amp) -> {
            PotionEffect pe = p.getPotionEffect(type);
            if (pe != null && pe.getAmplifier() == amp && pe.getDuration() <= PASSIVE_DURATION && pe.isAmbient())
                p.removePotionEffect(type);
        });
    }

    /** Removes one tracked passive effect (e.g. Strength when the rain stops). */
    public void removePassiveEffect(Player p, PotionEffectType type) {
        Map<PotionEffectType, Integer> applied = passiveEffects.get(p.getUniqueId());
        if (applied == null || !applied.containsKey(type)) return;
        int amp = applied.remove(type);
        PotionEffect pe = p.getPotionEffect(type);
        if (pe != null && pe.getAmplifier() == amp && pe.getDuration() <= PASSIVE_DURATION && pe.isAmbient())
            p.removePotionEffect(type);
    }

    // ------------------------------------------------------------ ticking

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            AbilityContext ctx = context(p);
            BladeType now = ctx == null ? null : ctx.type();
            BladeType before = lastHeld.get(p.getUniqueId());
            boolean blocked = now != null && (worldDisabled(p) || plugin.status().isShocked(p));

            if (before != null && (before != now || blocked)) {
                blades.get(before).onUnhold(p);
                removePassiveEffects(p);
                lastHeld.remove(p.getUniqueId());
            }
            if (now == null || blocked) {
                if (now != null && plugin.getConfig().getBoolean("controls.action-bar", true)) actionBar(p, ctx);
                continue;
            }
            lastHeld.put(p.getUniqueId(), now);
            try {
                blades.get(now).passiveTick(ctx);
            } catch (Exception ex) {
                plugin.getLogger().severe("Passive error (" + now + "): " + ex);
            }
            if (plugin.getConfig().getBoolean("controls.action-bar", true)) actionBar(p, ctx);
        }
    }

    public void onShocked(Player p) {
        BladeType before = lastHeld.remove(p.getUniqueId());
        if (before != null) blades.get(before).onUnhold(p);
        removePassiveEffects(p);
        // Stop abilities that are already running (fire ring, invisibility, dome...)
        if (plugin.getConfig().getBoolean("blades.lightning.ability1.cancel-active-abilities", true)) {
            for (Blade b : blades.values()) { b.cleanup(p); b.stopUltimate(p.getUniqueId()); }
        }
    }

    private void actionBar(Player p, AbilityContext ctx) {
        if (plugin.status().isShocked(p)) {
            p.sendActionBar(Component.text("⚡ Shocked — blade disabled for " + Text.seconds(plugin.status().shockRemaining(p)),
                    NamedTextColor.YELLOW));
            return;
        }
        Component bar = Component.text(ctx.type().displayName() + "  ", ctx.type().textColor())
                .append(slotStatus(p, ctx, 1)).append(Component.text("  |  ", NamedTextColor.DARK_GRAY))
                .append(slotStatus(p, ctx, 2));
        if (ultimatesEnabled()) bar = bar.append(Component.text("  |  ", NamedTextColor.DARK_GRAY)).append(ultStatus(p, ctx));
        String extra = blades.get(ctx.type()).statusText(p);
        if (extra != null) bar = bar.append(Component.text("  " + extra, NamedTextColor.GOLD));
        p.sendActionBar(bar);
    }

    private Component slotStatus(Player p, AbilityContext ctx, int slot) {
        String name = slot == 1 ? ctx.type().ability1Name() : ctx.type().ability2Name();
        long rem = cooldownRemaining(p, ctx.type(), slot);
        return rem <= 0 ? Component.text(name + " ✔", NamedTextColor.GREEN)
                : Component.text(name + " " + Text.seconds(rem), NamedTextColor.RED);
    }

    // ------------------------------------------------------------ lifecycle

    public void cleanup(Player p) {
        BladeType before = lastHeld.remove(p.getUniqueId());
        if (before != null) blades.get(before).onUnhold(p);
        removePassiveEffects(p);
        for (Blade b : blades.values()) { b.cleanup(p); b.stopUltimate(p.getUniqueId()); }
    }

    public void forget(UUID id) {
        hitCounters.remove(id);
        ultLockout.remove(id);
        if (!cfg().getBoolean("ultimate.keep-charge-on-quit", true)) ultCharge.remove(id);
        passiveEffects.remove(id);
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) cleanup(p);
        for (Blade b : blades.values()) { b.shutdown(); b.stopAllUltimates(); }
    }
}
