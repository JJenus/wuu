package com.surf.tracker.notification.domain.enums;

public enum DevicePlatform {
    IOS("iOS"),
    ANDROID("Android"),
    WEB("Web");

    private final String displayName;

    DevicePlatform(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
