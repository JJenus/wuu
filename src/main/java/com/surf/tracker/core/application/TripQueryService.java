package com.surf.tracker.core.application;

import com.surf.tracker.core.domain.entity.Trip;
import com.surf.tracker.core.infrastructure.repository.TripRepository;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TripQueryService {
    private final TripRepository tripRepository;

    public TripQueryService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    public Page<Trip> getTrips(Pageable pageable) {
        return tripRepository.findByOrganizationId(TenantContext.getCurrentOrgId(), pageable);
    }

    public Optional<Trip> getTripById(String tripId) {
        return tripRepository.findByIdAndOrganizationId(tripId, TenantContext.getCurrentOrgId());
    }
}
