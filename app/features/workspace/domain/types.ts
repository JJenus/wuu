import type { NewVehicle, Vehicle } from '../../fleet/domain/fleet'
import type { GeofenceDraft, Geometry } from '../../geofences/domain/geofence'

export type AlertState = 'active' | 'acknowledged' | 'resolved'

export interface FleetAlert {
  acknowledgedBy?: string | null
  acknowledgedAt?: string | null
  resolvedBy?: string | null
  resolvedAt?: string | null
  resolutionNotes?: string | null
  id: string
  title: string
  description: string
  vehicleId: string
  vehicle: string | null
  severity: 'critical' | 'warning' | 'info'
  time: string
  triggeredAt: string | null
  icon: string
  status: AlertState
}

export interface Trip {
  id: string
  vehicleId: string
  vehicle: string | null
  driver: string | null
  from: string | null
  to: string | null
  started: string
  startedAt: string | null
  endedAt: string | null
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
  active: boolean
  vehicleIds: string[]
  geometry: Geometry | null
}

export interface Workspace {
  vehicles: Vehicle[]
  alerts: FleetAlert[]
  trips: Trip[]
  preferences: Preferences
  geofences: Geofence[]
}

/** Legacy aggregate used by the preview fixtures and their contract tests. */
export interface WorkspaceService {
  load(): Promise<Workspace>
  addVehicle(input: NewVehicle): Promise<void>
  updateAlert(id: string, status: AlertState): Promise<void>
  savePreferences(input: Preferences): Promise<void>
  createGeofence(input: GeofenceDraft): Promise<string>
  updateGeofence(id: string, input: GeofenceDraft): Promise<void>
}
