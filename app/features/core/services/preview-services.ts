import { createPreviewService } from '../../workspace/api/preview-service'
import type { WorkspaceServices } from './workspace-services'
import { ApiError } from '../domain/errors'

export function createPreviewServices(): WorkspaceServices {
  const service = createPreviewService()
  return {
    mode: 'preview',
    subscriptions: {
      catalog: unavailableSubscriptions,
      list: unavailableSubscriptions,
      enroll: unavailableSubscriptions,
      cancel: unavailableSubscriptions,
    },
    fleet: {
      async list() {
        return (await service.load()).vehicles
      },
      async get(id) {
        return (await service.load()).vehicles.find((vehicle) => vehicle.id === id) ?? null
      },
      async create(input) {
        await service.addVehicle(input)
        const vehicles = (await service.load()).vehicles
        const created = vehicles.find(
          (vehicle) => vehicle.plate.toLowerCase() === input.plate.trim().toLowerCase(),
        )
        if (!created) throw new Error('The vehicle was created but could not be read back.')
        return created
      },
    },
    alerts: {
      async list() {
        return (await service.load()).alerts
      },
      async get(id) {
        return (await service.load()).alerts.find((alert) => alert.id === id) ?? null
      },
      async transition(id, status) {
        await service.updateAlert(id, status)
      },
    },
    trips: {
      async list() {
        return (await service.load()).trips
      },
      async get(id) {
        return (await service.load()).trips.find((trip) => trip.id === id) ?? null
      },
    },
    geofences: {
      async list() {
        return (await service.load()).geofences
      },
      async get(id) {
        return (await service.load()).geofences.find((zone) => zone.id === id) ?? null
      },
      create: (input) => service.createGeofence(input),
      update: (id, input) => service.updateGeofence(id, input),
    },
    preferences: {
      async get() {
        return (await service.load()).preferences
      },
      save: (input) => service.savePreferences(input),
    },
  }
}

async function unavailableSubscriptions(): Promise<never> {
  throw new ApiError({
    kind: 'unavailable',
    message: 'Subscriptions require a live signed-in workspace. No preview billing is simulated.',
  })
}
