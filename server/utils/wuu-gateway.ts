export const allowedBases: Record<string, string> = {
  vehicles: '/api/v1/vehicles',
  trips: '/api/v1/trips',
  alerts: '/alerts',
  geofences: '/alerts/geofences',
  notifications: '/api/notifications',
  subscriptions: '/api/v1/subscriptions',
  access: '/api/v1/users/me/access',
  roles: '/api/v1/roles',
}

const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]'])

/** Only customer-owned enrollment use cases are exposed, never grants or catalog administration. */
export function subscriptionRequestAllowed(method: string, target: string): boolean {
  const base = '/api/v1/subscriptions'
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
  const base =
    resource && Object.hasOwn(allowedBases, resource) ? allowedBases[resource] : undefined
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
