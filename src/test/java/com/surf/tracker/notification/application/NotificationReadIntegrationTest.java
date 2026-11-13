package com.surf.tracker.notification.application;

import com.surf.tracker.notification.domain.entity.*;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.shared.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ContextConfiguration;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notification_reads;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = NotificationReadIntegrationTest.Config.class)
class NotificationReadIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    @Autowired EntityManager em;
    @Autowired NotificationQueryService queries;
    @Autowired NotificationCommandService commands;
    @Autowired DeliveryEventRepository events;

    @Configuration
    @EntityScan(basePackageClasses = Delivery.class)
    @EnableJpaRepositories(basePackageClasses = DeliveryRepository.class)
    @Import({NotificationQueryService.class, NotificationCommandService.class})
    static class Config {
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @BeforeEach void setUp() {
        TenantContext.setCurrentUserId(1L);
        TenantContext.setCurrentOrgId(10L);
        delivery("a-read", "1", "10");
        delivery("b-unread", "1", "10");
        delivery("c-unread", "1", "10");
        delivery("foreign-user", "2", "10");
        delivery("foreign-tenant", "1", "20");
        em.persist(DeliveryEvent.opened("a-read", "Read fixture", NOW));
        // Delivery events other than OPENED must not hide unread deliveries.
        em.persist(DeliveryEvent.delivered("b-unread", "Provider receipt", NOW));
        em.flush();
        em.clear();
    }

    private void delivery(String id, String user, String tenant) {
        NotificationHub hub = new NotificationHub();
        hub.setNotificationId("hub-" + id);
        hub.setUserId(user);
        hub.setTenantId(tenant);
        hub.setTemplateId("template");
        Delivery delivery = new Delivery();
        delivery.setDeliveryId(id);
        delivery.setChannel(NotificationChannel.EMAIL);
        delivery.setRecipient("private@example.test");
        delivery.setTemplateId("template");
        delivery.setStatus(DeliveryStatus.DELIVERED);
        hub.addDelivery(delivery);
        em.persist(hub);
    }

    @AfterEach void clearContext() { TenantContext.clear(); }

    @Test void getUserNotifications_unreadOnly_filtersBeforePaginationAndCounts() {
        // given / when
        var first = queries.getUserNotifications("1", true, PageRequest.of(0, 1, Sort.by("deliveryId")));
        var second = queries.getUserNotifications("1", true, PageRequest.of(1, 1, Sort.by("deliveryId")));
        // then
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(response -> response.getDeliveryId()).containsExactly("b-unread");
        assertThat(second.getContent()).extracting(response -> response.getDeliveryId()).containsExactly("c-unread");
        assertThat(queries.getUserNotifications("1", false, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(3);
    }

    @Test void markAsRead_persistedOpenedEvent_removesOnlyThatDeliveryFromUnreadResults() {
        // when
        commands.markAsRead("b-unread");
        commands.markAsRead("b-unread"); // Existing duplicate audit events must not duplicate/count rows.
        em.flush();
        em.clear();
        // then
        assertThat(queries.getUserNotifications("1", true, PageRequest.of(0, 20)).getContent())
            .extracting(response -> response.getDeliveryId()).containsExactly("c-unread");
        assertThat(queries.getUserNotifications("1", false, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(3);
    }

    @Test void markAllAsRead_ownedDeliveries_returnsEmptyUnreadPageAndPreservesOtherScopes() {
        // when
        commands.markAllAsRead("1");
        em.flush();
        em.clear();
        // then
        assertThat(queries.getUserNotifications("1", true, PageRequest.of(0, 20))).isEmpty();
        TenantContext.setCurrentOrgId(20L);
        assertThat(queries.getUserNotifications("1", true, PageRequest.of(0, 20)).getContent())
            .extracting(response -> response.getDeliveryId()).containsExactly("foreign-tenant");
        TenantContext.setCurrentOrgId(10L);
        TenantContext.setCurrentUserId(2L);
        assertThat(queries.getUserNotifications("2", true, PageRequest.of(0, 20)).getContent())
            .extracting(response -> response.getDeliveryId()).containsExactly("foreign-user");
    }

    @Test void getUserNotifications_foreignRecipientOrMissingContext_failsClosed() {
        assertThatThrownBy(() -> queries.getUserNotifications("2", true, PageRequest.of(0, 20)))
            .isInstanceOf(AccessDeniedException.class);
        TenantContext.clear();
        assertThatThrownBy(() -> queries.getUserNotifications("1", true, PageRequest.of(0, 20)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test void markAllAsRead_moreThanOneThousandDeliveries_marksEntireOwnedInboxWithoutDuplicateReadEvents() {
        // given: a large inbox, including an already-read record and unrelated scopes.
        for (int index = 0; index < 1505; index++) delivery("bulk-" + index, "1", "10");
        em.flush();
        em.clear();
        long unread = queries.getUserNotifications("1", true, PageRequest.of(0, 1)).getTotalElements();
        long before = events.count();
        // when
        commands.markAllAsRead("1");
        em.flush();
        em.clear();
        // then
        assertThat(queries.getUserNotifications("1", true, PageRequest.of(0, 20))).isEmpty();
        assertThat(events.count()).isEqualTo(before + unread);
        assertThat(events.countByDeliveryId("a-read")).isEqualTo(1);
        assertThat(events.countByDeliveryId("foreign-user")).isZero();
        assertThat(events.countByDeliveryId("foreign-tenant")).isZero();
        commands.markAllAsRead("1");
        em.flush();
        assertThat(events.count()).isEqualTo(before + unread);
    }

    @Test void markAllAsRead_emptyInboxOrDeniedCaller_doesNotCreateEvents() {
        // given
        long before = events.count();
        TenantContext.setCurrentUserId(3L);
        // when / then
        commands.markAllAsRead("3");
        assertThat(events.count()).isEqualTo(before);
        assertThatThrownBy(() -> commands.markAllAsRead("1")).isInstanceOf(AccessDeniedException.class);
        TenantContext.clear();
        assertThatThrownBy(() -> commands.markAllAsRead("1")).isInstanceOf(AccessDeniedException.class);
        assertThat(events.count()).isEqualTo(before);
    }
}
