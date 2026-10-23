// Test-only contract server. Never used by the app or deployed as a backend.
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
const sessions = new Map()
const page = (content) => ({
  content,
  total_elements: content.length,
  total_pages: 1,
  page_number: 0,
  page_size: 100,
  first: true,
  last: true,
  empty: !content.length,
})
createServer(async (request, response) => {
  const send = (status, body) => {
    response.writeHead(status, { 'content-type': 'application/json' })
    response.end(body === undefined ? undefined : JSON.stringify(body))
  }
  const path = new URL(request.url, 'http://localhost').pathname
  if (path === '/health') return send(200, { testOnly: true })
  let body = ''
  for await (const chunk of request) body += chunk
  if (path === '/api/v1/auth/login') {
    const input = JSON.parse(body)
    if (input.email === 'limited@example.com') return send(429, { code: 'RATE_LIMITED' })
    if (input.password !== 'test-only-password') return send(401, { code: 'INVALID_CREDENTIALS' })
    const token = `private-access-${randomUUID()}`
    sessions.set(token, input.email)
    return send(200, {
      access_token: token,
      refresh_token: 'private-refresh-never-forward',
      expires_in: 900,
      token_type: 'Bearer',
    })
  }
  const token = request.headers.authorization?.replace('Bearer ', '')
  const email = sessions.get(token)
  if (!email) return send(401, { code: 'UNAUTHENTICATED' })
  if (path === '/api/v1/auth/logout') {
    for (const [key, value] of sessions) if (value === email) sessions.delete(key)
    return send(204)
  }
  if (path === '/api/v1/users/me')
    return send(200, {
      id: email === 'forbidden@example.com' ? 8 : 7,
      email,
      first_name: 'Fleet',
      last_name: 'Operator',
      organization_id: 12,
      roles: ['SUPER_ADMIN'],
      enabled: true,
      locked: false,
    })
  if (path === '/api/v1/vehicles') {
    if (email === 'forbidden@example.com') return send(403, { code: 'FORBIDDEN' })
    return send(
      200,
      page([
        {
          vehicle_id: 'live-1',
          license_plate: 'WUU LIVE 01',
          model: 'Delivery van',
          fuel_level: 54,
          current_location: null,
        },
      ]),
    )
  }
  if (path === '/api/v1/trips' && email === 'fleet-only@example.com')
    return send(403, { code: 'FORBIDDEN' })
  if (['/api/v1/trips', '/alerts', '/alerts/geofences'].includes(path)) return send(200, page([]))
  return send(404, { code: 'NOT_FOUND' })
}).listen(18080, '127.0.0.1')
