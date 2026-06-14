package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.WaypointManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeleportCancelListener implements Listener {

    private final PinpointPlugin plugin;

    public TeleportCancelListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Only cancel on block position change — head rotation is fine
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();
        cancelTeleport(player, plugin.msg("prefix") + plugin.msgCfg("teleport-cancelled-moved"));

        if (plugin.getWaypointManager().hasPlayerSession(player.getUniqueId())) {
            WaypointManager.PlayerTeleportSession session =
                    plugin.getWaypointManager().getPlayerSession(player.getUniqueId());
            plugin.getTeleportHelper().cancelPlayerSession(session, player.getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        cancelTeleport(player, plugin.msg("prefix") + plugin.msgCfg("teleport-cancelled-damaged"));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any outgoing player teleport request
        UUID outgoingTarget = plugin.getWaypointManager().getPendingPlayerRequest(player.getUniqueId());
        if (outgoingTarget != null) {
            int taskId = plugin.getWaypointManager().getPendingPlayerRequestTaskId(player.getUniqueId());
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingPlayerRequest(player.getUniqueId());
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(player.getUniqueId());
            Player target = plugin.getServer().getPlayer(outgoingTarget);
            if (target != null) target.sendMessage(plugin.msg("prefix")
                    + "§7Request from §b" + player.getName() + " §7cancelled — they disconnected.");
        }

        // Cancel any incoming player teleport request targeting this player
        UUID incomingRequester = plugin.getWaypointManager().getIncomingRequest(player.getUniqueId());
        if (incomingRequester != null) {
            int taskId = plugin.getWaypointManager().getPendingPlayerRequestTaskId(incomingRequester);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingPlayerRequest(incomingRequester);
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(incomingRequester);
            Player requester = plugin.getServer().getPlayer(incomingRequester);
            if (requester != null) requester.sendMessage(plugin.msg("prefix")
                    + "§7Your request to §b" + player.getName() + " §7was cancelled — they disconnected.");
        }

        // Cancel active player teleport session
        if (plugin.getWaypointManager().hasPlayerSession(player.getUniqueId())) {
            WaypointManager.PlayerTeleportSession session =
                    plugin.getWaypointManager().getPlayerSession(player.getUniqueId());
            plugin.getTeleportHelper().cancelPlayerSession(session, player.getUniqueId(), false);
        }

        cancelTeleport(player, null);
        plugin.getPartyManager().clearLastTravelOffer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getWaypointManager().hasPlayerSession(player.getUniqueId())) {
            WaypointManager.PlayerTeleportSession session =
                    plugin.getWaypointManager().getPlayerSession(player.getUniqueId());
            plugin.getTeleportHelper().cancelPlayerSession(session, player.getUniqueId(), false);
        }
    }

    private void cancelTeleport(Player player, String message) {
        if (!plugin.getWaypointManager().hasPendingTeleport(player.getUniqueId())) return;
        WaypointManager.PendingTeleport pt = plugin.getWaypointManager().getPendingTeleport(player.getUniqueId());
        if (pt.taskId != -1) plugin.getServer().getScheduler().cancelTask(pt.taskId);
        if (pt.countdownTaskId != -1) plugin.getServer().getScheduler().cancelTask(pt.countdownTaskId);
        plugin.getWaypointManager().clearPendingTeleport(player.getUniqueId());
        if (player.isOnline()) {
            player.sendActionBar(Component.empty());
            if (message != null) player.sendMessage(message);
        }
    }
}
