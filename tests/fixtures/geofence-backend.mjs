// Test-only contract fixture, not the application backend.
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
const sessions = new Map()
const geofences = new Map()
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
  if (path === '/alerts/geofences' && request.method === 'GET')
    return send(200, page([geofences.get(email)]))
  if (path === '/alerts/geofences/9' && request.method === 'GET')
    return send(200, geofences.get(email))
  if (
    (path === '/alerts/geofences' && request.method === 'POST') ||
    (path === '/alerts/geofences/9' && request.method === 'PUT')
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
  if (['/api/v1/trips', '/alerts'].includes(path)) return send(200, page([]))
  return send(404, {})
}).listen(18084, '127.0.0.1')
