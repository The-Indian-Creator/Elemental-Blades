package com.elementalblades.team;

import com.elementalblades.ElementalBlades;
import com.elementalblades.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Owns all teams, invites and team persistence (plugins/ElementalBlades/teams.yml). */
public final class TeamManager {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");
    public static final List<NamedTextColor> COLORS = List.of(
            NamedTextColor.AQUA, NamedTextColor.RED, NamedTextColor.GREEN, NamedTextColor.YELLOW,
            NamedTextColor.LIGHT_PURPLE, NamedTextColor.GOLD, NamedTextColor.BLUE, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_PURPLE, NamedTextColor.WHITE);

    private final ElementalBlades plugin;
    private final File file;
    private final Map<String, Team> teams = new LinkedHashMap<>();      // lower-case name -> team
    private final Map<UUID, Team> byPlayer = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();          // invitee -> invite
    private final Set<UUID> teamChat = new HashSet<>();
    private final Map<UUID, BukkitTask> homeWarmups = new HashMap<>();
    private final Map<UUID, Long> homeCooldowns = new HashMap<>();
    private boolean dirty;

    public record Invite(String team, UUID inviter, long expires) {}

    /** Holder for the shared team ender chest so the GUI listener leaves it alone. */
    public static final class ChestHolder implements InventoryHolder {
        private final String team;
        private Inventory inv;
        ChestHolder(String team) { this.team = team; }
        public String team() { return team; }
        @Override public Inventory getInventory() { return inv; }
    }

    public TeamManager(ElementalBlades plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    // ------------------------------------------------------------ queries

    public Team get(UUID player) { return byPlayer.get(player); }
    public Team byName(String name) { return name == null ? null : teams.get(name.toLowerCase(Locale.ROOT)); }
    public Collection<Team> all() { return Collections.unmodifiableCollection(teams.values()); }

    public boolean sameTeam(UUID a, UUID b) {
        Team t = byPlayer.get(a);
        return t != null && t.isMember(b);
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("teams.enabled", true); }

    // ------------------------------------------------------------ mutations (return error message or null)

    public String validateName(String name) {
        int min = plugin.getConfig().getInt("teams.name-min-length", 3);
        int max = plugin.getConfig().getInt("teams.name-max-length", 16);
        if (name == null || name.length() < min || name.length() > max)
            return "Team names must be " + min + "-" + max + " characters.";
        if (!NAME.matcher(name).matches()) return "Use only letters, numbers and _.";
        if (byName(name) != null) return "A team with that name already exists.";
        return null;
    }

    public String create(Player owner, String name) {
        if (get(owner.getUniqueId()) != null) return "You are already in a team. Leave it first.";
        String err = validateName(name);
        if (err != null) return err;
        Team t = new Team(name, owner.getUniqueId());
        t.friendlyFire(plugin.getConfig().getBoolean("teams.default-friendly-fire", false));
        t.abilityFriendlyFire(plugin.getConfig().getBoolean("teams.default-ability-friendly-fire", false));
        t.color(COLORS.get(teams.size() % COLORS.size()));
        teams.put(name.toLowerCase(Locale.ROOT), t);
        byPlayer.put(owner.getUniqueId(), t);
        updateNametag(t);
        markDirty();
        return null;
    }

    public void disband(Team t) {
        // Drop the chest contents at the owner if online, so items aren't silently deleted.
        Inventory chest = t.chest;
        if (chest != null) {
            for (var viewer : new ArrayList<>(chest.getViewers())) viewer.closeInventory();
            Player owner = Bukkit.getPlayer(t.owner());
            if (owner != null) {
                for (ItemStack it : chest.getContents()) {
                    if (it == null || it.getType().isAir()) continue;
                    owner.getInventory().addItem(it).values()
                            .forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
                }
                Text.send(owner, "<gray>The team chest contents were returned to you.");
            }
        }
        for (UUID m : t.members()) {
            byPlayer.remove(m);
            teamChat.remove(m);
        }
        teams.remove(t.name().toLowerCase(Locale.ROOT));
        removeNametag(t);
        markDirty();
    }

    public String invite(Player inviter, Player target) {
        Team t = get(inviter.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (!t.canManage(inviter.getUniqueId())) return "Only the owner or admins can invite.";
        if (get(target.getUniqueId()) != null) return target.getName() + " is already in a team.";
        if (t.members().size() >= maxMembers()) return "Your team is full.";
        long expires = System.currentTimeMillis() + plugin.getConfig().getInt("teams.invite-expire-seconds", 60) * 1000L;
        invites.put(target.getUniqueId(), new Invite(t.name(), inviter.getUniqueId(), expires));
        return null;
    }

    /** Tells the invited player, with clickable accept/deny. */
    public void notifyInvite(Player inviter, Player target) {
        Team t = get(inviter.getUniqueId());
        if (t == null) return;
        String cmd = plugin.getConfig().getString("teams.command", "team");
        int secs = plugin.getConfig().getInt("teams.invite-expire-seconds", 60);
        target.sendMessage(Text.MM.deserialize(Text.PREFIX + "<aqua>" + Text.esc(inviter.getName())
                + "</aqua> invited you to <" + t.color().asHexString() + ">" + Text.esc(t.name()) + "</" + t.color().asHexString() + ">. "
                + "<green><click:run_command:'/" + cmd + " accept'><hover:show_text:'Join the team'>[Accept]</hover></click></green> "
                + "<red><click:run_command:'/" + cmd + " deny'><hover:show_text:'Decline'>[Deny]</hover></click></red> "
                + "<dark_gray>(expires in " + secs + "s)"));
        target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    public void denyInvite(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null) { Text.send(p, "<red>You have no pending invite."); return; }
        Text.send(p, "<gray>Invite declined.");
        Player inviter = Bukkit.getPlayer(inv.inviter());
        if (inviter != null) Text.send(inviter, "<yellow>" + p.getName() + " declined your invite.");
    }

    public Invite pendingInvite(UUID player) {
        Invite inv = invites.get(player);
        if (inv != null && inv.expires() < System.currentTimeMillis()) {
            invites.remove(player);
            return null;
        }
        return inv;
    }

    /** Join via invite (or open team). */
    public String join(Player p, String teamName) {
        if (get(p.getUniqueId()) != null) return "You are already in a team.";
        Team t = byName(teamName);
        if (t == null) return "That team doesn't exist.";
        Invite inv = pendingInvite(p.getUniqueId());
        boolean invited = inv != null && inv.team().equalsIgnoreCase(t.name());
        if (!invited && !t.open()) return "That team is invite-only.";
        if (t.members().size() >= maxMembers()) return "That team is full.";
        invites.remove(p.getUniqueId());
        t.members().add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), t);
        updateNametag(t);
        markDirty();
        broadcast(t, "<green>" + p.getName() + " joined the team.");
        return null;
    }

