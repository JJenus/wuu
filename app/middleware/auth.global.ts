import { useAuth } from '~/features/auth/composables/useAuth'
import { safeReturnPath } from '~/features/auth/domain/auth'

export default defineNuxtRouteMiddleware((to) => {
  if (useRuntimeConfig().public.dataMode !== 'live') return
  const { user } = useAuth()
  if (to.path === '/sign-in') return
  if (!user.value)
    return navigateTo({ path: '/sign-in', query: { next: safeReturnPath(to.fullPath) } })
  // Until effective platform capabilities have a verified contract, live admin stays unavailable.
  if (to.path.startsWith('/admin')) return navigateTo('/access-denied')
})
