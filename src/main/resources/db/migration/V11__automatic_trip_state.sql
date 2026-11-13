ALTER TABLE vehicles ADD COLUMN trip_last_recorded_at TIMESTAMP WITH TIME ZONE;
UPDATE vehicles SET trip_last_recorded_at = (
    SELECT recorded_at FROM tracker_location_data WHERE location_id = vehicles.current_location_id
    AND recorded_at <= CURRENT_TIMESTAMP
);
ALTER TABLE trips ADD COLUMN stationary_since TIMESTAMP WITH TIME ZONE;
ALTER TABLE trips ADD COLUMN recorded_points_associated BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_trip_active_vehicle ON trips (vehicle_id, is_active);
CREATE INDEX idx_trip_points_order ON trip_points (trip_id, point_order);
CREATE INDEX idx_location_trip_recorded ON tracker_location_data (trip_id, recorded_at, location_id);
