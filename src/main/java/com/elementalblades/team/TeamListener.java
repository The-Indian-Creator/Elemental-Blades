package com.elementalblades.team;

import com.elementalblades.ElementalBlades;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Chat prompts, team chat, home warm-up cancelling. */
public final class TeamListener implements Listener {

    private final ElementalBlades plugin;

    public TeamListener(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (plugin.prompts().handle(p, msg)) {
            e.setCancelled(true);
            return;
        }
        TeamManager tm = plugin.teams();
        if (!tm.enabled() || tm.get(p.getUniqueId()) == null) return;
        String prefix = plugin.getConfig().getString("teams.chat.prefix", "!");
        boolean prefixed = prefix != null && !prefix.isEmpty() && msg.startsWith(prefix) && msg.length() > prefix.length();
        if (prefixed || tm.inTeamChat(p.getUniqueId())) {
            e.setCancelled(true);
            String text = prefixed ? msg.substring(prefix.length()).trim() : msg;
            Bukkit.getScheduler().runTask(plugin, () -> tm.teamChat(p, text));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedBlock()) return;
        if (plugin.teams().hasWarmup(e.getPlayer().getUniqueId())
                && plugin.getConfig().getBoolean("teams.home.cancel-on-move", true)) {
            plugin.teams().cancelWarmup(e.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.teams().hasWarmup(p.getUniqueId())) {
            plugin.teams().cancelWarmup(p.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Team t = plugin.teams().get(e.getPlayer().getUniqueId());
        if (t != null) plugin.teams().updateNametag(t);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.teams().cancelWarmup(e.getPlayer().getUniqueId(), false);
        plugin.prompts().forget(e.getPlayer().getUniqueId());
    }
}
