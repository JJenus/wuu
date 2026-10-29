import { loginSchema } from '../../../app/features/auth/domain/auth'
import {
  assertSameOrigin,
  authBackend,
  authConfig,
  authError,
  noStore,
  writeSession,
  createLoginSession,
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
  const chunks: Buffer[] = []
  let bytes = 0
  for await (const chunk of event.node.req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
    bytes += buffer.length
    if (bytes > 4096)
      throw createError({ statusCode: 413, message: 'Sign-in request is too large.' })
    chunks.push(buffer)
  }
  const raw = Buffer.concat(chunks).toString('utf8')
  if (!raw) throw createError({ statusCode: 400, message: 'Invalid sign-in request.' })
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
    const session = createLoginSession(result.tokens)
    writeSession(event, session)
    return { user: result.user, expires_at: session.expiresAt }
  } catch (error) {
    authError(error)
  }
})
