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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in: real HTTP, migrated PostgreSQL, Redis and broker; no shared application data. */
@SpringBootTest(classes = TrackingEngineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"userauth.jwt.secret=geofence-test-only-secret-at-least-thirty-two-bytes",
                "userauth.registration-enabled=false", "userauth.password.bcrypt-strength=4",
                "tracking.alert-outbox.poll-delay-ms=3600000"})
@ActiveProfiles("postgres")
@DirtiesContext
class GeofenceLifecycleIT {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final String PASSWORD = "geofence-disposable-test-password";
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withEnv("POSTGRES_DB", "geofence_test").withEnv("POSTGRES_USER", "geofence_test")
            .withEnv("POSTGRES_PASSWORD", PASSWORD).withExposedPorts(5432);
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    private static final GenericContainer<?> ARTEMIS = new GenericContainer<>(DockerImageName.parse("apache/activemq-artemis:2.40.0-alpine"))
            .withEnv("ARTEMIS_USER", "geofence_test").withEnv("ARTEMIS_PASSWORD", PASSWORD)
            .withEnv("EXTRA_ARGS", "--http-host 0.0.0.0 --relax-jolokia --no-autotune --java-memory 512M")
            .withEnv("ANONYMOUS_LOGIN", "false").withExposedPorts(61616)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*AMQ221007.*", 1))
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        Startables.deepStart(POSTGRES, REDIS, ARTEMIS).join();
        properties.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/geofence_test");
        properties.add("spring.datasource.username", () -> "geofence_test");
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.artemis.broker-url", () -> "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616));
        properties.add("spring.artemis.user", () -> "geofence_test");
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
    @Autowired org.springframework.data.redis.core.RedisTemplate<String, Object> redis;
    @Autowired com.surf.tracker.alerting.infrastructure.cache.RedisKeyGenerator cacheKeys;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void createGeofence_completePolygon_persistsDescriptionAndAllAssociationsAtomically() {
        // given
        HttpHeaders owner = tenantHeaders();
        String first = createVehicle(owner);
        String second = createVehicle(owner);
        var data = new java.util.HashMap<String, Object>();
        data.put("name", "Complete polygon");
        data.put("description", "Gate and loading yard");
        data.put("vehicle_ids", List.of(first, second));
        data.put("shape_type", "POLYGON");
        data.put("points", triangle(10));
        data.put("active", false);

        // when
        ResponseEntity<Map> created = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", data);

        // then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = "/api/v1/alerts/geofences/" + created.getBody().get("geofence_id");
        Map saved = read(owner, path);
        assertThat(saved).containsEntry("description", "Gate and loading yard")
                .containsEntry("points", triangle(10)).containsEntry("active", false);
        assertThat((List<String>) saved.get("vehicle_ids")).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void createGeofence_legacyVehicle_preservesDescriptionAndExplicitEmptyOverridesAlias() {
        // given
        HttpHeaders owner = tenantHeaders();
        var legacy = new java.util.HashMap<>(circle(createVehicle(owner)));
        legacy.put("description", "Legacy caller description");

        // when / then
        ResponseEntity<Map> created = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", legacy);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("description", "Legacy caller description");
        legacy.put("vehicle_ids", List.of());
        ResponseEntity<Map> unlinked = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", legacy);
        assertThat(unlinked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<?>) unlinked.getBody().get("vehicle_ids")).isEmpty();
    }

    @Test
    void createGeofence_foreignPluralAssociation_rejectsWithoutPersistingAnyRow() {
        // given
        HttpHeaders owner = tenantHeaders();
        String own = createVehicle(owner);
        String foreign = createVehicle(tenantHeaders());
        var data = new java.util.HashMap<>(circle(own));
        data.put("vehicle_ids", List.of(own, foreign));
        long before = jdbc.queryForObject("select count(*) from geofences", Long.class);

        // when / then
        assertThat(request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", data).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select count(*) from geofences", Long.class)).isEqualTo(before);
    }

    @Test
    void createGeofence_spoofedCreator_persistsAuthenticatedActorAndCreationTime() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        Number userId = (Number) read(owner, "/api/v1/users/me").get("id");
        var data = new java.util.HashMap<>(circle(vehicle));
        data.put("created_by", "spoofed actor");

        // when
        ResponseEntity<Map> created = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", data);

        // then
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number id = (Number) created.getBody().get("geofence_id");
        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(jdbc.queryForObject("select created_by from geofences where geofence_id = ?", String.class, id))
                .isEqualTo(userId.toString());
        softly.assertThat(java.time.Instant.parse((String) created.getBody().get("created_at")))
                .isAfter(java.time.Instant.EPOCH);
        softly.assertAll();
    }

    @Test
    void updateGeofence_shapeTransitions_replaceOnlyRelevantGeometry() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String path = createCircle(owner, vehicle);

        // when / then
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("shape_type", "POLYGON", "points", triangle(20)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read(owner, path)).containsEntry("points", triangle(20)).containsEntry("radius_meters", null)
                .containsEntry("center_latitude", null).containsEntry("center_longitude", null);
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("shape_type", "CIRCLE", "center_latitude", 40,
                "center_longitude", 20, "radius_meters", 200)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read(owner, path)).containsEntry("shape_type", "CIRCLE").containsEntry("radius_meters", 200)
                .containsEntry("vehicle_ids", List.of(vehicle));
        Object points = read(owner, path).get("points");
        assertThat(points == null || ((List<?>) points).isEmpty()).isTrue();
    }

    @Test
    void updateGeofence_selfIntersectingPolygon_rejectsBeforeMutation() {
        // given
        HttpHeaders owner = tenantHeaders();
        String path = createPolygon(owner, createVehicle(owner));
        Map before = read(owner, path);

        // when / then: the crossing ring has nonzero signed area, so area alone is insufficient.
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("points",
                List.of(point(0, 0), point(4, 4), point(0, 4), point(3, 0))))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(read(owner, path)).isEqualTo(before);
    }

    @Test
    void updateGeofence_moreThanSupportedFiftyVertices_rejectsBeforeMutation() {
        // given: the existing editor contract caps polygon complexity at fifty vertices.
        HttpHeaders owner = tenantHeaders();
        String path = createPolygon(owner, createVehicle(owner));
        Map before = read(owner, path);
        var vertices = java.util.stream.IntStream.range(0, 51).mapToObj(i ->
                point(10 + Math.cos(i * 2 * Math.PI / 51), 10 + Math.sin(i * 2 * Math.PI / 51))).toList();

        // when / then
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("points", vertices)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(read(owner, path)).isEqualTo(before);
    }

    @Test
    void getGeofence_polygon_warmsRealRedisAndReturnsSameOrderedPoints() {
        // given
        HttpHeaders owner = tenantHeaders();
        String path = createPolygon(owner, createVehicle(owner));
        long organization = ((Number) read(owner, "/api/v1/users/me").get("organization_id")).longValue();
        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));

        // when
        read(owner, path);
        Object cached = redis.opsForValue().get(cacheKeys.getGeofenceDetailKey(id, organization));

        // then: fail if serialization is silently swallowed and every read falls back to the DB.
        assertThat(cached).isInstanceOfSatisfying(com.surf.tracker.alerting.domain.entity.Geofence.class, polygon -> {
            assertThat(polygon.getPoints()).hasSize(3);
            assertThat(polygon.isPointInside(10.25, 10.25)).isTrue();
        });
        assertThat(read(owner, path)).containsEntry("points", triangle(10));
    }

    @Test
    void updateGeofence_nameOnly_preservesPolygonAssociationsAndDisabledState() {
        // given
        HttpHeaders owner = tenantHeaders();
        String first = createVehicle(owner);
        String second = createVehicle(owner);
        String path = createPolygon(owner, first);
        assertThat(request(owner, HttpMethod.PUT, path,
                Map.of("vehicle_ids", List.of(first, second), "active", false, "description", "Keep me"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // when
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("name", "Renamed polygon"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        // then: both direct and paged reads expose the complete persisted associations.
        Map saved = read(owner, path);
        assertThat(saved).containsEntry("name", "Renamed polygon").containsEntry("active", false)
                .containsEntry("description", "Keep me").containsEntry("points", triangle(10));
        assertThat((List<String>) saved.get("vehicle_ids")).containsExactlyInAnyOrder(first, second);
        Map page = read(owner, "/api/v1/alerts/geofences");
        assertThat((List<Map>) page.get("content")).singleElement().satisfies(row ->
                assertThat((List<String>) row.get("vehicle_ids")).containsExactlyInAnyOrder(first, second));
    }

    @Test
    void updateGeofence_replacePointsAndClearAssociations_persistsExplicitChanges() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);
        String path = createPolygon(owner, vehicle);
        assertThat(http.exchange("/api/v1/alerts/geofences/vehicle/" + vehicle + "/list", HttpMethod.GET,
                new HttpEntity<>(owner), List.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // when
        assertThat(request(owner, HttpMethod.PUT, path,
                Map.of("points", triangle(20), "vehicle_ids", List.of())).getStatusCode()).isEqualTo(HttpStatus.OK);

        // then: replacement order survives DB reload, and old vehicle-list cache is invalidated.
        assertThat(read(owner, path)).containsEntry("points", triangle(20))
                .containsEntry("vehicle_ids", List.of());
        ResponseEntity<List> oldList = http.exchange("/api/v1/alerts/geofences/vehicle/" + vehicle + "/list",
                HttpMethod.GET, new HttpEntity<>(owner), List.class);
        assertThat(oldList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(oldList.getBody()).isEmpty();
    }

    @Test
    void updateGeofence_invalidGeometry_leavesPersistedAndCachedRecordUnchanged() {
        // given
        HttpHeaders owner = tenantHeaders();
        String path = createCircle(owner, createVehicle(owner));
        Map before = read(owner, path);

        // when / then
        for (Map<String, Object> invalid : List.<Map<String, Object>>of(
                Map.of("name", "Must not persist", "radius_meters", -1),
                Map.of("center_latitude", 91), Map.of("center_longitude", -181),
                Map.of("shape_type", "POLYGON", "points", List.of()),
                Map.of("shape_type", "POLYGON", "points", List.of(point(0, 0), point(1, 1), point(2, 2))))) {
            assertThat(request(owner, HttpMethod.PUT, path, invalid).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(read(owner, path)).isEqualTo(before);
        }
    }

    @Test
    void updateGeofence_foreignZoneAndVehicle_cannotMutateOrAssociateForeignData() {
        // given
        HttpHeaders owner = tenantHeaders();
        HttpHeaders foreign = tenantHeaders();
        String ownVehicle = createVehicle(owner);
        String foreignVehicle = createVehicle(foreign);
        String path = createCircle(owner, ownVehicle);
        Map before = read(owner, path);

        // when / then
        assertThat(request(foreign, HttpMethod.PUT, path, Map.of("name", "Foreign mutation"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("vehicle_ids", List.of(foreignVehicle)))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(read(owner, path)).isEqualTo(before);
        assertThat(request(owner, HttpMethod.PUT, path, Map.of("vehicle_ids", List.of("missing-vehicle")))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(read(owner, path)).isEqualTo(before);
    }

    @Test
    void createGeofence_invalidGeometry_rejectsWithoutRows() {
        // given
        HttpHeaders owner = tenantHeaders();
        String vehicle = createVehicle(owner);

        // when / then
        assertThat(request(owner, HttpMethod.POST, "/api/v1/alerts/geofences",
                Map.of("vehicle_id", vehicle, "name", "Invalid circle", "shape_type", "CIRCLE", "radius_meters", -1))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<?>) read(owner, "/api/v1/alerts/geofences").get("content")).isEmpty();
    }

    @Test
    void createGeofence_foreignVehicle_rejectsWithoutRows() {
        // given
        HttpHeaders owner = tenantHeaders();
        String foreignVehicle = createVehicle(tenantHeaders());

        // when / then
        assertThat(request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", circle(foreignVehicle))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<?>) read(owner, "/api/v1/alerts/geofences").get("content")).isEmpty();
    }

    @Test
    void updateGeofence_nullPolygonPoint_rejectsWithoutMutation() {
        // given
        HttpHeaders owner = tenantHeaders();
        String path = createPolygon(owner, createVehicle(owner));
        Map before = read(owner, path);

        // when / then
        assertThat(request(owner, HttpMethod.PUT, path,
                Map.of("points", java.util.Arrays.asList(null, null, null))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(read(owner, path)).isEqualTo(before);
    }

    private String createPolygon(HttpHeaders owner, String vehicle) {
        ResponseEntity<Map> created = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences",
                Map.of("vehicle_id", vehicle, "name", "Polygon", "shape_type", "POLYGON", "points", triangle(10)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return "/api/v1/alerts/geofences/" + created.getBody().get("geofence_id");
    }

    private String createCircle(HttpHeaders owner, String vehicle) {
        ResponseEntity<Map> created = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", circle(vehicle));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return "/api/v1/alerts/geofences/" + created.getBody().get("geofence_id");
    }

    private Map<String, Object> circle(String vehicle) {
        return Map.of("vehicle_id", vehicle, "name", "Circle", "shape_type", "CIRCLE",
                "center_latitude", 51.5, "center_longitude", -0.12, "radius_meters", 100);
    }

    private List<Map<String, Double>> triangle(double offset) {
        return List.of(point(offset, offset), point(offset, offset + 2), point(offset + 2, offset));
    }

    private Map<String, Double> point(double latitude, double longitude) {
        return Map.of("latitude", latitude, "longitude", longitude);
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
        String id = "geo-vehicle-" + IDS.incrementAndGet();
        assertThat(request(headers, HttpMethod.POST, "/api/v1/vehicles",
                Map.of("vehicle_id", id, "device_id", "device-" + id, "model", "Geofence fixture"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return id;
    }

    private HttpHeaders tenantHeaders() {
        String email = "geofence-" + IDS.incrementAndGet() + "@example.com";
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Organization organization = organizations.save(Organization.create(email, "geofence-" + IDS.get()));
            Role role = Role.orgRole(organization, "TENANT_ADMIN", "Geofence isolated fixture");
            permissions.findAll().forEach(role::addPermission);
            roles.save(role);
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(passwords.hash(PASSWORD));
            user.assignRole(role);
            users.save(user);
        });
        var login = auth.login(email, PASSWORD, "127.0.0.1", "geofence-fixture");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getAccessToken());
        return headers;
    }
}
