import { useAuth } from '~/features/auth/composables/useAuth'
import { errorMessage, isApiError } from '~/features/core/domain/errors'

export default defineNuxtPlugin({
  name: 'auth',
  async setup() {
    if (useRuntimeConfig().public.dataMode !== 'live') return
    const auth = useAuth()
    const responseCookies = import.meta.server ? useResponseHeader('set-cookie') : undefined
    try {
      await auth.restore(useRequestFetch(), (cookies) => {
        if (responseCookies && cookies.length) responseCookies.value = cookies
      })
    } catch (failure) {
      auth.user.value = null
      auth.stopRenewal()
      auth.sessionError.value =
        isApiError(failure) && failure.status === 401 ? '' : errorMessage(failure)
    }
  },
})
