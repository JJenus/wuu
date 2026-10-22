import { effectScope, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useMapSearch } from '../../app/features/maps/composables/useMapSearch'
import type { MapSearchResult, MapSearchService } from '../../app/features/maps/domain/types'
afterEach(() => vi.useRealTimers())
const result = (id: string): MapSearchResult => ({
  id,
  label: id,
  description: 'Provider result',
  kind: 'place',
  source: 'live',
  coordinate: { latitude: 37.7, longitude: -122.4 },
})
describe('abortable search workflow', () => {
  it('debounces, aborts stale work and ignores out-of-order provider responses', async () => {
    vi.useFakeTimers()
    const requests: {
      query: string
      signal: AbortSignal
      resolve: (value: MapSearchResult[]) => void
    }[] = []
    const source: MapSearchService = {
      search: (query, { signal }) =>
        new Promise((resolve) => requests.push({ query, signal, resolve })),
    }
    const scope = effectScope(),
      model = scope.run(() => useMapSearch(source))!
    try {
      model.query.value = 'first'
      await nextTick()
      await vi.advanceTimersByTimeAsync(190)
      model.query.value = 'second'
      await nextTick()
      expect(requests[0]!.signal.aborted).toBe(true)
      await vi.advanceTimersByTimeAsync(190)
      requests[1]!.resolve([result('second')])
      await Promise.resolve()
      requests[0]!.resolve([result('first')])
      await Promise.resolve()
      expect(model.results.value.map((r) => r.id)).toEqual(['second'])
      model.close()
      expect(model.open.value).toBe(false)
      model.clear()
      expect(model.query.value).toBe('')
    } finally {
      scope.stop()
    }
  })
  it('surfaces live provider failures without preview fallback and cancels on scope disposal', async () => {
    const source = ref<MapSearchService>({
      search: async () => {
        throw new Error('Provider unavailable')
      },
    })
    const scope = effectScope(),
      model = scope.run(() => useMapSearch(source))!
    model.query.value = 'failed'
    await nextTick()
    await model.search()
    expect(model.error.value).toBe('Provider unavailable')
    expect(model.results.value).toEqual([])
    expect(model.busy.value).toBe(false)
    let signal: AbortSignal | undefined
    source.value = {
      search: (_query, options) => {
        signal = options.signal
        return new Promise(() => {})
      },
    }
    await nextTick()
    expect(signal?.aborted).toBe(false)
    scope.stop()
    expect(signal?.aborted).toBe(true)
  })
})
