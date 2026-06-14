package com.pinpoint.data;

import com.pinpoint.PinpointPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class WaypointStorage {

    private final PinpointPlugin plugin;
    private File dataFile;
    private YamlConfiguration data;

    public WaypointStorage(PinpointPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "waypoints.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create waypoints.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save waypoints.yml", e);
        }
    }

    public Collection<Waypoint> loadAll() {
        List<Waypoint> waypoints = new ArrayList<>();
        if (!data.contains("waypoints")) return waypoints;

        for (String key : data.getConfigurationSection("waypoints").getKeys(false)) {
            String path = "waypoints." + key + ".";
            try {
                UUID id = UUID.fromString(key);
                String name = data.getString(path + "name");
                UUID ownerUuid = UUID.fromString(Objects.requireNonNull(data.getString(path + "owner-uuid")));
                String ownerName = data.getString(path + "owner-name", "Unknown");
                String worldName = data.getString(path + "world", "world");
                double x = data.getDouble(path + "x");
                double y = data.getDouble(path + "y");
                double z = data.getDouble(path + "z");
                float yaw = (float) data.getDouble(path + "yaw");
                float pitch = (float) data.getDouble(path + "pitch");
                boolean isPublic = data.getBoolean(path + "public", false);
                double fee = data.getDouble(path + "fee", 0);

                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found for waypoint " + name + ", skipping.");
                    continue;
                }

                org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z, yaw, pitch);
                Waypoint wp = new Waypoint(id, name, ownerUuid, ownerName, loc, isPublic, fee);

                String typeStr = data.getString(path + "type", "PINPOINT");
                try { wp.setType(WaypointType.valueOf(typeStr)); } catch (Exception ignored) {}

                String iconStr = data.getString(path + "icon", "LODESTONE");
                org.bukkit.Material iconMat = org.bukkit.Material.matchMaterial(iconStr);
                if (iconMat != null) wp.setIconMaterial(iconMat);

                if (data.contains(path + "teleport-direction")) {
                    wp.setTeleportDirection(data.getString(path + "teleport-direction"));
                } else if (data.contains(path + "teleport-yaw")) {
                    // Migrate legacy exact-yaw to nearest cardinal direction.
                    float legacyYaw = (float) data.getDouble(path + "teleport-yaw");
                    wp.setTeleportDirection(yawToCardinal(legacyYaw));
                }

                List<String> invited = data.getStringList(path + "invited");
                for (String s : invited) {
                    try { wp.addInvite(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                List<String> orbs = data.getStringList(path + "recall-orbs");
                for (String s : orbs) {
                    try { wp.addRecallOrb(UUID.fromString(s)); } catch (Exception ignored) {}
                }

                waypoints.add(wp);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load waypoint " + key + ": " + e.getMessage());
            }
        }
        return waypoints;
    }

    public void saveWaypoint(Waypoint wp) {
        String path = "waypoints." + wp.getId() + ".";
        data.set(path + "type", wp.getType().name());
        data.set(path + "name", wp.getName());
        data.set(path + "owner-uuid", wp.getOwnerUuid().toString());
        data.set(path + "owner-name", wp.getOwnerName());
        data.set(path + "world", wp.getWorldName());
        data.set(path + "x", wp.getX());
        data.set(path + "y", wp.getY());
        data.set(path + "z", wp.getZ());
        data.set(path + "yaw", (double) wp.getYaw());
        data.set(path + "pitch", (double) wp.getPitch());
        data.set(path + "public", wp.isPublic());
        data.set(path + "fee", wp.getFee());
        data.set(path + "icon", wp.getIconMaterialName());
        data.set(path + "teleport-direction",
                wp.hasTeleportDirection() ? wp.getTeleportDirection() : null);
        data.set(path + "teleport-yaw",   null);
        data.set(path + "teleport-pitch", null);

        List<String> invited = new ArrayList<>();
        wp.getInvitedPlayers().forEach(u -> invited.add(u.toString()));
        data.set(path + "invited", invited);

        List<String> orbs = new ArrayList<>();
        wp.getLinkedRecallOrbs().forEach(u -> orbs.add(u.toString()));
        data.set(path + "recall-orbs", orbs);

        save();
    }

    public void deleteWaypoint(UUID id) {
        data.set("waypoints." + id, null);
        save();
    }

    private static String yawToCardinal(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw < 45 || yaw >= 315) return "SOUTH";
        if (yaw < 135)               return "WEST";
        if (yaw < 225)               return "NORTH";
        return "EAST";
    }
}
