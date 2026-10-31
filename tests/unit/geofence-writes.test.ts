import { describe, expect, it, vi } from 'vitest'
import { createLiveGeofenceService } from '../../app/features/geofences/api/live-geofence-service'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import type { GeofenceDraft } from '../../app/features/geofences/domain/geofence'

const draft: GeofenceDraft = {
  name: 'Depot',
  description: 'Gate and loading yard',
  location: '',
  active: false,
  events: '',
  vehicleIds: ['VH-1', 'VH-2'],
  geometry: {
    type: 'polygon',
    vertices: [
      { lat: 51, lng: 0 },
      { lat: 51, lng: 0.01 },
      { lat: 51.01, lng: 0.01 },
    ],
  },
}

function fixture() {
  const fetchImpl = vi.fn(
    async (_input: RequestInfo | URL, _options?: RequestInit) =>
      new Response(
        JSON.stringify({
          geofence_id: 9,
          name: 'Depot',
          shape_type: 'POLYGON',
          active: false,
          vehicle_ids: ['VH-1', 'VH-2'],
        }),
        { headers: { 'content-type': 'application/json' } },
      ),
  )
  return { fetchImpl, service: createLiveGeofenceService(createGatewayClient({ fetchImpl })) }
}

describe('live geofence lossless updates', () => {
  it('saves description, ordered polygon and every assignment in one request', async () => {
    const { service, fetchImpl } = fixture()
    await service.update('9', draft)
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    const [path, options] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit]
    expect(path).toBe('/api/_wuu/geofences/9')
    expect(options.method).toBe('PUT')
    expect(JSON.parse(String(options.body))).toEqual({
      name: 'Depot',
      description: 'Gate and loading yard',
      active: false,
      vehicleIds: ['VH-1', 'VH-2'],
      shapeType: 'POLYGON',
      points: [
        { latitude: 51, longitude: 0 },
        { latitude: 51, longitude: 0.01 },
        { latitude: 51.01, longitude: 0.01 },
      ],
    })
  })

  it('sends explicit empty description and assignments with circle geometry', async () => {
    const { service, fetchImpl } = fixture()
    await service.update('9', {
      ...draft,
      description: '',
      vehicleIds: [],
      geometry: { type: 'circle', center: { lat: 51.5, lng: -0.1 }, radiusMeters: 250 },
    })
    const options = fetchImpl.mock.calls[0]![1] as RequestInit
    expect(JSON.parse(String(options.body))).toEqual({
      name: 'Depot',
      description: '',
      active: false,
      vehicleIds: [],
      shapeType: 'CIRCLE',
      centerLatitude: 51.5,
      centerLongitude: -0.1,
      radiusMeters: 250,
    })
  })

  it('rejects a malformed record path before making a request', async () => {
    const { service, fetchImpl } = fixture()
    await expect(service.update('9/other', draft)).rejects.toMatchObject({ kind: 'validation' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('does not convert a denied save into success or retry a mutation', async () => {
    const fetchImpl = vi.fn(async () => new Response('{}', { status: 403 }))
    const service = createLiveGeofenceService(createGatewayClient({ fetchImpl }))
    await expect(service.update('9', draft)).rejects.toMatchObject({ kind: 'forbidden' })
    expect(fetchImpl).toHaveBeenCalledTimes(1)
  })
})
