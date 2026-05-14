package com.waypointsystem.util;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class TeleportHelper {

    private final WaypointPlugin plugin;

    public TeleportHelper(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, Waypoint wp) {
        Location loc = wp.getLocation();
        if (loc == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCould not load that waypoint's world.");
            return;
        }

        double fee = wp.getFee();
        if (fee > 0 && plugin.getEconomyManager().isEnabled()) {
            if (!plugin.getEconomyManager().hasBalance(player, fee)) {
                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.getConfig().getString("messages.fee-insufficient", "&cNeed %s"),
                                plugin.getEconomyManager().format(fee)).replace("&", "§"));
                return;
            }
            plugin.getEconomyManager().charge(player, fee);
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.getConfig().getString("messages.fee-charged", "&aCharged %s"),
                            plugin.getEconomyManager().format(fee)).replace("&", "§"));
        }

        Location safe = findSafe(loc);
        player.teleport(safe);
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.getConfig().getString("messages.waypoint-teleported", "&aTeleported to %s"),
                        wp.getName()).replace("&", "§"));
    }

    public Location findSafe(Location origin) {
        int radius = plugin.getConfig().getInt("settings.safe-teleport-radius", 5);
        // Try the exact location first (on top of the block)
        Location candidate = origin.clone();
        candidate.setY(Math.floor(candidate.getY()));

        if (isSafe(candidate)) return center(candidate);

        // Search upward from origin
        for (int dy = 0; dy <= radius * 2; dy++) {
            Location check = candidate.clone().add(0, dy, 0);
            if (isSafe(check)) return center(check);
        }

        // Spiral outward
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 0; dy <= radius; dy++) {
                        Location check = candidate.clone().add(dx, dy, dz);
                        if (isSafe(check)) return center(check);
                    }
                }
            }
        }

        // Fall back to original with yaw/pitch
        return center(origin.clone());
    }

    private boolean isSafe(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block floor = loc.clone().add(0, -1, 0).getBlock();
        return feet.getType().isAir()
                && head.getType().isAir()
                && floor.getType().isSolid()
                && !floor.isLiquid();
    }

    private Location center(Location loc) {
        return new Location(loc.getWorld(),
                Math.floor(loc.getX()) + 0.5,
                loc.getY(),
                Math.floor(loc.getZ()) + 0.5,
                loc.getYaw(),
                loc.getPitch());
    }
}
