import { validateGeofence, type GeofenceDraft, type Geometry } from '../domain/geofence'
import { useWorkspace } from '../../workspace/composables/useWorkspace'

export function useGeofenceEditor(initial: GeofenceDraft, id?: string) {
  const { geofences, vehicles, createGeofence, updateGeofence } = useWorkspace()
  const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value))
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
    if (!mapDragging.value) remember()
    draft.geometry = copy(geometry)
  }
  function beginMapEdit() {
    remember()
    mapDragging.value = true
  }
  function endMapEdit() {
    mapDragging.value = false
  }
  function undo() {
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
    if (busy.value) return
    errors.value = validateGeofence(
      draft,
      geofences.value,
      vehicles.value.map((v) => v.id),
      id,
    )
    if (Object.keys(errors.value).length) {
      await nextTick()
      document.getElementById('geofence-errors')?.focus()
      return
    }
    busy.value = true
    try {
      const target = id || (await createGeofence(copy(draft)))
      if (id) await updateGeofence(id, copy(draft))
      allowed = true
      toast.add({
        title: id ? 'Geofence updated in preview' : 'Geofence created in preview',
        description: 'Session only. No monitoring or notification was activated.',
        color: 'success',
      })
      busy.value = false
      await navigateTo(`/geofences/${target}`)
    } catch (error) {
      errors.value = {
        save:
          error instanceof Error
            ? error.message
            : 'Unable to save this preview geofence. Please try again.',
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
