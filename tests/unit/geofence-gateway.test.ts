import { describe, expect, it } from 'vitest'
import { geofenceWriteAllowed, geofenceWriteBody } from '../../server/utils/geofence-write'

const base = '/api/v1/alerts/geofences'

describe('strict geofence write gateway', () => {
  it('admits only collection POST with complete geometry and explicit associations', () => {
    expect(geofenceWriteAllowed('POST', base)).toBe(true)
    expect(geofenceWriteAllowed('PUT', base)).toBe(false)
    const body = {
      name: 'Depot',
      description: 'Complete creation',
      active: true,
      vehicleIds: [],
      shapeType: 'CIRCLE',
      centerLatitude: 51,
      centerLongitude: 0,
      radiusMeters: 100,
    }
    expect(geofenceWriteBody(base, body)).toEqual({
      name: 'Depot',
      description: 'Complete creation',
      active: true,
      vehicle_ids: [],
      shape_type: 'CIRCLE',
      center_latitude: 51,
      center_longitude: 0,
      radius_meters: 100,
    })
    for (const missing of [
      'name',
      'active',
      'vehicleIds',
      'shapeType',
      'centerLatitude',
      'radiusMeters',
    ]) {
      const incomplete = { ...body } as Record<string, unknown>
      delete incomplete[missing]
      expect(() => geofenceWriteBody(base, incomplete)).toThrow()
    }
    expect(() => geofenceWriteBody(base, { ...body, shapeType: 'POLYGON', points: [] })).toThrow()
  })
  it('only permits individual positive Java-long PUT targets', () => {
    expect(geofenceWriteAllowed('PUT', `${base}/9`)).toBe(true)
    expect(geofenceWriteAllowed('PUT', `${base}/9223372036854775807`)).toBe(true)
    for (const target of [
      base,
      `${base}/0`,
      `${base}/-1`,
      `${base}/01`,
      `${base}/9223372036854775808`,
      `${base}/9/vehicles`,
      `${base}/9/delete`,
      '/api/v1/alerts/9',
    ])
      expect(geofenceWriteAllowed('PUT', target)).toBe(false)
    for (const method of ['POST', 'DELETE', 'PATCH', 'GET'])
      expect(geofenceWriteAllowed(method, `${base}/9`)).toBe(false)
  })

  it('maps browser fields to Jackson snake case without erasing omitted fields', () => {
    expect(geofenceWriteBody(`${base}/9`, { name: 'Depot' })).toEqual({ name: 'Depot' })
    expect(
      geofenceWriteBody(`${base}/9`, { description: '', active: false, vehicleIds: [] }),
    ).toEqual({ description: '', active: false, vehicle_ids: [] })
    expect(
      geofenceWriteBody(`${base}/9`, {
        shapeType: 'POLYGON',
        points: [
          { latitude: 1, longitude: 1 },
          { latitude: 1, longitude: 2 },
          { latitude: 2, longitude: 2 },
        ],
        vehicleIds: ['VH-1', 'VH-2'],
      }),
    ).toEqual({
      shape_type: 'POLYGON',
      points: [
        { latitude: 1, longitude: 1 },
        { latitude: 1, longitude: 2 },
        { latitude: 2, longitude: 2 },
      ],
      vehicle_ids: ['VH-1', 'VH-2'],
    })
    expect(
      geofenceWriteBody(`${base}/9`, {
        shapeType: 'CIRCLE',
        centerLatitude: 51,
        centerLongitude: 0,
        radiusMeters: 100,
      }),
    ).toEqual({
      shape_type: 'CIRCLE',
      center_latitude: 51,
      center_longitude: 0,
      radius_meters: 100,
    })
  })

  it.each([
    {},
    { name: ' ' },
    { description: 'x'.repeat(501) },
    { createdBy: 'forged' },
    { organizationId: 4 },
    { vehicle_ids: [] },
    { vehicleIds: [null] },
    { active: 'false' },
    { points: [null] },
    { centerLatitude: 91 },
    { centerLongitude: -181 },
    { radiusMeters: 0 },
    { radiusMeters: 1.5 },
    { shapeType: 'BOX' },
    { points: Array.from({ length: 51 }, () => ({ latitude: 1, longitude: 1 })) },
  ])('rejects unsupported or malformed payload %j', (body) => {
    expect(() => geofenceWriteBody(`${base}/9`, body)).toThrow()
  })

  it('rejects non-record targets even when the body is valid', () => {
    expect(() => geofenceWriteBody(`${base}/9/vehicles`, { name: 'Depot' })).toThrow()
  })
})
