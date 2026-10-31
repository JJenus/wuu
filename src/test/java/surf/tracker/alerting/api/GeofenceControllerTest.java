package com.surf.tracker.alerting.api;

import com.surf.tracker.alerting.api.dto.UpdateGeofenceRequest;
import com.surf.tracker.alerting.application.service.GeofenceService;
import com.surf.tracker.alerting.domain.entity.Geofence;
import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeofenceControllerTest {

    @Mock
    private GeofenceService service;
    private GeofenceController controller;
    private Geofence saved;

    @BeforeEach
    void setUp() {
        controller = new GeofenceController(service);
        saved = new Geofence();
        saved.setGeofenceId(5L);
        saved.setName("Depot");
        saved.setShapeType(GeofenceShapeType.CIRCLE);
        when(service.updateGeofence(eq(5L), any(Geofence.class))).thenReturn(saved);
    }

    @Test
    void updateGeofence_omittedVehicleIds_remainsOmitted() {
        // given
        UpdateGeofenceRequest request = new UpdateGeofenceRequest();
        request.setName("Renamed depot");

        // when
        controller.updateGeofence(5L, request);

        // then: an omitted association is not an explicit request to unlink every vehicle.
        ArgumentCaptor<Geofence> updates = ArgumentCaptor.forClass(Geofence.class);
        verify(service).updateGeofence(eq(5L), updates.capture());
        assertThat(updates.getValue().getVehicleIds()).isNull();
    }

    @Test
    void updateGeofence_polygonPoints_reachesServiceWithoutLoss() throws Exception {
        // given: Boot ignores unknown JSON fields; submitted points must not disappear.
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        UpdateGeofenceRequest request = mapper.readValue("""
                {"points":[{"latitude":10.0,"longitude":10.0},
                {"latitude":10.0,"longitude":12.0},{"latitude":12.0,"longitude":10.0}]}
                """, UpdateGeofenceRequest.class);

        // when
        controller.updateGeofence(5L, request);

        // then
        ArgumentCaptor<Geofence> updates = ArgumentCaptor.forClass(Geofence.class);
        verify(service).updateGeofence(eq(5L), updates.capture());
        assertThat(updates.getValue().getPoints()).extracting(point -> point.getLatitude())
                .containsExactly(10.0, 10.0, 12.0);
    }

    @Test
    void updateGeofence_multipleAssociations_responseContainsEveryId() {
        // given
        saved.setVehicleIds(Set.of("OWN-1", "OWN-2"));

        // when
        JsonNode json = new ObjectMapper().findAndRegisterModules()
                .valueToTree(controller.updateGeofence(5L, new UpdateGeofenceRequest()).getBody());

        // then
        assertThat(json.path("vehicleIds").isArray()).isTrue();
        assertThat(json.path("vehicleIds")).hasSize(2);
        assertThat(json.path("vehicleIds").toString()).contains("OWN-1", "OWN-2");
    }

    @Test
    void updateGeofence_explicitVehicleIds_reachesServiceWithoutLoss() {
        // given
        UpdateGeofenceRequest request = new UpdateGeofenceRequest();
        request.setDescription("Authorized replacement associations");
        request.setVehicleIds(Set.of("OWN-1", "OWN-2"));

        // when
        controller.updateGeofence(5L, request);

        // then
        ArgumentCaptor<Geofence> updates = ArgumentCaptor.forClass(Geofence.class);
        verify(service).updateGeofence(eq(5L), updates.capture());
        assertThat(updates.getValue().getVehicleIds()).containsExactlyInAnyOrder("OWN-1", "OWN-2");
        assertThat(updates.getValue().getDescription()).isEqualTo(request.getDescription());
    }
}
