package com.surf.tracker.shared.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.mockito.Mockito.*;

class ArtemisEventPublisherTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void publish_durableWork_usesQueueInsteadOfTransientTopic() {
        var topic = mock(JmsTemplate.class);
        var queue = mock(JmsTemplate.class);
        var event = new VehicleUpdatedEvent(clock, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "vehicle", 12L, new LocationPoint(1, 2, 24, clock.instant()), null);
        new ArtemisEventPublisher(topic, queue, mapper).publish(event);
        verify(queue).convertAndSend("tracking.work.vehicleupdatedevent", event);
        verifyNoInteractions(topic);
    }

    @Test
    void publish_legacyBroadcast_retainsExistingTopicContract() {
        var topic = mock(JmsTemplate.class);
        var queue = mock(JmsTemplate.class);
        var event = new LocationDataEvent(clock, "8168000008", new LocationPoint(1, 2, 24, clock.instant()), "AUTOSEEKER");
        new ArtemisEventPublisher(topic, queue, mapper).publish(event);
        verify(topic).convertAndSend("tracking.events.locationdataevent", event);
        verifyNoInteractions(queue);
    }
}
