import {
  createError,
  deleteCookie,
  getCookie,
  getHeader,
  getRequestURL,
  setCookie,
  setResponseHeader,
  type H3Event,
} from 'h3'
import { AuthFailure, createAuthBackend, openSession, sameOrigin, sealWuuSession } from './wuu-auth'

const cookieName = 'wuu_session'
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
  const session = openSession(getCookie(event, cookieName), authConfig(event).password)
  if (!session) throw createError({ statusCode: 401, message: 'Please sign in to continue.' })
  return session
}
export function clearWuuSession(event: H3Event) {
  deleteCookie(event, cookieName, { path: '/' })
  // Remove the old deployment cookie as well; it can no longer grant gateway access.
  deleteCookie(event, 'wuu_access_token', { path: '/' })
}
export function writeSession(event: H3Event, accessToken: string, expiresIn: number) {
  const value = sealWuuSession(
    { accessToken, expiresAt: Date.now() + expiresIn * 1000 },
    authConfig(event).password,
  )
  if (value.length > 3800)
    throw new AuthFailure(502, 'The sign-in service returned an oversized session.')
  const hostname = getRequestURL(event).hostname
  setCookie(event, cookieName, value, {
    httpOnly: true,
    sameSite: 'strict',
    secure: !['localhost', '127.0.0.1', '[::1]'].includes(hostname),
    path: '/',
    maxAge: expiresIn,
  })
}
export function authBackend(event: H3Event) {
  return createAuthBackend(authConfig(event).origin)
}
export function authError(error: unknown): never {
  if (error instanceof AuthFailure)
    throw createError({ statusCode: error.status, message: error.message })
  throw createError({ statusCode: 502, message: 'The sign-in request could not be completed.' })
}
