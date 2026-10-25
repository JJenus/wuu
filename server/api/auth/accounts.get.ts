import { authConfig, authError, noStore } from '../../utils/wuu-session'
import { loadLocalAccounts, localPickerAllowed } from '../../utils/wuu-local-accounts'

export default defineEventHandler(async (event) => {
  noStore(event)
  const config = useRuntimeConfig(event)
  const origin = authConfig(event).origin
  if (
    !localPickerAllowed({
      enabled: String(config.localAccountPicker || process.env.WUU_LOCAL_ACCOUNT_PICKER || ''),
      host: getHeader(event, 'host'),
      peer: event.node.req.socket.remoteAddress,
      origin,
    })
  )
    return { enabled: false, accounts: [] }
  try {
    return { enabled: true, ...(await loadLocalAccounts(origin)) }
  } catch (error) {
    authError(error)
  }
})
