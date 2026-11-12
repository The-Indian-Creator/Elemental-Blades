package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.team.Team;
import com.elementalblades.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Manage one team member: promote/demote/transfer/kick. */
public final class MemberMenu extends Menu {

    private final UUID target;

    public MemberMenu(ElementalBlades plugin, Player viewer, UUID target) {
        super(plugin, viewer, 3, Text.mm("<dark_aqua>Member: " + plugin.teams().nameOf(target)));
        this.target = target;
    }

    @Override
    protected void build() {
        Team t = plugin.teams().get(viewer.getUniqueId());
        if (t == null || !t.isMember(target)) { new TeamMenu(plugin, viewer).open(); return; }
        String name = plugin.teams().nameOf(target);
        set(4, Icons.head(Bukkit.getOfflinePlayer(target), "<white>" + name, List.of("<gray>Rank: <white>" + t.role(target))));
        boolean owner = t.owner().equals(viewer.getUniqueId());

        if (owner) {
            if (t.role(target) == Team.Role.ADMIN) {
                set(10, Icons.item(Material.IRON_INGOT, "<gray>Demote to Member"), e -> result(plugin.teams().demote(viewer, target)));
            } else {
                set(10, Icons.item(Material.GOLD_INGOT, "<aqua>Promote to Admin",
                        "<gray>Admins can invite, kick members,", "<gray>set home and change settings."),
                        e -> result(plugin.teams().promote(viewer, target)));
            }
            set(12, Icons.item(Material.GOLDEN_HELMET, "<gold>Transfer Ownership", "<gray>Make " + name + " the owner."),
                    e -> new ConfirmMenu(plugin, viewer, "Give " + t.name() + " to " + name + "?",
                            () -> { result(plugin.teams().transfer(viewer, target)); new TeamMenu(plugin, viewer).open(); },
                            () -> open()).open());
        }
        set(16, Icons.item(Material.IRON_DOOR, "<red>Kick from Team"),
                e -> new ConfirmMenu(plugin, viewer, "Kick " + name + "?",
                        () -> { result(plugin.teams().kick(viewer, target)); new TeamMenu(plugin, viewer).open(); },
                        () -> open()).open());
        back(22, () -> new TeamMenu(plugin, viewer).open());
    }

    private void result(String err) {
        if (err != null) error(err);
        else { success("Done."); refresh(); }
    }
}
