package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationHubTest {

    @Test
    void addDelivery_associatesWithHub() {
        NotificationHub hub = new NotificationHub();
        hub.setNotificationId("n_001");
        hub.setUserId("user_1");

        Delivery delivery = createDelivery(NotificationChannel.EMAIL);

        hub.addDelivery(delivery);

        assertThat(hub.getDeliveries()).hasSize(1);
        assertThat(delivery.getNotificationHub()).isSameAs(hub);
    }

    @Test
    void recalculateStatus_noDeliveries_returnsCreated() {
        NotificationHub hub = new NotificationHub();
        hub.recalculateStatus();
        assertThat(hub.getStatus()).isEqualTo(NotificationStatus.CREATED);
    }

    @Test
    void recalculateStatus_allSent_returnsCompleted() {
        NotificationHub hub = new NotificationHub();
        Delivery d1 = createDelivery(NotificationChannel.EMAIL);
        Delivery d2 = createDelivery(NotificationChannel.SMS);
        d1.markSent();
        d2.markSent();
        hub.addDelivery(d1);
        hub.addDelivery(d2);

        hub.recalculateStatus();

        assertThat(hub.getStatus()).isEqualTo(NotificationStatus.COMPLETED);
    }

    @Test
    void recalculateStatus_mixedSentAndFailed_returnsPartialFailure() {
        NotificationHub hub = new NotificationHub();
        Delivery d1 = createDelivery(NotificationChannel.EMAIL);
        Delivery d2 = createDelivery(NotificationChannel.SMS);
        d1.markSent();
        d2.markFailed("err", ErrorType.TRANSIENT);
        hub.addDelivery(d1);
        hub.addDelivery(d2);

        hub.recalculateStatus();

        assertThat(hub.getStatus()).isEqualTo(NotificationStatus.PARTIAL_FAILURE);
    }

    @Test
    void recalculateStatus_allFailed_returnsFailed() {
        NotificationHub hub = new NotificationHub();
        Delivery d1 = createDelivery(NotificationChannel.EMAIL);
        Delivery d2 = createDelivery(NotificationChannel.SMS);
        d1.markFailed("err", ErrorType.TRANSIENT);
        d2.markExhausted();
        hub.addDelivery(d1);
        hub.addDelivery(d2);

        hub.recalculateStatus();

        assertThat(hub.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void recalculateStatus_allPending_returnsProcessing() {
        NotificationHub hub = new NotificationHub();
        Delivery d1 = createDelivery(NotificationChannel.EMAIL);
        hub.addDelivery(d1);

        hub.recalculateStatus();

        assertThat(hub.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    @Test
    void markProcessing_setsProcessingStatus() {
        NotificationHub hub = new NotificationHub();
        hub.markProcessing();
        assertThat(hub.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    private Delivery createDelivery(NotificationChannel channel) {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("d_" + channel.name());
        delivery.setChannel(channel);
        delivery.setRecipient("user_1");
        delivery.setTemplateId("TPL_001");
        delivery.setTitle("Title");
        delivery.setMessage("Body");
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setMaxAttempts(5);
        return delivery;
    }
}