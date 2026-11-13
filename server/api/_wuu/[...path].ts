import { randomUUID } from 'node:crypto'
import { upstreamApi, upstreamSuffix } from '../../utils/upstream-api'
import type { H3Event } from 'h3'
import { originIsSafe, upstreamPath, subscriptionRequestAllowed } from '../../utils/wuu-gateway'
import { assertSameOrigin, currentSession, ensureFreshSession } from '../../utils/wuu-session'
import { z } from 'zod'
import { alertTransitionAllowed, alertTransitionBody } from '../../utils/alert-transition'
import { fleetRegistrationAllowed, fleetRegistrationBody } from '../../utils/fleet-registration'
import { fleetManagementAllowed, fleetManagementBody } from '../../utils/fleet-management'
import { geofenceWriteAllowed, geofenceWriteBody } from '../../utils/geofence-write'
import { ruleRequestAllowed, ruleRequestBody } from '../../utils/geofence-rule'
import { teamRequestAllowed, teamRequestBody, teamQueryAllowed } from '../../utils/team-request'

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
  const session = await ensureFreshSession(event, currentSession(event))

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
  const target = upstreamPath(getRouterParam(event, 'path'))
  const subscription = target !== null && upstreamSuffix(target, upstreamApi.subscriptions) !== null
  const alertTransition = !!target && alertTransitionAllowed(method, target)
  const fleetRegistration = !!target && fleetRegistrationAllowed(method, target)
  const fleetManagement = !!target && fleetManagementAllowed(method, target)
  const geofenceWrite = !!target && geofenceWriteAllowed(method, target)
  const ruleRequest = !!target && ruleRequestAllowed(method, target)
  const ruleWrite = ruleRequest && method !== 'GET'
  const team =
    target !== null &&
    target !== upstreamApi.access &&
    upstreamSuffix(target, upstreamApi.team) !== null
  const teamWrite = team && teamRequestAllowed(method, target!) && method !== 'GET'
  if (
    !methods.has(method) ||
    (subscription
      ? !subscriptionRequestAllowed(method, target!)
      : team
        ? !teamRequestAllowed(method, target!)
        : method !== 'GET' &&
          !alertTransition &&
          !fleetRegistration &&
          !fleetManagement &&
          !geofenceWrite &&
          !ruleWrite)
  )
    return jsonError(event, 405, 'This request method is not allowed.', requestId)

  if (!target) return jsonError(event, 404, 'Unknown API resource.', requestId)
  if (method !== 'GET') assertSameOrigin(event)

  const query = getQuery(event)
  if (team && (teamWrite ? Object.keys(query).length > 0 : !teamQueryAllowed(target!, query)))
    return jsonError(event, 400, 'Invalid team query parameters.', requestId)
  if (alertTransition && Object.keys(query).length)
    return jsonError(event, 400, 'Alert transitions do not accept query parameters.', requestId)
  if (fleetRegistration && Object.keys(query).length)
    return jsonError(event, 400, 'Fleet registration does not accept query parameters.', requestId)
  if (fleetManagement && Object.keys(query).length)
    return jsonError(event, 400, 'Fleet updates do not accept query parameters.', requestId)
  if (geofenceWrite && Object.keys(query).length)
    return jsonError(event, 400, 'Geofence writes do not accept query parameters.', requestId)
  if (ruleWrite && Object.keys(query).length)
    return jsonError(event, 400, 'Rule writes do not accept query parameters.', requestId)
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
  headers.authorization = `Bearer ${session.accessToken}`

  let body: string | undefined
  if (!['GET', 'HEAD'].includes(method)) {
    if (!contentType?.toLowerCase().startsWith('application/json'))
      return jsonError(event, 415, 'A JSON request body is required.', requestId)
    const chunks: Buffer[] = []
    let size = 0
    // Bound memory even when Content-Length is absent or untrusted. Do not
    // destroy the socket on iterator exit: the caller must receive the 413.
    for await (const chunk of event.node.req.iterator({ destroyOnReturn: false })) {
      const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
      size += bytes.length
      if (size > (geofenceWrite || ruleWrite ? 16384 : 4096)) {
        event.node.req.resume()
        return jsonError(event, 413, 'Request body is too large.', requestId)
      }
      chunks.push(bytes)
    }
    body = Buffer.concat(chunks).toString('utf8')
    try {
      const schema =
        target === upstreamApi.subscriptions
          ? z
              .object({
                plan_id: z.string().uuid(),
                version: z.number().int().positive().max(Number.MAX_SAFE_INTEGER),
                operation_key: z
                  .string()
                  .min(1)
                  .max(200)
                  .refine((key) => key.trim().length > 0),
              })
              .strict()
          : z.object({}).strict()
      const parsed = JSON.parse(body || '{}')
      body = JSON.stringify(
        teamWrite
          ? teamRequestBody(method, target, parsed)
          : fleetManagement
            ? fleetManagementBody(target, parsed)
            : fleetRegistration
              ? fleetRegistrationBody(target, parsed)
              : alertTransition
                ? alertTransitionBody(target, parsed)
                : geofenceWrite
                  ? geofenceWriteBody(target, parsed)
                  : ruleWrite
                    ? ruleRequestBody(target, parsed)
                    : schema.parse(parsed),
      )
    } catch {
      return jsonError(
        event,
        400,
        teamWrite
          ? 'Invalid team request.'
          : fleetManagement
            ? 'Invalid fleet update request.'
            : fleetRegistration
              ? 'Invalid fleet registration request.'
              : alertTransition
                ? 'Invalid alert transition request.'
                : geofenceWrite
                  ? 'Invalid geofence edit request.'
                  : ruleWrite
                    ? 'Invalid geofence rule request.'
                    : 'Invalid subscription request.',
        requestId,
      )
    }
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
