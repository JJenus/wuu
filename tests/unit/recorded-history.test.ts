import { describe, expect, it, vi } from 'vitest'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import { createLiveTripService } from '../../app/features/trips/api/live-trip-service'
import { recordedSample } from '../../app/features/tracking/domain/recorded-history'

const history = {
  tripId: 'journey', vehicleId: 'owned', source: 'VEHICLE_TIME_WINDOW',
  windowStart: '2026-11-12T12:00:00Z', windowEnd: '2026-11-12T12:10:00Z',
  truncated: false,
  points: [
    { locationId: 1, recordedAt: '2026-11-12T12:00:10Z', latitude: 11, longitude: 20, speedKmh: 42 },
    { locationId: 2, recordedAt: '2026-11-12T12:09:00Z', latitude: 12, longitude: 21, speedKmh: null },
  ],
} as const

describe('recorded history', () => {
  it('uses only exact recorded samples, never interpolates, and hides markers in gaps', () => {
    expect(recordedSample(history.points, Date.parse(history.windowStart))).toBeNull()
    expect(recordedSample(history.points, Date.parse('2026-11-12T12:00:15Z'))).toEqual(history.points[0])
    expect(recordedSample(history.points, Date.parse('2026-11-12T12:05:00Z'))).toBeNull()
    expect(recordedSample(history.points, Date.parse('2026-11-12T12:09:00Z'))).toEqual(history.points[1])
    expect(recordedSample([], 0)).toBeNull()
    expect(recordedSample(history.points, NaN)).toBeNull()
  })
  it('reads validated recorded data with cancellation, without preview fallback', async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify(history), {
      headers: { 'content-type': 'application/json' },
    }))
    const service = createLiveTripService(createGatewayClient({ fetchImpl }))
    const signal = new AbortController().signal
    await expect(service.history('journey', { signal })).resolves.toEqual(history)
    expect(fetchImpl).toHaveBeenCalledWith('/api/_wuu/trips/journey/history', expect.objectContaining({ signal: expect.any(AbortSignal) }))
  })
  it.each([
    { ...history, points: [...history.points].reverse() },
    { ...history, points: [{ ...history.points[0], latitude: 91 }] },
    { ...history, points: [{ ...history.points[0], recordedAt: '2026-11-13T00:00:00Z' }] },
    { ...history, source: 'INFERRED' },
  ])('rejects unsafe or unlabelled history', async (body) => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } }))
    await expect(createLiveTripService(createGatewayClient({ fetchImpl })).history('journey')).rejects.toMatchObject({ kind: 'contract' })
  })
  it('preserves empty history, truncation and service errors', async () => {
    for (const value of [{ ...history, points: [] }, { ...history, truncated: true }]) {
      const fetchImpl = vi.fn(async () => new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } }))
      await expect(createLiveTripService(createGatewayClient({ fetchImpl })).history('journey')).resolves.toEqual(value)
    }
    for (const status of [403, 404, 500]) {
      const fetchImpl = vi.fn(async () => new Response('{}', { status, headers: { 'content-type': 'application/json' } }))
      await expect(createLiveTripService(createGatewayClient({ fetchImpl })).history('journey')).rejects.toMatchObject({ status })
    }
  })
})
