import type { TrackPoint } from '../domain/playback'
import type { VehicleStatus } from '../../fleet/domain/fleet'
import { validCoordinate, type Coordinate } from '../../maps/domain/geographic'

// Fictional geographic samples. No routing/snap-to-road or historical GPS claim.
export function movementTrack(vehicle: {
  position: Coordinate | null
  status: VehicleStatus
}): TrackPoint[] {
  if (vehicle.status !== 'moving' || !validCoordinate(vehicle.position)) return []
  const { lat, lng } = vehicle.position
  return [
    { seconds: 0, lat, lng, speed: 32 },
    { seconds: 30, lat: lat + 0.001, lng: lng + 0.0013, speed: 42 },
    { seconds: 60, lat: lat + 0.002, lng: lng + 0.0026, speed: 28 },
    { seconds: 90, lat: lat + 0.001, lng: lng + 0.0013, speed: 38 },
    { seconds: 120, lat, lng, speed: 32 },
  ]
}
const paths: Record<string, { minutes: number; points: [number, number][] }> = {
  'TR-1048': {
    minutes: 24,
    points: [
      [37.755, -122.387],
      [37.764, -122.388],
      [37.772, -122.394],
      [37.7786, -122.4148],
      [37.784, -122.407],
      [37.79, -122.401],
      [37.794, -122.401],
    ],
  },
  'TR-1047': {
    minutes: 28,
    points: [
      [37.769, -122.389],
      [37.778, -122.395],
      [37.784, -122.403],
      [37.784, -122.403],
      [37.791, -122.409],
      [37.7985, -122.4112],
      [37.805, -122.41],
    ],
  },
  'TR-1046': {
    minutes: 32,
    points: [
      [37.778, -122.405],
      [37.782, -122.402],
      [37.788, -122.398],
      [37.788, -122.398],
      [37.791, -122.395],
      [37.7955, -122.3937],
      [37.799, -122.398],
    ],
  },
  'TR-1045': {
    minutes: 41,
    points: [
      [37.755, -122.387],
      [37.758, -122.395],
      [37.76, -122.403],
      [37.76, -122.403],
      [37.7599, -122.4182],
      [37.759, -122.42],
      [37.759, -122.421],
    ],
  },
  'TR-1044': {
    minutes: 38,
    points: [
      [37.78, -122.47],
      [37.78, -122.46],
      [37.78, -122.45],
      [37.778, -122.44],
      [37.778, -122.44],
      [37.776, -122.43],
      [37.7755, -122.4241],
    ],
  },
}
export function tripTrack(id: string): TrackPoint[] {
  const path = paths[id]
  if (!path) return []
  return path.points.map(([lat, lng], i) => ({
    seconds: (i * path.minutes * 60) / (path.points.length - 1),
    lat,
    lng,
    speed:
      i === 0 ||
      i === path.points.length - 1 ||
      (i > 0 && lat === path.points[i - 1]![0] && lng === path.points[i - 1]![1])
        ? 0
        : 28 + i * 3,
  }))
}
