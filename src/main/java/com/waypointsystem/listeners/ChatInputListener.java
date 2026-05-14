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

import java.util.UUID;

public class ChatInputListener implements Listener {

    private final WaypointPlugin plugin;

    public ChatInputListener(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Waypoint naming
        if (plugin.getWaypointManager().hasPendingNaming(uuid)) {
            event.setCancelled(true);
            String name = event.getMessage().trim();
            if (name.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("prefix") +
                                plugin.getConfig().getString("messages.name-empty", "&cName cannot be empty.")
                                        .replace("&", "§")));
                return;
            }
            if (name.length() > 32) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.msg("prefix") +
                                plugin.getConfig().getString("messages.name-too-long", "&cName must be 32 chars or less.")
                                        .replace("&", "§")));
                return;
            }
            Location loc = plugin.getWaypointManager().getPendingNaming(uuid);
            plugin.getWaypointManager().clearPendingNaming(uuid);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Waypoint wp = plugin.getWaypointManager().createWaypoint(name, player, loc);

                // Replace unnamed waypoint item in hand with named one
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (plugin.getItemManager().isWaypointItem(hand) && !plugin.getItemManager().isNamedWaypointItem(hand)) {
                    player.getInventory().setItemInMainHand(
                            plugin.getItemManager().createNamedWaypointItem(wp.getId(), wp.getName()));
                }

                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.getConfig().getString("messages.waypoint-named", "&aWaypoint '%s' created!"),
                                wp.getName()).replace("&", "§"));
            });
            return;
        }

        // Fee input
        if (plugin.getWaypointManager().hasPendingFeeInput(uuid)) {
            event.setCancelled(true);
            String input = event.getMessage().trim();
            UUID waypointId = plugin.getWaypointManager().getPendingFeeInput(uuid);
            plugin.getWaypointManager().clearPendingFeeInput(uuid);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                final double fee;
                double parsed;
                try {
                    parsed = Double.parseDouble(input);
                    if (parsed < 0) parsed = 0;
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.msg("prefix") + "§cInvalid number.");
                    return;
                }
                fee = parsed;
                plugin.getWaypointManager().getWaypoint(waypointId).ifPresent(wp -> {
                    if (!wp.isOwner(uuid)) {
                        player.sendMessage(plugin.msg("prefix") + "§cNot your waypoint.");
                        return;
                    }
                    wp.setFee(fee);
                    plugin.getWaypointManager().saveWaypoint(wp);
                    player.sendMessage(plugin.msg("prefix") + "§aFee set to " +
                            plugin.getEconomyManager().format(fee) + " for waypoint §b" + wp.getName() + "§a.");
                });
            });
        }
    }
}
