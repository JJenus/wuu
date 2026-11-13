// Test-only fleet contract fixture. Not a replacement for Java integration checks.
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'

const sessions = new Map()
const fleets = new Map()
const page = (content) => ({ content, last: true, empty: content.length === 0 })
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
  const writer = email.startsWith('fleet-writer-')
  if (path === '/api/v1/users/me')
    return send(200, {
      id: 7,
      email,
      first_name: 'Fleet',
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
      permissions: [
        'vehicles.read',
        'devices.read',
        ...(writer ? ['vehicles.write', 'vehicles.assign_device'] : []),
      ],
      access_expires_at: '2099-01-01T00:00:00Z',
    })
  if (!fleets.has(email)) fleets.set(email, { vehicles: new Map(), trackers: new Map() })
  const fleet = fleets.get(email)
  if (path === '/api/v1/vehicles' && request.method === 'POST') {
    if (!writer) return send(403, {})
    if (fleet.vehicles.has(body.vehicle_id))
      return send(409, { message: 'Vehicle ID already exists' })
    fleet.vehicles.set(body.vehicle_id, { ...body, organization_id: 12, current_location: null })
    return send(201, fleet.vehicles.get(body.vehicle_id))
  }
  if (path === '/api/v1/trackers' && request.method === 'POST') {
    if (!writer) return send(403, {})
    if (!fleet.vehicles.has(body.vehicle_id)) return send(400, {})
    if (
      fleet.trackers.has(body.tracker_id) ||
      [...fleet.trackers.values()].some((tracker) => tracker.device_id === body.device_id)
    )
      return send(409, { message: 'Device ID already registered' })
    fleet.trackers.set(body.tracker_id, body)
    return send(201, body)
  }
  if (path === '/api/v1/vehicles') return send(200, page([...fleet.vehicles.values()]))
  if (path === '/api/v1/trackers') return send(200, page([...fleet.trackers.values()]))
  const profile = path.match(/^\/api\/v1\/trackers\/([^/]+)\/profile$/)
  const move = path.match(/^\/api\/v1\/trackers\/([^/]+)\/reassign$/)
  if (profile || move) {
    if (!writer) return send(403, {})
    const tracker = fleet.trackers.get(decodeURIComponent((profile || move)[1]))
    if (!tracker) return send(404, {})
    if (profile) Object.assign(tracker, body)
    else {
      if (!fleet.vehicles.has(body.target_vehicle_id)) return send(404, {})
      if (tracker.vehicle_id !== body.source_vehicle_id) return send(409, { message: 'Assignment changed' })
      tracker.vehicle_id = body.target_vehicle_id
    }
    return send(200, tracker)
  }
  if (path.startsWith('/api/v1/vehicles/')) {
    const vehicle = fleet.vehicles.get(decodeURIComponent(path.slice('/api/v1/vehicles/'.length)))
    if (request.method === 'PUT') {
      if (!writer) return send(403, {})
      if (vehicle) Object.assign(vehicle, body)
    }
    return vehicle ? send(200, vehicle) : send(404, {})
  }
  if (['/api/v1/trips', '/api/v1/alerts', '/api/v1/alerts/geofences'].includes(path))
    return send(200, page([]))
  return send(404, {})
}).listen(18081, '127.0.0.1')
