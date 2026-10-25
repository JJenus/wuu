package com.surf.tracker.core.api.dto;

import com.surf.tracker.core.domain.enums.CommandType;

public class CreateDeviceCommandRequest {
    private String trackerId;
    private CommandType commandType;
    private String commandData;
    private String initiatedBy;

    public String getTrackerId() { return trackerId; }
    public void setTrackerId(String trackerId) { this.trackerId = trackerId; }

    public CommandType getCommandType() { return commandType; }
    public void setCommandType(CommandType commandType) { this.commandType = commandType; }

    public String getCommandData() { return commandData; }
    public void setCommandData(String commandData) { this.commandData = commandData; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}
