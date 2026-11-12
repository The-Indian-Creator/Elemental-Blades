package com.elementalblades.combat;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeType;
import com.elementalblades.team.Team;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Who can hurt whom: team checks, ability targeting and ability damage. */
public final class CombatRules {

    private final ElementalBlades plugin;
    /** True while we are applying ability damage, so melee listeners (friendly fire, on-hit passives) ignore it. */
    private int abilityDamageDepth;

    public CombatRules(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    public boolean isAbilityDamage() { return abilityDamageDepth > 0; }

    public boolean sameTeam(UUID a, UUID b) {
        return plugin.teams().enabled() && plugin.teams().sameTeam(a, b);
    }

    /** Should normal melee/projectile damage between these two players be blocked? */
    public boolean blockFriendlyFire(Player attacker, Player victim) {
        if (attacker.equals(victim) || !sameTeam(attacker.getUniqueId(), victim.getUniqueId())) return false;
        Team t = plugin.teams().get(attacker.getUniqueId());
        return t != null && !t.friendlyFire();
    }

    /** Can an ability of this blade affect the target? Handles self, teammates, pets, creative/spectator. */
    public boolean canAffect(Player user, Entity target, BladeType blade) {
        if (!(target instanceof LivingEntity living) || living.isDead() || !living.isValid()) return false;
        if (target.equals(user) || target instanceof ArmorStand) return false;
        if (living.isInvulnerable()) return false;
        if (target instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null) {
            UUID owner = pet.getOwnerUniqueId();
            if (owner.equals(user.getUniqueId())) return false;
            if (sameTeam(user.getUniqueId(), owner) && spares(user, blade)) return false;
        }
        if (target instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
            if (sameTeam(user.getUniqueId(), p.getUniqueId()) && spares(user, blade)) return false;
        }
        return true;
    }

    /** True if teammates must be spared for this blade (blade setting, else team's ability-friendly-fire). */
    private boolean spares(Player user, BladeType blade) {
        if (plugin.getConfig().getBoolean(blade.path() + "spare-teammates", true)) return true;
        Team t = plugin.teams().get(user.getUniqueId());
        return t == null || !t.abilityFriendlyFire();
    }

    public List<LivingEntity> targetsNear(Player user, Location center, double radius, BladeType blade) {
        List<LivingEntity> out = new ArrayList<>();
        if (center.getWorld() == null) return out;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e.getLocation().distanceSquared(center) > radius * radius) continue;
            if (canAffect(user, e, blade)) out.add((LivingEntity) e);
        }
        return out;
    }

    /** Damage that armour reduces, credited to the user. */
    public void armouredDamage(Player user, LivingEntity target, double amount) {
        damage(user, target, amount, DamageType.PLAYER_ATTACK);
    }

    /** Damage that ignores armour, credited to the user. */
    public void trueDamage(Player user, LivingEntity target, double amount) {
        damage(user, target, amount, DamageType.MAGIC);
    }

    public void damage(Player user, LivingEntity target, double amount, DamageType type) {
        if (amount <= 0) return;
        abilityDamageDepth++;
        try {
            DamageSource src = DamageSource.builder(type).withCausingEntity(user).withDirectEntity(user).build();
            target.setNoDamageTicks(0);
            target.damage(amount, src);
        } finally {
            abilityDamageDepth--;
        }
    }
}
