import { localAccountsSchema } from '../domain/local-accounts'
import { responseApiError, toApiError } from '../../core/domain/errors'

export async function localAccountsRequest(fetcher: typeof $fetch = $fetch) {
  try {
    return localAccountsSchema.parse(
      await fetcher('/api/auth/accounts', { credentials: 'same-origin', cache: 'no-store' }),
    )
  } catch (error) {
    const failure = error as { statusCode?: number; data?: unknown }
    if (failure.statusCode) throw responseApiError(failure.statusCode, failure.data)
    throw toApiError(error)
  }
}
