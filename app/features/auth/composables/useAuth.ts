import type { AuthUser } from '../domain/auth'
import { safeReturnPath } from '../domain/auth'
import { authRequest, type AuthSession } from '../api/auth-client'
import { errorMessage, isApiError } from '../../core/domain/errors'

let renewalTimer: ReturnType<typeof setTimeout> | undefined
let renewalGeneration = 0

export function useAuth() {
  const user = useState<AuthUser | null>('auth-user', () => null)
  const expiresAt = useState('auth-expires-at', () => 0)
  const sessionError = useState('auth-error', () => '')
  const busy = ref(false)
  const renewing = useState('auth-renewing', () => false)
  const error = ref('')

  function stopRenewal() {
    if (renewalTimer) clearTimeout(renewalTimer)
    renewalTimer = undefined
  }

  function adopt(session: AuthSession) {
    user.value = session.user
    expiresAt.value = session.expiresAt
    sessionError.value = ''
    scheduleRenewal()
  }

  function scheduleRenewal() {
    stopRenewal()
    if (!import.meta.client || !user.value || !expiresAt.value) return
    const remaining = expiresAt.value - Date.now() - 60_000
    const delay = Math.min(Math.max(remaining, 1_000), 2_147_000_000)
    renewalTimer = setTimeout(() => void renew(), delay)
  }

  async function renew() {
    if (!import.meta.client || renewing.value || !user.value) return
    const generation = renewalGeneration
    const previousUser = user.value
    renewing.value = true
    try {
      const session = await authRequest('refresh')
      if (generation !== renewalGeneration) return
      if (session) {
        adopt(session)
        if (
          session.user.id !== previousUser.id ||
          session.user.organization_id !== previousUser.organization_id
        )
          await navigateTo('/', { external: true })
      }
    } catch (failure) {
      if (generation !== renewalGeneration) return
      if (isApiError(failure) && [401, 403].includes(failure.status ?? 0)) {
        user.value = null
        expiresAt.value = 0
        stopRenewal()
        await navigateTo('/sign-in?expired=1', { external: true })
      } else {
        // A transport or upstream failure is ambiguous. Keep the current session
        // and retry; a later request will fail closed if the server revoked it.
        stopRenewal()
        renewalTimer = setTimeout(() => void renew(), 5_000)
      }
    } finally {
      renewing.value = false
    }
  }

  async function restore(fetcher: typeof $fetch, onCookies?: (cookies: string[]) => void) {
    const session = await authRequest('session', undefined, fetcher, onCookies)
    if (session) adopt(session)
  }

  async function signIn(email: string, password: string, next: unknown) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    try {
      const session = await authRequest('login', { email, password })
      if (session) adopt(session)
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
    renewalGeneration += 1
    stopRenewal()
    try {
      await authRequest('logout')
      user.value = null
      expiresAt.value = 0
      await navigateTo('/sign-in', { external: true })
    } catch {
      // The server clears the local cookie even if upstream revocation fails.
      user.value = null
      expiresAt.value = 0
      await navigateTo('/sign-in?revocation=failed', { external: true })
    } finally {
      busy.value = false
    }
  }
  return {
    user,
    expiresAt,
    sessionError,
    busy,
    renewing,
    error,
    signIn,
    signOut,
    renew,
    restore,
    scheduleRenewal,
    stopRenewal,
  }
}
