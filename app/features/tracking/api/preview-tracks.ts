import type { TrackPoint } from '../domain/playback'
import type { VehicleStatus } from '../../fleet/domain/fleet'

// Deliberately invented canvas paths; never historical telemetry or a live fallback.
export function movementTrack(vehicle: {
  x: number
  y: number
  status: VehicleStatus
}): TrackPoint[] {
  if (vehicle.status !== 'moving') return []
  const { x, y } = vehicle
  return [
    { seconds: 0, x, y, speed: 32 },
    { seconds: 30, x: x + 4, y: y - 7, speed: 42 },
    { seconds: 60, x: x + 9, y: y - 10, speed: 28 },
    { seconds: 90, x: x + 4, y: y - 7, speed: 38 },
    { seconds: 120, x, y, speed: 32 },
  ]
}
const paths: Record<string, { minutes: number; points: [number, number][] }> = {
  'TR-1048': {
    minutes: 24,
    points: [
      [58, 84],
      [55, 74],
      [50, 67],
      [46, 51],
      [52, 44],
      [61, 36],
      [67, 28],
    ],
  },
  'TR-1047': {
    minutes: 28,
    points: [
      [62, 84],
      [58, 74],
      [53, 65],
      [53, 65],
      [55, 48],
      [61, 34],
      [63, 23],
    ],
  },
  'TR-1046': {
    minutes: 32,
    points: [
      [47, 62],
      [48, 54],
      [54, 47],
      [54, 47],
      [61, 39],
      [67, 32],
      [72, 35],
    ],
  },
  'TR-1045': {
    minutes: 41,
    points: [
      [58, 84],
      [54, 79],
      [48, 76],
      [48, 76],
      [43, 73],
      [38, 78],
      [33, 82],
    ],
  },
  'TR-1044': {
    minutes: 38,
    points: [
      [19, 36],
      [22, 41],
      [27, 46],
      [30, 49],
      [30, 49],
      [32, 53],
      [32, 57],
    ],
  },
}
export function tripTrack(id: string): TrackPoint[] {
  const path = paths[id]
  if (!path) return []
  return path.points.map(([x, y], i) => ({
    seconds: (i * path.minutes * 60) / (path.points.length - 1),
    x,
    y,
    speed:
      i === 0 ||
      i === path.points.length - 1 ||
      (i > 0 && x === path.points[i - 1]![0] && y === path.points[i - 1]![1])
        ? 0
        : 28 + i * 3,
  }))
}
