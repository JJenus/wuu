package com.surf.tracker.main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.core.domain.FuelCutRequestedEvent;
import com.surf.tracker.main.config.AppConfig;
import com.surf.tracker.main.config.JmsConfig;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.*;
import com.surf.tracker.shared.pubsub.DomainEvent;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.support.converter.MessageConverter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JmsContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void listenerFactories_globalTopicDefault_preserveQueueAndTopicBoundaries() {
        // given
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JmsAutoConfiguration.class))
            .withUserConfiguration(JmsConfig.class)
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(ObjectMapper.class, () -> new AppConfig().objectMapper())
            .withPropertyValues("spring.jms.pub-sub-domain=true")
            .run(context -> {
                // when
                var queue = context.getBean("queueJmsListenerContainerFactory", DefaultJmsListenerContainerFactory.class)
                    .createListenerContainer(endpoint());
                var topic = context.getBean("topicJmsListenerContainerFactory", DefaultJmsListenerContainerFactory.class)
                    .createListenerContainer(endpoint());
                // then
                assertThat(queue.isPubSubDomain()).isFalse();
                assertThat(topic.isPubSubDomain()).isTrue();
            });
    }

    @ParameterizedTest
    @MethodSource("events")
    void convert_concreteEvent_roundTripsIdentityTimeAndPayload(DomainEvent event) throws Exception {
        // given: the actual application mapper and JMS converter, with transport mocked only
        MessageConverter converter = new JmsConfig().jacksonJmsMessageConverter(new AppConfig().objectMapper());
        Session session = mock(Session.class);
        TextMessage message = mock(TextMessage.class);
        Map<String, String> headers = new HashMap<>();
        when(session.createTextMessage(anyString())).thenAnswer(invocation -> {
            when(message.getText()).thenReturn(invocation.getArgument(0));
            return message;
        });
        doAnswer(invocation -> {
            headers.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(message).setStringProperty(anyString(), anyString());
        when(message.getStringProperty(anyString())).thenAnswer(invocation -> headers.get(invocation.getArgument(0)));
        // when
        Object restored = converter.fromMessage(converter.toMessage(event, session));
        // then
        assertThat(restored).usingRecursiveComparison().isEqualTo(event);
    }

    static Stream<DomainEvent> events() {
        var location = new LocationPoint(1, 2, 50, CLOCK.instant());
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return Stream.of(
            new LocationDataEvent(CLOCK, id, "device", location, "GT06", Map.of("source", "fixture")),
            new VehicleUpdatedEvent(CLOCK, id, "vehicle", 11L, location, Map.of("source", "fixture")),
            new AlertDetectedEvent(CLOCK, "speed", AlertType.OVERSPEED, "vehicle", "Speed exceeded", AlertSeverity.WARNING, location),
            new AlertRaisedEvent(CLOCK, "42", "speed", "vehicle", "OVERSPEED", "WARNING", "Speed exceeded", CLOCK.instant(), 1.0, 2.0, 50.0, Map.of()),
            new NotificationSentEvent(CLOCK, "notification", "42", "IN_APP", "1", true, null),
            new FuelCutRequestedEvent(CLOCK, "vehicle", "device"));
    }

    private SimpleJmsListenerEndpoint endpoint() {
        var endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setDestination("contract.fixture");
        endpoint.setMessageListener(message -> { });
        return endpoint;
    }
}
