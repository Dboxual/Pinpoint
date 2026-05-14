package com.pinpoint.data;

import java.util.UUID;

public class TravelOffer {

    public final UUID id;
    public final UUID travelerUuid;
    public final String travelerName;
    public final UUID waypointUuid;
    public final String waypointName;
    public int taskId = -1;

    public TravelOffer(UUID id, UUID travelerUuid, String travelerName,
                       UUID waypointUuid, String waypointName) {
        this.id = id;
        this.travelerUuid = travelerUuid;
        this.travelerName = travelerName;
        this.waypointUuid = waypointUuid;
        this.waypointName = waypointName;
    }
}
