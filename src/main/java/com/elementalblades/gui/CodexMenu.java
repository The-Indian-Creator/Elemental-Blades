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
import java.util.function.Supplier;

/** All blades + the Mythic Core; click one to see its crafting recipe. */
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
            set(SLOTS[i], withHint(BladeItems.create(t, 1)), e ->
                    new RecipeView(plugin, viewer, t.displayName(), plugin.recipes().recipe(t), () -> BladeItems.create(t, 1)).open());
        }
        set(22, withHint(BladeItems.mythicCore(1)), e ->
                new RecipeView(plugin, viewer, "Mythic Core", plugin.recipes().coreRecipe(), () -> BladeItems.mythicCore(1)).open());
        back(31, () -> new MainMenu(plugin, viewer).open());
    }

    private static ItemStack withHint(ItemStack icon) {
        icon.editMeta(m -> {
            List<Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            lore.add(Component.empty());
            lore.add(Text.mm("<yellow>Click to view recipe"));
            m.lore(lore);
        });
        return icon;
    }

    /** 3x3 recipe display. */
    public static final class RecipeView extends Menu {
        private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        private final RecipeManager.ShapedSpec spec;
        private final Supplier<ItemStack> result;

        RecipeView(ElementalBlades plugin, Player viewer, String name, RecipeManager.ShapedSpec spec, Supplier<ItemStack> result) {
            super(plugin, viewer, 5, Text.mm("<dark_gray>Recipe: " + Text.esc(name)));
            this.spec = spec;
            this.result = result;
        }

        @Override
        protected void build() {
            if (spec == null) {
                set(22, Icons.item(Material.BARRIER, "<red>This can't be crafted", "<gray>(disabled in config)"));
            } else {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        char c = spec.shape()[row].charAt(col);
                        int slot = GRID[row * 3 + col];
                        if (c == ' ') set(slot, null);
                        else {
                            String token = spec.ingredients().get(c);
                            boolean core = RecipeManager.CORE_TOKEN.equals(token);
                            set(slot, RecipeManager.displayItem(token), core ? e ->
                                    new RecipeView(plugin, viewer, "Mythic Core", plugin.recipes().coreRecipe(),
                                            () -> BladeItems.mythicCore(1)).open() : null);
                        }
                    }
                }
                set(23, Icons.item(Material.SPECTRAL_ARROW, "<gray>Crafts into →"));
            }
            boolean admin = viewer.hasPermission("elementalblades.admin");
            set(25, result.get(), admin ? e -> {
                viewer.getInventory().addItem(result.get());
                success("Given (admin).");
            } : null);
            set(16, Icons.item(Material.CRAFTING_TABLE, "<gold>How to craft",
                    "<gray>Use a normal crafting table.",
                    "<gray>Blades need a <light_purple>Mythic Core</light_purple>,",
                    "<gray>click it in the grid to see its recipe.",
                    "<gray>The sword must be a plain one."));
            back(40, () -> new CodexMenu(plugin, viewer).open());
        }
    }
}
