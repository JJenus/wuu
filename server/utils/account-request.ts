import { createError, getHeader, getQuery, setResponseStatus, type H3Event } from 'h3'
import { selfProfileSchema, passwordChangeSchema } from '#shared/self-account'
import { createAccountBackend } from './account-backend'
import { AuthFailure } from './wuu-auth'
import {
  assertSameOrigin,
  authConfig,
  authError,
  clearWuuSession,
  currentSession,
  ensureFreshSession,
  noStore,
} from './wuu-session'

async function accountBody(event: H3Event): Promise<unknown> {
  if (getHeader(event, 'content-type')?.split(';')[0]?.trim().toLowerCase() !== 'application/json')
    throw createError({ statusCode: 415, message: 'A JSON body is required.' })
  const chunks: Buffer[] = []
  let size = 0
  for await (const chunk of event.node.req.iterator({ destroyOnReturn: false })) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
    size += buffer.length
    if (size > 4096) {
      event.node.req.resume()
      throw createError({ statusCode: 413, message: 'Account request is too large.' })
    }
    chunks.push(buffer)
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown
  } catch {
    throw createError({ statusCode: 400, message: 'Invalid account request.' })
  }
}

export async function handleAccountMutation(event: H3Event, action: 'profile' | 'password') {
  noStore(event)
  assertSameOrigin(event)
  if (Object.keys(getQuery(event)).length)
    throw createError({
      statusCode: 400,
      message: 'Account updates do not accept query parameters.',
    })
  const current = currentSession(event)
  const body = await accountBody(event)
  const backend = createAccountBackend(authConfig(event).origin)
  if (action === 'profile') {
    const parsed = selfProfileSchema.safeParse(body)
    if (!parsed.success)
      throw createError({
        statusCode: 400,
        message: 'Enter first and last names of at most 100 characters.',
      })
    try {
      const session = await ensureFreshSession(event, current)
      return {
        user: await backend.updateProfile(session.accessToken, parsed.data),
        expires_at: session.expiresAt,
      }
    } catch (error) {
      if (error instanceof AuthFailure && [401, 403].includes(error.status)) clearWuuSession(event)
      return authError(error)
    }
  }
  const parsed = passwordChangeSchema.safeParse(body)
  if (!parsed.success)
    throw createError({
      statusCode: 400,
      message:
        'Enter your current password and a new password of at least 8 characters, at most 72 UTF-8 bytes.',
    })
  const session = await ensureFreshSession(event, current)
  try {
    await backend.changePassword(session.accessToken, parsed.data)
    clearWuuSession(event)
    setResponseStatus(event, 204)
    return null
  } catch (error) {
    // Definite validation/wrong-password/rate-limit failures leave the existing session intact.
    if (error instanceof AuthFailure && [400, 429].includes(error.status)) return authError(error)
    clearWuuSession(event)
    if (error instanceof AuthFailure && [401, 403].includes(error.status)) return authError(error)
    throw createError({
      statusCode: 502,
      message:
        'Password change could not be confirmed. Sign in with the new password first; the request was not retried.',
    })
  }
}
