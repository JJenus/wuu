export interface RecordedPoint {
  locationId: number
  recordedAt: string
  latitude: number
  longitude: number
  speedKmh: number | null
}

export interface TripHistory {
  tripId: string
  vehicleId: string
  source: 'VEHICLE_TIME_WINDOW' | 'TRIP_ASSOCIATED'
  windowStart: string
  windowEnd: string
  truncated: boolean
  points: RecordedPoint[]
}

export const RECORDING_GAP_SECONDS = 120

/** Hold the last exact fix briefly; never interpolate coordinates or speed. */
export function recordedSample(points: readonly RecordedPoint[], at: number): RecordedPoint | null {
  if (!Number.isFinite(at)) return null
  let low = 0,
    high = points.length - 1,
    found = -1
  while (low <= high) {
    const mid = Math.floor((low + high) / 2)
    if (Date.parse(points[mid]!.recordedAt) <= at) {
      found = mid
      low = mid + 1
    } else high = mid - 1
  }
  const point = points[found]
  return point && at - Date.parse(point.recordedAt) <= RECORDING_GAP_SECONDS * 1000 ? point : null
}
