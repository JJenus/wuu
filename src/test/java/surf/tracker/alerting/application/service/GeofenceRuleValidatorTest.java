package com.surf.tracker.alerting.application.service;

import com.surf.tracker.alerting.api.dto.GeofenceRuleTemplateRequest;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GeofenceRuleValidatorTest {
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void validateGeofenceRuleRequest_foreignOrMissingVehicle_rejectsBeforeSaving() {
        // given
        var geofences = mock(GeofenceService.class);
        var vehicles = mock(VehicleRepository.class);
        TenantContext.setCurrentOrgId(12L);
        when(geofences.existsAndActive(1L)).thenReturn(true);
        when(vehicles.findByIdAndOrganizationId("foreign", 12L)).thenReturn(Optional.empty());
        var request = new GeofenceRuleTemplateRequest();
        request.setGeofenceId("1");
        request.setVehicleIds(Set.of("foreign"));
        // when / then
        assertThatThrownBy(() -> new GeofenceRuleValidator(geofences, vehicles)
                .validateGeofenceRuleRequest(request)).isInstanceOf(IllegalArgumentException.class);
    }
}
