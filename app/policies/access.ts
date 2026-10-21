export const roleTemplates = [
  {
    role: 'SUPER_ADMIN',
    label: 'Platform administrator',
    scope: 'Platform',
    permissions: [
      'platform.access',
      'organizations.read',
      'organizations.write',
      'users.read',
      'users.write',
      'users.delete',
      'users.assign_roles',
      'roles.read',
      'roles.write',
      'vehicles.read',
      'vehicles.write',
      'vehicles.assign_device',
      'alerts.read',
      'alerts.write',
      'trips.read',
      'trips.export',
      'devices.read',
      'devices.command',
      'notifications.read',
      'notifications.write',
    ],
  },
  {
    role: 'TENANT_ADMIN',
    label: 'Organization administrator',
    scope: 'Organization',
    permissions: [
      'users.read',
      'users.write',
      'users.delete',
      'users.assign_roles',
      'organizations.read',
      'roles.read',
      'roles.write',
      'vehicles.read',
      'vehicles.write',
      'vehicles.assign_device',
      'alerts.read',
      'alerts.write',
      'devices.read',
      'devices.command',
    ],
  },
  {
    role: 'OPERATOR',
    label: 'Fleet operator',
    scope: 'Organization',
    permissions: [
      'users.read',
      'vehicles.read',
      'alerts.read',
      'alerts.write',
      'trips.read',
      'notifications.read',
      'notifications.write',
    ],
  },
  {
    role: 'VIEWER',
    label: 'Read-only viewer',
    scope: 'Organization',
    permissions: ['vehicles.read', 'alerts.read', 'trips.read'],
  },
] as const

export type Role = (typeof roleTemplates)[number]['role']
export function can(role: string, permission: string): boolean {
  const template = roleTemplates.find((item) => item.role === role)
  return template ? (template.permissions as readonly string[]).includes(permission) : false
}
