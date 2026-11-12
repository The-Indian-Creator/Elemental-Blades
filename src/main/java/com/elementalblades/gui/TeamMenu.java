package com.elementalblades.gui;

import com.elementalblades.ElementalBlades;
import com.elementalblades.team.Team;
import com.elementalblades.team.TeamManager;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Team hub: members, chest, home, settings. */
public final class TeamMenu extends Menu {

    public TeamMenu(ElementalBlades plugin, Player viewer) {
        super(plugin, viewer, 5, Text.mm("<dark_aqua><bold>Team"));
    }

    private TeamManager tm() { return plugin.teams(); }

    @Override
    protected void build() {
        Team t = tm().get(viewer.getUniqueId());
        if (t == null) buildNoTeam();
        else buildTeam(t);
        back(0, () -> new MainMenu(plugin, viewer).open());
    }

    // ------------------------------------------------------------ not in a team

    private void buildNoTeam() {
        set(20, Icons.item(Material.WRITABLE_BOOK, "<green><bold>Create a Team",
                "<gray>Start your own team and", "<gray>invite your friends.", "", "<yellow>Click, then type a name in chat"),
                e -> plugin.prompts().ask(viewer, "<aqua>Type a name for your new team:", name -> {
                    String err = tm().create(viewer, name);
                    if (err != null) { error(err); return; }
                    success("Team " + name + " created!");
                    new TeamMenu(plugin, viewer).open();
                }));

        TeamManager.Invite inv = tm().pendingInvite(viewer.getUniqueId());
        if (inv != null) {
            set(22, Icons.glow(Icons.item(Material.PAPER, "<gold><bold>Invite from " + Text.esc(inv.team()),
                    "<gray>Invited by <white>" + tm().nameOf(inv.inviter()), "", "<green>Click to join"), true), e -> {
                String err = tm().join(viewer, inv.team());
                if (err != null) { error(err); return; }
                new TeamMenu(plugin, viewer).open();
            });
        } else {
            set(22, Icons.item(Material.MAP, "<gray>No pending invites",
                    "<gray>Ask a team owner to run", "<white>/team invite " + viewer.getName()));
        }

        set(24, Icons.item(Material.SPYGLASS, "<aqua><bold>Browse Open Teams", "<gray>Join a team that doesn't", "<gray>need an invite."),
                e -> new OpenTeamsMenu(plugin, viewer).open());
    }

    // ------------------------------------------------------------ in a team

