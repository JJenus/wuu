package com.surf.tracker.main;

import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.PasswordService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in: owns disposable Docker services; never connects to application data. */
@SpringBootTest(classes = TrackingEngineApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"userauth.jwt.secret=deployment-test-only-secret-at-least-thirty-two-bytes",
        "userauth.registration-enabled=false", "userauth.password.bcrypt-strength=4",
        "tracking.alert-outbox.poll-delay-ms=3600000"})
@ActiveProfiles("postgres")
@DirtiesContext
class PostgresDeploymentIT {
    private static final String PASSWORD = "test-only-database-password";
    private static final String USER_PASSWORD = "a-long-test-password";
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
        .withEnv("POSTGRES_DB", "tracker_test").withEnv("POSTGRES_USER", "tracker_test")
        .withEnv("POSTGRES_PASSWORD", PASSWORD).withExposedPorts(5432);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    private static final GenericContainer<?> ARTEMIS = new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.40.0-alpine"))
        .withEnv("ARTEMIS_USER", "tracker_test").withEnv("ARTEMIS_PASSWORD", PASSWORD)
        // Avoid the image's 2 GB default heap and startup disk benchmark on shared CI hosts.
        // Authentication, persistent journals, fsync and all application listeners stay enabled.
        // The generated profile uses -Xms512M; a smaller maximum makes the JVM exit.
        .withEnv("EXTRA_ARGS", "--http-host 0.0.0.0 --relax-jolokia --no-autotune --java-memory 512M")
        .withEnv("ANONYMOUS_LOGIN", "false").withExposedPorts(61616)
        .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*AMQ221007.*", 1))
        .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        Startables.deepStart(POSTGRES, REDIS, ARTEMIS).join();
        properties.add("spring.datasource.url", PostgresDeploymentIT::jdbcUrl);
        properties.add("spring.datasource.username", () -> "tracker_test");
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.artemis.broker-url", () -> "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
        properties.add("spring.artemis.user", () -> "tracker_test");
        properties.add("spring.artemis.password", () -> PASSWORD);
    }

    private static String jdbcUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/tracker_test";
    }

    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired PasswordService passwords;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired SessionRepository sessions;
    @Autowired RefreshTokenRepository tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired StringRedisTemplate redis;
    @Autowired @Qualifier("queueJmsTemplate") JmsTemplate queue;
    @Autowired @Qualifier("topicJmsTemplate") JmsTemplate topic;
    @Autowired jakarta.jms.ConnectionFactory messaging;
    @Autowired org.springframework.jms.support.converter.MessageConverter converter;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.surf.tracker.notification.application.NotificationOrchestrator notificationOrchestrator;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.surf.tracker.alerting.application.AlertingEngine alertingEngine;

    @Test
    void notification_missingRecipientPolicy_redeliversAndRetainsOriginalEventInBrokerDlq() {
        // given: no routing policy or external provider is fabricated/enabled by this fixture.
        String identity = "unroutable-alert-" + IDS.incrementAndGet();
        var event = new com.surf.tracker.shared.events.AlertRaisedEvent(identity, java.time.Instant.EPOCH,
            identity, "rule", "vehicle", "OVERSPEED", "WARNING", "fixture", java.time.Instant.EPOCH,
            null, null, null, Map.of());
        // when
        topic.convertAndSend("tracking.events.alertraisedevent", event);
        // then: the original payload survives exhausted broker retries rather than being acknowledged/lost.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            org.mockito.Mockito.verify(notificationOrchestrator, org.mockito.Mockito.atLeast(2)).processAlert(
                org.mockito.ArgumentMatchers.argThat(received -> identity.equals(received.getEventId())));
            Boolean retained = queue.browse("DLQ", (session, browser) -> {
                var messages = browser.getEnumeration();
                while (messages.hasMoreElements()) {
                    var message = (jakarta.jms.Message) messages.nextElement();
                    if (message instanceof jakarta.jms.TextMessage
                            && converter.fromMessage(message) instanceof com.surf.tracker.shared.events.AlertRaisedEvent retainedEvent
                            && identity.equals(retainedEvent.getEventId())) return true;
                }
                return false;
            });
            assertThat(retained).isTrue();
        });
        assertThat(jdbc.queryForObject("select count(*) from notification_hubs where alertid = ?",
            Long.class, identity)).isZero();
    }
    @Autowired com.surf.tracker.alerting.application.event.AlertCreationEventHandler alertIntake;
    @Autowired com.surf.tracker.alerting.application.event.AlertEventOutbox alertOutbox;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.surf.tracker.shared.pubsub.ArtemisEventPublisher publisherSpy;
    @Autowired com.surf.tracker.core.infrastructure.repository.VehicleRepository vehicles;
    @Autowired com.surf.tracker.core.infrastructure.repository.TripRepository trips;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.surf.tracker.alerting.infrastructure.repository.AlertRuleRepository alertRules;

    @Test
    void telemetry_transientRuleDatabaseFailure_redeliversInsteadOfAcknowledgingEmptyRules() {
        // given: inject one repository failure, then use the actual PostgreSQL implementation.
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        Long organization = vehicles.findById(vehicle).orElseThrow().getOrganizationId();
        String key = "database-retry-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed",
            Map.of("rule_key", key, "rule_name", "Database retry acceptance", "speed_limit", 30,
                "vehicle_ids", List.of(vehicle), "enabled", true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Spring Data's interface spy delegates to a repository proxy; there is no abstract real method to call.
        var repositoryDelegate = org.mockito.Mockito.mockingDetails(alertRules).getMockCreationSettings().getDefaultAnswer();
        var failures = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            if (failures.getAndIncrement() == 0) {
                throw new org.springframework.dao.DataAccessResourceFailureException("Injected rule DB failure");
            }
            return repositoryDelegate.answer(invocation);
        }).when(alertRules).findActiveRulesForVehicleAndOrganizationId(vehicle, organization);
        var source = new com.surf.tracker.shared.events.VehicleUpdatedEvent("database-retry-" + IDS.incrementAndGet(),
            java.time.Instant.parse("2026-01-01T12:00:00Z"), vehicle, organization,
            new com.surf.tracker.shared.domain.LocationPoint(1, 2, 60, java.time.Instant.parse("2026-01-01T12:00:00Z")), Map.of());
        // when / then: real JMS redelivery must invoke the repository again and create the alert.
        topic.convertAndSend("tracking.events.vehicleupdatedevent", source);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(25)).untilAsserted(() ->
            assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox where vehicle_id = ?",
                Long.class, vehicle)).isEqualTo(1L));
        org.mockito.Mockito.verify(alertRules, org.mockito.Mockito.atLeast(2))
            .findActiveRulesForVehicleAndOrganizationId(vehicle, organization);
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id = ?",
            Long.class, vehicle)).isEqualTo(1L);
    }

    @Test
    void telemetry_alertPublicationFailure_redeliversSourceWithStableDerivedIdentity() throws Exception {
        // given: only one actual publication attempt fails; no fake rule/persistence components.
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String key = "retry-speed-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed",
            Map.of("rule_key", key, "rule_name", "Retry acceptance", "speed_limit", 30,
                "vehicle_ids", List.of(vehicle), "enabled", true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var attempts = new java.util.concurrent.CopyOnWriteArrayList<com.surf.tracker.alerting.domain.AlertDetectedEvent>();
        org.mockito.Mockito.doAnswer(invocation -> {
            var detected = (com.surf.tracker.alerting.domain.AlertDetectedEvent) invocation.getArgument(0);
            attempts.add(detected);
            if (attempts.size() == 1) throw new RuntimeException("Injected publication failure");
            return invocation.callRealMethod();
        }).when(publisherSpy).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event instanceof com.surf.tracker.alerting.domain.AlertDetectedEvent detected
                && vehicle.equals(detected.getVehicleId())));
        var source = new com.surf.tracker.shared.events.VehicleUpdatedEvent("retry-update-" + IDS.incrementAndGet(),
            java.time.Instant.parse("2026-01-01T12:00:00Z"), vehicle,
            vehicles.findById(vehicle).orElseThrow().getOrganizationId(),
            new com.surf.tracker.shared.domain.LocationPoint(1, 2, 60, java.time.Instant.parse("2026-01-01T12:00:00Z")),
            Map.of());
        // when: actual listener failure must trigger broker redelivery without the cooldown hiding the retry.
        topic.convertAndSend("tracking.events.vehicleupdatedevent", source);
        // then
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(25)).untilAsserted(() -> {
            assertThat(attempts).hasSize(2);
            assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox where vehicle_id = ?",
                Long.class, vehicle)).isEqualTo(1L);
        });
        assertThat(attempts.get(1)).usingRecursiveComparison().isEqualTo(attempts.getFirst());
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id = ?",
            Long.class, vehicle)).isEqualTo(1L);
    }

    @Test
    void ruleCache_disconnectedRedis_fallsBackToInitializedTenantScopedPostgresRules() throws Exception {
        // given: real vendor data, but this cache instance points at a deliberately unavailable Redis endpoint.
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String vehicle = createVehicle(owner);
        String foreignVehicle = createVehicle(outsider);
        String siblingVehicle = createVehicle(owner);
        Long organizationId = vehicles.findById(vehicle).orElseThrow().getOrganizationId();
        Long foreignOrganization = vehicles.findById(foreignVehicle).orElseThrow().getOrganizationId();
        String key = "outage-speed-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed",
            Map.of("rule_key", key, "rule_name", "Outage acceptance", "speed_limit", 30,
                "vehicle_ids", List.of(vehicle, siblingVehicle), "enabled", true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int unavailablePort;
        try (var reserved = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            unavailablePort = reserved.getLocalPort();
        }
        var settings = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
            .commandTimeout(java.time.Duration.ofMillis(250)).shutdownTimeout(java.time.Duration.ZERO).build();
        var factory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration("127.0.0.1", unavailablePort), settings);
        factory.afterPropertiesSet();
        try {
            var unavailableRedis = new org.springframework.data.redis.core.RedisTemplate<String, Object>();
            unavailableRedis.setConnectionFactory(factory);
            unavailableRedis.afterPropertiesSet();
            var cache = new com.surf.tracker.alerting.infrastructure.cache.VehicleRuleCacheService(
                unavailableRedis, alertRules, new com.surf.tracker.alerting.infrastructure.cache.RedisKeyGenerator());
            // when / then: failed cache/index reads do not turn existing rules into an empty result.
            var loaded = cache.getActiveRulesForVehicle(vehicle, organizationId);
            assertThat(loaded).extracting(com.surf.tracker.alerting.domain.entity.AlertRule::getRuleKey).containsExactly(key);
            assertThat(loaded.getFirst().getVehicleIds()).containsExactlyInAnyOrder(vehicle, siblingVehicle);
            com.surf.tracker.shared.security.TenantContext.setCurrentOrgId(organizationId);
            assertThat(cache.hasRulesCached(vehicle)).isTrue();
            assertThat(cache.getActiveRulesForVehicle(vehicle, foreignOrganization)).isEmpty();
        } finally {
            com.surf.tracker.shared.security.TenantContext.clear();
            factory.destroy();
        }
    }

    @Test
    void outbox_concurrentRelays_skipLockedIntentWithoutDuplicateTransport() throws Exception {
        // given: periodic polling is delayed in this fixture; each real relay transaction is explicit.
        String vehicle = createVehicle(tenantHeaders());
        var source = new com.surf.tracker.alerting.domain.AlertDetectedEvent(java.time.Clock.systemUTC(),
            "relay-lock", com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED, vehicle,
            "Concurrent relay acceptance", com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, null);
        alertIntake.handleAlertEvent(source);
        String payload = jdbc.queryForObject("select payload from alert_event_outbox where source_event_id = ?",
            String.class, source.getEventId());
        var stored = new com.surf.tracker.main.config.AppConfig().objectMapper()
            .readValue(payload, com.surf.tracker.shared.events.AlertRaisedEvent.class);
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        org.mockito.Mockito.doAnswer(invocation -> {
            calls.incrementAndGet();
            publishing.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Test publisher timed out");
            return null;
        }).when(publisherSpy).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event != null && stored.getEventId().equals(event.getEventId())));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // when: worker one holds the SQL claim while waiting on transport.
            var first = executor.submit(() -> alertOutbox.relayPending());
            assertThat(publishing.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> alertOutbox.relayPending());
            second.get(5, TimeUnit.SECONDS);
            // then: worker two neither blocks on that row nor sends it again.
            assertThat(calls).hasValue(1);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("select published_at from alert_event_outbox where source_event_id = ?",
                Object.class, source.getEventId())).isNotNull();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void alerts_concurrentSourceReplay_persistsOneAlertAndRelaysOriginalIdentityThroughArtemis() throws Exception {
        // given: actual database transactions, application intake and authenticated broker transport.
        String vehicle = createVehicle(tenantHeaders());
        var event = new com.surf.tracker.alerting.domain.AlertDetectedEvent(java.time.Clock.systemUTC(),
            "outbox-fixture", com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED, vehicle,
            "Outbox acceptance", com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING, null);
        try (var connection = messaging.createConnection();
             var session = connection.createSession(false, jakarta.jms.Session.AUTO_ACKNOWLEDGE);
             var raised = session.createConsumer(session.createTopic("tracking.events.alertraisedevent"))) {
            connection.start();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                java.util.concurrent.Callable<Void> deliver = () -> {
                    start.await();
                    alertIntake.handleAlertEvent(event);
                    return null;
                };
                // when: two workers receive the same event, followed by post-commit redelivery.
                var first = executor.submit(deliver);
                var second = executor.submit(deliver);
                start.countDown();
                first.get(30, TimeUnit.SECONDS);
                second.get(30, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            alertIntake.handleAlertEvent(event);
            alertOutbox.relayPending();
            // then: source tombstone, persisted alert and real outgoing identity agree.
            assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id = ?",
                Long.class, vehicle)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox where source_event_id = ?",
                Long.class, event.getEventId())).isEqualTo(1L);
            com.surf.tracker.shared.events.AlertRaisedEvent outgoing = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                var message = raised.receive(500);
                if (message == null) continue;
                var candidate = (com.surf.tracker.shared.events.AlertRaisedEvent) converter.fromMessage(message);
                if (vehicle.equals(candidate.getVehicleId())) {
                    outgoing = candidate;
                    break;
                }
            }
            assertThat(outgoing).isNotNull();
            assertThat(outgoing.getAlertId()).isEqualTo(jdbc.queryForObject(
                "select cast(alert_id as varchar) from tracker_alerts where vehicle_id = ?", String.class, vehicle));
            String payload = jdbc.queryForObject("select payload from alert_event_outbox where source_event_id = ?",
                String.class, event.getEventId());
            var persisted = new com.surf.tracker.main.config.AppConfig().objectMapper()
                .readValue(payload, com.surf.tracker.shared.events.AlertRaisedEvent.class);
            assertThat(outgoing).usingRecursiveComparison().isEqualTo(persisted);
            jakarta.jms.Message additional;
            while ((additional = raised.receive(500)) != null) {
                var other = (com.surf.tracker.shared.events.AlertRaisedEvent) converter.fromMessage(additional);
                assertThat(other.getVehicleId()).as("unrelated outbox intents may also be relayed").isNotEqualTo(vehicle);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/alerts", "/alerts?search=missing", "/alerts/geofences",
        "/alerts/geofences?search=missing", "/alerts/rules", "/alerts/rules?search=missing"})
    void alertQueries_optionalSearch_returnsEmptyTenantPageOnPostgres(String path) {
        // given
        HttpHeaders tenant = tenantHeaders();
        // when / then: execute real vendor queries even with no matching rows.
        assertThat(content(tenant, path)).isEmpty();
    }

    @Test
    void alerts_searchFiltersAndOrdering_preserveTenantAndOptionalTimeBounds() {
        // given: explicit persisted fixtures isolate query behavior from notification delivery.
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String ownVehicle = createVehicle(owner);
        String foreignVehicle = createVehicle(outsider);
        var ownerOrg = vehicles.findById(ownVehicle).orElseThrow().getOrganizationId();
        var foreignOrg = vehicles.findById(foreignVehicle).orElseThrow().getOrganizationId();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int index = 0; index < 3; index++) {
                var alert = new com.surf.tracker.alerting.domain.entity.TrackerAlert();
                alert.setOrganizationId(index == 2 ? foreignOrg : ownerOrg);
                alert.setVehicle(index == 2 ? foreignVehicle : ownVehicle);
                alert.setTracker("query-tracker");
                alert.setAlertType(com.surf.tracker.alerting.domain.enums.AlertType.OVERSPEED);
                alert.setSeverity(com.surf.tracker.alerting.domain.enums.AlertSeverity.WARNING);
                alert.setMessage("Speed marker " + index);
                alert.setTriggeredAt(java.time.Instant.parse(index == 0
                    ? "2026-01-01T12:00:00Z" : "2026-01-03T12:00:00Z"));
                entityManager.persist(alert);
            }
        });
        // when / then
        assertThat(content(owner, "/alerts?sortBy=triggeredAt&sortDirection=ASC"))
            .extracting(row -> row.get("message")).containsExactly("Speed marker 0", "Speed marker 1");
        assertThat(content(outsider, "/alerts")).extracting(row -> row.get("message"))
            .containsExactly("Speed marker 2");
        String filters = "&vehicleId=" + ownVehicle
            + "&trackerId=query-tracker&alertType=OVERSPEED&severity=WARNING&acknowledged=false&resolved=false";
        assertThat(content(owner, "/alerts?search=sPeEd" + filters + "&endDate=2026-01-02T00:00:00Z"))
            .extracting(row -> row.get("message")).containsExactly("Speed marker 0");
        assertThat(content(owner, "/alerts?search=sPeEd" + filters + "&startDate=2026-01-02T00:00:00Z"))
            .extracting(row -> row.get("message")).containsExactly("Speed marker 1");
        assertThat(content(outsider, "/alerts?search=sPeEd" + filters)).isEmpty();
    }

    private HttpHeaders tenantHeaders() {
        // Real service/session/JWT fixture; dedicated auth tests exercise HTTP login.
        // Fleet setup must not exhaust the unchanged per-IP HTTP auth limit.
        var response = auth.login(provisionUser(), USER_PASSWORD, "127.0.0.1", "fleet-test-fixture");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getAccessToken());
        return headers;
    }

    @Test
    void roleCatalog_authenticatedTenantAdmin_pagesOnlyCurrentTenantWithoutGlobalRoles() {
        // given: independent ordinary administrators, neither has system.admin.
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        var identity = request(owner, HttpMethod.GET, "/api/v1/users/me", null).getBody();
        long ownerOrg = ((Number) identity.get("organization_id")).longValue();
        // The minimal login fixture seeds only TENANT_ADMIN. Add same-tenant roles
        // through the fixture transaction to exercise real ordering/paging.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var org = organizations.findById(ownerOrg).orElseThrow();
            for (String name : List.of("OPERATOR", "VIEWER")) {
                Role role = Role.orgRole(org, name, name + " deployment fixture");
                role.addPermission(permissions.findByKey("vehicles.read").orElseThrow());
                roles.save(role);
            }
        });
        var ownerRoles = content(owner, "/api/v1/roles");
        var foreignRoles = content(outsider, "/api/v1/roles");
        // when
        var first = request(owner, HttpMethod.GET, "/api/v1/roles?page=0&size=1&orgId=999999", null);
        var second = content(owner, "/api/v1/roles?page=1&size=1");
        // then: actual PostgreSQL paging, lazy permission mapping and deterministic ordering.
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(first.getBody().get("total_elements")).isEqualTo(ownerRoles.size());
        assertThat(ownerRoles).extracting(row -> row.get("name"))
            .containsExactly("OPERATOR", "TENANT_ADMIN", "VIEWER");
        assertThat(ownerRoles).extracting(row -> row.get("id"))
            .doesNotContainAnyElementsOf(foreignRoles.stream().map(row -> row.get("id")).toList());
        assertThat(second).extracting(row -> row.get("id")).containsExactly(ownerRoles.get(1).get("id"));
        assertThat(ownerRoles.get(1).get("permissions")).asList().contains("roles.read");
        assertThat(request(owner, HttpMethod.GET, "/api/v1/roles?size=101", null).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void telemetry_registeredDevices_persistOwningTrackerAndPublishTenantScopedUpdates() throws Exception {
        // given: distinct vehicle and tracker device IDs are allowed by the fleet API.
        HttpHeaders first = tenantHeaders();
        HttpHeaders second = tenantHeaders();
        String firstVehicle = createVehicle(first);
        String secondVehicle = createVehicle(second);
        String firstTracker = createTracker(first, firstVehicle);
        String secondTracker = createTracker(second, secondVehicle);
        java.time.Instant timestamp = java.time.Instant.parse("2026-01-01T12:00:00Z");
        Map<String, String> assignments = Map.of(firstVehicle, firstTracker, secondVehicle, secondTracker);
        try (var connection = messaging.createConnection();
             var session = connection.createSession(false, jakarta.jms.Session.AUTO_ACKNOWLEDGE);
             var updates = session.createConsumer(session.createTopic("tracking.events.vehicleupdatedevent"))) {
            connection.start();
            // when: real broker consumers run without an HTTP TenantContext.
            assignments.forEach((vehicle, tracker) -> topic.convertAndSend("tracking.events.locationdataevent",
                new com.surf.tracker.shared.events.LocationDataEvent(java.time.Clock.systemUTC(),
                    "device-" + tracker, new com.surf.tracker.shared.domain.LocationPoint(1, 2, 20, timestamp), "GT06")));
            // then: each update identifies the persisted owner and the original physical tracker.
            java.util.Set<String> received = new java.util.HashSet<>();
            for (int index = 0; index < assignments.size(); index++) {
                var message = updates.receive(15000);
                assertThat(message).as("telemetry must reach the actual update listener and publisher").isNotNull();
                var update = (com.surf.tracker.shared.events.VehicleUpdatedEvent) converter.fromMessage(message);
                assertThat(assignments).containsKey(update.getVehicleId());
                var owner = vehicles.findById(update.getVehicleId()).orElseThrow();
                assertThat(update.getOrganizationId()).isEqualTo(owner.getOrganizationId());
                assertThat(update.getNewLocation().timestamp()).isEqualTo(timestamp);
                received.add(update.getVehicleId());
            }
            assertThat(received).containsExactlyInAnyOrderElementsOf(assignments.keySet());
            // The outgoing event can precede DB commit; take the row lock to wait for completion.
            var transaction = new TransactionTemplate(transactions);
            assignments.forEach((vehicle, tracker) -> transaction.executeWithoutResult(status -> {
                var stored = entityManager.find(com.surf.tracker.core.domain.entity.Vehicle.class,
                    vehicle, jakarta.persistence.LockModeType.PESSIMISTIC_READ);
                assertThat(stored.getCurrentLocation()).isNotNull();
                assertThat(stored.getCurrentLocation().getTracker().getTrackerId()).isEqualTo(tracker);
                assertThat(stored.getCurrentLocation().getLatitude()).isEqualTo(1.0);
            }));
        }
    }

    @Test
    void telemetry_httpConfiguredRule_reachesPersistedAlertAndOriginalOutboxPublication() throws Exception {
        // given: real HTTP rule creation, not a directly injected AlertDetectedEvent.
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String vehicle = createVehicle(owner);
        String tracker = createTracker(owner, vehicle);
        String key = "telemetry-speed-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed",
            Map.of("rule_key", key, "rule_name", "Telemetry acceptance", "speed_limit", 30,
                "vehicle_ids", List.of(vehicle), "enabled", true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try (var connection = messaging.createConnection();
             var session = connection.createSession(false, jakarta.jms.Session.AUTO_ACKNOWLEDGE);
             var raised = session.createConsumer(session.createTopic("tracking.events.alertraisedevent"))) {
            connection.start();
            // when: physical-device identity enters actual JMS location and evaluation listeners.
            topic.convertAndSend("tracking.events.locationdataevent",
                new com.surf.tracker.shared.events.LocationDataEvent(java.time.Clock.systemUTC(), "device-" + tracker,
                    new com.surf.tracker.shared.domain.LocationPoint(1, 2, 60,
                        java.time.Instant.parse("2026-01-01T12:00:00Z")), "GT06"));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox where vehicle_id = ?",
                    Long.class, vehicle)).isEqualTo(1L));
            alertOutbox.relayPending();
            // then: actual outgoing event matches the alert visible only to its owner.
            com.surf.tracker.shared.events.AlertRaisedEvent outgoing = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (System.nanoTime() < deadline) {
                var message = raised.receive(500);
                if (message == null) continue;
                var candidate = (com.surf.tracker.shared.events.AlertRaisedEvent) converter.fromMessage(message);
                if (vehicle.equals(candidate.getVehicleId())) {
                    outgoing = candidate;
                    break;
                }
            }
            assertThat(outgoing).isNotNull();
            assertThat(outgoing.getVehicleId()).isEqualTo(vehicle);
            assertThat(outgoing.getRuleKey()).isEqualTo(key);
            assertThat(outgoing.getAlertType()).isEqualTo("OVERSPEED");
            assertThat(content(owner, "/alerts?vehicleId=" + vehicle))
                .extracting(row -> String.valueOf(row.get("alert_id"))).containsExactly(outgoing.getAlertId());
            assertThat(content(outsider, "/alerts?vehicleId=" + vehicle)).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id = ?",
                Long.class, vehicle)).isEqualTo(1L);
            jakarta.jms.Message additional;
            while ((additional = raised.receive(500)) != null) {
                var other = (com.surf.tracker.shared.events.AlertRaisedEvent) converter.fromMessage(additional);
                assertThat(other.getVehicleId()).as("unrelated outbox intents may also be relayed").isNotEqualTo(vehicle);
            }
        }
    }

    @Test
    void telemetry_ambiguousUpdatePublication_redeliversWithStableUpdateIdentity() {
        // given: first send reaches Artemis, then reports failure before core DB commit.
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String tracker = createTracker(owner, vehicle);
        String key = "core-retry-speed-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed",
            Map.of("rule_key", key, "rule_name", "Core retry acceptance", "speed_limit", 30,
                "vehicle_ids", List.of(vehicle), "enabled", true)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var attempts = new java.util.concurrent.CopyOnWriteArrayList<com.surf.tracker.shared.events.VehicleUpdatedEvent>();
        var evaluated = new java.util.concurrent.CopyOnWriteArrayList<com.surf.tracker.shared.events.VehicleUpdatedEvent>();
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            evaluated.add(invocation.getArgument(0));
            return null;
        }).when(alertingEngine).processVehicleUpdate(org.mockito.ArgumentMatchers.argThat(event ->
            event != null && vehicle.equals(event.getVehicleId())));
        org.mockito.Mockito.doAnswer(invocation -> {
            attempts.add(invocation.getArgument(0));
            if (attempts.size() == 1) {
                // Leave the listener's local JMS transaction so the first send really survives rollback.
                CompletableFuture.runAsync(() -> topic.convertAndSend("tracking.events.vehicleupdatedevent",
                    attempts.getFirst())).join();
                throw new IllegalStateException("Injected ambiguous update publication");
            }
            invocation.callRealMethod();
            return null;
        }).when(publisherSpy).publish(org.mockito.ArgumentMatchers.argThat(event ->
            event instanceof com.surf.tracker.shared.events.VehicleUpdatedEvent update
                && vehicle.equals(update.getVehicleId())));
        var timestamp = java.time.Instant.parse("2026-01-01T12:00:00Z");
        var source = new com.surf.tracker.shared.events.LocationDataEvent(
            java.time.Clock.fixed(timestamp, java.time.ZoneOffset.UTC),
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000051"), "device-" + tracker,
            new com.surf.tracker.shared.domain.LocationPoint(1, 2, 60, timestamp), "GT06", Map.of());
        // when
        topic.convertAndSend("tracking.events.locationdataevent", source);
        // then: actual incoming redelivery retains identity through converter and both listeners.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> {
            assertThat(attempts).hasSize(2);
            assertThat(evaluated).as("both outgoing update attempts must finish downstream evaluation").hasSize(2);
            assertThat(jdbc.queryForObject("select count(*) from alert_event_outbox where vehicle_id = ?",
                Long.class, vehicle)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from tracker_location_data where tracker_id = ?",
                Long.class, tracker)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id = ?",
                Long.class, vehicle)).isEqualTo(1L);
        });
        assertThat(attempts.get(1)).usingRecursiveComparison().isEqualTo(attempts.getFirst());
        assertThat(attempts.getFirst().getOccurredOn()).isEqualTo(source.getOccurredOn());
    }

    private ResponseEntity<Map> request(HttpHeaders headers, HttpMethod method, String path, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private String createVehicle(HttpHeaders headers) {
        String id = "fleet-vehicle-" + IDS.incrementAndGet();
        ResponseEntity<Map> response = request(headers, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", id, "device_id", "device-" + id, "model", "Original model"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("vehicle_id", id).containsEntry("model", "Original model");
        return id;
    }

    private String createTracker(HttpHeaders headers, String vehicleId) {
        String id = "fleet-tracker-" + IDS.incrementAndGet();
        ResponseEntity<Map> response = request(headers, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", id, "device_id", "device-" + id, "vehicle_id", vehicleId,
                "model", "Original tracker", "protocol", "GT06"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("tracker_id", id).containsEntry("vehicle_id", vehicleId);
        return id;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> content(HttpHeaders headers, String path) {
        ResponseEntity<Map> response = request(headers, HttpMethod.GET, path, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) response.getBody().get("content");
    }

    @Test void vehicles_crud_isolatesTenantAndPersistsUpdates() {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String id = createVehicle(owner);
        String path = "/api/v1/vehicles/" + id;
        // when / then
        assertThat(content(owner, "/api/v1/vehicles")).extracting(row -> row.get("vehicle_id")).containsExactly(id);
        assertThat(content(outsider, "/api/v1/vehicles")).isEmpty();
        assertThat(request(outsider, HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(outsider, HttpMethod.PUT, path, Map.of("model", "Intruder")).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(outsider, HttpMethod.DELETE, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(owner, HttpMethod.PUT, path,
            Map.of("model", "Updated model", "license_plate", "FLEET-123", "vin", "TESTVIN123")).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(request(owner, HttpMethod.GET, path, null).getBody()).containsEntry("model", "Updated model")
            .containsEntry("license_plate", "FLEET-123").containsEntry("vin", "TESTVIN123");
        assertThat(request(owner, HttpMethod.DELETE, path, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(owner, HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(content(owner, "/api/v1/vehicles")).isEmpty();
    }

    @Test void trackers_crudAndFilters_isolateTenantAndPreserveVehicleAssociation() {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String vehicle = createVehicle(owner);
        String id = createTracker(owner, vehicle);
        String path = "/api/v1/trackers/" + id;
        // when / then
        assertThat(content(owner, "/api/v1/trackers?status=ACTIVE")).extracting(row -> row.get("tracker_id"))
            .containsExactly(id);
        assertThat(content(outsider, "/api/v1/trackers?status=ACTIVE")).isEmpty();
        assertThat(request(outsider, HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(outsider, HttpMethod.PUT, path, Map.of("model", "Intruder")).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(outsider, HttpMethod.DELETE, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("model", "Updated tracker", "protocol", "GT06",
            "battery_level", 12.5, "signal_strength", 3, "status", "ACTIVE")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(owner, HttpMethod.GET, path, null).getBody()).containsEntry("model", "Updated tracker")
            .containsEntry("vehicle_id", vehicle).containsEntry("battery_level", 12.5).containsEntry("signal_strength", 3);
        assertThat(content(owner, "/api/v1/trackers?batteryThreshold=20")).extracting(row -> row.get("tracker_id"))
            .containsExactly(id);
        assertThat(content(outsider, "/api/v1/trackers?batteryThreshold=20")).isEmpty();
        assertThat(request(owner, HttpMethod.DELETE, path, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(owner, HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(content(owner, "/api/v1/trackers")).isEmpty();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void vehicles_duplicateId_returnsConflictWithoutChangingOriginal(boolean otherTenant) {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders caller = otherTenant ? tenantHeaders() : owner;
        String id = createVehicle(owner);
        String tracker = createTracker(owner, id);
        Map original = request(owner, HttpMethod.GET, "/api/v1/vehicles/" + id, null).getBody();
        // when
        ResponseEntity<Map> duplicate = request(caller, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", id, "device_id", "replacement-" + id, "model", "Replacement"));
        // then: also inspect state if the status assertion fails
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            softly.assertThat(request(owner, HttpMethod.GET, "/api/v1/vehicles/" + id, null).getBody()).isEqualTo(original);
            softly.assertThat(request(owner, HttpMethod.GET, "/api/v1/trackers/" + tracker, null).getBody())
                .containsEntry("vehicle_id", id);
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fleet_concurrentCrossTenantCreate_oneInsertWinsWithoutTakeover(boolean tracker) throws Exception {
        // given: race the real database unique key, not a check-before-insert mock.
        HttpHeaders first = tenantHeaders();
        HttpHeaders second = tenantHeaders();
        String id = "race-" + IDS.incrementAndGet();
        String path = tracker ? "/api/v1/trackers" : "/api/v1/vehicles";
        Map<String, Object> firstBody = tracker
            ? Map.of("tracker_id", id, "device_id", "first-" + id, "vehicle_id", createVehicle(first))
            : Map.of("vehicle_id", id, "device_id", "first-" + id);
        Map<String, Object> secondBody = tracker
            ? Map.of("tracker_id", id, "device_id", "second-" + id, "vehicle_id", createVehicle(second))
            : Map.of("vehicle_id", id, "device_id", "second-" + id);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            // when
            Future<ResponseEntity<Map>> one = executor.submit(() -> {
                start.await();
                return request(first, HttpMethod.POST, path, firstBody);
            });
            Future<ResponseEntity<Map>> two = executor.submit(() -> {
                start.await();
                return request(second, HttpMethod.POST, path, secondBody);
            });
            start.countDown();
            var firstResult = one.get(30, TimeUnit.SECONDS);
            var secondResult = two.get(30, TimeUnit.SECONDS);
            // then
            assertThat(List.of(firstResult.getStatusCode(), secondResult.getStatusCode()))
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            boolean firstWon = firstResult.getStatusCode() == HttpStatus.CREATED;
            var winner = request(firstWon ? first : second, HttpMethod.GET, path + "/" + id, null);
            assertThat(winner.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(winner.getBody()).containsEntry("device_id", (firstWon ? "first-" : "second-") + id);
            assertThat(request(firstWon ? second : first, HttpMethod.GET, path + "/" + id, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void trackers_duplicateId_returnsConflictWithoutReassignment(boolean otherTenant) {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders caller = otherTenant ? tenantHeaders() : owner;
        String vehicle = createVehicle(owner);
        String target = createVehicle(caller);
        String id = createTracker(owner, vehicle);
        Map original = request(owner, HttpMethod.GET, "/api/v1/trackers/" + id, null).getBody();
        // when
        ResponseEntity<Map> duplicate = request(caller, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", id, "device_id", "replacement-" + id, "vehicle_id", target, "model", "Replacement"));
        // then
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            softly.assertThat(request(owner, HttpMethod.GET, "/api/v1/trackers/" + id, null).getBody()).isEqualTo(original);
            softly.assertThat(content(owner, "/api/v1/trackers")).anySatisfy(row ->
                assertThat(row).containsEntry("tracker_id", id).containsEntry("vehicle_id", vehicle));
        });
    }

    @Test void trackers_foreignVehicleAssignment_isRejectedWithoutCreatingTracker() {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String vehicle = createVehicle(owner);
        String id = "foreign-tracker-" + IDS.incrementAndGet();
        // when
        ResponseEntity<Map> response = request(outsider, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", id, "device_id", "device-" + id, "vehicle_id", vehicle));
        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(content(outsider, "/api/v1/trackers")).isEmpty();
        assertThat(content(owner, "/api/v1/trackers")).isEmpty();
    }

    @Test void fleet_invalidTrackerStatus_returnsBadRequestWithoutChangingState() {
        // given
        HttpHeaders owner = tenantHeaders();
        String id = createTracker(owner, createVehicle(owner));
        String path = "/api/v1/trackers/" + id;
        Map original = request(owner, HttpMethod.GET, path, null).getBody();
        // when
        ResponseEntity<Map> response = request(owner, HttpMethod.PUT, path,
            Map.of("model", "Must not persist", "status", "NOT_A_TRACKER_STATUS"));
        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.GET, path, null).getBody()).isEqualTo(original);
        assertThat(request(owner, HttpMethod.GET, "/api/v1/trackers?status=NOT_A_TRACKER_STATUS", null).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void fleet_deviceIdentity_isUniqueAcrossVehiclesAndTrackersPerTenantAndGlobally() {
        // given: a vehicle already claims a physical device identity
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String ownerVehicle = createVehicle(owner);
        String otherOwnerVehicle = createVehicle(owner);
        String vehicleDevice = "shared-device-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", ownerVehicle + "-with-device", "device_id", vehicleDevice)).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        // when: a same-tenant and a foreign-tenant tracker both try to register the device
        String sameTenantTracker = "collision-tracker-" + IDS.incrementAndGet();
        String foreignTracker = "collision-tracker-" + IDS.incrementAndGet();
        ResponseEntity<Map> sameTenant = request(owner, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", sameTenantTracker, "device_id", vehicleDevice, "vehicle_id", otherOwnerVehicle));
        ResponseEntity<Map> foreign = request(outsider, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", foreignTracker, "device_id", vehicleDevice, "vehicle_id", createVehicle(outsider)));
        // then: neither may split device ownership, and neither tracker is persisted
        assertThat(sameTenant.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(owner, HttpMethod.GET, "/api/v1/trackers/" + sameTenantTracker, null).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(outsider, HttpMethod.GET, "/api/v1/trackers/" + foreignTracker, null).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(content(owner, "/api/v1/trackers")).isEmpty();
        assertThat(content(outsider, "/api/v1/trackers")).isEmpty();

        // given: a tracker now claims a device identity
        String tracker = createTracker(owner, otherOwnerVehicle);
        String trackerDevice = request(owner, HttpMethod.GET, "/api/v1/trackers/" + tracker, null)
            .getBody().get("device_id").toString();
        // when: vehicle creation tries to claim the same device, same-tenant and foreign-tenant
        String sameTenantVehicleId = "collision-vehicle-" + IDS.incrementAndGet();
        String foreignVehicleId = "collision-vehicle-" + IDS.incrementAndGet();
        ResponseEntity<Map> sameTenantVehicle = request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", sameTenantVehicleId, "device_id", trackerDevice));
        ResponseEntity<Map> foreignVehicle = request(outsider, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", foreignVehicleId, "device_id", trackerDevice));
        // then: vehicle ownership cannot duplicate a registered tracker device, and nothing is persisted
        assertThat(sameTenantVehicle.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(foreignVehicle.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(owner, HttpMethod.GET, "/api/v1/vehicles/" + sameTenantVehicleId, null).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(outsider, HttpMethod.GET, "/api/v1/vehicles/" + foreignVehicleId, null).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void fleet_createValidation_rejectsMissingAndOversizedContractFields() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String tracker = "validated-tracker-" + IDS.incrementAndGet();
        List<String> vehicleSnapshot = content(owner, "/api/v1/vehicles").stream()
            .map(row -> row.get("vehicle_id").toString()).toList();
        // when / then: required device identity is enforced before persistence
        assertThat(request(owner, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", tracker, "vehicle_id", vehicle)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", tracker, "device_id", " ", "vehicle_id", vehicle)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(content(owner, "/api/v1/trackers")).isEmpty();

        // when / then: bounded identifiers/descriptions fail as client errors, not conflicts
        String tooLongId = "x".repeat(51);
        String oversizedVehicle = "validated-vehicle-" + IDS.incrementAndGet();
        String oversizedVinVehicle = "validated-vehicle-" + IDS.incrementAndGet();
        String oversizedPlateVehicle = "validated-vehicle-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", tooLongId)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", oversizedVehicle, "device_id", "D".repeat(51))).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", oversizedVinVehicle,
                "vin", "V".repeat(18))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", oversizedPlateVehicle,
                "license_plate", "P".repeat(21))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", tracker, "device_id", "validated-device-" + IDS.incrementAndGet(),
                "vehicle_id", vehicle, "protocol", "P".repeat(21))).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(content(owner, "/api/v1/trackers")).isEmpty();
        assertThat(content(owner, "/api/v1/vehicles").stream()
            .map(row -> row.get("vehicle_id").toString()).toList()).isEqualTo(vehicleSnapshot)
            .doesNotContain(tooLongId, oversizedVehicle, oversizedVinVehicle, oversizedPlateVehicle);
    }

    @Test void fleet_updateValidation_rejectsOversizedFieldsWithoutMutation() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String tracker = createTracker(owner, vehicle);
        String vehiclePath = "/api/v1/vehicles/" + vehicle;
        String trackerPath = "/api/v1/trackers/" + tracker;
        Map vehicleBefore = request(owner, HttpMethod.GET, vehiclePath, null).getBody();
        Map trackerBefore = request(owner, HttpMethod.GET, trackerPath, null).getBody();
        // when / then: every bounded update field is rejected before any entity is saved
        assertThat(request(owner, HttpMethod.PUT, vehiclePath,
            Map.of("model", "M".repeat(101))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.PUT, vehiclePath,
            Map.of("license_plate", "P".repeat(21))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.PUT, vehiclePath,
            Map.of("vin", "V".repeat(18))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.PUT, trackerPath,
            Map.of("model", "M".repeat(101))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.PUT, trackerPath,
            Map.of("protocol", "P".repeat(21))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.PUT, trackerPath,
            Map.of("firmware_version", "F".repeat(51))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(owner, HttpMethod.PUT, trackerPath,
            Map.of("sim_number", "S".repeat(21))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // then
        assertThat(request(owner, HttpMethod.GET, vehiclePath, null).getBody()).isEqualTo(vehicleBefore);
        assertThat(request(owner, HttpMethod.GET, trackerPath, null).getBody()).isEqualTo(trackerBefore);
    }

    @Test void fleet_createTrimsIdentifierAndDeviceWhitespace() {
        // given
        HttpHeaders owner = tenantHeaders();
        String suffix = String.valueOf(IDS.incrementAndGet());
        String vehicleId = "trimmed-vehicle-" + suffix;
        String trackerId = "trimmed-tracker-" + suffix;
        String vehicleDevice = "trimmed-vehicle-device-" + suffix;
        String trackerDevice = "trimmed-tracker-device-" + suffix;
        // when
        ResponseEntity<Map> vehicle = request(owner, HttpMethod.POST, "/api/v1/vehicles",
            Map.of("vehicle_id", "  " + vehicleId + "  ", "device_id", "  " + vehicleDevice + "  "));
        ResponseEntity<Map> tracker = request(owner, HttpMethod.POST, "/api/v1/trackers",
            Map.of("tracker_id", " " + trackerId + " ", "device_id", " " + trackerDevice + " ",
                "vehicle_id", " " + vehicleId + " "));
        // then: canonical identifiers are stored and returned, never the padded variants
        assertThat(vehicle.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(vehicle.getBody()).containsEntry("vehicle_id", vehicleId)
            .containsEntry("device_id", vehicleDevice);
        assertThat(tracker.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tracker.getBody()).containsEntry("tracker_id", trackerId)
            .containsEntry("device_id", trackerDevice).containsEntry("vehicle_id", vehicleId);
        assertThat(request(owner, HttpMethod.GET, "/api/v1/vehicles/" + vehicleId, null).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(request(owner, HttpMethod.GET, "/api/v1/trackers/" + trackerId, null).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test void fleet_concurrentCrossTableDeviceClaim_allowsExactlyOneOwner() throws Exception {
        // given: two valid target vehicles, one free device id, and two different tables racing for it
        HttpHeaders owner = tenantHeaders();
        String trackerVehicle = createVehicle(owner);
        String suffix = String.valueOf(IDS.incrementAndGet());
        String device = "cross-table-device-" + suffix;
        String racingVehicle = "cross-table-vehicle-" + suffix;
        String racingTracker = "cross-table-tracker-" + suffix;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            // when
            Future<ResponseEntity<Map>> vehicleAttempt = executor.submit(() -> {
                start.await();
                return request(owner, HttpMethod.POST, "/api/v1/vehicles",
                    Map.of("vehicle_id", racingVehicle, "device_id", device));
            });
            Future<ResponseEntity<Map>> trackerAttempt = executor.submit(() -> {
                start.await();
                return request(owner, HttpMethod.POST, "/api/v1/trackers",
                    Map.of("tracker_id", racingTracker, "device_id", device, "vehicle_id", trackerVehicle));
            });
            start.countDown();
            ResponseEntity<Map> vehicleResult = vehicleAttempt.get(30, TimeUnit.SECONDS);
            ResponseEntity<Map> trackerResult = trackerAttempt.get(30, TimeUnit.SECONDS);
            // then: the advisory lock serializes both writers; only the first insert may commit
            assertThat(List.of(vehicleResult.getStatusCode(), trackerResult.getStatusCode()))
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            Long owners = jdbc.queryForObject(
                "select (select count(*) from vehicles where device_id = ?) "
                    + "+ (select count(*) from trackers where device_id = ?)", Long.class, device, device);
            assertThat(owners).isEqualTo(1L);
            boolean vehicleWon = vehicleResult.getStatusCode() == HttpStatus.CREATED;
            assertThat(request(owner, HttpMethod.GET, "/api/v1/vehicles/" + racingVehicle, null).getStatusCode())
                .isEqualTo(vehicleWon ? HttpStatus.OK : HttpStatus.NOT_FOUND);
            assertThat(request(owner, HttpMethod.GET, "/api/v1/trackers/" + racingTracker, null).getStatusCode())
                .isEqualTo(vehicleWon ? HttpStatus.NOT_FOUND : HttpStatus.OK);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void fleet_createdTimestamps_areCurrentInsteadOfEpoch() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String tracker = createTracker(owner, vehicle);
        // when
        java.sql.Timestamp vehicleCreated = jdbc.queryForObject(
            "select created_at from vehicles where vehicle_id = ?", java.sql.Timestamp.class, vehicle);
        java.sql.Timestamp trackerCreated = jdbc.queryForObject(
            "select created_at from trackers where tracker_id = ?", java.sql.Timestamp.class, tracker);
        java.sql.Timestamp trackerUpdated = jdbc.queryForObject(
            "select updated_at from trackers where tracker_id = ?", java.sql.Timestamp.class, tracker);
        // then: new aggregate roots must not persist the 1970 placeholder
        assertThat(vehicleCreated.toInstant()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(trackerCreated.toInstant()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(trackerUpdated.toInstant()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
    }

    private String provisionTrip(String vehicleId, String suffix) {
        String id = "fleet-trip-" + suffix + "-" + IDS.incrementAndGet();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var trip = new com.surf.tracker.core.domain.entity.Trip();
            trip.setTripId(id);
            trip.setVehicle(vehicles.findById(vehicleId).orElseThrow());
            trip.setStartTime(java.time.Instant.parse("2026-01-02T10:00:00Z"));
            trip.setEndTime(java.time.Instant.parse("2026-01-02T10:30:00Z"));
            trip.setIsActive(false);
            trip.setTotalDistanceKm(12.5f);
            trips.save(trip);
        });
        return id;
    }

    @Test void trips_listingDetailAndCsv_onlyExposeTenantTrips() {
        // given: trip creation is telemetry-driven, so fixture persistence is explicit
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String vehicle = createVehicle(owner);
        String ownTrip = provisionTrip(vehicle, "own");
        String foreignTrip = provisionTrip(createVehicle(outsider), "foreign");
        // when / then
        assertThat(content(owner, "/api/v1/trips")).extracting(row -> row.get("trip_id")).containsExactly(ownTrip);
        assertThat(content(outsider, "/api/v1/trips")).extracting(row -> row.get("trip_id")).containsExactly(foreignTrip);
        assertThat(request(owner, HttpMethod.GET, "/api/v1/trips/" + ownTrip, null).getBody())
            .containsEntry("vehicle_id", vehicle).containsEntry("total_distance_km", 12.5).containsEntry("is_active", false);
        assertThat(request(outsider, HttpMethod.GET, "/api/v1/trips/" + ownTrip, null).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> csv = http.exchange("/api/v1/trips/export", HttpMethod.GET,
            new HttpEntity<>(owner), String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("text/csv"))).isTrue();
        assertThat(csv.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment; filename=\"trips-");
        assertThat(csv.getBody()).contains("trip_id,vehicle_id,start_time", ownTrip + "," + vehicle,
            "2026-01-02T10:00:00Z,2026-01-02T10:30:00Z,30").doesNotContain(foreignTrip);
        assertThat(csv.getBody().lines()).hasSize(2);
        ResponseEntity<String> inRange = http.exchange("/api/v1/trips/export?vehicleId=" + vehicle
            + "&start=2026-01-01T00:00:00Z&end=2026-01-03T00:00:00Z", HttpMethod.GET,
            new HttpEntity<>(owner), String.class);
        assertThat(inRange.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inRange.getBody()).contains(ownTrip).doesNotContain(foreignTrip);
        assertThat(inRange.getBody().lines()).hasSize(2);
        ResponseEntity<String> foreignCsv = http.exchange("/api/v1/trips/export?vehicleId=" + vehicle,
            HttpMethod.GET, new HttpEntity<>(outsider), String.class);
        assertThat(foreignCsv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(foreignCsv.getBody().lines()).hasSize(1);
        ResponseEntity<String> outsideRange = http.exchange("/api/v1/trips/export?vehicleId=" + vehicle
            + "&start=2026-02-01T00:00:00Z&end=2026-02-02T00:00:00Z", HttpMethod.GET,
            new HttpEntity<>(owner), String.class);
        assertThat(outsideRange.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(outsideRange.getBody().lines()).hasSize(1);
    }

    @Test void alertRules_overspeedTemplate_validatesAndIsolatesLifecycleWithRealRedis() {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders outsider = tenantHeaders();
        String vehicle = createVehicle(owner);
        String key = "fleet-speed-" + IDS.incrementAndGet();
        String path = "/alerts/rules/" + key;
        Map<String, Object> template = Map.of("rule_key", key, "rule_name", "Fleet speed limit", "speed_limit", 80,
            "vehicle_ids", List.of(vehicle), "enabled", false);
        // when / then
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed",
            Map.of("rule_key", key, "rule_name", "Invalid speed", "speed_limit", -1, "vehicle_ids", List.of(vehicle)))
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> created = request(owner, HttpMethod.POST, "/alerts/rules/templates/overspeed", template);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("rule_key", key).containsEntry("rule_type", "SPEED")
            .containsEntry("enabled", false);
        assertThat(request(owner, HttpMethod.GET, path, null).getBody()).containsEntry("rule_key", key);
        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(request(outsider, HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        softly.assertThat(request(outsider, HttpMethod.PUT, path, Map.of("rule_name", "Intruder", "priority", 2))
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        softly.assertThat(request(outsider, HttpMethod.DELETE, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("rule_name", "Updated speed limit", "priority", 2))
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(owner, HttpMethod.POST, "/alerts/rules/batch/enable", List.of(key)).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(request(owner, HttpMethod.GET, path, null).getBody()).containsEntry("enabled", true)
            .containsEntry("rule_name", "Updated speed limit");
        assertThat(content(owner, "/alerts/rules?enabled=true")).extracting(row -> row.get("rule_key")).containsExactly(key);
        assertThat(content(outsider, "/alerts/rules?enabled=true")).isEmpty();
        assertThat(request(owner, HttpMethod.DELETE, path, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        softly.assertThat(request(owner, HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(content(owner, "/alerts/rules?enabled=true")).isEmpty();
        softly.assertAll();
    }

    private String provisionUser() {
        String email = "deployment-" + IDS.incrementAndGet() + "@example.com";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Organization org = organizations.save(Organization.create(email, "deployment-" + IDS.get()));
            Role role = Role.orgRole(org, "TENANT_ADMIN", "test fixture tenant administrator");
            permissions.findAll().forEach(role::addPermission);
            roles.save(role);
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwords.hash(USER_PASSWORD));
            user.assignRole(role);
            users.save(user);
        });
        return email;
    }

    @Test void currentAccess_realPostgres_reflectsPermissionRemovalAndSessionRevocation() {
        String email = provisionUser();
        var tokens = auth.login(email, USER_PASSWORD, "127.0.0.1", "effective-access-fixture");
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokens.getAccessToken());
        var initial = request(headers, HttpMethod.GET, "/api/v1/users/me/access", null);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat((List<String>) initial.getBody().get("permissions")).contains("vehicles.read");
        assertThat(initial.getBody()).doesNotContainKeys("session_id", "access_token", "refresh_token");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var member = users.findByEmail(email).orElseThrow();
            var role = member.getRoles().iterator().next();
            role.getPermissions().removeIf(permission -> permission.getKey().equals("vehicles.read"));
        });
        var changed = request(headers, HttpMethod.GET, "/api/v1/users/me/access", null);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<String>) changed.getBody().get("permissions")).doesNotContain("vehicles.read");
        assertThat(request(headers, HttpMethod.GET, "/api/v1/vehicles", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(headers, HttpMethod.POST, "/api/v1/auth/logout", null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(request(headers, HttpMethod.GET, "/api/v1/users/me/access", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> login(String email) {
        return http.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", USER_PASSWORD), Map.class);
    }

    @Test void subscription_usage_realPostgres_serializesFinalUnitAndReplaysReceipt() throws Exception {
        var admin = scopedSystemAdministratorHeaders();
        var outsider = scopedSystemAdministratorHeaders();
        String base = "/api/v1/subscriptions";
        var plan = java.util.UUID.randomUUID().toString();
        assertThat(request(admin, HttpMethod.POST, base + "/catalog", Map.of(
            "plan_id", plan, "version", 1, "model", "USAGE_TOKEN", "meter", "locations",
            "usage_limit", 1, "amount", new java.math.BigDecimal("0.25"), "currency", "EUR")).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        var enrolled = request(admin, HttpMethod.POST, base, Map.of("plan_id", plan, "version", 1, "operation_key", "enroll"));
        assertThat(enrolled.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = base + "/" + enrolled.getBody().get("id");
        var grant = Map.of("operation_key", "grant", "kind", "COMPLIMENTARY", "reason", "vendor acceptance",
            "start", "2026-01-01T00:00:00Z", "end", "2099-01-01T00:00:00Z", "units", 1);
        assertThat(request(outsider, HttpMethod.POST, path + "/grants", grant).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(admin, HttpMethod.POST, path + "/grants", grant).getStatusCode()).isEqualTo(HttpStatus.OK);
        var pool = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        Map winningReceipt = null;
        String winningKey = null;
        try {
            var work = new java.util.ArrayList<Future<ResponseEntity<Map>>>();
            for (int i = 0; i < 6; i++) {
                String key = "use-" + i;
                work.add(pool.submit(() -> { start.await(); return request(admin, HttpMethod.POST,
                    path + "/consumptions", Map.of("operation_key", key, "meter", "locations", "units", 1)); }));
            }
            start.countDown();
            int successes = 0;
            for (int i = 0; i < work.size(); i++) {
                var response = work.get(i).get(20, TimeUnit.SECONDS);
                if (response.getStatusCode() == HttpStatus.OK) {
                    successes++; winningReceipt = response.getBody(); winningKey = "use-" + i;
                } else assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            }
            assertThat(successes).isEqualTo(1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(request(admin, HttpMethod.POST, path + "/consumptions",
            Map.of("operation_key", winningKey, "meter", "locations", "units", 1)).getBody()).isEqualTo(winningReceipt);
        assertThat(request(admin, HttpMethod.GET, path, null).getBody()).containsEntry("remaining_units", 0);
        assertThat(request(outsider, HttpMethod.POST, path + "/consumptions",
            Map.of("operation_key", winningKey, "meter", "locations", "units", 1)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpHeaders scopedSystemAdministratorHeaders() {
        String email = provisionUser();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var user = users.findByEmail(email).orElseThrow();
            user.assignRole(roles.findByNameAndOrgIsNull("SUPER_ADMIN").orElseThrow());
        });
        var tokens = auth.login(email, USER_PASSWORD, "127.0.0.1", "subscription-admin-fixture");
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokens.getAccessToken());
        return headers;
    }

    @Test void health_realPostgresRedisAndArtemis_returnsUp() {
        // when / then: no disabled health indicators or listeners
        ResponseEntity<Map> response = http.getForEntity("/actuator/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test void login_logout_revokesAccessTokenOverRealHttp() {
        // given: public registration stays disabled; provision through repositories
        String email = provisionUser();
        assertThat(http.postForEntity("/api/v1/auth/register", Map.of(), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        // when
        ResponseEntity<Map> login = login(email);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) login.getBody().get("access_token"));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        assertThat(http.exchange("/api/v1/users/me", HttpMethod.GET, request, Map.class).getBody())
            .containsEntry("email", email);
        assertThat(http.exchange("/api/v1/auth/logout", HttpMethod.POST, request, Void.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        // then
        assertThat(http.exchange("/api/v1/users/me", HttpMethod.GET, request, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void refresh_concurrentSameToken_commitsReplayRevocation() throws Exception {
        // given
        String email = provisionUser();
        var initial = auth.login(email, USER_PASSWORD, "127.0.0.1", "deployment-test");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> refresh = () -> {
            start.await();
            try {
                auth.refresh(initial.getRefreshToken(), "127.0.0.1", "deployment-test");
                return true;
            } catch (com.surf.tracker.userauth.infrastructure.security.SecurityException expected) {
                return false;
            }
        };
        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(refresh);
            Future<Boolean> second = executor.submit(refresh);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        }
        // then: fresh repository transactions must observe the committed revocation
        Long userId = users.findByEmail(email).orElseThrow().getId();
        assertThat(sessions.findByUserId(userId)).isNotEmpty().allSatisfy(session -> {
            assertThat(session.getRevokedAt()).isNotNull();
            assertThat(tokens.findBySessionId(session.getId())).isNotEmpty().allSatisfy(token ->
                assertThat(token.getRevokedAt()).isNotNull());
        });
    }

    @Test void messaging_realArtemis_roundTripsAuthenticatedQueueMessage() {
        // given: isolated queue, not an application's telemetry destination
        String destination = "deployment.validation." + IDS.incrementAndGet();
        queue.setReceiveTimeout(5000);
        // when
        queue.convertAndSend(destination, "deployment-check");
        // then
        assertThat(queue.receiveAndConvert(destination)).isEqualTo("deployment-check");
    }

    @Test void redis_realServer_roundTripsExpiringValue() {
        String key = "deployment:validation:" + IDS.incrementAndGet();
        try {
            redis.opsForValue().set(key, "test", Duration.ofMinutes(1));
            assertThat(redis.opsForValue().get(key)).isEqualTo("test");
        } finally {
            redis.delete(key);
        }
    }

    @Test void migration_upgradeFromV2_revokesLegacySessionsAndTokens() {
        // given: separate schema in the same disposable PostgreSQL, migrated only to V2
        String[] locations = {"classpath:db/postgresql"};
        Flyway old = Flyway.configure().dataSource(jdbcUrl(), "tracker_test", PASSWORD)
            .schemas("upgrade_check").locations(locations).target("2").load();
        old.migrate();
        DriverManagerDataSource source = new DriverManagerDataSource(jdbcUrl(), "tracker_test", PASSWORD);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Long user = jdbc.queryForObject("INSERT INTO upgrade_check.users (email, enabled, locked, created_at, updated_at) "
            + "VALUES ('legacy@example.com', true, false, now(), now()) RETURNING id", Long.class);
        Long session = jdbc.queryForObject("INSERT INTO upgrade_check.sessions (user_id, session_uuid, token_hash, created_at, expires_at) "
            + "VALUES (?, 'legacy-session', 'legacy-hash', now(), now() + interval '1 day') RETURNING id", Long.class, user);
        jdbc.update("INSERT INTO upgrade_check.refresh_tokens (session_id, token_hash, created_at, expires_at) "
            + "VALUES (?, 'legacy-refresh', now(), now() + interval '1 day')", session);
        // when
        var result = Flyway.configure().dataSource(jdbcUrl(), "tracker_test", PASSWORD)
            .schemas("upgrade_check").locations(locations).load().migrate();
        // then
        assertThat(result.migrationsExecuted).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_check.alert_event_outbox", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_check.sessions WHERE revoked_at IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_check.refresh_tokens WHERE revoked_at IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM upgrade_check.sessions WHERE org_id IS NULL", Long.class)).isEqualTo(1);
    }
}
