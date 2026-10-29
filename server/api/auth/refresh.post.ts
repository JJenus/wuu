import {
  assertSameOrigin,
  authBackend,
  authError,
  clearWuuSession,
  currentSession,
  noStore,
  refreshSession,
} from '../../utils/wuu-session'
import { AuthFailure } from '../../utils/wuu-auth'

export default defineEventHandler(async (event) => {
  noStore(event)
  assertSameOrigin(event)
  let session = currentSession(event)
  try {
    // A retried request whose successful response was lost should not rotate again.
    if (
      session.expiresAt <= Date.now() ||
      (!event.context.wuuSessionRecovered && session.expiresAt <= Date.now() + 5 * 60_000)
    )
      session = await refreshSession(event, session)
    return {
      user: await authBackend(event).me(session.accessToken),
      expires_at: session.expiresAt,
    }
  } catch (error) {
    if (error instanceof AuthFailure && [401, 403].includes(error.status)) clearWuuSession(event)
    authError(error)
  }
})
