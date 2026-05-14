package com.pinpoint.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Party {

    private final UUID id;
    private final Set<UUID> members;

    public Party(UUID id, Set<UUID> members) {
        this.id = id;
        this.members = new HashSet<>(members);
    }

    public UUID getId() { return id; }

    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }

    public boolean hasMember(UUID uuid) { return members.contains(uuid); }

    void addMember(UUID uuid) { members.add(uuid); }

    boolean removeMember(UUID uuid) { return members.remove(uuid); }

    public int size() { return members.size(); }
}
