package com.surf.tracker.core.infrastructure;

import com.surf.tracker.core.CoreTestConfiguration;
import com.surf.tracker.core.infrastructure.repository.DeviceCommandRepository;
import com.surf.tracker.core.infrastructure.repository.TrackerLocationRepository;
import com.surf.tracker.core.infrastructure.repository.TrackerRawDataRepository;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.core.infrastructure.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = CoreTestConfiguration.class)
class CoreRepositoryContextIT {

    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private TrackerRepository trackerRepository;
    @Autowired
    private TrackerLocationRepository trackerLocationRepository;
    @Autowired
    private TrackerRawDataRepository trackerRawDataRepository;
    @Autowired
    private TripRepository tripRepository;
    @Autowired
    private DeviceCommandRepository deviceCommandRepository;

    @Test
    void allCoreRepositories_BootWithValidQueries() {
        assertThat(vehicleRepository).isNotNull();
        assertThat(trackerRepository).isNotNull();
        assertThat(trackerLocationRepository).isNotNull();
        assertThat(trackerRawDataRepository).isNotNull();
        assertThat(tripRepository).isNotNull();
        assertThat(deviceCommandRepository).isNotNull();
    }
}
