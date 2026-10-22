import { describe, expect, it, vi } from 'vitest'
import { createGatewayClient, type GatewayClient } from '../../app/features/core/api/gateway-client'
import { responseApiError } from '../../app/features/core/domain/errors'
import { workspaceContext } from '../../app/features/core/domain/data-mode'
import { toVehicle, vehicleDtoFixture } from '../../app/features/fleet/api/contracts'
import { createLiveFleetService } from '../../app/features/fleet/api/live-fleet-service'
import {
  createLiveAlertService,
  toFleetAlert,
} from '../../app/features/alerts/api/live-alert-service'
import { toTrip } from '../../app/features/trips/api/live-trip-service'
import {
  toGeofence,
  createLiveGeofenceService,
} from '../../app/features/geofences/api/live-geofence-service'
import { createLivePreferencesService } from '../../app/features/settings/api/live-preferences-service'
import { createLiveTripService } from '../../app/features/trips/api/live-trip-service'
import { createServices } from '../../app/features/core/services/create-services'
import type { Geometry, GeofenceDraft } from '../../app/features/geofences/domain/geofence'

function page(content: unknown[], overrides: Record<string, unknown> = {}) {
  return {
    content,
    pageNumber: 0,
    pageSize: 200,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

describe('live vehicle mapping and paging', () => {
  it('maps only observable telemetry and never invents a driver', () => {
    const vehicle = toVehicle(vehicleDtoFixture, Date.parse('2026-10-22T12:01:00.000Z'))
    expect(vehicle).toMatchObject({
      id: 'VH-LIVE-1',
      name: 'CA 00001',
      plate: 'CA 00001',
      type: 'Delivery van',
      driver: null,
      initials: null,
      status: 'parked',
      speed: 0,
      fuel: 62,
      distance: 1204.4,
      updated: '1 min ago',
    })
    expect(vehicle.position).toEqual({ lat: 37.7749, lng: -122.4194 })
  })

  it('marks missing/stale telemetry offline and missing fields explicit', () => {
    const missing = toVehicle({ vehicleId: 'VH-X' }, Date.parse('2026-10-22T12:00:00.000Z'))
    expect(missing).toMatchObject({
      name: 'VH-X',
      plate: 'Not provided',
      type: 'Vehicle type not provided',
      status: 'offline',
      driver: null,
      position: null,
      updated: 'No update recorded',
    })
    const stale = toVehicle(
      { ...vehicleDtoFixture, lastTelemetryTime: '2026-10-22T11:00:00.000Z' },
      Date.parse('2026-10-22T12:00:00.000Z'),
    )
    expect(stale.status).toBe('offline')
  })

  it('follows every page but stops on the last page', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('page=0')) {
        return jsonResponse(
          page([vehicleDtoFixture], {
            totalPages: 2,
            last: false,
            empty: false,
          }),
        )
      }
      return jsonResponse(
        page([{ ...vehicleDtoFixture, vehicleId: 'VH-LIVE-2' }], {
          pageNumber: 1,
          totalPages: 2,
          first: false,
          empty: false,
        }),
      )
    }) as unknown as typeof fetch
    const client = createGatewayClient({ baseUrl: '/api/_wuu', fetchImpl, timeoutMs: 1_000 })
    const vehicles = await createLiveFleetService(client).list()
    expect(vehicles.map((vehicle) => vehicle.id)).toEqual(['VH-LIVE-1', 'VH-LIVE-2'])
    expect(fetchImpl).toHaveBeenCalledTimes(2)
    const firstUrl = String((fetchImpl as unknown as ReturnType<typeof vi.fn>).mock.calls[0]![0])
    expect(firstUrl).toContain('/api/_wuu/vehicles?page=0&size=200')
  })
})

