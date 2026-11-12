import { useQuery } from '@tanstack/vue-query'
import {
  useWorkspaceServices,
  useWorkspaceContextValue,
} from '../../workspace/composables/useWorkspaceServices'
import { recordedSample } from '../domain/recorded-history'
import { usePlayback } from './usePlayback'

export function useRecordedReplay(id: MaybeRefOrGetter<string>) {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const query = useQuery({
    queryKey: computed(() => ['trips', context, 'history', toValue(id)]),
    queryFn: ({ signal }) => services.trips.history(toValue(id), { signal }),
    enabled: computed(() => services.mode === 'live' && !import.meta.server && !!toValue(id)),
    staleTime: 30_000,
    retry: false,
  })
  // Never display cached coordinates after a refresh has failed or while replacing them.
  const history = computed(() =>
    query.isError.value || query.isFetching.value ? undefined : query.data.value,
  )
  const duration = computed(() =>
    history.value
      ? Math.max(
          0,
          (Date.parse(history.value.windowEnd) - Date.parse(history.value.windowStart)) / 1000,
        )
      : 0,
  )
  const playback = usePlayback(duration)
  const at = computed(
    () =>
      (history.value ? Date.parse(history.value.windowStart) : 0) + playback.seconds.value * 1000,
  )
  const current = computed(() => recordedSample(history.value?.points ?? [], at.value))
  watch(history, () => playback.restart())
  return { ...playback, query, history, duration, at, current }
}
