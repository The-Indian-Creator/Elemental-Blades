package com.elementalblades;

import com.elementalblades.ability.AbilityManager;
import com.elementalblades.ability.blades.SilenceBlade;
import com.elementalblades.blade.RecipeManager;
import com.elementalblades.combat.BladeListener;
import com.elementalblades.combat.CombatRules;
import com.elementalblades.combat.StatusManager;
import com.elementalblades.command.Commands;
import com.elementalblades.gui.ChatPrompt;
import com.elementalblades.gui.GuiListener;
import com.elementalblades.pack.ResourcePackManager;
import com.elementalblades.team.TeamListener;
import com.elementalblades.team.TeamManager;
import com.elementalblades.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** ElementalBlades — 7 craftable elemental swords + an integrated PvP team system. */
public final class ElementalBlades extends JavaPlugin {

    private static ElementalBlades instance;

    private TeamManager teams;
    private CombatRules combat;
    private StatusManager status;
    private AbilityManager abilities;
    private RecipeManager recipes;
    private ResourcePackManager pack;
    private ChatPrompt prompts;

    public static ElementalBlades get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Keys.init(this);

        teams = new TeamManager(this);
        teams.load();
        combat = new CombatRules(this);
        status = new StatusManager(this);
        abilities = new AbilityManager(this);
        recipes = new RecipeManager(this);
        recipes.registerAll();
        prompts = new ChatPrompt(this);
        pack = new ResourcePackManager(this);
        pack.start();

        abilities.blade(SilenceBlade.class).recoverDomes();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BladeListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new TeamListener(this), this);
        pm.registerEvents(recipes, this);
        pm.registerEvents(pack, this);

        new Commands(this).register();

        // Autosave teams every 2 minutes if something changed
        Bukkit.getScheduler().runTaskTimer(this, () -> teams.saveIfDirty(), 2400L, 2400L);

        for (Player p : Bukkit.getOnlinePlayers()) recipes.discover(p);
        getLogger().info("ElementalBlades enabled — 7 blades, teams " + (teams.enabled() ? "on" : "off") + ".");
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.shutdown();
        if (status != null) status.shutdown();
        if (teams != null) teams.save();
        if (recipes != null) recipes.unregisterAll();
        if (pack != null) pack.shutdown();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof com.elementalblades.gui.Menu
                    || p.getOpenInventory().getTopInventory().getHolder() instanceof TeamManager.ChestHolder) {
                p.closeInventory();
            }
        }
        instance = null;
    }

    /** /blades reload */
    public void reload() {
        reloadConfig();
        recipes.registerAll();
        for (Player p : Bukkit.getOnlinePlayers()) recipes.discover(p);
        pack.start();
    }

    public TeamManager teams() { return teams; }
    public CombatRules combat() { return combat; }
    public StatusManager status() { return status; }
    public AbilityManager abilities() { return abilities; }
    public RecipeManager recipes() { return recipes; }
    public ResourcePackManager pack() { return pack; }
    public ChatPrompt prompts() { return prompts; }
}
