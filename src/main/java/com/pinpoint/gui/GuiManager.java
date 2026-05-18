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

import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GuiManager implements Listener {

    private static final List<Material> ICON_PALETTE = List.of(
            Material.GRASS_BLOCK, Material.DIAMOND, Material.EMERALD, Material.NETHER_STAR,
            Material.ENDER_PEARL, Material.COMPASS, Material.CHEST, Material.OAK_DOOR,
            Material.BEACON, Material.LODESTONE
    );

    private final PinpointPlugin plugin;
    private final Map<UUID, Map<Integer, Runnable>> openGuis = new HashMap<>();
    private final Map<UUID, Inventory> playerInventories = new HashMap<>();
    private final Map<UUID, Integer>   hubPageMap        = new HashMap<>();

    private static final int HUB_PAGE_SIZE = 28; // 4 content rows × 7 columns per page

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
        hubPageMap.remove(player.getUniqueId());
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

    public void openHubGui(Player player, UUID focusedWaypointId, boolean fromBlock) {
        hubPageMap.putIfAbsent(player.getUniqueId(), 0);
        renderHubGui(player, focusedWaypointId, fromBlock, hubPageMap.get(player.getUniqueId()));
    }

    private void renderHubGui(Player player, UUID focusedWaypointId, boolean fromBlock, int page) {
        List<Waypoint> accessible = plugin.getWaypointManager()
                .getAccessibleWaypoints(player.getUniqueId());
        Set<String> dupes = duplicateNames(accessible);

        int totalPages = Math.max(1, (int) Math.ceil((double) accessible.size() / HUB_PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        hubPageMap.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Pinpoint Hub").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        // Top row and side borders for rows 0-4; bottom row filled separately
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9, glass);
            inv.setItem(row * 9 + 8, glass);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, glass);

        // Content slots: rows 1-4, columns 1-7 (28 per page)
        int[] contentSlots = new int[HUB_PAGE_SIZE];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                contentSlots[idx++] = row * 9 + col;
            }
        }

        int start = page * HUB_PAGE_SIZE;
        for (int i = 0; i < HUB_PAGE_SIZE; i++) {
            int wpIdx = start + i;
            if (wpIdx >= accessible.size()) break;
            Waypoint wp = accessible.get(wpIdx);
            int slot = contentSlots[i];

            List<Component> lore = new ArrayList<>();
            lore.add(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY));
            lore.add(colorLine(wp.isPublic() ? "Public" : "Private",
                    wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                    ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                    : colorLine("Free", NamedTextColor.GREEN));
            lore.add(colorLine("Click to open", NamedTextColor.YELLOW));

            inv.setItem(slot, makeItem(wp.getIconMaterial(), label(wp, dupes), lore));
            final Waypoint finalWp = wp;
            handlers.put(slot, () -> handleWaypointClick(player, finalWp, fromBlock));
        }

        // Bottom row navigation
        final int currentPage = page;
        if (page > 0) {
            inv.setItem(45, makeItem(Material.RED_STAINED_GLASS_PANE, "Previous Page",
                    List.of(colorLine("Go to page " + page, NamedTextColor.GRAY))));
            handlers.put(45, () -> renderHubGui(player, focusedWaypointId, fromBlock, currentPage - 1));
        }

        inv.setItem(47, makeItem(Material.PAPER, "Page " + (page + 1) + " / " + totalPages, List.of()));

        if (focusedWaypointId != null) {
            plugin.getWaypointManager().getWaypoint(focusedWaypointId).ifPresent(focused -> {
                String focusedLabel = label(focused, dupes);
                inv.setItem(49, makeItem(Material.COMPASS, "This Pinpoint: " + focusedLabel,
                        List.of(colorLine("Click to manage", NamedTextColor.YELLOW))));
                handlers.put(49, () -> openManageGui(player, focused, fromBlock));
            });
        }

        inv.setItem(51, makeItem(Material.BARRIER, "Close", List.of()));
        handlers.put(51, () -> closeGui(player));

        if (page < totalPages - 1) {
            inv.setItem(53, makeItem(Material.GREEN_STAINED_GLASS_PANE, "Next Page",
                    List.of(colorLine("Go to page " + (page + 2), NamedTextColor.GRAY))));
            handlers.put(53, () -> renderHubGui(player, focusedWaypointId, fromBlock, currentPage + 1));
        }

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
                    ? colorLine("Anyone can visit this Pinpoint.", NamedTextColor.GREEN)
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

            inv.setItem(14, makeItem(Material.COMPASS, "Get Pinpoint Compass",
                    List.of(colorLine("Gives you a Pinpoint Compass", NamedTextColor.YELLOW),
                            colorLine("Use it to access all your Pinpoints", NamedTextColor.GRAY))));
            handlers.put(14, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                closeGui(player);
                plugin.getItemManager().giveWaypointPearl(player);
            });

            inv.setItem(15, makeItem(Material.NAME_TAG, "Rename Pinpoint",
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

            inv.setItem(16, makeItem(Material.TNT, "Delete Pinpoint",
                    List.of(colorLine("Permanently removes this Pinpoint", NamedTextColor.RED),
                            colorLine("This cannot be undone!", NamedTextColor.DARK_RED))));
            handlers.put(16, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                openConfirmDeleteGui(player, wp, fromBlock);
            });

            // Change Icon — slot 20, overrides bottom border glass
            inv.setItem(20, makeItem(wp.getIconMaterial(), "Change Icon",
                    List.of(colorLine("Current: " + friendlyName(wp.getIconMaterial()), NamedTextColor.GRAY),
                            colorLine("Click to choose a different icon", NamedTextColor.YELLOW))));
            handlers.put(20, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                openIconSelectGui(player, wp, fromBlock);
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

        inv.setItem(13, makeItem(wp.getIconMaterial(), wpLabel,
                List.of(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY),
                        colorLine("This cannot be undone!", NamedTextColor.RED))));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Cancel",
                List.of(colorLine("Go back, keep Pinpoint", NamedTextColor.GREEN))));
        handlers.put(11, () -> openManageGui(player, wp, fromBlock));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Confirm Delete",
                List.of(colorLine("Permanently deletes this Pinpoint", NamedTextColor.RED))));
        handlers.put(15, () -> {
            Location blockLoc = wp.getLocation();
            plugin.getHologramManager().removeHologram(wp.getId());
            plugin.getWaypointManager().deleteWaypoint(wp.getId());
            if (blockLoc != null && blockLoc.getBlock().getType() == Material.LODESTONE) {
                blockLoc.getBlock().setType(Material.AIR);
            }
            // Return the Pinpoint item to the owner; drop at block location if inventory is full
            ItemStack returned = plugin.getItemManager().createWaypointBlockItem();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(returned);
            if (!leftover.isEmpty() && blockLoc != null) {
                blockLoc.getWorld().dropItemNaturally(blockLoc, leftover.get(0));
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

        inv.setItem(13, makeItem(wp.getIconMaterial(), wpLabel,
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

        // Online players excluding the waypoint owner
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.removeIf(p -> p.getUniqueId().equals(wp.getOwnerUuid()));

        int rows = Math.max(3, Math.min(6, (int) Math.ceil((onlinePlayers.size() + 9) / 9.0) + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Invite Players: " + wpLabel).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, rows);

        if (onlinePlayers.isEmpty()) {
            inv.setItem(13, makeItem(Material.BARRIER, "No other players online",
                    List.of(colorLine("No players available to invite.", NamedTextColor.GRAY))));
        }

        int slot = 10;
        for (Player online : onlinePlayers) {
            if (slot >= rows * 9 - 9) break;
            boolean isInvited = wp.isInvited(online.getUniqueId());
            List<Component> lore = new ArrayList<>();
            if (isInvited) {
                lore.add(colorLine("Already invited", NamedTextColor.GREEN));
                lore.add(colorLine("Click to remove access", NamedTextColor.RED));
            } else {
                lore.add(colorLine("Click to grant access", NamedTextColor.YELLOW));
            }
            inv.setItem(slot, makeSkull(online, online.getName(), lore));
            final Player finalOnline = online;
            final boolean finalIsInvited = isInvited;
            handlers.put(slot, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                if (finalIsInvited) {
                    wp.removeInvite(finalOnline.getUniqueId());
                    player.sendMessage(plugin.msg("prefix")
                            + "§cRemoved access for §b" + finalOnline.getName() + "§c.");
                } else {
                    wp.addInvite(finalOnline.getUniqueId());
                    player.sendMessage(plugin.msg("prefix")
                            + "§b" + finalOnline.getName() + " §acan now access §b" + wp.getName() + "§a.");
                    if (finalOnline.isOnline()) {
                        finalOnline.sendMessage(plugin.msg("prefix")
                                + "§b" + player.getName()
                                + " §ahas given you access to Pinpoint §b" + wp.getName() + "§a.");
                    }
                }
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
                Component.text("Pinpoint Access: " + target.getName()).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, rows);

        int slot = 10;
        for (Waypoint wp : options) {
            if (slot >= rows * 9 - 9) break;
            boolean hasAccess = wp.isInvited(target.getUniqueId());
            List<Component> lore = new ArrayList<>();
            lore.add(colorLine(wp.isPublic() ? "Public" : "Private",
                    wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (wp.isPublic()) {
                lore.add(colorLine("Everyone already has access", NamedTextColor.GRAY));
            } else if (hasAccess) {
                lore.add(colorLine(target.getName() + " has access", NamedTextColor.GREEN));
                lore.add(colorLine("Click to remove access", NamedTextColor.RED));
            } else {
                lore.add(colorLine("Click to invite " + target.getName(), NamedTextColor.YELLOW));
            }
            inv.setItem(slot, makeItem(wp.getIconMaterial(), label(wp, dupes), lore));
            final Waypoint finalWp = wp;
            final boolean finalHasAccess = hasAccess;
            handlers.put(slot, () -> {
                if (wp.isPublic()) {
                    inviter.sendMessage(plugin.msg("prefix") + "§7This Pinpoint is public — everyone already has access.");
                    return;
                }
                if (finalHasAccess) {
                    finalWp.removeInvite(target.getUniqueId());
                    plugin.getWaypointManager().saveWaypoint(finalWp);
                    inviter.sendMessage(plugin.msg("prefix")
                            + "§cRemoved access for §b" + target.getName() + "§c.");
                    if (target.isOnline()) {
                        target.sendMessage(plugin.msg("prefix")
                                + "§cYour access to §b" + finalWp.getName() + "§c has been removed.");
                    }
                    closeGui(inviter);
                } else {
                    sendInvite(inviter, target, finalWp);
                }
            });
            slot++;
            if ((slot % 9) == 8) slot += 2;
        }

        int cancelSlot = rows * 9 - 1;
        inv.setItem(cancelSlot, makeItem(Material.BARRIER, "Cancel", List.of()));
        handlers.put(cancelSlot, () -> closeGui(inviter));

        openGui(inviter, inv, handlers);
    }

    public void openIconSelectGui(Player player, Waypoint wp, boolean fromBlock) {
        Inventory inv = Bukkit.createInventory(null, 36,
                Component.text("Choose Icon").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 4);

        // 10 icons fill slots 10-16 (first row of content) then 19-21 (second row)
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
        Material current = wp.getIconMaterial();

        for (int i = 0; i < ICON_PALETTE.size() && i < slots.length; i++) {
            Material mat = ICON_PALETTE.get(i);
            int slot = slots[i];
            List<Component> lore = new ArrayList<>();
            if (mat == current) {
                lore.add(colorLine("Currently selected", NamedTextColor.GREEN));
            } else {
                lore.add(colorLine("Click to select", NamedTextColor.YELLOW));
            }
            inv.setItem(slot, makeItem(mat, friendlyName(mat), lore));
            final Material finalMat = mat;
            handlers.put(slot, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                wp.setIconMaterial(finalMat);
                plugin.getWaypointManager().saveWaypoint(wp);
                player.sendMessage(plugin.msg("prefix") + "§aIcon changed to §b" + friendlyName(finalMat) + "§a.");
                openManageGui(player, wp, fromBlock);
            });
        }

        // Back button overrides bottom border glass at slot 31 (center of bottom row)
        inv.setItem(31, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(31, () -> openManageGui(player, wp, fromBlock));

        openGui(player, inv, handlers);
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

        // Chat-only notification — no GUI is forced open so shift+Pearl works immediately on all clients
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

    private ItemStack makeSkull(Player owner, String name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.displayName(Component.text(name).color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
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

    private String friendlyName(Material mat) {
        StringBuilder sb = new StringBuilder();
        for (String word : mat.name().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
