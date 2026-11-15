package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Text;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Silent, paginated item browser for the secret menu. Gives any normal item with no console line.
 * Deliberately excludes command blocks / structure blocks / debug stick — those run server commands.
 */
public final class CreativeItemsMenu extends Menu {

    /** Items that grant command execution or world/server control — never offered. */
    private static final Set<Material> BANNED = EnumSet.of(
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART, Material.STRUCTURE_BLOCK, Material.STRUCTURE_VOID,
            Material.JIGSAW, Material.DEBUG_STICK);

    private static List<Material> ITEMS;

    private static List<Material> items() {
        if (ITEMS == null) {
            List<Material> list = new ArrayList<>();
            for (Material m : Material.values()) {
                if (m.isLegacy() || m == Material.AIR || !m.isItem() || BANNED.contains(m)) continue;
                list.add(m);
            }
            ITEMS = list;
        }
        return ITEMS;
    }

    private static final int PER_PAGE = 45;
    private final int page;

    public CreativeItemsMenu(ElementalBlades plugin, Player viewer, int page) {
        super(plugin, viewer, 6, Text.mm("<dark_gray>✦ Items"));
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        List<Material> all = items();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.min(page, pages - 1);
        int start = p * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            Material m = all.get(start + i);
            set(i, Icons.item(m, "<yellow>" + pretty(m), "<gray>Left: 1  ·  Right: 64"), e -> {
                int amt = e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT ? m.getMaxStackSize() : 1;
                viewer.getInventory().addItem(new ItemStack(m, Math.max(1, amt)));   // silent
            });
        }
        if (p > 0) set(45, Icons.item(Material.ARROW, "<white>← Previous"),
                e -> new CreativeItemsMenu(plugin, viewer, p - 1).open());
        set(49, Icons.item(Material.PAPER, "<gray>Page " + (p + 1) + " / " + pages),
                e -> new SecretMenu(plugin, viewer).open());
        if (p < pages - 1) set(53, Icons.item(Material.ARROW, "<white>Next →"),
                e -> new CreativeItemsMenu(plugin, viewer, p + 1).open());
    }

    private static String pretty(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder b = new StringBuilder();
        for (String s : parts) b.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
        return b.toString().trim();
    }
}
