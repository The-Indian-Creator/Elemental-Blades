package com.elementalblades.ability;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Behaviour of one blade type. Abilities return true when they actually fired (starts the cooldown). */
public abstract class Blade {

    protected final ElementalBlades plugin;
    protected final BladeType type;

    protected Blade(ElementalBlades plugin, BladeType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public BladeType type() { return type; }

    public abstract boolean ability1(AbilityContext ctx);

    public abstract boolean ability2(AbilityContext ctx);

    /** Ultimate (F key). Return true when it fired (consumes the charge / starts the cooldown). */
    public boolean ultimate(AbilityContext ctx) { return false; }

    /** Running ultimate tasks (one per player), stopped on death/quit/shock/disable. */
    protected final Map<UUID, BukkitTask> ultTasks = new HashMap<>();

    protected void trackUlt(Player p, BukkitTask task) {
        stopUltimate(p.getUniqueId());
        ultTasks.put(p.getUniqueId(), task);
    }

    public void stopUltimate(UUID id) {
        BukkitTask t = ultTasks.remove(id);
        if (t != null) t.cancel();
    }

    public void stopAllUltimates() {
        for (UUID id : new ArrayList<>(ultTasks.keySet())) stopUltimate(id);
    }

    /** Called every 10 ticks while the blade is in the main hand (and the player isn't shocked). */
    public void passiveTick(AbilityContext ctx) {}

    /** Called once when the player stops holding this blade (or gets shocked). */
    public void onUnhold(Player player) {}

    /** Melee hit by the holder (not ability damage). */
    public void onMeleeHit(AbilityContext ctx, LivingEntity victim, EntityDamageByEntityEvent event) {}

    /** The holder killed something with this blade in hand. */
    public void onKill(AbilityContext ctx, LivingEntity victim) {}

    /** Player quit/died: stop any running effects. */
    public void cleanup(Player player) {}

    /** Plugin disabling: stop everything. */
    public void shutdown() {}

    /** Extra text for the action bar (e.g. "Dome active"), or null. */
    public String statusText(Player player) { return null; }

    /** Optional custom cooldown check (e.g. toggle abilities that can be turned off while on cooldown). */
    public boolean bypassCooldown(Player player, int slot) { return false; }
}
