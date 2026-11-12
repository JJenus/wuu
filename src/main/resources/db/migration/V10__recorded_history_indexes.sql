CREATE INDEX idx_location_tracker_recorded_id ON tracker_location_data (tracker_id, recorded_at, location_id);
CREATE INDEX idx_tracker_vehicle_history ON trackers (vehicle_id);
