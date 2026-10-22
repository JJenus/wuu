import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createGatewayClient } from '~/features/core/api/gateway-client'
import { workspaceContext, type DataMode } from '~/features/core/domain/data-mode'
import { queryKeys } from '~/features/core/queries/keys'
import { createServices } from '~/features/core/services/create-services'

export default defineNuxtPlugin(async (nuxtApp) => {
  const config = useRuntimeConfig()
  const mode: DataMode = config.public.dataMode === 'live' ? 'live' : 'preview'
  const context = workspaceContext({
    mode,
    organizationId: config.public.workspaceOrganizationId,
    userId: config.public.workspaceUserId,
  })
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: false, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  })
  const services = createServices(mode, createGatewayClient())

  if (mode === 'preview') {
    const [vehicles, alerts, trips, geofences, preferences] = await Promise.all([
      services.fleet.list(),
      services.alerts.list(),
      services.trips.list(),
      services.geofences.list(),
      services.preferences.get(),
    ])
    queryClient.setQueryData(queryKeys.fleet.list(context), vehicles)
    queryClient.setQueryData(queryKeys.alerts.list(context), alerts)
    queryClient.setQueryData(queryKeys.trips.list(context), trips)
    queryClient.setQueryData(queryKeys.geofences.list(context), geofences)
    queryClient.setQueryData(queryKeys.preferences.current(context), preferences)
  }

  nuxtApp.vueApp.use(VueQueryPlugin, { queryClient })
  // A fresh adapter per Nuxt application: preview state is never shared across SSR users.
  return { provide: { workspaceServices: services, workspaceContext: context } }
})
