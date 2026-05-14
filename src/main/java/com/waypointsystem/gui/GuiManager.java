package com.waypointsystem.gui;

import com.waypointsystem.WaypointPlugin;
import com.waypointsystem.data.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GuiManager implements Listener {

    private final WaypointPlugin plugin;
    // Maps open GUI inventories to their click handlers
    private final Map<UUID, Map<Integer, Runnable>> openGuis = new HashMap<>();
    // Track which inventory belongs to which player
    private final Map<UUID, Inventory> playerInventories = new HashMap<>();

    public GuiManager(WaypointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!openGuis.containsKey(uuid)) return;

        Inventory tracked = playerInventories.get(uuid);
        if (tracked == null || !event.getInventory().equals(tracked)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Map<Integer, Runnable> handlers = openGuis.get(uuid);
        Runnable handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            Bukkit.getScheduler().runTask(plugin, handler);
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Inventory tracked = playerInventories.get(uuid);
        if (tracked != null && event.getInventory().equals(tracked)) {
            openGuis.remove(uuid);
            playerInventories.remove(uuid);
        }
    }

    public void openGui(Player player, Inventory inv, Map<Integer, Runnable> handlers) {
        UUID uuid = player.getUniqueId();
        openGuis.put(uuid, handlers);
        playerInventories.put(uuid, inv);
        player.openInventory(inv);
    }

    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
        playerInventories.remove(player.getUniqueId());
        player.closeInventory();
    }

    // --- Internal click handlers ---

    private void handleWaypointClick(Player player, Waypoint wp) {
        // If owner, open manage GUI; otherwise open a use dialog
        if (wp.isOwner(player.getUniqueId())) {
            openManageGui(player, wp);
        } else {
            // Show a simple confirmation: teleport or cancel
            openUseGui(player, wp);
        }
    }

    public void openUseGui(Player player, Waypoint wp) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Use: " + wp.getName()).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        inv.setItem(13, makeItem(Material.PAPER, wp.getName(),
                List.of(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY),
                        colorLine(wp.isPublic() ? "Public" : "Private", wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED))));

        inv.setItem(11, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(11, () -> plugin.getTeleportHelper().teleport(player, wp));

        inv.setItem(15, makeItem(Material.ARROW, "Back",
                List.of(colorLine("Back to hub", NamedTextColor.GRAY))));
        handlers.put(15, () -> openHubGui(player, null));

        openGui(player, inv, handlers);
    }

    // --- GUI builders ---

    public void openHubGui(Player player, UUID focusedWaypointId) {
        List<Waypoint> accessible = plugin.getWaypointManager()
                .getAccessibleWaypoints(player.getUniqueId());

        int rows = Math.max(3, Math.min(6, (int) Math.ceil((accessible.size() + 9) / 9.0) + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Waypoint Hub").color(NamedTextColor.AQUA));

        Map<Integer, Runnable> handlers = new HashMap<>();

        // Fill border with glass
        fillBorder(inv, rows);

        // Waypoint items start at slot 10
        int slot = 10;
        for (Waypoint wp : accessible) {
            if (slot >= rows * 9 - 9) break;
            Material mat = wp.isPublic() ? Material.LIME_DYE : Material.ORANGE_DYE;
            if (wp.isOwner(player.getUniqueId())) mat = Material.BLUE_DYE;

            ItemStack wpItem = makeItem(mat, wp.getName(),
                    List.of(
                            colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY),
                            colorLine(wp.isPublic() ? "Public" : "Private", wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED),
                            wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                                    ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                                    : colorLine("Free", NamedTextColor.GREEN),
                            colorLine("Click to teleport", NamedTextColor.YELLOW)
                    ));
            inv.setItem(slot, wpItem);
            final Waypoint finalWp = wp;
            handlers.put(slot, () -> handleWaypointClick(player, finalWp));
            slot++;
            // Skip border columns
            if ((slot % 9) == 8) slot += 2;
        }

        // Bottom row: focused waypoint manage button (if applicable)
        if (focusedWaypointId != null) {
            plugin.getWaypointManager().getWaypoint(focusedWaypointId).ifPresent(focused -> {
                ItemStack thisWp = makeItem(Material.COMPASS,
                        "This Waypoint: " + focused.getName(),
                        List.of(colorLine("Click to manage", NamedTextColor.YELLOW)));
                int manageSlot = rows * 9 - 5;
                inv.setItem(manageSlot, thisWp);
                handlers.put(manageSlot, () -> openManageGui(player, focused));
            });
        }

        // Close button
        int closeSlot = rows * 9 - 1;
        inv.setItem(closeSlot, makeItem(Material.BARRIER, "Close", List.of()));
        handlers.put(closeSlot, () -> closeGui(player));

        openGui(player, inv, handlers);
    }

    public void openManageGui(Player player, Waypoint wp) {
        boolean isOwner = wp.isOwner(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Manage: " + wp.getName()).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        fillBorder(inv, 3);

        // Teleport
        inv.setItem(10, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(10, () -> plugin.getTeleportHelper().teleport(player, wp));

        if (isOwner) {
            // Toggle public/private
            Material pubMat = wp.isPublic() ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            String pubLabel = wp.isPublic() ? "Public (click to make Private)" : "Private (click to make Public)";
            inv.setItem(11, makeItem(pubMat, pubLabel, List.of()));
            handlers.put(11, () -> {
                wp.setPublic(!wp.isPublic());
                plugin.getWaypointManager().saveWaypoint(wp);
                openManageGui(player, wp);
            });

            // Set fee
            inv.setItem(12, makeItem(Material.GOLD_NUGGET, "Set Fee",
                    List.of(colorLine("Current: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD),
                            colorLine("Chat: type new fee amount", NamedTextColor.GRAY))));
            handlers.put(12, () -> {
                closeGui(player);
                plugin.getWaypointManager().setPendingFeeInput(player.getUniqueId(), wp.getId());
                player.sendMessage(plugin.msg("prefix") + "§eType the new fee amount in chat (or §c0§e for free, §ecancel§e to abort):");
                plugin.getChatInputListener().schedulePendingFeeTimeout(player, wp.getId());
            });

            // Invite players (only for private waypoints)
            if (!wp.isPublic()) {
                inv.setItem(13, makeItem(Material.PLAYER_HEAD, "Invite Players",
                        List.of(colorLine("Click to manage invites", NamedTextColor.YELLOW))));
                handlers.put(13, () -> openInviteGui(player, wp));
            }

            // Create Recall Orb
            inv.setItem(14, makeItem(Material.ENDER_PEARL, "Create Recall Orb",
                    List.of(colorLine("Creates a linked Recall Orb", NamedTextColor.YELLOW))));
            handlers.put(14, () -> {
                closeGui(player);
                plugin.getItemManager().giveRecallOrb(player, wp);
            });
        }

        // Back
        inv.setItem(22, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(22, () -> openHubGui(player, wp.getId()));

        openGui(player, inv, handlers);
    }

    public void openInviteGui(Player player, Waypoint wp) {
        Set<UUID> invited = wp.getInvitedPlayers();
        int rows = Math.max(3, (int) Math.ceil((invited.size() + 9) / 9.0) + 1);
        rows = Math.min(rows, 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Invites: " + wp.getName()).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, rows);

        int slot = 10;
        for (UUID invitedUuid : invited) {
            if (slot >= rows * 9 - 9) break;
            String name = Bukkit.getOfflinePlayer(invitedUuid).getName();
            if (name == null) name = invitedUuid.toString().substring(0, 8);
            ItemStack head = makeItem(Material.PAPER, name,
                    List.of(colorLine("Click to remove invite", NamedTextColor.RED)));
            inv.setItem(slot, head);
            final UUID finalUuid = invitedUuid;
            handlers.put(slot, () -> {
                wp.removeInvite(finalUuid);
                plugin.getWaypointManager().saveWaypoint(wp);
                openInviteGui(player, wp);
            });
            slot++;
            if ((slot % 9) == 8) slot += 2;
        }

        int backSlot = rows * 9 - 5;
        inv.setItem(backSlot, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(backSlot, () -> openManageGui(player, wp));

        openGui(player, inv, handlers);
    }

    public void openAcceptDenyGui(Player player, String inviterName, String waypointName) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Teleport Invite").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        inv.setItem(13, makeItem(Material.PAPER,
                inviterName + " invites you to " + waypointName,
                List.of(colorLine("Accept or deny below", NamedTextColor.GRAY))));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Accept",
                List.of(colorLine("Teleport with " + inviterName, NamedTextColor.GREEN))));
        handlers.put(11, () -> plugin.getCommandHandler().processAccept(player));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Deny",
                List.of(colorLine("Decline the invite", NamedTextColor.RED))));
        handlers.put(15, () -> plugin.getCommandHandler().processDeny(player));

        openGui(player, inv, handlers);
    }

    // --- Helpers ---

    private void fillBorder(Inventory inv, int rows) {
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        int size = rows * 9;
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = size - 9; i < size; i++) inv.setItem(i, glass);
        for (int i = 0; i < rows; i++) {
            inv.setItem(i * 9, glass);
            inv.setItem(i * 9 + 8, glass);
        }
    }

    private ItemStack makeItem(Material mat, String name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component colorLine(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
