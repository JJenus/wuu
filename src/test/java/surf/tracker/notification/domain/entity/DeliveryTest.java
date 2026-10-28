package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTest {

    @Test
    void markSent_updatesStatusAndSentAt() {
        Delivery delivery = createDelivery();
        Instant before = Instant.now();

        delivery.markSent();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isAfterOrEqualTo(before);
        assertThat(delivery.getNextRetryAt()).isNull();
    }

    @Test
    void markDelivered_updatesStatusAndDeliveredAt() {
        Delivery delivery = createDelivery();
        delivery.markSent();

        delivery.markDelivered();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isNotNull();
    }

    @Test
    void markFailed_setsStatusAndIncrementsAttempts() {
        Delivery delivery = createDelivery();

        delivery.markFailed("SMTP error", ErrorType.TRANSIENT);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getLastError()).isEqualTo("SMTP error");
        assertThat(delivery.getLastErrorType()).isEqualTo(ErrorType.TRANSIENT);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void markExhausted_setsExhaustedStatus() {
        Delivery delivery = createDelivery();
        delivery.setNextRetryAt(Instant.now().plusSeconds(60));

        delivery.markExhausted();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.EXHAUSTED);
        assertThat(delivery.getNextRetryAt()).isNull();
    }

    @Test
    void markSkipped_setsSkippedStatus() {
        Delivery delivery = createDelivery();

        delivery.markSkipped();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
    }

    @Test
    void canRetry_statusFailedTransientBelowMax_returnsTrue() {
        Delivery delivery = createDelivery();
        delivery.markFailed("error", ErrorType.TRANSIENT);
        delivery.setAttemptCount(1);
        delivery.setMaxAttempts(5);

        assertThat(delivery.canRetry()).isTrue();
    }

    @Test
    void canRetry_statusFailedPermanent_returnsFalse() {
        Delivery delivery = createDelivery();
        delivery.markFailed("bad token", ErrorType.PERMANENT);

        assertThat(delivery.canRetry()).isFalse();
    }

    @Test
    void canRetry_atMaxAttempts_returnsFalse() {
        Delivery delivery = createDelivery();
        delivery.markFailed("error", ErrorType.TRANSIENT);
        delivery.setAttemptCount(5);
        delivery.setMaxAttempts(5);

        assertThat(delivery.canRetry()).isFalse();
    }

    @Test
    void canRetry_statusNotFailed_returnsFalse() {
        Delivery delivery = createDelivery();
        delivery.markSent();

        assertThat(delivery.canRetry()).isFalse();
    }

    @Test
    void scheduleNextRetry_canRetry_setsNextRetryAt() {
        Delivery delivery = createDelivery();
        delivery.markFailed("error", ErrorType.TRANSIENT);
        delivery.setAttemptCount(1);
        delivery.setMaxAttempts(5);

        Instant before = Instant.now();
        delivery.scheduleNextRetry();

        assertThat(delivery.getNextRetryAt()).isAfterOrEqualTo(before);
    }

    @Test
    void scheduleNextRetry_cannotRetry_marksExhausted() {
        Delivery delivery = createDelivery();
        delivery.markFailed("bad token", ErrorType.PERMANENT);
        delivery.setAttemptCount(5);
        delivery.setMaxAttempts(5);

        delivery.scheduleNextRetry();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.EXHAUSTED);
    }

    @Test
    void computeNextRetryDelay_attemptZero_returnsBaseSeconds() {
        var delay = Delivery.computeNextRetryDelay(0);
        assertThat(delay.getSeconds()).isBetween(0L, 1L);
    }

    @Test
    void computeNextRetryDelay_largeAttempt_capsAt300() {
        var delay = Delivery.computeNextRetryDelay(20);
        assertThat(delay.getSeconds()).isLessThanOrEqualTo(360L);
    }

    private Delivery createDelivery() {
        Delivery delivery = new Delivery();
        delivery.setChannel(NotificationChannel.EMAIL);
        delivery.setRecipient("user@example.com");
        delivery.setTemplateId("TPL_001");
        delivery.setTitle("Hello");
        delivery.setMessage("World");
        delivery.setMaxAttempts(5);
        delivery.setAttemptCount(0);
        return delivery;
    }
}