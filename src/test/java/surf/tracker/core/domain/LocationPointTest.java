package com.surf.tracker.core.domain;

import com.surf.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class LocationPointTest {

    @Test
    void testValidLocationPoint() {
        Instant now = Instant.now();
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.5f, now);

        assertEquals(40.7128, location.latitude(), 0.0001);
        assertEquals(-74.0060, location.longitude(), 0.0001);
        assertEquals(60.5f, location.speedKmh(), 0.1);
        assertEquals(now, location.timestamp());
        assertTrue(location.isValid());
    }

    @Test
    void testInvalidLatitude() {
        Instant now = Instant.now();
        LocationPoint location1 = new LocationPoint(100.0, -74.0060, 60.5f, now);
        LocationPoint location2 = new LocationPoint(-100.0, -74.0060, 60.5f, now);

        assertFalse(location1.isValid());
        assertFalse(location2.isValid());
    }

    @Test
    void testInvalidLongitude() {
        Instant now = Instant.now();
        LocationPoint location1 = new LocationPoint(40.7128, 200.0, 60.5f, now);
        LocationPoint location2 = new LocationPoint(40.7128, -200.0, 60.5f, now);

        assertFalse(location1.isValid());
        assertFalse(location2.isValid());
    }

    @Test
    void testInvalidSpeed() {
        Instant now = Instant.now();
        LocationPoint location = new LocationPoint(40.7128, -74.0060, -10.0f, now);

        assertFalse(location.isValid());
    }

    @Test
    void testNullTimestamp() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.5f, null);

        assertFalse(location.isValid());
    }

    @Test
    void testDistanceCalculation() {
        // New York coordinates
        LocationPoint ny = new LocationPoint(40.7128, -74.0060, 0.0f, Instant.now());

        // Los Angeles coordinates
        LocationPoint la = new LocationPoint(34.0522, -118.2437, 0.0f, Instant.now());

        double distance = ny.distanceTo(la);

        // Distance should be approximately 3940 km
        assertTrue(distance > 3900 && distance < 4000,
            String.format("Expected distance between 3900-4000 km, got %.2f km", distance));
    }

    @Test
    void testDistanceToSamePoint() {
        Instant now = Instant.now();
        LocationPoint point1 = new LocationPoint(40.7128, -74.0060, 60.5f, now);
        LocationPoint point2 = new LocationPoint(40.7128, -74.0060, 80.0f, now.plusSeconds(10));

        double distance = point1.distanceTo(point2);

        assertEquals(0.0, distance, 0.0001, "Distance to same coordinates should be 0");
    }

    @Test
    void testRecordEquality() {
        Instant now = Instant.now();
        LocationPoint point1 = new LocationPoint(40.7128, -74.0060, 60.5f, now);
        LocationPoint point2 = new LocationPoint(40.7128, -74.0060, 60.5f, now);
        LocationPoint point3 = new LocationPoint(34.0522, -118.2437, 60.5f, now);

        assertEquals(point1, point2);
        assertNotEquals(point1, point3);
        assertEquals(point1.hashCode(), point2.hashCode());
    }

    @Test
    void testToString() {
        Instant now = Instant.now();
        LocationPoint point = new LocationPoint(40.7128, -74.0060, 60.5f, now);

        String str = point.toString();

        assertTrue(str.contains("40.7128"));
        assertTrue(str.contains("-74.006"));
        assertTrue(str.contains("60.5"));
    }
}
