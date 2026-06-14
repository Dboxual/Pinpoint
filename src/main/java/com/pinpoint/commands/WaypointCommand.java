package com.pinpoint.commands;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            case "list"      -> handleList(sender);
            case "reload"    -> handleReload(sender);
            case "give"      -> handleGive(sender, args);
            case "landmark"  -> handleLandmark(sender, args);
            case "tpaccept"  -> handleTpAccept(sender);
            case "tpdeny"    -> handleTpDeny(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // /waypoint tpaccept — accept an incoming player teleport request (fallback; primary flow is GUI)
    private void handleTpAccept(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        UUID requesterId = plugin.getWaypointManager().getIncomingRequest(target.getUniqueId());
        if (requesterId == null) {
            target.sendMessage(plugin.msg("prefix") + "§cYou have no pending teleport requests.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            int taskId = plugin.getWaypointManager().getPendingPlayerRequestTaskId(requesterId);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);
            target.sendMessage(plugin.msg("prefix") + "§cThat player is no longer online.");
            return;
        }
        plugin.getTeleportHelper().acceptPlayerTeleportRequest(target, requester);
    }

    // /waypoint tpdeny — deny an incoming player teleport request (fallback; primary flow is GUI)
    private void handleTpDeny(CommandSender sender) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }
        UUID requesterId = plugin.getWaypointManager().getIncomingRequest(target.getUniqueId());
        if (requesterId == null) {
            target.sendMessage(plugin.msg("prefix") + "§cYou have no pending teleport requests.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            int taskId = plugin.getWaypointManager().getPendingPlayerRequestTaskId(requesterId);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);
            target.sendMessage(plugin.msg("prefix") + "§cThat player is no longer online.");
            return;
        }
        plugin.getTeleportHelper().denyPlayerTeleportRequest(target, requester);
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
    // /waypoint give <player> compass [amount]   (alias: pearl)
    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("waypoint.give")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> waypoint [amount]");
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /waypoint give <player> compass [amount]");
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
            case "compass" -> {
                int amount = parseAmount(args, 3, 1);
                plugin.getItemManager().giveWaypointCompasses(target, amount);
                sender.sendMessage(plugin.msg("prefix") + "§aGave §e" + amount
                        + "x §dPinpoint Compass§a to §b" + target.getName() + "§a.");
                if (!sender.equals(target)) {
                    target.sendMessage(plugin.msg("prefix") + "§aYou received §e" + amount
                            + "x §dPinpoint Compass§a.");
                }
            }
            default -> sender.sendMessage(plugin.msg("prefix")
                    + "§cUnknown type. Use §ewaypoint §cor §ecompass§c.");
        }
    }

    // /waypoint landmark <create|delete|list|reload> [name]
    private void handleLandmark(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pinpoint.admin.landmark")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        if (args.length < 2) {
            sendLandmarkUsage(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> handleLandmarkCreate(sender, args);
            case "delete" -> handleLandmarkDelete(sender, args);
            case "list"   -> handleLandmarkList(sender);
            case "reload" -> handleLandmarkReload(sender);
            default -> sendLandmarkUsage(sender);
        }
    }

    private void handleLandmarkCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pinpoint.admin.landmark.create")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("prefix") + "§cOnly players can create landmarks.");
            return;
        }
        if (!plugin.getConfig().getBoolean("landmarks.enabled", true)) {
            sender.sendMessage(plugin.msg("prefix") + "§cLandmarks are disabled in config.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /wp landmark create <name>");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (name.length() > 32) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-too-long"));
            return;
        }
        if (plugin.getWaypointManager().getLandmarks().stream()
                .anyMatch(lm -> lm.getName().equalsIgnoreCase(name))) {
            sender.sendMessage(plugin.msg("prefix") + "§cA landmark named '§e" + name + "§c' already exists.");
            return;
        }

        plugin.getWaypointManager().setPendingLandmarkCreation(player.getUniqueId(), name);

        // Ensure the admin has a Landmark Block to place
        boolean hasLandmarkBlock = false;
        for (ItemStack i : player.getInventory().getContents()) {
            if (plugin.getItemManager().isLandmarkBlock(i)) { hasLandmarkBlock = true; break; }
        }
        if (!hasLandmarkBlock) {
            player.getInventory().addItem(plugin.getItemManager().createLandmarkBlock());
            player.sendMessage(plugin.msg("prefix") + "§7(A Landmark Block has been added to your inventory.)");
        }
        player.sendMessage(plugin.msg("prefix")
                + "§bPlace the Landmark Block to set the location for landmark '§a" + name + "§b'.");
    }

    private void handleLandmarkDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pinpoint.admin.landmark.delete")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("prefix") + "§cUsage: /wp landmark delete <name>");
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Waypoint landmark = plugin.getWaypointManager().getLandmarks().stream()
                .filter(lm -> lm.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (landmark == null) {
            sender.sendMessage(plugin.msg("prefix") + "§cLandmark '§e" + name + "§c' not found.");
            return;
        }
        plugin.getHologramManager().removeHologram(landmark.getId());
        Location blockLoc = landmark.getLocation();
        plugin.getWaypointManager().deleteWaypoint(landmark.getId());
        if (blockLoc != null) {
            String matName = plugin.getConfig().getString("landmarks.block.material", "LODESTONE");
            Material lmMat = Material.matchMaterial(matName);
            if (lmMat == null) lmMat = Material.LODESTONE;
            if (blockLoc.getBlock().getType() == lmMat) {
                blockLoc.getBlock().setType(Material.AIR);
            }
        }
        sender.sendMessage(plugin.msg("prefix") + "§aLandmark '§b" + landmark.getName() + "§a' deleted.");
    }

    private void handleLandmarkList(CommandSender sender) {
        if (!sender.hasPermission("pinpoint.admin.landmark.list")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        List<Waypoint> landmarks = plugin.getWaypointManager().getLandmarks();
        if (landmarks.isEmpty()) {
            sender.sendMessage(plugin.msg("prefix") + "§7No landmarks have been created.");
            return;
        }
        sender.sendMessage(plugin.msg("prefix") + "§eLandmarks §7(" + landmarks.size() + ")§e:");
        for (Waypoint lm : landmarks) {
            String loc = lm.getWorldName() + " " + (int)lm.getX() + " " + (int)lm.getY() + " " + (int)lm.getZ();
            sender.sendMessage("  §7» §b" + lm.getName() + " §8(" + loc + ")");
        }
    }

    private void handleLandmarkReload(CommandSender sender) {
        if (!sender.hasPermission("pinpoint.admin.landmark.reload")) {
            sender.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }
        plugin.reloadConfig();
        for (Waypoint lm : plugin.getWaypointManager().getLandmarks()) {
            plugin.getHologramManager().updateHologram(lm);
        }
        sender.sendMessage(plugin.msg("prefix") + "§aLandmark config reloaded.");
    }

    private void sendLandmarkUsage(CommandSender sender) {
        sender.sendMessage(plugin.msg("prefix") + "§eLandmark commands:");
        sender.sendMessage("  §b/wp landmark create §e<name>  §7- Create a landmark");
        sender.sendMessage("  §b/wp landmark delete §e<name>  §7- Delete a landmark");
        sender.sendMessage("  §b/wp landmark list             §7- List all landmarks");
        sender.sendMessage("  §b/wp landmark reload           §7- Reload landmark config");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("menu", "list", "reload", "give", "landmark"));
            return filter(subs, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("waypoint", "compass"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("landmark")) {
            return filter(List.of("create", "delete", "list", "reload"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("landmark")
                && args[1].equalsIgnoreCase("delete")) {
            return plugin.getWaypointManager().getLandmarks().stream()
                    .map(Waypoint::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    // --- Helpers ---

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.msg("prefix") + "§ePinpoint commands:");
        sender.sendMessage("  §b/wp§e or §b/wp menu §7- Open Pinpoint hub");
        sender.sendMessage("  §b/wp list            §7- List accessible Pinpoints");
        if (sender.hasPermission("waypoint.reload"))
            sender.sendMessage("  §b/wp reload          §7- Reload config and data");
        if (sender.hasPermission("waypoint.give"))
            sender.sendMessage("  §b/wp give §e<player> waypoint§7|§ecompass §7- Give items");
        if (sender.hasPermission("pinpoint.admin.landmark"))
            sender.sendMessage("  §b/wp landmark        §7- Landmark admin commands");
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
