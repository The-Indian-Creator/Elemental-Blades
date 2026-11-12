package com.elementalblades.command;

import com.elementalblades.ElementalBlades;
import com.elementalblades.blade.BladeItems;
import com.elementalblades.blade.BladeType;
import com.elementalblades.gui.MainMenu;
import com.elementalblades.gui.TeamMenu;
import com.elementalblades.team.Team;
import com.elementalblades.team.TeamManager;
import com.elementalblades.util.Text;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Registers /blades, /team and /tc with Paper's Brigadier command API. */
public final class Commands {

    private final ElementalBlades plugin;

    public Commands(ElementalBlades plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(blades().build(), "Elemental Blades menu and admin commands", List.of("eb", "swords"));
            if (plugin.getConfig().getBoolean("teams.enabled", true)) {
                String name = plugin.getConfig().getString("teams.command", "team");
                registrar.register(team(name).build(), "Team commands", plugin.getConfig().getStringList("teams.aliases"));
                registrar.register(teamChat().build(), "Send a message to your team", List.of("teamchat"));
            }
        });
    }

    // ============================================================ helpers

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String name) {
        return io.papermc.paper.command.brigadier.Commands.literal(name);
    }

    private static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, T> arg(String name,
            com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return io.papermc.paper.command.brigadier.Commands.argument(name, type);
    }

    /** Runs the action only for players. */
    private static Command<CommandSourceStack> player(Consumer<CommandContext<CommandSourceStack>> action) {
        return ctx -> {
            if (!(ctx.getSource().getExecutor() instanceof Player) && !(ctx.getSource().getSender() instanceof Player)) {
                ctx.getSource().getSender().sendMessage(Text.MM.deserialize(Text.PREFIX + "<red>Players only."));
                return 0;
            }
            action.accept(ctx);
            return Command.SINGLE_SUCCESS;
        };
    }

    private static Player me(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player p) return p;
        return (Player) ctx.getSource().getSender();
    }

    private static void reply(CommandSender s, String err, String ok) {
        Text.send(s, err != null ? "<red>" + Text.esc(err) : ok);
    }

    private SuggestionProvider<CommandSourceStack> onlinePlayers() {
        return (ctx, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(p.getName());
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> teamMembers() {
        return (ctx, b) -> {
            if (ctx.getSource().getSender() instanceof Player p) {
                Team t = plugin.teams().get(p.getUniqueId());
                if (t != null) {
                    String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                    for (UUID id : t.members()) {
                        String n = plugin.teams().nameOf(id);
                        if (!id.equals(p.getUniqueId()) && n.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(n);
                    }
                }
            }
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> teamNames() {
        return (ctx, b) -> {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            for (Team t : plugin.teams().all()) if (t.name().toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(t.name());
            return b.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> bladeIds() {
        return (ctx, b) -> {
            for (BladeType t : BladeType.values()) if (t.id().startsWith(b.getRemaining().toLowerCase(Locale.ROOT))) b.suggest(t.id());
            return b.buildFuture();
        };
    }

    /** Resolve a member of the sender's team by name (works for offline members). */
    private UUID memberByName(Player me, String name) {
        Team t = plugin.teams().get(me.getUniqueId());
        if (t == null) return null;
        for (UUID id : t.members()) if (plugin.teams().nameOf(id).equalsIgnoreCase(name)) return id;
        return null;
    }

    // ============================================================ /blades

    private LiteralArgumentBuilder<CommandSourceStack> blades() {
        return lit("blades")
                .executes(player(ctx -> new MainMenu(plugin, me(ctx)).open()))
                .then(lit("give").requires(s -> s.getSender().hasPermission("elementalblades.admin"))
                        .then(arg("player", StringArgumentType.word()).suggests(onlinePlayers())
                                .then(arg("blade", StringArgumentType.word()).suggests(bladeIds())
                                        .executes(ctx -> give(ctx, 1))
                                        .then(arg("level", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(lit("pearl").requires(s -> s.getSender().hasPermission("elementalblades.admin"))
                        .executes(player(ctx -> me(ctx).getInventory().addItem(
                                BladeItems.silencePearl(plugin.getConfig().getInt("blades.silence.passive.pearl-uses", 5))))))
                .then(lit("reload").requires(s -> s.getSender().hasPermission("elementalblades.admin"))
                        .executes(ctx -> {
                            plugin.reload();
                            Text.send(ctx.getSource().getSender(), "<green>Config, recipes and resource pack reloaded.");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(lit("resetcooldowns").requires(s -> s.getSender().hasPermission("elementalblades.admin"))
                        .executes(player(ctx -> {
                            plugin.abilities().resetCooldowns(me(ctx).getUniqueId());
                            Text.send(me(ctx), "<green>Your cooldowns were reset.");
                        })))
                .then(lit("pack").executes(player(ctx -> plugin.pack().send(me(ctx), true))))
                .then(lit("upgrade").executes(player(ctx -> new com.elementalblades.gui.BladeMenu(plugin, me(ctx)).open())));
    }

    private int give(CommandContext<CommandSourceStack> ctx, int level) {
        CommandSender s = ctx.getSource().getSender();
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
        BladeType type = BladeType.fromId(StringArgumentType.getString(ctx, "blade"));
        if (target == null) { Text.send(s, "<red>Player not online."); return 0; }
        if (type == null) { Text.send(s, "<red>Unknown blade. Use: flame, frost, life, silence, lightning, velocity, water"); return 0; }
        int max = plugin.getConfig().getInt("upgrades.max-level", 3);
        int lvl = Math.min(level, max);
        target.getInventory().addItem(BladeItems.create(type, lvl))
                .values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        Text.send(s, "<green>Gave " + type.displayName() + " " + BladeItems.roman(lvl) + " to " + target.getName() + ".");
        return Command.SINGLE_SUCCESS;
    }

    // ============================================================ /team

    private LiteralArgumentBuilder<CommandSourceStack> team(String name) {
        TeamManager tm = plugin.teams();
        return lit(name)
                .requires(s -> s.getSender().hasPermission("elementalblades.team"))
                .executes(player(ctx -> new TeamMenu(plugin, me(ctx)).open()))
                .then(lit("create").then(arg("name", StringArgumentType.word()).executes(player(ctx -> {
                    String n = StringArgumentType.getString(ctx, "name");
                    reply(me(ctx), tm.create(me(ctx), n), "<green>Team <white>" + Text.esc(n) + "</white> created! Invite players with /team invite <player>.");
                }))))
                .then(lit("invite").then(arg("player", StringArgumentType.word()).suggests(onlinePlayers()).executes(player(ctx -> {
                    Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "player"));
                    if (target == null) { Text.send(me(ctx), "<red>That player isn't online."); return; }
                    String err = tm.invite(me(ctx), target);
                    reply(me(ctx), err, "<green>Invited " + target.getName() + ".");
                    if (err == null) tm.notifyInvite(me(ctx), target);
                }))))
                .then(lit("accept").executes(player(ctx -> {
                    TeamManager.Invite inv = tm.pendingInvite(me(ctx).getUniqueId());
                    if (inv == null) { Text.send(me(ctx), "<red>You have no pending invite."); return; }
                    reply(me(ctx), tm.join(me(ctx), inv.team()), "<green>You joined " + Text.esc(inv.team()) + "!");
                })))
                .then(lit("deny").executes(player(ctx -> tm.denyInvite(me(ctx)))))
                .then(lit("join").then(arg("team", StringArgumentType.word()).suggests(teamNames()).executes(player(ctx -> {
                    String n = StringArgumentType.getString(ctx, "team");
                    reply(me(ctx), tm.join(me(ctx), n), "<green>You joined " + Text.esc(n) + "!");
                }))))
                .then(lit("leave").executes(player(ctx -> reply(me(ctx), tm.leave(me(ctx)), "<yellow>You left your team."))))
                .then(lit("disband").then(lit("confirm").executes(player(ctx -> {
                    Team t = tm.get(me(ctx).getUniqueId());
                    if (t == null || !t.owner().equals(me(ctx).getUniqueId())) { Text.send(me(ctx), "<red>Only the team owner can disband."); return; }
                    tm.broadcast(t, "<red>The team was disbanded.");
                    tm.disband(t);
                }))).executes(player(ctx -> Text.send(me(ctx), "<yellow>Type <white>/team disband confirm</white> to delete your team."))))
                .then(memberCmd("kick", tm::kick, "<yellow>Kicked."))
                .then(memberCmd("promote", tm::promote, "<green>Promoted."))
                .then(memberCmd("demote", tm::demote, "<gray>Demoted."))
                .then(memberCmd("transfer", tm::transfer, "<gold>Ownership transferred."))
                .then(lit("sethome").executes(player(ctx -> reply(me(ctx), tm.setHome(me(ctx)), "<green>Team home set."))))
                .then(lit("home").executes(player(ctx -> tm.teleportHome(me(ctx)))))
                .then(lit("chest").executes(player(ctx -> {
                    Team t = tm.get(me(ctx).getUniqueId());
                    if (t == null) { Text.send(me(ctx), "<red>You are not in a team."); return; }
                    me(ctx).openInventory(tm.chest(t));
                })))
                .then(lit("chat").executes(player(ctx -> {
                    boolean on = tm.toggleTeamChat(me(ctx).getUniqueId());
                    Text.send(me(ctx), on ? "<aqua>Team chat ON — your messages go to your team." : "<gray>Team chat OFF.");
                })).then(arg("message", StringArgumentType.greedyString()).executes(player(ctx ->
                        tm.teamChat(me(ctx), StringArgumentType.getString(ctx, "message"))))))
                .then(lit("ff").executes(player(ctx -> toggle(me(ctx), "Friendly fire", t -> { t.friendlyFire(!t.friendlyFire()); return t.friendlyFire(); }))))
                .then(lit("abilityff").executes(player(ctx -> toggle(me(ctx), "Ability friendly fire", t -> { t.abilityFriendlyFire(!t.abilityFriendlyFire()); return t.abilityFriendlyFire(); }))))
                .then(lit("open").executes(player(ctx -> toggle(me(ctx), "Open joining", t -> { t.open(!t.open()); return t.open(); }))))
                .then(lit("rename").then(arg("name", StringArgumentType.word()).executes(player(ctx ->
                        reply(me(ctx), tm.rename(me(ctx), StringArgumentType.getString(ctx, "name")), "<green>Team renamed.")))))
                .then(lit("color").then(arg("color", StringArgumentType.word()).suggests((c, b) -> {
                    for (NamedTextColor col : TeamManager.COLORS) b.suggest(col.toString());
                    return b.buildFuture();
                }).executes(player(ctx -> {
                    Team t = tm.get(me(ctx).getUniqueId());
                    if (t == null || !t.owner().equals(me(ctx).getUniqueId())) { Text.send(me(ctx), "<red>Only the owner can change the colour."); return; }
                    NamedTextColor c = NamedTextColor.NAMES.value(StringArgumentType.getString(ctx, "color").toLowerCase(Locale.ROOT));
                    if (c == null) { Text.send(me(ctx), "<red>Unknown colour."); return; }
                    t.color(c);
                    tm.updateNametag(t);
                    tm.markDirty();
                    Text.send(me(ctx), "<" + c.asHexString() + ">Team colour changed.");
                }))))
                .then(lit("info").executes(player(ctx -> info(me(ctx), tm.get(me(ctx).getUniqueId()))))
                        .then(arg("team", StringArgumentType.word()).suggests(teamNames()).executes(player(ctx ->
                                info(me(ctx), tm.byName(StringArgumentType.getString(ctx, "team")))))))
                .then(lit("list").executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (tm.all().isEmpty()) { Text.send(s, "<gray>There are no teams yet."); return 1; }
                    Text.send(s, "<aqua>Teams (" + tm.all().size() + "):");
                    for (Team t : tm.all()) s.sendMessage(Text.MM.deserialize(" <dark_gray>•</dark_gray> <" + t.color().asHexString() + ">"
                            + Text.esc(t.name()) + "</" + t.color().asHexString() + "> <gray>(" + t.members().size() + " members"
                            + (t.open() ? ", open" : "") + ")"));
                    return 1;
                }))
                .then(lit("help").executes(ctx -> { help(ctx.getSource().getSender(), name); return 1; }));
    }

    private LiteralArgumentBuilder<CommandSourceStack> memberCmd(String sub, java.util.function.BiFunction<Player, UUID, String> fn, String ok) {
        return lit(sub).then(arg("member", StringArgumentType.word()).suggests(teamMembers()).executes(player(ctx -> {
            Player p = me(ctx);
            UUID target = memberByName(p, StringArgumentType.getString(ctx, "member"));
            if (target == null) { Text.send(p, "<red>That player isn't in your team."); return; }
            reply(p, fn.apply(p, target), ok);
        })));
    }

    private void toggle(Player p, String what, java.util.function.Function<Team, Boolean> fn) {
        Team t = plugin.teams().get(p.getUniqueId());
        if (t == null) { Text.send(p, "<red>You are not in a team."); return; }
        if (!t.canManage(p.getUniqueId())) { Text.send(p, "<red>Only the owner or admins can change settings."); return; }
        boolean now = fn.apply(t);
        plugin.teams().markDirty();
        plugin.teams().broadcast(t, "<gray>" + what + " is now " + (now ? "<green>ON" : "<red>OFF") + "<gray> (by " + p.getName() + ").");
    }

    private void info(Player p, Team t) {
        if (t == null) { Text.send(p, "<red>Team not found (or you're not in one)."); return; }
        String c = t.color().asHexString();
        Text.send(p, "<" + c + "><bold>" + Text.esc(t.name()));
        StringBuilder members = new StringBuilder();
        for (UUID id : t.members()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean online = op.isOnline();
            String role = switch (t.role(id)) { case OWNER -> "★"; case ADMIN -> "✦"; default -> ""; };
            members.append(online ? "<green>" : "<gray>").append(role).append(plugin.teams().nameOf(id)).append("</") .append(online ? "green" : "gray").append("> ");
        }
        p.sendMessage(Text.MM.deserialize(" <gray>Members: " + members));
        p.sendMessage(Text.MM.deserialize(" <gray>Friendly fire: " + (t.friendlyFire() ? "<green>on" : "<red>off")
                + " <gray>| Ability FF: " + (t.abilityFriendlyFire() ? "<green>on" : "<red>off")
                + " <gray>| " + (t.open() ? "<green>Open" : "<yellow>Invite only")));
    }

    private void help(CommandSender s, String cmd) {
        String c = "/" + cmd;
        Text.send(s, "<aqua>Team commands:");
        for (String line : List.of(
                c + " — open the team menu",
                c + " create <name> | invite <player> | accept | deny | join <team>",
                c + " leave | kick <member> | promote/demote <member> | transfer <member>",
                c + " sethome | home | chest | chat [message] | /tc <message>",
                c + " ff | abilityff | open | rename <name> | color <colour>",
                c + " info [team] | list | disband confirm")) {
            s.sendMessage(Text.MM.deserialize(" <gray>" + Text.esc(line)));
        }
    }

    // ============================================================ /tc

    private LiteralArgumentBuilder<CommandSourceStack> teamChat() {
        return lit("tc").requires(s -> s.getSender().hasPermission("elementalblades.team"))
                .executes(player(ctx -> {
                    boolean on = plugin.teams().toggleTeamChat(me(ctx).getUniqueId());
                    Text.send(me(ctx), on ? "<aqua>Team chat ON." : "<gray>Team chat OFF.");
                }))
                .then(arg("message", StringArgumentType.greedyString()).executes(player(ctx ->
                        plugin.teams().teamChat(me(ctx), StringArgumentType.getString(ctx, "message")))));
    }
}
