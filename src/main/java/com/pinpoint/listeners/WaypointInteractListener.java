package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.TravelOffer;
import com.pinpoint.data.Waypoint;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public class WaypointInteractListener implements Listener {

    private final PinpointPlugin plugin;

    public WaypointInteractListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        // Right-click on a waypoint block (shift state irrelevant here)
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Optional<Waypoint> wpOpt = plugin.getWaypointManager()
                    .getWaypointAt(event.getClickedBlock().getLocation());
            if (wpOpt.isPresent()) {
                event.setCancelled(true);
                if (!player.hasPermission("waypoint.use")) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
                    return;
                }
                Waypoint wp = wpOpt.get();
                if (!wp.canAccess(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + "§cThis waypoint is private.");
                    return;
                }
                plugin.getGuiManager().openHubGui(player, wp.getId(), true);
                return;
            }
        }

        // Right-click air or block with Waypoint Pearl
        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemManager().isWaypointPearl(item)) return;
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        if (!player.hasPermission("waypoint.use")) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }

        if (player.isSneaking()) {
            // Shift+right-click: accept pending teleport invite, or follow party travel offer
            if (plugin.getWaypointManager().hasPendingInvite(player.getUniqueId())) {
                plugin.getCommandHandler().processAccept(player);
                return;
            }
            TravelOffer offer = plugin.getPartyManager().getLastTravelOffer(player.getUniqueId());
            if (offer != null) {
                plugin.getPartyCommand().processFollowOffer(player, offer);
                return;
            }
            player.sendMessage(plugin.msg("prefix") + "§7No pending invite or travel offer.");
            return;
        }

        // Normal right-click: open hub GUI (pearl path — full countdown applies)
        plugin.getGuiManager().openHubGui(player, null, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isWaypointPearl(item)) return;
        event.setCancelled(true);

        if (!player.hasPermission("waypoint.use")) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }

        if (player.isSneaking()) {
            // Shift+right-click player → party link request
            plugin.getPartyGuiManager().sendLinkRequest(player, target);
        } else {
            // Regular right-click player → invite them to a waypoint
            List<Waypoint> owned = plugin.getWaypointManager().getOwnedWaypoints(player.getUniqueId());
            if (owned.isEmpty()) {
                player.sendMessage(plugin.msg("prefix") + "§cYou don't own any waypoints to invite to.");
                return;
            }
            plugin.getGuiManager().openInviteSelectGui(player, target, owned);
        }
    }
}
