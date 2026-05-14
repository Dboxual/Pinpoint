package com.pinpoint.util;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class TeleportHelper {

    private final PinpointPlugin plugin;

    public TeleportHelper(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initiates a delayed teleport with a countdown. All players (including owners)
     * see the countdown and are subject to cancellation on movement/damage/logout.
     * After the delay, the actual teleport fires and the reuse cooldown is set.
     */
    public void teleport(Player player, Waypoint wp) {
        // Reuse cooldown — applies to all players
        if (plugin.getWaypointManager().isOnRecallCooldown(player.getUniqueId())) {
            long secs = plugin.getWaypointManager().getRemainingCooldownSeconds(player.getUniqueId());
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("cooldown-active"), secs));
            return;
        }

        // Already waiting on a previous teleport?
        if (plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cYou already have a teleport in progress.");
            return;
        }

        int delaySeconds = plugin.getConfig().getInt("settings.teleport-delay-seconds", 10);

        if (delaySeconds <= 0) {
            doTeleport(player, wp);
            return;
        }

        WaypointManager.PendingTeleport pt = new WaypointManager.PendingTeleport(
                player.getUniqueId(), wp.getId(), player.getLocation().clone());
        plugin.getWaypointManager().setPendingTeleport(player.getUniqueId(), pt);

        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("teleport-countdown"), delaySeconds));

        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
            plugin.getWaypointManager().clearPendingTeleport(player.getUniqueId());

            // Re-validate waypoint in case it was deleted during the countdown
            plugin.getWaypointManager().getWaypoint(wp.getId()).ifPresentOrElse(
                    current -> doTeleport(player, current),
                    () -> player.sendMessage(plugin.msg("prefix") + "§cWaypoint no longer exists.")
            );
        }, delaySeconds * 20L).getTaskId();

        pt.taskId = taskId;
    }

    // --- Internal: immediate teleport (called after countdown elapses) ---

    public void doTeleport(Player player, Waypoint wp) {
        World world = org.bukkit.Bukkit.getWorld(wp.getWorldName());
        if (world == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCannot teleport — world §e"
                    + wp.getWorldName() + "§c is not loaded.");
            return;
        }

        Location loc = wp.getLocation();
        if (loc == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCannot teleport — waypoint location is invalid.");
            return;
        }

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
            if (fee > 0 && plugin.getEconomyManager().isEnabled()) {
                plugin.getEconomyManager().deposit(player, fee);
                player.sendMessage(plugin.msg("prefix") + "§aFee refunded.");
            }
            return;
        }

        player.teleport(safe);
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("waypoint-teleported"), wp.getName()));
        plugin.getWaypointManager().setRecallCooldown(player.getUniqueId());
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
