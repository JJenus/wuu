package com.surf.tracker.userauth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "userauth.login")
public class LoginPolicyConfig {

    private int maxFailedAttempts = 5;
    private Duration failedAttemptsWindow = Duration.ofMinutes(30);
    private Duration lockoutDuration = Duration.ofMinutes(30);

    public int getMaxFailedAttempts() { return maxFailedAttempts; }
    public void setMaxFailedAttempts(int maxFailedAttempts) { this.maxFailedAttempts = maxFailedAttempts; }

    public Duration getFailedAttemptsWindow() { return failedAttemptsWindow; }
    public void setFailedAttemptsWindow(Duration failedAttemptsWindow) { this.failedAttemptsWindow = failedAttemptsWindow; }

    public Duration getLockoutDuration() { return lockoutDuration; }
    public void setLockoutDuration(Duration lockoutDuration) { this.lockoutDuration = lockoutDuration; }
}
