import {
  assertSameOrigin,
  authBackend,
  authError,
  clearWuuSession,
  currentSession,
  noStore,
} from '../../utils/wuu-session'
import { AuthFailure } from '../../utils/wuu-auth'

export default defineEventHandler(async (event) => {
  noStore(event)
  assertSameOrigin(event)
  let token: string | undefined
  try {
    token = currentSession(event).accessToken
  } catch {
    /* Already signed out locally. */
  }
  clearWuuSession(event)
  if (token) {
    try {
      await authBackend(event).logout(token)
    } catch (error) {
      if (!(error instanceof AuthFailure && error.status === 401)) authError(error)
    }
  }
  setResponseStatus(event, 204)
  return null
})
