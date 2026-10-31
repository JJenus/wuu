package com.surf.tracker.alerting.application.service;

import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.exception.AlertException;
import com.surf.tracker.alerting.infrastructure.cache.GeofenceCacheService;
import com.surf.tracker.alerting.infrastructure.repository.GeofenceRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeofenceServiceTest {

    private static final long ORG_ID = 7L;
    private static final long OTHER_ORG_ID = 99L;

    @Mock
    private GeofenceRepository geofenceRepository;
    @Mock
    private GeofenceCacheService geofenceCacheService;

    private GeofenceService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentOrgId(ORG_ID);
        service = new GeofenceService(geofenceRepository, geofenceCacheService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createGeofence_setsCurrentOrgIdAndSaves() {
        Geofence geofence = new Geofence();
        when(geofenceRepository.save(any(Geofence.class))).thenAnswer(inv -> inv.getArgument(0));

        Geofence saved = service.createGeofence(geofence);

        assertThat(saved.getOrganizationId()).isEqualTo(ORG_ID);
        verify(geofenceRepository).save(geofence);
    }

    @Test
    void getGeofenceById_loadsFromRepoWithOrgScopedQuery() {
        Geofence geofence = new Geofence();
        geofence.setGeofenceId(1L);
        geofence.setOrganizationId(ORG_ID);
        when(geofenceRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(geofence));

        Geofence result = service.getGeofenceById(1L);

        assertThat(result.getGeofenceId()).isEqualTo(1L);
        verify(geofenceRepository).findByIdAndOrganizationId(1L, ORG_ID);
    }

    @Test
    void getGeofenceById_notInOrg_throws() {
        when(geofenceRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGeofenceById(1L))
                .isInstanceOfSatisfying(AlertException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("ALERT_GEOFENCE_NOT_FOUND"))
                .hasMessageContaining("not found");
    }

    @Test
    void getGeofenceById_foreignCachedRecord_hasSameNotFoundContract() {
        // given: a foreign cached candidate must not bypass the tenant-scoped database lookup.
        Geofence foreign = new Geofence();
        foreign.setGeofenceId(1L);
        foreign.setOrganizationId(OTHER_ORG_ID);
        when(geofenceCacheService.getGeofenceById(1L)).thenReturn(Optional.of(foreign));
        when(geofenceRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.empty());

        // when / then: missing and foreign IDs share the domain error mapped to HTTP404.
        assertThatThrownBy(() -> service.getGeofenceById(1L))
                .isInstanceOfSatisfying(AlertException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("ALERT_GEOFENCE_NOT_FOUND"));
        verify(geofenceRepository).findByIdAndOrganizationId(1L, ORG_ID);
    }

    @Test
    void updateGeofence_usesOrgScopedQuery() {
        Geofence existing = new Geofence();
        existing.setGeofenceId(1L);
        existing.setOrganizationId(ORG_ID);
        Geofence updates = new Geofence();
        updates.setName("Updated");
        when(geofenceRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(existing));
        when(geofenceRepository.save(any(Geofence.class))).thenAnswer(inv -> inv.getArgument(0));

        Geofence result = service.updateGeofence(1L, updates);

        assertThat(result.getName()).isEqualTo("Updated");
        verify(geofenceRepository).findByIdAndOrganizationId(1L, ORG_ID);
    }

    @Test
    void deleteGeofence_usesOrgScopedQuery() {
        Geofence existing = new Geofence();
        existing.setGeofenceId(1L);
        existing.setOrganizationId(ORG_ID);
        when(geofenceRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(existing));

        service.deleteGeofence(1L);

        verify(geofenceRepository).findByIdAndOrganizationId(1L, ORG_ID);
        verify(geofenceRepository).deleteById(1L);
    }

    @Test
    void getVehicleGeofences_passesOrgIdToQuery() {
        when(geofenceCacheService.getVehicleGeofences("VEH-1")).thenReturn(null);
        when(geofenceRepository.findByVehicleId("VEH-1", ORG_ID)).thenReturn(java.util.List.of());

        service.getVehicleGeofences("VEH-1");

        verify(geofenceRepository).findByVehicleId("VEH-1", ORG_ID);
    }

    @Test
    void findNearbyGeofencesForVehicle_passesOrgIdToQuery() {
        service.findNearbyGeofencesForVehicle("VEH-1", 40.0, -74.0);

        verify(geofenceRepository).findNearbyGeofencesForVehicle("VEH-1", 40.0, -74.0, ORG_ID);
    }

    @Test
    void existsAndActive_usesOrgScopedQuery() {
        Geofence geofence = new Geofence();
        geofence.setIsActive(true);
        when(geofenceRepository.findByIdAndOrganizationId(1L, ORG_ID)).thenReturn(Optional.of(geofence));

        boolean result = service.existsAndActive(1L);

        assertThat(result).isTrue();
        verify(geofenceRepository).findByIdAndOrganizationId(1L, ORG_ID);
    }
}
