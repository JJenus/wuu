package com.surf.tracker.alerting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.api.dto.*;
import com.surf.tracker.alerting.application.event.AlertCreationEventHandler;
import com.surf.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.surf.tracker.alerting.domain.AlertDetectedEvent;
import com.surf.tracker.alerting.domain.enums.AlertType;
import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import com.surf.tracker.core.api.dto.CreateVehicleRequest;
import com.surf.tracker.core.api.dto.VehicleResponse;
import com.surf.tracker.main.TrackingEngineApplication;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.security.TenantContext;
import com.surf.tracker.userauth.application.dto.RegisterRequest;
import com.surf.tracker.userauth.application.dto.RegisterResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/** Opt-in: owned loopback PostgreSQL/Redis only. Replaces broker transport, not evaluation/persistence. */
@EnabledIfSystemProperty(named = "rules.test.jdbc", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
@SpringBootTest(classes = TrackingEngineApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"userauth.jwt.secret=rules-test-only-jwt-secret-at-least-thirty-two-bytes",
                "userauth.registration-enabled=true", "userauth.password.bcrypt-strength=4",
                "spring.jms.listener.auto-startup=false", "spring.artemis.user=test-only", "spring.artemis.password=test-only",
                "tracking.alert-outbox.poll-delay-ms=3600000"})
