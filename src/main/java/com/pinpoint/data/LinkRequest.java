package com.pinpoint.data;

import java.util.UUID;

public class LinkRequest {

    public final UUID id;
    public final UUID senderUuid;
    public final UUID targetUuid;
    public int taskId = -1;

    public LinkRequest(UUID id, UUID senderUuid, UUID targetUuid) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.targetUuid = targetUuid;
    }
}
