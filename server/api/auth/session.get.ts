import {
  authBackend,
  authError,
  clearWuuSession,
  currentSession,
  noStore,
} from '../../utils/wuu-session'
import { AuthFailure } from '../../utils/wuu-auth'

export default defineEventHandler(async (event) => {
  noStore(event)
  const session = currentSession(event)
  try {
    return { user: await authBackend(event).me(session.accessToken) }
  } catch (error) {
    if (error instanceof AuthFailure && [401, 403].includes(error.status)) clearWuuSession(event)
    authError(error)
  }
})
