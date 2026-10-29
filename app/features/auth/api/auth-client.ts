import { z } from 'zod'
import { userSchema, type AuthUser } from '../domain/auth'
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
  action: 'session' | 'login' | 'logout' | 'refresh',
  body?: unknown,
  fetcher: typeof $fetch = $fetch,
  onCookies?: (cookies: string[]) => void,
): Promise<AuthSession | null> {
  try {
    const result = await fetcher(`/api/auth/${action}`, {
      method: action === 'session' ? 'GET' : 'POST',
      body: body as Record<string, unknown> | undefined,
      credentials: 'same-origin',
      onResponse({ response }) {
        onCookies?.(response.headers.getSetCookie())
      },
    })
    if (action === 'logout') return null
    const parsed = sessionSchema.parse(result)
    return { user: parsed.user, expiresAt: parsed.expires_at }
  } catch (error) {
    const failure = error as { statusCode?: number; data?: unknown }
    if (failure.statusCode) throw responseApiError(failure.statusCode, failure.data)
    throw toApiError(error)
  }
}
