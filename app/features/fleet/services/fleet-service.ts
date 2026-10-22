import type { NewVehicle, Vehicle } from '../domain/fleet'

export interface ServiceRequestOptions {
  signal?: AbortSignal
}

export interface FleetService {
  list(options?: ServiceRequestOptions): Promise<Vehicle[]>
  get(id: string, options?: ServiceRequestOptions): Promise<Vehicle | null>
  create(input: NewVehicle): Promise<Vehicle>
}
