import { z } from 'zod'
import {
  localAccountSchema,
  type LocalAccount,
} from '../../app/features/auth/domain/local-accounts'
import { AuthFailure } from './wuu-auth'

const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]'])
function loopbackOrigin(origin: string) {
  try {
    const url = new URL(origin)
    return (
      url.origin === origin &&
      ['http:', 'https:'].includes(url.protocol) &&
      loopbackHosts.has(url.hostname) &&
      !url.username &&
      !url.password
    )
  } catch {
    return false
  }
}
export function localPickerAllowed(input: {
  enabled: string
  host: string | undefined
  peer: string | undefined
  origin: string
}) {
  return (
    input.enabled === 'true' &&
    !!input.host &&
    loopbackOrigin(`http://${input.host}`) &&
    ['127.0.0.1', '::1', '::ffff:127.0.0.1'].includes(input.peer || '') &&
    loopbackOrigin(input.origin)
  )
}

const pageSchema = z.object({
  accounts: z.array(localAccountSchema).max(100),
  next_page: z.number().int().nonnegative().nullable(),
})
export async function loadLocalAccounts(origin: string, fetcher: typeof fetch = globalThis.fetch) {
  if (!loopbackOrigin(origin))
    throw new AuthFailure(404, 'The local account picker is unavailable.')
  const accounts: LocalAccount[] = []
  try {
    for (let page = 0; page < 50; page++) {
      const response = await fetcher(`${origin}/api/v1/auth/local-accounts?page=${page}`, {
        method: 'GET',
        headers: { accept: 'application/json' },
        cache: 'no-store',
        redirect: 'manual',
        signal: AbortSignal.timeout(15000),
      })
      if (!response.ok) throw new Error('Account directory unavailable')
      const result = pageSchema.parse(await response.json())
      accounts.push(...result.accounts)
      if (result.next_page === null) return { accounts }
      if (result.next_page !== page + 1) throw new Error('Invalid account page')
    }
    throw new Error('Account directory exceeds the supported limit')
  } catch {
    throw new AuthFailure(
      502,
      'Local accounts could not be loaded. You can still enter your email.',
    )
  }
}
