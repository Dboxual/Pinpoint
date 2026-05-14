package com.waypointsystem.listeners;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import com.waypointsystem.item.ItemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

public class WaypointInteractListener implements Listener {

    private final WaypointPlugin plugin;

    public WaypointInteractListener(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        ItemManager im = plugin.getItemManager();

        if (im.isWaypointItem(item)) {
            event.setCancelled(true);

            if (!player.hasPermission("waypoint.use")) {
                player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
                return;
            }

            if (im.isNamedWaypointItem(item)) {
                String idStr = im.getWaypointId(item);
                UUID waypointId = null;
                try { waypointId = UUID.fromString(idStr); } catch (Exception ignored) {}
                plugin.getGuiManager().openHubGui(player, waypointId);
            } else {
                if (plugin.getWaypointManager().hasPendingNaming(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + "§cYou already have a pending waypoint name input.");
                    return;
                }
                if (plugin.getWaypointManager().isAtWaypointLimit(player.getUniqueId())) {
                    int max = plugin.getWaypointManager().getMaxWaypoints();
                    player.sendMessage(plugin.msg("prefix") +
                            String.format(plugin.msgCfg("waypoint-limit-reached"), max));
                    return;
                }
                plugin.getWaypointManager().setPendingNaming(player.getUniqueId(), player.getLocation());
                player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("name-prompt"));
                plugin.getChatInputListener().schedulePendingNamingTimeout(player);
            }
            return;
        }

        if (im.isRecallOrb(item)) {
            event.setCancelled(true);

            if (!player.hasPermission("waypoint.use")) {
                player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
                return;
            }

            handleRecallOrbUse(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!plugin.getItemManager().isRecallOrb(item)) return;
        event.setCancelled(true);

        if (!player.hasPermission("waypoint.use")) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("no-permission"));
            return;
        }

        // Config gate for recall orb invites
        if (!plugin.getConfig().getBoolean("settings.allow-recall-orb-invites", true)) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("recall-orb-invites-disabled"));
            return;
        }

        String waypointIdStr = plugin.getItemManager().getRecallWaypointId(item);
        String orbIdStr = plugin.getItemManager().getRecallOrbId(item);
        if (waypointIdStr == null || orbIdStr == null) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("orb-invalid"));
            return;
        }

        UUID waypointId;
        UUID orbId;
        try {
            waypointId = UUID.fromString(waypointIdStr);
            orbId = UUID.fromString(orbIdStr);
        } catch (Exception e) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("orb-invalid"));
            return;
        }

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(waypointId);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat waypoint no longer exists.");
            return;
        }

        Waypoint wp = wpOpt.get();

        if (plugin.getConfig().getBoolean("settings.require-owner-for-orb-invites", true)
                && !wp.isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cOnly the waypoint owner can send invites with a Recall Orb.");
            return;
        }

        if (plugin.getWaypointManager().hasPendingInvite(target.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " already has a pending invite.");
            return;
        }

        plugin.getWaypointManager().createInvite(player.getUniqueId(), target.getUniqueId(), waypointId, orbId);

        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("invite-sent"), target.getName()));

        plugin.getGuiManager().openAcceptDenyGui(target, player.getName(), wp.getName());
        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("invite-received"), player.getName(), wp.getName()));

        int timeoutSeconds = plugin.getConfig().getInt("settings.invite-timeout", 60);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWaypointManager().hasPendingInvite(target.getUniqueId())) {
                plugin.getWaypointManager().removeInvite(target.getUniqueId());
                if (target.isOnline()) {
                    target.sendMessage(plugin.msg("prefix") + plugin.msgCfg("invite-expired"));
                }
                if (player.isOnline()) {
                    player.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + "'s invite expired.");
                }
            }
        }, timeoutSeconds * 20L);
    }

    private void handleRecallOrbUse(Player player, ItemStack item) {
        // Cooldown check
        if (plugin.getWaypointManager().isOnRecallCooldown(player.getUniqueId())) {
            long secs = plugin.getWaypointManager().getRemainingCooldownSeconds(player.getUniqueId());
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("cooldown-active"), secs));
            return;
        }

        String waypointIdStr = plugin.getItemManager().getRecallWaypointId(item);
        if (waypointIdStr == null) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("orb-invalid"));
            return;
        }

        UUID waypointId;
        try {
            waypointId = UUID.fromString(waypointIdStr);
        } catch (Exception e) {
            player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("orb-invalid"));
            return;
        }

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(waypointId);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat waypoint no longer exists.");
            return;
        }

        Waypoint wp = wpOpt.get();

        String ownerStr = plugin.getItemManager().getRecallOwner(item);
        boolean isOrbOwner = ownerStr != null && ownerStr.equals(player.getUniqueId().toString());
        boolean isWpOwner = wp.isOwner(player.getUniqueId());

        if (!isOrbOwner && !isWpOwner && !wp.canAccess(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cYou cannot use this Recall Orb.");
            return;
        }

        plugin.getWaypointManager().setRecallCooldown(player.getUniqueId());
        plugin.getTeleportHelper().teleport(player, wp);
    }
}
