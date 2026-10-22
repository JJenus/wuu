export interface GeoPoint {
  lat: number
  lng: number
}
export type Geometry =
  | { type: 'circle'; center: GeoPoint; radiusMeters: number }
  | { type: 'polygon'; vertices: GeoPoint[] }
export interface GeofenceDraft {
  name: string
  description: string
  location: string
  active: boolean
  events: string
  vehicleIds: string[]
  geometry: Geometry | null
}
export const eventOptions = ['Entry and exit', 'Entry only', 'Exit only', 'Entry, exit, and dwell']
function validPoint(p: GeoPoint) {
  return validCoordinate(p) && Math.abs(p.lat) <= 85.0511
}
export function geometryArea(g: Geometry | null): number {
  if (!g) return 0
  if (g.type === 'circle') return Math.PI * g.radiusMeters ** 2
  // Spherical polygon area in square meters (site-scale, non-antimeridian boundaries).
  const radians = Math.PI / 180
  return (
    (Math.abs(
      g.vertices.reduce((sum, p, i) => {
        const n = g.vertices[(i + 1) % g.vertices.length]!
        return (
          sum +
          (n.lng - p.lng) * radians * (2 + Math.sin(p.lat * radians) + Math.sin(n.lat * radians))
        )
      }, 0),
    ) *
      6371000 ** 2) /
    2
  )
}
export function geometrySummary(g: Geometry | null): string {
  if (!g) return 'No boundary drawn'
  return g.type === 'circle' ? `${g.radiusMeters} m radius` : `${g.vertices.length} vertices`
}
function crosses(a: GeoPoint, b: GeoPoint, c: GeoPoint, d: GeoPoint) {
  const turn = (p: GeoPoint, q: GeoPoint, r: GeoPoint) =>
    (q.lng - p.lng) * (r.lat - p.lat) - (q.lat - p.lat) * (r.lng - p.lng)
  const sign = (n: number) => (Math.abs(n) < 1e-12 ? 0 : Math.sign(n))
  const on = (p: GeoPoint, q: GeoPoint, r: GeoPoint) =>
    r.lat >= Math.min(p.lat, q.lat) - 1e-12 &&
    r.lat <= Math.max(p.lat, q.lat) + 1e-12 &&
    r.lng >= Math.min(p.lng, q.lng) - 1e-12 &&
    r.lng <= Math.max(p.lng, q.lng) + 1e-12
  const [s, t, u, v] = [
    sign(turn(a, b, c)),
    sign(turn(a, b, d)),
    sign(turn(c, d, a)),
    sign(turn(c, d, b)),
  ]
  return (
    (s !== t && u !== v) ||
    (s === 0 && on(a, b, c)) ||
    (t === 0 && on(a, b, d)) ||
    (u === 0 && on(c, d, a)) ||
    (v === 0 && on(c, d, b))
  )
}
export function validateGeofence(
  input: GeofenceDraft,
  existing: readonly { id: string; name: string }[],
  vehicleIds: readonly string[],
  editingId?: string,
): Record<string, string> {
  const errors: Record<string, string> = {}
  const name = input.name.trim()
  if (!name || name.length > 100)
    errors.name = 'Enter a geofence name between 1 and 100 characters.'
  else if (
    existing.some((z) => z.id !== editingId && z.name.trim().toLowerCase() === name.toLowerCase())
  )
    errors.name = 'A geofence with this name already exists.'
  if (input.description.length > 500) errors.description = 'Keep the description to 500 characters.'
  if (input.location.length > 100) errors.location = 'Keep the location label to 100 characters.'
  if (!input.vehicleIds.length || input.vehicleIds.some((id) => !vehicleIds.includes(id)))
    errors.vehicleIds = 'Choose at least one vehicle from the preview fleet.'
  if (!eventOptions.includes(input.events))
    errors.events = 'Choose a supported preview event setting.'
  const g = input.geometry
  if (!g) errors.geometry = 'Draw a circle or polygon boundary before saving.'
  else if (g.type === 'circle') {
    if (!validPoint(g.center))
      errors.geometry = 'Use valid longitude and a latitude between −85.0511 and 85.0511.'
    else if (!Number.isInteger(g.radiusMeters) || g.radiusMeters < 50 || g.radiusMeters > 5000)
      errors.geometry = 'Use a whole-number radius between 50 and 5,000 meters.'
  } else {
    const v = g.vertices
    if (v.length < 3 || v.length > 50)
      errors.geometry = 'A polygon needs between 3 and 50 vertices.'
    else if (v.some((p) => !validPoint(p)))
      errors.geometry = 'Use valid longitude and latitudes between −85.0511 and 85.0511.'
    else if (Math.max(...v.map((p) => p.lng)) - Math.min(...v.map((p) => p.lng)) > 180)
      errors.geometry = 'Boundaries crossing the antimeridian are not supported.'
    else if (new Set(v.map((p) => `${p.lat.toFixed(7)},${p.lng.toFixed(7)}`)).size !== v.length)
      errors.geometry = 'Polygon vertices must be distinct.'
    else {
      for (let i = 0; i < v.length; i++)
        for (let j = i + 1; j < v.length; j++) {
          if (j === i + 1 || (i === 0 && j === v.length - 1)) continue
          if (crosses(v[i]!, v[(i + 1) % v.length]!, v[j]!, v[(j + 1) % v.length]!))
            errors.geometry = 'Polygon edges must not cross or touch.'
        }
      if (!errors.geometry && geometryArea(g) < 25)
        errors.geometry = 'Draw a polygon with an area of at least 25 square meters.'
    }
  }
  return errors
}
import { validCoordinate } from '../../maps/domain/geographic'
