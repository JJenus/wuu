import { z } from 'zod'
import { userSchema, loginSchema, type AuthUser } from '../domain/auth'
import type { SelfProfileInput, PasswordChangeInput } from '#shared/self-account'
import { responseApiError, toApiError } from '../../core/domain/errors'

const sessionSchema = z.object({
  user: userSchema,
  expires_at: z.number().int().positive().max(Number.MAX_SAFE_INTEGER),
})
export interface AuthSession {
  user: AuthUser
  expiresAt: number
}
export async function authRequest(
  action: 'session' | 'login' | 'logout' | 'refresh' | 'profile' | 'change-password',
  body?: z.infer<typeof loginSchema> | SelfProfileInput | PasswordChangeInput,
  fetcher: typeof $fetch = $fetch,
  onCookies?: (cookies: string[]) => void,
): Promise<AuthSession | null> {
  try {
    let responseStatus: number | undefined
    const result = await fetcher(`/api/auth/${action}`, {
      method: action === 'session' ? 'GET' : 'POST',
      body,
      credentials: 'same-origin',
      onResponse({ response }) {
        responseStatus = response.status
        onCookies?.(response.headers.getSetCookie())
      },
    })
    if (action === 'logout') return null
    if (action === 'change-password') {
      if (responseStatus !== 204) throw new Error('Password change could not be confirmed.')
      return null
    }
    const parsed = sessionSchema.parse(result)
    return { user: parsed.user, expiresAt: parsed.expires_at }
  } catch (error) {
    const failure = error as { statusCode?: number; data?: unknown }
    if (failure.statusCode) throw responseApiError(failure.statusCode, failure.data)
    throw toApiError(error)
  }
}
