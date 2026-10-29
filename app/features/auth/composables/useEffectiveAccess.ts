import { useQuery } from '@tanstack/vue-query'
import {
  useWorkspaceContextValue,
  useWorkspaceServices,
} from '../../workspace/composables/useWorkspaceServices'

export function useEffectiveAccess() {
  const services = useWorkspaceServices()
  const context = useWorkspaceContextValue()
  return useQuery({
    queryKey: ['effective-access', context],
    queryFn: ({ signal }) => services.access.get(signal),
    enabled: services.mode === 'live' && !import.meta.server,
    staleTime: 0,
  })
}
