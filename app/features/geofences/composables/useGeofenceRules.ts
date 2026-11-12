import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useEffectiveAccess } from '../../auth/composables/useEffectiveAccess'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'
import { queryKeys } from '../../core/queries/keys'
import { errorMessage, isApiError } from '../../core/domain/errors'
import type { GeofenceRuleInput } from '../domain/geofence-rule'

export function useGeofenceRules(id: () => string) {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const access = useEffectiveAccess()
  const client = useQueryClient()
  const key = computed(() => [...queryKeys.geofences.all(context), 'rules', id()])
  const allowed = (permission: string) =>
    !access.isPending.value &&
    !access.isError.value &&
    !!access.data.value?.permissions.includes(permission)
  const canRead = computed(() => services.mode === 'live' && allowed('alerts.read'))
  const canWrite = computed(() => services.mode === 'live' && allowed('alerts.write'))
  const rules = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => services.geofences.rules.list(id(), signal),
    enabled: computed(() => !import.meta.server && canRead.value && !!id()),
    retry: false,
  })
  const busy = ref(false)
  const failure = ref('')
  const success = ref('')
  async function mutate(operation: () => Promise<unknown>) {
    if (busy.value) return false
    busy.value = true
    failure.value = ''
    success.value = ''
    try {
      const current = await access.refetch()
      if (current.isError || !current.data?.permissions.includes('alerts.write'))
        throw new Error('You do not currently have permission to configure alert rules.')
      await operation()
      await client.invalidateQueries({ queryKey: key.value })
      success.value = 'Rule configuration saved. Hardware delivery and notifications are separate.'
      return true
    } catch (error) {
      const uncertain =
        isApiError(error) && ['network', 'timeout', 'server', 'contract'].includes(error.kind)
      failure.value =
        errorMessage(error, 'Rule configuration could not be saved.') +
        (uncertain
          ? ' It may have been saved. Reload the rule list before retrying; requests are not replayed automatically.'
          : '')
      return false
    } finally {
      busy.value = false
    }
  }
  return {
    rules,
    canRead,
    canWrite,
    busy,
    failure,
    success,
    accessError: computed(() => access.isError.value),
    retryAccess: () => access.refetch(),
    create: (input: GeofenceRuleInput) => mutate(() => services.geofences.rules.create(input)),
    setEnabled: (key: string, enabled: boolean) =>
      mutate(() => services.geofences.rules.setEnabled(key, enabled)),
  }
}
