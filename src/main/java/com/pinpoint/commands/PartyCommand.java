package com.pinpoint.commands;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.LinkRequest;
import com.pinpoint.data.Party;
import com.pinpoint.data.TravelOffer;
import com.pinpoint.data.Waypoint;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PinpointPlugin plugin;

    public PartyCommand(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /party.");
            return true;
        }

        if (args.length == 0) {
            plugin.getPartyGuiManager().openPartyGui(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "leave"   -> processLeave(player);
            case "disband" -> processDisband(player);
            case "remove"  -> handleRemove(player, args);
            case "accept"  -> processAcceptLink(player);
            case "deny"    -> processDenyLink(player);
            case "follow"  -> handleFollow(player, args);
            case "stay"    -> handleStay(player, args);
            default        -> sendUsage(player);
        }
        return true;
    }

    // --- Leave ---

    public void processLeave(Player player) {
        plugin.getGuiManager().closeGui(player);
        Set<UUID> remaining = plugin.getPartyManager().leaveParty(player.getUniqueId());
        plugin.getPartyStorage().saveAll(plugin.getPartyManager().getAllParties());

        player.sendMessage(plugin.msg("prefix") + "§aYou left the party.");

        if (remaining.isEmpty()) {
            // Party dissolved — the one remaining member is already removed from state
            return;
        }
        for (UUID memberUuid : remaining) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null)
                member.sendMessage(plugin.msg("prefix") + "§e" + player.getName() + "§7 left the party.");
        }
    }

    // --- Disband ---

    public void processDisband(Player player) {
        plugin.getGuiManager().closeGui(player);
        Set<UUID> former = plugin.getPartyManager().disbandParty(player.getUniqueId());
        if (former.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cYou are not in a party.");
            return;
        }
        plugin.getPartyStorage().saveAll(plugin.getPartyManager().getAllParties());

        for (UUID memberUuid : former) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null)
                member.sendMessage(plugin.msg("prefix") + "§c" + player.getName() + "§7 disbanded the party.");
        }
    }

    // --- Remove member ---

    private void handleRemove(Player remover, String[] args) {
        if (args.length < 2) {
            remover.sendMessage(plugin.msg("prefix") + "§cUsage: /party remove <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            remover.sendMessage(plugin.msg("prefix") + "§cPlayer §e" + args[1] + "§c is not online.");
            return;
        }
        if (!plugin.getPartyManager().inSameParty(remover.getUniqueId(), target.getUniqueId())) {
            remover.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " is not in your party.");
            return;
        }
        if (target.equals(remover)) {
            remover.sendMessage(plugin.msg("prefix") + "§cUse §e/party leave§c to leave your own party.");
            return;
        }
        processRemoveMember(remover, target.getUniqueId(), target.getName());
    }

    public void processRemoveMember(Player remover, UUID targetUuid, String targetName) {
        plugin.getGuiManager().closeGui(remover);

        if (!plugin.getPartyManager().inSameParty(remover.getUniqueId(), targetUuid)) {
            remover.sendMessage(plugin.msg("prefix") + "§c" + targetName + " is not in your party.");
            return;
        }

        Set<UUID> remaining = plugin.getPartyManager().removeMember(targetUuid);
        plugin.getPartyStorage().saveAll(plugin.getPartyManager().getAllParties());

        remover.sendMessage(plugin.msg("prefix") + "§aRemoved §e" + targetName + "§a from the party.");

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null)
            target.sendMessage(plugin.msg("prefix") + "§eYou were removed from the party by §b" + remover.getName() + "§e.");

        if (remaining.isEmpty()) return; // party dissolved
        for (UUID memberUuid : remaining) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.equals(remover))
                member.sendMessage(plugin.msg("prefix") + "§e" + targetName + "§7 was removed from the party.");
        }
    }

    // --- Accept / Deny link request ---

    public void processAcceptLink(Player player) {
        LinkRequest request = plugin.getPartyManager().getLinkRequest(player.getUniqueId());
        if (request == null) {
            player.sendMessage(plugin.msg("prefix") + "§cYou have no pending link request.");
            return;
        }

        // Cancel expiry task
        if (request.taskId != -1) Bukkit.getScheduler().cancelTask(request.taskId);
        plugin.getPartyManager().removeLinkRequest(player.getUniqueId());

        boolean alreadyLinked = plugin.getPartyManager().inSameParty(request.senderUuid, player.getUniqueId());
        plugin.getPartyManager().linkPlayers(request.senderUuid, player.getUniqueId());
        plugin.getPartyStorage().saveAll(plugin.getPartyManager().getAllParties());

        plugin.getGuiManager().closeGui(player);

        Party party = plugin.getPartyManager().getPartyOf(player.getUniqueId());
        int size = party != null ? party.size() : 2;

        player.sendMessage(plugin.msg("prefix") + "§aLinked! Your party now has §e" + size + "§a member(s).");

        Player sender = Bukkit.getPlayer(request.senderUuid);
        if (sender != null) {
            if (alreadyLinked) {
                sender.sendMessage(plugin.msg("prefix") + "§e" + player.getName() + " is already linked with you.");
            } else {
                sender.sendMessage(plugin.msg("prefix") + "§b" + player.getName()
                        + "§a accepted your link request! Party size: §e" + size);
            }
        }
    }

    public void processDenyLink(Player player) {
        LinkRequest request = plugin.getPartyManager().getLinkRequest(player.getUniqueId());
        if (request == null) {
            player.sendMessage(plugin.msg("prefix") + "§cYou have no pending link request.");
            return;
        }
        if (request.taskId != -1) Bukkit.getScheduler().cancelTask(request.taskId);
        plugin.getPartyManager().removeLinkRequest(player.getUniqueId());
        plugin.getGuiManager().closeGui(player);

        player.sendMessage(plugin.msg("prefix") + "§cLink request denied.");

        Player sender = Bukkit.getPlayer(request.senderUuid);
        if (sender != null)
            sender.sendMessage(plugin.msg("prefix") + "§c" + player.getName() + " denied your link request.");
    }

    // --- Follow travel offer ---

    private void handleFollow(Player player, String[] args) {
        TravelOffer offer;
        if (args.length >= 2) {
            // /party follow <offerId>  (from clickable chat button)
            try {
                UUID offerId = UUID.fromString(args[1]);
                offer = plugin.getPartyManager().getTravelOffer(offerId);
            } catch (IllegalArgumentException e) {
                player.sendMessage(plugin.msg("prefix") + "§cInvalid offer ID.");
                return;
            }
        } else {
            // /party follow  (no-arg, Bedrock-friendly — uses last received offer)
            offer = plugin.getPartyManager().getLastTravelOffer(player.getUniqueId());
        }

        if (offer == null) {
            player.sendMessage(plugin.msg("prefix") + "§cNo active travel offer to follow.");
            return;
        }
        com.pinpoint.data.Party playerParty = plugin.getPartyManager().getPartyOf(player.getUniqueId());
        if (playerParty == null || !playerParty.hasMember(offer.travelerUuid)) {
            player.sendMessage(plugin.msg("prefix") + "§cThat travel offer is no longer valid.");
            return;
        }

        Optional<Waypoint> wpOpt = plugin.getWaypointManager().getWaypoint(offer.waypointUuid);
        if (wpOpt.isEmpty()) {
            player.sendMessage(plugin.msg("prefix") + "§cThat waypoint no longer exists.");
            return;
        }

        plugin.getTeleportHelper().partyFollow(player, wpOpt.get(), offer.travelerName);
    }

    // --- Stay (dismiss travel offer) ---

    private void handleStay(Player player, String[] args) {
        player.sendMessage(plugin.msg("prefix") + "§7You chose to stay. Safe travels to your party!");
    }

    // --- Tab completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("leave", "disband", "remove", "accept", "deny", "follow"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    // --- Helpers ---

    private void sendUsage(Player player) {
        player.sendMessage(plugin.msg("prefix") + "§eParty commands:");
        player.sendMessage("  §b/party             §7- Open party management");
        player.sendMessage("  §b/party leave        §7- Leave your party");
        player.sendMessage("  §b/party disband      §7- Disband the whole party");
        player.sendMessage("  §b/party remove §e<player>§7- Remove a member");
        player.sendMessage("  §b/party accept§7/§bdeny  §7- Respond to a link request");
        player.sendMessage("  §b/party follow       §7- Follow a party travel");
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}
