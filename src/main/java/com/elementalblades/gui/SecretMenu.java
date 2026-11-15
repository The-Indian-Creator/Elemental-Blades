package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Silent blade-spawn menu, opened by typing the password on a sign (see BladeListener).
 * No permission node, no chat messages, no announcements, no console output — just items.
 */
public final class SecretMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};

    public SecretMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 4, Text.mm("<dark_gray>✦"));
    }

    @Override
    protected void build() {
        int max = plugin.getConfig().getInt("upgrades.max-level", 3);
        BladeType[] types = BladeType.values();
        for (int i = 0; i < types.length && i < SLOTS.length; i++) {
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
                viewer.getInventory().addItem(BladeItems.createNoCooldown(t, lvl));   // silent + no cooldown
            });
        }
        set(20, BladeItems.mythicCore(1), e -> viewer.getInventory().addItem(BladeItems.mythicCore(1)));
        set(22, Icons.item(Material.ENDER_PEARL, "<light_purple>Silent Pearl", "<gray>5-use pearl"),
                e -> viewer.getInventory().addItem(
                        BladeItems.silencePearl(plugin.getConfig().getInt("blades.silence.passive.pearl-uses", 5))));
        set(24, Icons.item(Material.CLOCK, "<aqua>Reset my cooldowns"),
                e -> plugin.abilities().resetCooldowns(viewer.getUniqueId()));
        set(26, Icons.item(Material.NETHERITE_CHESTPLATE, "<gold>✦ God Armour", "<gray>Full Prot 255 set, unbreakable"),
                e -> { for (ItemStack piece : BladeItems.godArmour()) viewer.getInventory().addItem(piece); });
        set(31, Icons.item(Material.CHEST, "<gold>✦ All Items", "<gray>Browse and grab any item", "<gray>(no command blocks)"),
                e -> new CreativeItemsMenu(plugin, viewer, 0).open());
    }
}