    public String leave(Player p) {
        Team t = get(p.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.owner().equals(p.getUniqueId())) {
            if (t.members().size() == 1) {
                disband(t);
                return null;
            }
            return "You own this team. Transfer ownership first (/team transfer <player>) or disband it.";
        }
        removeMember(t, p.getUniqueId());
        broadcast(t, "<yellow>" + p.getName() + " left the team.");
        return null;
    }

    public String kick(Player by, UUID target) {
        Team t = get(by.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (!t.isMember(target)) return "That player isn't in your team.";
        if (target.equals(by.getUniqueId())) return "Use /team leave instead.";
        Team.Role byRole = t.role(by.getUniqueId());
        Team.Role targetRole = t.role(target);
        if (byRole == Team.Role.MEMBER) return "Only the owner or admins can kick.";
        if (targetRole == Team.Role.OWNER || (targetRole == Team.Role.ADMIN && byRole != Team.Role.OWNER))
            return "You can't kick someone of equal or higher rank.";
        removeMember(t, target);
        Player tp = Bukkit.getPlayer(target);
        if (tp != null) Text.send(tp, "<red>You were kicked from " + Text.esc(t.name()) + ".");
        broadcast(t, "<yellow>" + nameOf(target) + " was kicked.");
        return null;
    }

    public String promote(Player by, UUID target) {
        Team t = get(by.getUniqueId());
        if (t == null || !t.owner().equals(by.getUniqueId())) return "Only the team owner can promote.";
        if (!t.isMember(target) || target.equals(t.owner())) return "Invalid player.";
        if (!t.admins().add(target)) return nameOf(target) + " is already an admin.";
        markDirty();
        broadcast(t, "<aqua>" + nameOf(target) + " is now a team admin.");
        return null;
    }

    public String demote(Player by, UUID target) {
        Team t = get(by.getUniqueId());
        if (t == null || !t.owner().equals(by.getUniqueId())) return "Only the team owner can demote.";
        if (!t.admins().remove(target)) return nameOf(target) + " is not an admin.";
        markDirty();
        broadcast(t, "<gray>" + nameOf(target) + " is no longer an admin.");
        return null;
    }

    public String transfer(Player by, UUID target) {
        Team t = get(by.getUniqueId());
        if (t == null || !t.owner().equals(by.getUniqueId())) return "Only the team owner can transfer ownership.";
        if (!t.isMember(target) || target.equals(by.getUniqueId())) return "Invalid player.";
        t.setOwner(target);
        markDirty();
        broadcast(t, "<gold>" + nameOf(target) + " is the new team owner.");
        return null;
    }

    public String rename(Player by, String newName) {
        Team t = get(by.getUniqueId());
        if (t == null || !t.owner().equals(by.getUniqueId())) return "Only the team owner can rename the team.";
        String err = validateName(newName);
        if (err != null && !newName.equalsIgnoreCase(t.name())) return err;
        removeNametag(t);
        teams.remove(t.name().toLowerCase(Locale.ROOT));
        t.name(newName);
        teams.put(newName.toLowerCase(Locale.ROOT), t);
        updateNametag(t);
        markDirty();
        return null;
    }

    private void removeMember(Team t, UUID id) {
        t.members().remove(id);
        t.admins().remove(id);
        byPlayer.remove(id);
        teamChat.remove(id);
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        org.bukkit.scoreboard.Team sb = mainBoard().getTeam(sbName(t));
        if (sb != null && op.getName() != null) sb.removeEntry(op.getName());
        markDirty();
    }

    public int maxMembers() { return plugin.getConfig().getInt("teams.max-members", 8); }

    // ------------------------------------------------------------ chat

    public boolean toggleTeamChat(UUID id) {
        if (!teamChat.add(id)) { teamChat.remove(id); return false; }
        return true;
    }

    public boolean inTeamChat(UUID id) { return teamChat.contains(id); }

    public void teamChat(Player from, String message) {
        Team t = get(from.getUniqueId());
        if (t == null) { Text.send(from, "<red>You are not in a team."); return; }
        String fmt = plugin.getConfig().getString("teams.chat.format",
                "<dark_gray>[<team_color><team></team_color>]</dark_gray> <gray><player></gray><dark_gray>:</dark_gray> <white><message>");
        String colorHex = t.color().asHexString();
        fmt = fmt.replace("<team_color>", "<" + colorHex + ">").replace("</team_color>", "</" + colorHex + ">")
                .replace("<team>", Text.esc(t.name()))
                .replace("<player>", Text.esc(from.getName()))
                .replace("<message>", Text.esc(message));
        Component c = Text.MM.deserialize(fmt);
        for (UUID m : t.members()) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) p.sendMessage(c);
        }
        plugin.getComponentLogger().info(Component.text("[TeamChat] ").append(c));
    }

    public void broadcast(Team t, String miniMessage) {
        for (UUID m : t.members()) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) Text.send(p, miniMessage);
        }
    }

    // ------------------------------------------------------------ chest

    public Inventory chest(Team t) {
        if (t.chest == null) {
            ChestHolder holder = new ChestHolder(t.name());
            t.chest = Bukkit.createInventory(holder, 54,
                    Component.text(t.name() + " — Team Chest", t.color()));
            holder.inv = t.chest;
        }
        return t.chest;
    }

    // ------------------------------------------------------------ home

    public String setHome(Player p) {
        Team t = get(p.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (!t.canManage(p.getUniqueId())) return "Only the owner or admins can set the team home.";
        t.home(p.getLocation());
        markDirty();
        broadcast(t, "<green>Team home set by " + p.getName() + ".");
        return null;
    }

    public void teleportHome(Player p) {
        Team t = get(p.getUniqueId());
        if (t == null) { Text.send(p, "<red>You are not in a team."); return; }
        Location home = t.home();
        if (home == null || home.getWorld() == null) { Text.send(p, "<red>Your team has no home set."); return; }
        long now = System.currentTimeMillis();
        Long cd = homeCooldowns.get(p.getUniqueId());
        if (cd != null && cd > now && !p.hasPermission("elementalblades.admin")) {
            Text.send(p, "<red>Team home is on cooldown for " + Text.seconds(cd - now) + ".");
            return;
        }
        cancelWarmup(p.getUniqueId(), false);
        int warmup = plugin.getConfig().getInt("teams.home.warmup-seconds", 3);
        Runnable go = () -> {
            homeWarmups.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            p.teleportAsync(home).thenAccept(ok -> {
                if (ok) {
                    Text.send(p, "<green>Welcome to your team home.");
                    homeCooldowns.put(p.getUniqueId(),
                            System.currentTimeMillis() + plugin.getConfig().getInt("teams.home.cooldown-seconds", 30) * 1000L);
                }
            });
        };
        if (warmup <= 0) { go.run(); return; }
        Text.send(p, "<gray>Teleporting to team home in <white>" + warmup + "s</white>"
                + (plugin.getConfig().getBoolean("teams.home.cancel-on-move", true) ? " — don't move." : "."));
        homeWarmups.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(plugin, go, warmup * 20L));
    }

    public boolean hasWarmup(UUID id) { return homeWarmups.containsKey(id); }

    public void cancelWarmup(UUID id, boolean notify) {
        BukkitTask task = homeWarmups.remove(id);
        if (task != null) {
            task.cancel();
            Player p = Bukkit.getPlayer(id);
            if (notify && p != null) Text.send(p, "<red>Teleport cancelled.");
        }
    }

    // ------------------------------------------------------------ nametags

    private Scoreboard mainBoard() { return Bukkit.getScoreboardManager().getMainScoreboard(); }
    private String sbName(Team t) { return "eb_" + t.name().toLowerCase(Locale.ROOT); }

    public void updateNametag(Team t) {
        if (!plugin.getConfig().getBoolean("teams.nametag-prefix", true)) return;
        Scoreboard board = mainBoard();
        org.bukkit.scoreboard.Team sb = board.getTeam(sbName(t));
        if (sb == null) sb = board.registerNewTeam(sbName(t));
        sb.prefix(Component.text("[" + t.name() + "] ", t.color()));
        sb.color(t.color());
        sb.setAllowFriendlyFire(true);   // our listener decides friendly fire
        sb.setCanSeeFriendlyInvisibles(true);
        for (UUID m : t.members()) {
            String n = Bukkit.getOfflinePlayer(m).getName();
            if (n != null && !sb.hasEntry(n)) sb.addEntry(n);
        }
    }

    private void removeNametag(Team t) {
        org.bukkit.scoreboard.Team sb = mainBoard().getTeam(sbName(t));
        if (sb != null) sb.unregister();
    }

    // ------------------------------------------------------------ persistence

    public void markDirty() { dirty = true; }

    public void saveIfDirty() { if (dirty) save(); }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Team t : teams.values()) {
            String base = "teams." + t.name() + ".";
            y.set(base + "owner", t.owner().toString());
            y.set(base + "members", t.members().stream().map(UUID::toString).toList());
            y.set(base + "admins", t.admins().stream().map(UUID::toString).toList());
            y.set(base + "color", t.color().toString());
            y.set(base + "friendly-fire", t.friendlyFire());
            y.set(base + "ability-friendly-fire", t.abilityFriendlyFire());
            y.set(base + "open", t.open());
            Location h = t.home();
            if (h != null && h.getWorld() != null) {
                y.set(base + "home.world", h.getWorld().getName());
                y.set(base + "home.x", h.getX());
                y.set(base + "home.y", h.getY());
                y.set(base + "home.z", h.getZ());
                y.set(base + "home.yaw", (double) h.getYaw());
                y.set(base + "home.pitch", (double) h.getPitch());
            }
            if (t.chest != null) {
                List<String> items = new ArrayList<>();
                for (ItemStack it : t.chest.getContents()) {
                    items.add(it == null || it.getType().isAir() ? "" : Base64.getEncoder().encodeToString(it.serializeAsBytes()));
                }
                y.set(base + "chest", items);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml: " + e.getMessage());
        }
    }

    public void load() {
        teams.clear();
        byPlayer.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("teams");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(name);
            if (s == null) continue;
            try {
                Team t = new Team(name, UUID.fromString(s.getString("owner")));
                for (String m : s.getStringList("members")) t.members().add(UUID.fromString(m));
                for (String a : s.getStringList("admins")) t.admins().add(UUID.fromString(a));
                NamedTextColor c = NamedTextColor.NAMES.value(s.getString("color", "aqua"));
                t.color(c == null ? NamedTextColor.AQUA : c);
                t.friendlyFire(s.getBoolean("friendly-fire"));
                t.abilityFriendlyFire(s.getBoolean("ability-friendly-fire"));
                t.open(s.getBoolean("open"));
                if (s.isConfigurationSection("home")) {
                    World w = Bukkit.getWorld(s.getString("home.world", "world"));
                    if (w != null) t.home(new Location(w, s.getDouble("home.x"), s.getDouble("home.y"), s.getDouble("home.z"),
                            (float) s.getDouble("home.yaw"), (float) s.getDouble("home.pitch")));
                }
                List<String> chest = s.getStringList("chest");
                if (!chest.isEmpty()) {
                    Inventory inv = chest(t);
                    for (int i = 0; i < Math.min(54, chest.size()); i++) {
                        String b64 = chest.get(i);
                        if (b64 == null || b64.isEmpty()) continue;
                        try {
                            inv.setItem(i, ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)));
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Bad item in team chest of " + name + " slot " + i + ": " + ex.getMessage());
                        }
                    }
                }
                teams.put(name.toLowerCase(Locale.ROOT), t);
                for (UUID m : t.members()) byPlayer.put(m, t);
                updateNametag(t);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping broken team '" + name + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + teams.size() + " team(s).");
    }

    public String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }
}
