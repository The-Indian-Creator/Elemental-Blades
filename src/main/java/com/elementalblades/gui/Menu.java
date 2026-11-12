package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Base chest GUI: slots map to click actions. Rebuilt every time it's (re)opened. */
public abstract class Menu implements InventoryHolder {

    protected final ElementalBlades plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(ElementalBlades plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    protected abstract void build();

    public void open() {
        refresh();
        viewer.openInventory(inventory);
    }

    /** Rebuild contents in place (no re-open flicker). */
    public void refresh() {
        inventory.clear();
        actions.clear();
        fill(Material.BLACK_STAINED_GLASS_PANE);
        build();
    }

    protected void fill(Material pane) {
        ItemStack filler = Icons.filler(pane);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    protected void set(int slot, ItemStack item) { set(slot, item, null); }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    void handleClick(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a == null) return;
        click();
        // Run next tick: opening/closing inventories inside a click event is unsafe in Bukkit.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                a.accept(e);
            } catch (Exception ex) {
                plugin.getLogger().severe("GUI error: " + ex);
                ex.printStackTrace();
                Text.send(viewer, "<red>Something went wrong — check the console.");
            }
        });
    }

    protected void click() { viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f); }

    protected void error(String msg) {
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
        Text.send(viewer, "<red>" + msg);
    }

    protected void success(String msg) {
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
        Text.send(viewer, "<green>" + msg);
    }

    /** Back button at the given slot. */
    protected void back(int slot, Runnable to) {
        set(slot, Icons.item(Material.ARROW, "<gray>« Back"), e -> to.run());
    }

    protected static List<String> lines(String... l) { return new ArrayList<>(List.of(l)); }
}
