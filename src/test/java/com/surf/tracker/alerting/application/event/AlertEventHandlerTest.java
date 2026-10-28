package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.application.AlertingEngine;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.VehicleUpdatedEvent;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertEventHandlerTest {
    private final AlertingEngine engine = mock(AlertingEngine.class);
    private final AlertEventHandler handler = new AlertEventHandler(engine);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clear_context() { TenantContext.clear(); }

    @Test
    void handleVehicleUpdate_tenant_event_scopes_evaluation_and_restores_context() {
        // given
        TenantContext.setCurrentOrgId(99L);
        TenantContext.setCurrentUserId(88L);
        VehicleUpdatedEvent event = event(11L);
        doAnswer(invocation -> {
            assertThat(TenantContext.getCurrentOrgId()).isEqualTo(11L);
            assertThat(TenantContext.getCurrentUserId()).isNull();
            throw new RuntimeException("evaluation failed");
        }).when(engine).processVehicleUpdate(event);
        // when / then
        assertThatThrownBy(() -> handler.handleVehicleUpdate(event)).hasMessage("evaluation failed");
        assertThat(TenantContext.getCurrentOrgId()).isEqualTo(99L);
        assertThat(TenantContext.getCurrentUserId()).isEqualTo(88L);
    }

    @Test
    void handleVehicleUpdate_missing_owner_fails_closed() {
        // given
        VehicleUpdatedEvent event = event(null);
        // when / then
        assertThatThrownBy(() -> handler.handleVehicleUpdate(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(engine);
    }

    private VehicleUpdatedEvent event(Long org) {
        return new VehicleUpdatedEvent(clock, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "vehicle-1", org, new LocationPoint(1, 2, 0, clock.instant()), Map.of());
    }
}
