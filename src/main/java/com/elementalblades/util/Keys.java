package com.elementalblades.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** All NamespacedKeys used by the plugin, created once. */
public final class Keys {
    public static NamespacedKey BLADE_TYPE;
    public static NamespacedKey BLADE_LEVEL;
    public static NamespacedKey FLAME_CHOICE;
    public static NamespacedKey PEARL_USES;
    public static NamespacedKey PROJECTILE_OWNER;
    public static NamespacedKey PROJECTILE_BLADE;
    public static NamespacedKey FROST_WALKER;
    public static NamespacedKey MOD_FREEZE_SPEED;
    public static NamespacedKey MOD_FREEZE_JUMP;
    public static NamespacedKey MOD_LIFE_HEALTH;
    public static NamespacedKey MOD_LIFE_ABSORB;
    public static NamespacedKey MOD_VELOCITY_SPEED;
    public static NamespacedKey MOD_BUBBLE_GRAVITY;
    public static NamespacedKey MOD_BUBBLE_SPEED;
    public static NamespacedKey MOD_BUBBLE_JUMP;
    public static NamespacedKey GUI_ACTION;

    private Keys() {}

    public static void init(Plugin plugin) {
        BLADE_TYPE = new NamespacedKey(plugin, "blade_type");
        BLADE_LEVEL = new NamespacedKey(plugin, "blade_level");
        FLAME_CHOICE = new NamespacedKey(plugin, "flame_choice");
        PEARL_USES = new NamespacedKey(plugin, "pearl_uses");
        PROJECTILE_OWNER = new NamespacedKey(plugin, "projectile_owner");
        PROJECTILE_BLADE = new NamespacedKey(plugin, "projectile_blade");
        FROST_WALKER = new NamespacedKey(plugin, "frost_walker");
        MOD_FREEZE_SPEED = new NamespacedKey(plugin, "freeze_speed");
        MOD_FREEZE_JUMP = new NamespacedKey(plugin, "freeze_jump");
        MOD_LIFE_HEALTH = new NamespacedKey(plugin, "life_health");
        MOD_LIFE_ABSORB = new NamespacedKey(plugin, "life_absorb");
        MOD_VELOCITY_SPEED = new NamespacedKey(plugin, "velocity_speed");
        MOD_BUBBLE_GRAVITY = new NamespacedKey(plugin, "bubble_gravity");
        MOD_BUBBLE_SPEED = new NamespacedKey(plugin, "bubble_speed");
        MOD_BUBBLE_JUMP = new NamespacedKey(plugin, "bubble_jump");
        GUI_ACTION = new NamespacedKey(plugin, "gui_action");
    }
}
