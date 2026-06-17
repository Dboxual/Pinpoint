package com.pinpoint.data;

import com.pinpoint.PinpointPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class WaypointManager {

    private final PinpointPlugin plugin;
    private final WaypointStorage storage;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();

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

    // Pending delayed teleports: player UUID -> PendingTeleport
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    // Pending landmark creation: admin UUID -> landmark name
    private final Map<UUID, String> pendingLandmarkCreation = new HashMap<>();

    // Pending player teleport requests: requester UUID -> target UUID
    private final Map<UUID, UUID> pendingPlayerRequests = new HashMap<>();
    private final Map<UUID, Integer> pendingPlayerRequestTaskIds = new HashMap<>();
    // Expiry timestamp (epoch ms) for each pending request — used by GUI to show time remaining
    private final Map<UUID, Long> pendingPlayerRequestExpiry = new HashMap<>();

    // Active player teleport sessions — both requester and target UUID key into the same session
    private final Map<UUID, PlayerTeleportSession> activePlayerSessions = new HashMap<>();

    public WaypointManager(PinpointPlugin plugin, WaypointStorage storage) {
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

    public Waypoint createLandmark(String name, Location location) {
        UUID id = UUID.randomUUID();
        double cost = plugin.getConfig().getDouble("landmarks.teleport.cost", 0);
        Waypoint landmark = new Waypoint(id, name, Waypoint.LANDMARK_OWNER_UUID, "", location, false, cost);
        landmark.setType(WaypointType.LANDMARK);
        waypoints.put(id, landmark);
        locationIndex.put(locationKey(landmark), id);
        storage.saveWaypoint(landmark);
        return landmark;
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

    public List<Waypoint> getInvitedWaypoints(UUID playerUuid) {
        return waypoints.values().stream()
                .filter(wp -> !wp.isOwner(playerUuid) && !wp.isPublic() && wp.isInvited(playerUuid))
                .sorted(Comparator.comparing(Waypoint::getName))
                .collect(Collectors.toList());
    }

    public List<Waypoint> getPublicWaypoints(UUID playerUuid) {
        return waypoints.values().stream()
                .filter(wp -> wp.getType() != WaypointType.LANDMARK && wp.isPublic() && !wp.isOwner(playerUuid))
                .sorted(Comparator.comparing(Waypoint::getName))
                .collect(Collectors.toList());
    }

    public List<Waypoint> getLandmarks() {
        return waypoints.values().stream()
                .filter(wp -> wp.getType() == WaypointType.LANDMARK)
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

    public Collection<Waypoint> getAllWaypoints() {
        return Collections.unmodifiableCollection(waypoints.values());
    }

    // --- Pending delayed teleports ---

    public void setPendingTeleport(UUID playerUuid, PendingTeleport tp) { pendingTeleports.put(playerUuid, tp); }
    public PendingTeleport getPendingTeleport(UUID playerUuid) { return pendingTeleports.get(playerUuid); }
    public boolean hasPendingTeleport(UUID playerUuid) { return pendingTeleports.containsKey(playerUuid); }
    public void clearPendingTeleport(UUID playerUuid) { pendingTeleports.remove(playerUuid); }

    // --- Pending landmark creation ---

    public void setPendingLandmarkCreation(UUID playerUuid, String name) { pendingLandmarkCreation.put(playerUuid, name); }
    public String getPendingLandmarkCreation(UUID playerUuid) { return pendingLandmarkCreation.get(playerUuid); }
    public boolean hasPendingLandmarkCreation(UUID playerUuid) { return pendingLandmarkCreation.containsKey(playerUuid); }
    public void clearPendingLandmarkCreation(UUID playerUuid) { pendingLandmarkCreation.remove(playerUuid); }

    // --- Pending player teleport requests ---

    public void setPendingPlayerRequest(UUID requesterId, UUID targetId) { pendingPlayerRequests.put(requesterId, targetId); }
    public UUID getPendingPlayerRequest(UUID requesterId) { return pendingPlayerRequests.get(requesterId); }
    public boolean hasPendingPlayerRequest(UUID requesterId) { return pendingPlayerRequests.containsKey(requesterId); }
    public void clearPendingPlayerRequest(UUID requesterId) { pendingPlayerRequests.remove(requesterId); }

    public void setPendingPlayerRequestTaskId(UUID requesterId, int taskId) { pendingPlayerRequestTaskIds.put(requesterId, taskId); }
    public int getPendingPlayerRequestTaskId(UUID requesterId) { return pendingPlayerRequestTaskIds.getOrDefault(requesterId, -1); }
    public void clearPendingPlayerRequestTaskId(UUID requesterId) { pendingPlayerRequestTaskIds.remove(requesterId); }

    public void setPendingPlayerRequestExpiry(UUID requesterId, long expiryMs) { pendingPlayerRequestExpiry.put(requesterId, expiryMs); }
    public long getPendingPlayerRequestExpiry(UUID requesterId) { return pendingPlayerRequestExpiry.getOrDefault(requesterId, 0L); }
    public void clearPendingPlayerRequestExpiry(UUID requesterId) { pendingPlayerRequestExpiry.remove(requesterId); }

    /** Returns the UUID of the first requester who has a pending request targeting {@code targetId}, or null. */
    public UUID getIncomingRequest(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : pendingPlayerRequests.entrySet()) {
            if (targetId.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    // --- Active player teleport sessions ---

    public void setPlayerSession(UUID requesterId, UUID targetId, PlayerTeleportSession session) {
        activePlayerSessions.put(requesterId, session);
        activePlayerSessions.put(targetId, session);
    }

    public PlayerTeleportSession getPlayerSession(UUID playerUuid) { return activePlayerSessions.get(playerUuid); }
    public boolean hasPlayerSession(UUID playerUuid) { return activePlayerSessions.containsKey(playerUuid); }

    public void clearPlayerSession(UUID requesterId, UUID targetId) {
        activePlayerSessions.remove(requesterId);
        activePlayerSessions.remove(targetId);
    }

    public static class PendingTeleport {
        public final UUID playerId;
        public final UUID waypointId;
        public int taskId = -1;
        public int countdownTaskId = -1;
        public final Location startLocation;

        public PendingTeleport(UUID playerId, UUID waypointId, Location startLocation) {
            this.playerId = playerId;
            this.waypointId = waypointId;
            this.startLocation = startLocation;
        }
    }

    public static class PlayerTeleportSession {
        public final UUID requesterId;
        public final UUID targetId;
        public final String requesterName;
        public final String targetName;
        public int teleportTaskId = -1;
        public int countdownTaskId = -1;

        public PlayerTeleportSession(UUID requesterId, UUID targetId, String requesterName, String targetName) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.requesterName = requesterName;
            this.targetName = targetName;
        }
    }

    // --- Stale block validation ---

    /** Returns true if the physical block at this waypoint's location is no longer the expected block type. */
    public boolean isBlockStale(Waypoint wp) {
        Location loc = wp.getLocation();
        if (loc == null || loc.getWorld() == null) return true;
        if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return false;
        Material actual = loc.getWorld().getBlockAt(loc).getType();
        if (wp.getType() == WaypointType.LANDMARK) {
            String matName = plugin.getConfig().getString("landmarks.block.material", "LODESTONE");
            Material expected = Material.matchMaterial(matName);
            if (expected == null) expected = Material.LODESTONE;
            return actual != expected;
        }
        return actual != Material.LODESTONE;
    }

    /** Fully removes a waypoint whose physical block is gone: deletes hologram, data, and logs. */
    public void cleanStale(Waypoint wp) {
        plugin.getLogger().info("[Pinpoint] Removed stale pinpoint at "
                + wp.getWorldName() + "," + (int) wp.getX() + "," + (int) wp.getY() + "," + (int) wp.getZ()
                + " because the block no longer exists.");
        plugin.getHologramManager().removeHologram(wp.getId());
        deleteWaypoint(wp.getId());
    }

}
