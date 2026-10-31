import { useQuery } from '@tanstack/vue-query'
import { toValue, type MaybeRefOrGetter } from 'vue'
import { isRetryable } from '../../core/domain/errors'
import { queryKeys } from '../../core/queries/keys'
import type { Vehicle } from '../../fleet/domain/fleet'
import type { FleetAlert, Trip, Geofence } from '../domain/types'
import { useWorkspaceContextValue, useWorkspaceServices } from './useWorkspaceServices'

type Records = { fleet: Vehicle; alerts: FleetAlert; trips: Trip; geofences: Geofence }

export function useWorkspaceRecord<K extends keyof Records>(
  domain: K,
  id: MaybeRefOrGetter<string>,
) {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  return useQuery({
    queryKey: computed(() => queryKeys[domain].detail(context, toValue(id))),
    queryFn: async ({ signal }): Promise<Records[K] | null> =>
      // Each service's get result matches its domain; preserve that relationship for callers.
      (await services[domain].get(toValue(id), { signal })) as Records[K] | null,
    enabled: computed(() => !!toValue(id) && (services.mode === 'preview' || !import.meta.server)),
    staleTime: domain === 'alerts' ? 15_000 : 30_000,
    retry: (count, error) => count < 1 && isRetryable(error),
  })
}
