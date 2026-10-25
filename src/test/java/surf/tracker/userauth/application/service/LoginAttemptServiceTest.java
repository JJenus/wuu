package com.surf.tracker.userauth.application.service;

import com.surf.tracker.userauth.infrastructure.repository.LoginAttemptRepository;
import com.surf.tracker.userauth.infrastructure.security.LoginPolicyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final Instant FIXED = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    @Mock private LoginAttemptRepository loginAttemptRepository;
    private LoginPolicyConfig policy;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        policy = new LoginPolicyConfig();
        policy.setMaxFailedAttempts(5);
        policy.setFailedAttemptsWindow(Duration.ofMinutes(30));
        service = new LoginAttemptService(loginAttemptRepository, policy, FIXED_CLOCK);
    }

    @Test
    void isRateLimited_belowThreshold_returnsFalse() {
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any()))
            .thenReturn(3L);

        assertThat(service.isRateLimited(1L)).isFalse();
    }

    @Test
    void isRateLimited_atThreshold_returnsTrue() {
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any()))
            .thenReturn(5L);

        assertThat(service.isRateLimited(1L)).isTrue();
    }

    @Test
    void isRateLimited_nullUserId_returnsFalse() {
        assertThat(service.isRateLimited(null)).isFalse();
    }

    @Test
    void isIpRateLimited_belowThreshold_returnsFalse() {
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndAttemptedAtAfter(anyString(), any()))
            .thenReturn(2L);

        assertThat(service.isIpRateLimited("1.2.3.4")).isFalse();
    }

    @Test
    void isIpRateLimited_atThreshold_returnsTrue() {
        when(loginAttemptRepository.countByIpAddressAndSuccessFalseAndAttemptedAtAfter(anyString(), any()))
            .thenReturn(5L);

        assertThat(service.isIpRateLimited("1.2.3.4")).isTrue();
    }

    @Test
    void isIpRateLimited_blankIp_returnsFalse() {
        assertThat(service.isIpRateLimited(null)).isFalse();
        assertThat(service.isIpRateLimited("")).isFalse();
        assertThat(service.isIpRateLimited("   ")).isFalse();
    }

    @Test
    void recentFailedAttempts_returnsCount() {
        when(loginAttemptRepository.countByUserIdAndSuccessFalseAndAttemptedAtAfter(anyLong(), any()))
            .thenReturn(7L);

        assertThat(service.recentFailedAttempts(1L)).isEqualTo(7L);
    }

    @Test
    void recentFailedAttempts_nullUserId_returnsZero() {
        assertThat(service.recentFailedAttempts(null)).isZero();
    }
}
