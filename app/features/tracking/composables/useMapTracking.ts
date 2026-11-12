import { useQuery } from '@tanstack/vue-query'
import { queryKeys } from '../../core/queries/keys'
import { errorMessage } from '../../core/domain/errors'
import { fleetSummary } from '../../fleet/domain/fleet'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'
import { positionRefreshInterval } from '../domain/live-tracking'
import { useTrackingPreview } from './useTrackingPreview'

/** Snapshot polling only: no notification-socket GPS, interpolated paths or historical claims. */
export function useMapTracking() {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  const live = services.mode === 'live'
  const paused = ref(false)
  const query = useQuery({
    queryKey: queryKeys.fleet.list(context),
    queryFn: ({ signal }) => services.fleet.list({ signal }),
    enabled: !live || !import.meta.server,
    staleTime: live ? 0 : 30_000,
    retry: false,
    refetchInterval: (state) =>
      live ? positionRefreshInterval(state.state.error, paused.value) : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: computed(() => live && !paused.value),
  })
  const vehicles = computed(() => (query.isError.value ? [] : (query.data.value ?? [])))
  const simulation = useTrackingPreview(computed(() => (live ? [] : vehicles.value)))
  const positions = computed(() => (live ? vehicles.value : simulation.positions.value))
  async function toggleUpdates() {
    paused.value = !paused.value
    if (!paused.value) await query.refetch()
  }
  return {
    ...simulation,
    positions,
    summary: computed(() => fleetSummary(vehicles.value)),
    paused,
    toggleUpdates,
    isPending: query.isPending,
    isFetching: query.isFetching,
    isError: query.isError,
    error: computed(() => errorMessage(query.error.value, 'Unable to refresh fleet positions.')),
    refreshedAt: computed(() =>
      query.dataUpdatedAt.value ? new Date(query.dataUpdatedAt.value).toISOString() : null,
    ),
    refetch: () => query.refetch(),
  }
}
