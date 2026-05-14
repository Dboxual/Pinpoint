package com.pinpoint.listeners;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.LinkRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PartyListener implements Listener {

    private final PinpointPlugin plugin;

    public PartyListener(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel any pending link request targeting this player and notify the sender
        LinkRequest request = plugin.getPartyManager().getLinkRequest(player.getUniqueId());
        if (request != null) {
            if (request.taskId != -1) Bukkit.getScheduler().cancelTask(request.taskId);
            plugin.getPartyManager().removeLinkRequest(player.getUniqueId());

            Player sender = Bukkit.getPlayer(request.senderUuid);
            if (sender != null)
                sender.sendMessage(plugin.msg("prefix") + "§c" + player.getName()
                        + " went offline — link request cancelled.");
        }
    }
}
