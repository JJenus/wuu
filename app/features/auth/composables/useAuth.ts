import type { AuthUser } from '../domain/auth'
import { safeReturnPath } from '../domain/auth'
import { authRequest } from '../api/auth-client'
import { errorMessage } from '../../core/domain/errors'

export function useAuth() {
  const user = useState<AuthUser | null>('auth-user', () => null)
  const sessionError = useState('auth-error', () => '')
  const busy = ref(false)
  const error = ref('')
  async function signIn(email: string, password: string, next: unknown) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      user.value = await authRequest('login', { email, password })
      sessionError.value = ''
      await navigateTo(safeReturnPath(next), { external: true })
    } catch (failure) {
      error.value = errorMessage(failure)
    } finally {
      busy.value = false
    }
  }
  async function signOut() {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      await authRequest('logout')
      user.value = null
      await navigateTo('/sign-in', { external: true })
    } catch {
      // The server clears the local cookie even if upstream revocation fails.
      user.value = null
      await navigateTo('/sign-in?revocation=failed', { external: true })
    } finally {
      busy.value = false
    }
  }
  return { user, sessionError, busy, error, signIn, signOut }
}
