export interface Coordinate {
  lat: number
  lng: number
}
export const defaultCenter: Coordinate = { lat: 37.7749, lng: -122.4194 }
export function validCoordinate(value: unknown): value is Coordinate {
  if (!value || typeof value !== 'object') return false
  const p = value as Coordinate
  return (
    Number.isFinite(p.lat) &&
    Number.isFinite(p.lng) &&
    Math.abs(p.lat) <= 90 &&
    Math.abs(p.lng) <= 180
  )
}
export function normalizeLocation(
  value?: { latitude: number | null; longitude: number | null } | null,
): Coordinate | null {
  const point = value ? { lat: value.latitude, lng: value.longitude } : null
  return validCoordinate(point) ? point : null
}
export function circleEdge(center: Coordinate, meters: number): Coordinate {
  const distance = meters / 6371000,
    latitude = (center.lat * Math.PI) / 180,
    longitude = (center.lng * Math.PI) / 180
  const lat = Math.asin(Math.sin(latitude) * Math.cos(distance))
  const lng =
    longitude +
    Math.atan2(
      Math.sin(distance) * Math.cos(latitude),
      Math.cos(distance) - Math.sin(latitude) * Math.sin(lat),
    )
  return { lat: (lat * 180) / Math.PI, lng: (((lng * 180) / Math.PI + 540) % 360) - 180 }
}
export interface TileConfiguration {
  url: string
  attribution: string
  attributionUrl: string
  maxZoom: number
}
export function tileConfiguration(config: TileConfiguration): TileConfiguration {
  if (
    !/^https:\/\//.test(config.url) ||
    !['{x}', '{y}', '{z}'].every((key) => config.url.includes(key)) ||
    !config.attribution.trim() ||
    !/^https:\/\//.test(config.attributionUrl) ||
    !Number.isInteger(config.maxZoom) ||
    config.maxZoom < 1 ||
    config.maxZoom > 22
  )
    throw new Error('Map provider configuration is invalid.')
  // Validate protocols and URLs without interpreting provider HTML.
  new URL(config.url)
  new URL(config.attributionUrl)
  return { ...config }
}
