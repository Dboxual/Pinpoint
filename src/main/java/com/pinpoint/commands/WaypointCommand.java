package com.pinpoint.commands;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
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

    private final PinpointPlugin plugin;

    public WaypointCommand(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            if (!player.hasPermission("waypoint.use")) {
                player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
                return true;
            }
            plugin.getGuiManager().openHubGui(player, null, false);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list"   -> handleList(sender);
            case "accept" -> { if (sender instanceof Player p) processAccept(p); }
            case "deny"   -> { if (sender instanceof Player p) processDeny(p); }
            case "reload" -> handleReload(sender);
            case "give"   -> handleGive(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    // /waypoint list
    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("waypoint.list")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }

        UUID playerUuid = (sender instanceof Player p) ? p.getUniqueId() : null;

        List<Waypoint> waypoints = playerUuid != null
                ? plugin.getWaypointManager().getAccessibleWaypoints(playerUuid)
                : new ArrayList<>(plugin.getWaypointManager().getAllWaypoints());

        waypoints.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        if (waypoints.isEmpty()) {
            sender.sendMessage(plugin.msg("prefix") + "§7You have no accessible Pinpoints.");
            return;
        }

        sender.sendMessage(plugin.msg("prefix") + "§eAccessible Pinpoints §7(" + waypoints.size() + ")§e:");
        for (Waypoint wp : waypoints) {
            String tag;
            if (playerUuid != null && wp.isOwner(playerUuid)) {
                tag = "§a[yours]";
            } else if (wp.isPublic()) {
                tag = "§b[public]";
            } else {
                tag = "§6[invited]";
            }

            String feeStr = wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                    ? "§6" + plugin.getEconomyManager().format(wp.getFee())
                    : "§afree";

            String visStr = wp.isPublic() ? "§apublic" : "§cprivate";

            sender.sendMessage("  §7» §f" + wp.getName()
                    + " §8(#" + WaypointManager.shortId(wp.getId()) + ")"
                    + " " + tag
                    + " §7| owner: §f" + wp.getOwnerName()
                    + " §7| " + visStr
                    + " §7| fee: " + feeStr);
        }
    }

    // /waypoint reload
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("waypoint.reload")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(plugin.msg("prefix") + "§aReloaded.");
    }

    // /waypoint give <player> waypoint [amount]
    // /waypoint give <player> pearl [amount]
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("waypoint.give")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> waypoint [amount]");
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> pearl [amount]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.msg("prefix") + "§cPlayer §e" + args[1] + "§c is not online.");
            return;
        }

        switch (args[2].toLowerCase()) {
            case "waypoint" -> {
                int amount = parseAmount(args, 3, 1);
                ItemStack item = plugin.getItemManager().createWaypointBlockItem();
                item.setAmount(Math.min(amount, 64));
                target.getInventory().addItem(item);
                sender.sendMessage(plugin.msg("prefix") + "§aGave §e" + amount
                        + "x §bPinpoint Block§a to §b" + target.getName() + "§a.");
                if (!sender.equals(target)) {
                    target.sendMessage(plugin.msg("prefix") + "§aYou received §e" + amount
                            + "x §bPinpoint Block§a.");
                }
            }
            case "pearl" -> {
                int amount = parseAmount(args, 3, 1);
                plugin.getItemManager().giveWaypointPearls(target, amount);
                sender.sendMessage(plugin.msg("prefix") + "§aGave §e" + amount
                        + "x §dPinpoint Pearl§a to §b" + target.getName() + "§a.");
                if (!sender.equals(target)) {
                    target.sendMessage(plugin.msg("prefix") + "§aYou received §e" + amount
                            + "x §dPinpoint Pearl§a.");
                }
            }
            default -> sender.sendMessage(plugin.msg("prefix")
                    + "§cUnknown type §e" + args[2] + "§c. Use §ewaypoint§c or §epearl§c.");
        }
    }

    public void processAccept(Player player) {
        UUID uuid = player.getUniqueId();
        // Close any open GUI so countdown can begin unobstructed
        plugin.getGuiManager().closeGui(player);
        if (!plugin.getWaypointManager().hasPendingInvite(uuid)) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-pending-invite"));
            return;
        }

        WaypointManager.TeleportInvite invite = plugin.getWaypointManager().getInvite(uuid);
        invite.accepted = true;

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(invite.waypointId);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat Pinpoint no longer exists.");
            plugin.getWaypointManager().removeInvite(uuid);
            return;
        }

        Waypoint wp = wpOpt.get();
        Player inviter = Bukkit.getPlayer(invite.inviterUuid);
        String inviterName = inviter != null ? inviter.getName() : "your group";

        // Both players start their 5-second group countdown in the same tick → synchronized arrival
        plugin.getTeleportHelper().teleportGroupMember(player, wp, inviterName, false);

        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("invite-accepted"), player.getName()));
            plugin.getTeleportHelper().teleportGroupMember(inviter, wp, player.getName(), false);
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
            List<String> subs = new ArrayList<>(List.of("menu", "list", "accept", "deny", "reload", "give"));
            return filter(subs, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("waypoint", "pearl"), args[2]);
        }
        return List.of();
    }

    // --- Helpers ---

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.msg("prefix") + "§ePinpoint commands:");
        sender.sendMessage("  §b/wp§e or §b/wp menu §7- Open Pinpoint hub");
        sender.sendMessage("  §b/wp list            §7- List accessible Pinpoints");
        sender.sendMessage("  §b/wp accept§e/§bdeny  §7- Respond to a teleport invite");
        if (sender.hasPermission("waypoint.reload"))
            sender.sendMessage("  §b/wp reload          §7- Reload config and data");
        if (sender.hasPermission("waypoint.give"))
            sender.sendMessage("  §b/wp give §e<player> waypoint§7|§epearl §7- Give items");
    }

    private int parseAmount(String[] args, int index, int defaultVal) {
        if (args.length <= index) return defaultVal;
        try { return Math.max(1, Integer.parseInt(args[index])); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}
