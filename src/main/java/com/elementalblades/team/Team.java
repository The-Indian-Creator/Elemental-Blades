package com.elementalblades.team;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A player team. Members includes the owner and admins. */
public final class Team {

    public enum Role { OWNER, ADMIN, MEMBER }

    private String name;
    private UUID owner;
    private final Set<UUID> admins = new LinkedHashSet<>();
    private final Set<UUID> members = new LinkedHashSet<>();
    private NamedTextColor color = NamedTextColor.AQUA;
    private boolean friendlyFire;
    private boolean abilityFriendlyFire;
    private boolean open;
    private Location home;
    /** Shared double-chest (54 slots). Created lazily by TeamManager. */
    Inventory chest;

    public Team(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        members.add(owner);
    }

    public String name() { return name; }
    void name(String name) { this.name = name; }
    public UUID owner() { return owner; }
    public Set<UUID> members() { return members; }
    public Set<UUID> admins() { return admins; }
    public NamedTextColor color() { return color; }
    public void color(NamedTextColor c) { this.color = c; }
    public boolean friendlyFire() { return friendlyFire; }
    public void friendlyFire(boolean v) { this.friendlyFire = v; }
    public boolean abilityFriendlyFire() { return abilityFriendlyFire; }
    public void abilityFriendlyFire(boolean v) { this.abilityFriendlyFire = v; }
    public boolean open() { return open; }
    public void open(boolean v) { this.open = v; }
    public Location home() { return home == null ? null : home.clone(); }
    public void home(Location l) { this.home = l == null ? null : l.clone(); }

    public boolean isMember(UUID id) { return members.contains(id); }

    public Role role(UUID id) {
        if (id.equals(owner)) return Role.OWNER;
        if (admins.contains(id)) return Role.ADMIN;
        return Role.MEMBER;
    }

    /** Owner or admin. */
    public boolean canManage(UUID id) {
        return id.equals(owner) || admins.contains(id);
    }

    void setOwner(UUID id) {
        admins.remove(id);
        admins.add(owner);
        owner = id;
    }
}