describe('live alert mapping and guarded transitions', () => {
  it('derives status, severity, and display text only from the response', () => {
    const alert = toFleetAlert(
      {
        alertId: 12,
        vehicleId: 'VH-LIVE-1',
        alertType: 'GEOFENCE_EXIT',
        severity: 'CRITICAL',
        message: 'Vehicle left the depot zone.',
        triggeredAt: '2026-10-22T12:00:00.000Z',
        resolved: false,
        acknowledged: true,
      },
      Date.parse('2026-10-22T12:01:00.000Z'),
    )
    expect(alert).toMatchObject({
      id: '12',
      title: 'Geofence exit',
      vehicleId: 'VH-LIVE-1',
      vehicle: 'VH-LIVE-1',
      severity: 'critical',
      status: 'acknowledged',
      time: '1 min ago',
      icon: 'i-lucide-map-pin',
    })
  })

  it('fails closed without an actor or review notes', async () => {
    const request = vi.fn()
    const service = createLiveAlertService({ request } as unknown as GatewayClient)
    await expect(service.transition('12', 'active')).rejects.toMatchObject({ kind: 'unavailable' })
    await expect(service.transition('12', 'acknowledged')).rejects.toMatchObject({
      kind: 'unavailable',
    })
    await expect(service.transition('12', 'resolved', { actor: 'operator' })).rejects.toMatchObject(
      {
        kind: 'unavailable',
      },
    )
    await expect(
      service.transition('12', 'resolved', { actor: 'operator', resolutionNotes: 'short' }),
    ).rejects.toMatchObject({ kind: 'unavailable' })
    await service.transition('12', 'acknowledged', { actor: 'operator' })
    expect(request).toHaveBeenCalledWith(
      'POST',
      '/alerts/12/acknowledge',
      expect.anything(),
      expect.objectContaining({ body: { acknowledgedBy: 'operator' } }),
    )
    await service.transition('12', 'resolved', {
      actor: 'operator',
      resolutionNotes: '  Reviewed the depot exit.  ',
    })
    expect(request).toHaveBeenLastCalledWith(
      'POST',
      '/alerts/12/resolve',
      expect.anything(),
      expect.objectContaining({
        body: { resolvedBy: 'operator', resolutionNotes: 'Reviewed the depot exit.' },
      }),
    )
  })
})

describe('live trip and geofence mapping', () => {
  it('does not invent endpoints or drivers for a trip', () => {
    const trip = toTrip(
      {
        tripId: 'TR-1',
        vehicleId: 'VH-LIVE-1',
        startTime: '2026-10-22T10:00:00.000Z',
        endTime: '2026-10-22T11:00:00.000Z',
        isActive: false,
        totalDistanceKm: 43.2,
      },
      Date.parse('2026-10-22T12:00:00.000Z'),
    )
    expect(trip).toMatchObject({
      id: 'TR-1',
      vehicleId: 'VH-LIVE-1',
      vehicle: 'VH-LIVE-1',
      driver: null,
      from: null,
      to: null,
      distance: 43.2,
      status: 'completed',
      duration: '1 hr 0 min',
    })
    expect(trip.startedAt).toBe('2026-10-22T10:00:00.000Z')
  })

  it('maps circle and polygon geofences without synthesizing missing geometry', () => {
    const circle = toGeofence({
      geofenceId: 1,
      vehicleId: 'VH-LIVE-1',
      name: 'Depot',
      shapeType: 'CIRCLE',
      centerLatitude: 37.77,
      centerLongitude: -122.42,
      radiusMeters: 250,
      active: true,
    })
    expect(circle).toMatchObject({
      id: '1',
      radius: '250 m radius',
      vehicleIds: ['VH-LIVE-1'],
      location: 'Location label not provided',
      events: 'Monitoring configuration not provided',
    })
    expect(circle.geometry).toEqual({
      type: 'circle',
      center: { lat: 37.77, lng: -122.42 },
      radiusMeters: 250,
    })

    const polygon = toGeofence({
      geofenceId: 2,
      name: 'Yard',
      shapeType: 'POLYGON',
      active: false,
      points: [
        { latitude: 37.7, longitude: -122.4 },
        { latitude: 37.71, longitude: -122.4 },
        { latitude: 37.71, longitude: -122.39 },
      ],
    })
    expect(polygon.geometry?.type).toBe('polygon')
    expect(polygon.vehicleIds).toEqual([])

    const incomplete = toGeofence({
      geofenceId: 3,
      name: 'Incomplete',
      shapeType: 'CIRCLE',
      active: true,
    })
    expect(incomplete.geometry).toBeNull()
  })

  it('allows only the verified live creation subset and fails closed on update', async () => {
    const request = vi.fn(async () => ({ geofenceId: 9 }))
    const service = createLiveGeofenceService({ request } as unknown as GatewayClient)
    const geometry: Geometry = {
      type: 'circle',
      center: { lat: 37.77, lng: -122.42 },
      radiusMeters: 100,
    }
    const draft: GeofenceDraft = {
      name: 'Depot',
      description: 'Gate',
      location: '',
      active: true,
      events: 'Entry and exit',
      vehicleIds: ['VH-LIVE-1'],
      geometry,
    }
    await expect(service.create(draft)).resolves.toBe('9')
    expect(request).toHaveBeenCalledWith(
      'POST',
      '/geofences',
      expect.anything(),
      expect.objectContaining({
        body: expect.objectContaining({
          vehicleId: 'VH-LIVE-1',
          shapeType: 'CIRCLE',
          radiusMeters: 100,
        }),
      }),
    )
    await expect(service.create({ ...draft, vehicleIds: [] })).rejects.toMatchObject({
      kind: 'unavailable',
    })
    await expect(
      service.create({
        ...draft,
        geometry: {
          type: 'polygon',
          vertices: [
            { lat: 1, lng: 1 },
            { lat: 1, lng: 2 },
            { lat: 2, lng: 2 },
          ],
        },
      }),
    ).rejects.toMatchObject({ kind: 'unavailable' })
    await expect(service.update('9', draft)).rejects.toMatchObject({ kind: 'unavailable' })
  })
})

