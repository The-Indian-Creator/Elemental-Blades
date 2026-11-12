package com.elementalblades.ability;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Everything an ability needs: the player, the blade item, its level and config shortcuts. */
public record AbilityContext(ElementalBlades plugin, Player player, ItemStack item, BladeType type, int level) {

    private FileConfiguration cfg() { return plugin.getConfig(); }

    /** Read blades.<type>.<path> as double. */
    public double d(String path, double def) { return cfg().getDouble(type.path() + path, def); }
    public int i(String path, int def) { return cfg().getInt(type.path() + path, def); }
    public boolean b(String path, boolean def) { return cfg().getBoolean(type.path() + path, def); }
    public String s(String path, String def) { return cfg().getString(type.path() + path, def); }

    public double damageMultiplier() {
        return cfg().getDouble("upgrades.levels." + level + ".damage-multiplier", 1.0);
    }

    public double cooldownMultiplier() {
        return cfg().getDouble("upgrades.levels." + level + ".cooldown-multiplier", 1.0);
    }

    /** Damage value from config scaled by upgrade level. */
    public double dmg(String path, double def) { return d(path, def) * damageMultiplier(); }

    public long ticks(String secondsPath, double defSeconds) { return Math.round(d(secondsPath, defSeconds) * 20); }
}
