import { describe, expect, it, vi } from 'vitest'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import { createLiveGeofenceService } from '../../app/features/geofences/api/live-geofence-service'
import type { GeofenceDraft } from '../../app/features/geofences/domain/geofence'

const draft: GeofenceDraft = {
  name: 'Depot', description: 'Complete creation', active: true, location: '', events: '',
  vehicleIds: ['VH-1', 'VH-2'],
  geometry: { type: 'polygon', vertices: [
    { lat: 51, lng: 0 }, { lat: 51, lng: 0.1 }, { lat: 51.1, lng: 0.1 },
  ] },
}

describe('atomic live geofence creation', () => {
  it('creates polygon description and complete associations in one mutation', async () => {
    const fetchImpl = vi.fn(async (_path: RequestInfo | URL, _options?: RequestInit) => new Response(
      JSON.stringify({ geofence_id: 17, name: 'Depot', shape_type: 'POLYGON', active: true }),
      { status: 201, headers: { 'content-type': 'application/json' } },
    ))
    const service = createLiveGeofenceService(createGatewayClient({ fetchImpl }))
    await expect(service.create(draft)).resolves.toBe('17')
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(fetchImpl.mock.calls[0]![0]).toBe('/api/_wuu/geofences')
    expect(JSON.parse(String(fetchImpl.mock.calls[0]![1]!.body))).toEqual({
      name: 'Depot', description: 'Complete creation', active: true, vehicleIds: ['VH-1', 'VH-2'],
      shapeType: 'POLYGON', points: [
        { latitude: 51, longitude: 0 }, { latitude: 51, longitude: 0.1 }, { latitude: 51.1, longitude: 0.1 },
      ],
    })
  })

  it('allows an explicitly unassigned circle without silently inventing a binding', async () => {
    const fetchImpl = vi.fn(async (_path: RequestInfo | URL, _options?: RequestInit) => new Response(
      JSON.stringify({ geofence_id: 18, name: 'Depot', shape_type: 'CIRCLE', active: true }),
      { status: 201, headers: { 'content-type': 'application/json' } },
    ))
    const service = createLiveGeofenceService(createGatewayClient({ fetchImpl }))
    await expect(service.create({ ...draft, vehicleIds: [],
      geometry: { type: 'circle', center: { lat: 51, lng: 0 }, radiusMeters: 100 } })).resolves.toBe('18')
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(JSON.parse(String(fetchImpl.mock.calls[0]![1]!.body))).toMatchObject({
      vehicleIds: [], description: 'Complete creation', shapeType: 'CIRCLE', radiusMeters: 100,
    })
  })
})
