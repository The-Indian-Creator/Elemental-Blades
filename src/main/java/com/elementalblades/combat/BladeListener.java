package com.elementalblades.combat;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.AbilityContext;
import com.elementalblades.ability.blades.*;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Keys;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ability input, combat rules, passives that hook events, and Silent Dome protection. */
public final class BladeListener implements Listener {

    private final ElementalBlades plugin;
    private final Map<UUID, Long> lastActivation = new HashMap<>();

    public BladeListener(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    private SilenceBlade silence() { return plugin.abilities().blade(SilenceBlade.class); }
    private LifeBlade life() { return plugin.abilities().blade(LifeBlade.class); }

    // ============================================================ input

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("controls.right-click", true)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!BladeItems.isBlade(p.getInventory().getItemInMainHand())) return;
        if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable()) return; // let them open doors/chests
        if (debounce(p)) return;
        e.setCancelled(true);
        plugin.abilities().activate(p, p.isSneaking() ? 2 : 1);
    }

    /** Right-clicking directly on a player/mob also triggers abilities. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!plugin.getConfig().getBoolean("controls.right-click", true) || e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!BladeItems.isBlade(p.getInventory().getItemInMainHand())) return;
        if (e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof Villager) return;
        if (debounce(p)) return;
        e.setCancelled(true);
        plugin.abilities().activate(p, p.isSneaking() ? 2 : 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!plugin.getConfig().getBoolean("controls.swap-hand-key", true)) return;
        Player p = e.getPlayer();
        if (!BladeItems.isBlade(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        if (debounce(p)) return;
        plugin.abilities().activate(p, p.isSneaking() ? 2 : 1);
    }

    private boolean debounce(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastActivation.put(p.getUniqueId(), now);
        return last != null && now - last < 150;
    }

    // ============================================================ combat

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (plugin.combat().isAbilityDamage()) return; // abilities already decided their targets
        Entity damager = e.getDamager();
        Entity victimEntity = e.getEntity();

        // Our fireballs: team rules + upgrade scaling
        if (damager instanceof Projectile proj) {
            String tag = proj.getPersistentDataContainer().get(Keys.PROJECTILE_BLADE, PersistentDataType.STRING);
            if (tag != null) {
                Player owner = attackerOf(proj);
                BladeType bt = BladeType.fromId(tag.split(":")[0]);
                if (owner != null && bt != null && !plugin.combat().canAffect(owner, victimEntity, bt)) {
                    e.setCancelled(true);
                    return;
                }
                try {
                    int lvl = Integer.parseInt(tag.split(":")[1]);
                    e.setDamage(e.getDamage() * plugin.getConfig().getDouble("upgrades.levels." + lvl + ".damage-multiplier", 1.0));
                } catch (Exception ignored) { }
            }
        }

        Player attacker = attackerOf(damager);
        if (attacker == null) return;

        if (victimEntity instanceof Player victim && plugin.combat().blockFriendlyFire(attacker, victim)) {
            e.setCancelled(true);
            return;
        }
        if (plugin.status().isShocked(attacker)
                && plugin.getConfig().getBoolean("blades.lightning.ability1.disable-weapons", true)) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("⚡ You are shocked — weapons disabled!", NamedTextColor.YELLOW));
            return;
        }
        if (plugin.status().isFrozen(attacker) && plugin.getConfig().getBoolean("blades.frost.freeze-blocks-attacks", true)
                && damager == attacker) {
            e.setCancelled(true);
            return;
        }

        // Melee hit with a blade -> on-hit passives
        if (damager != attacker) return;
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return; // sweep hits don't count
        if (!(victimEntity instanceof LivingEntity victim)) return;
        AbilityContext ctx = plugin.abilities().context(attacker);
        if (ctx == null || plugin.abilities().worldDisabled(attacker)) return;
        if (ctx.type() == BladeType.VELOCITY) plugin.abilities().blade(VelocityBlade.class).onAttack(attacker);
        if (!(victim instanceof Player) && !plugin.getConfig().getBoolean("count-mob-hits", true)) return;
        if (victim instanceof ArmorStand) return;
        plugin.abilities().blade(ctx.type()).onMeleeHit(ctx, victim, e);
    }

    // LOW so the Silence sphere cancels hits before on-hit passives (HIGH) can react to them
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityDamageEvent.DamageCause cause = e.getCause();

        if (cause == EntityDamageEvent.DamageCause.FALL && plugin.status().hasNoFall(p)) {
            e.setCancelled(true);
            return;
        }

        // Silence — Void Sphere
        if (silence().isShielded(p)) {
            boolean all = plugin.getConfig().getBoolean("blades.silence.ability1.block-all-damage", false);
            boolean attack = e instanceof EntityDamageByEntityEvent;
            if (all ? cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.KILL : attack) {
                e.setCancelled(true);
                p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
                if (attack && plugin.getConfig().getBoolean("blades.silence.ability1.knockback-attackers", true)) {
                    Entity d = ((EntityDamageByEntityEvent) e).getDamager();
                    Entity push = d instanceof Projectile ? null : d;
                    if (push != null) {
                        Vector away = push.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                        if (away.lengthSquared() > 1e-4) push.setVelocity(away.normalize().multiply(0.9).setY(0.35));
                    }
                }
                return;
            }
        }

        BladeType held = BladeItems.held(p);
        if (held == null || plugin.status().isShocked(p) || plugin.abilities().worldDisabled(p)) return;

        // Life — immune to harming (magic) damage, poison and wither ticks
        if (held == BladeType.LIFE && !plugin.combat().isAbilityDamage()) {
            boolean harming = cause == EntityDamageEvent.DamageCause.MAGIC
                    && plugin.getConfig().getBoolean("blades.life.passive.block-harming-damage", true);
            if (harming || cause == EntityDamageEvent.DamageCause.POISON || cause == EntityDamageEvent.DamageCause.WITHER) {
                e.setCancelled(true);
                return;
            }
        }
        // Frost — immune to freezing (powder snow / frost damage)
        if (held == BladeType.FROST && cause == EntityDamageEvent.DamageCause.FREEZE) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getNewEffect() == null) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        // Lightning shock blocks new potion effects (our own ability effects such as slowness still apply)
        if (plugin.status().isShocked(p) && e.getCause() != EntityPotionEffectEvent.Cause.PLUGIN
                && plugin.getConfig().getBoolean("blades.lightning.ability1.block-new-potion-effects", true)) {
            e.setCancelled(true);
            return;
        }
        if (BladeItems.holding(p, BladeType.LIFE) && !plugin.status().isShocked(p) && !plugin.abilities().worldDisabled(p)
                && life().isImmune(e.getModifiedType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {
        if (e.getEntity() instanceof Player p && silence().isShielded(p)) { e.setCancelled(true); return; }
        if (!(e.getCombuster() instanceof Projectile proj)) return;
        String tag = proj.getPersistentDataContainer().get(Keys.PROJECTILE_BLADE, PersistentDataType.STRING);
        if (tag == null) return;
        Player owner = attackerOf(proj);
        BladeType bt = BladeType.fromId(tag.split(":")[0]);
        if (owner != null && bt != null && !plugin.combat().canAffect(owner, e.getEntity(), bt)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && plugin.status().isShocked(p)
                && plugin.getConfig().getBoolean("blades.lightning.ability1.disable-weapons", true)) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("⚡ You are shocked — weapons disabled!", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent e) {
        Player p = e.getPlayer();
        if (plugin.status().isShocked(p) && e.getProjectile() instanceof Trident
                && plugin.getConfig().getBoolean("blades.lightning.ability1.disable-weapons", true)) {
            e.setCancelled(true);
            return;
        }
        // Silent Pearls: multi-use ender pearls
        ItemStack used = e.getItemStack();
        if (BladeItems.pearlUses(used) <= 0) return;
        e.setShouldConsume(false);
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        boolean inMain = BladeItems.pearlUses(main) > 0 && main.isSimilar(used);
        ItemStack held = inMain ? main : off;
        if (BladeItems.pearlUses(held) <= 0) return;
        int left = BladeItems.pearlUses(held) - 1;
        if (left <= 0) {
            if (inMain) p.getInventory().setItemInMainHand(null);
            else p.getInventory().setItemInOffHand(null);
            p.sendActionBar(Component.text("Silent Pearl used up", NamedTextColor.GRAY));
        } else {
            BladeItems.setPearlUses(held, left);
            if (inMain) p.getInventory().setItemInMainHand(held);
            else p.getInventory().setItemInOffHand(held);
            p.sendActionBar(Component.text("Silent Pearl: " + left + " uses left", BladeType.SILENCE.textColor()));
        }
    }

    // ============================================================ death / quit / join / drop

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) handleKill(killer, dead);
        plugin.abilities().cleanup(dead);
        plugin.status().clear(dead.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer != null) handleKill(killer, e.getEntity());
    }

    private void handleKill(Player killer, LivingEntity victim) {
        AbilityContext ctx = plugin.abilities().context(killer);
        if (ctx == null || plugin.abilities().worldDisabled(killer) || plugin.status().isShocked(killer)) return;
        plugin.abilities().blade(ctx.type()).onKill(ctx, victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.abilities().cleanup(p);
        plugin.abilities().forget(p.getUniqueId());
        plugin.status().clear(p.getUniqueId());
        lastActivation.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.abilities().blade(VelocityBlade.class).onJoin(e.getPlayer());
        plugin.recipes().discover(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (BladeItems.getType(e.getItemDrop().getItemStack()) == BladeType.SILENCE) {
            silence().endDome(e.getPlayer().getUniqueId(), "<gray>You dropped your Silence Blade — the dome collapsed.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        plugin.abilities().blade(WaterBlade.class).onSneakToggle(e.getPlayer(), e.isSneaking());
    }

    // ============================================================ movement: dome walls + frost walker

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (silence().blockingDome(p, e.getFrom(), e.getTo()) != null) {
            e.setCancelled(true);
            p.sendActionBar(Component.text("The Silent Dome holds you in.", BladeType.SILENCE.textColor()));
            return;
        }
        if (!e.hasChangedBlock()) return;
        if (BladeItems.holding(p, BladeType.FROST) && !plugin.status().isShocked(p) && !plugin.abilities().worldDisabled(p)) {
            plugin.abilities().blade(FrostBlade.class).frostWalk(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND && p.hasPermission("elementalblades.admin")) return;
        if (silence().blockingDome(p, e.getFrom(), e.getTo()) != null) {
            e.setCancelled(true);
            Text.send(p, "<dark_purple>You can't teleport through a Silent Dome.");
        }
    }

    // ============================================================ dome protection

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (silence().isDomeBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent e) {
        if (silence().isDomeBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(silence()::isDomeBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(silence()::isDomeBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (silence().isDomeBlock(b)) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (silence().isDomeBlock(b)) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (silence().isDomeBlock(e.getBlock())) e.setCancelled(true);
    }
}
