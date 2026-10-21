import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPreviewService } from '~/features/workspace/api/preview-service'

export default defineNuxtPlugin(async (nuxtApp) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { staleTime: 30_000, retry: false } },
  })
  const service = createPreviewService()
  // Seed this deterministic preview on both server and client. No empty-to-full
  // hydration race, and no dropped interactions while fixtures are initializing.
  queryClient.setQueryData(['workspace', 'preview', 'northstar'], await service.load())
  nuxtApp.vueApp.use(VueQueryPlugin, { queryClient })
  // A fresh adapter per Nuxt application: preview state is never shared across SSR users.
  return { provide: { workspaceService: service } }
})
