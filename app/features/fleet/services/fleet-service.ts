import type { NewVehicle, Vehicle } from '../domain/fleet'
import type { FleetTracker, VehicleRegistration, TrackerRegistration } from '../domain/registration'
import type { VehicleProfile, TrackerProfile, TrackerReassignment } from '#shared/fleet-management'

export interface ServiceRequestOptions {
  signal?: AbortSignal
}

export interface FleetService {
  updateVehicle(id: string, input: VehicleProfile): Promise<Vehicle>
  updateTracker(id: string, input: TrackerProfile): Promise<FleetTracker>
  reassignTracker(id: string, input: TrackerReassignment): Promise<FleetTracker>
  list(options?: ServiceRequestOptions): Promise<Vehicle[]>
  get(id: string, options?: ServiceRequestOptions): Promise<Vehicle | null>
  create(input: NewVehicle): Promise<Vehicle>
  registerVehicle(input: VehicleRegistration): Promise<Vehicle>
  registerTracker(input: TrackerRegistration): Promise<FleetTracker>
  listTrackers(vehicleId: string, options?: ServiceRequestOptions): Promise<FleetTracker[]>
}
