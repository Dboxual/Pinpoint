package com.waypointsystem.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class Waypoint {

    private final UUID id;
    private String name;
    private final UUID ownerUuid;
    private String ownerName;
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;
    private boolean isPublic;
    private double fee;
    private final Set<UUID> invitedPlayers = new HashSet<>();
    private final Set<UUID> linkedRecallOrbs = new HashSet<>();

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
        return isPublic || ownerUuid.equals(playerUuid) || invitedPlayers.contains(playerUuid);
    }

    public boolean isOwner(UUID playerUuid) {
        return ownerUuid.equals(playerUuid);
    }

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
}
