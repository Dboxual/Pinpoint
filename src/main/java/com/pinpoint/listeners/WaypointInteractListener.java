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

    // ignoreCancelled is intentionally false (the default) so we process the event even when
    // a plugin such as WorldGuard has already cancelled it for a protected region.
    // Our GUI open is custom code; it is not gated on the event's cancelled state.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // getHand() is documented to be nullable. It is null for some empty-hand interactions
        // and for certain Geyser/Bedrock client packets. Store once and use null-safe comparisons.
        EquipmentSlot hand = event.getHand();

        // ── Waypoint block right-click ──────────────────────────────────────────────
        //
        // This path is evaluated BEFORE any hand filter so that:
        //   • empty main hand  works (getHand() == HAND or null)
        //   • item in main hand works (getHand() == HAND)
        //   • offhand item present works (fires as HAND *and* OFF_HAND; handled below)
        //   • Geyser/Bedrock works (getHand() may be null)
        //   • WorldGuard-protected regions work (we run even if the event was cancelled)
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Optional<Waypoint> wpOpt = plugin.getWaypointManager()
                    .getWaypointAt(event.getClickedBlock().getLocation());
            if (wpOpt.isPresent()) {
                // Cancel vanilla block use regardless of hand so that, for example, a compass
                // in the offhand cannot magnetise the Lodestone via the OFF_HAND firing.
                event.setCancelled(true);

                // When the player has items in both hands, Bukkit fires the event twice —
                // once for HAND and once for OFF_HAND. We open the GUI only on the first
                // (HAND or null-hand) firing and ignore the duplicate OFF_HAND event.
                if (EquipmentSlot.OFF_HAND.equals(hand)) return;

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

        // ── Pinpoint Compass right-click (air or non-waypoint block) ───────────────
        //
        // Strictly main-hand only: prevents double-processing when both HAND and
        // OFF_HAND events arrive. Null-safe: null hand is not HAND, so Geyser clients
        // holding nothing in either hand won't accidentally trigger the compass path.
        if (!EquipmentSlot.HAND.equals(hand)) return;

        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemManager().isWaypointCompass(item)) return;
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

        // Normal right-click: open hub GUI (full countdown applies)
        plugin.getGuiManager().openHubGui(player, null, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isWaypointCompass(item)) return;
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
