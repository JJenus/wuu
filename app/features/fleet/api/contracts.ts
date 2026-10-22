import { z } from 'zod'
import { nullableIsoInstantSchema, pagedResponseSchema } from '../../core/api/contracts'
import type { Vehicle } from '../domain/fleet'
import { vehicleStatusFromTelemetry } from '../domain/fleet'
import { normalizeLocation } from '../../maps/domain/geographic'
import { formatRelativeTime } from '../../core/domain/time'

export const locationDtoSchema = z.object({
  latitude: z.number().nullable().optional(),
  longitude: z.number().nullable().optional(),
  speedKmh: z.number().nullable().optional(),
  recordedAt: nullableIsoInstantSchema.optional(),
})

export const vehicleDtoSchema = z.object({
  vehicleId: z.string().min(1),
  deviceId: z.string().nullable().optional(),
  model: z.string().nullable().optional(),
  licensePlate: z.string().nullable().optional(),
  vin: z.string().nullable().optional(),
  engineState: z.string().nullable().optional(),
  accStatus: z.boolean().nullable().optional(),
  fuelLevel: z.number().nullable().optional(),
  odometerKm: z.number().nullable().optional(),
  fuelCutActive: z.boolean().nullable().optional(),
  organizationId: z.number().nullable().optional(),
  lastTelemetryTime: nullableIsoInstantSchema.optional(),
  currentLocation: locationDtoSchema.nullable().optional(),
})

export const vehiclePageSchema = pagedResponseSchema(vehicleDtoSchema)

export type VehicleDto = z.infer<typeof vehicleDtoSchema>

export function toVehicle(dto: VehicleDto, now: number = Date.now()): Vehicle {
  const location = dto.currentLocation ?? null
  const observedAt = dto.lastTelemetryTime ?? location?.recordedAt ?? null
  const speed = location?.speedKmh ?? 0
  const plate = dto.licensePlate?.trim() || 'Not provided'
  return {
    id: dto.vehicleId,
    name: plate === 'Not provided' ? dto.vehicleId : plate,
    plate,
    type: dto.model?.trim() || 'Vehicle type not provided',
    driver: null,
    initials: null,
    status: vehicleStatusFromTelemetry({
      observedAt,
      speedKmh: speed,
      accStatus: dto.accStatus ?? null,
      now,
    }),
    speed,
    fuel: dto.fuelLevel ?? null,
    location: null,
    updated: formatRelativeTime(observedAt, now),
    observedAt,
    distance: dto.odometerKm ?? null,
    position: normalizeLocation(
      location
        ? {
            latitude: location.latitude ?? null,
            longitude: location.longitude ?? null,
          }
        : null,
    ),
  }
}

export const vehicleDtoFixture = {
  vehicleId: 'VH-LIVE-1',
  licensePlate: 'CA 00001',
  model: 'Delivery van',
  accStatus: false,
  fuelLevel: 62,
  odometerKm: 1204.4,
  lastTelemetryTime: '2026-10-22T12:00:00.000Z',
  currentLocation: {
    latitude: 37.7749,
    longitude: -122.4194,
    speedKmh: 0,
    recordedAt: '2026-10-22T12:00:00.000Z',
  },
} satisfies z.infer<typeof vehicleDtoSchema>
