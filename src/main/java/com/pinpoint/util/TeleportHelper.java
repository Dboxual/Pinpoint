package com.pinpoint.util;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
     * Pearl-path teleport: 10-second countdown by default (waypoint-pearl-teleport-delay-seconds).
     * Cancelled by movement, damage, or logout. Item switching does NOT cancel it.
     */
    public void teleport(Player player, Waypoint wp) {
        startTeleport(player, wp,
                plugin.getConfig().getInt("settings.waypoint-pearl-teleport-delay-seconds", 10),
                false);
    }

    /**
     * Block-path teleport: 5-second countdown by default (waypoint-block-teleport-delay-seconds).
     * Still runs fee and safe-spot checks. Cooldown still applies.
     */
    public void teleportFromBlock(Player player, Waypoint wp) {
        startTeleport(player, wp,
                plugin.getConfig().getInt("settings.waypoint-block-teleport-delay-seconds", 5),
                true);
    }

    private void startTeleport(Player player, Waypoint wp, int delaySeconds, boolean fromBlock) {
        // Reuse cooldown — applies to all players and both teleport paths
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

        if (delaySeconds <= 0) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("teleport-block"));
            player.sendActionBar(Component.text("Teleporting...").color(NamedTextColor.GREEN));
            doTeleport(player, wp, false);
            return;
        }

        WaypointManager.PendingTeleport pt = new WaypointManager.PendingTeleport(
                player.getUniqueId(), wp.getId(), player.getLocation().clone());
        plugin.getWaypointManager().setPendingTeleport(player.getUniqueId(), pt);

        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("teleport-countdown"), delaySeconds));

        // Action bar ticker — counts down every second until teleport fires or is cancelled
        int[] remaining = {delaySeconds};
        int countdownTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
            if (remaining[0] > 0) {
                player.sendActionBar(Component.text("Teleporting in ")
                        .color(NamedTextColor.YELLOW)
                        .append(Component.text(remaining[0] + "s")
                                .color(NamedTextColor.WHITE)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text("... Don't move.")
                                .color(NamedTextColor.YELLOW)));
                remaining[0]--;
            }
        }, 0L, 20L).getTaskId();
        pt.countdownTaskId = countdownTaskId;

        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
            plugin.getWaypointManager().clearPendingTeleport(player.getUniqueId());
            // Stop the countdown ticker and clear action bar
            if (pt.countdownTaskId != -1) plugin.getServer().getScheduler().cancelTask(pt.countdownTaskId);
            player.sendActionBar(Component.empty());

            // Re-validate waypoint in case it was deleted during the countdown
            plugin.getWaypointManager().getWaypoint(wp.getId()).ifPresentOrElse(
                    current -> doTeleport(player, current, false),
                    () -> player.sendMessage(plugin.msg("prefix") + "§cPinpoint no longer exists.")
            );
        }, delaySeconds * 20L).getTaskId();

        pt.taskId = taskId;
    }

    /**
     * Party-follow teleport: skips the normal countdown and hold-still requirement.
     * A 1-second delay gives the "traveling together" feeling without the full queue.
     * suppressFollowPrompt=true so the follow itself does not trigger another notification chain.
     */
    public void partyFollow(Player player, Waypoint wp, String travelerName) {
        player.sendMessage(plugin.msg("prefix") + "§aTraveling with §b" + travelerName + "§a...");
        player.sendActionBar(Component.text("Following ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(travelerName).color(NamedTextColor.AQUA))
                .append(Component.text("...").color(NamedTextColor.GREEN)));
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getWaypointManager().getWaypoint(wp.getId()).ifPresentOrElse(
                        current -> doTeleport(player, current, true),
                        () -> player.sendMessage(plugin.msg("prefix")
                                + "§cThe Pinpoint you were following no longer exists.")
                ), 20L);
    }

    // --- Internal: immediate teleport (called after countdown elapses) ---

    public void doTeleport(Player player, Waypoint wp, boolean suppressFollowPrompt) {
        World world = org.bukkit.Bukkit.getWorld(wp.getWorldName());
        if (world == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCannot teleport — world §e"
                    + wp.getWorldName() + "§c is not loaded.");
            return;
        }

        Location loc = wp.getLocation();
        if (loc == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCannot teleport — Pinpoint location is invalid.");
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

            // Pay the owner (works for offline owners via Vault OfflinePlayer API)
            boolean paid = plugin.getEconomyManager().depositToOwner(wp.getOwnerUuid(), fee);
            if (!paid) {
                // Refund the player — owner deposit failed
                plugin.getEconomyManager().deposit(player, fee);
                player.sendMessage(plugin.msg("prefix")
                        + "§cTeleport fee could not be credited to the owner. Fee refunded.");
                return;
            }

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

        // Notify party members — skipped for follow teleports to prevent re-notification loops
        if (!suppressFollowPrompt) {
            plugin.getPartyGuiManager().notifyPartyTravel(player, wp);
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
