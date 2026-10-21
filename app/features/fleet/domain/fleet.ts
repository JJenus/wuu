export type VehicleStatus = 'moving' | 'idle' | 'parked' | 'offline'
export interface Vehicle {
  id: string
  name: string
  plate: string
  type: string
  driver: string
  initials: string
  status: VehicleStatus
  speed: number
  fuel: number
  location: string
  updated: string
  distance: number
  x: number
  y: number
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
      `${v.name} ${v.plate} ${v.driver} ${v.location}`.toLowerCase().includes(search),
  )
}

export function fleetSummary(vehicles: Vehicle[]) {
  return {
    total: vehicles.length,
    moving: vehicles.filter((v) => v.status === 'moving').length,
    idle: vehicles.filter((v) => v.status === 'idle').length,
    parked: vehicles.filter((v) => v.status === 'parked').length,
    offline: vehicles.filter((v) => v.status === 'offline').length,
    distance: Math.round(vehicles.reduce((sum, v) => sum + v.distance, 0)),
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
