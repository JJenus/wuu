import type { Vehicle } from '../../fleet/domain/fleet'
import { isRetryable } from '../../core/domain/errors'
import { isStale, parseInstant } from '../../core/domain/time'

export const POSITION_REFRESH_MS = 10_000

export function positionRefreshInterval(error: unknown, paused: boolean): number | false {
  if (paused || (error && !isRetryable(error))) return false
  return POSITION_REFRESH_MS
}

export function locationFreshness(vehicle: Vehicle, now = Date.now()): string {
  if (!vehicle.position) return 'No GPS location'
  const instant = parseInstant(vehicle.observedAt)
  if (!instant) return 'Location time unknown'
  if (instant.getTime() > now + 5000) return 'Location clock skew'
  return isStale(vehicle.observedAt, now) ? 'Stale location' : 'Last reported location'
}
