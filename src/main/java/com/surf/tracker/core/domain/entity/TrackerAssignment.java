package com.surf.tracker.core.domain.entity;

import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tracker_assignments")
public class TrackerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", length = 100)
    private String assignedBy; // User/admin who made assignment

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by", length = 100)
    private String removedBy;

    @Column(name = "removal_reason", length = 200)
    private String removalReason;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "notes", length = 500)
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = TimeProvider.now();
        }
    }

    // Business methods
    public void deactivateAssignment(String removedBy, String reason) {
        this.isActive = false;
        this.removedAt = TimeProvider.now();
        this.removedBy = removedBy;
        this.removalReason = reason;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public Tracker getTracker() { return tracker; }
    public void setTracker(Tracker tracker) { this.tracker = tracker; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public Instant getRemovedAt() { return removedAt; }
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }

    public String getRemovedBy() { return removedBy; }
    public void setRemovedBy(String removedBy) { this.removedBy = removedBy; }

    public String getRemovalReason() { return removalReason; }
    public void setRemovalReason(String removalReason) { this.removalReason = removalReason; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}