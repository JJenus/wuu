-- Existing sessions cannot safely be assigned an active tenant. Require reauthentication.
ALTER TABLE sessions ADD COLUMN org_id BIGINT;
UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE revoked_at IS NULL;
UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE revoked_at IS NULL;
CREATE INDEX idx_session_user ON sessions(user_id);
ALTER TABLE roles ADD CONSTRAINT uk_role_org_name UNIQUE(org_id, name);
