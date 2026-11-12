// Test-only contract fixture, not the application backend.
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
const sessions = new Map()
const geofences = new Map()
/** @typedef {{rule_key: string, rule_name: string, rule_type: string, enabled: boolean, priority: number, parameters: {geofenceId: string, action: string, vehicleIds: string[]}}} FixtureRule */
// Keyed fixture state genuinely needs dictionaries; payloads have a defined shape.
/** @type {Map<string, Map<string, FixtureRule>>} */
const rules = new Map()
const page = (content) => ({ content, last: true, empty: !content.length })
createServer(async (request, response) => {
  const send = (status, body) => {
    response.writeHead(status, { 'content-type': 'application/json' })
    response.end(JSON.stringify(body))
  }
  const path = new URL(request.url, 'http://localhost').pathname
  if (path === '/health') return send(200, { testOnly: true })
  let raw = ''
  for await (const chunk of request) raw += chunk
  const body = raw ? JSON.parse(raw) : {}
  if (path === '/api/v1/auth/login') {
    if (body.password !== 'test-only-password') return send(401, {})
    const token = randomUUID()
    sessions.set(token, body.email)
    return send(200, {
      access_token: token,
      refresh_token: randomUUID(),
      expires_in: 900,
      refresh_expires_in: 604800,
      token_type: 'Bearer',
    })
  }
  const email = sessions.get(request.headers.authorization?.replace('Bearer ', ''))
  if (!email) return send(401, {})
  const writer = email.startsWith('geo-ui-write-')
  if (path === '/api/v1/users/me')
    return send(200, {
      id: 7,
      email,
      first_name: 'Geo',
      last_name: 'Tester',
      organization_id: 12,
      roles: ['SUPER_ADMIN'],
      enabled: true,
      locked: false,
    })
  if (path === '/api/v1/users/me/access')
    return send(200, {
      user_id: 7,
      organization_id: 12,
      permissions: ['vehicles.read', 'alerts.read', ...(writer ? ['alerts.write'] : [])],
      access_expires_at: '2099-01-01T00:00:00Z',
    })
  if (!rules.has(email)) rules.set(email, new Map())
  const ownedRules = rules.get(email)
  if (path === '/api/v1/alerts/rules' && request.method === 'GET')
    return send(200, page([...ownedRules.values()]))
  if (path === '/api/v1/alerts/rules/templates/geofence' && request.method === 'POST') {
    if (!writer) return send(403, {})
    if (ownedRules.has(body.rule_key)) return send(409, { message: 'Rule key already exists' })
    /** @type {FixtureRule} */
    const saved = {
      rule_key: body.rule_key,
      rule_name: body.rule_name,
      rule_type: 'GEOFENCE',
      enabled: body.enabled,
      priority: body.priority,
      parameters: {
        geofenceId: body.geofence_id,
        action: body.action,
        vehicleIds: body.vehicle_ids,
      },
    }
    ownedRules.set(body.rule_key, saved)
    return send(201, saved)
  }
  const transition = /^\/api\/v1\/alerts\/rules\/([A-Za-z0-9_-]+)\/(enable|disable)$/.exec(path)
  if (transition && request.method === 'PATCH') {
    if (!writer) return send(403, {})
    const saved = ownedRules.get(transition[1])
    if (!saved) return send(404, {})
    saved.enabled = transition[2] === 'enable'
    return send(200, null)
  }
  if (!geofences.has(email))
    geofences.set(email, {
      geofence_id: 9,
      name: 'London depot',
      description: 'Original description',
      shape_type: 'CIRCLE',
      center_latitude: 51.5,
      center_longitude: -0.1,
      radius_meters: 250,
      active: true,
      vehicle_ids: ['VH-1', 'VH-2'],
    })
  if (path === '/api/v1/vehicles')
    return send(
      200,
      page([
        { vehicle_id: 'VH-1', license_plate: 'LONDON-1' },
        { vehicle_id: 'VH-2', license_plate: 'LONDON-2' },
      ]),
    )
  if (path === '/api/v1/alerts/geofences' && request.method === 'GET')
    return send(200, page([geofences.get(email)]))
  if (path === '/api/v1/alerts/geofences/9' && request.method === 'GET')
    return send(200, geofences.get(email))
  if (
    (path === '/api/v1/alerts/geofences' && request.method === 'POST') ||
    (path === '/api/v1/alerts/geofences/9' && request.method === 'PUT')
  ) {
    if (!writer) return send(403, {})
    if (Object.keys(body).some((key) => /[A-Z]/.test(key)))
      return send(400, { message: 'Expected snake_case payload' })
    geofences.set(
      email,
      request.method === 'POST'
        ? { ...body, geofence_id: 9 }
        : { ...geofences.get(email), ...body },
    )
    return send(request.method === 'POST' ? 201 : 200, geofences.get(email))
  }
  if (['/api/v1/trips', '/api/v1/alerts'].includes(path)) return send(200, page([]))
  return send(404, {})
}).listen(18084, '127.0.0.1')
