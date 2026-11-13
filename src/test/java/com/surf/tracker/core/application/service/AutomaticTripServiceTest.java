package com.surf.tracker.core.application.service;

import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.core.infrastructure.repository.TripPointRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AutomaticTripServiceTest {
    @ParameterizedTest
    @CsvSource({"1,1,300,900", "NaN,1,300,900", "5,-1,300,900", "5,1,0,900", "5,1,300,299"})
    void validatePolicy_invalidThresholds_failsStartup(float start, float stationary, int stop, int gap) {
        var service = policy(start, stationary, stop, gap);
        assertThatThrownBy(service::validatePolicy).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"5,1,300,900", "5,0,1,1"})
    void validatePolicy_validThresholds_acceptsPolicy(float start, float stationary, int stop, int gap) {
        assertThatCode(policy(start, stationary, stop, gap)::validatePolicy).doesNotThrowAnyException();
    }

    private AutomaticTripService policy(float start, float stationary, int stop, int gap) {
        var service = new AutomaticTripService(mock(TripRepository.class), mock(TripPointRepository.class), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "startSpeed", start);
        ReflectionTestUtils.setField(service, "stationarySpeed", stationary);
        ReflectionTestUtils.setField(service, "stopSeconds", stop);
        ReflectionTestUtils.setField(service, "maxGapSeconds", gap);
        return service;
    }
}
