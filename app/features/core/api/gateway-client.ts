import { z } from 'zod'
import { responseApiError, toApiError, ApiError, type FieldIssue } from '../domain/errors'
import { normalizeWireKeys } from './wire-format'

export interface GatewayRequestOptions {
  signal?: AbortSignal
  query?: Record<string, string | number | boolean | undefined | null>
  body?: unknown
  headers?: Record<string, string>
  timeoutMs?: number
}

export interface GatewayClient {
  request<T>(
    method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
    path: string,
    schema: z.ZodType<T>,
    options?: GatewayRequestOptions,
  ): Promise<T>
}

export type GatewayFetch = typeof globalThis.fetch

export interface GatewayClientOptions {
  onUnauthorized?: () => void
  baseUrl?: string
  fetchImpl?: GatewayFetch
  timeoutMs?: number
}

function queryString(query: GatewayRequestOptions['query']): string {
  if (!query) return ''
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query))
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  const text = params.toString()
  return text ? `?${text}` : ''
}

function combinedSignal(signal: AbortSignal | undefined, timeoutMs: number) {
  const controller = new AbortController()
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort(new DOMException('Timeout', 'TimeoutError'))
  }, timeoutMs)
  const abort = () => controller.abort(signal?.reason)
  if (signal?.aborted) abort()
  else signal?.addEventListener('abort', abort, { once: true })
  return {
    signal: controller.signal,
    didTimeOut: () => timedOut,
    cleanup() {
      clearTimeout(timer)
      signal?.removeEventListener('abort', abort)
    },
  }
}

async function responseBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined
  const type = response.headers.get('content-type') ?? ''
  if (type.includes('application/json')) {
    try {
      return await response.json()
    } catch {
      return undefined
    }
  }
  const text = await response.text()
  return text || undefined
}

function validationFields(error: z.ZodError): FieldIssue[] {
  return error.issues.map((issue) => ({
    path: issue.path.map(String).join('.'),
    message: issue.message,
  }))
}

export function createGatewayClient(options: GatewayClientOptions = {}): GatewayClient {
  const baseUrl = options.baseUrl ?? '/api/_wuu'
  const fetchImpl = options.fetchImpl ?? globalThis.fetch
  const defaultTimeout = options.timeoutMs ?? 15_000

  return {
    async request(method, path, schema, requestOptions = {}) {
      const normalized = path.startsWith('/') ? path : `/${path}`
      if (normalized.includes('..'))
        throw new ApiError({ kind: 'validation', message: 'Invalid request path.' })
      const request = combinedSignal(
        requestOptions.signal,
        requestOptions.timeoutMs ?? defaultTimeout,
      )
      try {
        const response = await fetchImpl(
          `${baseUrl}${normalized}${queryString(requestOptions.query)}`,
          {
            method,
            credentials: 'same-origin',
            headers: {
              accept: 'application/json',
              ...(requestOptions.body === undefined ? {} : { 'content-type': 'application/json' }),
              ...requestOptions.headers,
            },
            body:
              requestOptions.body === undefined ? undefined : JSON.stringify(requestOptions.body),
            signal: request.signal,
          },
        )
        const body = await responseBody(response)
        const requestId = response.headers.get('x-request-id') ?? undefined
        if (!response.ok) {
          if (response.status === 401) options.onUnauthorized?.()
          throw responseApiError(response.status, body, requestId)
        }
        try {
          return schema.parse(normalizeWireKeys(body))
        } catch (error) {
          if (error instanceof Error)
            throw new ApiError({
              kind: 'contract',
              status: response.status,
              requestId,
              message: 'The service returned data this app cannot safely read.',
              fields: error instanceof z.ZodError ? validationFields(error) : [],
              cause: error,
            })
          throw error
        }
      } catch (error) {
        if (error instanceof ApiError) throw error
        if (request.signal.aborted) {
          if (request.didTimeOut())
            throw new ApiError({
              kind: 'timeout',
              message: 'The request took too long. Please try again.',
              cause: error,
            })
          throw error
        }
        throw toApiError(error)
      } finally {
        request.cleanup()
      }
    },
  }
}
