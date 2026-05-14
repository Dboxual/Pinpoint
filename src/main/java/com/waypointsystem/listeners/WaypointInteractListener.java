package com.waypointsystem.listeners;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import com.waypointsystem.item.ItemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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

            if (im.isNamedWaypointItem(item)) {
                // Open hub focused on this waypoint
                String idStr = im.getWaypointId(item);
                UUID waypointId = null;
                try { waypointId = UUID.fromString(idStr); } catch (Exception ignored) {}
                plugin.getGuiManager().openHubGui(player, waypointId);
            } else {
                // Unnamed - start naming flow
                if (plugin.getWaypointManager().hasPendingNaming(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + "§cYou already have a pending waypoint name input.");
                    return;
                }
                plugin.getWaypointManager().setPendingNaming(player.getUniqueId(), player.getLocation());
                player.sendMessage(plugin.msg("prefix") +
                        plugin.getConfig().getString("messages.name-prompt", "&eType the waypoint name in chat (or 'cancel'):")
                                .replace("&", "§"));
                plugin.getChatInputListener().schedulePendingNamingTimeout(player);
            }
            return;
        }

        if (im.isRecallOrb(item)) {
            event.setCancelled(true);
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

        // Right-clicked another player with Recall Orb -> send invite
        String waypointIdStr = plugin.getItemManager().getRecallWaypointId(item);
        String orbIdStr = plugin.getItemManager().getRecallOrbId(item);
        if (waypointIdStr == null || orbIdStr == null) return;

        UUID waypointId;
        UUID orbId;
        try {
            waypointId = UUID.fromString(waypointIdStr);
            orbId = UUID.fromString(orbIdStr);
        } catch (Exception e) {
            return;
        }

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(waypointId);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat waypoint no longer exists.");
            return;
        }

        Waypoint wp = wpOpt.get();

        if (plugin.getWaypointManager().hasPendingInvite(target.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " already has a pending invite.");
            return;
        }

        // Create invite
        plugin.getWaypointManager().createInvite(player.getUniqueId(), target.getUniqueId(), waypointId, orbId);

        // Notify both
        player.sendMessage(plugin.msg("prefix") +
                String.format(plugin.getConfig().getString("messages.invite-sent", "&aInvite sent to %s."), target.getName())
                        .replace("&", "§"));

        // Show GUI invite to target if possible, else chat
        String inviterName = player.getName();
        String wpName = wp.getName();
        plugin.getGuiManager().openAcceptDenyGui(target, inviterName, wpName);

        // Also send chat message as fallback
        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.getConfig().getString("messages.invite-received",
                        "&b%s &ahas invited you to teleport to &b%s&a. Type &e/waypoint accept &aor &e/waypoint deny&a."),
                        player.getName(), wp.getName()).replace("&", "§"));

        // Expire invite after timeout
        int timeoutSeconds = plugin.getConfig().getInt("settings.invite-timeout", 60);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWaypointManager().hasPendingInvite(target.getUniqueId())) {
                plugin.getWaypointManager().removeInvite(target.getUniqueId());
                if (target.isOnline()) {
                    target.sendMessage(plugin.msg("prefix") +
                            plugin.getConfig().getString("messages.invite-expired", "&cYour invite has expired.")
                                    .replace("&", "§"));
                }
                if (player.isOnline()) {
                    player.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + "'s invite expired.");
                }
            }
        }, timeoutSeconds * 20L);
    }

    private void handleRecallOrbUse(Player player, ItemStack item) {
        String waypointIdStr = plugin.getItemManager().getRecallWaypointId(item);
        if (waypointIdStr == null) return;

        UUID waypointId;
        try { waypointId = UUID.fromString(waypointIdStr); } catch (Exception e) { return; }

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(waypointId);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat waypoint no longer exists.");
            return;
        }

        Waypoint wp = wpOpt.get();

        // Check if this player is the orb owner or the waypoint owner
        String ownerStr = plugin.getItemManager().getRecallOwner(item);
        boolean isOrbOwner = ownerStr != null && ownerStr.equals(player.getUniqueId().toString());
        boolean isWpOwner = wp.isOwner(player.getUniqueId());

        if (!isOrbOwner && !isWpOwner && !wp.canAccess(player.getUniqueId())) {
            player.sendMessage(plugin.msg("prefix") + "§cYou cannot use this Recall Orb.");
            return;
        }

        plugin.getTeleportHelper().teleport(player, wp);

        // Check for accepted invite - if there's an accepted invite waiting for this orb owner
        // The invitee has already accepted; now the orb user teleports them too
        // Actually on right-click air, it just teleports self. Group teleport handled via accept flow.
    }
}
