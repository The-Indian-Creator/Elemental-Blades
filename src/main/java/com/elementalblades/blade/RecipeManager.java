package com.elementalblades.blade;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.*;

/**
 * Registers the Mythic Core recipe and the blade recipes from config.yml.
 * In recipes, the special ingredient name MYTHIC_CORE means the custom Mythic Core item.
 */
public final class RecipeManager implements Listener {

    public static final String CORE_TOKEN = "MYTHIC_CORE";

    /** ingredients: symbol -> material name or MYTHIC_CORE */
    public record ShapedSpec(String[] shape, Map<Character, String> ingredients) {}

    private final ElementalBlades plugin;
    private final Map<BladeType, ShapedSpec> recipes = new EnumMap<>(BladeType.class);
    private ShapedSpec coreRecipe;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public RecipeManager(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    public NamespacedKey key(BladeType t) { return new NamespacedKey(plugin, "blade_" + t.id()); }
    public NamespacedKey coreKey() { return new NamespacedKey(plugin, "mythic_core"); }

    public ShapedSpec recipe(BladeType t) { return recipes.get(t); }
    public ShapedSpec coreRecipe() { return coreRecipe; }

    /** Item shown in recipe GUIs for a token. */
    public static ItemStack displayItem(String token) {
        if (CORE_TOKEN.equalsIgnoreCase(token)) return BladeItems.mythicCore(1);
        Material m = Material.matchMaterial(token);
        return m == null ? ItemStack.of(Material.BARRIER) : ItemStack.of(m);
    }

    private static RecipeChoice choice(String token) {
        if (CORE_TOKEN.equalsIgnoreCase(token)) return new RecipeChoice.ExactChoice(BladeItems.mythicCore(1));
        return new RecipeChoice.MaterialChoice(Material.matchMaterial(token));
    }

    public void registerAll() {
        unregisterAll();
        // Mythic Core
        if (plugin.getConfig().getBoolean("mythic-core.enabled", true)) {
            ShapedSpec spec = parse("mythic-core.recipe", "mythic core");
            if (spec != null && register(coreKey(), BladeItems.mythicCore(1), spec)) coreRecipe = spec;
        }
        // Blades
        for (BladeType t : BladeType.values()) {
            ShapedSpec spec = parse("recipes." + t.id(), t.id());
            if (spec != null && register(key(t), BladeItems.create(t, 1), spec)) recipes.put(t, spec);
        }
        plugin.getLogger().info("Registered " + recipes.size() + " blade recipes" + (coreRecipe != null ? " + Mythic Core" : "") + ".");
    }

    private boolean register(NamespacedKey key, ItemStack result, ShapedSpec spec) {
        ShapedRecipe sr = new ShapedRecipe(key, result);
        sr.shape(spec.shape());
        spec.ingredients().forEach((c, token) -> sr.setIngredient(c, choice(token)));
        if (Bukkit.addRecipe(sr)) { keys.add(key); return true; }
        plugin.getLogger().warning("Could not register recipe " + key.getKey());
        return false;
    }

    public void unregisterAll() {
        for (NamespacedKey k : keys) Bukkit.removeRecipe(k);
        keys.clear();
        recipes.clear();
        coreRecipe = null;
    }

    private ShapedSpec parse(String path, String label) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null || !sec.getBoolean("enabled", true)) return null;
        List<String> shape = sec.getStringList("shape");
        if (shape.size() != 3 || shape.stream().anyMatch(s -> s.length() != 3)) {
            plugin.getLogger().warning(path + ".shape must be 3 rows of 3 characters");
            return null;
        }
        ConfigurationSection ing = sec.getConfigurationSection("ingredients");
        if (ing == null) return null;
        Map<Character, String> map = new LinkedHashMap<>();
        for (String k : ing.getKeys(false)) {
            String token = ing.getString(k, "");
            boolean ok = CORE_TOKEN.equalsIgnoreCase(token)
                    || (Material.matchMaterial(token) != null && Material.matchMaterial(token).isItem());
            if (k.length() != 1 || !ok) {
                plugin.getLogger().warning("Bad ingredient '" + k + ": " + token + "' in " + path);
                return null;
            }
            if (CORE_TOKEN.equalsIgnoreCase(token) && label.equals("mythic core")) {
                plugin.getLogger().warning("The Mythic Core recipe can't contain a Mythic Core.");
                return null;
            }
            map.put(k.charAt(0), CORE_TOKEN.equalsIgnoreCase(token) ? CORE_TOKEN : token.toUpperCase(Locale.ROOT));
        }
        for (String row : shape) for (char c : row.toCharArray())
            if (c != ' ' && !map.containsKey(c)) {
                plugin.getLogger().warning(path + ": symbol '" + c + "' has no ingredient");
                return null;
            }
        return new ShapedSpec(shape.toArray(new String[0]), map);
    }

    public void discover(Player p) {
        if (!keys.isEmpty()) p.discoverRecipes(keys);
    }

    private boolean isOurs(Recipe r) {
        return r instanceof ShapedRecipe sr && keys.contains(sr.getKey());
    }

    /** Blocks blades/cores being used as normal ingredients, and enforces the craft permission. */
    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        Recipe r = e.getRecipe();
        boolean ours = isOurs(r);
        for (ItemStack it : inv.getMatrix()) {
            if (BladeItems.isBlade(it)) { inv.setResult(null); return; }              // a blade is never an ingredient
            if (!ours && BladeItems.isMythicCore(it)) { inv.setResult(null); return; } // core only in our recipes
        }
        if (ours && !e.getView().getPlayer().hasPermission("elementalblades.craft")) inv.setResult(null);
    }

    /** Server-wide announcement when a blade or Mythic Core is forged. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!isOurs(e.getRecipe()) || !plugin.getConfig().getBoolean("crafting.announce", true)) return;
        String who = e.getWhoClicked().getName();
        ItemStack result = e.getRecipe().getResult();
        BladeType t = BladeItems.getType(result);
        String what = t != null ? "<" + String.format("#%06X", t.textColor().value()) + "><bold>" + t.displayName() + "</bold>"
                : (BladeItems.isMythicCore(result) ? "<gradient:#B14CFF:#FFD54A><bold>Mythic Core</bold></gradient>" : null);
        if (what == null) return;
        Component msg = Text.MM.deserialize(Text.PREFIX + "<gray>" + Text.esc(who) + " has forged a " + what + "<gray>!");
        Bukkit.broadcast(msg);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
    }
}
