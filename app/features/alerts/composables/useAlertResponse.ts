import { useEffectiveAccess } from '../../auth/composables/useEffectiveAccess'
import { useDataSource } from '../../core/composables/useDataSource'
import { errorMessage } from '../../core/domain/errors'
import { useWorkspace } from '../../workspace/composables/useWorkspace'

export function useAlertResponse() {
  const source = useDataSource()
  const access = useEffectiveAccess()
  const { updateAlert } = useWorkspace('alerts')
  const busy = ref(false)
  const failure = ref('')
  const success = ref('')
  const allowed = computed(
    () =>
      source.isPreview ||
      (!access.isPending.value &&
        !access.isError.value &&
        !!access.data.value?.permissions.includes('alerts.write')),
  )
  const accessPending = computed(() => !source.isPreview && access.isPending.value)
  const accessError = computed(() => !source.isPreview && access.isError.value)

  async function submit(id: string, status: 'acknowledged' | 'resolved', notes?: string) {
    if (busy.value) return false
    busy.value = true
    failure.value = ''
    success.value = ''
    try {
      if (!source.isPreview) {
        const current = await access.refetch()
        if (current.isError || !current.data?.permissions.includes('alerts.write'))
          throw new Error('You do not currently have permission to change alerts.')
      }
      await updateAlert(id, status, notes === undefined ? undefined : { resolutionNotes: notes })
      success.value = source.isPreview
        ? `Alert ${status} in preview. This change resets on reload.`
        : `Alert ${status}. Saved to the live workspace.`
      return true
    } catch (error) {
      failure.value = errorMessage(error, 'Unable to update this alert. Please try again.')
      return false
    } finally {
      busy.value = false
    }
  }
  return {
    source,
    allowed,
    accessPending,
    accessError,
    busy,
    failure,
    success,
    submit,
    retryAccess: () => access.refetch(),
  }
}
