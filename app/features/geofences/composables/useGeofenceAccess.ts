import { useDataSource } from '../../core/composables/useDataSource'
import { useEffectiveAccess } from '../../auth/composables/useEffectiveAccess'

export function useGeofenceAccess() {
  const source = useDataSource()
  const access = useEffectiveAccess()
  const allowed = computed(
    () =>
      source.isPreview ||
      (!access.isPending.value &&
        !access.isError.value &&
        !!access.data.value?.permissions.includes('alerts.write')),
  )
  const message = computed(() =>
    source.isPreview || allowed.value
      ? ''
      : access.isPending.value
        ? 'Checking geofence permissions…'
        : access.isError.value
          ? 'Geofence permissions could not be checked.'
          : 'You do not have permission to edit geofences.',
  )
  async function assertWriteAccess() {
    if (source.isPreview) return
    const current = await access.refetch()
    if (current.isError || !current.data?.permissions.includes('alerts.write'))
      throw new Error('You do not currently have permission to edit geofences.')
  }
  return { source, allowed, message, assertWriteAccess, retry: () => access.refetch() }
}
