package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.ability.blades.FrostBlade;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Upgrade and configure the blade in the viewer's main hand. */
public final class BladeMenu extends Menu {

    public BladeMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 5, Text.mm("<dark_gray>Blade Forge"));
    }

    @Override
    protected void build() {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        BladeType type = BladeItems.getType(held);
        if (type == null) {
            set(22, Icons.item(Material.BARRIER, "<red>Hold a blade in your main hand"));
            back(40, () -> new MainMenu(plugin, viewer).open());
            return;
        }
        int level = BladeItems.getLevel(held);
        int max = plugin.getConfig().getInt("upgrades.max-level", 3);

        set(4, held.clone());

        set(19, abilityIcon(type, 1));
        set(25, abilityIcon(type, 2));
        if (plugin.abilities().ultimatesEnabled()) set(13, ultimateIcon(type));

        // Upgrade
        if (level >= max) {
            set(22, Icons.glow(Icons.item(Material.NETHER_STAR, "<gold><bold>Max level reached",
                    "<gray>This blade is fully upgraded."), true));
        } else {
            Map<Material, Integer> cost = cost(level + 1);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Level <white>" + BladeItems.roman(level) + "</white> → <green>" + BladeItems.roman(level + 1));
            lore.add("");
            lore.add("<gray>Cooldowns: <white>x" + plugin.getConfig().getDouble("upgrades.levels." + (level + 1) + ".cooldown-multiplier", 1));
            lore.add("<gray>Ability damage: <white>x" + plugin.getConfig().getDouble("upgrades.levels." + (level + 1) + ".damage-multiplier", 1));
            lore.add("");
            lore.add("<gold>Cost:");
            boolean affordable = true;
            for (var en : cost.entrySet()) {
                boolean has = viewer.getInventory().containsAtLeast(ItemStack.of(en.getKey()), en.getValue());
                affordable &= has;
                lore.add((has ? "<green>✔ " : "<red>✘ ") + "<white>" + en.getValue() + "x " + pretty(en.getKey()));
            }
            lore.add("");
            lore.add(affordable ? "<yellow>Click to upgrade" : "<red>You can't afford this yet");
            boolean canAfford = affordable;
            set(22, Icons.glow(Icons.item(Material.ANVIL, "<green><bold>Upgrade Blade", lore), canAfford), e -> upgrade());
        }

        // Passive options
        switch (type) {
            case FLAME -> {
                boolean fa = BladeItems.FLAME_FIRE_ASPECT.equals(BladeItems.getFlameChoice(held));
                set(30, Icons.glow(Icons.item(Material.MAGMA_CREAM, "<gold>Fire Resistance",
                        "<gray>Permanent Fire Resistance while held.", "", !fa ? "<green>● Selected" : "<yellow>Click to select"), !fa),
                        e -> chooseFlame(BladeItems.FLAME_FIRE_RES));
                set(32, Icons.glow(Icons.item(Material.BLAZE_POWDER, "<red>Fire Aspect",
                        "<gray>Fire Aspect " + BladeItems.roman(plugin.getConfig().getInt("blades.flame.passive.fire-aspect-level", 2))
                                + " on the blade.", "", fa ? "<green>● Selected" : "<yellow>Click to select"), fa),
                        e -> chooseFlame(BladeItems.FLAME_FIRE_ASPECT));
            }
            case FROST -> {
                boolean on = FrostBlade.frostWalkerEnabled(viewer);
                set(31, Icons.toggle(Material.ICE, "<aqua>Frost Walker", on, "<gray>Freeze water under your feet", "<gray>while holding the Frost Blade."),
                        e -> { FrostBlade.setFrostWalker(viewer, !on); refresh(); });
            }
            default -> {
                List<String> lore = new ArrayList<>();
                for (String l : type.passiveLines()) lore.add("<gray>" + l);
                set(31, Icons.item(Material.GLOWSTONE_DUST, "<gold>Passive", lore));
            }
        }

        back(40, () -> new MainMenu(plugin, viewer).open());
    }

    private ItemStack abilityIcon(BladeType type, int slot) {
        String name = slot == 1 ? type.ability1Name() : type.ability2Name();
        String desc = slot == 1 ? type.ability1Desc() : type.ability2Desc();
        long rem = plugin.abilities().cooldownRemaining(viewer, type, slot);
        double cd = plugin.getConfig().getDouble(type.path() + "ability" + slot + ".cooldown", 30);
        List<String> lore = new ArrayList<>(wrap(desc));
        lore.add("");
        lore.add("<gray>Base cooldown: <white>" + (int) cd + "s");
        lore.add("<gray>Use: <white>" + (slot == 1 ? "Right-click" : "Sneak + Right-click"));
        lore.add(rem > 0 ? "<red>Ready in " + Text.seconds(rem) : "<green>Ready");
        return Icons.item(slot == 1 ? Material.FIRE_CHARGE : Material.DRAGON_BREATH,
                "<#" + String.format("%06X", type.textColor().value()) + "><bold>" + name, lore);
    }

    private ItemStack ultimateIcon(BladeType type) {
        List<String> lore = new ArrayList<>(wrap(type.ultimateDesc()));
        lore.add("");
        boolean cdMode = "COOLDOWN".equalsIgnoreCase(plugin.getConfig().getString("ultimate.mode", "CHARGE"));
        if (cdMode) {
            lore.add("<gray>Cooldown: <white>" + (int) plugin.getConfig().getDouble("ultimate.cooldown", 120) + "s");
        } else {
            lore.add("<gray>Charges as you deal damage");
            lore.add("<gray>(kills give a big boost).");
            lore.add("<gray>Charge: <white>" + (int) (plugin.abilities().ultCharge(viewer) * 100) + "%");
        }
        lore.add("<gray>Use: <white>" + plugin.abilities().ultKeyName());
        boolean ready = plugin.abilities().ultReady(viewer, type);
        lore.add(ready ? "<green>READY" : "<red>Not ready");
        return Icons.glow(Icons.item(Material.NETHER_STAR, "<gold><bold>✦ " + type.ultimateName() + " <yellow>(Ultimate)", lore), ready);
    }

    private void chooseFlame(String choice) {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (BladeItems.getType(held) != BladeType.FLAME) { viewer.closeInventory(); return; }
        BladeItems.setFlameChoice(held, choice);
        viewer.getInventory().setItemInMainHand(held);
        refresh();
    }

    private Map<Material, Integer> cost(int toLevel) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("upgrades.cost." + toLevel);
        if (sec == null) return out;
        for (String k : sec.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null && sec.getInt(k) > 0) out.put(m, sec.getInt(k));
        }
        return out;
    }

    private void upgrade() {
        if (!viewer.hasPermission("elementalblades.upgrade")) { error("You don't have permission to upgrade."); return; }
        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (!BladeItems.isBlade(held)) { viewer.closeInventory(); return; }
        int level = BladeItems.getLevel(held);
        if (level >= plugin.getConfig().getInt("upgrades.max-level", 3)) return;
        Map<Material, Integer> cost = cost(level + 1);
        for (var en : cost.entrySet()) {
            if (!viewer.getInventory().containsAtLeast(ItemStack.of(en.getKey()), en.getValue())) {
                error("You need " + en.getValue() + "x " + pretty(en.getKey()) + ".");
                return;
            }
        }
        for (var en : cost.entrySet()) viewer.getInventory().removeItem(ItemStack.of(en.getKey(), en.getValue()));
        // removeItem may have shifted nothing in the main hand (blades aren't cost items), but re-read to be safe
        held = viewer.getInventory().getItemInMainHand();
        BladeItems.setLevel(held, level + 1);
        viewer.getInventory().setItemInMainHand(held);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
        success("Blade upgraded to level " + BladeItems.roman(level + 1) + "!");
        refresh();
    }

    static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }

    static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() > 34) { out.add("<gray>" + line.toString().trim()); line.setLength(0); }
            line.append(w).append(' ');
        }
        if (!line.isEmpty()) out.add("<gray>" + line.toString().trim());
        return out;
    }
}
