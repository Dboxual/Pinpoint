package com.pinpoint.gui;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.LinkRequest;
import com.pinpoint.data.Party;
import com.pinpoint.data.TravelOffer;
import com.pinpoint.data.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PartyGuiManager {

    private final PinpointPlugin plugin;

    public PartyGuiManager(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Link Request ---

    /**
     * Initiates a link request from sender to target. Validates conditions, creates
     * the request, and opens the accept/deny GUI for the target.
     */
    public void sendLinkRequest(Player sender, Player target) {
        if (target.equals(sender)) {
            sender.sendMessage(plugin.msg("prefix") + "§cYou can't link with yourself.");
            return;
        }
        if (plugin.getPartyManager().inSameParty(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage(plugin.msg("prefix") + "§eYou're already linked with §b" + target.getName() + "§e.");
            return;
        }
        if (plugin.getPartyManager().hasPendingLinkRequest(target.getUniqueId())) {
            sender.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " already has a pending link request.");
            return;
        }

        LinkRequest request = new LinkRequest(UUID.randomUUID(), sender.getUniqueId(), target.getUniqueId());
        plugin.getPartyManager().addLinkRequest(request);

        sender.sendMessage(plugin.msg("prefix") + "§aLink request sent to §b" + target.getName() + "§a.");
        openLinkRequestGui(target, request, sender.getName());

        int timeoutSeconds = plugin.getConfig().getInt("settings.link-request-timeout", 60);
        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getPartyManager().hasPendingLinkRequest(target.getUniqueId())
                    && plugin.getPartyManager().getLinkRequest(target.getUniqueId()).id.equals(request.id)) {
                plugin.getPartyManager().removeLinkRequest(target.getUniqueId());
                if (target.isOnline())
                    target.sendMessage(plugin.msg("prefix") + "§cLink request from §e" + sender.getName() + "§c expired.");
                if (sender.isOnline())
                    sender.sendMessage(plugin.msg("prefix") + "§cYour link request to §e" + target.getName() + "§c expired.");
            }
        }, timeoutSeconds * 20L).getTaskId();

        request.taskId = taskId;
    }

    public void openLinkRequestGui(Player target, LinkRequest request, String senderName) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Link Request").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        fillBorder(inv, 3);

        inv.setItem(13, makeItem(Material.ENDER_PEARL, senderName + " wants to link with you",
                List.of(
                        colorLine("Accept to join their party.", NamedTextColor.GRAY),
                        colorLine("Or type /party accept | /party deny", NamedTextColor.DARK_GRAY)
                )));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Accept",
                List.of(colorLine("Link up with " + senderName, NamedTextColor.GREEN))));
        handlers.put(11, () -> plugin.getPartyCommand().processAcceptLink(target));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Deny",
                List.of(colorLine("Decline the link request", NamedTextColor.RED))));
        handlers.put(15, () -> plugin.getPartyCommand().processDenyLink(target));

        plugin.getGuiManager().openGui(target, inv, handlers);
    }

    // --- Party Management GUI ---

    public void openPartyGui(Player player) {
        Party party = plugin.getPartyManager().getPartyOf(player.getUniqueId());

        if (party == null) {
            player.sendMessage(plugin.msg("prefix") + "§cYou are not in a party. Shift+right-click a player with your Waypoint Pearl to link up.");
            return;
        }

        Set<UUID> memberUuids = party.getMembers();
        int rows = Math.max(3, Math.min(6, (int) Math.ceil((memberUuids.size() + 9) / 9.0) + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text("Party Members").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, rows);

        // Member heads in row 1 (slots 10–16)
        int slot = 10;
        for (UUID memberUuid : memberUuids) {
            if (slot >= rows * 9 - 9) break;
            String name = Bukkit.getOfflinePlayer(memberUuid).getName();
            if (name == null) name = memberUuid.toString().substring(0, 8);
            boolean isSelf = memberUuid.equals(player.getUniqueId());
            boolean isOnline = Bukkit.getPlayer(memberUuid) != null;

            List<Component> lore = new ArrayList<>();
            lore.add(colorLine(isOnline ? "Online" : "Offline", isOnline ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            if (!isSelf) lore.add(colorLine("Click to remove from party", NamedTextColor.RED));

            ItemStack head = makePlayerHead(name, lore);
            inv.setItem(slot, head);

            if (!isSelf) {
                final UUID finalUuid = memberUuid;
                final String finalName = name;
                handlers.put(slot, () -> openConfirmRemoveGui(player, finalUuid, finalName));
            }
            slot++;
            if ((slot % 9) == 8) slot += 2;
        }

        // Bottom row controls
        int leaveSlot = rows * 9 - 7;
        inv.setItem(leaveSlot, makeItem(Material.RED_BED, "Leave Party",
                List.of(colorLine("You leave — others stay linked.", NamedTextColor.YELLOW),
                        colorLine("Party dissolves if only 1 member remains.", NamedTextColor.GRAY))));
        handlers.put(leaveSlot, () -> plugin.getPartyCommand().processLeave(player));

        int disbandSlot = rows * 9 - 3;
        inv.setItem(disbandSlot, makeItem(Material.TNT, "Disband Party",
                List.of(colorLine("Removes everyone from the party.", NamedTextColor.RED),
                        colorLine("This cannot be undone!", NamedTextColor.DARK_RED))));
        handlers.put(disbandSlot, () -> plugin.getPartyCommand().processDisband(player));

        int closeSlot = rows * 9 - 1;
        inv.setItem(closeSlot, makeItem(Material.BARRIER, "Close", List.of()));
        handlers.put(closeSlot, () -> plugin.getGuiManager().closeGui(player));

        plugin.getGuiManager().openGui(player, inv, handlers);
    }

    public void openConfirmRemoveGui(Player remover, UUID targetUuid, String targetName) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Remove " + targetName + "?").color(NamedTextColor.RED));
        Map<Integer, Runnable> handlers = new HashMap<>();
        fillBorder(inv, 3);

        inv.setItem(13, makeItem(Material.PLAYER_HEAD, targetName,
                List.of(colorLine("This will remove them from the party.", NamedTextColor.GRAY))));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Cancel",
                List.of(colorLine("Go back", NamedTextColor.GREEN))));
        handlers.put(11, () -> openPartyGui(remover));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Remove " + targetName,
                List.of(colorLine("Removes them from the party.", NamedTextColor.RED))));
        handlers.put(15, () -> plugin.getPartyCommand().processRemoveMember(remover, targetUuid, targetName));

        plugin.getGuiManager().openGui(remover, inv, handlers);
    }

    // --- Travel Notification ---

    /**
     * Called after a Pinpoint teleport completes (suppressFollowPrompt=false only).
     * Opens a Follow/Stay GUI for each online party member and sends a plain-text hint.
     * Offer expires after the configured timeout.
     */
    public void notifyPartyTravel(Player traveler, Waypoint wp) {
        Party party = plugin.getPartyManager().getPartyOf(traveler.getUniqueId());
        if (party == null) return;

        UUID offerId = UUID.randomUUID();
        TravelOffer offer = new TravelOffer(
                offerId, traveler.getUniqueId(), traveler.getName(), wp.getId(), wp.getName());
        plugin.getPartyManager().addTravelOffer(offer);

        int timeout = plugin.getConfig().getInt("settings.party-travel-offer-timeout", 30);

        for (UUID memberUuid : party.getMembers()) {
            if (memberUuid.equals(traveler.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberUuid);
            if (member == null || !member.isOnline()) continue;

            plugin.getPartyManager().setLastTravelOffer(memberUuid, offerId);

            // Open GUI (works on Java and Bedrock/Geyser)
            openTravelOfferGui(member, offer);

            // Plain-text hint — no clickable components needed since the GUI is primary
            member.sendMessage(Component.text("[Pinpoint] ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(traveler.getName(), NamedTextColor.AQUA))
                    .append(Component.text(" traveled to ", NamedTextColor.YELLOW))
                    .append(Component.text(wp.getName(), NamedTextColor.AQUA))
                    .append(Component.text("  §7(Shift+Pearl to follow)", NamedTextColor.GRAY)));
        }

        int taskId = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getPartyManager().removeTravelOffer(offerId), timeout * 20L).getTaskId();
        offer.taskId = taskId;
    }

    /** Opens the Follow / Stay GUI for a party member who received a travel notification. */
    public void openTravelOfferGui(Player member, TravelOffer offer) {
        Inventory inv = Bukkit.createInventory(null, 27,
                Component.text("Travel Offer").color(NamedTextColor.AQUA));
        Map<Integer, Runnable> handlers = new HashMap<>();

        fillBorder(inv, 3);

        inv.setItem(13, makeItem(Material.ENDER_PEARL,
                offer.travelerName + " → " + offer.waypointName,
                List.of(
                        colorLine("Your party just traveled to " + offer.waypointName, NamedTextColor.GRAY),
                        colorLine("Follow or stay behind.", NamedTextColor.GRAY)
                )));

        inv.setItem(11, makeItem(Material.LIME_WOOL, "Follow",
                List.of(colorLine("Teleport to " + offer.waypointName, NamedTextColor.GREEN))));
        handlers.put(11, () -> plugin.getPartyCommand().processFollowOffer(member, offer));

        inv.setItem(15, makeItem(Material.RED_WOOL, "Stay",
                List.of(colorLine("Stay where you are", NamedTextColor.RED))));
        handlers.put(15, () -> {
            plugin.getPartyManager().clearLastTravelOffer(member.getUniqueId());
            plugin.getGuiManager().closeGui(member);
            member.sendMessage(plugin.msg("prefix") + "§7You chose to stay. Safe travels!");
        });

        plugin.getGuiManager().openGui(member, inv, handlers);
    }

    // --- Item helpers ---

    private void fillBorder(Inventory inv, int rows) {
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        int size = rows * 9;
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = size - 9; i < size; i++) inv.setItem(i, glass);
        for (int row = 0; row < rows; row++) {
            inv.setItem(row * 9, glass);
            inv.setItem(row * 9 + 8, glass);
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

    private ItemStack makePlayerHead(String playerName, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        meta.displayName(Component.text(playerName).color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private Component colorLine(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }
}
