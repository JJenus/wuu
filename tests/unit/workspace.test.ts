import { describe, expect, it } from 'vitest'
import {
  filterVehicles,
  fleetSummary,
  validateVehicle,
  validateVehicleFields,
  vehicleStatusFromTelemetry,
} from '../../app/features/fleet/domain/fleet'
import { can, roleTemplates } from '../../app/policies/access'
import { createPreviewService } from '../../app/features/workspace/api/preview-service'

describe('fleet workspace', () => {
  it('filters registration, driver, and status without changing the source', async () => {
    const data = await createPreviewService().load()
    expect(filterVehicles(data.vehicles, 'Maya', 'all').length).toBe(1)
    expect(filterVehicles(data.vehicles, '', 'moving').every((v) => v.status === 'moving')).toBe(
      true,
    )
    expect(filterVehicles(data.vehicles, 'no match', 'all')).toEqual([])
    expect(data.vehicles.length).toBe(8)
  })
  it('derives counts from fleet data rather than decorative dashboard numbers', async () => {
    const { vehicles } = await createPreviewService().load()
    const summary = fleetSummary(vehicles)
    expect(summary.total).toBe(8)
    expect(summary.moving + summary.idle + summary.offline + summary.parked).toBe(8)
    expect(fleetSummary([]).total).toBe(0)
  })
  it('rejects missing and duplicate registrations', async () => {
    const { vehicles } = await createPreviewService().load()
    expect(validateVehicle({ name: '', plate: '' }, vehicles)).toBeTruthy()
    expect(validateVehicle({ name: 'Van', plate: '' }, vehicles)).toBeTruthy()
    expect(validateVehicle({ name: 'V'.repeat(61), plate: 'NEW-123' }, vehicles)).toMatch(/valid/)
    expect(validateVehicle({ name: 'Van', plate: '<invalid>' }, vehicles)).toMatch(/valid/)
    expect(
      validateVehicle({ name: 'Van', plate: vehicles[0]!.plate.toLowerCase() }, vehicles),
    ).toMatch(/already/)
    expect(validateVehicle({ name: 'Van', plate: 'NEW-123' }, vehicles)).toBeNull()
  })
})

describe('vehicle field validation and telemetry status', () => {
  it('returns field-specific messages without accepting duplicates', async () => {
    const { vehicles } = await createPreviewService().load()
    expect(validateVehicleFields({ name: '', plate: '' }, vehicles)).toEqual({
      name: 'Enter a vehicle name.',
      plate: 'Enter a registration.',
    })
    expect(validateVehicleFields({ name: 'V'.repeat(61), plate: 'BAD!' }, vehicles)).toEqual({
      name: 'Keep the name to 60 characters or fewer.',
      plate: 'Use 2–20 letters, numbers, spaces, or hyphens.',
    })
    expect(
      validateVehicleFields({ name: 'Van', plate: vehicles[0]!.plate.toLowerCase() }, vehicles),
    ).toEqual({ plate: 'This registration already exists in your fleet.' })
    expect(validateVehicleFields({ name: 'Van', plate: 'NEW-123' }, vehicles)).toEqual({})
  })

  it('derives status only from fresh observable telemetry', () => {
    const now = Date.parse('2026-10-22T12:00:00Z')
    expect(
      vehicleStatusFromTelemetry({ observedAt: null, speedKmh: 40, accStatus: true, now }),
    ).toBe('offline')
    expect(
      vehicleStatusFromTelemetry({ observedAt: 'not-a-date', speedKmh: 40, accStatus: true, now }),
    ).toBe('offline')
    expect(
      vehicleStatusFromTelemetry({
        observedAt: '2026-10-22T11:00:00Z',
        speedKmh: 40,
        accStatus: true,
        now,
      }),
    ).toBe('offline')
    expect(
      vehicleStatusFromTelemetry({
        observedAt: '2026-10-22T11:59:00Z',
        speedKmh: 2,
        accStatus: false,
        now,
      }),
    ).toBe('moving')
    expect(
      vehicleStatusFromTelemetry({
        observedAt: '2026-10-22T11:59:00Z',
        speedKmh: 0,
        accStatus: false,
        now,
      }),
    ).toBe('parked')
    expect(
      vehicleStatusFromTelemetry({
        observedAt: '2026-10-22T11:59:00Z',
        speedKmh: null,
        accStatus: null,
        now,
      }),
    ).toBe('idle')
  })
})

describe('permission templates', () => {
  it('includes every organization-template permission in the platform template', () => {
    for (const template of roleTemplates) {
      for (const permission of template.permissions) {
        expect(can('SUPER_ADMIN', permission), permission).toBe(true)
      }
    }
  })
  it('denies unknown roles and separates platform from organization access', () => {
    expect(can('UNKNOWN', 'vehicles.read')).toBe(false)
    expect(can('VIEWER', 'vehicles.write')).toBe(false)
    expect(can('OPERATOR', 'alerts.write')).toBe(true)
    expect(can('TENANT_ADMIN', 'platform.access')).toBe(false)
    expect(can('SUPER_ADMIN', 'platform.access')).toBe(true)
    expect(roleTemplates.length).toBe(4)
  })
})

describe('isolated preview adapter', () => {
  it('adds a vehicle, rejects duplicates, and never mutates another session', async () => {
    const first = createPreviewService()
    const second = createPreviewService()
    await first.addVehicle({ name: 'Transit 09', plate: 'NEW-123' })
    expect((await first.load()).vehicles.length).toBe(9)
    expect((await second.load()).vehicles.length).toBe(8)
    await expect(first.addVehicle({ name: 'Duplicate', plate: 'NEW-123' })).rejects.toThrow(
      /already/,
    )
    await expect(first.addVehicle({ name: '', plate: '' })).rejects.toThrow()
  })
  it('acknowledges and resolves alerts with missing-ID handling', async () => {
    const service = createPreviewService()
    const id = (await service.load()).alerts[0]!.id
    await service.updateAlert(id, 'acknowledged')
    expect((await service.load()).alerts[0]!.status).toBe('acknowledged')
    await service.updateAlert(id, 'resolved')
    expect((await service.load()).alerts[0]!.status).toBe('resolved')
    await expect(service.updateAlert('missing', 'resolved')).rejects.toThrow(/not found/)
  })
  it('returns defensive copies and updates preview preferences', async () => {
    const service = createPreviewService()
    const data = await service.load()
    data.vehicles.splice(0)
    expect((await service.load()).vehicles.length).toBe(8)
    await service.savePreferences({ email: false, push: true, weekly: false })
    expect((await service.load()).preferences.email).toBe(false)
  })
})
