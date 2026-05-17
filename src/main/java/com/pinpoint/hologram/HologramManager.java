package com.pinpoint.hologram;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.*;

public class HologramManager implements Listener {

    private final PinpointPlugin plugin;
    // waypoint UUID -> live ArmorStand references (one per display line)
    private final Map<UUID, List<ArmorStand>> waypointStands = new HashMap<>();
    private int visibilityTaskId = -1;

    public HologramManager(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("holograms.enabled", true);
    }

    // ── Visibility task ───────────────────────────────────────────────────────

    public void startVisibilityTask() {
        if (visibilityTaskId != -1) {
            Bukkit.getScheduler().cancelTask(visibilityTaskId);
        }
        visibilityTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateVisibilityForPlayer(player);
            }
        }, 20L, 20L);
    }

    public void stopVisibilityTask() {
        if (visibilityTaskId != -1) {
            Bukkit.getScheduler().cancelTask(visibilityTaskId);
            visibilityTaskId = -1;
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!isEnabled()) return;
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        World world = event.getWorld();
        for (Waypoint wp : plugin.getWaypointManager().getAllWaypoints()) {
            Location loc = wp.getLocation();
            if (loc == null || !world.equals(loc.getWorld())) continue;
            if ((loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                spawnHologram(wp);
            }
        }
    }

    // Hide all holograms on join; the visibility task reveals applicable ones within the next second.
    // The 5-tick delay lets the server finish initialising the player's position and chunks.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            hideAllFromPlayer(player);
            updateVisibilityForPlayer(player);
        }, 5L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void spawnAll() {
        if (!isEnabled()) return;
        for (Waypoint wp : plugin.getWaypointManager().getAllWaypoints()) {
            spawnHologram(wp);
        }
    }

    public void removeAll() {
        stopVisibilityTask();
        for (List<ArmorStand> stands : waypointStands.values()) {
            removeStands(stands);
        }
        waypointStands.clear();
    }

    public void spawnHologram(Waypoint wp) {
        if (!isEnabled()) return;
        removeHologram(wp.getId());

        Location loc = wp.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;

        double x = loc.getBlockX() + 0.5;
        double z = loc.getBlockZ() + 0.5;
        double baseY = loc.getY() + plugin.getConfig().getDouble("holograms.height", 1.8);

        List<Component> lines = buildLines(wp);
        List<ArmorStand> stands = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            double y = baseY + i * 0.3;
            Location spawnLoc = new Location(world, x, y, z);
            Component line = lines.get(i);
            ArmorStand stand = world.spawn(spawnLoc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setGravity(false);
                as.setCustomNameVisible(true);
                as.customName(line);
                as.setPersistent(false);
            });
            // Start hidden from everyone; per-player visibility applied below
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideEntity(plugin, stand);
            }
            stands.add(stand);
        }

        waypointStands.put(wp.getId(), stands);

        // Immediately show to players already in range/LOS so there's no 1-second delay
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyVisibility(p, stands);
        }
    }

    public void removeHologram(UUID waypointId) {
        List<ArmorStand> stands = waypointStands.remove(waypointId);
        if (stands != null) removeStands(stands);
    }

    public void updateHologram(Waypoint wp) {
        spawnHologram(wp);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void updateVisibilityForPlayer(Player player) {
        for (List<ArmorStand> stands : waypointStands.values()) {
            applyVisibility(player, stands);
        }
    }

    // Show or hide a group of stands for one player based on range + LOS.
    private void applyVisibility(Player player, List<ArmorStand> stands) {
        if (stands.isEmpty()) return;
        ArmorStand ref = stands.get(0);
        if (!ref.isValid()) return;

        boolean visible = canSee(player, ref);
        for (ArmorStand stand : stands) {
            if (!stand.isValid()) continue;
            if (visible) {
                player.showEntity(plugin, stand);
            } else {
                player.hideEntity(plugin, stand);
            }
        }
    }

    private boolean canSee(Player player, ArmorStand stand) {
        if (!player.getWorld().equals(stand.getWorld())) return false;

        double viewDist = plugin.getConfig().getDouble("holograms.view-distance", 8.0);
        if (player.getLocation().distanceSquared(stand.getLocation()) > viewDist * viewDist) return false;

        if (plugin.getConfig().getBoolean("holograms.require-line-of-sight", true)) {
            if (!player.hasLineOfSight(stand)) return false;
        }

        return true;
    }

    private void hideAllFromPlayer(Player player) {
        for (List<ArmorStand> stands : waypointStands.values()) {
            for (ArmorStand stand : stands) {
                if (stand.isValid()) player.hideEntity(plugin, stand);
            }
        }
    }

    private List<Component> buildLines(Waypoint wp) {
        List<Component> lines = new ArrayList<>();

        // Bottom: fee (only when vault is enabled)
        if (plugin.getConfig().getBoolean("holograms.show-fee", true)
                && plugin.getEconomyManager().isEnabled()) {
            lines.add(wp.getFee() > 0
                    ? Component.text("Fee: " + plugin.getEconomyManager().format(wp.getFee())).color(NamedTextColor.YELLOW)
                    : Component.text("Free").color(NamedTextColor.GREEN));
        }

        // Visibility status
        lines.add(wp.isPublic()
                ? Component.text("Public").color(NamedTextColor.GREEN)
                : Component.text("Private").color(NamedTextColor.RED));

        // Owner
        if (plugin.getConfig().getBoolean("holograms.show-owner", true)) {
            lines.add(Component.text("Owner: ").color(NamedTextColor.GRAY)
                    .append(Component.text(wp.getOwnerName()).color(NamedTextColor.WHITE)));
        }

        // Top: waypoint name
        lines.add(Component.text(wp.getName()).color(NamedTextColor.AQUA));

        return lines;
    }

    private void removeStands(List<ArmorStand> stands) {
        for (ArmorStand stand : stands) {
            if (stand.isValid()) stand.remove();
        }
    }
}
