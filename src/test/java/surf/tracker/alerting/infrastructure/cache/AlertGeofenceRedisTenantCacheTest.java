package com.surf.tracker.alerting.infrastructure.cache;

import com.surf.tracker.alerting.domain.GeofenceTestBuilder;
import com.surf.tracker.alerting.domain.TrackerAlertTestBuilder;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.entity.TrackerAlert;
import com.surf.tracker.alerting.support.RedisCacheTestStore;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class AlertGeofenceRedisTenantCacheTest {
    private RedisCacheTestStore store;
    private RedisKeyGenerator keys;
    private AlertCacheService alerts;
    private GeofenceCacheService geofences;

    @BeforeEach
    void setUp() {
        store = new RedisCacheTestStore();
        keys = new RedisKeyGenerator();
        alerts = new AlertCacheService(store.template(), keys);
        geofences = new GeofenceCacheService(store.template(), keys);
        TenantContext.setCurrentOrgId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAlertStatistics_sameLabelAcrossTenants_neverSharesEntry() {
        // given
        alerts.cacheAlertStatistics("counts", Map.of("WARNING", 10L));

        // when
        TenantContext.setCurrentOrgId(2L);

        // then
        assertThat(alerts.getAlertStatistics("counts")).isEmpty();
    }

    @Test
    void cacheAlert_explicitEntityOwner_doesNotUseAmbientTenant() {
        // given
        TrackerAlert foreign = alert(2L);
        alerts.cacheAlert(foreign);

        // when / then
        assertThat(alerts.getAlertById(2L)).isEmpty();
        TenantContext.setCurrentOrgId(2L);
        assertThat(alerts.getAlertById(2L)).contains(foreign);
    }

    @Test
    void cacheGeofence_explicitEntityOwner_doesNotUseAmbientTenant() {
        // given
        Geofence foreign = geofence(2L);
        geofences.cacheGeofence(foreign);

        // when / then
        assertThat(geofences.getGeofenceById(2L)).isEmpty();
        TenantContext.setCurrentOrgId(2L);
        assertThat(geofences.getGeofenceById(2L)).contains(foreign);
    }

    @Test
    void getActiveVehicleAlerts_sameVehicleAcrossTenants_isolatesLists() {
        // given
        alerts.cacheActiveVehicleAlerts("same-vehicle", List.of(alert(1L)));

        // when / then
        TenantContext.setCurrentOrgId(2L);
        assertThat(alerts.getActiveVehicleAlerts("same-vehicle")).isNull();
    }

    @Test
    void getVehicleGeofences_sameVehicleAcrossTenants_isolatesLists() {
        // given
        geofences.cacheVehicleGeofences("same-vehicle", List.of(geofence(1L)));

        // when / then
        TenantContext.setCurrentOrgId(2L);
        assertThat(geofences.getVehicleGeofences("same-vehicle")).isNull();
    }

    @Test
    void getActiveVehicleAlerts_foreignPayloadInScopedKey_treatsAsMiss() {
        // given
        store.template().opsForList().rightPushAll(keys.getActiveVehicleAlertsKey("same-vehicle"), alert(2L));

        // when / then
        assertThat(alerts.getActiveVehicleAlerts("same-vehicle")).isNull();
    }

    @Test
    void getVehicleGeofences_foreignPayloadInScopedKey_treatsAsMiss() {
        // given
        store.template().opsForList().rightPushAll(keys.getVehicleGeofencesKey("same-vehicle"), geofence(2L));

        // when / then
        assertThat(geofences.getVehicleGeofences("same-vehicle")).isNull();
    }

    @Test
    void cacheGeofence_foreignOwner_invalidatesOnlyOwnerVehicleLists() {
        // given
        Geofence first = geofence(1L);
        geofences.cacheVehicleGeofences("same-vehicle", List.of(first));

        // when
        geofences.cacheGeofence(geofence(2L));

        // then
        assertThat(geofences.getVehicleGeofences("same-vehicle")).containsExactly(first);
    }

    @Test
    void getDetails_foreignPayloadInScopedKeys_treatsAsMiss() {
        // given
        store.template().opsForValue().set(keys.getAlertDetailKey(2L), alert(2L), 1L,
            java.util.concurrent.TimeUnit.HOURS);
        store.template().opsForValue().set(keys.getGeofenceDetailKey(2L), geofence(2L), 1L,
            java.util.concurrent.TimeUnit.HOURS);

        // when / then
        assertThat(alerts.getAlertById(2L)).isEmpty();
        assertThat(geofences.getGeofenceById(2L)).isEmpty();
    }

    @Test
    void readDetails_legacyGlobalEntries_ignoresUnsafeNamespace() {
        // given
        store.template().opsForValue().set(RedisKeyGenerator.ALERT_DETAIL_KEY_PREFIX + "2", alert(2L), 1L,
            java.util.concurrent.TimeUnit.HOURS);
        store.template().opsForValue().set(RedisKeyGenerator.GEOFENCE_DETAIL_KEY_PREFIX + "2", geofence(2L), 1L,
            java.util.concurrent.TimeUnit.HOURS);

        // when / then
        assertThat(alerts.getAlertById(2L)).isEmpty();
        assertThat(geofences.getGeofenceById(2L)).isEmpty();
    }

    @Test
    void cacheVehicleLists_mixedOwnership_rejectsForeignEntries() {
        // given
        alerts.cacheActiveVehicleAlerts("same-vehicle", List.of(alert(1L), alert(2L)));
        geofences.cacheVehicleGeofences("same-vehicle", List.of(geofence(1L), geofence(2L)));

        // when / then
        assertThat(alerts.getActiveVehicleAlerts("same-vehicle")).isNull();
        assertThat(geofences.getVehicleGeofences("same-vehicle")).isNull();
    }

    @Test
    void invalidateAlerts_currentTenant_preservesOtherTenantEntries() {
        // given
        alerts.cacheAlert(alert(1L));
        alerts.cacheActiveVehicleAlerts("same-vehicle", List.of(alert(1L)));
        alerts.cacheAlertStatistics("counts", Map.of("WARNING", 1L));
        TenantContext.setCurrentOrgId(2L);
        alerts.cacheAlert(alert(2L));
        alerts.cacheActiveVehicleAlerts("same-vehicle", List.of(alert(2L)));
        alerts.cacheAlertStatistics("counts", Map.of("WARNING", 2L));

        // when
        TenantContext.setCurrentOrgId(1L);
        alerts.invalidateAlert(1L);
        alerts.invalidateVehicleAlerts("same-vehicle");
        alerts.invalidateStatistics();

        // then
        assertThat(alerts.getAlertById(1L)).isEmpty();
        assertThat(alerts.getActiveVehicleAlerts("same-vehicle")).isNull();
        assertThat(alerts.getAlertStatistics("counts")).isEmpty();
        TenantContext.setCurrentOrgId(2L);
        assertThat(alerts.getAlertById(2L)).isPresent();
        assertThat(alerts.getActiveVehicleAlerts("same-vehicle")).hasSize(1);
        assertThat(alerts.getAlertStatistics("counts")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alertWrite", "alertRead", "alertListWrite", "alertListRead", "statsWrite", "statsRead",
        "alertEvict", "alertVehicleEvict", "statsEvict", "alertClear", "geofenceWrite", "geofenceRead",
        "geofenceListWrite", "geofenceListRead", "geofenceEvict", "geofenceVehicleEvict", "geofenceClear"})
    @SuppressWarnings("unchecked")
    void manualCache_redisUnavailable_preservesBestEffortBehavior(String operation) {
        // given
        RedisTemplate<String, Object> unavailable = mock(RedisTemplate.class, invocation -> {
            throw new IllegalStateException("Redis unavailable");
        });
        alerts = new AlertCacheService(unavailable, keys);
        geofences = new GeofenceCacheService(unavailable, keys);

        // when / then
        assertThatCode(() -> {
            switch (operation) {
                case "alertWrite" -> alerts.cacheAlert(alert(1L));
                case "alertRead" -> assertThat(alerts.getAlertById(1L)).isEmpty();
                case "alertListWrite" -> alerts.cacheActiveVehicleAlerts("same-vehicle", List.of(alert(1L)));
                case "alertListRead" -> assertThat(alerts.getActiveVehicleAlerts("same-vehicle")).isNull();
                case "statsWrite" -> alerts.cacheAlertStatistics("counts", Map.of());
                case "statsRead" -> assertThat(alerts.getAlertStatistics("counts")).isEmpty();
                case "alertEvict" -> alerts.invalidateAlert(1L);
                case "alertVehicleEvict" -> alerts.invalidateVehicleAlerts("same-vehicle");
                case "statsEvict" -> alerts.invalidateStatistics();
                case "alertClear" -> alerts.clearAll();
                case "geofenceWrite" -> geofences.cacheGeofence(geofence(1L));
                case "geofenceRead" -> assertThat(geofences.getGeofenceById(1L)).isEmpty();
                case "geofenceListWrite" -> geofences.cacheVehicleGeofences("same-vehicle", List.of(geofence(1L)));
                case "geofenceListRead" -> assertThat(geofences.getVehicleGeofences("same-vehicle")).isNull();
                case "geofenceEvict" -> geofences.invalidateGeofence(1L);
                case "geofenceVehicleEvict" -> geofences.invalidateVehicleGeofenceCache("same-vehicle");
                case "geofenceClear" -> geofences.clearAll();
                default -> throw new AssertionError("Unexpected operation");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void clearAll_currentTenant_preservesOtherTenantFamilies() {
        // given
        alerts.cacheAlertStatistics("counts", Map.of("WARNING", 1L));
        geofences.cacheGeofence(geofence(1L));
        TenantContext.setCurrentOrgId(2L);
        alerts.cacheAlertStatistics("counts", Map.of("WARNING", 2L));
        geofences.cacheGeofence(geofence(2L));

        // when
        TenantContext.setCurrentOrgId(1L);
        alerts.clearAll();
        geofences.clearAll();

        // then
        assertThat(alerts.getAlertStatistics("counts")).isEmpty();
        assertThat(geofences.getGeofenceById(1L)).isEmpty();
        TenantContext.setCurrentOrgId(2L);
        assertThat(alerts.getAlertStatistics("counts")).contains(Map.of("WARNING", 2L));
        assertThat(geofences.getGeofenceById(2L)).isPresent();
    }

    private TrackerAlert alert(Long owner) {
        TrackerAlert alert = TrackerAlertTestBuilder.defaultAlert().alertId(owner).vehicleId("same-vehicle").build();
        alert.setOrganizationId(owner);
        alert.setTriggeredAt(Instant.EPOCH); alert.setCreatedAt(Instant.EPOCH); alert.setUpdatedAt(Instant.EPOCH);
        return alert;
    }

    private Geofence geofence(Long owner) {
        Geofence geofence = GeofenceTestBuilder.defaultGeofence().geofenceId(owner).vehicleId("same-vehicle").build();
        geofence.setOrganizationId(owner);
        geofence.setCreatedAt(Instant.EPOCH); geofence.setUpdatedAt(Instant.EPOCH);
        return geofence;
    }
}
