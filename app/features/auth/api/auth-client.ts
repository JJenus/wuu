import { z } from 'zod'
import { userSchema, type AuthUser } from '../domain/auth'
import { responseApiError, toApiError } from '../../core/domain/errors'

const sessionSchema = z.object({ user: userSchema })
export async function authRequest(
  action: 'session' | 'login' | 'logout',
  body?: unknown,
  fetcher: typeof $fetch = $fetch,
): Promise<AuthUser | null> {
  try {
    const result = await fetcher(`/api/auth/${action}`, {
      method: action === 'session' ? 'GET' : 'POST',
      body: body as Record<string, unknown> | undefined,
      credentials: 'same-origin',
    })
    return action === 'logout' ? null : sessionSchema.parse(result).user
  } catch (error) {
    const failure = error as { statusCode?: number; data?: unknown }
    if (failure.statusCode) throw responseApiError(failure.statusCode, failure.data)
    throw toApiError(error)
  }
}
