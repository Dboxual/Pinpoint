package com.pinpoint.gui;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GuiManager implements Listener {

    private final PinpointPlugin plugin;
    private final Map<UUID, Map<Integer, Runnable>> openGuis = new HashMap<>();
    private final Map<UUID, Inventory> playerInventories = new HashMap<>();

    public GuiManager(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Inventory event handling ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!openGuis.containsKey(uuid)) return;

        Inventory tracked = playerInventories.get(uuid);
        if (tracked == null || !event.getInventory().equals(tracked)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Runnable handler = openGuis.get(uuid).get(event.getRawSlot());
        if (handler != null) Bukkit.getScheduler().runTask(plugin, handler);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Inventory tracked = playerInventories.get(uuid);
        if (tracked != null && event.getInventory().equals(tracked)) {
            openGuis.remove(uuid);
            playerInventories.remove(uuid);
        }
    }

    public void openGui(Player player, Inventory inv, Map<Integer, Runnable> handlers) {
        openGuis.put(player.getUniqueId(), handlers);
        playerInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
        playerInventories.remove(player.getUniqueId());
        player.closeInventory();
    }

    // --- Disambiguation ---

    private Set<String> duplicateNames(Collection<Waypoint> pool) {
        Set<String> seen = new HashSet<>();
        Set<String> dupes = new HashSet<>();
        for (Waypoint wp : pool) {
            String key = wp.getName().toLowerCase();
            if (!seen.add(key)) dupes.add(key);
        }
        return dupes;
    }

    private String label(Waypoint wp, Set<String> dupes) {
        if (dupes.contains(wp.getName().toLowerCase())) {
            return wp.getName() + " (#" + WaypointManager.shortId(wp.getId()) + ")";
        }
        return wp.getName();
    }

    // --- GUI builders ---

    /**
     * Opens the waypoint hub.
     * fromBlock=true when triggered by right-clicking a placed waypoint block;
     * fromBlock=false when triggered by a Waypoint Pearl or command.
     * This flag is threaded through to the teleport button so the right delay is used.
     */
    public void openHubGui(Player player, UUID focusedWaypointId, boolean fromBlock) {
        List<Waypoint> accessible = plugin.getWaypointManager()
                .getAccessibleWaypoints(player.getUniqueId());
        Set<String> dupes = duplicateNames(accessible);

        int rows = Math.max(3, Math.min(6, (int) Math.ceil((accessible.size() + 9) / 9.0) + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Waypoint Hub").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        fillBorder(inv, rows);

        int slot = 10;
        for (Waypoint wp : accessible) {
            if (slot >= rows * 9 - 9) break;
            Material mat = wp.isPublic() ? Material.LIME_DYE : Material.ORANGE_DYE;
            if (wp.isOwner(player.getUniqueId())) mat = Material.BLUE_DYE;

            List<Component> lore = new ArrayList<>();
            lore.add(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY));
            lore.add(colorLine(wp.isPublic() ? "Public" : "Private",
                    wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                    ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                    : colorLine("Free", NamedTextColor.GREEN));
            lore.add(colorLine("Click to open", NamedTextColor.YELLOW));

            inv.setItem(slot, makeItem(mat, label(wp, dupes), lore));
            final Waypoint finalWp = wp;
            handlers.put(slot, () -> handleWaypointClick(player, finalWp, fromBlock));
            slot++;
            if ((slot % 9) == 8) slot += 2;
        }

        if (focusedWaypointId != null) {
            plugin.getWaypointManager().getWaypoint(focusedWaypointId).ifPresent(focused -> {
                String focusedLabel = label(focused, dupes);
                ItemStack thisWp = makeItem(Material.COMPASS,
                        "This Waypoint: " + focusedLabel,
                        List.of(colorLine("Click to manage", NamedTextColor.YELLOW)));
                int manageSlot = rows * 9 - 5;
                inv.setItem(manageSlot, thisWp);
                handlers.put(manageSlot, () -> openManageGui(player, focused, fromBlock));
            });
        }

        int closeSlot = rows * 9 - 1;
        inv.setItem(closeSlot, makeItem(Material.BARRIER, "Close", List.of()));
        handlers.put(closeSlot, () -> closeGui(player));

        openGui(player, inv, handlers);
    }

    public void openManageGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());
        boolean isOwner = wp.isOwner(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Manage: " + wpLabel).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        inv.setItem(10, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(10, () -> {
            if (fromBlock) plugin.getTeleportHelper().teleportFromBlock(player, wp);
            else plugin.getTeleportHelper().teleport(player, wp);
        });

        if (isOwner) {
            // --- Visibility toggle (Emerald = public, Redstone = private) ---
            Material pubMat = wp.isPublic() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
            String pubLabel = wp.isPublic() ? "Visibility: PUBLIC" : "Visibility: PRIVATE";
            List<Component> pubLore = new ArrayList<>();
            pubLore.add(wp.isPublic()
                    ? colorLine("Anyone can visit this waypoint.", NamedTextColor.GREEN)
                    : colorLine("Only you and invited players can visit.", NamedTextColor.RED));
            pubLore.add(colorLine("Click to toggle visibility", NamedTextColor.GRAY));
            inv.setItem(11, makeItem(pubMat, pubLabel, pubLore));
            handlers.put(11, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                wp.setPublic(!wp.isPublic());
                plugin.getWaypointManager().saveWaypoint(wp);
                plugin.getHologramManager().updateHologram(wp);
                openManageGui(player, wp, fromBlock);
            });

            inv.setItem(12, makeItem(Material.GOLD_NUGGET, "Set Fee",
                    List.of(colorLine("Current: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD),
                            colorLine("Chat: type new fee amount", NamedTextColor.GRAY))));
            handlers.put(12, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                closeGui(player);
                plugin.getWaypointManager().setPendingFeeInput(player.getUniqueId(), wp.getId());
                player.sendMessage(plugin.msg("prefix") + "§eType the new fee (or §c0§e for free, §ccancel§e to abort):");
                plugin.getChatInputListener().schedulePendingFeeTimeout(player, wp.getId());
            });

            if (!wp.isPublic()) {
                inv.setItem(13, makeItem(Material.PLAYER_HEAD, "Invite Players",
                        List.of(colorLine("Click to manage invites", NamedTextColor.YELLOW))));
                handlers.put(13, () -> {
                    if (!wp.isOwner(player.getUniqueId())) {
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                    }
                    openInviteGui(player, wp, fromBlock);
                });
            }

            inv.setItem(14, makeItem(Material.ENDER_PEARL, "Get Waypoint Pearl",
                    List.of(colorLine("Gives you a Waypoint Pearl", NamedTextColor.YELLOW),
                            colorLine("Use it to access all your waypoints", NamedTextColor.GRAY))));
            handlers.put(14, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                closeGui(player);
                plugin.getItemManager().giveWaypointPearl(player);
            });

            inv.setItem(15, makeItem(Material.NAME_TAG, "Rename Waypoint",
                    List.of(colorLine("Type a new name in chat", NamedTextColor.YELLOW))));
            handlers.put(15, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                closeGui(player);
                plugin.getWaypointManager().setPendingRenaming(player.getUniqueId(), wp.getId());
                player.sendMessage(plugin.msg("prefix") +
                        String.format(plugin.msgCfg("rename-prompt"), wp.getName()));
                plugin.getChatInputListener().schedulePendingRenameTimeout(player, wp.getId());
            });

            inv.setItem(16, makeItem(Material.TNT, "Delete Waypoint",
                    List.of(colorLine("Permanently removes this waypoint", NamedTextColor.RED),
                            colorLine("This cannot be undone!", NamedTextColor.DARK_RED))));
            handlers.put(16, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                openConfirmDeleteGui(player, wp, fromBlock);
            });
        }

        inv.setItem(22, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(22, () -> openHubGui(player, wp.getId(), fromBlock));

        openGui(player, inv, handlers);
    }

    public void openConfirmDeleteGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Delete: " + wpLabel + "?").color(NamedTextColor.RED));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        inv.setItem(13, makeItem(Material.PAPER, wpLabel,
                List.of(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY),
                        colorLine("This cannot be undone!", NamedTextColor.RED))));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Cancel",
                List.of(colorLine("Go back, keep waypoint", NamedTextColor.GREEN))));
        handlers.put(11, () -> openManageGui(player, wp, fromBlock));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Confirm Delete",
                List.of(colorLine("Permanently deletes this waypoint", NamedTextColor.RED))));
        handlers.put(15, () -> {
            Location blockLoc = wp.getLocation();
            plugin.getHologramManager().removeHologram(wp.getId());
            plugin.getWaypointManager().deleteWaypoint(wp.getId());
            if (blockLoc != null && blockLoc.getBlock().getType() == Material.LODESTONE) {
                blockLoc.getBlock().setType(Material.AIR);
            }
            closeGui(player);
            player.sendMessage(plugin.msg("prefix") +
                    String.format(plugin.msgCfg("waypoint-deleted"), wp.getName()));
        });

        openGui(player, inv, handlers);
    }

    public void openUseGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Use: " + wpLabel).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        inv.setItem(13, makeItem(Material.PAPER, wpLabel,
                List.of(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY),
                        colorLine(wp.isPublic() ? "Public" : "Private",
                                wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED))));

        inv.setItem(11, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(11, () -> {
            if (fromBlock) plugin.getTeleportHelper().teleportFromBlock(player, wp);
            else plugin.getTeleportHelper().teleport(player, wp);
        });

        inv.setItem(15, makeItem(Material.ARROW, "Back",
                List.of(colorLine("Back to hub", NamedTextColor.GRAY))));
        handlers.put(15, () -> openHubGui(player, null, fromBlock));

        openGui(player, inv, handlers);
    }

    public void openInviteGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());
        Set<UUID> invited = wp.getInvitedPlayers();
        int rows = Math.min(6, Math.max(3, (int) Math.ceil((invited.size() + 9) / 9.0) + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Invites: " + wpLabel).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, rows);

        int slot = 10;
        for (UUID invitedUuid : invited) {
            if (slot >= rows * 9 - 9) break;
            String name = Bukkit.getOfflinePlayer(invitedUuid).getName();
            if (name == null) name = invitedUuid.toString().substring(0, 8);
            inv.setItem(slot, makeItem(Material.PAPER, name,
                    List.of(colorLine("Click to remove invite", NamedTextColor.RED))));
            final UUID finalUuid = invitedUuid;
            handlers.put(slot, () -> {
                wp.removeInvite(finalUuid);
                plugin.getWaypointManager().saveWaypoint(wp);
                openInviteGui(player, wp, fromBlock);
            });
            slot++;
            if ((slot % 9) == 8) slot += 2;
        }

        int backSlot = rows * 9 - 5;
        inv.setItem(backSlot, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(backSlot, () -> openManageGui(player, wp, fromBlock));

        openGui(player, inv, handlers);
    }

    public void openAcceptDenyGui(Player player, String inviterName, String waypointLabel) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Teleport Invite").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        inv.setItem(13, makeItem(Material.PAPER,
                inviterName + " invites you to " + waypointLabel,
                List.of(colorLine("Accept or deny below", NamedTextColor.GRAY))));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Accept",
                List.of(colorLine("Teleport with " + inviterName, NamedTextColor.GREEN))));
        handlers.put(11, () -> plugin.getCommandHandler().processAccept(player));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Deny",
                List.of(colorLine("Decline the invite", NamedTextColor.RED))));
        handlers.put(15, () -> plugin.getCommandHandler().processDeny(player));

        openGui(player, inv, handlers);
    }

    public void openInviteSelectGui(Player inviter, Player target, List<Waypoint> options) {
        Set<String> dupes = duplicateNames(options);
        int rows = Math.max(3, Math.min(6, (int) Math.ceil((options.size() + 9) / 9.0) + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Invite " + target.getName() + " to:").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, rows);

        int slot = 10;
        for (Waypoint wp : options) {
            if (slot >= rows * 9 - 9) break;
            List<Component> lore = new ArrayList<>();
            lore.add(colorLine(wp.isPublic() ? "Public" : "Private",
                    wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(colorLine("Click to invite " + target.getName(), NamedTextColor.YELLOW));
            inv.setItem(slot, makeItem(Material.ENDER_PEARL, label(wp, dupes), lore));
            final Waypoint finalWp = wp;
            handlers.put(slot, () -> sendInvite(inviter, target, finalWp));
            slot++;
            if ((slot % 9) == 8) slot += 2;
        }

        int cancelSlot = rows * 9 - 1;
        inv.setItem(cancelSlot, makeItem(Material.BARRIER, "Cancel", List.of()));
        handlers.put(cancelSlot, () -> closeGui(inviter));

        openGui(inviter, inv, handlers);
    }

    private void sendInvite(Player inviter, Player target, Waypoint wp) {
        if (!target.isOnline()) {
            inviter.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " is no longer online.");
            closeGui(inviter);
            return;
        }
        if (plugin.getWaypointManager().hasPendingInvite(target.getUniqueId())) {
            inviter.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " already has a pending invite.");
            return;
        }

        plugin.getWaypointManager().createInvite(
                inviter.getUniqueId(), target.getUniqueId(), wp.getId(), java.util.UUID.randomUUID());
        closeGui(inviter);
        inviter.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("invite-sent"), target.getName()));

        openAcceptDenyGui(target, inviter.getName(), wp.getName());
        target.sendMessage(plugin.msg("prefix") +
                String.format(plugin.msgCfg("invite-received"), inviter.getName(), wp.getName()));

        int timeoutSeconds = plugin.getConfig().getInt("settings.invite-timeout", 60);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getWaypointManager().hasPendingInvite(target.getUniqueId())) {
                plugin.getWaypointManager().removeInvite(target.getUniqueId());
                if (target.isOnline())
                    target.sendMessage(plugin.msg("prefix") + plugin.msgCfg("invite-expired"));
                if (inviter.isOnline())
                    inviter.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + "'s invite expired.");
            }
        }, timeoutSeconds * 20L);
    }

    // --- Internal ---

    private void handleWaypointClick(Player player, Waypoint wp, boolean fromBlock) {
        if (wp.isOwner(player.getUniqueId())) {
            openManageGui(player, wp, fromBlock);
        } else {
            openUseGui(player, wp, fromBlock);
        }
    }

    private String labelForPlayer(Waypoint wp, UUID playerUuid) {
        List<Waypoint> accessible = plugin.getWaypointManager().getAccessibleWaypoints(playerUuid);
        return label(wp, duplicateNames(accessible));
    }

    // --- Item / lore helpers ---

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
        meta.displayName(Component.text(name).color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component colorLine(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
