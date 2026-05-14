package com.waypointsystem.listeners;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

public class BlockBreakListener implements Listener {

    private final WaypointPlugin plugin;

    public BlockBreakListener(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypointAt(event.getBlock().getLocation());
        if (wpOpt.isEmpty()) return;

        Waypoint wp = wpOpt.get();

        if (!wp.isOwner(player.getUniqueId()) && !player.hasPermission("waypoint.admin")) {
            player.sendMessage(plugin.msg("prefix") + "§cOnly the waypoint owner can break this.");
            event.setCancelled(true);
            return;
        }

        // Owner or admin — remove waypoint from storage; block drops normally
        plugin.getWaypointManager().deleteWaypoint(wp.getId());
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("waypoint-deleted"), wp.getName()));
    }
}
