package com.surf.tracker.subscription.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.subscription.application.SubscriptionService;
import com.surf.tracker.subscription.infrastructure.persistence.*;
import com.surf.tracker.userauth.api.GlobalExceptionHandler;
import com.surf.tracker.userauth.domain.entity.*;
import com.surf.tracker.userauth.infrastructure.repository.*;
import com.surf.tracker.userauth.infrastructure.security.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = SubscriptionControllerIntegrationTest.Config.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:subscriptions;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"})
class SubscriptionControllerIntegrationTest {
    private static final String BASE = "/api/v1/subscriptions";
    private static final UUID PLAN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Configuration
    @EnableAutoConfiguration(exclude = PersistenceExceptionTranslationAutoConfiguration.class)
    @EnableJpaRepositories(basePackageClasses = PlanVersionRepository.class)
    @Import({SubscriptionController.class, SubscriptionService.class, SecurityConfig.class,
        JwtAuthFilter.class, AuthIdentityService.class, AuthRateLimitFilter.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean PersistenceManagedTypes types() {
            return PersistenceManagedTypes.of(PlanVersionEntity.class.getName(), SubscriptionEntity.class.getName());
        }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean JwtService jwt() {
            var config = new JwtConfig();
            config.setSecret("subscription-test-only-secret-at-least-thirty-two-bytes");
            return new JwtService(config);
        }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean SessionRepository sessions() { return mock(SessionRepository.class); }
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired JwtService jwt;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SubscriptionService service;
    String admin;
    String owner;
    String peer;
    String foreign;
    Session ownerSession;

    @BeforeEach void setUp() {
        reset(users, sessions);
        jdbc.update("delete from subscription_enrollments");
        jdbc.update("delete from subscription_plan_versions");
        jdbc.update("delete from users where id in (801,802,803,804)");
        jdbc.update("delete from organizations where id in (810,820)");
        for (long org : List.of(810L, 820L)) {
            jdbc.update("insert into organizations(id,name,slug,created_at,updated_at) values (?,?,?,current_timestamp,current_timestamp)", org, "org" + org, "org" + org);
        }
        admin = token(801, 810, true);
        owner = token(802, 810, false);
        peer = token(803, 810, false);
        foreign = token(804, 820, false);
    }

    String token(long id, long orgId, boolean administrator) {
        jdbc.update("insert into users(id,email,enabled,locked,created_at,updated_at) values (?,?,true,false,current_timestamp,current_timestamp)", id, id + "@example.test");
        var org = Organization.create("org" + orgId, "org" + orgId); org.setId(orgId);
        var user = new User(); user.setId(id); user.setEmail(id + "@example.test");
        var role = Role.orgRole(org, "VIEWER", "viewer");
        if (administrator) role.addPermission(Permission.of("system.admin", "admin"));
        user.assignRole(role);
        var session = Session.create(user, "hash", Instant.now().plusSeconds(600)); session.setOrgId(orgId);
        when(users.findWithAuthoritiesById(id)).thenReturn(Optional.of(user));
        when(sessions.findBySessionUuidAndUserId(session.getSessionUuid(), id)).thenReturn(Optional.of(session));
        if (id == 802) ownerSession = session;
        return jwt.generateAccessToken(id, orgId, user.getEmail(), List.of(), session.getSessionUuid());
    }

    ResponseEntity<String> request(HttpMethod method, String path, String token, String body) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(BASE + path, method, new HttpEntity<>(body, headers), String.class);
    }
    String plan(long version, String model, String cadence) {
        return """
            {"plan_id":"%s","version":%d,"model":"%s","cadence":%s,
             "meter":"locations","usage_limit":100,"amount":1.234567890123456789,"currency":"EUR"}
            """.formatted(PLAN, version, model, cadence == null ? "null" : "\"" + cadence + "\"");
    }
    void createPlan() { var body = plan(1, "USAGE_TOKEN", null); var response = request(HttpMethod.POST, "/catalog", admin, body); assertThat(response.getStatusCode().value()).as("%s -> %s", body, response.getBody()).isEqualTo(201); }
    String enroll(String key, long version) {
        return "{\"plan_id\":\"" + PLAN + "\",\"version\":" + version + ",\"operation_key\":\"" + key + "\"}";
    }
    JsonNode body(ResponseEntity<String> response) throws Exception {
        return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .readTree(response.getBody());
    }

    @Test void catalog_authenticatedModelsAndCadences_roundTripExactImmutableVersions() throws Exception {
        // given / when
        createPlan();
        assertThat(request(HttpMethod.POST, "/catalog", admin, plan(2, "PERIODIC", "MONTHLY")).getStatusCode().value()).isEqualTo(201);
        assertThat(request(HttpMethod.POST, "/catalog", admin, plan(3, "PERIODIC", "YEARLY")).getStatusCode().value()).isEqualTo(201);
        // then
        var catalog = body(request(HttpMethod.GET, "/catalog", owner, null));
        assertThat(catalog.size()).isEqualTo(3);
        var version = body(request(HttpMethod.GET, "/catalog/" + PLAN + "/1", owner, null));
        assertThat(version.get("price").get("amount").decimalValue()).isEqualByComparingTo("1.234567890123456789");
        assertThat(request(HttpMethod.POST, "/catalog", admin, plan(1, "PERIODIC", "YEARLY")).getStatusCode().value()).isEqualTo(409);
        assertThat(body(request(HttpMethod.GET, "/catalog/" + PLAN + "/1", owner, null))).isEqualTo(version);
        assertThat(request(HttpMethod.PUT, "/catalog/" + PLAN + "/1", admin, "{}").getStatusCode().value()).isEqualTo(405);
        assertThat(request(HttpMethod.DELETE, "/catalog/" + PLAN + "/1", admin, null).getStatusCode().value()).isEqualTo(405);
    }

    @Test void catalog_invalidDomainConfiguration_returns400WithoutWrites() {
        // given
        var valid = plan(1, "USAGE_TOKEN", null);
        // when / then
        for (String invalid : List.of(valid.replace("100", "0"), valid.replace("1.234567890123456789", "-1"),
                valid.replace("EUR", "NOPE"), valid.replace("locations", " "), plan(0, "USAGE_TOKEN", null),
                plan(1, "USAGE_TOKEN", "MONTHLY"), plan(1, "PERIODIC", null), valid.replace("\"amount\":1.234567890123456789", "\"amount\":null"),
                valid.replace("1.234567890123456789", "1.2345678901234567891"),
                valid.replace("1.234567890123456789", "123456789012345678901234567890123456789"))) {
            assertThat(request(HttpMethod.POST, "/catalog", admin, invalid).getStatusCode().value()).isEqualTo(400);
        }
        assertThat(jdbc.queryForObject("select count(*) from subscription_plan_versions", Long.class)).isZero();
    }

    @Test void catalog_oversizedMeter_returns400WithoutWrites() {
        var invalid = plan(1, "USAGE_TOKEN", null).replace("locations", "m".repeat(101));

        assertThat(request(HttpMethod.POST, "/catalog", admin, invalid).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from subscription_plan_versions", Long.class)).isZero();
    }

    @Test void catalog_extremePriceExponent_returns400WithoutWrites() {
        var invalid = plan(1, "USAGE_TOKEN", null).replace("1.234567890123456789", "1e2147483647");

        assertThat(request(HttpMethod.POST, "/catalog", admin, invalid).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from subscription_plan_versions", Long.class)).isZero();
    }

    @Test void authentication_missingRevokedAndNonAdmin_deniesAccess() {
        // given / when / then
        assertThat(request(HttpMethod.GET, "/catalog", null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(request(HttpMethod.POST, "/catalog", owner, plan(1, "USAGE_TOKEN", null)).getStatusCode().value()).isEqualTo(403);
        ownerSession.revokeAt(Instant.now());
        assertThat(request(HttpMethod.GET, "/catalog", owner, null).getStatusCode().value()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from subscription_plan_versions", Long.class)).isZero();
    }

    @Test void enrollment_replayCancelAndOwnership_remainsPendingWithoutGrant() throws Exception {
        // given
        createPlan();
        var created = request(HttpMethod.POST, "", owner, enroll("enroll-1", 1));
        var original = body(created); String id = original.get("id").asText();
        // when / then
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(original.get("status").asText()).isEqualTo("PENDING");
        assertThat(original.get("remaining_units").asLong()).isZero();
        assertThat(body(request(HttpMethod.POST, "", owner, enroll("enroll-1", 1)))).isEqualTo(original);
        for (String other : List.of(peer, foreign)) {
            assertThat(body(request(HttpMethod.GET, "", other, null)).size()).isZero();
            assertThat(request(HttpMethod.GET, "/" + id, other, null).getStatusCode().value()).isEqualTo(404);
            assertThat(request(HttpMethod.POST, "/" + id + "/cancel", other, "{}").getStatusCode().value()).isEqualTo(404);
        }
        var canceled = body(request(HttpMethod.POST, "/" + id + "/cancel", owner, "{}"));
        assertThat(canceled.get("status").asText()).isEqualTo("CANCELED");
        assertThat(body(request(HttpMethod.POST, "/" + id + "/cancel", owner, "{}"))).isEqualTo(canceled);
        assertThat(body(request(HttpMethod.POST, "", owner, enroll("enroll-1", 1)))).isEqualTo(original);
        assertThat(body(request(HttpMethod.GET, "/" + id, owner, null))).isEqualTo(canceled);
        assertThat(request(HttpMethod.POST, "", owner, enroll("enroll-1", 99)).getStatusCode().value()).isEqualTo(409);
        assertThat(request(HttpMethod.POST, "", peer, enroll("enroll-1", 1)).getStatusCode().value()).isEqualTo(201);
    }

    @Test void enrollment_missingPlanAndInvalidKeys_rollBackWithoutReservingKey() {
        // given / when / then
        assertThat(request(HttpMethod.POST, "", owner, enroll("retry", 1)).getStatusCode().value()).isEqualTo(404);
        createPlan();
        assertThat(request(HttpMethod.POST, "", owner, enroll(" ", 1)).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST, "", owner, enroll("x".repeat(201), 1)).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from subscription_enrollments", Long.class)).isZero();
        assertThat(request(HttpMethod.POST, "", owner, enroll("retry", 1)).getStatusCode().value()).isEqualTo(201);
    }

    @Test void enrollment_concurrentSameKey_insertsOnceAndReturnsOriginal() throws Exception {
        // given
        createPlan(); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Future<ResponseEntity<String>>>();
            for (int i = 0; i < 6; i++) tasks.add(pool.submit(() -> { start.await(); return request(HttpMethod.POST, "", owner, enroll("race", 1)); }));
            // when
            start.countDown(); var responses = new ArrayList<JsonNode>();
            for (var task : tasks) { var response = task.get(20, TimeUnit.SECONDS); assertThat(response.getStatusCode().value()).isIn(200, 201); responses.add(body(response)); }
            // then
            assertThat(responses).allMatch(responses.getFirst()::equals);
            assertThat(jdbc.queryForObject("select count(*) from subscription_enrollments", Long.class)).isEqualTo(1);
        }
    }

    @Test void enrollment_databaseRollback_doesNotReserveOperationKey() {
        // given
        createPlan();
        var tx = new TransactionTemplate(transactions);
        // when / then: real FK failure rolls back rather than creating an orphan enrollment
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update("insert into subscription_enrollments(id,organization_id,user_id,operation_key,plan_version_id,status,created_at) values (?,?,?,?,?,'PENDING',current_timestamp)", UUID.randomUUID(), 810, 802, "rollback", UUID.randomUUID())))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(request(HttpMethod.POST, "", owner, enroll("rollback", 1)).getStatusCode().value()).isEqualTo(201);
    }
}
