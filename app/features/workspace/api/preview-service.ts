import { validateVehicle, type NewVehicle } from '../../fleet/domain/fleet'
import type { AlertState, Preferences, WorkspaceService } from '../domain/types'
import { previewWorkspace } from './fixtures'

export function createPreviewService(): WorkspaceService {
  const data = structuredClone(previewWorkspace)
  return {
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
        x: 50,
        y: 50,
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
