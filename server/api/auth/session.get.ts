import {
  authBackend,
  authError,
  clearWuuSession,
  currentSession,
  ensureFreshSession,
  noStore,
} from '../../utils/wuu-session'
import { AuthFailure } from '../../utils/wuu-auth'

export default defineEventHandler(async (event) => {
  noStore(event)
  let session = currentSession(event)
  try {
    session = await ensureFreshSession(event, session, 60_000)
    return {
      user: await authBackend(event).me(session.accessToken),
      expires_at: session.expiresAt,
    }
  } catch (error) {
    if (error instanceof AuthFailure && [401, 403].includes(error.status)) clearWuuSession(event)
    authError(error)
  }
})
