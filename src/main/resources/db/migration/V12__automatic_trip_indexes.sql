CREATE INDEX idx_trip_active_vehicle ON trips (vehicle_id, is_active);
CREATE INDEX idx_trip_points_order ON trip_points (trip_id, point_order);
CREATE INDEX idx_location_trip_recorded ON tracker_location_data (trip_id, recorded_at, location_id);
