package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class BlockPlaceListener implements Listener {

    private final PinpointPlugin plugin;

    public BlockPlaceListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        boolean isLandmarkBlock = plugin.getItemManager().isLandmarkBlock(item);
        boolean isPinpointBlock = plugin.getItemManager().isWaypointBlockItem(item);

        if (!isLandmarkBlock && !isPinpointBlock) return;

        // ── Landmark Block path ──────────────────────────────────────────────
        if (isLandmarkBlock) {
            if (!player.hasPermission("pinpoint.admin.landmark.create")) {
                String msg = plugin.getConfig().getString(
                        "landmarks.block.no-permission-message",
                        "&cYou do not have permission to place Landmark Blocks.");
                player.sendMessage(plugin.msg("prefix") + msg.replace("&", "§"));
                event.setCancelled(true);
                return;
            }
            if (!plugin.getWaypointManager().hasPendingLandmarkCreation(player.getUniqueId())) {
                String msg = plugin.getConfig().getString(
                        "landmarks.block.setup-required-message",
                        "&cUse /wp landmark create <name> before placing a Landmark Block.");
                player.sendMessage(plugin.msg("prefix") + msg.replace("&", "§"));
                event.setCancelled(true);
                return;
            }
            if (!plugin.getConfig().getBoolean("landmarks.enabled", true)) {
                player.sendMessage(plugin.msg("prefix") + "§cLandmarks are disabled in config.");
                plugin.getWaypointManager().clearPendingLandmarkCreation(player.getUniqueId());
                event.setCancelled(true);
                return;
            }
            String name = plugin.getWaypointManager().getPendingLandmarkCreation(player.getUniqueId());
            plugin.getWaypointManager().clearPendingLandmarkCreation(player.getUniqueId());
            com.pinpoint.data.Waypoint landmark = plugin.getWaypointManager()
                    .createLandmark(name, event.getBlock().getLocation());
            plugin.getHologramManager().spawnHologram(landmark);
            player.sendMessage(plugin.msg("prefix") + "§aLandmark '§b" + name + "§a' created!");
            return;
        }

        // ── Normal Pinpoint Block path ────────────────────────────────────────
        if (!player.hasPermission("waypoint.use")) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            event.setCancelled(true);
            return;
        }

        if (plugin.getWaypointManager().isAtWaypointLimit(player.getUniqueId())) {
            int max = plugin.getWaypointManager().getMaxWaypoints();
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("waypoint-limit-reached"), max));
            event.setCancelled(true);
            return;
        }

        if (plugin.getWaypointManager().hasPendingNaming(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cFinish naming your current Pinpoint first.");
            event.setCancelled(true);
            return;
        }

        plugin.getWaypointManager().setPendingNaming(player.getUniqueId(), event.getBlock().getLocation());
        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-prompt"));
        plugin.getChatInputListener().schedulePendingNamingTimeout(player);
    }
}
