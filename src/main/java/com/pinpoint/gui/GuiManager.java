package com.pinpoint.gui;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import com.pinpoint.data.WaypointManager;
import com.pinpoint.data.WaypointType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GuiManager implements Listener {

    public enum Category { OWNED, INVITED, PUBLIC, PLAYERS, LANDMARKS }

    private static final int PAGE_SIZE = 28; // 4 content rows × 7 columns

    private static final List<Material> ICON_PALETTE = List.of(
            Material.RED_BED,        // Home
            Material.SHIELD,         // Base
            Material.WHEAT,          // Farm
            Material.EMERALD,        // Shop
            Material.IRON_PICKAXE,   // Mine
            Material.BELL,           // Village
            Material.NETHERRACK,     // Nether
            Material.ENDER_EYE,      // End
            Material.COMPASS,        // Other
            Material.LODESTONE       // Default
    );

    private final PinpointPlugin plugin;
    private final Map<UUID, Map<Integer, Runnable>> openGuis     = new HashMap<>();
    private final Map<UUID, Inventory>              playerInventories = new HashMap<>();
    private final Map<UUID, Category>               playerCategory    = new HashMap<>();
    private final Map<UUID, EnumMap<Category, Integer>> categoryPages = new HashMap<>();
    // Tracks whether the current GUI session was initiated by a physical block right-click.
    private final Map<UUID, Boolean>                playerFromBlock   = new HashMap<>();

    public GuiManager(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Inventory event handling
    // -------------------------------------------------------------------------

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
            playerFromBlock.remove(uuid);
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

    // -------------------------------------------------------------------------
    // Disambiguation helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Hub entry point
    // -------------------------------------------------------------------------

    /**
     * Primary entry point called by WaypointInteractListener and commands.
     * Always opens the main waypoint browser — ownership does not change the initial GUI.
     * fromBlock=true means the player right-clicked a placed block; teleport delay will be shorter.
     */
    public void openHubGui(Player player, UUID focusedWaypointId, boolean fromBlock) {
        playerFromBlock.put(player.getUniqueId(), fromBlock);
        Category cat = playerCategory.getOrDefault(player.getUniqueId(), Category.OWNED);
        renderCategoryGui(player, cat, getCategoryPage(player.getUniqueId(), cat));
    }

    // -------------------------------------------------------------------------
    // Category page state helpers
    // -------------------------------------------------------------------------

    private int getCategoryPage(UUID uuid, Category cat) {
        EnumMap<Category, Integer> pages = categoryPages.get(uuid);
        return (pages != null) ? pages.getOrDefault(cat, 0) : 0;
    }

    private void setCategoryPage(UUID uuid, Category cat, int page) {
        categoryPages.computeIfAbsent(uuid, k -> new EnumMap<>(Category.class)).put(cat, page);
    }

    // -------------------------------------------------------------------------
    // Category GUI — main renderer
    // -------------------------------------------------------------------------

    private void renderCategoryGui(Player player, Category category, int page) {
        UUID uuid = player.getUniqueId();
        playerCategory.put(uuid, category);

        List<?> items = getCategoryItems(player, category);
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        setCategoryPage(uuid, category, page);

        String title = switch (category) {
            case OWNED      -> "My Pinpoints";
            case INVITED    -> "Invited Pinpoints";
            case PUBLIC     -> "Public Pinpoints";
            case PLAYERS    -> "Online Players";
            case LANDMARKS  -> "Landmarks";
        };

        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(title).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        // Content slots: rows 1-4, cols 1-7 = 28 slots per page
        int[] contentSlots = new int[PAGE_SIZE];
        int idx = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                contentSlots[idx++] = row * 9 + col;
            }
        }

        int start = page * PAGE_SIZE;

        if (category == Category.PLAYERS) {
            renderPlayersContent(inv, handlers, contentSlots, items, start, player);
        } else {
            @SuppressWarnings("unchecked")
            List<Waypoint> waypoints = (List<Waypoint>) items;
            Set<String> dupes = duplicateNames(waypoints);
            renderWaypointContent(player, category, inv, handlers, contentSlots, waypoints, dupes, start);
        }

        if (items.isEmpty()) {
            renderEmptyState(inv, category);
        }

        // Pending incoming teleport request banner — occupies row 0 (slots 0-8)
        UUID incomingRequesterId = plugin.getWaypointManager().getIncomingRequest(uuid);
        if (incomingRequesterId != null) {
            renderPendingRequestBanner(player, inv, handlers, incomingRequesterId);
        }

        renderBottomNav(player, inv, handlers, category, page, totalPages);

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Category content helpers
    // -------------------------------------------------------------------------

    private List<?> getCategoryItems(Player player, Category category) {
        UUID uuid = player.getUniqueId();
        return switch (category) {
            case OWNED      -> plugin.getWaypointManager().getOwnedWaypoints(uuid);
            case INVITED    -> plugin.getWaypointManager().getInvitedWaypoints(uuid);
            case PUBLIC     -> plugin.getWaypointManager().getPublicWaypoints(uuid);
            case LANDMARKS  -> plugin.getWaypointManager().getLandmarks();
            case PLAYERS    -> new ArrayList<>(Bukkit.getOnlinePlayers()).stream()
                                    .filter(p -> !p.getUniqueId().equals(uuid))
                                    .sorted(Comparator.comparing(Player::getName))
                                    .collect(Collectors.toList());
        };
    }

    private void renderWaypointContent(Player player, Category category, Inventory inv,
            Map<Integer, Runnable> handlers, int[] contentSlots,
            List<Waypoint> waypoints, Set<String> dupes, int start) {
        for (int i = 0; i < PAGE_SIZE; i++) {
            int wpIdx = start + i;
            if (wpIdx >= waypoints.size()) break;
            Waypoint wp = waypoints.get(wpIdx);
            int slot = contentSlots[i];

            NamedTextColor nameColor = switch (category) {
                case OWNED      -> NamedTextColor.GREEN;
                case LANDMARKS  -> NamedTextColor.AQUA;
                default         -> NamedTextColor.WHITE;
            };
            Material icon = (category == Category.LANDMARKS) ? getLandmarkIcon() : wp.getIconMaterial();
            List<Component> lore = buildWaypointLore(wp, category);
            inv.setItem(slot, makeItem(icon, label(wp, dupes), lore, nameColor));
            final Waypoint finalWp = wp;
            handlers.put(slot, () -> handleWaypointClick(player, finalWp));
        }
    }

    private List<Component> buildWaypointLore(Waypoint wp, Category category) {
        List<Component> lore = new ArrayList<>();
        switch (category) {
            case OWNED -> {
                lore.add(wp.isPublic()
                        ? colorLine("Public", NamedTextColor.GREEN)
                        : colorLine("Private", NamedTextColor.RED));
                lore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                        ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                        : colorLine("Free", NamedTextColor.GREEN));
                lore.add(colorLine("Click to teleport or manage", NamedTextColor.YELLOW));
            }
            case INVITED -> {
                lore.add(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY));
                lore.add(colorLine("Private — invited", NamedTextColor.AQUA));
                lore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                        ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                        : colorLine("Free", NamedTextColor.GREEN));
                lore.add(colorLine("Click to teleport", NamedTextColor.YELLOW));
            }
            case PUBLIC -> {
                lore.add(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY));
                lore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                        ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                        : colorLine("Free", NamedTextColor.GREEN));
                lore.add(colorLine("Click to teleport", NamedTextColor.YELLOW));
            }
            case LANDMARKS -> {
                lore.add(colorLine("Landmark", NamedTextColor.AQUA));
                lore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                        ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                        : colorLine("Free", NamedTextColor.GREEN));
                lore.add(colorLine("Click to teleport", NamedTextColor.YELLOW));
            }
            default -> {}
        }
        return lore;
    }

    // -------------------------------------------------------------------------
    // Pending teleport request banner — row 0 (slots 0-8) of category GUI
    // -------------------------------------------------------------------------

    private void renderPendingRequestBanner(Player target, Inventory inv,
            Map<Integer, Runnable> handlers, UUID requesterId) {
        long expiryMs = plugin.getWaypointManager().getPendingPlayerRequestExpiry(requesterId);
        long remainingSecs = Math.max(0, (expiryMs - System.currentTimeMillis()) / 1000);
        int bopsCost = plugin.getConfig().getInt("player-teleport.cost-bops", 100);

        // Slot 0: orange glass accent
        inv.setItem(0, makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));
        // Slot 8: orange glass accent
        inv.setItem(8, makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));

        // Slot 1: requester skull
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(colorLine("Wants to teleport to you!", NamedTextColor.GREEN));
        infoLore.add(colorLine("Expires in: " + remainingSecs + "s", NamedTextColor.YELLOW));
        inv.setItem(1, makeSkullByUuid(requesterId,
                Bukkit.getOfflinePlayer(requesterId).getName() != null
                        ? Bukkit.getOfflinePlayer(requesterId).getName()
                        : "Unknown",
                infoLore));

        // Slot 2: separator
        inv.setItem(2, makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));

        // Slot 3: Accept button
        List<Component> acceptLore = new ArrayList<>();
        acceptLore.add(colorLine("Click to accept the request", NamedTextColor.GREEN));
        if (bopsCost > 0) {
            acceptLore.add(colorLine("Cost: " + bopsCost + " Bops each on success", NamedTextColor.GOLD));
        }
        inv.setItem(3, makeItem(Material.LIME_WOOL, "Accept Request", acceptLore, NamedTextColor.GREEN));

        // Slot 4: separator
        inv.setItem(4, makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));

        // Slot 5: Deny button
        List<Component> denyLore = new ArrayList<>();
        denyLore.add(colorLine("Click to deny the request", NamedTextColor.RED));
        inv.setItem(5, makeItem(Material.RED_WOOL, "Deny Request", denyLore, NamedTextColor.RED));

        // Slots 6-7: separator
        inv.setItem(6, makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));
        inv.setItem(7, makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of()));

        final UUID capturedId = requesterId;
        handlers.put(3, () -> handlePendingRequestAccept(target, capturedId));
        handlers.put(5, () -> handlePendingRequestDeny(target, capturedId));
    }

    private void handlePendingRequestAccept(Player target, UUID requesterId) {
        if (!plugin.getWaypointManager().hasPendingPlayerRequest(requesterId)
                || !target.getUniqueId().equals(plugin.getWaypointManager().getPendingPlayerRequest(requesterId))) {
            target.sendMessage(plugin.msg("prefix") + "§cThat request has already expired.");
            Category cat = playerCategory.getOrDefault(target.getUniqueId(), Category.OWNED);
            renderCategoryGui(target, cat, getCategoryPage(target.getUniqueId(), cat));
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            int taskId = plugin.getWaypointManager().getPendingPlayerRequestTaskId(requesterId);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);
            target.sendMessage(plugin.msg("prefix") + "§cThat player is no longer online.");
            Category cat = playerCategory.getOrDefault(target.getUniqueId(), Category.OWNED);
            renderCategoryGui(target, cat, getCategoryPage(target.getUniqueId(), cat));
            return;
        }
        closeGui(target);
        plugin.getTeleportHelper().acceptPlayerTeleportRequest(target, requester);
    }

    private void handlePendingRequestDeny(Player target, UUID requesterId) {
        if (!plugin.getWaypointManager().hasPendingPlayerRequest(requesterId)
                || !target.getUniqueId().equals(plugin.getWaypointManager().getPendingPlayerRequest(requesterId))) {
            target.sendMessage(plugin.msg("prefix") + "§cThat request has already expired.");
            Category cat = playerCategory.getOrDefault(target.getUniqueId(), Category.OWNED);
            renderCategoryGui(target, cat, getCategoryPage(target.getUniqueId(), cat));
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null || !requester.isOnline()) {
            int taskId = plugin.getWaypointManager().getPendingPlayerRequestTaskId(requesterId);
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            plugin.getWaypointManager().clearPendingPlayerRequest(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestTaskId(requesterId);
            plugin.getWaypointManager().clearPendingPlayerRequestExpiry(requesterId);
            target.sendMessage(plugin.msg("prefix") + "§cThat player is no longer online.");
            Category cat = playerCategory.getOrDefault(target.getUniqueId(), Category.OWNED);
            renderCategoryGui(target, cat, getCategoryPage(target.getUniqueId(), cat));
            return;
        }
        closeGui(target);
        plugin.getTeleportHelper().denyPlayerTeleportRequest(target, requester);
    }

    // -------------------------------------------------------------------------
    // Players content
    // -------------------------------------------------------------------------

    private void renderPlayersContent(Inventory inv, Map<Integer, Runnable> handlers,
            int[] contentSlots, List<?> rawItems, int start, Player viewer) {
        @SuppressWarnings("unchecked")
        List<Player> players = (List<Player>) rawItems;
        int bopsCost = plugin.getConfig().getInt("player-teleport.cost-bops", 100);
        for (int i = 0; i < PAGE_SIZE; i++) {
            int pIdx = start + i;
            if (pIdx >= players.size()) break;
            Player target = players.get(pIdx);
            int slot = contentSlots[i];
            List<Component> lore = new ArrayList<>();
            lore.add(colorLine("Click to send teleport request", NamedTextColor.YELLOW));
            if (bopsCost > 0) {
                lore.add(colorLine("Cost: " + bopsCost + " Bops each", NamedTextColor.GOLD));
            }
            inv.setItem(slot, makeSkull(target, target.getName(), lore));
            final Player finalTarget = target;
            handlers.put(slot, () -> {
                closeGui(viewer);
                plugin.getTeleportHelper().sendPlayerTeleportRequest(viewer, finalTarget);
            });
        }
    }

    private void renderEmptyState(Inventory inv, Category category) {
        String title = switch (category) {
            case OWNED      -> "No Pinpoints yet";
            case INVITED    -> "No invitations";
            case PUBLIC     -> "No public Pinpoints";
            case PLAYERS    -> "No other players online";
            case LANDMARKS  -> "No Landmarks";
        };
        String sub = switch (category) {
            case OWNED      -> "Place a Pinpoint Block to create one";
            case INVITED    -> "Ask a player to invite you to their Pinpoint";
            case PUBLIC     -> "No public Pinpoints have been created yet";
            case PLAYERS    -> "Check back when other players are online";
            case LANDMARKS  -> "No landmarks have been created yet";
        };
        inv.setItem(22, makeItem(Material.PAPER, title,
                List.of(colorLine(sub, NamedTextColor.DARK_GRAY))));
    }

    // -------------------------------------------------------------------------
    // Bottom navigation bar
    //
    // Slot layout (row 5, slots 45-53):
    //   45: Prev page  |  46: My  |  47: Invited  |  48: Public  |  49: Players
    //   50: separator  |  51: Page indicator  |  52: Close  |  53: Next page
    // -------------------------------------------------------------------------

    private void renderBottomNav(Player player, Inventory inv, Map<Integer, Runnable> handlers,
            Category currentCat, int page, int totalPages) {
        // Slot 45: Prev page
        if (page > 0) {
            final int pg = page;
            inv.setItem(45, makeItem(Material.ARROW, "Previous Page",
                    List.of(colorLine("Go to page " + pg, NamedTextColor.GRAY))));
            handlers.put(45, () -> renderCategoryGui(player, currentCat, pg - 1));
        }

        // Slots 46-50: Category tabs
        renderTabButton(inv, handlers, player, 46, Category.OWNED,     currentCat,
                Material.LODESTONE,    "My Pinpoints");
        renderTabButton(inv, handlers, player, 47, Category.INVITED,   currentCat,
                Material.WRITTEN_BOOK, "Invited");
        renderTabButton(inv, handlers, player, 48, Category.PUBLIC,    currentCat,
                Material.COMPASS,      "Public");
        renderTabButton(inv, handlers, player, 49, Category.PLAYERS,   currentCat,
                Material.PLAYER_HEAD,  "Players");
        renderTabButton(inv, handlers, player, 50, Category.LANDMARKS, currentCat,
                getLandmarkIcon(),     "Landmarks");

        // Slot 51: Page indicator — only shown when there is more than one page
        if (totalPages > 1) {
            inv.setItem(51, makeItem(Material.PAPER,
                    "Page " + (page + 1) + " / " + totalPages, List.of()));
        }

        // Slot 52: Close
        inv.setItem(52, makeItem(Material.BARRIER, "Close", List.of()));
        handlers.put(52, () -> closeGui(player));

        // Slot 53: Next page
        if (page < totalPages - 1) {
            final int pg = page;
            inv.setItem(53, makeItem(Material.ARROW, "Next Page",
                    List.of(colorLine("Go to page " + (pg + 2), NamedTextColor.GRAY))));
            handlers.put(53, () -> renderCategoryGui(player, currentCat, pg + 1));
        }
    }

    private void renderTabButton(Inventory inv, Map<Integer, Runnable> handlers,
            Player player, int slot, Category tab, Category currentTab,
            Material mat, String name) {
        boolean active = (tab == currentTab);
        NamedTextColor nameColor = active ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        List<Component> lore = active
                ? List.of(colorLine("▶ Currently viewing", NamedTextColor.GREEN))
                : List.of(colorLine("Click to switch", NamedTextColor.DARK_GRAY));
        inv.setItem(slot, makeItem(mat, name, lore, nameColor));
        if (!active) {
            // Tab switches always reset to page 0
            handlers.put(slot, () -> renderCategoryGui(player, tab, 0));
        }
    }

    // -------------------------------------------------------------------------
    // Waypoint click dispatch from category list
    // -------------------------------------------------------------------------

    private void handleWaypointClick(Player player, Waypoint wp) {
        boolean fromBlock = playerFromBlock.getOrDefault(player.getUniqueId(), false);
        if (wp.getType() == WaypointType.LANDMARK) {
            if (player.hasPermission("pinpoint.admin.landmark"))
                openLandmarkManageGui(player, wp, fromBlock);
            else
                openLandmarkUseGui(player, wp, fromBlock);
        } else if (wp.isOwner(player.getUniqueId())) {
            openOwnerViewGui(player, wp, fromBlock);
        } else {
            openUseGui(player, wp, fromBlock);
        }
    }

    // -------------------------------------------------------------------------
    // Owner View GUI — teleport primary, settings secondary
    // -------------------------------------------------------------------------

    private void openOwnerViewGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(wpLabel).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(wp.isPublic()
                ? colorLine("Public", NamedTextColor.GREEN)
                : colorLine("Private", NamedTextColor.RED));
        infoLore.add(wp.getFee() > 0 && plugin.getEconomyManager().isEnabled()
                ? colorLine("Fee: " + plugin.getEconomyManager().format(wp.getFee()), NamedTextColor.GOLD)
                : colorLine("Free", NamedTextColor.GREEN));
        infoLore.add(colorLine("Your Pinpoint", NamedTextColor.GREEN));
        inv.setItem(13, makeItem(wp.getIconMaterial(), wpLabel, infoLore, NamedTextColor.GREEN));

        inv.setItem(11, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(11, () -> {
            if (fromBlock) plugin.getTeleportHelper().teleportFromBlock(player, wp);
            else           plugin.getTeleportHelper().teleport(player, wp);
        });

        inv.setItem(15, makeItem(Material.WRITABLE_BOOK, "Settings",
                List.of(colorLine("Manage visibility, fee, invites...", NamedTextColor.GRAY),
                        colorLine("Click to open settings", NamedTextColor.YELLOW))));
        handlers.put(15, () -> openManageGui(player, wp, fromBlock));

        inv.setItem(22, makeItem(Material.ARROW, "Back",
                List.of(colorLine("Back to list", NamedTextColor.GRAY))));
        handlers.put(22, () -> {
            Category cat = playerCategory.getOrDefault(player.getUniqueId(), Category.OWNED);
            renderCategoryGui(player, cat, getCategoryPage(player.getUniqueId(), cat));
        });

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Manage GUI
    // -------------------------------------------------------------------------

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
            else           plugin.getTeleportHelper().teleport(player, wp);
        });

        if (isOwner) {
            // Visibility toggle
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
                plugin.getItemManager().giveWaypointCompass(player);
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

            inv.setItem(20, makeItem(wp.getIconMaterial(), "Change Icon",
                    List.of(colorLine("Current: " + categoryName(wp.getIconMaterial()), NamedTextColor.GRAY),
                            colorLine("Click to choose a different icon", NamedTextColor.YELLOW))));
            handlers.put(20, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                openIconSelectGui(player, wp, fromBlock);
            });

            List<Component> facingLore = new ArrayList<>();
            if (wp.hasTeleportDirection()) {
                facingLore.add(colorLine("Direction: " + capitalize(wp.getTeleportDirection()), NamedTextColor.AQUA));
            } else {
                facingLore.add(colorLine("Not set", NamedTextColor.GRAY));
            }
            facingLore.add(colorLine("Click to choose a direction", NamedTextColor.YELLOW));
            String facingTitle = wp.hasTeleportDirection()
                    ? "Facing: " + capitalize(wp.getTeleportDirection())
                    : "Facing Direction";
            inv.setItem(21, makeItem(Material.RECOVERY_COMPASS, facingTitle, facingLore));
            handlers.put(21, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                openDirectionPickerGui(player, wp, fromBlock);
            });
        }

        // Back: return to the last category the player was viewing
        inv.setItem(22, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(22, () -> {
            Category cat = playerCategory.getOrDefault(player.getUniqueId(), Category.OWNED);
            renderCategoryGui(player, cat, getCategoryPage(player.getUniqueId(), cat));
        });

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Confirm Delete GUI
    // -------------------------------------------------------------------------

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
            // Return the Pinpoint item; drop at block if inventory full
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

    // -------------------------------------------------------------------------
    // Use GUI (non-owners)
    // -------------------------------------------------------------------------

    public void openUseGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Use: " + wpLabel).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        inv.setItem(13, makeItem(wp.getIconMaterial(), wpLabel,
                List.of(colorLine("Owner: " + wp.getOwnerName(), NamedTextColor.GRAY),
                        colorLine(wp.isPublic() ? "Public" : "Private — invited",
                                  wp.isPublic() ? NamedTextColor.GREEN : NamedTextColor.AQUA))));

        inv.setItem(11, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(11, () -> {
            if (fromBlock) plugin.getTeleportHelper().teleportFromBlock(player, wp);
            else           plugin.getTeleportHelper().teleport(player, wp);
        });

        // Back: return to the last category the player was viewing
        inv.setItem(15, makeItem(Material.ARROW, "Back",
                List.of(colorLine("Back to list", NamedTextColor.GRAY))));
        handlers.put(15, () -> {
            Category cat = playerCategory.getOrDefault(player.getUniqueId(), Category.OWNED);
            renderCategoryGui(player, cat, getCategoryPage(player.getUniqueId(), cat));
        });

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Landmark Use GUI
    // -------------------------------------------------------------------------

    public void openLandmarkUseGui(Player player, Waypoint landmark, boolean fromBlock) {
        boolean teleportEnabled = plugin.getConfig().getBoolean("landmarks.teleport.enabled", true);
        double cost = plugin.getConfig().getDouble("landmarks.teleport.cost", 0);

        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text(landmark.getName()).color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(colorLine("Landmark", NamedTextColor.AQUA));
        if (cost > 0 && plugin.getEconomyManager().isEnabled()) {
            infoLore.add(colorLine("Fee: " + plugin.getEconomyManager().format(cost), NamedTextColor.GOLD));
        } else {
            infoLore.add(colorLine("Free", NamedTextColor.GREEN));
        }
        inv.setItem(13, makeItem(getLandmarkIcon(), landmark.getName(), infoLore, NamedTextColor.AQUA));

        if (teleportEnabled) {
            inv.setItem(11, makeItem(Material.ENDER_PEARL, "Teleport Here",
                    List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
            handlers.put(11, () -> {
                closeGui(player);
                if (fromBlock) plugin.getTeleportHelper().teleportFromBlock(player, landmark);
                else           plugin.getTeleportHelper().teleport(player, landmark);
            });
        }

        inv.setItem(15, makeItem(Material.ARROW, "Back",
                List.of(colorLine("Back to list", NamedTextColor.GRAY))));
        handlers.put(15, () -> {
            Category cat = playerCategory.getOrDefault(player.getUniqueId(), Category.LANDMARKS);
            renderCategoryGui(player, cat, getCategoryPage(player.getUniqueId(), cat));
        });

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Landmark Manage GUI (admin only)
    // -------------------------------------------------------------------------

    public void openLandmarkManageGui(Player player, Waypoint landmark, boolean fromBlock) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Manage: " + landmark.getName()).color(NamedTextColor.GOLD));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(colorLine("Landmark", NamedTextColor.AQUA));
        double cost = plugin.getConfig().getDouble("landmarks.teleport.cost", 0);
        if (cost > 0 && plugin.getEconomyManager().isEnabled()) {
            infoLore.add(colorLine("Fee: " + plugin.getEconomyManager().format(cost), NamedTextColor.GOLD));
        } else {
            infoLore.add(colorLine("Free", NamedTextColor.GREEN));
        }
        inv.setItem(13, makeItem(getLandmarkIcon(), landmark.getName(), infoLore, NamedTextColor.AQUA));

        inv.setItem(11, makeItem(Material.ENDER_PEARL, "Teleport Here",
                List.of(colorLine("Click to teleport", NamedTextColor.YELLOW))));
        handlers.put(11, () -> {
            closeGui(player);
            if (fromBlock) plugin.getTeleportHelper().teleportFromBlock(player, landmark);
            else           plugin.getTeleportHelper().teleport(player, landmark);
        });

        inv.setItem(15, makeItem(Material.TNT, "Delete Landmark",
                List.of(colorLine("Permanently removes this Landmark", NamedTextColor.RED),
                        colorLine("This cannot be undone!", NamedTextColor.DARK_RED))));
        handlers.put(15, () -> {
            if (!player.hasPermission("pinpoint.admin.landmark.delete")) {
                player.sendMessage(plugin.msg("prefix") + "§cYou do not have permission to delete Landmarks.");
                return;
            }
            openConfirmLandmarkDeleteGui(player, landmark, fromBlock);
        });

        inv.setItem(22, makeItem(Material.ARROW, "Back",
                List.of(colorLine("Back to Landmarks", NamedTextColor.GRAY))));
        handlers.put(22, () -> {
            Category cat = playerCategory.getOrDefault(player.getUniqueId(), Category.LANDMARKS);
            renderCategoryGui(player, cat, getCategoryPage(player.getUniqueId(), cat));
        });

        openGui(player, inv, handlers);
    }

    private void openConfirmLandmarkDeleteGui(Player player, Waypoint landmark, boolean fromBlock) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Delete: " + landmark.getName() + "?").color(NamedTextColor.RED));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        inv.setItem(13, makeItem(getLandmarkIcon(), landmark.getName(),
                List.of(colorLine("This cannot be undone!", NamedTextColor.RED)), NamedTextColor.AQUA));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Cancel",
                List.of(colorLine("Go back, keep Landmark", NamedTextColor.GREEN))));
        handlers.put(11, () -> openLandmarkManageGui(player, landmark, fromBlock));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Confirm Delete",
                List.of(colorLine("Permanently deletes this Landmark", NamedTextColor.RED))));
        handlers.put(15, () -> {
            Location blockLoc = landmark.getLocation();
            plugin.getHologramManager().removeHologram(landmark.getId());
            plugin.getWaypointManager().deleteWaypoint(landmark.getId());
            if (blockLoc != null) {
                org.bukkit.block.Block block = blockLoc.getBlock();
                String matName = plugin.getConfig().getString("landmarks.block.material", "LODESTONE");
                Material landmarkMat = Material.matchMaterial(matName != null ? matName : "LODESTONE");
                if (landmarkMat == null) landmarkMat = Material.LODESTONE;
                if (block.getType() == landmarkMat) block.setType(Material.AIR);
            }
            closeGui(player);
            player.sendMessage(plugin.msg("prefix") + "§aLandmark '§b" + landmark.getName() + "§a' deleted.");
        });

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Invite GUI
    // -------------------------------------------------------------------------

    public void openInviteGui(Player player, Waypoint wp, boolean fromBlock) {
        String wpLabel = labelForPlayer(wp, player.getUniqueId());

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


    // -------------------------------------------------------------------------
    // Icon Select GUI
    // -------------------------------------------------------------------------

    public void openIconSelectGui(Player player, Waypoint wp, boolean fromBlock) {
        List<Material> palette = buildPalette(player);
        Material current = wp.getIconMaterial();

        int contentRows = Math.max(1, (int) Math.ceil(palette.size() / 7.0));
        int totalRows   = Math.min(contentRows + 2, 6);
        int invSize     = totalRows * 9;

        Inventory inv = Bukkit.createInventory(null, invSize,
                Component.text("Choose Icon").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, totalRows);

        int palIdx = 0;
        outer:
        for (int row = 1; row <= totalRows - 2; row++) {
            for (int col = 1; col <= 7; col++) {
                if (palIdx >= palette.size()) break outer;
                int slot = row * 9 + col;
                Material mat = palette.get(palIdx);
                List<Component> lore = List.of(mat == current
                        ? colorLine("Currently selected", NamedTextColor.GREEN)
                        : colorLine("Click to select", NamedTextColor.YELLOW));
                inv.setItem(slot, makeItem(mat, categoryName(mat), lore));
                final Material finalMat = mat;
                handlers.put(slot, () -> {
                    if (!wp.isOwner(player.getUniqueId())) {
                        player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                    }
                    wp.setIconMaterial(finalMat);
                    plugin.getWaypointManager().saveWaypoint(wp);
                    player.sendMessage(plugin.msg("prefix") + "§aIcon changed to §b" + categoryName(finalMat) + "§a.");
                    openManageGui(player, wp, fromBlock);
                });
                palIdx++;
            }
        }

        int backSlot = (totalRows - 1) * 9 + 4;
        inv.setItem(backSlot, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(backSlot, () -> openManageGui(player, wp, fromBlock));

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Direction Picker GUI
    // -------------------------------------------------------------------------

    public void openDirectionPickerGui(Player player, Waypoint wp, boolean fromBlock) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Facing Direction").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        String current = wp.hasTeleportDirection() ? wp.getTeleportDirection() : null;

        String[]   dirNames = {"NORTH", "SOUTH", "EAST", "WEST"};
        Material[] dirMats  = {Material.BLUE_CONCRETE, Material.RED_CONCRETE,
                               Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE};
        int[]      dirSlots = {10, 12, 14, 16};

        for (int i = 0; i < dirNames.length; i++) {
            final String dirName = dirNames[i];
            final int    dirSlot = dirSlots[i];
            boolean isCurrent = dirName.equals(current);
            List<Component> lore = new ArrayList<>();
            lore.add(isCurrent
                    ? colorLine("Currently set", NamedTextColor.GREEN)
                    : colorLine("Click to set", NamedTextColor.YELLOW));
            inv.setItem(dirSlot, makeItem(dirMats[i], capitalize(dirName), lore));
            handlers.put(dirSlot, () -> {
                if (!wp.isOwner(player.getUniqueId())) {
                    player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
                }
                wp.setTeleportDirection(dirName);
                plugin.getWaypointManager().saveWaypoint(wp);
                player.sendActionBar(Component.text("Facing direction set to " + capitalize(dirName) + ".")
                        .color(NamedTextColor.GREEN));
                openManageGui(player, wp, fromBlock);
            });
        }

        inv.setItem(21, makeItem(Material.BARRIER, "Clear Direction",
                List.of(colorLine("Remove saved facing direction", NamedTextColor.GRAY))));
        handlers.put(21, () -> {
            if (!wp.isOwner(player.getUniqueId())) {
                player.sendMessage(plugin.msg("prefix") + plugin.msgCfg("not-owner")); return;
            }
            wp.clearTeleportDirection();
            plugin.getWaypointManager().saveWaypoint(wp);
            player.sendActionBar(Component.text("Facing direction cleared.").color(NamedTextColor.GRAY));
            openManageGui(player, wp, fromBlock);
        });

        inv.setItem(23, makeItem(Material.ARROW, "Back", List.of()));
        handlers.put(23, () -> openManageGui(player, wp, fromBlock));

        openGui(player, inv, handlers);
    }

    // -------------------------------------------------------------------------
    // Icon palette builder
    // -------------------------------------------------------------------------

    private List<Material> buildPalette(Player player) {
        List<Material> palette = new ArrayList<>(ICON_PALETTE);
        ConfigurationSection permSec = plugin.getConfig().getConfigurationSection("icon-permissions");
        if (permSec == null) return palette;
        for (String perm : permSec.getKeys(true)) {
            List<String> matNames = permSec.getStringList(perm);
            if (matNames.isEmpty()) continue;
            if (!player.hasPermission(perm)) continue;
            for (String name : matNames) {
                Material mat = Material.matchMaterial(name);
                if (mat != null && !palette.contains(mat)) palette.add(mat);
            }
        }
        return palette;
    }


    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Material getLandmarkIcon() {
        String iconStr = plugin.getConfig().getString("landmarks.gui.icon", "BEACON");
        Material mat = Material.matchMaterial(iconStr != null ? iconStr : "BEACON");
        return mat != null ? mat : Material.BEACON;
    }

    private String labelForPlayer(Waypoint wp, UUID playerUuid) {
        List<Waypoint> accessible = plugin.getWaypointManager().getAccessibleWaypoints(playerUuid);
        return label(wp, duplicateNames(accessible));
    }

    private void fillBorder(Inventory inv, int rows) {}

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

    private ItemStack makeSkullByUuid(UUID uuid, String name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(Component.text(name).color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name, List<Component> lore) {
        return makeItem(mat, name, lore, NamedTextColor.WHITE);
    }

    private ItemStack makeItem(Material mat, String name, List<Component> lore, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(color)
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

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String categoryName(Material mat) {
        return switch (mat) {
            case RED_BED      -> "Home";
            case SHIELD       -> "Base";
            case WHEAT        -> "Farm";
            case EMERALD      -> "Shop";
            case IRON_PICKAXE -> "Mine";
            case BELL         -> "Village";
            case NETHERRACK   -> "Nether";
            case ENDER_EYE    -> "End";
            case COMPASS      -> "Other";
            case LODESTONE    -> "Default";
            default           -> friendlyName(mat);
        };
    }
}
