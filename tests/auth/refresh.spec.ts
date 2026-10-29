import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { sealWuuSession } from '../../server/utils/wuu-auth'

const sessionPassword = 'test-only-session-password-at-least-32-characters'
const origin = 'http://localhost:3001'
function setCookie(response: APIResponse): string {
  return (
    response
      .headersArray()
      .filter((header) => header.name.toLowerCase() === 'set-cookie')
      .map((header) => header.value)
      .join('\n') || ''
  )
}
async function expiredSession(
  request: APIRequestContext,
  email = 'operator@example.com',
): Promise<{ oldCookie: string; aliasCookie: string; refreshToken: string }> {
  const refreshToken = `test-refresh-${randomUUID()}`
  expect(
    (
      await request.post('http://127.0.0.1:18080/__test/refresh-sessions', {
        data: { refresh_token: refreshToken, email },
      })
    ).status(),
  ).toBe(201)
  const session = {
    accessToken: 'expired-private-access-token',
    refreshToken,
    expiresAt: Date.now() - 1_000,
    refreshExpiresAt: Date.now() + 3_600_000,
  }
  return {
    refreshToken,
    oldCookie: `wuu_session=${sealWuuSession(session, sessionPassword)}`,
    aliasCookie: `wuu_session=${sealWuuSession(session, sessionPassword)}`,
  }
}

test('rotates a refresh token once for concurrent requests and recovers a lost response', async ({
  request,
}) => {
  const { oldCookie, aliasCookie, refreshToken } = await expiredSession(request)
  const headers = { cookie: oldCookie, origin }
  const [first, second] = await Promise.all([
    request.post('/api/auth/refresh', { headers: { ...headers, cookie: aliasCookie } }),
    request.post('/api/auth/refresh', { headers }),
  ])
  expect(first.status()).toBe(200)
  expect(second.status()).toBe(200)
  for (const response of [first, second]) {
    const body = await response.json()
    expect(body.user.email).toBe('operator@example.com')
    expect(body.expires_at).toBeGreaterThan(Date.now())
    expect(JSON.stringify(body)).not.toMatch(/refresh_token|access_token|private-access/)
    const cookieHeader = setCookie(response)
    expect(cookieHeader).toContain('wuu_session=')
    expect(cookieHeader).not.toContain(refreshToken)
  }
  const nextCookie = setCookie(first).match(/wuu_session=([^;]+)/)?.[1]
  expect(nextCookie).toBeTruthy()

  // A request whose successful response was lost can recover the same rotated
  // session instead of replaying a now-revoked token upstream.
  expect((await request.post('/api/auth/refresh', { headers })).status()).toBe(200)
  expect(
    (
      await request.get('/api/auth/session', {
        headers: { cookie: `wuu_session=${nextCookie}` },
      })
    ).status(),
  ).toBe(200)

  expect(
    (
      await request.post('/api/auth/refresh', {
        headers: { cookie: oldCookie, origin: 'https://evil.test' },
      })
    ).status(),
  ).toBe(403)
})

test('server-rendered renewal forwards its replacement HttpOnly cookie', async ({ request }) => {
  const { oldCookie } = await expiredSession(request)
  const response = await request.get('/vehicles', { headers: { cookie: oldCookie } })
  expect(response.status()).toBe(200)
  expect(response.url()).toBe(`${origin}/vehicles`)
  const cookieHeader = setCookie(response)
  expect(cookieHeader).toContain('wuu_session=')
  expect(cookieHeader).toContain('HttpOnly')
  expect(cookieHeader).not.toContain('expired-private-access-token')
})

test('an expired access token is renewed inside the gateway', async ({ request }) => {
  const { oldCookie } = await expiredSession(request)
  const response = await request.get('/api/_wuu/vehicles', { headers: { cookie: oldCookie } })
  expect(response.status()).toBe(200)
  const cookieHeader = setCookie(response)
  expect(cookieHeader).toContain('wuu_session=')
  expect(cookieHeader).not.toContain('expired-private-access-token')
  const nextCookie = cookieHeader.match(/wuu_session=([^;]+)/)?.[1]
  expect(
    (
      await request.get('/api/auth/session', {
        headers: { cookie: `wuu_session=${nextCookie}` },
      })
    ).status(),
  ).toBe(200)
})
