package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Yes / No confirmation. */
public final class ConfirmMenu extends Menu {

    private final String question;
    private final Runnable yes, no;

    public ConfirmMenu(ElementalBlades plugin, Player viewer, String question, Runnable yes, Runnable no) {
        super(plugin, viewer, 3, Text.mm("<dark_red>Are you sure?"));
        this.question = question;
        this.yes = yes;
        this.no = no;
    }

    @Override
    protected void build() {
        set(13, Icons.item(Material.PAPER, "<white>" + Text.esc(question)));
        set(11, Icons.item(Material.LIME_CONCRETE, "<green><bold>Yes"), e -> yes.run());
        set(15, Icons.item(Material.RED_CONCRETE, "<red><bold>No"), e -> no.run());
    }
}
