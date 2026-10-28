package com.surf.tracker.shared.events;

import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.pubsub.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Clock;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class LocationDataEvent extends DomainEvent {
    private final String deviceId;
    private final LocationPoint location;
    private final String protocol;
    private final Map<String, Object> metaData;

    @JsonCreator
    public LocationDataEvent(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("location") LocationPoint location,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("metaData") Map<String, Object> metaData) {
        super();
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.metaData = metaData != null ? metaData : new HashMap<>();
    }

    public LocationDataEvent(Clock clock, String deviceId, LocationPoint location, String protocol) {
        super(clock, UUID.randomUUID());
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.metaData = new HashMap<>();
    }

    public LocationDataEvent(Clock clock, UUID eventId, String deviceId,
                             LocationPoint location, String protocol,
                             Map<String, Object> metaData) {
        super(clock, eventId);
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.metaData = metaData != null ? metaData : new HashMap<>();
    }

    public String getDeviceId() { return deviceId; }
    public LocationPoint getLocation() { return location; }
    public String getProtocol() { return protocol; }
    public Map<String, Object> getMetaData() { return metaData; }

    public void addMetaData(String key, Object value) {
        this.metaData.put(key, value);
    }
}