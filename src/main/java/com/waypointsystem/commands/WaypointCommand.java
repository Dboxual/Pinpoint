package com.waypointsystem.commands;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import com.waypointsystem.data.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class WaypointCommand implements CommandExecutor, TabCompleter {

    private final WaypointPlugin plugin;

    public WaypointCommand(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use /waypoint.");
                return true;
            }
            plugin.getGuiManager().openHubGui(player, null);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "accept" -> {
                if (!(sender instanceof Player player)) return true;
                processAccept(player);
            }
            case "deny" -> {
                if (!(sender instanceof Player player)) return true;
                processDeny(player);
            }
            case "reload" -> {
                if (!sender.hasPermission("waypointsystem.admin")) {
                    sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.msg("prefix") + "§aReloaded.");
            }
            case "give" -> handleGive(sender, args);
            default -> sender.sendMessage(plugin.msg("prefix") +
                    "§eUsage: /waypoint [accept|deny|reload|give <player> waypoint [amount]|give <player> orb <name|id> [amount]]");
        }
        return true;
    }

    // /waypoint give <player> waypoint [amount]
    // /waypoint give <player> orb <waypointNameOrId> [amount]
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("waypointsystem.give")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> waypoint [amount]");
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> orb <waypointNameOrId> [amount]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("prefix") + "§cPlayer §e" + args[1] + "§c is not online.");
            return;
        }

        String type = args[2].toLowerCase();

        switch (type) {
            case "waypoint" -> {
                int amount = parseAmount(args, 3, 1);
                ItemStack item = plugin.getItemManager().createUnnamedWaypointItem();
                item.setAmount(Math.min(amount, 64));
                target.getInventory().addItem(item);
                sender.sendMessage(plugin.msg("prefix") + "§aGave §e" + amount
                        + "x §bWaypoint item§a to §b" + target.getName() + "§a.");
                if (!sender.equals(target)) {
                    target.sendMessage(plugin.msg("prefix") + "§aYou received §e" + amount
                            + "x §bWaypoint item§a.");
                }
            }
            case "orb" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> orb <waypointNameOrId> [amount]");
                    return;
                }
                String query = args[3];
                int amount = parseAmount(args, 4, 1);

                // If the query looks like a UUID, resolve directly by ID — no ambiguity possible.
                boolean looksLikeUuid = query.length() == 36 && query.contains("-");
                if (looksLikeUuid) {
                    Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypointByNameOrId(query);
                    if (wpOpt.isEmpty()) {
                        sender.sendMessage(plugin.msg("prefix") + "§cNo waypoint found with ID §e" + query + "§c.");
                        return;
                    }
                    Waypoint wp = wpOpt.get();
                    plugin.getItemManager().giveRecallOrbs(target, wp, amount);
                    sender.sendMessage(plugin.msg("prefix") + "§aGave §e" + amount
                            + "x §dRecall Orb§a (§b" + wp.getName()
                            + " #" + WaypointManager.shortId(wp.getId()) + "§a) to §b" + target.getName() + "§a.");
                    return;
                }

                // Name lookup — detect collisions before acting.
                List<Waypoint> matches = plugin.getWaypointManager().getWaypointsByName(query);
                if (matches.isEmpty()) {
                    sender.sendMessage(plugin.msg("prefix") + "§cNo waypoint found matching §e" + query + "§c.");
                    return;
                }
                if (matches.size() > 1) {
                    sender.sendMessage(plugin.msg("prefix") + "§eMultiple waypoints named §b" + query
                            + "§e exist. Use the full UUID instead:");
                    for (Waypoint m : matches) {
                        sender.sendMessage("  §7- §b" + m.getName()
                                + " §8(#" + WaypointManager.shortId(m.getId()) + ")§7"
                                + " owner: §f" + m.getOwnerName()
                                + " §8full-id: §7" + m.getId());
                    }
                    sender.sendMessage(plugin.msg("prefix") + "§eRe-run with: §b/waypoint give "
                            + target.getName() + " orb <full-uuid> [amount]");
                    return;
                }
                Waypoint wp = matches.get(0);
                plugin.getItemManager().giveRecallOrbs(target, wp, amount);
                sender.sendMessage(plugin.msg("prefix") + "§aGave §e" + amount
                        + "x §dRecall Orb§a (§b" + wp.getName() + "§a) to §b" + target.getName() + "§a.");
            }
            default -> {
                sender.sendMessage(plugin.msg("prefix") + "§cUnknown type §e" + type
                        + "§c. Use §ewaypoint§c or §eorb§c.");
            }
        }
    }

    private int parseAmount(String[] args, int index, int defaultVal) {
        if (args.length <= index) return defaultVal;
        try {
            int n = Integer.parseInt(args[index]);
            return Math.max(1, n);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public void processAccept(Player player) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getWaypointManager().hasPendingInvite(uuid)) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-pending-invite"));
            return;
        }

        WaypointManager.TeleportInvite invite = plugin.getWaypointManager().getInvite(uuid);
        invite.accepted = true;

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(invite.waypointId);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat waypoint no longer exists.");
            plugin.getWaypointManager().removeInvite(uuid);
            return;
        }

        Waypoint wp = wpOpt.get();
        plugin.getTeleportHelper().teleport(player, wp);

        Player inviter = Bukkit.getPlayer(invite.inviterUuid);
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("invite-accepted"), player.getName()));
            plugin.getTeleportHelper().teleport(inviter, wp);
        }

        plugin.getWaypointManager().removeInvite(uuid);
    }

    public void processDeny(Player player) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getWaypointManager().hasPendingInvite(uuid)) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-pending-invite"));
            return;
        }

        WaypointManager.TeleportInvite invite = plugin.getWaypointManager().getInvite(uuid);
        plugin.getWaypointManager().removeInvite(uuid);

        player.sendMessage(plugin.msg("prefix") + "§cYou denied the teleport invite.");

        Player inviter = Bukkit.getPlayer(invite.inviterUuid);
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("invite-denied"), player.getName()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("accept", "deny", "reload", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("waypoint", "orb"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("orb")) {
            List<String> names = new ArrayList<>();
            plugin.getWaypointManager().getAllWaypoints()
                    .forEach(wp -> names.add(wp.getName()));
            return filter(names, args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}
