package com.elementalblades.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Small text helpers. All item text is non-italic. */
public final class Text {
    public static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_gray>[<gradient:#FF5A1F:#2F7BFF>Blades</gradient>]</dark_gray> ";

    private Text() {}

    public static Component mm(String s) {
        return MM.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static Component plain(String s, TextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component gray(String s) { return plain(s, NamedTextColor.GRAY); }

    public static void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(PREFIX + miniMessage));
    }

    /** Escape user input (team names, chat) so it can't inject MiniMessage tags. */
    public static String esc(String s) {
        return MM.escapeTags(s);
    }

    public static String seconds(long millis) {
        double s = millis / 1000.0;
        return s >= 10 ? String.valueOf((long) Math.ceil(s)) + "s" : String.format(java.util.Locale.ROOT, "%.1fs", s);
    }
}
