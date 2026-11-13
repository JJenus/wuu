import { describe, expect, it, vi } from 'vitest'
import { fleetManagementAllowed, fleetManagementBody } from '../../server/utils/fleet-management'
import { createLiveFleetService } from '../../app/features/fleet/api/live-fleet-service'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json' },
})

describe('fleet management boundary', () => {
  it('admits only exact vehicle PUT, tracker profile PATCH and reassignment POST', () => {
    expect(fleetManagementAllowed('PUT', '/api/v1/vehicles/van')).toBe(true)
    expect(fleetManagementAllowed('PATCH', '/api/v1/trackers/gps/profile')).toBe(true)
    expect(fleetManagementAllowed('POST', '/api/v1/trackers/gps/reassign')).toBe(true)
    for (const target of ['/api/v1/vehicles', '/api/v1/vehicles/a/b', '/api/v1/trackers/gps', '/api/v1/trackers/gps/profile/extra', '/api/v1/trackers/../reassign', '/api/v1/trackers/a%2Fb/reassign'])
      for (const method of ['PUT', 'POST', 'PATCH', 'DELETE'])
        expect(fleetManagementAllowed(method, target)).toBe(false)
  })
  it('requires complete profile fields and rejects measured state, IDs and tenant overrides', () => {
    const profile = { model: ' GPS ', protocol: null, firmware_version: '2', sim_number: null }
    expect(fleetManagementBody('/api/v1/trackers/gps/profile', profile)).toEqual({ ...profile, model: 'GPS' })
    for (const extra of [{ device_id: 'new' }, { vehicle_id: 'new' }, { battery_level: 0 }, { organization_id: 2 }, { status: 'INACTIVE' }])
      expect(() => fleetManagementBody('/api/v1/trackers/gps/profile', { ...profile, ...extra })).toThrow()
    expect(() => fleetManagementBody('/api/v1/trackers/gps/profile', {})).toThrow()
    expect(() => fleetManagementBody('/api/v1/vehicles/van', { model: 'x'.repeat(101), license_plate: null, vin: null })).toThrow()
    expect(() => fleetManagementBody('/api/v1/vehicles/van', { model: null, license_plate: null, vin: null, device_id: 'new' })).toThrow()
  })
  it('requires expected source and target but no actor or tenant', () => {
    expect(fleetManagementBody('/api/v1/trackers/gps/reassign', { source_vehicle_id: 'old', target_vehicle_id: 'new' })).toEqual({ source_vehicle_id: 'old', target_vehicle_id: 'new' })
    for (const input of [{ target_vehicle_id: 'new' }, { source_vehicle_id: 'old', target_vehicle_id: '' }, { source_vehicle_id: 'old', target_vehicle_id: 'new', actor_id: 1 }])
      expect(() => fleetManagementBody('/api/v1/trackers/gps/reassign', input)).toThrow()
  })
})

describe('fleet management adapter', () => {
  it('updates raw vehicle fields without saving display placeholders', async () => {
    const fetchImpl = vi.fn(async () => json({ vehicle_id: 'van', model: null, license_plate: null, vin: null }))
    const service = createLiveFleetService(createGatewayClient({ fetchImpl }))
    const vehicle = await service.updateVehicle('van', { model: null, licensePlate: null, vin: null })
    expect(vehicle.profile).toEqual({ model: null, licensePlate: null, vin: null })
    expect(fetchImpl.mock.calls[0]![0]).toBe('/api/_wuu/vehicles/van')
    expect(fetchImpl.mock.calls[0]![1]?.method).toBe('PUT')
    expect(JSON.parse(fetchImpl.mock.calls[0]![1]!.body as string)).toEqual({ model: null, license_plate: null, vin: null })
  })
  it('updates only tracker metadata and verifies the returned identity', async () => {
    const fetchImpl = vi.fn(async () => json({ tracker_id: 'gps', device_id: 'd', vehicle_id: 'van', model: 'New' }))
    await createLiveFleetService(createGatewayClient({ fetchImpl })).updateTracker('gps', { model: 'New', protocol: null, firmwareVersion: null, simNumber: null })
    expect(fetchImpl.mock.calls[0]![1]?.method).toBe('PATCH')
    expect(JSON.parse(fetchImpl.mock.calls[0]![1]!.body as string)).toEqual({ model: 'New', protocol: null, firmware_version: null, sim_number: null })
  })
  it('checks reassignment response and does not replay a conflict', async () => {
    const input = { sourceVehicleId: 'old', targetVehicleId: 'new' }
    const fetchImpl = vi.fn(async () => json({ tracker_id: 'gps', device_id: 'd', vehicle_id: 'wrong' }))
    await expect(createLiveFleetService(createGatewayClient({ fetchImpl })).reassignTracker('gps', input)).rejects.toMatchObject({ kind: 'contract' })
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    const conflict = vi.fn(async () => json({ message: 'Tracker has history' }, 409))
    await expect(createLiveFleetService(createGatewayClient({ fetchImpl: conflict })).reassignTracker('gps', input)).rejects.toMatchObject({ kind: 'conflict' })
    expect(conflict).toHaveBeenCalledTimes(1)
  })
})
