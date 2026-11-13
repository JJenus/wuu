import type { AuthUser } from '../domain/auth'
import { safeReturnPath } from '../domain/auth'
import { authRequest, type AuthSession } from '../api/auth-client'
import { errorMessage, isApiError } from '../../core/domain/errors'
import type { SelfProfileInput, PasswordChangeInput } from '#shared/self-account'

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
  function discardLocalSession() {
    renewalGeneration += 1
    stopRenewal()
    user.value = null
    expiresAt.value = 0
  }

  async function updateProfile(input: SelfProfileInput): Promise<boolean> {
    if (busy.value) return false
    busy.value = true
    error.value = ''
    const generation = renewalGeneration
    try {
      const session = await authRequest('profile', input)
      if (generation !== renewalGeneration || !session) return false
      // An older in-flight renewal must not overwrite the names just saved.
      renewalGeneration += 1
      adopt(session)
      return true
    } catch (failure) {
      error.value = errorMessage(failure, 'Profile could not be saved.')
      if (isApiError(failure) && [401, 403].includes(failure.status ?? 0)) {
        discardLocalSession()
        await navigateTo('/sign-in?expired=1', { external: true })
      } else if (
        !isApiError(failure) ||
        ['network', 'timeout', 'contract', 'server'].includes(failure.kind)
      )
        error.value +=
          ' The update may have been saved. Reload before retrying; it will not be replayed automatically.'
      return false
    } finally {
      busy.value = false
    }
  }

  async function changePassword(input: PasswordChangeInput): Promise<boolean> {
    if (busy.value) return false
    busy.value = true
    error.value = ''
    renewalGeneration += 1
    const generation = renewalGeneration
    stopRenewal()
    try {
      await authRequest('change-password', input)
      if (generation !== renewalGeneration) return false
      discardLocalSession()
      await navigateTo('/sign-in?password=changed', { external: true })
      return true
    } catch (failure) {
      if (generation !== renewalGeneration) return false
      if (isApiError(failure) && [400, 429].includes(failure.status ?? 0)) {
        error.value = errorMessage(failure)
        scheduleRenewal()
        return false
      }
      discardLocalSession()
      // A lost browser response may hide a committed change. Forget/revoke the
      // old session best-effort, but never repeat the password mutation.
      try {
        await authRequest('logout')
      } catch {
        /* The sign-in page explains uncertainty. */
      }
      await navigateTo('/sign-in?password=uncertain', { external: true })
      return false
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
    updateProfile,
    changePassword,
    renew,
    restore,
    scheduleRenewal,
    stopRenewal,
  }
}
