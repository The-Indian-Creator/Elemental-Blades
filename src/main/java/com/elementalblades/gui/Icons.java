package com.elementalblades.gui;

import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** ItemStack builders for GUI icons (MiniMessage text, never italic). */
public final class Icons {

    private Icons() {}

    public static ItemStack item(Material mat, String name, String... lore) {
        return item(mat, name, List.of(lore));
    }

    public static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = ItemStack.of(mat);
        it.editMeta(meta -> {
            meta.itemName(Text.mm(name));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Text.mm(s));
            meta.lore(l);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        return it;
    }

    public static ItemStack glow(ItemStack it, boolean glow) {
        it.editMeta(m -> m.setEnchantmentGlintOverride(glow));
        return it;
    }

    public static ItemStack filler(Material pane) {
        ItemStack it = ItemStack.of(pane);
        it.editMeta(meta -> meta.setHideTooltip(true));
        return it;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = item(Material.PLAYER_HEAD, name, lore);
        it.editMeta(SkullMeta.class, m -> m.setOwningPlayer(owner));
        return it;
    }

    public static ItemStack toggle(Material mat, String name, boolean on, String... desc) {
        List<String> lore = new ArrayList<>(List.of(desc));
        lore.add("");
        lore.add(on ? "<green>● Enabled" : "<red>● Disabled");
        lore.add("<yellow>Click to toggle");
        return glow(item(mat, name, lore), on);
    }
}
