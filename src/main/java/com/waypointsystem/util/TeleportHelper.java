package com.waypointsystem.util;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class TeleportHelper {

    private final WaypointPlugin plugin;

    public TeleportHelper(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, Waypoint wp) {
        // Cooldown applies to non-owners only
        if (!wp.isOwner(player.getUniqueId())) {
            if (plugin.getWaypointManager().isOnRecallCooldown(player.getUniqueId())) {
                long secs = plugin.getWaypointManager().getRemainingCooldownSeconds(player.getUniqueId());
                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("cooldown-active"), secs));
                return;
            }
        }

        // World loaded?
        World world = org.bukkit.Bukkit.getWorld(wp.getWorldName());
        if (world == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCannot teleport — world §e" + wp.getWorldName()
                    + "§c is not loaded.");
            return;
        }

        Location loc = wp.getLocation();
        if (loc == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCannot teleport — waypoint location is invalid.");
            return;
        }

        // Charge fee before moving anything
        double fee = wp.getFee();
        if (fee > 0 && plugin.getEconomyManager().isEnabled()) {
            if (!plugin.getEconomyManager().hasBalance(player, fee)) {
                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("fee-insufficient"),
                                plugin.getEconomyManager().format(fee)));
                return;
            }
            plugin.getEconomyManager().charge(player, fee);
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("fee-charged"),
                            plugin.getEconomyManager().format(fee)));
        }

        Location safe = findSafe(loc);
        if (safe == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCould not find a safe landing spot near §e"
                    + wp.getName() + "§c. Teleport aborted.");
            // Refund fee on failed teleport
            if (fee > 0 && plugin.getEconomyManager().isEnabled()) {
                plugin.getEconomyManager().deposit(player, fee);
                player.sendMessage(plugin.msg("prefix") + "§aFee refunded.");
            }
            return;
        }

        player.teleport(safe);
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("waypoint-teleported"), wp.getName()));

        if (!wp.isOwner(player.getUniqueId())) {
            plugin.getWaypointManager().setRecallCooldown(player.getUniqueId());
        }
    }

    // Returns null if no safe spot found within radius.
    public Location findSafe(Location origin) {
        int radius = plugin.getConfig().getInt("settings.safe-teleport-radius", 5);
        Location candidate = origin.clone();
        candidate.setY(Math.floor(candidate.getY()));

        if (isSafe(candidate)) return center(candidate);

        for (int dy = 1; dy <= radius * 2; dy++) {
            Location check = candidate.clone().add(0, dy, 0);
            if (isSafe(check)) return center(check);
        }

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

        return null;
    }

    private boolean isSafe(Location loc) {
        Block feet  = loc.getBlock();
        Block head  = loc.clone().add(0, 1, 0).getBlock();
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