describe('live detail reads and guarded writes', () => {
  it('returns null for missing numeric records and fails closed for unsupported writes', async () => {
    const notFound = vi.fn(async () => {
      throw responseApiError(404, {})
    })
    const fleet = createLiveFleetService({ request: notFound } as unknown as GatewayClient)
    await expect(fleet.get('VH-LIVE-1')).resolves.toBeNull()
    expect(notFound).toHaveBeenCalledWith(
      'GET',
      '/vehicles/VH-LIVE-1',
      expect.anything(),
      expect.anything(),
    )
    await expect(fleet.create({ name: 'New', plate: 'NEW-1' })).rejects.toMatchObject({
      kind: 'unavailable',
    })

    const request = vi.fn()
    const alerts = createLiveAlertService({ request } as unknown as GatewayClient)
    await expect(alerts.get('not-a-number')).resolves.toBeNull()
    expect(request).not.toHaveBeenCalled()

    const geofences = createLiveGeofenceService({ request } as unknown as GatewayClient)
    await expect(geofences.get('GF-001')).resolves.toBeNull()
    expect(request).not.toHaveBeenCalled()
  })

  it('maps live lists and detail reads through their DTO contracts', async () => {
    const fixtureTrip = {
      tripId: 'TR-2',
      vehicleId: 'VH-LIVE-1',
      startTime: '2026-10-22T10:00:00.000Z',
      endTime: '2026-10-22T11:00:00.000Z',
      isActive: false,
      totalDistanceKm: 20,
    }
    const tripRequest = vi.fn(async (method: string, path: string) =>
      path === '/trips' ? page([fixtureTrip]) : fixtureTrip,
    )
    const trips = createLiveTripService({ request: tripRequest } as unknown as GatewayClient)
    await expect(trips.list()).resolves.toHaveLength(1)
    await expect(trips.get('TR-2')).resolves.toMatchObject({ id: 'TR-2', distance: 20 })

    const fixtureGeofence = {
      geofenceId: 8,
      vehicleId: 'VH-LIVE-1',
      name: 'Depot',
      shapeType: 'CIRCLE',
      centerLatitude: 37.77,
      centerLongitude: -122.42,
      radiusMeters: 50,
      active: true,
    }
    const geofenceRequest = vi.fn(async (method: string, path: string) =>
      path === '/geofences' ? page([fixtureGeofence]) : fixtureGeofence,
    )
    const geofences = createLiveGeofenceService({
      request: geofenceRequest,
    } as unknown as GatewayClient)
    await expect(geofences.list()).resolves.toHaveLength(1)
    await expect(geofences.get('8')).resolves.toMatchObject({ id: '8', name: 'Depot' })
  })
})

describe('workspace context', () => {
  it('accepts only known modes and supplies explicit isolation defaults', () => {
    expect(workspaceContext({ mode: 'live' })).toEqual({
      mode: 'live',
      organizationId: 'northstar',
      userId: 'preview-user',
    })
    expect(workspaceContext({ mode: 'production' })).toMatchObject({ mode: 'preview' })
    expect(workspaceContext({ mode: 'live', organizationId: 'org-2', userId: 'user-2' })).toEqual({
      mode: 'live',
      organizationId: 'org-2',
      userId: 'user-2',
    })
  })
})

describe('live composition', () => {
  it('keeps unverified preferences unavailable instead of returning fixtures', async () => {
    const service = createLivePreferencesService()
    await expect(service.get()).rejects.toMatchObject({ kind: 'unavailable' })
    await expect(service.save({ email: true, push: false, weekly: true })).rejects.toMatchObject({
      kind: 'unavailable',
    })
    const services = createServices('live', { request: vi.fn() } as unknown as GatewayClient)
    expect(services.mode).toBe('live')
    expect(services.preferences).toBeInstanceOf(Object)
  })
})
