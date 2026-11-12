package com.surf.tracker.alerting.api;

import com.surf.tracker.main.TrackingEngineApplication;
import com.surf.tracker.userauth.application.service.AuthService;
import com.surf.tracker.userauth.domain.entity.Organization;
import com.surf.tracker.userauth.domain.entity.Role;
import com.surf.tracker.userauth.domain.entity.User;
import com.surf.tracker.userauth.infrastructure.repository.OrganizationRepository;
import com.surf.tracker.userauth.infrastructure.repository.PermissionRepository;
import com.surf.tracker.userauth.infrastructure.repository.RoleRepository;
import com.surf.tracker.userauth.infrastructure.repository.UserRepository;
import com.surf.tracker.userauth.infrastructure.security.PasswordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Real HTTP and the same disposable PostgreSQL/Redis/broker fixture as GeofenceLifecycleIT. */
@SpringBootTest(classes = TrackingEngineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"userauth.jwt.secret=rule-test-only-secret-at-least-thirty-two-bytes",
                "userauth.registration-enabled=false", "userauth.password.bcrypt-strength=4",
                "tracking.alert-outbox.poll-delay-ms=3600000"})
@ActiveProfiles("postgres")
@DirtiesContext
class AlertRuleLifecycleIT {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final String PASSWORD = "rule-disposable-test-password";
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withEnv("POSTGRES_DB", "rule_test").withEnv("POSTGRES_USER", "rule_test")
            .withEnv("POSTGRES_PASSWORD", PASSWORD).withExposedPorts(5432);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final GenericContainer<?> ARTEMIS = new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.40.0-alpine"))
            .withEnv("ARTEMIS_USER", "rule_test").withEnv("ARTEMIS_PASSWORD", PASSWORD)
            .withEnv("EXTRA_ARGS", "--http-host 0.0.0.0 --relax-jolokia --no-autotune --java-memory 512M")
            .withEnv("ANONYMOUS_LOGIN", "false").withExposedPorts(61616)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*AMQ221007.*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        Startables.deepStart(POSTGRES, REDIS, ARTEMIS).join();
        properties.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/rule_test");
        properties.add("spring.datasource.username", () -> "rule_test");
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.artemis.broker-url", () -> "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
        properties.add("spring.artemis.user", () -> "rule_test");
        properties.add("spring.artemis.password", () -> PASSWORD);
    }

    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired PasswordService passwords;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"custom", "overspeed", "idle-timeout", "geofence", "batch"})
    void createRule_spoofedCreator_persistsAuthenticatedActorAndCurrentTime(String kind) {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String key = "actor-rule-" + IDS.incrementAndGet();
        Number userId = (Number) read(owner, "/api/v1/users/me").get("id");
        Map<String, Object> data = ruleRequest(kind, key, vehicle, owner);
        data.put("created_by", "spoofed actor");
        data.put("created_at", "1970-01-01T00:00:00Z");
        // Deliberately bound wall-clock time: this verifies the real production clock wiring.
        Instant before = Instant.now().minusSeconds(1);
        String path = switch (kind) {
            case "custom" -> "/api/v1/alerts/rules";
            case "batch" -> "/api/v1/alerts/rules/batch";
            default -> "/api/v1/alerts/rules/templates/" + kind;
        };

        // when
        ResponseEntity<?> created = http.exchange(path, HttpMethod.POST,
                new HttpEntity<>(kind.equals("batch") ? List.of(data) : data, owner), Object.class);

        // then: inspect the committed database row, not a mocked service or cached response.
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(jdbc.queryForObject("select created_by from alert_rules where rule_key = ?", String.class, key))
                .isEqualTo(userId.toString());
        Instant createdAt = jdbc.queryForObject("select created_at from alert_rules where rule_key = ?",
                java.sql.Timestamp.class, key).toInstant();
        softly.assertThat(createdAt).isBetween(before, Instant.now().plusSeconds(1));
        softly.assertThat(Instant.parse((String) read(owner, "/api/v1/alerts/rules/" + key).get("created_at")))
                .isCloseTo(createdAt, within(1, java.time.temporal.ChronoUnit.MILLIS));
        softly.assertAll();
    }

    @Test
    void updateRule_spoofedAttribution_preservesOriginalCreatorAndCreationTime() {
        // given: a second authenticated editor in the same organization.
        HttpHeaders owner = tenantHeaders();
        Map me = read(owner, "/api/v1/users/me");
        HttpHeaders editor = memberHeaders(((Number) me.get("id")).longValue());
        String key = "update-rule-" + IDS.incrementAndGet();
        assertThat(request(owner, HttpMethod.POST, "/api/v1/alerts/rules",
                ruleRequest("custom", key, createVehicle(owner), owner)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map before = jdbc.queryForMap("select created_by, created_at from alert_rules where rule_key = ?", key);

        // when
        ResponseEntity<Map> updated = request(editor, HttpMethod.PUT, "/api/v1/alerts/rules/" + key,
                Map.of("rule_name", "Edited rule", "created_by", "spoofed editor", "created_at", "1970-01-01T00:00:00Z"));

        // then
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("rule_name", "Edited rule");
        assertThat(before.get("created_by")).isEqualTo(me.get("id").toString());
        assertThat(jdbc.queryForMap("select created_by, created_at from alert_rules where rule_key = ?", key))
                .isEqualTo(before);
    }

    private Map<String, Object> ruleRequest(String kind, String key, String vehicle, HttpHeaders owner) {
        var data = new java.util.HashMap<String, Object>();
        data.put("rule_key", key);
        data.put("rule_name", "Actor attribution rule");
        data.put("vehicle_ids", List.of(vehicle));
        switch (kind) {
            case "custom", "batch" -> {
                data.put("rule_type", "SPEED");
                data.put("parameters", "{\"speedLimit\":80,\"vehicleIds\":[\"" + vehicle + "\"]}");
            }
            case "overspeed" -> data.put("speed_limit", 80);
            case "idle-timeout" -> data.put("max_idle_minutes", 10);
            case "geofence" -> {
                ResponseEntity<Map> zone = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences",
                        Map.of("vehicle_id", vehicle, "name", "Actor zone", "shape_type", "CIRCLE",
                                "center_latitude", 51.5, "center_longitude", -0.12, "radius_meters", 100));
                assertThat(zone.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                data.put("geofence_id", zone.getBody().get("geofence_id").toString());
            }
            default -> throw new IllegalArgumentException(kind);
        }
        return data;
    }

    private HttpHeaders memberHeaders(long ownerId) {
        String email = "rule-editor-" + IDS.incrementAndGet() + "@example.com";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User owner = users.findById(ownerId).orElseThrow();
            User editor = new User();
            editor.setEmail(email);
            editor.setPasswordHash(passwords.hash(PASSWORD));
            owner.getRoles().forEach(editor::assignRole);
            users.save(editor);
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.login(email, PASSWORD, "127.0.0.1", "rule-fixture").getAccessToken());
        return headers;
    }

    private Map read(HttpHeaders headers, String path) {
        ResponseEntity<Map> response = request(headers, HttpMethod.GET, path, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<Map> request(HttpHeaders headers, HttpMethod method, String path, Object body) {
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private String createVehicle(HttpHeaders headers) {
        String id = "rule-vehicle-" + IDS.incrementAndGet();
        assertThat(request(headers, HttpMethod.POST, "/api/v1/vehicles",
                Map.of("vehicle_id", id, "device_id", "device-" + id, "model", "Rule fixture"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return id;
    }

    private HttpHeaders tenantHeaders() {
        String email = "rule-" + IDS.incrementAndGet() + "@example.com";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Organization organization = organizations.save(Organization.create(email, "rule-" + IDS.get()));
            Role role = Role.orgRole(organization, "TENANT_ADMIN", "Rule isolated fixture");
            permissions.findAll().forEach(role::addPermission);
            roles.save(role);
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwords.hash(PASSWORD));
            user.assignRole(role);
            users.save(user);
        });
        var login = auth.login(email, PASSWORD, "127.0.0.1", "rule-fixture");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getAccessToken());
        return headers;
    }
}
