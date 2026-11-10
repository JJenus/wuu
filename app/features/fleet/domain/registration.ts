export { vehicleRegistrationSchema, trackerRegistrationSchema } from '#shared/fleet-registration'
export type { VehicleRegistration, TrackerRegistration } from '#shared/fleet-registration'

export interface FleetTracker {
  trackerId: string
  deviceId: string | null
  vehicleId: string | null
  model?: string | null
  protocol?: string | null
  firmwareVersion?: string | null
  simNumber?: string | null
}
