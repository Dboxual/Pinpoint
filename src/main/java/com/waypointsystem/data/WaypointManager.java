package com.waypointsystem.data;

import com.waypointsystem.WaypointPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class WaypointManager {

    private final WaypointPlugin plugin;
    private final WaypointStorage storage;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();

    // Pending invites: invitee UUID -> invite data
    private final Map<UUID, TeleportInvite> pendingInvites = new HashMap<>();

    // Pending name inputs: player UUID -> pending waypoint location
    private final Map<UUID, Location> pendingNaming = new HashMap<>();

    // Pending fee inputs: player UUID -> waypoint UUID
    private final Map<UUID, UUID> pendingFeeInput = new HashMap<>();

    // Timeout task IDs so they can be cancelled when input arrives early
    private final Map<UUID, Integer> pendingNamingTaskIds = new HashMap<>();
    private final Map<UUID, Integer> pendingFeeTaskIds = new HashMap<>();

    // Recall Orb use timestamps for cooldown enforcement
    private final Map<UUID, Long> recallOrbCooldowns = new HashMap<>();

    // Pending rename inputs: player UUID -> waypoint UUID being renamed
    private final Map<UUID, UUID> pendingRenaming = new HashMap<>();
    private final Map<UUID, Integer> pendingRenamingTaskIds = new HashMap<>();

    // Block location index: "world,x,y,z" -> waypoint UUID
    private final Map<String, UUID> locationIndex = new HashMap<>();

    public WaypointManager(WaypointPlugin plugin, WaypointStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadAll() {
        waypoints.clear();
        locationIndex.clear();
        for (Waypoint wp : storage.loadAll()) {
            waypoints.put(wp.getId(), wp);
            locationIndex.put(locationKey(wp), wp.getId());
        }
        plugin.getLogger().info("Loaded " + waypoints.size() + " waypoints.");
    }

    public Waypoint createWaypoint(String name, Player owner, Location location) {
        UUID id = UUID.randomUUID();
        Waypoint wp = new Waypoint(id, name, owner.getUniqueId(), owner.getName(), location,
                false, plugin.getConfig().getDouble("settings.default-fee", 0));
        waypoints.put(id, wp);
        locationIndex.put(locationKey(wp), id);
        storage.saveWaypoint(wp);
        return wp;
    }

    public void saveWaypoint(Waypoint wp) {
        storage.saveWaypoint(wp);
    }

    public void deleteWaypoint(UUID id) {
        Waypoint wp = waypoints.remove(id);
        if (wp != null) locationIndex.remove(locationKey(wp));
        storage.deleteWaypoint(id);
    }

    // --- Block location index ---

    private String locationKey(Waypoint wp) {
        return wp.getWorldName() + "," + (int) wp.getX() + "," + (int) wp.getY() + "," + (int) wp.getZ();
    }

    private String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public Optional<Waypoint> getWaypointAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();
        UUID id = locationIndex.get(locationKey(loc));
        return id != null ? getWaypoint(id) : Optional.empty();
    }

    public Optional<Waypoint> getWaypoint(UUID id) {
        return Optional.ofNullable(waypoints.get(id));
    }

    public List<Waypoint> getAccessibleWaypoints(UUID playerUuid) {
        return waypoints.values().stream()
                .filter(wp -> wp.canAccess(playerUuid))
                .sorted(Comparator.comparing(Waypoint::getName))
                .collect(Collectors.toList());
    }

    public List<Waypoint> getOwnedWaypoints(UUID playerUuid) {
        return waypoints.values().stream()
                .filter(wp -> wp.isOwner(playerUuid))
                .sorted(Comparator.comparing(Waypoint::getName))
                .collect(Collectors.toList());
    }

    // --- Pending naming ---

    public void setPendingNaming(UUID playerUuid, Location loc) {
        pendingNaming.put(playerUuid, loc);
    }

    public Location getPendingNaming(UUID playerUuid) {
        return pendingNaming.get(playerUuid);
    }

    public boolean hasPendingNaming(UUID playerUuid) {
        return pendingNaming.containsKey(playerUuid);
    }

    public void clearPendingNaming(UUID playerUuid) {
        pendingNaming.remove(playerUuid);
    }

    // --- Pending fee input ---

    public void setPendingFeeInput(UUID playerUuid, UUID waypointId) {
        pendingFeeInput.put(playerUuid, waypointId);
    }

    public UUID getPendingFeeInput(UUID playerUuid) {
        return pendingFeeInput.get(playerUuid);
    }

    public boolean hasPendingFeeInput(UUID playerUuid) {
        return pendingFeeInput.containsKey(playerUuid);
    }

    public void clearPendingFeeInput(UUID playerUuid) {
        pendingFeeInput.remove(playerUuid);
    }

    // --- Timeout task ID management ---

    public void setPendingNamingTaskId(UUID uuid, int taskId) { pendingNamingTaskIds.put(uuid, taskId); }
    public int getPendingNamingTaskId(UUID uuid) { return pendingNamingTaskIds.getOrDefault(uuid, -1); }
    public void clearPendingNamingTaskId(UUID uuid) { pendingNamingTaskIds.remove(uuid); }

    public void setPendingFeeTaskId(UUID uuid, int taskId) { pendingFeeTaskIds.put(uuid, taskId); }
    public int getPendingFeeTaskId(UUID uuid) { return pendingFeeTaskIds.getOrDefault(uuid, -1); }
    public void clearPendingFeeTaskId(UUID uuid) { pendingFeeTaskIds.remove(uuid); }

    // --- Pending renaming ---

    public void setPendingRenaming(UUID playerUuid, UUID waypointId) { pendingRenaming.put(playerUuid, waypointId); }
    public UUID getPendingRenaming(UUID playerUuid) { return pendingRenaming.get(playerUuid); }
    public boolean hasPendingRenaming(UUID playerUuid) { return pendingRenaming.containsKey(playerUuid); }
    public void clearPendingRenaming(UUID playerUuid) { pendingRenaming.remove(playerUuid); }

    public void setPendingRenamingTaskId(UUID uuid, int taskId) { pendingRenamingTaskIds.put(uuid, taskId); }
    public int getPendingRenamingTaskId(UUID uuid) { return pendingRenamingTaskIds.getOrDefault(uuid, -1); }
    public void clearPendingRenamingTaskId(UUID uuid) { pendingRenamingTaskIds.remove(uuid); }

    // --- Lookup by name ---

    public Optional<Waypoint> getWaypointByName(String name) {
        return waypoints.values().stream()
                .filter(wp -> wp.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public List<Waypoint> getWaypointsByName(String name) {
        return waypoints.values().stream()
                .filter(wp -> wp.getName().equalsIgnoreCase(name))
                .sorted(Comparator.comparing(Waypoint::getName))
                .collect(Collectors.toList());
    }

    // Resolves by UUID first; falls back to name only when the input is not a UUID.
    public Optional<Waypoint> getWaypointByNameOrId(String input) {
        try {
            return getWaypoint(UUID.fromString(input));
        } catch (IllegalArgumentException e) {
            return getWaypointByName(input);
        }
    }

    // Short display ID: first 4 hex chars of the UUID, upper-case.
    public static String shortId(UUID id) {
        return id.toString().substring(0, 4).toUpperCase();
    }

    // --- Recall Orb cooldown ---

    public boolean isOnRecallCooldown(UUID uuid) {
        Long last = recallOrbCooldowns.get(uuid);
        if (last == null) return false;
        int cooldown = plugin.getConfig().getInt("settings.teleport-cooldown-seconds", 3);
        return (System.currentTimeMillis() - last) < cooldown * 1000L;
    }

    public long getRemainingCooldownSeconds(UUID uuid) {
        Long last = recallOrbCooldowns.get(uuid);
        if (last == null) return 0;
        int cooldown = plugin.getConfig().getInt("settings.teleport-cooldown-seconds", 3);
        long remainingMs = (cooldown * 1000L) - (System.currentTimeMillis() - last);
        return Math.max(0, (remainingMs + 999) / 1000);
    }

    public void setRecallCooldown(UUID uuid) {
        recallOrbCooldowns.put(uuid, System.currentTimeMillis());
    }

    // --- Waypoint limit ---

    public boolean isAtWaypointLimit(UUID playerUuid) {
        int max = plugin.getConfig().getInt("settings.max-waypoints-per-player", 10);
        if (max <= 0) return false;
        return getOwnedWaypoints(playerUuid).size() >= max;
    }

    public int getMaxWaypoints() {
        return plugin.getConfig().getInt("settings.max-waypoints-per-player", 10);
    }

    // --- Teleport invites ---

    public void createInvite(UUID inviter, UUID invitee, UUID waypointId, UUID orbId) {
        pendingInvites.put(invitee, new TeleportInvite(inviter, invitee, waypointId, orbId));
    }

    public TeleportInvite getInvite(UUID invitee) {
        return pendingInvites.get(invitee);
    }

    public boolean hasPendingInvite(UUID invitee) {
        return pendingInvites.containsKey(invitee);
    }

    public void removeInvite(UUID invitee) {
        pendingInvites.remove(invitee);
    }

    public Collection<Waypoint> getAllWaypoints() {
        return Collections.unmodifiableCollection(waypoints.values());
    }

    public static class TeleportInvite {
        public final UUID inviterUuid;
        public final UUID inviteeUuid;
        public final UUID waypointId;
        public final UUID orbId;
        public final long createdAt;
        public boolean accepted = false;

        public TeleportInvite(UUID inviterUuid, UUID inviteeUuid, UUID waypointId, UUID orbId) {
            this.inviterUuid = inviterUuid;
            this.inviteeUuid = inviteeUuid;
            this.waypointId = waypointId;
            this.orbId = orbId;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
