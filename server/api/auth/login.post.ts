import { loginSchema } from '../../../app/features/auth/domain/auth'
import {
  assertSameOrigin,
  authBackend,
  authConfig,
  authError,
  noStore,
  writeSession,
} from '../../utils/wuu-session'

export default defineEventHandler(async (event) => {
  noStore(event)
  assertSameOrigin(event)
  if (authConfig(event).password.length < 32)
    throw createError({
      statusCode: 503,
      message: 'Secure sessions are not configured on this server.',
    })
  const length = Number(getHeader(event, 'content-length'))
  if (length > 4096)
    throw createError({ statusCode: 413, message: 'Sign-in request is too large.' })
  if (!getHeader(event, 'content-type')?.startsWith('application/json'))
    throw createError({ statusCode: 415, message: 'JSON is required.' })
  const raw = await readRawBody(event)
  if (!raw || Buffer.byteLength(raw) > 4096)
    throw createError({ statusCode: 400, message: 'Invalid sign-in request.' })
  let body: unknown
  try {
    body = JSON.parse(raw)
  } catch {
    throw createError({ statusCode: 400, message: 'Invalid sign-in request.' })
  }
  const parsed = loginSchema.safeParse(body)
  if (!parsed.success)
    throw createError({ statusCode: 400, message: 'Enter a valid email and password.' })
  try {
    const result = await authBackend(event).login(parsed.data)
    writeSession(event, result.accessToken, result.expiresIn)
    return { user: result.user }
  } catch (error) {
    authError(error)
  }
})
