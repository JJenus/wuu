export type ApiErrorKind =
  | 'network'
  | 'timeout'
  | 'unauthorized'
  | 'forbidden'
  | 'not-found'
  | 'conflict'
  | 'validation'
  | 'contract'
  | 'rate-limited'
  | 'server'
  | 'unavailable'

export interface FieldIssue {
  path: string
  message: string
}

interface ApiErrorInput {
  kind: ApiErrorKind
  message: string
  status?: number
  fields?: readonly FieldIssue[]
  requestId?: string
  retryable?: boolean
  cause?: unknown
}

const defaultMessages: Record<ApiErrorKind, string> = {
  network: 'The service could not be reached. Check the connection and try again.',
  timeout: 'The request took too long. Please try again.',
  unauthorized: 'Your session has expired. Sign in again.',
  forbidden: 'You do not have permission to perform this action.',
  'not-found': 'The requested record was not found.',
  conflict: 'This record changed while you were working. Refresh and try again.',
  validation: 'Some fields need attention.',
  contract: 'The service returned data this app cannot safely read.',
  'rate-limited': 'Too many requests. Wait a moment and try again.',
  server: 'The service is unavailable right now. Please try again.',
  unavailable: 'This capability is not available in the current environment.',
}

const retryableKinds = new Set<ApiErrorKind>(['network', 'timeout', 'rate-limited', 'server'])

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status?: number
  readonly fields: readonly FieldIssue[]
  readonly requestId?: string
  readonly retryable: boolean

  constructor(input: ApiErrorInput) {
    super(input.message || defaultMessages[input.kind], { cause: input.cause })
    this.name = 'ApiError'
    this.kind = input.kind
    this.status = input.status
    this.fields = input.fields ?? []
    this.requestId = input.requestId
    this.retryable = input.retryable ?? retryableKinds.has(input.kind)
  }
}

export function capabilityUnavailable(message = defaultMessages.unavailable) {
  return new ApiError({ kind: 'unavailable', message })
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export function errorMessage(
  error: unknown,
  fallback = 'Something went wrong. Please try again.',
): string {
  if (isApiError(error)) return error.message
  if (error instanceof Error && error.message.trim()) return error.message
  return fallback
}

export function isRetryable(error: unknown): boolean {
  return isApiError(error) ? error.retryable : false
}

function statusKind(status: number): ApiErrorKind {
  if (status === 401) return 'unauthorized'
  if (status === 403) return 'forbidden'
  if (status === 404) return 'not-found'
  if (status === 409) return 'conflict'
  if (status === 422) return 'validation'
  if (status === 429) return 'rate-limited'
  if (status >= 500) return 'server'
  return status >= 400 ? 'validation' : 'server'
}

function record(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : null
}

function stringValue(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}

function fieldsFrom(value: unknown): FieldIssue[] {
  const body = record(value)
  if (!body) return []
  const source = record(body.errors) ?? record(body.fieldErrors)
  if (source) {
    return Object.entries(source).flatMap(([path, value]) => {
      if (Array.isArray(value))
        return value
          .filter((item): item is string => typeof item === 'string')
          .map((message) => ({ path, message }))
      const message = stringValue(value)
      return message ? [{ path, message }] : []
    })
  }
  if (Array.isArray(body.errors))
    return body.errors.flatMap((item) => {
      const issue = record(item)
      const message = stringValue(issue?.message ?? item)
      return message
        ? [{ path: stringValue(issue?.path) ?? stringValue(issue?.field) ?? '', message }]
        : []
    })
  return []
}

function httpError(status: number, body: unknown, requestId?: string): ApiError {
  const payload = record(body)
  const kind = statusKind(status)
  return new ApiError({
    kind,
    status,
    requestId,
    fields: fieldsFrom(body),
    message:
      stringValue(payload?.message) ??
      stringValue(payload?.detail) ??
      stringValue(payload?.error) ??
      defaultMessages[kind],
  })
}

export function toApiError(error: unknown, fallbackMessage?: string): ApiError {
  if (isApiError(error)) return error
  const candidate = record(error)
  if (candidate) {
    const status = typeof candidate.status === 'number' ? candidate.status : undefined
    const response = record(candidate.response)
    const requestId = stringValue(candidate.requestId) ?? stringValue(response?.requestId)
    if (status) return httpError(status, candidate.data ?? candidate.body, requestId)
    if (candidate.name === 'AbortError')
      return new ApiError({ kind: 'timeout', message: defaultMessages.timeout, cause: error })
  }
  if (error instanceof TypeError)
    return new ApiError({ kind: 'network', message: defaultMessages.network, cause: error })
  return new ApiError({
    kind: 'server',
    message:
      error instanceof Error && error.message
        ? error.message
        : (fallbackMessage ?? defaultMessages.server),
    cause: error,
  })
}

export function responseApiError(status: number, body: unknown, requestId?: string): ApiError {
  return httpError(status, body, requestId)
}
