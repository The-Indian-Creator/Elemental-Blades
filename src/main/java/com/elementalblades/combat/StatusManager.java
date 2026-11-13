package com.elementalblades.combat;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Timed status effects that aren't vanilla potions:
 *  - FROZEN: can't move or jump (attribute modifiers), optional no-attack
 *  - SHOCKED (Lightning Blade): abilities/weapons/potion effects disabled
 *  - NO_FALL: fall damage ignored (after dashes)
 */
public final class StatusManager {

    public enum FreezeSource { FROST_DASH, FROST_NOVA, FROST_PASSIVE, OTHER }

    private final ElementalBlades plugin;
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Map<UUID, LivingEntity> frozenEntities = new HashMap<>();
    private final Map<UUID, Long> shockedUntil = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    /** Velocity frame-lock: frozen inside a "frame". */
    private final Map<UUID, FrameLock> frameLocks = new HashMap<>();
    private final Map<UUID, Long> frameImmuneUntil = new HashMap<>();

    private record FrameLock(LivingEntity entity, long start, long until, boolean unbreakable, Vector right) {}

    public StatusManager(ElementalBlades plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ------------------------------------------------------------ freeze

    /** @return true if the entity was frozen (false if immune). */
    public boolean freeze(LivingEntity target, double seconds, FreezeSource source) {
        if (seconds <= 0 || target.isDead()) return false;
        if (source == FreezeSource.FROST_DASH && target instanceof Player p && BladeItems.holding(p, BladeType.FLAME)) {
            double chance = plugin.getConfig().getDouble("blades.flame.passive.frost-dash-immunity-chance", 0.5);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.4f);
                return false;
            }
        }
        long until = System.currentTimeMillis() + (long) (seconds * 1000);
        frozenUntil.merge(target.getUniqueId(), until, Math::max);
        frozenEntities.put(target.getUniqueId(), target);
        addModifier(target, Attribute.MOVEMENT_SPEED, Keys.MOD_FREEZE_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        addModifier(target, Attribute.JUMP_STRENGTH, Keys.MOD_FREEZE_JUMP, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        Vector v = target.getVelocity();
        target.setVelocity(new Vector(0, Math.min(0, v.getY()), 0));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), target.getMaxFreezeTicks() - 1));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
        return true;
    }

    public boolean isFrozen(Entity e) {
        Long until = frozenUntil.get(e.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void unfreeze(UUID id) {
        frozenUntil.remove(id);
        LivingEntity e = frozenEntities.remove(id);
        if (e != null) {
            removeModifier(e, Attribute.MOVEMENT_SPEED, Keys.MOD_FREEZE_SPEED);
            removeModifier(e, Attribute.JUMP_STRENGTH, Keys.MOD_FREEZE_JUMP);
        }
    }

    // ------------------------------------------------------------ frame-lock (Velocity)

    /**
     * Locks the target in a frame: no moving, jumping, attacking or abilities.
     * @param unbreakable true = hits don't break it and re-lock immunity is ignored (ultimate)
     * @return true if locked (false if immune right now)
     */
    public boolean frameLock(LivingEntity target, double seconds, boolean unbreakable) {
        if (seconds <= 0 || target.isDead()) return false;
        long now = System.currentTimeMillis();
        if (!unbreakable) {
            Long immune = frameImmuneUntil.get(target.getUniqueId());
            if (immune != null && immune > now) return false;
            if (isFrameLocked(target)) return false;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1e-4) facing = new Vector(0, 0, 1);
        facing.normalize();
        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        long until = now + (long) (seconds * 1000);
        FrameLock old = frameLocks.get(target.getUniqueId());
        if (old != null) until = Math.max(until, old.until());
        frameLocks.put(target.getUniqueId(), new FrameLock(target, now, until, unbreakable || (old != null && old.unbreakable()), right));
        addModifier(target, Attribute.MOVEMENT_SPEED, Keys.MOD_FRAME_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        addModifier(target, Attribute.JUMP_STRENGTH, Keys.MOD_FRAME_JUMP, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        target.setVelocity(new Vector(0, Math.min(0, target.getVelocity().getY()), 0));
        target.getWorld().playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 1.2f, 2f);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.2f, 1.8f);
        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.08);
        drawFrame(frameLocks.get(target.getUniqueId()), 1.2f);
        if (target instanceof Player p)
            p.sendActionBar(net.kyori.adventure.text.Component.text("▣ FRAME-LOCKED", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        return true;
    }

    public boolean isFrameLocked(Entity e) {
        FrameLock f = frameLocks.get(e.getUniqueId());
        return f != null && f.until() > System.currentTimeMillis();
    }

    /** A hit breaks a normal frame-lock (not the lock-applying hit itself, nor ultimate locks). */
    public void breakFrameLock(Entity e) {
        FrameLock f = frameLocks.get(e.getUniqueId());
        if (f == null || f.unbreakable() || System.currentTimeMillis() - f.start() < 150) return;
        e.getWorld().spawnParticle(Particle.BLOCK, e.getLocation().add(0, 1, 0), 20, 0.4, 0.8, 0.4, 0,
                org.bukkit.Material.WHITE_STAINED_GLASS.createBlockData());
        e.getWorld().playSound(e.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.8f);
        unlockFrame(e.getUniqueId());
    }

    private void unlockFrame(UUID id) {
        FrameLock f = frameLocks.remove(id);
        if (f == null) return;
        removeModifier(f.entity(), Attribute.MOVEMENT_SPEED, Keys.MOD_FRAME_SPEED);
        removeModifier(f.entity(), Attribute.JUMP_STRENGTH, Keys.MOD_FRAME_JUMP);
        double immunity = plugin.getConfig().getDouble("blades.velocity.frame-lock.immunity-seconds", 3);
        frameImmuneUntil.put(id, System.currentTimeMillis() + (long) (immunity * 1000));
    }

    /** A flat white "film frame" around the entity. */
    private void drawFrame(FrameLock f, float size) {
        if (f == null) return;
        Location base = f.entity().getLocation();
        double hw = 0.75, h = 2.3;
        Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, size);
        for (double t = -hw; t <= hw + 1e-6; t += 0.15) {
            Location bottom = base.clone().add(f.right().clone().multiply(t)).add(0, -0.05, 0);
            base.getWorld().spawnParticle(Particle.DUST, bottom, 1, 0, 0, 0, 0, white);
            base.getWorld().spawnParticle(Particle.DUST, bottom.clone().add(0, h, 0), 1, 0, 0, 0, 0, white);
        }
        for (double y = 0; y <= h + 1e-6; y += 0.15) {
            base.getWorld().spawnParticle(Particle.DUST, base.clone().add(f.right().clone().multiply(-hw)).add(0, y - 0.05, 0), 1, 0, 0, 0, 0, white);
            base.getWorld().spawnParticle(Particle.DUST, base.clone().add(f.right().clone().multiply(hw)).add(0, y - 0.05, 0), 1, 0, 0, 0, 0, white);
        }
    }

    // ------------------------------------------------------------ shock (lightning disable)

    public void shock(Player target, double seconds, boolean clearEffects) {
        long until = System.currentTimeMillis() + (long) (seconds * 1000);
        shockedUntil.merge(target.getUniqueId(), until, Math::max);
        if (clearEffects) {
            for (PotionEffect pe : new ArrayList<>(target.getActivePotionEffects())) target.removePotionEffect(pe.getType());
        }
        plugin.abilities().onShocked(target);
    }

    public boolean isShocked(Entity e) {
        Long until = shockedUntil.get(e.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long shockRemaining(Entity e) {
        Long until = shockedUntil.get(e.getUniqueId());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    // ------------------------------------------------------------ no fall

    public void noFall(Player p, long millis) {
        noFallUntil.merge(p.getUniqueId(), System.currentTimeMillis() + millis, Math::max);
    }

    public boolean hasNoFall(Entity e) {
        Long until = noFallUntil.get(e.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    // ------------------------------------------------------------ tick

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(frozenUntil.keySet())) {
            LivingEntity e = frozenEntities.get(id);
            if (e == null || !e.isValid() || frozenUntil.get(id) <= now) {
                unfreeze(id);
                continue;
            }
            // keep the frost overlay on and stop sliding
            e.setFreezeTicks(Math.max(e.getFreezeTicks(), e.getMaxFreezeTicks() - 1));
            Vector v = e.getVelocity();
            if (Math.abs(v.getX()) > 0.01 || Math.abs(v.getZ()) > 0.01) e.setVelocity(new Vector(0, Math.min(0, v.getY()), 0));
            if (e.getTicksLived() % 5 == 0)
                e.getWorld().spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, 1, 0), 4, 0.3, 0.6, 0.3, 0.0);
        }
        for (UUID id : new ArrayList<>(frameLocks.keySet())) {
            FrameLock f = frameLocks.get(id);
            if (!f.entity().isValid() || f.entity().isDead() || f.until() <= now) { unlockFrame(id); continue; }
            Vector v = f.entity().getVelocity();
            if (Math.abs(v.getX()) > 0.01 || Math.abs(v.getZ()) > 0.01) f.entity().setVelocity(new Vector(0, Math.min(0, v.getY()), 0));
            if (f.entity().getTicksLived() % 3 == 0) drawFrame(f, 0.8f);
        }
        frameImmuneUntil.values().removeIf(t -> t <= now);
        shockedUntil.values().removeIf(t -> t <= now);
        noFallUntil.values().removeIf(t -> t <= now);
    }

    public void clear(UUID id) {
        unfreeze(id);
        unlockFrame(id);
        frameImmuneUntil.remove(id);
        shockedUntil.remove(id);
        noFallUntil.remove(id);
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(frozenUntil.keySet())) unfreeze(id);
        for (UUID id : new ArrayList<>(frameLocks.keySet())) unlockFrame(id);
    }

    // ------------------------------------------------------------ attribute helpers

    public static void addModifier(LivingEntity e, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(key);
        inst.addTransientModifier(new AttributeModifier(key, amount, op));
    }

    public static void removeModifier(LivingEntity e, Attribute attr, NamespacedKey key) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.removeModifier(key);
    }
}
