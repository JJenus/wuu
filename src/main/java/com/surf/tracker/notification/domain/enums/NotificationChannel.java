package com.surf.tracker.notification.domain.enums;

public enum NotificationChannel {
    WEBSOCKET("WebSocket", "Real-time browser notifications"),
    EMAIL("Email", "Email notifications"),
    SMS("SMS", "Text message notifications"),
    MOBILE_PUSH("Mobile Push", "Push notifications to mobile apps"),
    IN_APP("In-App", "In-application notifications"),
    SLACK("Slack", "Slack channel notifications"),
    TEAMS("Teams", "Microsoft Teams notifications");
    
    private final String displayName;
    private final String description;
    
    NotificationChannel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isRealTime() {
        return this == WEBSOCKET || this == MOBILE_PUSH;
    }
}
