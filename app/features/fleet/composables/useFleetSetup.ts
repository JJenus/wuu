import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useEffectiveAccess } from '../../auth/composables/useEffectiveAccess'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'
import { queryKeys } from '../../core/queries/keys'
import { errorMessage, isApiError } from '../../core/domain/errors'
import type { TrackerRegistration, VehicleRegistration } from '../domain/registration'

export function useFleetSetup() {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const access = useEffectiveAccess()
  const client = useQueryClient()
  const busy = ref(false)
  const failure = ref('')
  const permitted = (permission: string) =>
    !access.isPending.value &&
    !access.isError.value &&
    !!access.data.value?.permissions.includes(permission)
  const canCreate = computed(() => services.mode === 'preview' || permitted('vehicles.write'))
  const canAssign = computed(() => services.mode === 'live' && permitted('vehicles.assign_device'))
  const canReadTrackers = computed(() => services.mode === 'live' && permitted('devices.read'))

  async function submit<T>(permission: string, operation: () => Promise<T>): Promise<T | null> {
    if (busy.value) return null
    busy.value = true
    failure.value = ''
    try {
      const current = await access.refetch()
      if (current.isError || !current.data?.permissions.includes(permission))
        throw new Error('You do not currently have permission for this fleet action.')
      const result = await operation()
      // A successful write stays successful even when a subsequent refetch fails.
      await client.invalidateQueries({ queryKey: queryKeys.fleet.all(context) })
      return result
    } catch (error) {
      const uncertain =
        isApiError(error) && ['network', 'timeout', 'contract', 'server'].includes(error.kind)
      failure.value =
        errorMessage(error, 'The fleet action could not be completed.') +
        (uncertain
          ? ' The request may have been saved. Reload and check the IDs before retrying; requests are not replayed automatically.'
          : '')
      return null
    } finally {
      busy.value = false
    }
  }
  return {
    canCreate,
    canAssign,
    canReadTrackers,
    busy,
    failure,
    accessError: computed(() => access.isError.value),
    retryAccess: () => access.refetch(),
    registerVehicle: (input: VehicleRegistration) =>
      submit('vehicles.write', () => services.fleet.registerVehicle(input)),
    registerTracker: (input: TrackerRegistration) =>
      submit('vehicles.assign_device', () => services.fleet.registerTracker(input)),
  }
}

export function useAssignedTrackers(vehicleId: () => string, enabled: () => boolean) {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  return useQuery({
    queryKey: computed(() => [...queryKeys.fleet.all(context), 'trackers', vehicleId()]),
    queryFn: ({ signal }) => services.fleet.listTrackers(vehicleId(), { signal }),
    enabled: computed(() => !import.meta.server && enabled() && !!vehicleId()),
    retry: false,
  })
}
