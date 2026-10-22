import { describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { createGatewayClient, type GatewayFetch } from '../../app/features/core/api/gateway-client'
import {
  ApiError,
  capabilityUnavailable,
  errorMessage,
  isRetryable,
  responseApiError,
  toApiError,
} from '../../app/features/core/domain/errors'
import {
  isoInstantSchema,
  pagedResponseSchema,
  springPageSchema,
  toPage,
} from '../../app/features/core/api/contracts'
import {
  formatDateTime,
  formatDuration,
  formatRelativeTime,
  isStale,
  parseInstant,
} from '../../app/features/core/domain/time'

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  })
}

describe('API errors', () => {
  it('maps HTTP status and safe field issues without exposing untrusted payloads', () => {
    const error = responseApiError(
      422,
      { message: 'Check your input.', errors: { licensePlate: ['Already registered.'] } },
      'req-42',
    )
    expect(error).toBeInstanceOf(ApiError)
    expect(error.kind).toBe('validation')
    expect(error.status).toBe(422)
    expect(error.requestId).toBe('req-42')
    expect(error.message).toBe('Check your input.')
    expect(error.fields).toEqual([{ path: 'licensePlate', message: 'Already registered.' }])
    expect(error.retryable).toBe(false)
  })

  it('classifies retryable failures and unavailable capabilities', () => {
    expect(responseApiError(503, {}).kind).toBe('server')
    expect(responseApiError(429, {}).retryable).toBe(true)
    expect(responseApiError(401, {}).retryable).toBe(false)
    expect(isRetryable(capabilityUnavailable())).toBe(false)
    expect(capabilityUnavailable()).toMatchObject({ kind: 'unavailable' })
  })

  it('normalizes transport and wrapped failures without leaking arbitrary errors', () => {
    expect(toApiError(new TypeError('fetch failed')).kind).toBe('network')
    expect(toApiError({ status: 404, data: { message: 'Missing vehicle.' } })).toMatchObject({
      kind: 'not-found',
      message: 'Missing vehicle.',
    })
    expect(toApiError(new Error('unexpected')).message).toBe('unexpected')
    expect(errorMessage(new Error('readable'))).toBe('readable')
    expect(errorMessage('not an error', 'Try again.')).toBe('Try again.')
  })
})

