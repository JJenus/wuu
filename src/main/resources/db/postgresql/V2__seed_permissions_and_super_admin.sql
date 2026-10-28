INSERT INTO permissions (permission_key, description) VALUES
  ('users.read', 'Read user information'),
  ('users.write', 'Create and update users'),
  ('users.delete', 'Delete users'),
  ('users.assign_roles', 'Assign roles to users'),
  ('organizations.read', 'Read organizations'),
  ('organizations.write', 'Create and update organizations'),
  ('roles.read', 'Read roles'),
  ('roles.write', 'Create and update roles'),
  ('vehicles.read', 'Read vehicle data'),
  ('vehicles.write', 'Create and update vehicles'),
  ('vehicles.assign_device', 'Assign devices to vehicles'),
  ('alerts.read', 'Read alerts'),
  ('alerts.write', 'Create and update alert rules'),
  ('trips.read', 'Read trip data'),
  ('trips.export', 'Export trip data'),
  ('devices.command', 'Send commands to devices'),
  ('devices.read', 'Read device data'),
  ('notifications.read', 'Read notifications'),
  ('notifications.write', 'Create and update notifications');

INSERT INTO roles (name, org_id, description, created_at)
VALUES ('SUPER_ADMIN', NULL, 'System administrator with all permissions', TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00');

INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'SUPER_ADMIN';
