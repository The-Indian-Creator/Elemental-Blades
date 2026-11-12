package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.team.Team;
import com.elementalblades.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Lists teams that anyone can join. */
public final class OpenTeamsMenu extends Menu {

    public OpenTeamsMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 6, Text.mm("<dark_aqua>Open Teams"));
    }

    @Override
    protected void build() {
        int slot = 0;
        for (Team t : plugin.teams().all()) {
            if (!t.open() || slot >= 45) continue;
            boolean full = t.members().size() >= plugin.teams().maxMembers();
            set(slot++, Icons.item(TeamMenu.bannerFor(t.color()), "<" + t.color().asHexString() + "><bold>" + Text.esc(t.name()),
                    "<gray>Owner: <white>" + plugin.teams().nameOf(t.owner()),
                    "<gray>Members: <white>" + t.members().size() + "/" + plugin.teams().maxMembers(),
                    "", full ? "<red>Full" : "<green>Click to join"), full ? null : e -> {
                String err = plugin.teams().join(viewer, t.name());
                if (err != null) { error(err); return; }
                new TeamMenu(plugin, viewer).open();
            });
        }
        if (slot == 0) set(22, Icons.item(Material.STRUCTURE_VOID, "<gray>No open teams right now"));
        back(49, () -> new TeamMenu(plugin, viewer).open());
    }
}
