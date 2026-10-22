import { describe, expect, it } from 'vitest'
import {
  validateGeofence,
  geometryArea,
  geometrySummary,
  type GeofenceDraft,
} from '../../app/features/geofences/domain/geofence'
import { createPreviewService } from '../../app/features/workspace/api/preview-service'

const draft = (): GeofenceDraft => ({
  name: 'New depot',
  description: 'Sample zone',
  location: 'San Francisco',
  active: true,
  events: 'Entry and exit',
  vehicleIds: ['VH-001'],
  geometry: { type: 'circle', center: { lat: 37.76, lng: -122.43 }, radiusMeters: 500 },
})
const point = (lat: number, lng: number) => ({ lat, lng })
describe('geofence geometry and validation', () => {
  it('describes empty, circle and polygon boundaries', () => {
    expect(geometrySummary(null)).toBe('No boundary drawn')
    expect(geometrySummary(draft().geometry)).toBe('500 m radius')
    expect(geometrySummary({ type: 'polygon', vertices: [] })).toBe('0 vertices')
    expect(geometryArea(null)).toBe(0)
    expect(validateGeofence({ ...draft(), geometry: null }, [], ['VH-001']).geometry).toBeTruthy()
    expect(
      validateGeofence({ ...draft(), location: 'x'.repeat(101) }, [], ['VH-001']).location,
    ).toBeTruthy()
  })
  it('measures a circle in square meters', () => {
    expect(geometryArea(draft().geometry)).toBeCloseTo(Math.PI * 500 ** 2)
  })
  it('requires valid names, assignments, settings, coordinates and radius', () => {
    expect(validateGeofence(draft(), [], ['VH-001'])).toEqual({})
    for (const name of ['', 'x'.repeat(101)])
      expect(validateGeofence({ ...draft(), name }, [], ['VH-001']).name).toBeTruthy()
    expect(
      validateGeofence({ ...draft(), description: 'x'.repeat(501) }, [], ['VH-001']).description,
    ).toBeTruthy()
    expect(
      validateGeofence({ ...draft(), vehicleIds: ['unknown'] }, [], ['VH-001']).vehicleIds,
    ).toBeTruthy()
    expect(validateGeofence({ ...draft(), vehicleIds: [] }, [], ['VH-001']).vehicleIds).toBeTruthy()
    expect(validateGeofence({ ...draft(), events: 'bogus' }, [], ['VH-001']).events).toBeTruthy()
    for (const radiusMeters of [0, 49, 5001, NaN, 100.5])
      expect(
        validateGeofence(
          { ...draft(), geometry: { type: 'circle', center: point(37.76, -122.43), radiusMeters } },
          [],
          ['VH-001'],
        ).geometry,
      ).toBeTruthy()
    for (const center of [point(NaN, -122.4), point(90, 0), point(37.7, Infinity)])
      expect(
        validateGeofence(
          { ...draft(), geometry: { type: 'circle', center, radiusMeters: 500 } },
          [],
          ['VH-001'],
        ).geometry,
      ).toBeTruthy()
    expect(
      validateGeofence(draft(), [{ id: 'GF-1', name: ' new DEPOT ' }], ['VH-001']).name,
    ).toBeTruthy()
    expect(
      validateGeofence(draft(), [{ id: 'GF-1', name: 'New depot' }], ['VH-001'], 'GF-1'),
    ).toEqual({})
  })
  it('rejects incomplete, degenerate, intersecting and duplicate polygon vertices', () => {
    const a = point(37.75, -122.45),
      b = point(37.77, -122.45),
      c = point(37.77, -122.42),
      d = point(37.75, -122.42)
    for (const vertices of [
      [],
      [a, b],
      [a, b, a],
      [a, b, d, c],
      [a, point(37.76, -122.45), b],
      Array(51).fill(a),
      [a, b, point(NaN, 0)],
    ]) {
      expect(
        validateGeofence({ ...draft(), geometry: { type: 'polygon', vertices } }, [], ['VH-001'])
          .geometry,
      ).toBeTruthy()
    }
    const geometry = { type: 'polygon' as const, vertices: [a, b, c, d] }
    expect(validateGeofence({ ...draft(), geometry }, [], ['VH-001'])).toEqual({})
    expect(geometryArea(geometry)).toBeGreaterThan(1_000_000)
  })
})
describe('session-only geofence adapter', () => {
  it('creates and edits defensive copies without leaking across sessions', async () => {
    const first = createPreviewService(),
      second = createPreviewService()
    const input = draft()
    const id = await first.createGeofence(input)
    input.vehicleIds.length = 0
    expect((await first.load()).geofences.find((z) => z.id === id)?.vehicleIds).toEqual(['VH-001'])
    expect((await second.load()).geofences.find((z) => z.id === id)).toBeUndefined()
    await first.updateGeofence(id, { ...draft(), name: 'Edited depot', active: false })
    expect((await first.load()).geofences.find((z) => z.id === id)).toMatchObject({
      name: 'Edited depot',
      active: false,
    })
    const loaded = await first.load()
    loaded.geofences[0]!.vehicleIds.length = 0
    expect((await first.load()).geofences[0]!.vehicleIds.length).toBeGreaterThan(0)
    await expect(first.createGeofence({ ...draft(), name: 'Edited depot' })).rejects.toThrow()
    await expect(first.updateGeofence('missing', draft())).rejects.toThrow(/not found/i)
    await expect(
      first.updateGeofence(id, { ...draft(), vehicleIds: ['unknown'] }),
    ).rejects.toThrow()
  })
})
