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

    public WaypointManager(WaypointPlugin plugin, WaypointStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void loadAll() {
        waypoints.clear();
        for (Waypoint wp : storage.loadAll()) {
            waypoints.put(wp.getId(), wp);
        }
        plugin.getLogger().info("Loaded " + waypoints.size() + " waypoints.");
    }

    public Waypoint createWaypoint(String name, Player owner, Location location) {
        UUID id = UUID.randomUUID();
        Waypoint wp = new Waypoint(id, name, owner.getUniqueId(), owner.getName(), location,
                false, plugin.getConfig().getDouble("settings.default-fee", 0));
        waypoints.put(id, wp);
        storage.saveWaypoint(wp);
        return wp;
    }

    public void saveWaypoint(Waypoint wp) {
        storage.saveWaypoint(wp);
    }

    public void deleteWaypoint(UUID id) {
        waypoints.remove(id);
        storage.deleteWaypoint(id);
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

    // --- Lookup by name ---

    public Optional<Waypoint> getWaypointByName(String name) {
        return waypoints.values().stream()
                .filter(wp -> wp.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<Waypoint> getWaypointByNameOrId(String input) {
        try {
            return getWaypoint(UUID.fromString(input));
        } catch (IllegalArgumentException e) {
            return getWaypointByName(input);
        }
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
