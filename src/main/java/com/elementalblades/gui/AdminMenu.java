package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Operator menu: spawn blades. Left-click = level I, right-click = max level. */
public final class AdminMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    public AdminMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 4, Text.mm("<dark_red>Admin — Give Blades"));
    }

    @Override
    protected void build() {
        if (!viewer.hasPermission("elementalblades.admin")) { viewer.closeInventory(); return; }
        int max = plugin.getConfig().getInt("upgrades.max-level", 3);
        BladeType[] types = BladeType.values();
        for (int i = 0; i < types.length; i++) {
            BladeType t = types[i];
            ItemStack icon = BladeItems.create(t, 1);
            icon.editMeta(m -> {
                List<Component> lore = new ArrayList<>();
                lore.add(Text.mm("<yellow>Left-click: <white>Level I"));
                lore.add(Text.mm("<yellow>Right-click: <white>Level " + BladeItems.roman(max)));
                m.lore(lore);
            });
            set(SLOTS[i], icon, e -> {
                int lvl = e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT ? max : 1;
                viewer.getInventory().addItem(BladeItems.create(t, lvl));
                success("Gave " + t.displayName() + " " + BladeItems.roman(lvl));
            });
        }
        set(22, Icons.item(Material.ENDER_PEARL, "<light_purple>Silent Pearl", "<gray>Give a 5-use pearl"),
                e -> viewer.getInventory().addItem(BladeItems.silencePearl(plugin.getConfig().getInt("blades.silence.passive.pearl-uses", 5))));
        set(20, BladeItems.mythicCore(1), e -> {
            viewer.getInventory().addItem(BladeItems.mythicCore(1));
            success("Gave a Mythic Core.");
        });
        set(24, Icons.item(Material.CLOCK, "<aqua>Reset my cooldowns"), e -> {
            plugin.abilities().resetCooldowns(viewer.getUniqueId());
            success("Cooldowns reset.");
        });
        back(31, () -> new MainMenu(plugin, viewer).open());
    }
}
