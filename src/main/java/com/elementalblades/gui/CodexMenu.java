package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.blade.RecipeManager;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** All blades; click one to see its crafting recipe. */
public final class CodexMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public CodexMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 4, Text.mm("<dark_gray>Blade Codex"));
    }

    @Override
    protected void build() {
        BladeType[] types = BladeType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
            BladeType t = types[i];
            ItemStack icon = BladeItems.create(t, 1);
            icon.editMeta(m -> {
                List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                lore.add(Text.mm("<yellow>Click to view recipe"));
                m.lore(lore);
            });
            set(SLOTS[i], icon, e -> new RecipeView(plugin, viewer, t).open());
        }
        back(31, () -> new MainMenu(plugin, viewer).open());
    }

    /** 3x3 recipe display for one blade. */
    public static final class RecipeView extends Menu {
        private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        private final BladeType type;

        RecipeView(ElementalBlades plugin, Player viewer, BladeType type) {
            super(plugin, viewer, 5, Text.mm("<dark_gray>Recipe: " + type.displayName()));
            this.type = type;
        }

        @Override
        protected void build() {
            RecipeManager.BladeRecipe r = plugin.recipes().recipe(type);
            if (r == null) {
                set(22, Icons.item(Material.BARRIER, "<red>This blade can't be crafted", "<gray>(disabled in config)"));
            } else {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        char c = r.shape()[row].charAt(col);
                        int slot = GRID[row * 3 + col];
                        if (c == ' ') set(slot, null);
                        else set(slot, ItemStack.of(r.ingredients().get(c)));
                    }
                }
                set(23, Icons.item(Material.SPECTRAL_ARROW, "<gray>Crafts into →"));
            }
            ItemStack result = BladeItems.create(type, 1);
            boolean admin = viewer.hasPermission("elementalblades.admin");
            set(25, result, admin ? e -> {
                viewer.getInventory().addItem(BladeItems.create(type, 1));
                success("Gave you a " + type.displayName() + ".");
            } : null);
            set(16, Icons.item(Material.CRAFTING_TABLE, "<gold>How to craft",
                    "<gray>Use a normal crafting table.", "<gray>The netherite sword must be a", "<gray>plain one, not another blade."));
            back(40, () -> new CodexMenu(plugin, viewer).open());
        }
    }
}
