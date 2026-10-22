import type { MapSearchService, MapSearchResult } from '../domain/types'
export function useMapSearch(service: MaybeRefOrGetter<MapSearchService>) {
  const query = ref(''),
    results = ref<MapSearchResult[]>([]),
    busy = ref(false),
    error = ref(''),
    open = ref(false)
  let controller: AbortController | undefined,
    timer: ReturnType<typeof setTimeout> | undefined,
    version = 0
  function cancel() {
    clearTimeout(timer)
    controller?.abort()
    version++
    busy.value = false
  }
  async function search() {
    cancel()
    const current = version
    controller = new AbortController()
    busy.value = true
    error.value = ''
    results.value = []
    try {
      const data = await toValue(service).search(query.value, { signal: controller.signal })
      if (current === version) results.value = data
    } catch (reason) {
      if (current === version && !controller.signal.aborted)
        error.value = reason instanceof Error ? reason.message : 'Map search failed. Please retry.'
    } finally {
      if (current === version) busy.value = false
    }
  }
  watch(query, () => {
    cancel()
    error.value = ''
    results.value = []
    open.value = !!query.value.trim()
    if (open.value) {
      busy.value = true
      timer = setTimeout(search, 180)
    }
  })
  function close() {
    cancel()
    open.value = false
  }
  function clear() {
    query.value = ''
    close()
  }
  watch(
    () => toValue(service),
    () => {
      cancel()
      results.value = []
      error.value = ''
      if (open.value) void search()
    },
  )
  onScopeDispose(cancel)
  return { query, results, busy, error, open, search, close, clear }
}
import { ref, watch, toValue, onScopeDispose } from 'vue'
