import { upstreamApi } from './upstream-api'

export const allowedBases = {
  team: upstreamApi.team,
  vehicles: upstreamApi.vehicles,
  trackers: upstreamApi.trackers,
  trips: upstreamApi.trips,
  alerts: upstreamApi.alerts,
  geofences: upstreamApi.geofences,
  'geofence-rules': upstreamApi.alertRules,
  notifications: upstreamApi.notifications,
  subscriptions: upstreamApi.subscriptions,
  access: upstreamApi.access,
  roles: upstreamApi.roles,
}

function isGatewayResource(value: string): value is keyof typeof allowedBases {
  return Object.hasOwn(allowedBases, value)
}

const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]'])

/** Only customer-owned enrollment use cases are exposed, never grants or catalog administration. */
export function subscriptionRequestAllowed(method: string, target: string): boolean {
  const base = upstreamApi.subscriptions
  const id = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'
  if (method === 'GET')
    return (
      target === base || target === `${base}/catalog` || new RegExp(`^${base}/${id}$`).test(target)
    )
  return method === 'POST' && (target === base || new RegExp(`^${base}/${id}/cancel$`).test(target))
}

/**
 * Map a browser-facing gateway path to a known upstream base. Unknown resources,
 * encoded separators and traversal segments fail closed.
 */
export function upstreamPath(raw: string | undefined): string | null {
  if (!raw) return null
  const segments = raw
    .split('/')
    .filter(Boolean)
    .map((segment) => {
      try {
        return decodeURIComponent(segment)
      } catch {
        return ''
      }
    })
  if (
    !segments.length ||
    segments.some(
      (segment) =>
        !segment ||
        segment === '.' ||
        segment === '..' ||
        segment.includes('/') ||
        segment.includes('\\') ||
        /[\u0000-\u001f\u007f]/.test(segment),
    )
  )
    return null
  const [resource, ...rest] = segments
  if (resource === 'access' && rest.length) return null
  // The catalog is a tenant-selected collection; no client can address another org.
  if (resource === 'roles' && rest.length) return null
  if (
    resource === 'team' &&
    rest.length &&
    !(
      rest.length <= 2 &&
      /^[1-9][0-9]{0,15}$/.test(rest[0]!) &&
      (rest.length === 1 || rest[1] === 'roles')
    )
  )
    return null
  if (
    resource === 'geofence-rules' &&
    rest.length &&
    !(
      (rest.length === 2 && rest[0] === 'templates' && rest[1] === 'geofence') ||
      (rest.length === 2 &&
        /^[A-Za-z0-9_-]{1,100}$/.test(rest[0]!) &&
        ['enable', 'disable'].includes(rest[1]!))
    )
  )
    return null
  const base = resource && isGatewayResource(resource) ? allowedBases[resource] : undefined
  if (!base) return null
  return `${base}/${rest.map((segment) => encodeURIComponent(segment)).join('/')}`.replace(
    /\/$/,
    '',
  )
}

/** Only HTTPS or an explicit local HTTP upstream may receive the server-held token. */
export function originIsSafe(origin: string): boolean {
  try {
    const url = new URL(origin)
    if (url.username || url.password || url.search || url.hash) return false
    return (
      url.protocol === 'https:' || (url.protocol === 'http:' && loopbackHosts.has(url.hostname))
    )
  } catch {
    return false
  }
}
