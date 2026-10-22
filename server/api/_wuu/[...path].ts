import { randomUUID } from 'node:crypto'
import type { H3Event } from 'h3'
import { originIsSafe, upstreamPath } from '../../utils/wuu-gateway'

const methods = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE'])

function jsonError(event: H3Event, status: number, message: string, requestId: string) {
  setResponseStatus(event, status)
  setResponseHeader(event, 'content-type', 'application/json; charset=utf-8')
  return { message, requestId }
}

export default defineEventHandler(async (event) => {
  const requestId = randomUUID()
  setResponseHeader(event, 'x-request-id', requestId)
  setResponseHeader(event, 'cache-control', 'no-store')
  setResponseHeader(event, 'x-content-type-options', 'nosniff')

  const config = useRuntimeConfig(event)
  // Support the deployment's WUU_* names at runtime without baking the token into a build.
  const origin = (config.apiOrigin || process.env.WUU_API_ORIGIN || '').trim().replace(/\/$/, '')
  if (!origin)
    return jsonError(event, 503, 'The live API is not configured on this server.', requestId)
  if (!originIsSafe(origin))
    return jsonError(
      event,
      500,
      'The configured live API origin must use HTTPS, except for local development.',
      requestId,
    )

  const method = (event.method || 'GET').toUpperCase()
  if (!methods.has(method))
    return jsonError(event, 405, 'This request method is not allowed.', requestId)

  const target = upstreamPath(getRouterParam(event, 'path'))
  if (!target) return jsonError(event, 404, 'Unknown API resource.', requestId)

  const query = getQuery(event)
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (Array.isArray(value)) {
      for (const item of value)
        if (item !== undefined && item !== null) params.append(key, String(item))
    } else if (value !== undefined && value !== null) {
      params.append(key, String(value))
    }
  }
  const search = params.toString()
  const url = `${origin}${target}${search ? `?${search}` : ''}`

  const headers: Record<string, string> = {
    accept: 'application/json',
    'x-request-id': requestId,
  }
  const contentType = getHeader(event, 'content-type')
  if (contentType) headers['content-type'] = contentType
  const token =
    getCookie(event, 'wuu_access_token') ||
    config.apiBearerToken ||
    process.env.WUU_API_BEARER_TOKEN ||
    ''
  if (token) headers.authorization = `Bearer ${token}`

  let body: string | undefined
  if (!['GET', 'HEAD'].includes(method)) {
    const raw = await readRawBody(event, false)
    body = raw ? raw.toString() : undefined
  }

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(new Error('Upstream timeout')), 15_000)
  try {
    const response = await fetch(url, {
      method,
      headers,
      body,
      signal: controller.signal,
      redirect: 'manual',
    })
    setResponseStatus(event, response.status)
    const responseType = response.headers.get('content-type')
    if (responseType) setResponseHeader(event, 'content-type', responseType)
    const upstreamRequestId = response.headers.get('x-request-id')
    if (upstreamRequestId) setResponseHeader(event, 'x-request-id', upstreamRequestId)
    if (response.status === 204) return null
    const text = await response.text()
    return text || null
  } catch {
    const timedOut = controller.signal.aborted
    return jsonError(
      event,
      timedOut ? 504 : 502,
      timedOut
        ? 'The live API did not respond in time.'
        : 'The live API could not be reached from this server.',
      requestId,
    )
  } finally {
    clearTimeout(timeout)
  }
})
