package com.pinpoint.util;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
import com.pinpoint.data.WaypointType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import java.util.UUID;

public class TeleportHelper {

    private final PinpointPlugin plugin;

    public TeleportHelper(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Compass/GUI teleport: countdown from teleport.compass-delay-seconds (default 10).
     * Applies to all waypoint types including Landmarks.
     * Cancelled by movement, damage, or logout. Item switching does NOT cancel it.
     */
    public void teleport(Player player, Waypoint wp) {
        startTeleport(player, wp,
                plugin.getConfig().getInt("teleport.compass-delay-seconds", 10),
                false);
    }

    /**
     * Physical block teleport: countdown from teleport.block-delay-seconds (default 5).
     * Triggered by right-clicking a placed Pinpoint or Landmark block.
     * Applies to all waypoint types including Landmarks.
     */
    public void teleportFromBlock(Player player, Waypoint wp) {
        startTeleport(player, wp,
                plugin.getConfig().getInt("teleport.block-delay-seconds", 5),
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
     * Group teleport: 5-second countdown with movement cancellation.
     * Used for both invite-accept travel and party follow.
     * suppressFollowPrompt=true prevents re-triggering the party notification chain.
     */
    public void teleportGroupMember(Player player, Waypoint wp, String partnerName, boolean suppressFollowPrompt) {
        int delaySeconds = 5;

        if (plugin.getWaypointManager().isOnRecallCooldown(player.getUniqueId())) {
            long secs = plugin.getWaypointManager().getRemainingCooldownSeconds(player.getUniqueId());
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("cooldown-active"), secs));
            return;
        }
        if (plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cYou already have a teleport in progress.");
            return;
        }

        WaypointManager.PendingTeleport pt = new WaypointManager.PendingTeleport(
                player.getUniqueId(), wp.getId(), player.getLocation().clone());
        plugin.getWaypointManager().setPendingTeleport(player.getUniqueId(), pt);

        player.sendMessage(plugin.msg("prefix")
                + "§aTraveling to §b" + wp.getName()
                + "§a with §b" + partnerName
                + "§a — stand still for §b5s§a!");

        int[] remaining = {delaySeconds};
        int countdownTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
            if (remaining[0] > 0) {
                player.sendActionBar(Component.text("Traveling with ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(partnerName).color(NamedTextColor.AQUA))
                        .append(Component.text(" in ").color(NamedTextColor.GREEN))
                        .append(Component.text(remaining[0] + "s")
                                .color(NamedTextColor.WHITE)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text("... Don't move.").color(NamedTextColor.YELLOW)));
                remaining[0]--;
            }
        }, 0L, 20L).getTaskId();
        pt.countdownTaskId = countdownTaskId;

        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
            plugin.getWaypointManager().clearPendingTeleport(player.getUniqueId());
            if (pt.countdownTaskId != -1) plugin.getServer().getScheduler().cancelTask(pt.countdownTaskId);
            player.sendActionBar(Component.empty());

            plugin.getWaypointManager().getWaypoint(wp.getId()).ifPresentOrElse(
                    current -> doTeleport(player, current, suppressFollowPrompt),
                    () -> player.sendMessage(plugin.msg("prefix") + "§cPinpoint no longer exists.")
            );
        }, delaySeconds * 20L).getTaskId();

        pt.taskId = taskId;
    }

    /**
     * Party-follow teleport: 5-second group countdown with movement cancellation.
     * suppressFollowPrompt=true so the follow itself does not trigger another notification chain.
     */
    public void partyFollow(Player player, Waypoint wp, String travelerName) {
        teleportGroupMember(player, wp, travelerName, true);
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
        }

        Location safe = findSafe(loc);
        if (safe == null) {
            player.sendMessage(plugin.msg("prefix") + "§cCould not find a safe landing spot near §e"
                    + wp.getName() + "§c. Teleport aborted.");
            return;
        }

        if (wp.hasTeleportDirection()) {
            safe.setYaw(directionToYaw(wp.getTeleportDirection()));
            safe.setPitch(0f);
        } else if (wp.hasTeleportYaw()) {
            safe.setYaw(wp.getTeleportYaw());
            safe.setPitch(wp.getTeleportPitch());
        }

        player.teleport(safe);

        if (fee > 0 && plugin.getEconomyManager().isEnabled()) {
            plugin.getEconomyManager().charge(player, fee);

            if (wp.getType() == WaypointType.LANDMARK) {
                // Landmarks have no owner — fee is charged but not forwarded
                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("fee-charged"),
                                plugin.getEconomyManager().format(fee)));
            } else {
                // Pay the owner (works for offline owners via Vault OfflinePlayer API)
                boolean paid = plugin.getEconomyManager().depositToOwner(wp.getOwnerUuid(), fee);
                if (!paid) {
                    // Teleport already succeeded — refund the player since owner credit failed
                    plugin.getEconomyManager().deposit(player, fee);
                    player.sendMessage(plugin.msg("prefix")
                            + "§cTeleport fee could not be credited to the owner. Fee refunded.");
                } else {
                    player.sendMessage(plugin.msg("prefix") +
                            String.format(plugin.msgCfg("fee-charged"),
                                    plugin.getEconomyManager().format(fee)));
                }
            }
        }

        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("waypoint-teleported"), wp.getName()));
        plugin.getWaypointManager().setRecallCooldown(player.getUniqueId());

        // 5-second invincibility window after landing — only applied if not already invulnerable
        if (!player.isInvulnerable()) {
            player.setInvulnerable(true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.setInvulnerable(false);
            }, 100L);
        }

        // Notify party members — skipped for follow teleports to prevent re-notification loops
        if (!suppressFollowPrompt) {
            plugin.getPartyGuiManager().notifyPartyTravel(player, wp);
        }
    }

    private static float directionToYaw(String direction) {
        return switch (direction) {
            case "NORTH" -> 180f;
            case "SOUTH" -> 0f;
            case "EAST"  -> -90f;
            case "WEST"  -> 90f;
            default      -> 0f;
        };
    }

    // ─── Player-to-player teleport ────────────────────────────────────────────────

    public void sendPlayerTeleportRequest(Player requester, Player target) {
        if (!target.isOnline()) {
            requester.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " is no longer online.");
            return;
        }
        if (plugin.getWaypointManager().hasPendingPlayerRequest(requester.getUniqueId())) {
            requester.sendMessage(plugin.msg("prefix") + "§cYou already have a pending teleport request.");
            return;
        }
        if (plugin.getWaypointManager().hasPlayerSession(requester.getUniqueId())
                || plugin.getWaypointManager().hasPendingTeleport(requester.getUniqueId())) {
            requester.sendMessage(plugin.msg("prefix") + "§cYou already have a teleport in progress.");
            return;
        }

        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();
        String requesterName = requester.getName();
        String targetName = target.getName();

        int timeout = plugin.getConfig().getInt("player-teleport.request-expire-seconds", 10);

        plugin.getWaypointManager().setPendingPlayerRequest(requesterId, targetId);
        plugin.getWaypointManager().setPendingPlayerRequestExpiry(requesterId,
                System.currentTimeMillis() + timeout * 1000L);

        int expiryTaskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getWaypointManager().hasPendingPlayerRequest(requesterId)) return;
            if (!targetId.equals(plugin.getWaypointManager().getPendingPlayerRequest(requesterId))) return;
            plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);
            Player r = Bukkit.getPlayer(requesterId);
            Player t = Bukkit.getPlayer(targetId);
            if (r != null) r.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-request-expired-requester"), targetName));
            if (t != null) t.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-request-expired-target"), requesterName));
        }, timeout * 20L).getTaskId();
        plugin.getWaypointManager().setPendingPlayerRequestTaskId(requesterId, expiryTaskId);

        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-request-received"), requesterName));
        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-request-expires-in"), timeout));
    }

    public void acceptPlayerTeleportRequest(Player target, Player requester) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();
        String requesterName = requester.getName();
        String targetName = target.getName();

        int expiryTask = plugin.getWaypointManager().getPendingPlayerRequestTaskId(requesterId);
        if (expiryTask != -1) plugin.getServer().getScheduler().cancelTask(expiryTask);
        plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
        plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
        plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);

        if (plugin.getWaypointManager().hasPlayerSession(requesterId)
                || plugin.getWaypointManager().hasPlayerSession(targetId)
                || plugin.getWaypointManager().hasPendingTeleport(requesterId)
                || plugin.getWaypointManager().hasPendingTeleport(targetId)) {
            target.sendMessage(plugin.msg("prefix") + "§cCannot accept — a conflicting teleport is already in progress.");
            requester.sendMessage(plugin.msg("prefix") + "§cYour request was cancelled — a conflicting teleport is in progress.");
            return;
        }

        int standStill = plugin.getConfig().getInt("player-teleport.stand-still-seconds", 10);
        int cost = plugin.getConfig().getInt("player-teleport.cost-bops", 100);

        // Pre-countdown balance check — both must have enough Bops before the countdown starts
        if (cost > 0 && plugin.getEconomyManager().isEnabled()) {
            boolean requesterAfford = plugin.getEconomyManager().hasBalance(requester, cost);
            boolean targetAfford   = plugin.getEconomyManager().hasBalance(target, cost);
            if (!requesterAfford || !targetAfford) {
                Player poor  = !requesterAfford ? requester : target;
                Player other = !requesterAfford ? target    : requester;
                poor.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("player-tp-no-bops-self"), cost));
                other.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("player-tp-no-bops-other"), poor.getName(), cost));
                return;
            }
        }

        requester.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-request-accepted-requester"), targetName, standStill));
        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-request-accepted-target"), standStill));
        if (cost > 0) {
            requester.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-bops-notice"), cost));
            target.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-bops-notice"), cost));
        }

        WaypointManager.PlayerTeleportSession session = new WaypointManager.PlayerTeleportSession(
                requesterId, targetId, requesterName, targetName);
        plugin.getWaypointManager().setPlayerSession(requesterId, targetId, session);

        int[] remaining = {standStill};
        int countdownTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getWaypointManager().hasPlayerSession(requesterId)) return;
            if (remaining[0] > 0) {
                Player r = Bukkit.getPlayer(requesterId);
                Player t = Bukkit.getPlayer(targetId);
                if (r != null) r.sendActionBar(Component.text("Teleporting to ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text(targetName).color(NamedTextColor.AQUA))
                        .append(Component.text(" in ").color(NamedTextColor.GREEN))
                        .append(Component.text(remaining[0] + "s").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                        .append(Component.text("... Don't move!").color(NamedTextColor.YELLOW)));
                if (t != null) t.sendActionBar(Component.text(requesterName)
                        .color(NamedTextColor.AQUA)
                        .append(Component.text(" is teleporting to you in ").color(NamedTextColor.GREEN))
                        .append(Component.text(remaining[0] + "s").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                        .append(Component.text("... Don't move!").color(NamedTextColor.YELLOW)));
                remaining[0]--;
            }
        }, 0L, 20L).getTaskId();
        session.countdownTaskId = countdownTaskId;

        int teleportTaskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getWaypointManager().hasPlayerSession(requesterId)) return;
            plugin.getWaypointManager().clearPlayerSession(requesterId, targetId);
            if (session.countdownTaskId != -1) plugin.getServer().getScheduler().cancelTask(session.countdownTaskId);
            executePlayerTeleport(session);
        }, standStill * 20L).getTaskId();
        session.teleportTaskId = teleportTaskId;
    }

    public void denyPlayerTeleportRequest(Player target, Player requester) {
        UUID requesterId = requester.getUniqueId();
        int expiryTask = plugin.getWaypointManager().getPendingPlayerRequestTaskId(requesterId);
        if (expiryTask != -1) plugin.getServer().getScheduler().cancelTask(expiryTask);
        plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
        plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
        plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);

        target.sendMessage(plugin.msg("prefix") + plugin.msgCfg("player-tp-request-denied-target"));
        requester.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-request-denied-requester"), target.getName()));
    }

    /**
     * Cancels an active player teleport session. Called by TeleportCancelListener.
     * movedAway=true means the canceller moved; false means they disconnected or changed world.
     */
    public void cancelPlayerSession(WaypointManager.PlayerTeleportSession session, UUID cancellerUuid, boolean movedAway) {
        if (session.teleportTaskId != -1) plugin.getServer().getScheduler().cancelTask(session.teleportTaskId);
        if (session.countdownTaskId != -1) plugin.getServer().getScheduler().cancelTask(session.countdownTaskId);
        plugin.getWaypointManager().clearPlayerSession(session.requesterId, session.targetId);

        boolean cancellerIsRequester = cancellerUuid.equals(session.requesterId);
        String cancellerName = cancellerIsRequester ? session.requesterName : session.targetName;
        UUID otherId = cancellerIsRequester ? session.targetId : session.requesterId;

        Player canceller = Bukkit.getPlayer(cancellerUuid);
        Player other = Bukkit.getPlayer(otherId);

        if (canceller != null && canceller.isOnline()) {
            canceller.sendActionBar(Component.empty());
            if (movedAway) canceller.sendMessage(plugin.msg("prefix") + plugin.msgCfg("player-tp-cancelled-moved"));
        }
        if (other != null && other.isOnline()) {
            other.sendActionBar(Component.empty());
            if (movedAway) {
                other.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("player-tp-cancelled-other-moved"), cancellerName));
            } else {
                other.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("player-tp-cancelled-disconnect"), cancellerName));
            }
        }
    }

    private void executePlayerTeleport(WaypointManager.PlayerTeleportSession session) {
        Player requester = Bukkit.getPlayer(session.requesterId);
        Player target = Bukkit.getPlayer(session.targetId);

        if (requester == null || !requester.isOnline()) {
            if (target != null && target.isOnline()) {
                target.sendActionBar(Component.empty());
                target.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("player-tp-cancelled-disconnect"), session.requesterName));
            }
            return;
        }
        if (target == null || !target.isOnline()) {
            requester.sendActionBar(Component.empty());
            requester.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-cancelled-disconnect"), session.targetName));
            return;
        }

        requester.sendActionBar(Component.empty());
        target.sendActionBar(Component.empty());

        int cost = plugin.getConfig().getInt("player-teleport.cost-bops", 100);

        Location dest = findSafe(target.getLocation());
        if (dest == null) dest = target.getLocation();

        requester.teleport(dest);

        requester.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-success-requester"), session.targetName));
        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("player-tp-success-target"), session.requesterName));

        // Charge both players after a successful teleport
        if (cost > 0 && plugin.getEconomyManager().isEnabled()) {
            boolean rCharged = plugin.getEconomyManager().charge(requester, cost);
            if (!rCharged) {
                plugin.getLogger().warning("[Pinpoint] Post-teleport Bops charge FAILED for "
                        + session.requesterName + " (balance too low after countdown). No one was charged.");
                requester.sendMessage(plugin.msg("prefix")
                        + "§cBops charge failed — you ran out during the countdown. No Bops were taken.");
                target.sendMessage(plugin.msg("prefix")
                        + "§cBops charge failed — " + session.requesterName + " ran out during the countdown. No Bops were taken.");
                return;
            }
            boolean tCharged = plugin.getEconomyManager().charge(target, cost);
            if (!tCharged) {
                // Refund the requester so neither player loses Bops
                plugin.getEconomyManager().deposit(requester, cost);
                plugin.getLogger().warning("[Pinpoint] Post-teleport Bops charge FAILED for "
                        + session.targetName + " (balance too low after countdown). Requester refunded.");
                requester.sendMessage(plugin.msg("prefix")
                        + "§cBops charge failed — " + session.targetName + " ran out during the countdown. No Bops were taken.");
                target.sendMessage(plugin.msg("prefix")
                        + "§cBops charge failed — you ran out during the countdown. No Bops were taken.");
                return;
            }
            requester.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-bops-charged"), cost));
            target.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("player-tp-bops-charged"), cost));
        }

        plugin.getWaypointManager().setRecallCooldown(session.requesterId);

        if (!requester.isInvulnerable()) {
            requester.setInvulnerable(true);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (requester.isOnline()) requester.setInvulnerable(false);
            }, 100L);
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
