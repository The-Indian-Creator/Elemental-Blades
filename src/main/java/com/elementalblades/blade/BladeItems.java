package com.elementalblades.blade;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Keys;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and reads blade ItemStacks. A blade is a netherite sword with PDC tags and a custom item model. */
public final class BladeItems {

    public static final String FLAME_FIRE_RES = "FIRE_RESISTANCE";
    public static final String FLAME_FIRE_ASPECT = "FIRE_ASPECT";

    private BladeItems() {}

    public static ItemStack create(BladeType type, int level) {
        ItemStack item = ItemStack.of(Material.NETHERITE_SWORD);
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(Keys.BLADE_TYPE, PersistentDataType.STRING, type.id());
            pdc.set(Keys.BLADE_LEVEL, PersistentDataType.INTEGER, level);
            if (type == BladeType.FLAME) {
                String def = ElementalBlades.get().getConfig()
                        .getString("blades.flame.passive.default-choice", FLAME_FIRE_RES);
                pdc.set(Keys.FLAME_CHOICE, PersistentDataType.STRING,
                        FLAME_FIRE_ASPECT.equalsIgnoreCase(def) ? FLAME_FIRE_ASPECT : FLAME_FIRE_RES);
            }
            meta.setItemModel(new NamespacedKey("elementalblades", type.id() + "_blade"));
            meta.setRarity(ItemRarity.EPIC);
        });
        refresh(item);
        return item;
    }

    /** Rebuilds name, lore and choice-dependent enchantments. Keeps durability and player-added enchants. */
    public static void refresh(ItemStack item) {
        BladeType type = getType(item);
        if (type == null) return;
        int level = getLevel(item);
        String flameChoice = getFlameChoice(item);
        int maxLevel = ElementalBlades.get().getConfig().getInt("upgrades.max-level", 3);
        item.editMeta(meta -> {
            meta.itemName(Component.text(type.displayName(), type.textColor()).decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.mm("<gray>Level <white>" + roman(level) + "</white><dark_gray>/" + roman(maxLevel)));
            lore.add(Component.empty());
            lore.add(Text.plain("» " + type.ability1Name(), type.textColor()).append(Text.mm(" <dark_gray>[Right-click]")));
            lore.add(Text.gray("  " + type.ability1Desc()));
            lore.add(Text.plain("» " + type.ability2Name(), type.textColor()).append(Text.mm(" <dark_gray>[Sneak + Right-click]")));
            lore.add(Text.gray("  " + type.ability2Desc()));
            var cfg = ElementalBlades.get().getConfig();
            if (cfg.getBoolean("ultimate.enabled", true)) {
                String key = "SNEAK_SWAP".equalsIgnoreCase(cfg.getString("controls.ultimate-key", "SWAP_HAND")) ? "Sneak + F" : "F";
                lore.add(Text.mm("<gold>✦ " + type.ultimateName() + " <dark_gray>[" + key + "] <gold>ULTIMATE"));
                lore.add(Text.gray("  " + type.ultimateDesc()));
            }
            lore.add(Component.empty());
            lore.add(Text.mm("<gold>Passive"));
            for (String line : type.passiveLines()) lore.add(Text.gray("  " + line));
            if (type == BladeType.FLAME) {
                lore.add(Text.mm("  <yellow>Current: <white>" +
                        (FLAME_FIRE_ASPECT.equals(flameChoice) ? "Fire Aspect" : "Fire Resistance")));
            }
            lore.add(Component.empty());
            lore.add(Text.mm("<dark_gray>/blades to upgrade & configure"));
            meta.lore(lore);

            if (type == BladeType.FLAME) {
                int lvl = ElementalBlades.get().getConfig().getInt("blades.flame.passive.fire-aspect-level", 2);
                if (FLAME_FIRE_ASPECT.equals(flameChoice)) meta.addEnchant(Enchantment.FIRE_ASPECT, lvl, true);
                else meta.removeEnchant(Enchantment.FIRE_ASPECT);
            }
            meta.setEnchantmentGlintOverride(level >= maxLevel);
        });
    }

    public static boolean isBlade(ItemStack item) {
        return getType(item) != null;
    }

    public static BladeType getType(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return null;
        String id = item.getPersistentDataContainer().get(Keys.BLADE_TYPE, PersistentDataType.STRING);
        return BladeType.fromId(id);
    }

    public static int getLevel(ItemStack item) {
        if (item == null || item.getType().isAir()) return 1;
        Integer lvl = item.getPersistentDataContainer().get(Keys.BLADE_LEVEL, PersistentDataType.INTEGER);
        return lvl == null ? 1 : Math.max(1, lvl);
    }

    public static void setLevel(ItemStack item, int level) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.BLADE_LEVEL, PersistentDataType.INTEGER, level));
        refresh(item);
    }

    public static String getFlameChoice(ItemStack item) {
        if (item == null || item.getType().isAir()) return FLAME_FIRE_RES;
        String c = item.getPersistentDataContainer().get(Keys.FLAME_CHOICE, PersistentDataType.STRING);
        return FLAME_FIRE_ASPECT.equals(c) ? FLAME_FIRE_ASPECT : FLAME_FIRE_RES;
    }

    public static void setFlameChoice(ItemStack item, String choice) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.FLAME_CHOICE, PersistentDataType.STRING, choice));
        refresh(item);
    }

    /** Blade in the main hand, or null. */
    public static BladeType held(LivingEntity entity) {
        if (entity instanceof Player p) return getType(p.getInventory().getItemInMainHand());
        EntityEquipment eq = entity.getEquipment();
        return eq == null ? null : getType(eq.getItemInMainHand());
    }

    public static boolean holding(LivingEntity entity, BladeType type) {
        return held(entity) == type;
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    // ---------- Mythic Core ----------

    /** The rare crafting component every blade needs. */
    public static ItemStack mythicCore(int amount) {
        ItemStack core = ItemStack.of(Material.NETHER_STAR, Math.max(1, amount));
        core.editMeta(meta -> {
            meta.getPersistentDataContainer().set(Keys.MYTHIC_CORE, PersistentDataType.BOOLEAN, true);
            meta.itemName(Text.mm("<gradient:#B14CFF:#FFD54A><bold>Mythic Core</bold></gradient>"));
            meta.lore(List.of(
                    Text.mm("<gray>A shard of raw elemental power."),
                    Text.mm("<gray>The heart of every Elemental Blade."),
                    Component.empty(),
                    Text.mm("<dark_gray>Used in all blade recipes")));
            meta.setItemModel(new NamespacedKey("elementalblades", "mythic_core"));
            meta.setRarity(ItemRarity.EPIC);
            meta.setEnchantmentGlintOverride(true);
        });
        return core;
    }

    public static boolean isMythicCore(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        return Boolean.TRUE.equals(item.getPersistentDataContainer().get(Keys.MYTHIC_CORE, PersistentDataType.BOOLEAN));
    }

    // ---------- Silence pearls ----------

    public static ItemStack silencePearl(int uses) {
        ItemStack pearl = ItemStack.of(Material.ENDER_PEARL);
        pearl.editMeta(meta -> {
            meta.getPersistentDataContainer().set(Keys.PEARL_USES, PersistentDataType.INTEGER, uses);
            meta.setMaxStackSize(1);
            meta.itemName(Component.text("Silent Pearl", BladeType.SILENCE.textColor()));
            meta.setEnchantmentGlintOverride(true);
        });
        updatePearlLore(pearl);
        return pearl;
    }

    public static int pearlUses(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_PEARL) return -1;
        Integer u = item.getPersistentDataContainer().get(Keys.PEARL_USES, PersistentDataType.INTEGER);
        return u == null ? -1 : u;
    }

    public static void setPearlUses(ItemStack item, int uses) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.PEARL_USES, PersistentDataType.INTEGER, uses));
        updatePearlLore(item);
    }

    private static void updatePearlLore(ItemStack pearl) {
        int uses = pearlUses(pearl);
        int max = ElementalBlades.get().getConfig().getInt("blades.silence.passive.pearl-uses", 5);
        pearl.editMeta(meta -> meta.lore(List.of(
                Text.mm("<gray>Uses left: <white>" + uses + "<dark_gray>/" + max),
                Text.plain("Earned from a Silence Blade kill", NamedTextColor.DARK_GRAY))));
    }
}
