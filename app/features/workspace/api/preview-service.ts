import { validateVehicle, type NewVehicle } from '../../fleet/domain/fleet'
import type { AlertState, Preferences, WorkspaceService } from '../domain/types'
import { previewWorkspace } from './fixtures'
import {
  geometrySummary,
  validateGeofence,
  type GeofenceDraft,
} from '../../geofences/domain/geofence'

export function createPreviewService(): WorkspaceService {
  const data = structuredClone(previewWorkspace)
  function geofenceRecord(input: GeofenceDraft, id: string) {
    const errors = validateGeofence(
      input,
      data.geofences,
      data.vehicles.map((v) => v.id),
      id,
    )
    if (Object.keys(errors).length || !input.geometry)
      throw new Error(Object.values(errors)[0] || 'Boundary required.')
    return {
      ...structuredClone(input),
      name: input.name.trim(),
      description: input.description.trim(),
      location: input.location.trim() || 'San Francisco preview',
      vehicleIds: [...new Set(input.vehicleIds)],
      geometry: structuredClone(input.geometry),
      id,
      icon: 'i-lucide-map-pinned',
      radius: geometrySummary(input.geometry),
    }
  }
  return {
    async createGeofence(input) {
      const id = `GF-${String(data.geofences.length + 1).padStart(3, '0')}`
      data.geofences.push(geofenceRecord(input, id))
      return id
    },
    async updateGeofence(id, input) {
      const index = data.geofences.findIndex((z) => z.id === id)
      if (index < 0) throw new Error('Geofence not found.')
      data.geofences[index] = geofenceRecord(input, id)
    },
    async load() {
      return structuredClone(data)
    },
    async addVehicle(input: NewVehicle) {
      const error = validateVehicle(input, data.vehicles)
      if (error) throw new Error(error)
      data.vehicles.push({
        id: `VH-${String(data.vehicles.length + 1).padStart(3, '0')}`,
        name: input.name.trim(),
        plate: input.plate.trim().toUpperCase(),
        driver: 'Unassigned',
        initials: '—',
        type: 'Unassigned vehicle',
        status: 'offline',
        speed: 0,
        fuel: 0,
        location: 'Awaiting tracker assignment',
        updated: 'No telemetry',
        distance: 0,
        position: null,
      })
    },
    async updateAlert(id: string, status: AlertState) {
      const alert = data.alerts.find((a) => a.id === id)
      if (!alert) throw new Error('Alert not found.')
      alert.status = status
    },
    async savePreferences(input: Preferences) {
      data.preferences = { ...input }
    },
  }
}
