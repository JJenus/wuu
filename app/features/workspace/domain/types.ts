import type { NewVehicle, Vehicle } from '../../fleet/domain/fleet'

export type AlertState = 'active' | 'acknowledged' | 'resolved'
export interface FleetAlert {
  id: string
  title: string
  description: string
  vehicleId: string
  vehicle: string
  severity: 'critical' | 'warning' | 'info'
  time: string
  icon: string
  status: AlertState
}
export interface Trip {
  id: string
  vehicleId: string
  vehicle: string
  driver: string
  from: string
  to: string
  started: string
  duration: string
  distance: number
  status: 'completed' | 'in progress'
}
export interface Preferences {
  email: boolean
  push: boolean
  weekly: boolean
}
export interface Geofence {
  id: string
  name: string
  description: string
  icon: string
  radius: string
  location: string
  events: string
  vehicleIds: string[]
}
export interface Workspace {
  vehicles: Vehicle[]
  alerts: FleetAlert[]
  trips: Trip[]
  preferences: Preferences
  geofences: Geofence[]
}
export interface WorkspaceService {
  load(): Promise<Workspace>
  addVehicle(input: NewVehicle): Promise<void>
  updateAlert(id: string, status: AlertState): Promise<void>
  savePreferences(input: Preferences): Promise<void>
}
