import { describe, expect, it } from 'vitest'
import { ApiError } from '../../app/features/core/domain/errors'
import { toVehicle } from '../../app/features/fleet/api/contracts'
import {
  locationFreshness,
  positionRefreshInterval,
} from '../../app/features/tracking/domain/live-tracking'

describe('live map snapshot policy', () => {
  const now = Date.parse('2026-11-12T12:00:00Z')
  const vehicle = toVehicle(
    {
      vehicleId: 'gps-1',
      currentLocation: {
        latitude: 51.5,
        longitude: -0.1,
        speedKmh: 12,
        recordedAt: '2026-11-12T12:00:00Z',
      },
    },
    now,
  )

  it('refreshes transient failures but stops for pause, permission and contract errors', () => {
    expect(positionRefreshInterval(null, false)).toBe(10000)
    expect(positionRefreshInterval(null, true)).toBe(false)
    expect(
      positionRefreshInterval(new ApiError({ kind: 'server', message: 'Offline' }), false),
    ).toBe(10000)
    for (const kind of ['unauthorized', 'forbidden', 'contract'] as const)
      expect(positionRefreshInterval(new ApiError({ kind, message: kind }), false)).toBe(false)
  })

  it('labels real location freshness without altering positions or inventing GPS', () => {
    expect(locationFreshness(vehicle, now)).toBe('Last reported location')
    expect(locationFreshness(vehicle, now + 300001)).toBe('Stale location')
    expect(locationFreshness(vehicle, now - 6000)).toBe('Location clock skew')
    expect(locationFreshness({ ...vehicle, observedAt: null }, now)).toBe('Location time unknown')
    expect(locationFreshness({ ...vehicle, position: null }, now)).toBe('No GPS location')
    expect(vehicle.position).toEqual({ lat: 51.5, lng: -0.1 })
  })
})
