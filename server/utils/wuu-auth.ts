import { createCipheriv, createDecipheriv, createHash, randomBytes } from 'node:crypto'
import { z } from 'zod'
import { loginSchema, userSchema } from '../../app/features/auth/domain/auth'
import { originIsSafe } from './wuu-gateway'

const sessionSchema = z.object({
  accessToken: z.string().min(1).max(6000),
  refreshToken: z.string().min(1).max(6000),
  expiresAt: z.number().finite(),
  refreshExpiresAt: z.number().finite(),
  renewalEpoch: z.string().uuid().optional(),
})
export type WuuSession = z.infer<typeof sessionSchema>
const tokensSchema = z.object({
  access_token: z.string().min(1).max(6000),
  refresh_token: z.string().min(1).max(6000),
  expires_in: z.number().int().positive().max(86400),
  refresh_expires_in: z
    .number()
    .int()
    .positive()
    .max(365 * 24 * 60 * 60),
  token_type: z.literal('Bearer'),
})
export interface AuthTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
}
export function sessionFromTokens(tokens: AuthTokens, now = Date.now()): WuuSession {
  return sessionSchema.parse({
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: now + tokens.expiresIn * 1000,
    refreshExpiresAt: now + tokens.refreshExpiresIn * 1000,
  })
}

export class AuthFailure extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
  }
}

function key(password: string) {
  if (password.length < 32)
    throw new AuthFailure(503, 'Secure sessions are not configured on this server.')
  return createHash('sha256').update(password).digest()
}
export function sealWuuSession(session: WuuSession, password: string): string {
  const iv = randomBytes(12)
  const cipher = createCipheriv('aes-256-gcm', key(password), iv)
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(sessionSchema.parse(session))),
    cipher.final(),
  ])
  return Buffer.concat([iv, cipher.getAuthTag(), encrypted]).toString('base64url')
}
export function openSession(
  cookie: string | undefined,
  password: string,
  now = Date.now(),
): WuuSession | null {
  if (!cookie || cookie.length > 10000) return null
  try {
    const data = Buffer.from(cookie, 'base64url')
    if (data.toString('base64url') !== cookie) return null
    const decipher = createDecipheriv('aes-256-gcm', key(password), data.subarray(0, 12))
    decipher.setAuthTag(data.subarray(12, 28))
    const result = sessionSchema.parse(
      JSON.parse(Buffer.concat([decipher.update(data.subarray(28)), decipher.final()]).toString()),
    )
    return result.refreshExpiresAt > now ? result : null
  } catch {
    return null
  }
}
export function sameOrigin(origin: string | undefined, host: string | undefined): boolean {
  if (!origin || !host) return false
  try {
    const url = new URL(origin)
    return url.origin === origin && url.host === host && originIsSafe(origin)
  } catch {
    return false
  }
}

export function createAuthBackend(origin: string, fetcher: typeof fetch = globalThis.fetch) {
  async function request(path: string, token?: string, body?: unknown) {
    if (!origin || !originIsSafe(origin))
      throw new AuthFailure(503, 'The live API is not configured safely on this server.')
    let response: Response
    try {
      response = await fetcher(`${origin.replace(/\/$/, '')}${path}`, {
        method: body === undefined && path.endsWith('/me') ? 'GET' : 'POST',
        headers: {
          accept: 'application/json',
          'content-type': 'application/json',
          ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        redirect: 'manual',
        signal: AbortSignal.timeout(15000),
      })
    } catch {
      throw new AuthFailure(502, 'The sign-in service could not be reached. Please try again.')
    }
    if (!response.ok)
      throw new AuthFailure(
        response.status >= 400 && response.status < 600 ? response.status : 502,
        response.status === 429
          ? 'Too many sign-in attempts. Please wait before trying again.'
          : response.status === 401 || response.status === 403
            ? 'Sign-in failed or your session expired. Check your credentials and try again.'
            : 'The sign-in service is unavailable. Please try again.',
      )
    if (response.status === 204) return undefined
    try {
      return (await response.json()) as unknown
    } catch {
      throw new AuthFailure(502, 'The sign-in service returned an unreadable response.')
    }
  }
  async function me(token: string) {
    const result = userSchema.safeParse(await request('/api/v1/users/me', token))
    if (!result.success)
      throw new AuthFailure(502, 'The sign-in service returned an invalid identity.')
    if (!result.data.enabled || result.data.locked)
      throw new AuthFailure(401, 'This account is unavailable. Please sign in again.')
    return result.data
  }
  function parseTokens(value: unknown): AuthTokens {
    const result = tokensSchema.safeParse(value)
    if (!result.success)
      throw new AuthFailure(502, 'The sign-in service returned an invalid session.')
    return {
      accessToken: result.data.access_token,
      refreshToken: result.data.refresh_token,
      expiresIn: result.data.expires_in,
      refreshExpiresIn: result.data.refresh_expires_in,
    }
  }
  async function authenticatedTokens(value: unknown) {
    const tokens = parseTokens(value)
    const user = await me(tokens.accessToken)
    return { user, tokens }
  }
  return {
    me,
    async login(input: z.infer<typeof loginSchema>) {
      return authenticatedTokens(
        await request('/api/v1/auth/login', undefined, loginSchema.parse(input)),
      )
    },
    async refresh(refreshToken: string) {
      return authenticatedTokens(
        await request('/api/v1/auth/refresh', undefined, { refresh_token: refreshToken }),
      )
    },
    async logout(token: string) {
      await request('/api/v1/auth/logout', token)
    },
  }
}
