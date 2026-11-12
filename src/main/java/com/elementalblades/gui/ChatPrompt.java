package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** "Type it in chat" input for GUIs (team name, invite name...). Type 'cancel' to abort. */
public final class ChatPrompt {

    private record Pending(Consumer<String> callback, long expires) {}

    private final ElementalBlades plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompt(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String question, Consumer<String> callback) {
        p.closeInventory();
        pending.put(p.getUniqueId(), new Pending(callback, System.currentTimeMillis() + 60_000));
        Text.send(p, question + " <dark_gray>(type <gray>cancel</gray> to abort)");
    }

    /** Called from the (async) chat event. @return true if the message was consumed. */
    public boolean handle(Player p, String message) {
        Pending pend = pending.remove(p.getUniqueId());
        if (pend == null) return false;
        if (pend.expires() < System.currentTimeMillis()) return false;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.trim().equalsIgnoreCase("cancel")) {
                Text.send(p, "<gray>Cancelled.");
                return;
            }
            pend.callback().accept(message.trim());
        });
        return true;
    }

    public void forget(UUID id) { pending.remove(id); }
}
