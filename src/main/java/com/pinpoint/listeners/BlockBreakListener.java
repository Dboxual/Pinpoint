package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.Optional;

public class BlockBreakListener implements Listener {

    private final PinpointPlugin plugin;

    public BlockBreakListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location brokenLoc = event.getBlock().getLocation();

        // If this player has an unfinished Pinpoint setup at this block, cancel it and refund
        if (plugin.getWaypointManager().hasPendingNaming(player.getUniqueId())) {
            Location pendingLoc = plugin.getWaypointManager().getPendingNaming(player.getUniqueId());
            if (pendingLoc != null && sameBlock(brokenLoc, pendingLoc)) {
                int taskId = plugin.getWaypointManager().getPendingNamingTaskId(player.getUniqueId());
                if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
                plugin.getWaypointManager().clearPendingNaming(player.getUniqueId());
                plugin.getWaypointManager().clearPendingNamingTaskId(player.getUniqueId());

                // Suppress the vanilla Lodestone drop and refund the tagged Pinpoint item
                event.setDropItems(false);
                ItemStack refund = plugin.getItemManager().createWaypointBlockItem();
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(refund);
                if (!leftover.isEmpty()) {
                    brokenLoc.getWorld().dropItemNaturally(brokenLoc, leftover.get(0));
                }
                player.sendMessage(plugin.msg("prefix") + "§cPinpoint setup cancelled. Block returned.");
                return;
            }
        }

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypointAt(brokenLoc);
        if (wpOpt.isEmpty()) return;

        Waypoint wp = wpOpt.get();

        if (!wp.isOwner(player.getUniqueId()) && !player.hasPermission("waypoint.admin")) {
            player.sendMessage(plugin.msg("prefix") + "§cOnly the waypoint owner can break this.");
            event.setCancelled(true);
            return;
        }

        // Owner or admin — remove waypoint from storage; block drops normally
        plugin.getHologramManager().removeHologram(wp.getId());
        plugin.getWaypointManager().deleteWaypoint(wp.getId());
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("waypoint-deleted"), wp.getName()));
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
