package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.team.Team;
import com.elementalblades.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /blades — hub for blade upgrades, recipes and teams. */
public final class MainMenu extends Menu {

    public MainMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 3, Text.mm("<gradient:#FF5A1F:#2F7BFF><bold>Elemental Blades"));
    }

    @Override
    protected void build() {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        BladeType type = BladeItems.getType(held);
        if (type != null) {
            ItemStack icon = held.clone();
            icon.editMeta(m -> {
                var lore = m.lore() == null ? new java.util.ArrayList<net.kyori.adventure.text.Component>() : new java.util.ArrayList<>(m.lore());
                lore.add(net.kyori.adventure.text.Component.empty());
                lore.add(Text.mm("<yellow>Click to upgrade & configure"));
                m.lore(lore);
            });
            set(11, icon, e -> new BladeMenu(plugin, viewer).open());
        } else {
            set(11, Icons.item(Material.BARRIER, "<red>No blade in hand",
                    "<gray>Hold one of your blades in your", "<gray>main hand to upgrade or configure it."));
        }

        set(13, Icons.item(Material.CRAFTING_TABLE, "<gold><bold>Blade Codex & Recipes",
                "<gray>All 7 blades, their abilities", "<gray>and how to craft them.", "", "<yellow>Click to open"),
                e -> new CodexMenu(plugin, viewer).open());

        if (plugin.teams().enabled()) {
            Team t = plugin.teams().get(viewer.getUniqueId());
            List<String> lore = t == null
                    ? List.of("<gray>You're not in a team.", "<gray>Create one or accept an invite.", "", "<yellow>Click to open")
                    : List.of("<gray>Team: <white>" + Text.esc(t.name()), "<gray>Members: <white>" + t.members().size(),
                        "", "<yellow>Click to manage");
            set(15, Icons.item(Material.WHITE_BANNER, "<aqua><bold>Team", lore), e -> new TeamMenu(plugin, viewer).open());
        }

        set(22, Icons.item(Material.BOOK, "<white>Controls",
                "<gray>Right-click: <white>Ability 1",
                "<gray>Sneak + Right-click: <white>Ability 2",
                !plugin.getConfig().getBoolean("controls.swap-hand-key", true) ? ""
                        : !plugin.abilities().ultimatesEnabled() || "NONE".equalsIgnoreCase(plugin.getConfig().getString("controls.ultimate-key", "SWAP_HAND"))
                        ? "<gray>F (swap hands) / Sneak + F also work"
                        : "SNEAK_SWAP".equalsIgnoreCase(plugin.getConfig().getString("controls.ultimate-key", "SWAP_HAND"))
                        ? "<gray>F: <white>Ability 1  <gray>Sneak + F: <gold>Ultimate" : "<gray>F (swap hands): <gold>Ultimate",
                "<gray>Passives work while the blade is in your main hand."));

        if (viewer.hasPermission("elementalblades.admin")) {
            set(26, Icons.item(Material.COMMAND_BLOCK, "<red>Admin: Give Blades", "<gray>Spawn any blade at any level."),
                    e -> new AdminMenu(plugin, viewer).open());
        }
    }
}
