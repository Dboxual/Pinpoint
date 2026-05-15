package com.pinpoint.hologram;

import com.pinpoint.PinpointPlugin;
import com.pinpoint.data.Waypoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.*;

public class HologramManager implements Listener {

    private final PinpointPlugin plugin;
    // waypoint UUID -> list of armor stand entity UUIDs (one per display line)
    private final Map<UUID, List<UUID>> waypointEntities = new HashMap<>();

    public HologramManager(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("holograms.enabled", true);
    }

    // Respawn holograms for any waypoints whose block is in this chunk.
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

    public void spawnAll() {
        if (!isEnabled()) return;
        for (Waypoint wp : plugin.getWaypointManager().getAllWaypoints()) {
            spawnHologram(wp);
        }
    }

    public void removeAll() {
        for (List<UUID> ids : waypointEntities.values()) {
            removeEntities(ids);
        }
        waypointEntities.clear();
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
        List<UUID> ids = new ArrayList<>();

        // lines[0] = bottom (fee), lines[last] = top (name) — stack upward
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
            ids.add(stand.getUniqueId());
        }
        waypointEntities.put(wp.getId(), ids);
    }

    public void removeHologram(UUID waypointId) {
        List<UUID> ids = waypointEntities.remove(waypointId);
        if (ids != null) removeEntities(ids);
    }

    public void updateHologram(Waypoint wp) {
        spawnHologram(wp);
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

    private void removeEntities(List<UUID> ids) {
        for (UUID id : ids) {
            for (World world : Bukkit.getWorlds()) {
                Entity e = world.getEntity(id);
                if (e != null) {
                    e.remove();
                    break;
                }
            }
        }
    }
}
