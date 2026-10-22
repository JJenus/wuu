import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { fleetSummary, type NewVehicle } from '../../fleet/domain/fleet'
import type { AlertState, Preferences } from '../domain/types'
import type { GeofenceDraft } from '../../geofences/domain/geofence'

export function useWorkspace() {
  const { $workspaceService: service } = useNuxtApp()
  const queryClient = useQueryClient()
  const key = ['workspace', 'preview', 'northstar'] as const
  const query = useQuery({ queryKey: key, queryFn: () => service.load() })
  const vehicles = computed(() => query.data.value?.vehicles ?? [])
  const alerts = computed(() => query.data.value?.alerts ?? [])
  const trips = computed(() => query.data.value?.trips ?? [])
  const geofences = computed(() => query.data.value?.geofences ?? [])
  const summary = computed(() => fleetSummary(vehicles.value))
  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: key })
  }
  async function addVehicle(input: NewVehicle) {
    await service.addVehicle(input)
    await refresh()
  }
  async function updateAlert(id: string, status: AlertState) {
    await service.updateAlert(id, status)
    await refresh()
  }
  async function savePreferences(input: Preferences) {
    await service.savePreferences(input)
    await refresh()
  }
  async function createGeofence(input: GeofenceDraft) {
    const id = await service.createGeofence(input)
    await refresh()
    return id
  }
  async function updateGeofence(id: string, input: GeofenceDraft) {
    await service.updateGeofence(id, input)
    await refresh()
  }
  return {
    ...query,
    vehicles,
    alerts,
    trips,
    geofences,
    summary,
    addVehicle,
    updateAlert,
    savePreferences,
    createGeofence,
    updateGeofence,
  }
}
