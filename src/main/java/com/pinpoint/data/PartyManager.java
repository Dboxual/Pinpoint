package com.pinpoint.data;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PartyManager {

    private final Map<UUID, Party> parties = new HashMap<>();           // partyId → Party
    private final Map<UUID, UUID> playerToParty = new HashMap<>();      // playerUuid → partyId
    private final Map<UUID, LinkRequest> linkRequests = new HashMap<>(); // targetUuid → pending request
    private final Map<UUID, TravelOffer> travelOffers = new HashMap<>(); // offerId → offer
    private final Map<UUID, UUID> lastTravelOffer = new HashMap<>();    // playerUuid → latest offerId received

    // --- Party queries ---

    public Party getPartyOf(UUID playerUuid) {
        UUID partyId = playerToParty.get(playerUuid);
        return partyId != null ? parties.get(partyId) : null;
    }

    public boolean inSameParty(UUID a, UUID b) {
        UUID partyA = playerToParty.get(a);
        return partyA != null && partyA.equals(playerToParty.get(b));
    }

    public boolean inAnyParty(UUID playerUuid) {
        return playerToParty.containsKey(playerUuid);
    }

    /** All party members for a player excluding themselves. Empty if not in a party. */
    public Set<UUID> getOtherMembers(UUID playerUuid) {
        Party party = getPartyOf(playerUuid);
        if (party == null) return Collections.emptySet();
        Set<UUID> others = new HashSet<>(party.getMembers());
        others.remove(playerUuid);
        return others;
    }

    // --- Party mutations ---

    /**
     * Links two players. Handles all merge cases:
     *   Neither in party         → create new 2-person party
     *   One in party, other not  → add to existing party
     *   Both in different parties → merge (partyB members absorbed into partyA)
     */
    public void linkPlayers(UUID a, UUID b) {
        UUID partyA = playerToParty.get(a);
        UUID partyB = playerToParty.get(b);

        if (partyA != null && partyB != null) {
            if (partyA.equals(partyB)) return;
            Party pA = parties.get(partyA);
            Party pB = parties.remove(partyB);
            for (UUID member : pB.getMembers()) {
                pA.addMember(member);
                playerToParty.put(member, partyA);
            }
        } else if (partyA != null) {
            parties.get(partyA).addMember(b);
            playerToParty.put(b, partyA);
        } else if (partyB != null) {
            parties.get(partyB).addMember(a);
            playerToParty.put(a, partyB);
        } else {
            UUID newId = UUID.randomUUID();
            Party party = new Party(newId, new HashSet<>(List.of(a, b)));
            parties.put(newId, party);
            playerToParty.put(a, newId);
            playerToParty.put(b, newId);
        }
    }

    /**
     * Player leaves their party. If only 1 member remains after departure the party dissolves.
     * Returns the remaining members (empty set means party dissolved or player was not in one).
     */
    public Set<UUID> leaveParty(UUID playerUuid) {
        UUID partyId = playerToParty.remove(playerUuid);
        if (partyId == null) return Collections.emptySet();
        Party party = parties.get(partyId);
        if (party == null) return Collections.emptySet();
        party.removeMember(playerUuid);
        if (party.size() <= 1) {
            Set<UUID> remaining = new HashSet<>(party.getMembers());
            remaining.forEach(playerToParty::remove);
            parties.remove(partyId);
            return Collections.emptySet();
        }
        return new HashSet<>(party.getMembers());
    }

    /**
     * Disbands the entire party of playerUuid. Returns all former members for notification.
     * Empty if the player was not in a party.
     */
    public Set<UUID> disbandParty(UUID playerUuid) {
        UUID partyId = playerToParty.get(playerUuid);
        if (partyId == null) return Collections.emptySet();
        Party party = parties.remove(partyId);
        if (party == null) return Collections.emptySet();
        Set<UUID> members = new HashSet<>(party.getMembers());
        members.forEach(playerToParty::remove);
        return members;
    }

    /** Any member may remove any other — delegates to leaveParty for symmetry. */
    public Set<UUID> removeMember(UUID target) {
        return leaveParty(target);
    }

    // --- Link requests (one per target at a time) ---

    public void addLinkRequest(LinkRequest request) {
        linkRequests.put(request.targetUuid, request);
    }

    public LinkRequest getLinkRequest(UUID targetUuid) {
        return linkRequests.get(targetUuid);
    }

    public boolean hasPendingLinkRequest(UUID targetUuid) {
        return linkRequests.containsKey(targetUuid);
    }

    public void removeLinkRequest(UUID targetUuid) {
        linkRequests.remove(targetUuid);
    }

    // --- Travel offers ---

    public void addTravelOffer(TravelOffer offer) {
        travelOffers.put(offer.id, offer);
    }

    /** Records which offer a player last received (for no-arg /party follow). */
    public void setLastTravelOffer(UUID playerUuid, UUID offerId) {
        lastTravelOffer.put(playerUuid, offerId);
    }

    public TravelOffer getTravelOffer(UUID offerId) {
        return travelOffers.get(offerId);
    }

    /** Returns the most recent travel offer sent to playerUuid, or null if none/expired. */
    public TravelOffer getLastTravelOffer(UUID playerUuid) {
        UUID offerId = lastTravelOffer.get(playerUuid);
        return offerId != null ? travelOffers.get(offerId) : null;
    }

    public void removeTravelOffer(UUID offerId) {
        travelOffers.remove(offerId);
        lastTravelOffer.entrySet().removeIf(e -> offerId.equals(e.getValue()));
    }

    // --- Storage support ---

    public void loadParty(Party party) {
        parties.put(party.getId(), party);
        for (UUID member : party.getMembers()) {
            playerToParty.put(member, party.getId());
        }
    }

    public Collection<Party> getAllParties() {
        return Collections.unmodifiableCollection(parties.values());
    }

    public void clear() {
        parties.clear();
        playerToParty.clear();
        linkRequests.clear();
        travelOffers.clear();
        lastTravelOffer.clear();
    }
}