@ActiveProfiles("postgres")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeofenceRuleEvaluationIT {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("rules.test.jdbc", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/rules_test"))
            throw new IllegalArgumentException("Use an owned loopback rules_test database");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "rules_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.data.redis.host", () -> "127.0.0.1");
        properties.add("spring.data.redis.port", () -> System.getProperty("rules.test.redis.port"));
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired AlertingEngine engine;
    @Autowired AlertCreationEventHandler persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoBean EventPublisher publisher;
    @MockitoSpyBean AlertRuleEvaluationService evaluation;
    private HttpHeaders alpha;
    private HttpHeaders beta;
    private long alphaOrg;
    private int sequence;
    private final List<AlertDetectedEvent> events = new ArrayList<>();
    private final List<AlertDetectedEvent> evaluated = new ArrayList<>();

    @BeforeAll void accounts() {
        // Use the actual HTTP snake_case mapper with concrete DTOs in both directions.
        http.getRestTemplate().getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.setObjectMapper(mapper));
        RegisterResponse owner = account("alpha");
        alpha = authorization(owner);
        beta = authorization(account("beta"));
        alphaOrg = owner.getOrganizationId();
    }
    private RegisterResponse account(String label) {
        var input = new RegisterRequest();
        input.setEmail(label + "@rules-test.example"); input.setPassword("test-only-password");
        input.setFirstName("Rules"); input.setLastName(label); input.setOrganizationName("Owned rule test " + label);
        var response = http.postForEntity("/api/v1/auth/register", input, RegisterResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
    private HttpHeaders authorization(RegisterResponse account) {
        var headers = new HttpHeaders(); headers.setBearerAuth(account.getAccessToken()); return headers;
    }
    @BeforeEach void collectAlerts() {
        events.clear();
        evaluated.clear();
        doAnswer(invocation -> {
            // Mockito's dynamic invocation API is the only untyped boundary here.
            AlertDetectedEvent event = AlertDetectedEvent.class.cast(invocation.callRealMethod());
            if (event != null) evaluated.add(event);
            return event;
        }).when(evaluation).evaluateRule(any(), anyString(), any());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof AlertDetectedEvent event) {
                events.add(event);
                // Explicit in-process transport substitute: handler/outbox/database stay real.
                persistence.handleAlertEvent(event);
            }
            return null;
        }).when(publisher).publish(any());
    }
    @AfterEach void clearContext() { TenantContext.clear(); }

    @ParameterizedTest
    @CsvSource({"CIRCLE,ENTRY", "CIRCLE,EXIT", "CIRCLE,BOTH", "POLYGON,ENTRY", "POLYGON,EXIT", "POLYGON,BOTH"})
    void evaluate_persistedRule_honorsTransitionsCooldownAndInactiveBoundary(String shape, String action) {
        // given: actual HTTP configuration, committed PostgreSQL and production Redis caches/state.
        String vehicle = vehicle(alpha);
        String geofence = boundary(alpha, vehicle, shape);
        String key = "evaluate_" + (++sequence);
        assertThat(request(alpha, HttpMethod.POST, "/api/v1/alerts/rules/templates/geofence", rule(key, geofence, vehicle, action), AlertRuleResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TenantContext.setCurrentOrgId(alphaOrg);
        // when: each update reconstructs a domain rule from persisted configuration/state.
        update(vehicle, 11, 11); update(vehicle, 10, 10); update(vehicle, 10, 10); update(vehicle, 11, 11);
        // then
        AlertType[] expected = action.equals("ENTRY") ? new AlertType[]{AlertType.GEOFENCE_ENTRY}
                : action.equals("EXIT") ? new AlertType[]{AlertType.GEOFENCE_EXIT}
                : new AlertType[]{AlertType.GEOFENCE_ENTRY, AlertType.GEOFENCE_EXIT};
        assertThat(evaluated).extracting(AlertDetectedEvent::getAlertType).containsExactly(expected);
        // Existing policy: one publication per rule/vehicle within five minutes,
        // including opposite-direction transitions. Do not silently change it.
        assertThat(events).extracting(AlertDetectedEvent::getAlertType).containsExactly(expected[0]);
        assertThat(redis.getExpire("alert:dedup:" + key + ":" + vehicle, java.util.concurrent.TimeUnit.SECONDS))
                .isBetween(1L, 300L);
        assertThat(jdbc.queryForObject("select count(*) from tracker_alerts where vehicle_id=?", Integer.class, vehicle))
                .isEqualTo(1);
        Long alert = jdbc.queryForObject("select min(alert_id) from tracker_alerts where vehicle_id=?", Long.class, vehicle);
        assertThat(request(alpha, HttpMethod.GET, "/api/v1/alerts/" + alert, null, AlertResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(beta, HttpMethod.GET, "/api/v1/alerts/" + alert, null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Disabling the source boundary must stop both entry and exit evaluation.
        var disabled = new UpdateGeofenceRequest(); disabled.setActive(false);
        assertThat(request(alpha, HttpMethod.PUT, "/api/v1/alerts/geofences/" + geofence, disabled, GeofenceResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        events.clear(); evaluated.clear(); update(vehicle, 10, 10); update(vehicle, 11, 11);
        assertThat(events).isEmpty();
        assertThat(evaluated).isEmpty();
        assertThat(request(alpha, HttpMethod.PATCH, "/api/v1/alerts/rules/" + key + "/disable", null, Void.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(alpha, HttpMethod.PATCH, "/api/v1/alerts/rules/" + key + "/enable", null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select is_enabled from alert_rules where rule_key=?", Boolean.class, key)).isFalse();
    }

    @Test void createRule_foreignOrUnlinkedReferences_rejectsWithoutPersisting() {
        String own = vehicle(alpha), foreign = vehicle(beta), unlinked = vehicle(alpha);
        String zone = boundary(alpha, own, "CIRCLE"), otherZone = boundary(beta, foreign, "CIRCLE");
        assertThat(request(alpha, HttpMethod.POST, "/api/v1/alerts/rules/templates/geofence", rule("reject_foreign_zone", otherZone, own, "BOTH"), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(request(alpha, HttpMethod.POST, "/api/v1/alerts/rules/templates/geofence", rule("reject_foreign_vehicle", zone, foreign, "BOTH"), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(alpha, HttpMethod.POST, "/api/v1/alerts/rules/templates/geofence", rule("reject_unlinked_vehicle", zone, unlinked, "BOTH"), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("select count(*) from alert_rules where rule_key like 'reject_%'", Integer.class)).isZero();
    }

    private void update(String vehicle, double latitude, double longitude) {
        engine.processVehicleUpdate(vehicle, new LocationPoint(latitude, longitude, 0f, Instant.now()));
    }
    private GeofenceRuleTemplateRequest rule(String key, String geofence, String vehicle, String action) {
        var input = new GeofenceRuleTemplateRequest();
        input.setRuleKey(key); input.setRuleName("Rule evaluation"); input.setGeofenceId(geofence);
        input.setVehicleIds(Set.of(vehicle)); input.setAction(GeofenceRuleTemplateRequest.GeofenceAction.valueOf(action));
        return input;
    }
    private String vehicle(HttpHeaders owner) {
        String id = "rules-vehicle-" + (++sequence);
        var input = new CreateVehicleRequest(); input.setVehicleId(id);
        assertThat(request(owner, HttpMethod.POST, "/api/v1/vehicles", input, VehicleResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return id;
    }
    private String boundary(HttpHeaders owner, String vehicle, String shape) {
        var input = new CreateGeofenceRequest();
        input.setName("Evaluation boundary"); input.setVehicleIds(Set.of(vehicle));
        input.setShapeType(GeofenceShapeType.valueOf(shape));
        if (shape.equals("CIRCLE")) {
            input.setCenterLatitude(10d); input.setCenterLongitude(10d); input.setRadiusMeters(500);
        } else input.setPoints(List.of(point(9.99, 9.99), point(9.99, 10.01), point(10.01, 10.01), point(10.01, 9.99)));
        var created = request(owner, HttpMethod.POST, "/api/v1/alerts/geofences", input, GeofenceResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().getGeofenceId().toString();
    }
    private GeofencePointDto point(double latitude, double longitude) {
        var point = new GeofencePointDto(); point.setLatitude(latitude); point.setLongitude(longitude); return point;
    }
    private <B, T> ResponseEntity<T> request(HttpHeaders owner, HttpMethod method, String path, B body, Class<T> responseType) {
        return http.exchange(path, method, new HttpEntity<>(body, owner), responseType);
    }
}
