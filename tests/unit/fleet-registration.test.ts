import { describe, expect, it, vi } from 'vitest'
import { fleetRegistrationAllowed, fleetRegistrationBody } from '../../server/utils/fleet-registration'
import { createLiveFleetService } from '../../app/features/fleet/api/live-fleet-service'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json' },
})

describe('fleet registration gateway boundary', () => {
  it('allows only exact collection POSTs', () => {
    expect(fleetRegistrationAllowed('POST', '/api/v1/vehicles')).toBe(true)
    expect(fleetRegistrationAllowed('POST', '/api/v1/trackers')).toBe(true)
    for (const path of ['/api/v1/vehicles/new', '/api/v1/trackers/new/assign', '/api/v1/trackers/', '/api/v1/commands'])
      expect(fleetRegistrationAllowed('POST', path)).toBe(false)
    for (const method of ['PUT', 'PATCH', 'DELETE', 'GET'])
      expect(fleetRegistrationAllowed(method, '/api/v1/trackers')).toBe(false)
  })
  it('normalizes only approved wire fields and requires explicit identities', () => {
    expect(fleetRegistrationBody('/api/v1/vehicles', { vehicle_id: ' van-1 ', model: ' Van ' }))
      .toEqual({ vehicle_id: 'van-1', model: 'Van' })
    expect(fleetRegistrationBody('/api/v1/trackers', {
      tracker_id: ' tracker-1 ', device_id: ' gps-1 ', vehicle_id: ' van-1 ', protocol: ' GT06 ',
    })).toMatchObject({ tracker_id: 'tracker-1', device_id: 'gps-1', vehicle_id: 'van-1', protocol: 'GT06' })
  })
  it.each([
    { vehicle_id: 'van', organization_id: 99 }, { vehicle_id: 'van', device_id: 'legacy' },
    { vehicleId: 'van' }, { vehicle_id: 'van', vin: 'x'.repeat(18) },
    { vehicle_id: 'van', model: 'x'.repeat(101) }, { vehicle_id: '../van' },
    { vehicle_id: 'a/b' }, { vehicle_id: '  ' },
  ])('rejects invalid or unsupported vehicle input %j', (body) => {
    expect(() => fleetRegistrationBody('/api/v1/vehicles', body)).toThrow()
  })
  it.each([
    { tracker_id: 't', vehicle_id: 'v' }, { tracker_id: 't', vehicle_id: 'v', device_id: ' ' },
    { tracker_id: 't', vehicle_id: 'v', device_id: 'd', organization_id: 99 },
    { tracker_id: 't', vehicle_id: 'v', device_id: 'd', protocol: 'x'.repeat(21) },
  ])('rejects invalid tracker input %j', (body) => {
    expect(() => fleetRegistrationBody('/api/v1/trackers', body)).toThrow()
  })
})

describe('live fleet setup adapter', () => {
  it('sends snake_case vehicle fields without inventing a device or tenant', async () => {
    const fetchImpl = vi.fn(async () => json({ vehicle_id: 'van-1', model: 'Van', license_plate: 'ABC' }, 201))
    const service = createLiveFleetService(createGatewayClient({ fetchImpl }))
    await expect(service.registerVehicle({ vehicleId: ' van-1 ', model: ' Van ', licensePlate: 'ABC' }))
      .resolves.toMatchObject({ id: 'van-1', type: 'Van', plate: 'ABC', status: 'offline' })
    expect(JSON.parse(fetchImpl.mock.calls[0]![1]!.body as string))
      .toEqual({ vehicle_id: 'van-1', model: 'Van', license_plate: 'ABC' })
  })
  it('registers explicitly assigned trackers and preserves their contract', async () => {
    const fetchImpl = vi.fn(async () => json({ tracker_id: 't', vehicle_id: 'v', device_id: 'd', protocol: 'GT06' }, 201))
    const service = createLiveFleetService(createGatewayClient({ fetchImpl }))
    await expect(service.registerTracker({ trackerId: 't', vehicleId: 'v', deviceId: 'd', protocol: 'GT06' }))
      .resolves.toMatchObject({ trackerId: 't', vehicleId: 'v', deviceId: 'd', protocol: 'GT06' })
    expect(JSON.parse(fetchImpl.mock.calls[0]![1]!.body as string))
      .toEqual({ tracker_id: 't', vehicle_id: 'v', device_id: 'd', protocol: 'GT06' })
  })
  it('returns only the requested vehicle trackers after checking complete pagination', async () => {
    const fetchImpl = vi.fn(async () => json({ content: [
      { tracker_id: 't', vehicle_id: 'v', device_id: 'd' },
      { tracker_id: 'other', vehicle_id: 'another', device_id: 'another' },
    ], last: true, empty: false }))
    await expect(createLiveFleetService(createGatewayClient({ fetchImpl })).listTrackers('v'))
      .resolves.toEqual([expect.objectContaining({ trackerId: 't' })])
  })
  it('does not replay conflicts or failed creates', async () => {
    const fetchImpl = vi.fn(async () => json({ message: 'Already registered' }, 409))
    await expect(createLiveFleetService(createGatewayClient({ fetchImpl })).registerVehicle({ vehicleId: 'v' }))
      .rejects.toMatchObject({ kind: 'conflict' })
    expect(fetchImpl).toHaveBeenCalledTimes(1)
  })
})
