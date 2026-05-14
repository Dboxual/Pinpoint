package com.waypointsystem.listeners;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.UUID;

public class ChatInputListener implements Listener {

    private static final int TIMEOUT_TICKS = 1200; // 60 seconds
    private static final String CANCEL_KEYWORD = "cancel";

    private final WaypointPlugin plugin;

    public ChatInputListener(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // --- Waypoint renaming (checked before naming) ---
        if (plugin.getWaypointManager().hasPendingRenaming(uuid)) {
            event.setCancelled(true);
            String input = event.getMessage().trim();

            if (input.equalsIgnoreCase(CANCEL_KEYWORD)) {
                cancelRenaming(player, uuid);
                return;
            }

            if (input.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-empty")));
                return;
            }
            if (input.length() > 32) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-too-long")));
                return;
            }

            int taskId = plugin.getWaypointManager().getPendingRenamingTaskId(uuid);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingRenamingTaskId(uuid);

            UUID waypointId = plugin.getWaypointManager().getPendingRenaming(uuid);
            plugin.getWaypointManager().clearPendingRenaming(uuid);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getWaypointManager().getWaypoint(waypointId).ifPresentOrElse(wp -> {
                    if (!wp.isOwner(uuid)) {
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner"));
                        return;
                    }
                    wp.setName(input);
                    plugin.getWaypointManager().saveWaypoint(wp);

                    // Update held item name tag if it is linked to this waypoint
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (plugin.getItemManager().isNamedWaypointItem(hand)) {
                        String heldId = plugin.getItemManager().getWaypointId(hand);
                        if (waypointId.toString().equals(heldId)) {
                            player.getInventory().setItemInMainHand(
                                    plugin.getItemManager().createNamedWaypointItem(waypointId, input));
                        }
                    }

                    player.sendMessage(plugin.msg("prefix") +
                            String.format(plugin.msgCfg("waypoint-renamed"), input));
                }, () -> player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("waypoint-not-found")));
            });
            return;
        }

        // --- Waypoint naming ---
        if (plugin.getWaypointManager().hasPendingNaming(uuid)) {
            event.setCancelled(true);
            String input = event.getMessage().trim();

            if (input.equalsIgnoreCase(CANCEL_KEYWORD)) {
                cancelNaming(player, uuid);
                return;
            }

            if (input.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-empty")));
                return;
            }
            if (input.length() > 32) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-too-long")));
                return;
            }

            // Cancel the timeout task
            int taskId = plugin.getWaypointManager().getPendingNamingTaskId(uuid);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingNamingTaskId(uuid);

            Location loc = plugin.getWaypointManager().getPendingNaming(uuid);
            plugin.getWaypointManager().clearPendingNaming(uuid);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.getWaypointManager().isAtWaypointLimit(uuid)) {
                    int max = plugin.getWaypointManager().getMaxWaypoints();
                    player.sendMessage(plugin.msg("prefix") +
                            String.format(plugin.msgCfg("waypoint-limit-reached"), max));
                    return;
                }

                Waypoint wp = plugin.getWaypointManager().createWaypoint(input, player, loc);

                ItemStack hand = player.getInventory().getItemInMainHand();
                if (plugin.getItemManager().isWaypointItem(hand) && !plugin.getItemManager().isNamedWaypointItem(hand)) {
                    player.getInventory().setItemInMainHand(
                            plugin.getItemManager().createNamedWaypointItem(wp.getId(), wp.getName()));
                }

                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("waypoint-named"), wp.getName()));
            });
            return;
        }

        // --- Fee input ---
        if (plugin.getWaypointManager().hasPendingFeeInput(uuid)) {
            event.setCancelled(true);
            String input = event.getMessage().trim();

            if (input.equalsIgnoreCase(CANCEL_KEYWORD)) {
                cancelFeeInput(player, uuid);
                return;
            }

            int taskId = plugin.getWaypointManager().getPendingFeeTaskId(uuid);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingFeeTaskId(uuid);

            UUID waypointId = plugin.getWaypointManager().getPendingFeeInput(uuid);
            plugin.getWaypointManager().clearPendingFeeInput(uuid);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                final double fee;
                try {
                    double parsed = Double.parseDouble(input);
                    fee = Math.max(0, parsed);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.msg("prefix") + "§cInvalid number. Fee not changed.");
                    return;
                }
                plugin.getWaypointManager().getWaypoint(waypointId).ifPresentOrElse(wp -> {
                    if (!wp.isOwner(uuid)) {
                        player.sendMessage(plugin.msg("prefix") + "§cNot your waypoint.");
                        return;
                    }
                    wp.setFee(fee);
                    plugin.getWaypointManager().saveWaypoint(wp);
                    player.sendMessage(plugin.msg("prefix") + "§aFee for §b" + wp.getName() + "§a set to §e"
                            + plugin.getEconomyManager().format(fee) + "§a.");
                }, () -> player.sendMessage(plugin.msg("prefix") + "§cWaypoint no longer exists."));
            });
        }
    }

    // --- Called by GuiManager when starting a rename session ---

    public void schedulePendingRenameTimeout(Player player, UUID waypointId) {
        UUID uuid = player.getUniqueId();
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWaypointManager().hasPendingRenaming(uuid)) {
                plugin.getWaypointManager().clearPendingRenaming(uuid);
                plugin.getWaypointManager().clearPendingRenamingTaskId(uuid);
                if (player.isOnline()) {
                    player.sendMessage(plugin.msg("prefix") + "§cWaypoint rename timed out. No changes made.");
                }
            }
        }, TIMEOUT_TICKS).getTaskId();
        plugin.getWaypointManager().setPendingRenamingTaskId(uuid, taskId);
    }

    // --- Called by WaypointInteractListener when starting a naming session ---

    public void schedulePendingNamingTimeout(Player player) {
        UUID uuid = player.getUniqueId();
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWaypointManager().hasPendingNaming(uuid)) {
                plugin.getWaypointManager().clearPendingNaming(uuid);
                plugin.getWaypointManager().clearPendingNamingTaskId(uuid);
                if (player.isOnline()) {
                    player.sendMessage(plugin.msg("prefix") + "§cWaypoint naming timed out. Type §e/waypoint§c to try again.");
                }
            }
        }, TIMEOUT_TICKS).getTaskId();
        plugin.getWaypointManager().setPendingNamingTaskId(uuid, taskId);
    }

    public void schedulePendingFeeTimeout(Player player, UUID waypointId) {
        UUID uuid = player.getUniqueId();
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWaypointManager().hasPendingFeeInput(uuid)) {
                plugin.getWaypointManager().clearPendingFeeInput(uuid);
                plugin.getWaypointManager().clearPendingFeeTaskId(uuid);
                if (player.isOnline()) {
                    player.sendMessage(plugin.msg("prefix") + "§cFee input timed out. Fee not changed.");
                }
            }
        }, TIMEOUT_TICKS).getTaskId();
        plugin.getWaypointManager().setPendingFeeTaskId(uuid, taskId);
    }

    private void cancelRenaming(Player player, UUID uuid) {
        int taskId = plugin.getWaypointManager().getPendingRenamingTaskId(uuid);
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        plugin.getWaypointManager().clearPendingRenaming(uuid);
        plugin.getWaypointManager().clearPendingRenamingTaskId(uuid);
        plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(plugin.msg("prefix") + "§cWaypoint rename cancelled."));
    }

    private void cancelNaming(Player player, UUID uuid) {
        int taskId = plugin.getWaypointManager().getPendingNamingTaskId(uuid);
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        plugin.getWaypointManager().clearPendingNaming(uuid);
        plugin.getWaypointManager().clearPendingNamingTaskId(uuid);
        plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(plugin.msg("prefix") + "§cWaypoint naming cancelled."));
    }

    private void cancelFeeInput(Player player, UUID uuid) {
        int taskId = plugin.getWaypointManager().getPendingFeeTaskId(uuid);
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        plugin.getWaypointManager().clearPendingFeeInput(uuid);
        plugin.getWaypointManager().clearPendingFeeTaskId(uuid);
        plugin.getServer().getScheduler().runTask(plugin, () ->
                player.sendMessage(plugin.msg("prefix") + "§cFee input cancelled."));
    }
}
