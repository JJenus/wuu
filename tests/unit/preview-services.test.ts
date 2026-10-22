import { describe, expect, it, vi } from 'vitest'
import { createPreviewService } from '../../app/features/workspace/api/preview-service'
import { createPreviewServices } from '../../app/features/core/services/preview-services'
import { createServices } from '../../app/features/core/services/create-services'
import type { GatewayClient } from '../../app/features/core/api/gateway-client'
import type { GeofenceDraft } from '../../app/features/geofences/domain/geofence'

describe('preview service state', () => {
  it('returns isolated snapshots so callers cannot mutate the session store', async () => {
    const service = createPreviewService()
    const first = await service.load()
    first.vehicles[0]!.name = 'Mutated outside the service'
    first.alerts.length = 0
    const second = await service.load()
    expect(second.vehicles[0]!.name).not.toBe('Mutated outside the service')
    expect(second.alerts.length).toBeGreaterThan(0)
  })

  it('creates a preview vehicle only after validation', async () => {
    const service = createPreviewService()
    const before = await service.load()
    await expect(
      service.addVehicle({ name: 'Depot van', plate: before.vehicles[0]!.plate }),
    ).rejects.toThrow(/already exists/)
    await service.addVehicle({ name: 'Depot van', plate: 'new-123' })
    const after = await service.load()
    expect(after.vehicles).toHaveLength(before.vehicles.length + 1)
    expect(after.vehicles.at(-1)).toMatchObject({
      id: `VH-${String(before.vehicles.length + 1).padStart(3, '0')}`,
      name: 'Depot van',
      plate: 'NEW-123',
      driver: 'Unassigned',
      status: 'offline',
      position: null,
      observedAt: null,
    })
  })

  it('enforces alert transitions without reopening resolved events', async () => {
    const service = createPreviewService()
    const data = await service.load()
    const active = data.alerts.find((alert) => alert.status === 'active')!
    await service.updateAlert(active.id, 'acknowledged')
    expect((await service.load()).alerts.find((alert) => alert.id === active.id)?.status).toBe(
      'acknowledged',
    )
    await service.updateAlert(active.id, 'resolved')
    await expect(service.updateAlert(active.id, 'acknowledged')).rejects.toThrow(
      /cannot be reopened/,
    )
    await expect(service.updateAlert('missing', 'resolved')).rejects.toThrow(/not found/)
  })

  it('creates and updates geofences through the same validation path', async () => {
    const service = createPreviewService()
    const before = await service.load()
    const geometry = {
      type: 'circle' as const,
      center: { lat: 37.77, lng: -122.42 },
      radiusMeters: 300,
    }
    const draft: GeofenceDraft = {
      name: 'New depot',
      description: 'A new boundary.',
      location: 'San Francisco',
      active: true,
      events: 'Entry and exit',
      vehicleIds: [before.vehicles[0]!.id],
      geometry,
    }
    const id = await service.createGeofence(draft)
    expect(id).toBe(`GF-${String(before.geofences.length + 1).padStart(3, '0')}`)
    const created = (await service.load()).geofences.find((zone) => zone.id === id)
    expect(created).toMatchObject({ name: 'New depot', radius: '300 m radius' })

    await service.updateGeofence(id, { ...draft, name: 'Renamed depot', active: false })
    const updated = (await service.load()).geofences.find((zone) => zone.id === id)
    expect(updated).toMatchObject({ name: 'Renamed depot', active: false })
    await expect(service.updateGeofence('missing', draft)).rejects.toThrow(/not found/)
  })

  it('persists preferences for the preview session', async () => {
    const service = createPreviewService()
    await service.savePreferences({ email: false, push: true, weekly: false })
    expect((await service.load()).preferences).toEqual({
      email: false,
      push: true,
      weekly: false,
    })
  })
})

describe('preview composition', () => {
  it('exposes the same domain contract as live services', async () => {
    const services = createPreviewServices()
    const fleet = await services.fleet.list()
    expect(fleet).toHaveLength(8)
    expect(await services.fleet.get(fleet[0]!.id)).toMatchObject({ id: fleet[0]!.id })
    expect(await services.fleet.get('missing')).toBeNull()

    const alerts = await services.alerts.list()
    expect(await services.alerts.get(alerts[0]!.id)).toMatchObject({ id: alerts[0]!.id })
    const trips = await services.trips.list()
    expect(await services.trips.get(trips[0]!.id)).toMatchObject({ id: trips[0]!.id })
    const geofences = await services.geofences.list()
    expect(await services.geofences.get(geofences[0]!.id)).toMatchObject({
      id: geofences[0]!.id,
    })
    expect((await services.preferences.get()).email).toBe(true)

    const created = await services.fleet.create({ name: 'Service van', plate: 'SVC-1' })
    expect(created.plate).toBe('SVC-1')
    expect(await services.fleet.list()).toHaveLength(fleet.length + 1)
  })

  it('preserves preview mode when composing services and never needs a network client', async () => {
    const request = vi.fn()
    const services = createServices('preview', { request } as unknown as GatewayClient)
    expect(services.mode).toBe('preview')
    expect((await services.fleet.list()).length).toBe(8)
    expect(request).not.toHaveBeenCalled()
  })
})
