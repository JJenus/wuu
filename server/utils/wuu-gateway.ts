export const allowedBases: Record<string, string> = {
  vehicles: '/api/v1/vehicles',
  trips: '/api/v1/trips',
  alerts: '/alerts',
  geofences: '/alerts/geofences',
  notifications: '/api/notifications',
}

const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]'])

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
  const base = resource ? allowedBases[resource] : undefined
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
