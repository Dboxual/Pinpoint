package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class BlockPlaceListener implements Listener {

    private final PinpointPlugin plugin;

    public BlockPlaceListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (!plugin.getItemManager().isWaypointBlockItem(item)) return;

        if (!player.hasPermission("waypoint.use")) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            event.setCancelled(true);
            return;
        }

        if (plugin.getWaypointManager().isAtWaypointLimit(player.getUniqueId())) {
            int max = plugin.getWaypointManager().getMaxWaypoints();
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("waypoint-limit-reached"), max));
            event.setCancelled(true);
            return;
        }

        if (plugin.getWaypointManager().hasPendingNaming(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cFinish naming your current waypoint first.");
            event.setCancelled(true);
            return;
        }

        // Block placed — start naming flow using the block's location
        plugin.getWaypointManager().setPendingNaming(player.getUniqueId(), event.getBlock().getLocation());
        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-prompt"));
        plugin.getChatInputListener().schedulePendingNamingTimeout(player);
    }
}
