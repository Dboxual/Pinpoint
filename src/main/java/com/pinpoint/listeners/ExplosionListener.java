package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExplosionListener implements Listener {

    private final PinpointPlugin plugin;

    public ExplosionListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeWaypointsInBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeWaypointsInBlocks(event.blockList());
    }

    private void removeWaypointsInBlocks(List<Block> blocks) {
        List<Waypoint> toRemove = new ArrayList<>();
        for (Block block : blocks) {
            Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypointAt(block.getLocation());
            wpOpt.ifPresent(toRemove::add);
        }
        for (Waypoint wp : toRemove) {
            plugin.getWaypointManager().cleanStale(wp);
        }
    }
}
