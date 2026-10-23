import { authRequest } from '~/features/auth/api/auth-client'
import type { AuthUser } from '~/features/auth/domain/auth'
import { errorMessage, isApiError } from '~/features/core/domain/errors'

export default defineNuxtPlugin({
  name: 'auth',
  async setup() {
    const user = useState<AuthUser | null>('auth-user', () => null)
    const error = useState('auth-error', () => '')
    if (useRuntimeConfig().public.dataMode !== 'live') return
    try {
      user.value = await authRequest('session', undefined, useRequestFetch())
    } catch (failure) {
      user.value = null
      error.value = isApiError(failure) && failure.status === 401 ? '' : errorMessage(failure)
    }
  },
})
