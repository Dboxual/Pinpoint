package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
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
import java.util.stream.Collectors;

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

        // Right-click on a waypoint block
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
                plugin.getGuiManager().openHubGui(player, wp.getId());
                return;
            }
        }

        // Right-click air or block with Waypoint Pearl (but not onto a waypoint block handled above)
        ItemStack item = event.getItem();
        if (item == null || !plugin.getItemManager().isWaypointPearl(item)) return;
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        if (!player.hasPermission("waypoint.use")) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }

        plugin.getGuiManager().openHubGui(player, null);
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

        // Shift+right-click player → party link request
        if (player.isSneaking()) {
            plugin.getPartyGuiManager().sendLinkRequest(player, target);
            return;
        }

        // Regular right-click player → waypoint invite (existing behaviour)
        if (!plugin.getConfig().getBoolean("settings.allow-recall-orb-invites", true)) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("recall-orb-invites-disabled"));
            return;
        }

        List<Waypoint> invitable = plugin.getWaypointManager()
                .getAccessibleWaypoints(player.getUniqueId());

        if (plugin.getConfig().getBoolean("settings.require-owner-for-orb-invites", true)) {
            invitable = invitable.stream()
                    .filter(wp -> wp.isOwner(player.getUniqueId()))
                    .collect(Collectors.toList());
        }

        if (invitable.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cYou have no waypoints you can invite players to.");
            return;
        }

        plugin.getGuiManager().openInviteSelectGui(player, target, invitable);
    }
}
