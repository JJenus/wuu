// Test-only contract server. Never used by the app or deployed as a backend.
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
const sessions = new Map()
const enrollments = new Map()
const planId = '00000000-0000-4000-8000-000000000001'
const accounts = new Map([
  ['operator@example.com', 'Fleet Operator'],
  ['admin@example.com', 'Local Administrator'],
])
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
  if (path === '/__test/accounts' && request.method === 'POST') {
    const account = JSON.parse(body)
    accounts.set(account.email, account.display_name)
    return send(201, { testOnly: true })
  }
  if (path === '/api/v1/auth/local-accounts')
    return send(200, {
      accounts: [...accounts].map(([email, display_name]) => ({ email, display_name })),
      next_page: null,
    })
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
  if (path.startsWith('/api/v1/subscriptions')) {
    if (email === 'subscription-denied@example.com') return send(403, { code: 'FORBIDDEN' })
    if (path === '/api/v1/subscriptions/catalog' && request.method === 'GET')
      return send(200, [
        {
          plan_id: planId,
          version: 1,
          model: 'USAGE_TOKEN',
          cadence: null,
          meter: 'locations',
          usage_limit: 100,
          price: { amount: 2.5, currency: 'EUR' },
        },
      ])
    const owned = enrollments.get(email) ?? []
    enrollments.set(email, owned)
    if (path === '/api/v1/subscriptions' && request.method === 'GET') return send(200, owned)
    if (path === '/api/v1/subscriptions' && request.method === 'POST') {
      const input = JSON.parse(body)
      const previous = owned.find((item) => item.operation_key === input.operation_key)
      if (previous) return send(200, previous)
      const item = {
        id: randomUUID(),
        plan_id: input.plan_id,
        version: input.version,
        status: 'PENDING',
        remaining_units: 0,
        operation_key: input.operation_key,
      }
      owned.push(item)
      return send(201, item)
    }
    if (path.endsWith('/cancel') && request.method === 'POST') {
      const item = owned.find((item) => path === `/api/v1/subscriptions/${item.id}/cancel`)
      if (!item) return send(404, { code: 'NOT_FOUND' })
      item.status = 'CANCELED'
      item.remaining_units = 0
      return send(200, item)
    }
    return send(404, { code: 'NOT_FOUND' })
  }
  if (path === '/api/v1/auth/logout') {
    if (email === 'logout-failure@example.com') return send(503, { code: 'UNAVAILABLE' })
    for (const [key, value] of sessions) if (value === email) sessions.delete(key)
    return send(204)
  }
  if (path === '/api/v1/users/me' && email === 'unavailable@example.com')
    return send(503, { code: 'UNAVAILABLE' })
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
