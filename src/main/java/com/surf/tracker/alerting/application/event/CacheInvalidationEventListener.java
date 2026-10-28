package com.surf.tracker.alerting.application.event;

import com.surf.tracker.alerting.infrastructure.cache.GeofenceCacheService;
import com.surf.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CacheInvalidationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationEventListener.class);

    private final VehicleRuleCacheService vehicleRuleCacheService;
    private final GeofenceCacheService geofenceCacheService;

    public CacheInvalidationEventListener(
            VehicleRuleCacheService vehicleRuleCacheService,
            GeofenceCacheService geofenceCacheService) {
        this.vehicleRuleCacheService = vehicleRuleCacheService;
        this.geofenceCacheService = geofenceCacheService;
    }

    @EventListener
    public void handleGeofenceChange(GeofenceChangedEvent event) {
        logger.debug("Handling geofence change event for: {}", event.getGeofenceId());

        // Invalidate geofence cache
        geofenceCacheService.invalidateGeofence(event.getGeofenceId());

        // Invalidate affected vehicle caches
        if (event.getAffectedVehicleIds() != null) {
            geofenceCacheService.invalidateVehicleGeofenceCaches(event.getAffectedVehicleIds());
            event.getAffectedVehicleIds().forEach(vehicleRuleCacheService::invalidateVehicleRules);
        }
    }

    public static class GeofenceChangedEvent {
        private final Long geofenceId;
        private final Set<String> affectedVehicleIds;

        public GeofenceChangedEvent(Long geofenceId, Set<String> affectedVehicleIds) {
            this.geofenceId = geofenceId;
            this.affectedVehicleIds = affectedVehicleIds;
        }

        public Long getGeofenceId() { return geofenceId; }
        public Set<String> getAffectedVehicleIds() { return affectedVehicleIds; }
    }
}