describe('gateway client', () => {
  it('sends filtered queries and parses a typed response', async () => {
    const mockFetch = vi.fn(async () => jsonResponse({ value: 7 }))
    const fetchImpl = mockFetch as unknown as GatewayFetch
    const client = createGatewayClient({
      baseUrl: '/api/_wuu',
      fetchImpl,
      timeoutMs: 1_000,
    })
    const result = await client.request('GET', '/vehicles', z.object({ value: z.number() }), {
      query: { page: 0, size: 20, empty: '', missing: undefined, flag: false },
      headers: { 'x-test': 'yes' },
    })
    expect(result).toEqual({ value: 7 })
    const [url, init] = mockFetch.mock.calls[0]!
    expect(url).toBe('/api/_wuu/vehicles?page=0&size=20&flag=false')
    expect(init).toMatchObject({
      method: 'GET',
      credentials: 'same-origin',
      headers: { accept: 'application/json', 'x-test': 'yes' },
    })
    expect(init?.body).toBeUndefined()
  })

  it('maps a malformed live payload to a contract error with field paths', async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse({ value: 'not-a-number' }),
    ) as unknown as GatewayFetch
    const client = createGatewayClient({ fetchImpl, timeoutMs: 1_000 })
    await expect(
      client.request('GET', '/vehicles', z.object({ value: z.number() })),
    ).rejects.toMatchObject({
      kind: 'contract',
      fields: [{ path: 'value' }],
    })
  })

  it('maps upstream errors and keeps the request reference', async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse({ message: 'No access to this fleet.' }, 403, { 'x-request-id': 'up-9' }),
    ) as unknown as GatewayFetch
    const client = createGatewayClient({ fetchImpl, timeoutMs: 1_000 })
    await expect(client.request('GET', '/vehicles', z.any())).rejects.toMatchObject({
      kind: 'forbidden',
      status: 403,
      requestId: 'up-9',
      message: 'No access to this fleet.',
    })
  })

  it('accepts an empty 204 response only when the caller expects void', async () => {
    const fetchImpl = vi.fn(
      async () => new Response(null, { status: 204 }),
    ) as unknown as GatewayFetch
    const client = createGatewayClient({ fetchImpl, timeoutMs: 1_000 })
    await expect(client.request('DELETE', '/vehicles/1', z.void())).resolves.toBeUndefined()
    await expect(
      client.request('DELETE', '/vehicles/1', z.object({ id: z.string() })),
    ).rejects.toMatchObject({ kind: 'contract' })
  })

  it('classifies its own timeout and does not turn caller cancellation into an API error', async () => {
    const hangingFetch = vi.fn(
      (_input: RequestInfo | URL, init?: RequestInit) =>
        new Promise<Response>((_resolve, reject) => {
          const signal = init?.signal
          signal?.addEventListener('abort', () =>
            reject(signal.reason ?? new DOMException('Aborted', 'AbortError')),
          )
        }),
    ) as unknown as GatewayFetch
    const timeoutClient = createGatewayClient({ fetchImpl: hangingFetch, timeoutMs: 5 })
    await expect(timeoutClient.request('GET', '/vehicles', z.any())).rejects.toMatchObject({
      kind: 'timeout',
    })

    const controller = new AbortController()
    const cancelClient = createGatewayClient({ fetchImpl: hangingFetch, timeoutMs: 5_000 })
    const pending = cancelClient.request('GET', '/vehicles', z.any(), {
      signal: controller.signal,
    })
    controller.abort(new DOMException('Cancelled', 'AbortError'))
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('rejects traversal paths before calling fetch', async () => {
    const fetchImpl = vi.fn() as unknown as GatewayFetch
    const client = createGatewayClient({ fetchImpl })
    await expect(client.request('GET', '/../secrets', z.any())).rejects.toMatchObject({
      kind: 'validation',
    })
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})

describe('contracts and page normalization', () => {
  const itemSchema = z.object({ id: z.string() })

  it('requires an explicit timezone on API instants', () => {
    expect(isoInstantSchema.safeParse('2026-10-22T12:00:00Z').success).toBe(true)
    expect(isoInstantSchema.safeParse('2026-10-22T12:00:00+00:00').success).toBe(true)
    expect(isoInstantSchema.safeParse('2026-10-22T12:00:00').success).toBe(false)
  })

  it('defaults malformed page metadata while still rejecting malformed records', () => {
    const page = pagedResponseSchema(itemSchema).parse({
      content: [{ id: 'A' }],
      pageNumber: -1,
      pageSize: 0,
      totalElements: -2,
      totalPages: -3,
      first: 'nope',
      last: 'nope',
      empty: 'nope',
    })
    expect(page).toMatchObject({ pageNumber: 0, pageSize: 20, totalElements: 0, totalPages: 0 })
    expect(pagedResponseSchema(itemSchema).safeParse({ content: [{ id: 3 }] }).success).toBe(false)
  })

  it('normalizes Spring and custom page envelopes', () => {
    const spring = springPageSchema(itemSchema).parse({
      content: [{ id: 'A' }],
      number: 2,
      size: 10,
      totalElements: 21,
      totalPages: 3,
      first: false,
      last: true,
      empty: false,
    })
    expect(toPage(spring)).toEqual({
      items: [{ id: 'A' }],
      page: 2,
      size: 10,
      total: 21,
      pages: 3,
      first: false,
      last: true,
    })
    expect(
      toPage({
        content: [],
        pageNumber: 0,
        pageSize: 20,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      }),
    ).toMatchObject({ page: 0, size: 20, total: 0 })
  })
})

describe('time presentation', () => {
  it('handles invalid and missing instants predictably', () => {
    expect(parseInstant('not-a-date')).toBeNull()
    expect(formatRelativeTime(null)).toBe('No update recorded')
    expect(formatDateTime('not-a-date')).toBe('Not recorded')
    expect(formatDuration('not-a-date')).toBe('Not recorded')
    expect(isStale(null)).toBe(true)
  })

  it('renders instants deterministically in UTC regardless of host timezone', () => {
    expect(formatDateTime('2026-10-21T17:42:00.000Z')).toBe('Oct 21, 2026, 5:42 PM UTC')
    expect(formatDateTime('2026-10-21T17:48:00.000Z')).toBe('Oct 21, 2026, 5:48 PM UTC')
  })

  it('formats elapsed preview durations from explicit boundaries', () => {
    expect(formatDuration('2026-10-22T10:00:00Z', '2026-10-22T10:00:30Z')).toBe('30 sec')
    expect(formatDuration('2026-10-22T10:00:00Z', '2026-10-22T10:05:30Z')).toBe('5 min')
    expect(formatDuration('2026-10-22T10:00:00Z', '2026-10-22T12:15:00Z')).toBe('2 hr 15 min')
  })
})
