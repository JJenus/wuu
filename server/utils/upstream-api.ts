/** Server-only Java route registry. UI/adapters address BFF resources, never these URLs. */
const apiBase = '/api/v1'
const alerts = `${apiBase}/alerts`
const notifications = `${apiBase}/notifications`
const auth = `${apiBase}/auth`
const users = `${apiBase}/users`

export const upstreamApi = Object.freeze({
  vehicles: `${apiBase}/vehicles`,
  trackers: `${apiBase}/trackers`,
  trips: `${apiBase}/trips`,
  alerts,
  geofences: `${alerts}/geofences`,
  alertRules: `${alerts}/rules`,
  notifications,
  subscriptions: `${apiBase}/subscriptions`,
  access: `${users}/me/access`,
  roles: `${apiBase}/roles`,
  team: users,
  me: `${users}/me`,
  login: `${auth}/login`,
  refresh: `${auth}/refresh`,
  logout: `${auth}/logout`,
  localAccounts: `${auth}/local-accounts`,
})

/** Match an exact resource boundary, not misleading prefixes such as subscriptions-evil. */
export function upstreamSuffix(target: string, base: string): string | null {
  if (target === base) return ''
  return target.startsWith(`${base}/`) ? target.slice(base.length) : null
}
