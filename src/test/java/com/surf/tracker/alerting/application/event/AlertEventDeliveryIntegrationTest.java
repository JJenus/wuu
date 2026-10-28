package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.application.service.AlertService;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.domain.enums.AlertSeverity;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.core.domain.entity.Vehicle;
import com.surf.tracker.main.config.AppConfig;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:alert_event_delivery;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AlertEventDeliveryIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AlertEventDeliveryIntegrationTest {
    @Configuration
    @EntityScan(basePackageClasses = {Vehicle.class, TrackerAlert.class})
    @EnableJpaRepositories(basePackages = {"com.surf.tracker.core.infrastructure.repository",
        "com.surf.tracker.alerting.infrastructure.repository"})
    @Import({AlertService.class, AlertService.AlertQueryService.class, AlertCreationEventHandler.class, AlertEventOutbox.class})
    static class Config {
        @Bean Clock clock() { return new TestClock(); }
        @Bean EventPublisher publisher() { return mock(EventPublisher.class); }
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() { return new AppConfig().objectMapper(); }
    }

    @Autowired AlertCreationEventHandler handler;
    @Autowired EventPublisher publisher;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired AlertEventOutbox outbox;

    @BeforeEach
    void setUp() {
        reset(publisher);
        ((TestClock) clock).current = Instant.parse("2026-01-01T12:00:00Z");
        jdbc.update("delete from alert_event_outbox");
        jdbc.update("delete from tracker_alerts");
        jdbc.update("delete from vehicles");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleId("replay-vehicle");
            vehicle.setOrganizationId(11L);
            entities.persist(vehicle);
        });
    }

    @AfterEach
    void clearContext() { TenantContext.clear(); }

    @Test
    void handleAlertEvent_brokerFailureThenRedelivery_persistsOnlyOneAlert() {
        // given: actual commits, not a mocked persistence counter.
        var event = new AlertDetectedEvent(clock, "speed-rule", AlertType.OVERSPEED,
            "replay-vehicle", "Speed exceeded", AlertSeverity.WARNING, null);
        doThrow(new RuntimeException("broker unavailable")).when(publisher).publish(any());
        // when: retries must not turn one source event into multiple committed alerts.
        for (int delivery = 0; delivery < 2; delivery++) {
            try {
                handler.handleAlertEvent(event);
            } catch (RuntimeException unavailable) {
                assertThat(unavailable).hasMessage("broker unavailable");
            }
        }
        // then
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id = ?",
            Long.class, "replay-vehicle")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox", Long.class)).isEqualTo(1L);
        verifyNoInteractions(publisher);
        assertThat(TenantContext.getCurrentOrgId()).isNull();
    }

    @Test
    void relayPending_brokerFailureThenRetry_preservesCommittedIdentityAndPayload() throws Exception {
        // given
        var event = event();
        handler.handleAlertEvent(event);
        var mapper = new AppConfig().objectMapper();
        String payload = jdbc.queryForObject("select payload from alert_event_outbox", String.class);
        var expected = mapper.readValue(payload, com.surf.tracker.shared.events.AlertRaisedEvent.class);
        doThrow(new RuntimeException("broker unavailable")).doNothing().when(publisher).publish(any());
        // when
        outbox.relayPending();
        // then: committed alert survives, attempt retained; early polling cannot bypass backoff.
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select attempts from alert_event_outbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select published_at from alert_event_outbox", Object.class)).isNull();
        outbox.relayPending();
        verify(publisher, times(1)).publish(any());
        // when: retry uses the serialized original event, not a new ID/time.
        ((TestClock) clock).current = clock.instant().plusSeconds(2);
        outbox.relayPending();
        // then
        var published = org.mockito.ArgumentCaptor.forClass(com.surf.tracker.shared.pubsub.DomainEvent.class);
        verify(publisher, times(2)).publish(published.capture());
        published.getAllValues().forEach(value -> assertThat(value).usingRecursiveComparison().isEqualTo(expected));
        assertThat(jdbc.queryForObject("select published_at from alert_event_outbox", Object.class)).isNotNull();
        handler.handleAlertEvent(event);
        outbox.relayPending();
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isEqualTo(1L);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void handleAlertEvent_outerRollback_removesBothAlertAndPublicationIntent() {
        // given / when
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            handler.handleAlertEvent(event());
            status.setRollbackOnly();
        });
        // then
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox", Long.class)).isZero();
        verifyNoInteractions(publisher);
    }

    @Test
    void relayPending_sendSucceededButTransactionRolledBack_retriesSameIdentity() {
        // given: broker side effect cannot roll back with the database transaction.
        var source = event();
        handler.handleAlertEvent(source);
        // when: simulate failure after transport success but before the database commit.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            outbox.relayPending();
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select published_at from alert_event_outbox", Object.class)).isNull();
        outbox.relayPending();
        // then: at-least-once means two sends, but exactly the same event and one alert.
        var sends = org.mockito.ArgumentCaptor.forClass(com.surf.tracker.shared.pubsub.DomainEvent.class);
        verify(publisher, times(2)).publish(sends.capture());
        assertThat(sends.getAllValues().get(1)).usingRecursiveComparison().isEqualTo(sends.getAllValues().getFirst());
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select published_at from alert_event_outbox", Object.class)).isNotNull();
    }

    @Test
    void relayPending_corruptStoredPayload_retainsFailedIntentAndContinuesHealthyWork() {
        // given: a damaged historical intent must not silently disappear or block the whole batch.
        var broken = event();
        var healthy = event();
        handler.handleAlertEvent(broken);
        handler.handleAlertEvent(healthy);
        jdbc.update("update alert_event_outbox set payload = ? where source_event_id = ?", "{broken", broken.getEventId());
        // when
        outbox.relayPending();
        // then
        assertThat(jdbc.queryForObject("select attempts from alert_event_outbox where source_event_id = ?",
            Integer.class, broken.getEventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select published_at from alert_event_outbox where source_event_id = ?",
            Object.class, broken.getEventId())).isNull();
        assertThat(jdbc.queryForObject("select published_at from alert_event_outbox where source_event_id = ?",
            Object.class, healthy.getEventId())).isNotNull();
        verify(publisher, times(1)).publish(any());
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isEqualTo(2L);
    }

    @Test
    void handleAlertEvent_concurrentRedelivery_commitsOneAlertAndIntent() throws Exception {
        // given
        var event = event();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Void> deliver = () -> {
                start.await();
                handler.handleAlertEvent(event);
                assertThat(TenantContext.getCurrentOrgId()).isNull();
                return null;
            };
            // when
            var first = executor.submit(deliver);
            var second = executor.submit(deliver);
            start.countDown();
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
            // then
            assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox", Long.class)).isEqualTo(1L);
            verifyNoInteractions(publisher);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void handleAlertEvent_reusedSourceForAnotherTenant_rejectsWithoutCreatingForeignAlert() {
        // given
        var original = event();
        handler.handleAlertEvent(original);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Vehicle vehicle = new Vehicle();
            vehicle.setVehicleId("foreign-vehicle");
            vehicle.setOrganizationId(22L);
            entities.persist(vehicle);
        });
        var collision = new AlertDetectedEvent(original.getEventId(), original.getOccurredOn(),
            original.getRuleKey(), original.getAlertType(), "foreign-vehicle", original.getMessage(),
            original.getSeverity(), original.getAlertTimestamp(), original.getLocation(), original.getCooldownMinutes());
        // when / then
        assertThatThrownBy(() -> handler.handleAlertEvent(collision)).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Source event identity belongs to another vehicle or organization");
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox", Long.class)).isEqualTo(1L);
        assertThat(TenantContext.getCurrentOrgId()).isNull();
    }

    private AlertDetectedEvent event() {
        return new AlertDetectedEvent(clock, "speed-rule", AlertType.OVERSPEED,
            "replay-vehicle", "Speed exceeded", AlertSeverity.WARNING, null);
    }

    private static class TestClock extends Clock {
        private Instant current = Instant.parse("2026-01-01T12:00:00Z");
        @Override public Instant instant() { return current; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
    }
}
