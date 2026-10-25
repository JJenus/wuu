package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.shared.util.TimeProvider;
import com.surf.tracker.userauth.domain.enums.FailureReason;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.EPOCH;

    public static LoginAttempt success(User user, String ipAddress, String userAgent) {
        LoginAttempt a = new LoginAttempt();
        a.user = user;
        a.ipAddress = ipAddress;
        a.userAgent = userAgent;
        a.success = true;
        a.attemptedAt = TimeProvider.now();
        return a;
    }

    public static LoginAttempt failure(User user, String ipAddress, String userAgent, FailureReason reason) {
        LoginAttempt a = new LoginAttempt();
        a.user = user;
        a.ipAddress = ipAddress;
        a.userAgent = userAgent;
        a.success = false;
        a.attemptedAt = TimeProvider.now();
        a.failureReason = reason == null ? null : reason.name();
        return a;
    }

    public boolean isFailure() {
        return !success;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
