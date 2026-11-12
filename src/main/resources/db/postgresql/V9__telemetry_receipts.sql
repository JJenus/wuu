-- Additive receipt ledger. Rollback: stop new consumers, then drop this new table;
-- do not remove receipts while retained packets can still be replayed.
CREATE TABLE telemetry_receipts (
    receipt_key VARCHAR(64) PRIMARY KEY,
    capture_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    organization_id BIGINT NOT NULL,
    vehicle_id VARCHAR(50) NOT NULL REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    device_id VARCHAR(50) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    update_payload TEXT,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL
);
