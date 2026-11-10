import type { NewVehicle, Vehicle } from '../domain/fleet'
import type { FleetTracker, VehicleRegistration, TrackerRegistration } from '../domain/registration'

export interface ServiceRequestOptions {
  signal?: AbortSignal
}

export interface FleetService {
  list(options?: ServiceRequestOptions): Promise<Vehicle[]>
  get(id: string, options?: ServiceRequestOptions): Promise<Vehicle | null>
  create(input: NewVehicle): Promise<Vehicle>
  registerVehicle(input: VehicleRegistration): Promise<Vehicle>
  registerTracker(input: TrackerRegistration): Promise<FleetTracker>
  listTrackers(vehicleId: string, options?: ServiceRequestOptions): Promise<FleetTracker[]>
}
