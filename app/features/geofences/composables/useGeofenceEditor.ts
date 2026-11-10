import { validateGeofence, type GeofenceDraft, type Geometry } from '../domain/geofence'
import { useWorkspace } from '../../workspace/composables/useWorkspace'
import { useGeofenceAccess } from './useGeofenceAccess'
import { isApiError } from '../../core/domain/errors'

export function useGeofenceEditor(initial: GeofenceDraft, id?: string) {
  const { geofences, vehicles, fleetQuery, createGeofence, updateGeofence } =
    useWorkspace('geofences')
  const {
    source,
    allowed: writeAllowed,
    message: permissionMessage,
    retry: retryPermissions,
    assertWriteAccess,
  } = useGeofenceAccess()
  const saveUnavailable = computed(
    () =>
      !source.isPreview &&
      (!writeAllowed.value || fleetQuery.isPending.value || fleetQuery.isError.value),
  )
  // Preserve invalid numeric drafts (NaN) until validation. JSON cloning turns
  // them into null, which can silently become a zero coordinate in arithmetic.
  const copy = <T>(value: T): T => {
    if (Array.isArray(value)) return value.map((entry) => copy(entry)) as T
    if (value !== null && typeof value === 'object')
      return Object.fromEntries(
        Object.entries(value).map(([key, entry]) => [key, copy(entry)]),
      ) as T
    return value
  }
  const draft = reactive<GeofenceDraft>(copy(initial))
  const baseline = JSON.stringify(initial)
  const dirty = computed(() => JSON.stringify(draft) !== baseline)
  const errors = ref<Record<string, string>>({})
  const busy = ref(false)
  const history = ref<(Geometry | null)[]>([])
  const mapDragging = ref(false)
  const discardOpen = ref(false)
  const toast = useToast()
  let allowed = false
  let decision: ((leave: boolean) => void) | undefined
  function remember() {
    history.value.push(copy(draft.geometry))
    if (history.value.length > 50) history.value.shift()
  }
  function setGeometry(geometry: Geometry | null) {
    if (busy.value || !writeAllowed.value) return
    if (!mapDragging.value) remember()
    draft.geometry = copy(geometry)
  }
  function beginMapEdit() {
    if (busy.value || !writeAllowed.value) return
    remember()
    mapDragging.value = true
  }
  function endMapEdit() {
    mapDragging.value = false
  }
  function undo() {
    if (busy.value || !writeAllowed.value) return
    if (history.value.length) draft.geometry = history.value.pop()!
  }
  function answerDiscard(leave: boolean) {
    discardOpen.value = false
    decision?.(leave)
    decision = undefined
  }
  onBeforeRouteLeave(() => {
    if (allowed || !dirty.value || busy.value) return !busy.value
    discardOpen.value = true
    return new Promise<boolean>((resolve) => {
      decision = resolve
    })
  })
  watch(discardOpen, (open) => {
    if (!open && decision) answerDiscard(false)
  })
  function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value && !allowed) {
      event.preventDefault()
      event.returnValue = ''
    }
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onUnmounted(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    decision?.(false)
  })
  async function save() {
    if (busy.value || saveUnavailable.value) return
    const input = copy(draft)
    errors.value = validateGeofence(
      input,
      geofences.value,
      vehicles.value.map((v) => v.id),
      id,
      source.mode,
    )
    if (Object.keys(errors.value).length) {
      await nextTick()
      document.getElementById('geofence-errors')?.focus()
      return
    }
    busy.value = true
    try {
      await assertWriteAccess()
      const target = id || (await createGeofence(input))
      if (id) await updateGeofence(id, input)
      allowed = true
      toast.add({
        title: source.isPreview
          ? id
            ? 'Geofence updated in preview'
            : 'Geofence created in preview'
          : id
            ? 'Geofence updated'
            : 'Geofence created',
        description: source.isPreview
          ? 'Session only. No monitoring or notification was activated.'
          : 'Saved to the live workspace. Alert rules are configured separately.',
        color: 'success',
      })
      busy.value = false
      await navigateTo(`/geofences/${target}`)
    } catch (error) {
      const uncertain =
        isApiError(error) && ['network', 'timeout', 'server', 'contract'].includes(error.kind)
      errors.value = {
        save:
          (error instanceof Error
            ? error.message
            : 'Unable to save this geofence. Please try again.') +
          (uncertain && !source.isPreview
            ? ' This request may have been saved. Check the geofence list before retrying; requests are not replayed automatically.'
            : ''),
      }
    } finally {
      busy.value = false
    }
  }
  return {
    draft,
    dirty,
    errors,
    busy,
    source,
    saveUnavailable,
    fleetQuery,
    writeAllowed,
    permissionMessage,
    retryPermissions,
    history,
    discardOpen,
    vehicles,
    setGeometry,
    beginMapEdit,
    endMapEdit,
    undo,
    answerDiscard,
    save,
  }
}
