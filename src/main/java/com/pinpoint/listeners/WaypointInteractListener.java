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
        Player player = event.getPlayer();

        // Cancel vanilla item behaviour for tagged Pinpoint items in either hand.
        // Old Ender-Pearl tagged items still need this to prevent throwing.
        // New Compass items have no vanilla right-click action but we cancel anyway for consistency.
        // PlayerInteractEvent fires separately for HAND and OFF_HAND; cancelling both firings
        // prevents the OFF_HAND event from reaching vanilla behaviour.
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand  = player.getInventory().getItemInOffHand();
        if (plugin.getItemManager().isWaypointPearl(mainHand)
                || plugin.getItemManager().isWaypointPearl(offHand)) {
            event.setCancelled(true);
        }

        // GUI / invite logic only runs for the main-hand firing.
        if (event.getHand() != EquipmentSlot.HAND) return;
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
                    player.sendMessage(plugin.msg("prefix") + "§cThis Pinpoint is private.");
                    return;
                }
                plugin.getGuiManager().openHubGui(player, wp.getId(), true);
                return;
            }
        }

        // Right-click air or block with Waypoint Compass (or old tagged Ender Pearl)
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

        // Normal right-click: open hub GUI (compass path — full countdown applies)
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

        List<Waypoint> owned = plugin.getWaypointManager().getOwnedWaypoints(player.getUniqueId());

        if (player.isSneaking()) {
            // Shift+right-click player → invite or remove access
            if (!owned.isEmpty()) {
                plugin.getGuiManager().openInviteSelectGui(player, target, owned);
            } else {
                // No Pinpoints owned — fall back to party link request
                plugin.getPartyGuiManager().sendLinkRequest(player, target);
            }
        } else {
            // Regular right-click player → invite or remove access
            if (owned.isEmpty()) {
                player.sendMessage(plugin.msg("prefix") + "§cYou don't own any Pinpoints to invite to.");
                return;
            }
            plugin.getGuiManager().openInviteSelectGui(player, target, owned);
        }
    }
}
