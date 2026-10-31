import { ApiError } from '../domain/errors'

// A bounded collection must never masquerade as a complete fleet or export.
export function finalListPage(
  response: { content: readonly unknown[]; last: boolean },
  page: number,
  maxPages: number,
): boolean {
  if (response.last) return true
  if (!response.content.length || page + 1 >= maxPages)
    throw new ApiError({
      kind: 'contract',
      message: 'This list is incomplete. Narrow the request or contact your administrator.',
    })
  return false
}
