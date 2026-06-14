package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class NewcomerListener implements Listener {

    private final PinpointPlugin plugin;

    public NewcomerListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("compass.enabled", true)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getCompassDataManager().hasReceived(uuid)) return;

        // Run 1 tick later so the player's inventory is fully initialised
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Duplicate protection: if they already own one, mark and skip
            if (hasCompass(player)) {
                plugin.getCompassDataManager().markReceived(uuid);
                return;
            }

            ItemStack compass = plugin.getItemManager().createWaypointCompass();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(compass);

            String msgKey;
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
                msgKey = plugin.getConfig().getString(
                        "compass.inventory-full",
                        "&eYour inventory was full. Your Pinpoint Compass was dropped nearby.");
            } else {
                msgKey = plugin.getConfig().getString(
                        "compass.message",
                        "&bYou received a Pinpoint Compass!");
            }

            if (msgKey != null && !msgKey.isEmpty()) {
                player.sendMessage(plugin.msg("prefix") + msgKey.replace("&", "§"));
            }

            plugin.getCompassDataManager().markReceived(uuid);
        });
    }

    private boolean hasCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isWaypointCompass(item)) return true;
        }
        return false;
    }
}
