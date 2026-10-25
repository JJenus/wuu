package com.surf.tracker.userauth.domain.entity;

import com.surf.tracker.userauth.domain.enums.FailureReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LoginAttemptTest {

    @Test
    void success_createsSuccessAttempt() {
        User user = new User();
        user.setId(1L);

        LoginAttempt attempt = LoginAttempt.success(user, "192.168.1.1", "Mozilla/5.0");

        assertThat(attempt.isSuccess()).isTrue();
        assertThat(attempt.getUser()).isEqualTo(user);
        assertThat(attempt.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(attempt.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(attempt.getFailureReason()).isNull();
        assertThat(attempt.isFailure()).isFalse();
    }

    @Test
    void failure_createsFailureAttempt() {
        User user = new User();
        user.setId(1L);

        LoginAttempt attempt = LoginAttempt.failure(user, "10.0.0.1", "curl/7.0", FailureReason.INVALID_CREDENTIALS);

        assertThat(attempt.isSuccess()).isFalse();
        assertThat(attempt.getUser()).isEqualTo(user);
        assertThat(attempt.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(attempt.getFailureReason()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(attempt.isFailure()).isTrue();
    }

    @Test
    void failure_unknownUser_nullUserId() {
        LoginAttempt attempt = LoginAttempt.failure(null, "1.2.3.4", null, FailureReason.USER_NOT_FOUND);

        assertThat(attempt.getUser()).isNull();
        assertThat(attempt.getIpAddress()).isEqualTo("1.2.3.4");
        assertThat(attempt.getFailureReason()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void failure_nullReason_failureReasonIsNull() {
        LoginAttempt attempt = LoginAttempt.failure(null, "1.2.3.4", null, null);

        assertThat(attempt.getFailureReason()).isNull();
    }
}
