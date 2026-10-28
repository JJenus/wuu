package com.surf.tracker.shared.domain;

import java.time.Clock;
import java.time.Instant;

public class ConnectionInfo {
    private String connectionId;
    private String deviceId;
    private String clientIp;
    private Instant connectedAt;
    private Instant lastSeen;

    public ConnectionInfo() {}

    public ConnectionInfo(String connectionId, String deviceId, String clientIp, Clock clock) {
        this.connectionId = connectionId;
        this.deviceId = deviceId;
        this.clientIp = clientIp;
        this.connectedAt = clock.instant();
        this.lastSeen = this.connectedAt;
    }

    public ConnectionInfo(String connectionId, String deviceId, String clientIp,
                          Instant connectedAt, Instant lastSeen) {
        this.connectionId = connectionId;
        this.deviceId = deviceId;
        this.clientIp = clientIp;
        this.connectedAt = connectedAt;
        this.lastSeen = lastSeen;
    }

    public void updateLastSeen(Clock clock) {
        this.lastSeen = clock.instant();
    }

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

    @Override
    public String toString() {
        return "ConnectionInfo{" +
                "connectionId='" + connectionId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", connectedAt=" + connectedAt +
                ", lastSeen=" + lastSeen +
                '}';
    }
}