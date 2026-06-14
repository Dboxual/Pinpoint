package com.pinpoint.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

public class Waypoint {

    public static final UUID LANDMARK_OWNER_UUID = new UUID(0L, 0L);

    private final UUID id;
    private String name;
    private final UUID ownerUuid;
    private WaypointType type = WaypointType.PINPOINT;
    private String ownerName;
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;
    private boolean isPublic;
    private double fee;
    private final Set<UUID> invitedPlayers = new HashSet<>();
    private final Set<UUID> linkedRecallOrbs = new HashSet<>();
    private String iconMaterial = "LODESTONE";
    // Owner-configured teleport facing. Float.NaN = not set (uses safe-spot default).
    private float teleportYaw   = Float.NaN;
    private float teleportPitch = 0f;
    // Cardinal direction (NORTH/SOUTH/EAST/WEST). null = not set. Takes priority over teleportYaw.
    private String teleportDirection = null;

    public Waypoint(UUID id, String name, UUID ownerUuid, String ownerName,
                    Location location, boolean isPublic, double fee) {
        this.id = id;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.isPublic = isPublic;
        this.fee = fee;
        setLocation(location);
    }

    public void setLocation(Location loc) {
        this.worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.pitch = loc.getPitch();
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean canAccess(UUID playerUuid) {
        if (type == WaypointType.LANDMARK) return true;
        return isPublic || ownerUuid.equals(playerUuid) || invitedPlayers.contains(playerUuid);
    }

    public boolean isOwner(UUID playerUuid) {
        if (type == WaypointType.LANDMARK) return false;
        return ownerUuid.equals(playerUuid);
    }

    public WaypointType getType() { return type; }
    public void setType(WaypointType type) { this.type = type; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean pub) { this.isPublic = pub; }
    public double getFee() { return fee; }
    public void setFee(double fee) { this.fee = fee; }
    public Set<UUID> getInvitedPlayers() { return Collections.unmodifiableSet(invitedPlayers); }
    public void addInvite(UUID uuid) { invitedPlayers.add(uuid); }
    public void removeInvite(UUID uuid) { invitedPlayers.remove(uuid); }
    public boolean isInvited(UUID uuid) { return invitedPlayers.contains(uuid); }
    public Set<UUID> getLinkedRecallOrbs() { return Collections.unmodifiableSet(linkedRecallOrbs); }
    public void addRecallOrb(UUID orbId) { linkedRecallOrbs.add(orbId); }
    public void removeRecallOrb(UUID orbId) { linkedRecallOrbs.remove(orbId); }

    public Material getIconMaterial() {
        Material mat = Material.matchMaterial(iconMaterial);
        return mat != null ? mat : Material.LODESTONE;
    }

    public void setIconMaterial(Material mat) { this.iconMaterial = mat.name(); }
    public String getIconMaterialName() { return iconMaterial; }

    public boolean hasTeleportYaw() { return !Float.isNaN(teleportYaw); }
    public float getTeleportYaw()   { return teleportYaw; }
    public float getTeleportPitch() { return teleportPitch; }
    public void setTeleportYaw(float yaw) { this.teleportYaw = yaw; }
    public void setTeleportPitch(float pitch) { this.teleportPitch = pitch; }

    public boolean hasTeleportDirection() { return teleportDirection != null; }
    public String getTeleportDirection()  { return teleportDirection; }

    public void setTeleportDirection(String direction) {
        this.teleportDirection = direction;
        this.teleportYaw       = Float.NaN;
        this.teleportPitch     = 0f;
    }

    public void clearTeleportDirection() {
        this.teleportDirection = null;
        this.teleportYaw       = Float.NaN;
        this.teleportPitch     = 0f;
    }
}
