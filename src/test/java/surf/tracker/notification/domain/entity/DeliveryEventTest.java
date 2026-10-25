package com.surf.tracker.notification.domain.entity;

import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryEventTest {

    @Test
    void created_factoryBuildsEvent() {
        Instant now = Instant.now();
        DeliveryEvent event = DeliveryEvent.created("del_001", "metadata", now);

        assertThat(event.getDeliveryId()).isEqualTo("del_001");
        assertThat(event.getEventType()).isEqualTo(DeliveryEventType.CREATED);
        assertThat(event.getMetadata()).isEqualTo("metadata");
        assertThat(event.getOccurredAt()).isEqualTo(now);
    }

    @Test
    void sent_factoryBuildsSentEvent() {
        DeliveryEvent event = DeliveryEvent.sent("d1", null, Instant.now());
        assertThat(event.getEventType()).isEqualTo(DeliveryEventType.SENT);
    }

    @Test
    void delivered_factoryBuildsDeliveredEvent() {
        DeliveryEvent event = DeliveryEvent.delivered("d1", null, Instant.now());
        assertThat(event.getEventType()).isEqualTo(DeliveryEventType.DELIVERED);
    }

    @Test
    void failed_factoryBuildsFailedEvent() {
        DeliveryEvent event = DeliveryEvent.failed("d1", "error", Instant.now());
        assertThat(event.getEventType()).isEqualTo(DeliveryEventType.FAILED);
        assertThat(event.getMetadata()).isEqualTo("error");
    }

    @Test
    void retryScheduled_factoryBuildsRetryEvent() {
        DeliveryEvent event = DeliveryEvent.retryScheduled("d1", "next at +60s", Instant.now());
        assertThat(event.getEventType()).isEqualTo(DeliveryEventType.RETRY_SCHEDULED);
    }

    @Test
    void exhausted_factoryBuildsExhaustedEvent() {
        DeliveryEvent event = DeliveryEvent.exhausted("d1", "max attempts", Instant.now());
        assertThat(event.getEventType()).isEqualTo(DeliveryEventType.EXHAUSTED);
    }

    @Test
    void deliveryEvent_hasNoPublicMutators() {
        Method[] methods = DeliveryEvent.class.getDeclaredMethods();
        long setters = Arrays.stream(methods)
            .filter(m -> m.getName().startsWith("set"))
            .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
            .count();

        assertThat(setters).isZero();
    }
}