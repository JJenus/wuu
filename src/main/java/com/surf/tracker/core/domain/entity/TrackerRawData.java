package com.surf.tracker.core.domain.entity;

import com.surf.tracker.shared.util.TimeProvider;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tracker_raw_data")
public class TrackerRawData {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "raw_data_id")
    private Long rawDataId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;
    
    @Column(name = "raw_message", columnDefinition = "TEXT", nullable = false)
    private String rawMessage;
    
    @Column(name = "protocol", length = 20)
    private String protocol;
    
    @Column(name = "source_ip", length = 45)
    private String sourceIp;
    
    @Column(name = "received_at")
    private Instant receivedAt = Instant.EPOCH;
    
    @Column(name = "processed")
    private Boolean processed = false;
    
    @Column(name = "processing_error", columnDefinition = "TEXT")
    private String processingError;
    
    @Column(name = "parsed_data", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String parsedData;
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.EPOCH;
    
    // Getters and Setters
    public Long getRawDataId() { return rawDataId; }
    public void setRawDataId(Long rawDataId) { this.rawDataId = rawDataId; }
    
    public Tracker getTracker() { return tracker; }
    public void setTracker(Tracker tracker) { this.tracker = tracker; }
    
    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }
    
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    
    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean processed) { this.processed = processed; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }
    
    public String getParsedData() { return parsedData; }
    public void setParsedData(String parsedData) { this.parsedData = parsedData; }
}
