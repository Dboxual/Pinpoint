package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.WaypointManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeleportCancelListener implements Listener {

    private final PinpointPlugin plugin;

    public TeleportCancelListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Only cancel on block position change — head rotation is fine
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        cancelTeleport(event.getPlayer(), plugin.msg("prefix") + plugin.msgCfg("teleport-cancelled-moved"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        cancelTeleport(player, plugin.msg("prefix") + plugin.msgCfg("teleport-cancelled-damaged"));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancelTeleport(player, null);
        // Clear pending waypoint teleport invite and party travel offer
        plugin.getWaypointManager().removeInvite(player.getUniqueId());
        plugin.getPartyManager().clearLastTravelOffer(player.getUniqueId());
    }

    private void cancelTeleport(Player player, String message) {
        if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
        WaypointManager.PendingTeleport pt = plugin.getWaypointManager().getPendingTeleport(player.getUniqueId());
        if (pt.taskId != -1) plugin.getServer().getScheduler().cancelTask(pt.taskId);
        plugin.getWaypointManager().clearPendingTeleport(player.getUniqueId());
        if (message != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }
}
