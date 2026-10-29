import {
  assertSameOrigin,
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
  assertSameOrigin(event)
  let session
  try {
    session = currentSession(event)
  } catch {
    /* Already signed out locally. */
  }
  try {
    if (session) {
      const fresh = await ensureFreshSession(event, session)
      await authBackend(event).logout(fresh.accessToken)
    }
  } catch (error) {
    if (!(error instanceof AuthFailure && error.status === 401)) authError(error)
  } finally {
    clearWuuSession(event)
  }
  setResponseStatus(event, 204)
  return null
})
