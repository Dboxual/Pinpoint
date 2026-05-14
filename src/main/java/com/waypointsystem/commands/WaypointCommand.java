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
            case "give" -> {
                if (!sender.hasPermission("waypointsystem.give")) {
                    sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.msg("prefix") + "§cPlayer not found.");
                    return true;
                }
                target.getInventory().addItem(plugin.getItemManager().createUnnamedWaypointItem());
                sender.sendMessage(plugin.msg("prefix") + "§aGave a Waypoint item to §b" + target.getName() + "§a.");
                target.sendMessage(plugin.msg("prefix") + "§aYou received a Waypoint item!");
            }
            default -> sender.sendMessage(plugin.msg("prefix") +
                    "§eUsage: /waypoint [accept|deny|reload|give <player>]");
        }
        return true;
    }

    public void processAccept(Player player) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getWaypointManager().hasPendingInvite(uuid)) {
            player.sendMessage(plugin.msg("prefix") +
                    plugin.msgCfg("no-pending-invite"));
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
        Player inviter = Bukkit.getPlayer(invite.inviterUuid);

        // Teleport the invitee now; inviter gets notified and can also teleport
        plugin.getTeleportHelper().teleport(player, wp);

        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("invite-accepted"), player.getName()));
            // Also teleport the inviter to complete the group teleport
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
            return List.of("accept", "deny", "reload", "give").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
