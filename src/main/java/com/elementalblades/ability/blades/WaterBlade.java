package com.elementalblades.ability.blades;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.Blade;
import com.elementalblades.ability.Fx;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.combat.StatusManager;
import com.elementalblades.util.Keys;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public final class WaterBlade extends Blade {

    private static final Color BLUE = Color.fromRGB(47, 123, 255);

    /** target -> bubble */
    private final Map<UUID, Bubble> bubbles = new HashMap<>();
    private final Map<UUID, Long> sneakStart = new HashMap<>();
    private final Map<UUID, Long> lastAbility = new HashMap<>();
    private final Map<UUID, Long> riptideCooldown = new HashMap<>();

    private record Bubble(UUID owner, LivingEntity target, BukkitTask task) {}

    public WaterBlade(ElementalBlades plugin) {
        super(plugin, BladeType.WATER);
    }

    // ============================================================ Ability 1 — Bubble Prison

    @Override
    public boolean ability1(AbilityContext ctx) {
        Player p = ctx.player();
        lastAbility.put(p.getUniqueId(), System.currentTimeMillis());
        double range = ctx.d("ability1.range", 20);
        RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, 0.6,
                e -> !bubbles.containsKey(e.getUniqueId()) && plugin.combat().canAffect(p, e, type));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            p.sendActionBar(Component.text("No target in sight for Bubble Prison", NamedTextColor.GRAY));
            return false;
        }
        // Don't shoot through walls
        RayTraceResult wall = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                p.getEyeLocation().distance(target.getEyeLocation()));
        if (wall != null && wall.getHitBlock() != null) {
            p.sendActionBar(Component.text("Bubble Prison is blocked by a wall", NamedTextColor.GRAY));
            return false;
        }
        long duration = ctx.ticks("ability1.duration", 4);
        double drown = ctx.dmg("ability1.drown-damage", 2.0);
        int drownInterval = Math.max(1, ctx.i("ability1.drown-interval-ticks", 15));
        boolean respectBreathing = ctx.b("ability1.respect-water-breathing", true);
        Location anchor = target.getLocation().add(0, 0.6, 0);

        StatusManager.addModifier(target, Attribute.MOVEMENT_SPEED, Keys.MOD_BUBBLE_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        StatusManager.addModifier(target, Attribute.JUMP_STRENGTH, Keys.MOD_BUBBLE_JUMP, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        StatusManager.addModifier(target, Attribute.GRAVITY, Keys.MOD_BUBBLE_GRAVITY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        target.getWorld().playSound(anchor, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.8f);
        target.getWorld().playSound(anchor, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5f, 1f);
        if (target instanceof Player tp) Text.send(tp, "<blue>You're trapped in " + Text.esc(p.getName()) + "'s Bubble Prison!");

        final int[] air = {target.getRemainingAir()};
        BukkitTask task = Fx.repeat(plugin, 1, duration, t -> {
            if (!target.isValid() || target.isDead()) { endBubble(target.getUniqueId()); return; }
            // hold in place
            Vector pull = anchor.toVector().subtract(target.getLocation().toVector()).multiply(0.35);
            target.setVelocity(pull);
            Location center = target.getLocation().add(0, 1, 0);
            if (t % 2 == 0) Fx.sphere(center, 1.3, BLUE, 1.0f, 40);
            target.getWorld().spawnParticle(Particle.BUBBLE_POP, center, 6, 0.6, 0.7, 0.6, 0.02);
            target.getWorld().spawnParticle(Particle.FALLING_WATER, center, 4, 0.7, 0.8, 0.7, 0);
            // drowning
            boolean breathes = target.canBreatheUnderwater()
                    || (respectBreathing && (target.hasPotionEffect(PotionEffectType.WATER_BREATHING)
                        || target.hasPotionEffect(PotionEffectType.CONDUIT_POWER)));
            if (!breathes) {
                air[0] = Math.max(-20, air[0] - 30);
                target.setRemainingAir(Math.max(0, air[0]));
                if (air[0] <= 0 && t % drownInterval == 0) plugin.combat().damage(p, target, drown, DamageType.DROWN);
            }
        }, () -> endBubble(target.getUniqueId()));
        bubbles.put(target.getUniqueId(), new Bubble(p.getUniqueId(), target, task));
        return true;
    }

    private void endBubble(UUID targetId) {
        Bubble b = bubbles.remove(targetId);
        if (b == null) return;
        b.task().cancel();
        LivingEntity t = b.target();
        StatusManager.removeModifier(t, Attribute.MOVEMENT_SPEED, Keys.MOD_BUBBLE_SPEED);
        StatusManager.removeModifier(t, Attribute.JUMP_STRENGTH, Keys.MOD_BUBBLE_JUMP);
        StatusManager.removeModifier(t, Attribute.GRAVITY, Keys.MOD_BUBBLE_GRAVITY);
        if (t.isValid()) {
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 1.2f);
            t.getWorld().spawnParticle(Particle.SPLASH, t.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6, 0.2);
        }
    }

    public boolean isBubbled(Entity e) { return bubbles.containsKey(e.getUniqueId()); }

    // ============================================================ Ability 2 — Tidal Wave

    @Override
    public boolean ability2(AbilityContext ctx) {
        Player p = ctx.player();
        lastAbility.put(p.getUniqueId(), System.currentTimeMillis());
        int length = Math.max(3, ctx.i("ability2.length", 14));
        double width = ctx.d("ability2.width", 3.0);
        double damage = ctx.dmg("ability2.damage", 6.0);
        double push = ctx.d("ability2.push", 1.8);
        int slowTicks = (int) ctx.ticks("ability2.slowness-seconds", 3);
        boolean extinguish = ctx.b("ability2.extinguish-allies", true);
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        final Vector forward = dir.normalize();
        final Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        final Location origin = p.getLocation();
        Set<UUID> hit = new HashSet<>();
        p.getWorld().playSound(origin, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.6f);
        p.getWorld().playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.2f, 0.8f);
        if (extinguish) p.setFireTicks(0);
        Fx.repeat(plugin, 1, length, t -> {
            Location front = origin.clone().add(forward.clone().multiply(t + 1));
            for (double s = -width; s <= width; s += 0.5) {
                Location col = front.clone().add(side.clone().multiply(s));
                double h = 1.6 * (1 - Math.abs(s) / (width + 0.5)) + 0.4;
                for (double y = 0; y <= h; y += 0.4) {
                    p.getWorld().spawnParticle(Particle.DUST, col.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(BLUE, 1.6f));
                }
                p.getWorld().spawnParticle(Particle.SPLASH, col.clone().add(0, h, 0), 2, 0.1, 0.1, 0.1, 0.1);
            }
            for (Entity e : p.getWorld().getNearbyEntities(front.clone().add(0, 1, 0), width + 0.5, 1.8, width + 0.5)) {
                if (!(e instanceof LivingEntity le) || !hit.add(le.getUniqueId())) continue;
                if (plugin.combat().canAffect(p, le, type)) {
                    plugin.combat().armouredDamage(p, le, damage);
                    le.setVelocity(forward.clone().multiply(push).setY(0.4));
                    le.setFireTicks(0);
                    if (slowTicks > 0) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 0));
                } else if (extinguish && (le.equals(p) || (le instanceof Player ally
                        && plugin.combat().sameTeam(p.getUniqueId(), ally.getUniqueId())))) {
                    le.setFireTicks(0);
                }
            }
        }, null);
        return true;
    }

    // ============================================================ Passive

    @Override
    public void passiveTick(AbilityContext ctx) {
        Player p = ctx.player();
        if (ctx.b("passive.water-breathing", true)) plugin.abilities().passiveEffect(p, PotionEffectType.WATER_BREATHING, 0);
        if (ctx.b("passive.conduit-power", true) && p.isInWater())
            plugin.abilities().passiveEffect(p, PotionEffectType.CONDUIT_POWER, 0);
        Long start = sneakStart.get(p.getUniqueId());
        if (start != null && p.isSneaking() && System.currentTimeMillis() - start >= 1000 && riptideAllowed(p)) {
            p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /** Riptide surge: hold sneak ~1s while holding the blade, release to launch. */
    public void onSneakToggle(Player p, boolean sneaking) {
        if (!plugin.getConfig().getBoolean("blades.water.passive.riptide", true)) return;
        if (sneaking) {
            if (BladeItems.holding(p, type)) sneakStart.put(p.getUniqueId(), System.currentTimeMillis());
            return;
        }
        Long start = sneakStart.remove(p.getUniqueId());
        if (start == null || System.currentTimeMillis() - start < 1000) return;
        if (!BladeItems.holding(p, type) || plugin.status().isShocked(p) || plugin.abilities().worldDisabled(p)) return;
        Long last = lastAbility.get(p.getUniqueId());
        if (last != null && last >= start) return; // they used Sneak+Right-click during this sneak
        if (!riptideAllowed(p)) return;
        long now = System.currentTimeMillis();
        Long cd = riptideCooldown.get(p.getUniqueId());
        if (cd != null && cd > now) {
            p.sendActionBar(Component.text("Riptide surge ready in " + Text.seconds(cd - now), NamedTextColor.RED));
            return;
        }
        riptideCooldown.put(p.getUniqueId(), now + (long) (plugin.getConfig().getDouble("blades.water.passive.riptide-cooldown", 3) * 1000));
        double power = plugin.getConfig().getDouble("blades.water.passive.riptide-power", 2.6);
        p.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(power));
        p.startRiptideAttack(20, 8f, p.getInventory().getItemInMainHand());
        plugin.status().noFall(p, 3000);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
    }

    private boolean riptideAllowed(Player p) {
        return plugin.getConfig().getBoolean("blades.water.passive.riptide-anywhere", false) || p.isInWater() || p.isInRain();
    }

    @Override
    public void onUnhold(Player player) {
        sneakStart.remove(player.getUniqueId());
    }

    // ============================================================ lifecycle

    @Override
    public void cleanup(Player player) {
        endBubble(player.getUniqueId()); // they were bubbled
        for (Bubble b : new ArrayList<>(bubbles.values()))
            if (b.owner().equals(player.getUniqueId())) endBubble(b.target().getUniqueId());
        sneakStart.remove(player.getUniqueId());
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(bubbles.keySet())) endBubble(id);
    }
}
