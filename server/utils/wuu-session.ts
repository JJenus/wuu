import { createHash, randomUUID } from 'node:crypto'
import {
  createError,
  deleteCookie,
  getCookie,
  getHeader,
  getRequestURL,
  setCookie,
  setResponseHeader,
  isError,
  type H3Event,
} from 'h3'
import {
  AuthFailure,
  createAuthBackend,
  openSession,
  sameOrigin,
  sealWuuSession,
  sessionFromTokens,
  type WuuSession,
  type AuthTokens,
} from './wuu-auth'

const cookieName = 'wuu_session'
const refreshGraceMs = 30_000
const maxCachedRefreshes = 256
const refreshed = new Map<string, { session: WuuSession; expiresAt: number }>()
const pending = new Map<string, { promise: Promise<WuuSession>; cancelled: boolean }>()
// A transport failure may have consumed the token upstream. Retain that fact
// until its original expiry instead of allowing another request to replay it.
const blocked = new Map<string, number>()
let renewalEpoch = randomUUID()

export function createLoginSession(tokens: AuthTokens): WuuSession {
  return { ...sessionFromTokens(tokens), renewalEpoch }
}

function sessionKey(event: H3Event, session: WuuSession) {
  const config = authConfig(event)
  return createHash('sha256')
    .update(JSON.stringify([config.origin, config.password, session.refreshToken]))
    .digest('hex')
}
function cachedSession(key: string): WuuSession | null {
  const entry = refreshed.get(key)
  if (!entry) return null
  if (entry.expiresAt <= Date.now() || entry.session.refreshExpiresAt <= Date.now()) {
    refreshed.delete(key)
    return null
  }
  return entry.session
}
function rememberSession(key: string, session: WuuSession) {
  refreshed.set(key, { session, expiresAt: Date.now() + refreshGraceMs })
}
function invalidateSession(event: H3Event) {
  const session = openSession(getCookie(event, cookieName), authConfig(event).password)
  if (!session) return
  const keys = new Set([sessionKey(event, session)])
  // Include both ancestors and descendants so logout cannot leave a recoverable
  // receipt or in-flight response for this refresh chain. Other users are untouched.
  let previousSize = 0
  while (keys.size !== previousSize) {
    previousSize = keys.size
    for (const [key, entry] of refreshed) {
      const next = sessionKey(event, entry.session)
      if (keys.has(key) || keys.has(next)) {
        keys.add(key)
        keys.add(next)
      }
    }
  }
  for (const key of keys) {
    if (refreshed.has(key) || pending.has(key) || blocked.has(key))
      blocked.set(key, session.refreshExpiresAt)
    refreshed.delete(key)
    const inFlight = pending.get(key)
    if (inFlight) inFlight.cancelled = true
  }
  if (blocked.size >= maxCachedRefreshes) {
    // Exact long-lived rejection tracking cannot grow indefinitely. A local
    // fail-closed epoch cutover requires old clients to sign in again, while
    // fresh logins can immediately renew after the upstream recovers.
    renewalEpoch = randomUUID()
    blocked.clear()
    refreshed.clear()
    for (const operation of pending.values()) operation.cancelled = true
  }
}
export function authConfig(event: H3Event) {
  const config = useRuntimeConfig(event)
  return {
    origin: String(config.apiOrigin || process.env.WUU_API_ORIGIN || '').trim(),
    password: String(config.sessionPassword || process.env.WUU_SESSION_PASSWORD || ''),
  }
}
export function noStore(event: H3Event) {
  setResponseHeader(event, 'cache-control', 'no-store')
  setResponseHeader(event, 'vary', 'Cookie')
}
export function assertSameOrigin(event: H3Event) {
  if (!sameOrigin(getHeader(event, 'origin'), getHeader(event, 'host')))
    throw createError({ statusCode: 403, message: 'Same-origin requests are required.' })
}
export function currentSession(event: H3Event) {
  const cookie = getCookie(event, cookieName)
  const session = openSession(cookie, authConfig(event).password)
  if (!session || session.renewalEpoch !== renewalEpoch)
    throw createError({ statusCode: 401, message: 'Please sign in to continue.' })
  let current = session
  for (let step = 0; step < maxCachedRefreshes; step++) {
    const key = sessionKey(event, current)
    const rejectedUntil = blocked.get(key)
    if (rejectedUntil !== undefined) {
      if (rejectedUntil > Date.now()) {
        clearWuuSession(event)
        throw createError({ statusCode: 401, message: 'Please sign in to continue.' })
      }
      blocked.delete(key)
    }
    const cached = cachedSession(key)
    if (!cached) break
    current = cached
  }
  if (current !== session) writeSession(event, current)
  event.context.wuuSessionRecovered = current !== session
  return current
}
export function clearWuuSession(event: H3Event) {
  invalidateSession(event)
  deleteCookie(event, cookieName, { path: '/' })
  // Remove the old deployment cookie as well; it can no longer grant gateway access.
  deleteCookie(event, 'wuu_access_token', { path: '/' })
}
export function writeSession(event: H3Event, session: WuuSession) {
  const value = sealWuuSession(session, authConfig(event).password)
  if (value.length > 3800)
    throw new AuthFailure(502, 'The sign-in service returned an oversized session.')
  const hostname = getRequestURL(event).hostname
  setCookie(event, cookieName, value, {
    httpOnly: true,
    sameSite: 'strict',
    secure: !['localhost', '127.0.0.1', '[::1]'].includes(hostname),
    path: '/',
    maxAge: Math.max(1, Math.ceil((session.refreshExpiresAt - Date.now()) / 1000)),
  })
}
export async function refreshSession(event: H3Event, session: WuuSession): Promise<WuuSession> {
  const cookie = getCookie(event, cookieName)
  if (!cookie || session.refreshExpiresAt <= Date.now() || session.renewalEpoch !== renewalEpoch)
    throw createError({ statusCode: 401, message: 'Please sign in to continue.' })
  const key = sessionKey(event, session)
  let inFlight = pending.get(key)
  if (!inFlight) {
    for (const [receiptKey, entry] of refreshed)
      if (entry.expiresAt <= Date.now()) refreshed.delete(receiptKey)
    for (const [blockedKey, expiry] of blocked) if (expiry <= Date.now()) blocked.delete(blockedKey)
    if (pending.size + refreshed.size >= maxCachedRefreshes)
      throw new AuthFailure(503, 'Session renewal is busy. Please try again.')
    const startedAt = Date.now()
    const state = { cancelled: false, promise: Promise.resolve(session) }
    state.promise = (async () => {
      const result = await authBackend(event).refresh(session.refreshToken)
      if (state.cancelled) throw new AuthFailure(401, 'Please sign in to continue.')
      const next = sessionFromTokens(result.tokens, startedAt)
      next.renewalEpoch = session.renewalEpoch
      next.refreshExpiresAt = Math.min(next.refreshExpiresAt, session.refreshExpiresAt)
      rememberSession(key, next)
      return next
    })()
    inFlight = state
    pending.set(key, inFlight)
  }
  try {
    const next = await inFlight.promise
    if (inFlight.cancelled) throw new AuthFailure(401, 'Please sign in to continue.')
    writeSession(event, next)
    return next
  } catch (error) {
    // A lost upstream response may already have consumed the single-use token.
    // Never retry that credential: fail closed and require sign-in instead.
    clearWuuSession(event)
    return authError(error)
  } finally {
    if (pending.get(key) === inFlight) pending.delete(key)
  }
}
export async function ensureFreshSession(
  event: H3Event,
  session: WuuSession,
  thresholdMs = 30_000,
): Promise<WuuSession> {
  if (session.expiresAt > Date.now() + thresholdMs) return session
  return refreshSession(event, session)
}
export function authBackend(event: H3Event) {
  return createAuthBackend(authConfig(event).origin)
}
export function authError(error: unknown): never {
  if (isError(error)) throw error
  if (error instanceof AuthFailure)
    throw createError({ statusCode: error.status, message: error.message })
  throw createError({ statusCode: 502, message: 'The sign-in request could not be completed.' })
}
