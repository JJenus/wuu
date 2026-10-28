package com.surf.tracker.shared.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionMetadata {
    private String connectionId;
    private String deviceId;
    private String clientIp;
    private Instant connectedAt;
    private Instant lastSeen;

    // Required for Jackson deserialization
    public ConnectionMetadata() {}

    public ConnectionMetadata(String connectionId, String deviceId, String clientIp,
                              Instant connectedAt, Instant lastSeen) {
        this.connectionId = connectionId;
        this.deviceId = deviceId;
        this.clientIp = clientIp;
        this.connectedAt = connectedAt;
        this.lastSeen = lastSeen;
    }

    // Getters and Setters
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
}