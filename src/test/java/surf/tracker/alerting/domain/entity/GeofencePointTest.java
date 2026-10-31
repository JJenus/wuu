package com.surf.tracker.alerting.domain.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.alerting.domain.enums.GeofenceShapeType;
import com.surf.tracker.shared.redis.RedisJsonSerializer;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeofencePointTest {
    @Test
    void polygon_redisRoundTrip_preservesOrderedGeometryWithoutRecursiveParentSerialization() {
        // given: the production cache serializer and the real bidirectional entity graph.
        var serializer = RedisJsonSerializer.create(new ObjectMapper().findAndRegisterModules());
        Geofence polygon = new Geofence();
        polygon.setGeofenceId(51L);
        polygon.setOrganizationId(7L);
        polygon.setName("Cached polygon");
        polygon.setShapeType(GeofenceShapeType.POLYGON);
        polygon.setVehicleIds(Set.of("OWN-1", "OWN-2"));
        polygon.addPoint(10.0, 10.0, 1);
        polygon.addPoint(10.0, 12.0, 2);
        polygon.addPoint(12.0, 10.0, 3);

        // when
        Geofence restored = (Geofence) serializer.deserialize(serializer.serialize(polygon));

        // then
        assertThat(restored.getVehicleIds()).containsExactlyInAnyOrder("OWN-1", "OWN-2");
        assertThat(restored.getPoints()).extracting(GeofencePoint::getPointOrder).containsExactly(1, 2, 3);
        assertThat(restored.getPoints()).allSatisfy(point -> assertThat(point.getGeofence()).isSameAs(restored));
        assertThat(restored.isPointInside(10.25, 10.25)).isTrue();
        assertThat(restored.isPointInside(20.0, 20.0)).isFalse();
    }
}