    private void buildTeam(Team t) {
        UUID me = viewer.getUniqueId();
        boolean manager = t.canManage(me);
        boolean owner = t.owner().equals(me);
        String color = "<" + t.color().asHexString() + ">";

        set(4, Icons.item(bannerFor(t.color()), color + "<bold>" + Text.esc(t.name()),
                "<gray>Owner: <white>" + tm().nameOf(t.owner()),
                "<gray>Members: <white>" + t.members().size() + "/" + tm().maxMembers(),
                "<gray>Your rank: <white>" + t.role(me),
                "<gray>Friendly fire: " + onOff(t.friendlyFire()),
                "<gray>Ability friendly fire: " + onOff(t.abilityFriendlyFire()),
                "<gray>Joining: <white>" + (t.open() ? "Open" : "Invite only")));

        set(8, Icons.toggle(Material.OAK_SIGN, "<aqua>Team Chat", tm().inTeamChat(me),
                "<gray>Send all your chat messages", "<gray>to your team only.",
                "<gray>Or start a message with <white>" + plugin.getConfig().getString("teams.chat.prefix", "!")),
                e -> { tm().toggleTeamChat(me); refresh(); });

        // Members (rows 2-3)
        List<UUID> members = new ArrayList<>(t.members());
        for (int i = 0; i < members.size() && i < 18; i++) {
            UUID id = members.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean online = Bukkit.getPlayer(id) != null;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Rank: <white>" + t.role(id));
            lore.add(online ? "<green>● Online" : "<dark_gray>● Offline");
            if (manager && !id.equals(me)) { lore.add(""); lore.add("<yellow>Click to manage"); }
            set(9 + i, Icons.head(op, (online ? "<green>" : "<gray>") + tm().nameOf(id), lore),
                    manager && !id.equals(me) ? e -> new MemberMenu(plugin, viewer, id).open() : null);
        }

        // Actions (row 5)
        set(36, Icons.item(Material.ENDER_CHEST, "<dark_purple><bold>Team Chest",
                "<gray>A shared double chest for", "<gray>everyone in the team.", "", "<yellow>Click to open"),
                e -> viewer.openInventory(tm().chest(t)));
        set(37, Icons.item(Material.RED_BED, "<red><bold>Team Home",
                t.home() == null ? "<gray>No home set yet." : "<gray>Teleport to your team home.",
                t.home() == null ? "" : "<yellow>Click to teleport"),
                e -> { viewer.closeInventory(); tm().teleportHome(viewer); });

        if (manager) {
            set(38, Icons.item(Material.COMPASS, "<green>Set Team Home", "<gray>Set the home to where", "<gray>you're standing."), e -> {
                String err = tm().setHome(viewer);
                if (err != null) error(err); else refresh();
            });
            set(39, Icons.item(Material.NAME_TAG, "<aqua>Invite Player", "<gray>Click, then type their name."),
                    e -> plugin.prompts().ask(viewer, "<aqua>Type the name of the player to invite:", name -> {
                        Player target = Bukkit.getPlayerExact(name);
                        if (target == null) { error("Player " + name + " isn't online."); return; }
                        String err = tm().invite(viewer, target);
                        if (err != null) { error(err); return; }
                        success("Invited " + target.getName() + ".");
                        tm().notifyInvite(viewer, target);
                    }));
            set(40, Icons.toggle(Material.IRON_SWORD, "<red>Friendly Fire", t.friendlyFire(),
                    "<gray>Can team-mates hurt each", "<gray>other with normal attacks?"),
                    e -> { t.friendlyFire(!t.friendlyFire()); tm().markDirty(); refresh(); });
            set(41, Icons.toggle(Material.BLAZE_POWDER, "<gold>Ability Friendly Fire", t.abilityFriendlyFire(),
                    "<gray>Can blade abilities hit team-mates?",
                    "<dark_gray>Blades with spare-teammates: true",
                    "<dark_gray>never hit team-mates regardless."),
                    e -> { t.abilityFriendlyFire(!t.abilityFriendlyFire()); tm().markDirty(); refresh(); });
            set(42, Icons.toggle(Material.OAK_DOOR, "<yellow>Open Team", t.open(),
                    "<gray>Anyone can join without", "<gray>an invite when enabled."),
                    e -> { t.open(!t.open()); tm().markDirty(); refresh(); });
        }
        if (owner) {
            set(43, Icons.item(dyeFor(t.color()), color + "Team Colour", "<gray>Click to cycle the colour", "<gray>used for names and chat."), e -> {
                int idx = TeamManager.COLORS.indexOf(t.color());
                t.color(TeamManager.COLORS.get((idx + 1) % TeamManager.COLORS.size()));
                tm().updateNametag(t);
                tm().markDirty();
                refresh();
            });
        }
        if (owner) {
            set(44, Icons.item(Material.TNT, "<dark_red><bold>Disband Team", "<gray>Deletes the team. Chest items", "<gray>are given to you."),
                    e -> new ConfirmMenu(plugin, viewer, "Disband " + t.name() + "?", () -> {
                        tm().broadcast(t, "<red>The team was disbanded.");
                        tm().disband(t);
                        viewer.closeInventory();
                    }, () -> new TeamMenu(plugin, viewer).open()).open());
        } else {
            set(44, Icons.item(Material.BARRIER, "<red><bold>Leave Team"),
                    e -> new ConfirmMenu(plugin, viewer, "Leave " + t.name() + "?", () -> {
                        String err = tm().leave(viewer);
                        if (err != null) error(err);
                        viewer.closeInventory();
                    }, () -> new TeamMenu(plugin, viewer).open()).open());
        }
    }

    private static String onOff(boolean b) { return b ? "<green>On" : "<red>Off"; }

    static Material bannerFor(NamedTextColor c) { return colored(c, "BANNER"); }
    static Material dyeFor(NamedTextColor c) { return colored(c, "DYE"); }

    private static Material colored(NamedTextColor c, String suffix) {
        String dye = switch (c.toString()) {
            case "red", "dark_red" -> "RED";
            case "green" -> "LIME";
            case "dark_green" -> "GREEN";
            case "yellow" -> "YELLOW";
            case "gold" -> "ORANGE";
            case "light_purple" -> "MAGENTA";
            case "dark_purple" -> "PURPLE";
            case "blue", "dark_blue" -> "BLUE";
            case "dark_aqua" -> "CYAN";
            case "aqua" -> "LIGHT_BLUE";
            case "black" -> "BLACK";
            case "gray" -> "LIGHT_GRAY";
            case "dark_gray" -> "GRAY";
            default -> "WHITE";
        };
        Material m = Material.matchMaterial(dye + "_" + suffix);
        return m == null ? Material.WHITE_BANNER : m;
    }
}
