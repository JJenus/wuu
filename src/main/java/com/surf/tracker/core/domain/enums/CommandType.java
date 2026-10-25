package com.surf.tracker.core.domain.enums;

public enum CommandType {
    // GT06 Protocol Commands
    FUEL_CUT("Cut fuel supply"),
    FUEL_RESTORE("Restore fuel supply"),
    ENGINE_START("Start engine"),
    ENGINE_STOP("Stop engine"),
    SET_GEOFENCE("Configure geofence"),
    SET_OVERSPEED_LIMIT("Set speed limit"),
    SET_HEARTBEAT_INTERVAL("Set heartbeat interval"),
    SET_IP_PORT("Configure server IP/port"),
    SET_APN("Configure APN settings"),
    REBOOT_DEVICE("Reboot device"),
    FACTORY_RESET("Factory reset"),
    READ_STATUS("Read device status"),
    UPDATE_FIRMWARE("Update firmware"),

    // General Commands
    LOCATION_REQUEST("Request immediate location"),
    HISTORY_REQUEST("Request location history"),
    SET_WORKING_MODE("Set working mode"),
    SET_ALARM_MODE("Set alarm mode"),
    SET_CENTER_NUMBER("Set center phone number"),
    SET_ADMIN_NUMBER("Set admin numbers"),
    CHANGE_PASSWORD("Change device password");

    private final String description;

    CommandType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
