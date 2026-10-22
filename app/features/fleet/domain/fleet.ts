export type VehicleStatus = 'moving' | 'idle' | 'parked' | 'offline'
import type { Coordinate } from '../../maps/domain/geographic'

export interface Vehicle {
  id: string
  name: string
  plate: string
  type: string
  driver: string | null
  initials: string | null
  status: VehicleStatus
  speed: number
  fuel: number | null
  location: string | null
  updated: string | null
  observedAt: string | null
  distance: number | null
  position: Coordinate | null
}

export interface NewVehicle {
  name: string
  plate: string
}

export function filterVehicles(vehicles: Vehicle[], query: string, status: string): Vehicle[] {
  const search = query.trim().toLowerCase()
  return vehicles.filter(
    (v) =>
      (status === 'all' || v.status === status) &&
      `${v.name} ${v.plate} ${v.driver ?? ''} ${v.location ?? ''}`.toLowerCase().includes(search),
  )
}

export function fleetSummary(vehicles: Vehicle[]) {
  return {
    total: vehicles.length,
    moving: vehicles.filter((v) => v.status === 'moving').length,
    idle: vehicles.filter((v) => v.status === 'idle').length,
    parked: vehicles.filter((v) => v.status === 'parked').length,
    offline: vehicles.filter((v) => v.status === 'offline').length,
    distance: Math.round(vehicles.reduce((sum, v) => sum + (v.distance ?? 0), 0)),
  }
}

export function validateVehicle(input: NewVehicle, vehicles: Vehicle[]): string | null {
  if (!input.name.trim() || !input.plate.trim()) return 'Enter a vehicle name and registration.'
  if (input.name.trim().length > 60 || !/^[a-zA-Z0-9 -]{2,20}$/.test(input.plate.trim()))
    return 'Use a name under 60 characters and a valid registration (2–20 letters, numbers, spaces or hyphens).'
  if (vehicles.some((v) => v.plate.toLowerCase() === input.plate.trim().toLowerCase()))
    return 'This registration already exists in your fleet.'
  return null
}

export function validateVehicleFields(
  input: NewVehicle,
  vehicles: Vehicle[],
): Partial<Record<keyof NewVehicle, string>> {
  const fields: Partial<Record<keyof NewVehicle, string>> = {}
  const name = input.name.trim()
  const plate = input.plate.trim()
  if (!name) fields.name = 'Enter a vehicle name.'
  else if (name.length > 60) fields.name = 'Keep the name to 60 characters or fewer.'
  if (!plate) fields.plate = 'Enter a registration.'
  else if (!/^[a-zA-Z0-9 -]{2,20}$/.test(plate))
    fields.plate = 'Use 2–20 letters, numbers, spaces, or hyphens.'
  else if (vehicles.some((v) => v.plate.toLowerCase() === plate.toLowerCase()))
    fields.plate = 'This registration already exists in your fleet.'
  return fields
}

export interface VehicleStatusInput {
  observedAt: string | null
  speedKmh: number | null
  accStatus: boolean | null
  now?: number
  staleAfterMs?: number
}

/**
 * Live status is derived only from observable telemetry. Missing or stale telemetry is
 * offline; it is never inferred from a driver assignment or a client role.
 */
export function vehicleStatusFromTelemetry(input: VehicleStatusInput): VehicleStatus {
  if (!input.observedAt) return 'offline'
  const observed = new Date(input.observedAt).getTime()
  if (Number.isNaN(observed)) return 'offline'
  const now = input.now ?? Date.now()
  if (now - observed > (input.staleAfterMs ?? 5 * 60 * 1000)) return 'offline'
  const speed = input.speedKmh ?? 0
  if (speed > 1) return 'moving'
  if (input.accStatus === false) return 'parked'
  return 'idle'
}
