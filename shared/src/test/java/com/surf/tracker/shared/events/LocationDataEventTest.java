package com.surf.tracker.shared.events;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surf.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class LocationDataEventTest {
    @Test
    void deserialize_persistedEnvelope_preservesSourceIdentityAndOccurrence() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
        var source = new LocationDataEvent(clock, UUID.fromString("00000000-0000-0000-0000-000000000021"),
                "8168000008", new LocationPoint(51.5, -0.1, 24, clock.instant()), "AUTOSEEKER", null);
        var decoded = mapper.readValue(mapper.writeValueAsBytes(source), LocationDataEvent.class);
        assertThat(decoded).usingRecursiveComparison().isEqualTo(source);
    }
}
