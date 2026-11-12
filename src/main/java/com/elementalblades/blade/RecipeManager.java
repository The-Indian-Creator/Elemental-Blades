package com.elementalblades.blade;

import com.elementalblades.ElementalBlades;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.*;

/** Registers the blade crafting recipes from config.yml (recipes.<blade>). */
public final class RecipeManager implements Listener {

    public record BladeRecipe(BladeType type, String[] shape, Map<Character, Material> ingredients) {}

    private final ElementalBlades plugin;
    private final Map<BladeType, BladeRecipe> recipes = new EnumMap<>(BladeType.class);
    private final List<NamespacedKey> keys = new ArrayList<>();

    public RecipeManager(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey key(BladeType t) { return new NamespacedKey(plugin, "blade_" + t.id()); }

    public BladeRecipe recipe(BladeType t) { return recipes.get(t); }

    public void registerAll() {
        unregisterAll();
        for (BladeType t : BladeType.values()) {
            BladeRecipe r = parse(t);
            if (r == null) continue;
            ShapedRecipe sr = new ShapedRecipe(key(t), BladeItems.create(t, 1));
            sr.shape(r.shape());
            r.ingredients().forEach(sr::setIngredient);
            if (Bukkit.addRecipe(sr)) {
                recipes.put(t, r);
                keys.add(sr.getKey());
            } else {
                plugin.getLogger().warning("Could not register recipe for " + t.id());
            }
        }
        plugin.getLogger().info("Registered " + recipes.size() + " blade recipes.");
    }

    public void unregisterAll() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
        recipes.clear();
    }

    private BladeRecipe parse(BladeType t) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("recipes." + t.id());
        if (sec == null || !sec.getBoolean("enabled", true)) return null;
        List<String> shape = sec.getStringList("shape");
        if (shape.size() != 3 || shape.stream().anyMatch(s -> s.length() != 3)) {
            plugin.getLogger().warning("recipes." + t.id() + ".shape must be 3 rows of 3 characters");
            return null;
        }
        ConfigurationSection ing = sec.getConfigurationSection("ingredients");
        if (ing == null) return null;
        Map<Character, Material> map = new LinkedHashMap<>();
        for (String k : ing.getKeys(false)) {
            Material m = Material.matchMaterial(ing.getString(k, ""));
            if (k.length() != 1 || m == null || !m.isItem()) {
                plugin.getLogger().warning("Bad ingredient '" + k + ": " + ing.getString(k) + "' in recipes." + t.id());
                return null;
            }
            map.put(k.charAt(0), m);
        }
        for (String row : shape) for (char c : row.toCharArray())
            if (c != ' ' && !map.containsKey(c)) {
                plugin.getLogger().warning("recipes." + t.id() + ": symbol '" + c + "' has no ingredient");
                return null;
            }
        return new BladeRecipe(t, shape.toArray(new String[0]), map);
    }

    public void discover(Player p) {
        if (!keys.isEmpty()) p.discoverRecipes(keys);
    }

    /** Blocks using a blade as the netherite-sword ingredient, and enforces the craft permission. */
    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (!(r instanceof ShapedRecipe sr) || !keys.contains(sr.getKey())) {
            // Also stop blades being used as ingredients in any other recipe (e.g. repair crafting)
            for (ItemStack it : e.getInventory().getMatrix()) {
                if (BladeItems.isBlade(it)) { e.getInventory().setResult(null); return; }
            }
            return;
        }
        CraftingInventory inv = e.getInventory();
        for (ItemStack it : inv.getMatrix()) {
            if (BladeItems.isBlade(it)) { inv.setResult(null); return; }
        }
        if (!e.getView().getPlayer().hasPermission("elementalblades.craft")) inv.setResult(null);
    }
}
