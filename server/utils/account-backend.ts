import { z } from 'zod'
import {
  selfProfileSchema,
  passwordChangeSchema,
  type SelfProfileInput,
  type PasswordChangeInput,
} from '#shared/self-account'
import { userSchema } from '../../app/features/auth/domain/auth'
import { AuthFailure, createAuthBackend } from './wuu-auth'
import { originIsSafe } from './wuu-gateway'
import { upstreamApi } from './upstream-api'

export function createAccountBackend(origin: string, fetcher: typeof fetch = globalThis.fetch) {
  async function write(
    action: 'profile' | 'password',
    token: string,
    body: SelfProfileInput | PasswordChangeInput,
  ) {
    if (!origin || !originIsSafe(origin))
      throw new AuthFailure(503, 'The live API is not configured safely on this server.')
    const payload =
      action === 'profile'
        ? (() => {
            const parsed = selfProfileSchema.parse(body)
            return { first_name: parsed.firstName, last_name: parsed.lastName }
          })()
        : (() => {
            const parsed = passwordChangeSchema.parse(body)
            return { old_password: parsed.oldPassword, new_password: parsed.newPassword }
          })()
    let response: Response
    try {
      response = await fetcher(
        `${origin.replace(/\/$/, '')}${action === 'profile' ? upstreamApi.me : upstreamApi.changePassword}`,
        {
          method: action === 'profile' ? 'PUT' : 'POST',
          headers: {
            accept: 'application/json',
            'content-type': 'application/json',
            authorization: `Bearer ${token}`,
          },
          body: JSON.stringify(payload),
          redirect: 'manual',
          signal: AbortSignal.timeout(15000),
        },
      )
    } catch {
      throw new AuthFailure(502, 'The account service could not be reached.')
    }
    if (action === 'password' && response.status === 204) return undefined
    if (action === 'profile' && response.status === 200) {
      try {
        return userSchema.parse(await response.json())
      } catch {
        throw new AuthFailure(
          502,
          'The saved profile could not be verified. Reload before retrying.',
        )
      }
    }
    if (action === 'password' && response.status === 401) {
      const code = z.object({ code: z.string() }).safeParse(await response.json().catch(() => null))
      if (code.success && code.data.code === 'INVALID_CREDENTIALS')
        throw new AuthFailure(400, 'Current password is incorrect.')
    }
    if (response.status === 401 || response.status === 403)
      throw new AuthFailure(response.status, 'Your session ended. Please sign in again.')
    if (response.status === 429)
      throw new AuthFailure(429, 'Too many requests. Wait a moment before trying again.')
    if (response.status === 400)
      throw new AuthFailure(400, 'The account update was rejected. Check the field requirements.')
    throw new AuthFailure(502, 'The account update could not be confirmed.')
  }
  return {
    async updateProfile(token: string, input: SelfProfileInput) {
      const expected = await createAuthBackend(origin, fetcher).me(token)
      const result = await write('profile', token, input)
      if (
        !result ||
        result.id !== expected.id ||
        result.email !== expected.email ||
        result.organization_id !== expected.organization_id ||
        !result.enabled ||
        result.locked
      )
        throw new AuthFailure(
          502,
          'The saved profile identity could not be verified. Reload before retrying.',
        )
      return result
    },
    async changePassword(token: string, input: PasswordChangeInput) {
      await write('password', token, input)
    },
  }
}
