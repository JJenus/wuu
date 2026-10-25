package com.surf.tracker.core.domain.entity;

import com.surf.tracker.core.domain.enums.CommandStatus;
import com.surf.tracker.core.domain.enums.CommandType;
import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_commands")
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "command_id")
    private Long commandId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", length = 50, nullable = false)
    private CommandType commandType;

    @Column(name = "command_data", columnDefinition = "TEXT", nullable = false)
    private String commandData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CommandStatus status = CommandStatus.PENDING;

    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "initiated_by", length = 100)
    private String initiatedBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.EPOCH;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = TimeProvider.now();
    }

    // Business methods
    public void markAsSent() {
        this.status = CommandStatus.SENT;
        this.sentAt = TimeProvider.now();
    }

    public void markAsDelivered(String response) {
        this.status = CommandStatus.DELIVERED;
        this.responseData = response;
        this.respondedAt = TimeProvider.now();
    }

    public void markAsFailed(String error) {
        this.status = CommandStatus.FAILED;
        this.errorMessage = error;
        this.respondedAt = TimeProvider.now();
    }

    public void markAsTimeout() {
        this.status = CommandStatus.TIMEOUT;
        this.respondedAt = TimeProvider.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean canRetry() {
        return this.status == CommandStatus.FAILED ||
                this.status == CommandStatus.TIMEOUT &&
                        this.retryCount < this.maxRetries;
    }

    // Getters and Setters
    public Long getCommandId() { return commandId; }
    public void setCommandId(Long commandId) { this.commandId = commandId; }

    public Tracker getTracker() { return tracker; }
    public void setTracker(Tracker tracker) { this.tracker = tracker; }

    public CommandType getCommandType() { return commandType; }
    public void setCommandType(CommandType commandType) { this.commandType = commandType; }

    public String getCommandData() { return commandData; }
    public void setCommandData(String commandData) { this.commandData = commandData; }

    public CommandStatus getStatus() { return status; }
    public void setStatus(CommandStatus status) { this.status = status; }

    public String getResponseData() { return responseData; }
    public void setResponseData(String responseData) { this.responseData = responseData; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // Additional getters/setters
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
}