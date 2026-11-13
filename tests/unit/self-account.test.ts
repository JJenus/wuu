import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IncomingMessage, ServerResponse } from 'node:http'
import { Socket } from 'node:net'
import { createEvent } from 'h3'
import { selfProfileSchema, passwordChangeSchema } from '../../shared/self-account'
import { createAccountBackend } from '../../server/utils/account-backend'

const user = {
  id: 7,
  email: 'self@example.com',
  first_name: 'First',
  last_name: 'Last',
  organization_id: 12,
  roles: ['VIEWER'],
  enabled: true,
  locked: false,
}
const secret = 'test-only-session-password-at-least-32-characters'
describe('self account schemas and upstream boundary', () => {
  it('allows names only and never accepts identity or privilege overrides', () => {
    expect(selfProfileSchema.parse({ firstName: ' First ', lastName: '' })).toEqual({
      firstName: 'First',
      lastName: '',
    })
    for (const extra of [
      { id: 8 },
      { email: 'other@example.com' },
      { organizationId: 99 },
      { roles: ['SUPER_ADMIN'] },
      { enabled: false },
    ])
      expect(() =>
        selfProfileSchema.parse({ firstName: 'First', lastName: 'Last', ...extra }),
      ).toThrow()
    expect(() => selfProfileSchema.parse({ firstName: 'x'.repeat(101), lastName: '' })).toThrow()
  })
  it('preserves password whitespace and enforces eight codepoints and 72 UTF-8 bytes', () => {
    const input = { oldPassword: ' oldpass ', newPassword: ' new123 ' }
    expect(passwordChangeSchema.parse(input)).toEqual(input)
    for (const newPassword of ['seven12', '        ', 'é'.repeat(37), '😀'.repeat(7)])
      expect(() => passwordChangeSchema.parse({ ...input, newPassword })).toThrow()
    expect(passwordChangeSchema.parse({ ...input, newPassword: '😀'.repeat(8) }).newPassword).toBe(
      '😀'.repeat(8),
    )
    expect(() => passwordChangeSchema.parse({ ...input, userId: 9 })).toThrow()
  })
  it('updates only the authenticated identity with snake_case names', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(user))
      .mockResolvedValueOnce(Response.json({ ...user, first_name: 'New' }))
    const result = await createAccountBackend('http://localhost:8080', fetcher).updateProfile(
      'private-token',
      { firstName: 'New', lastName: 'Last' },
    )
    expect(result.first_name).toBe('New')
    expect(fetcher.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/v1/users/me')
    expect(fetcher.mock.calls[1]?.[1]?.method).toBe('PUT')
    expect(JSON.parse(fetcher.mock.calls[1]![1]!.body as string)).toEqual({
      first_name: 'New',
      last_name: 'Last',
    })
    expect(fetcher.mock.calls[1]?.[1]?.redirect).toBe('manual')
  })
  it('rejects mismatched profile identities and never adopts another account', async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json(user))
      .mockResolvedValueOnce(Response.json({ ...user, id: 8 }))
    await expect(
      createAccountBackend('http://localhost:8080', fetcher).updateProfile('token', {
        firstName: '',
        lastName: '',
      }),
    ).rejects.toMatchObject({ status: 502 })
  })
  it('distinguishes wrong current password from a revoked session and never retries', async () => {
    const fetcher = vi.fn<typeof fetch>(async () =>
      Response.json(
        { code: 'INVALID_CREDENTIALS', message: 'do not reflect upstream text' },
        { status: 401 },
      ),
    )
    await expect(
      createAccountBackend('http://localhost:8080', fetcher).changePassword('token', {
        oldPassword: 'wrong',
        newPassword: 'newpass1',
      }),
    ).rejects.toMatchObject({ status: 400, message: 'Current password is incorrect.' })
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(JSON.parse(fetcher.mock.calls[0]![1]!.body as string)).toEqual({
      old_password: 'wrong',
      new_password: 'newpass1',
    })
  })
  it.each([401, 403, 400, 429, 503, 302])(
    'maps account upstream status %s without reflecting its body',
    async (status) => {
      const fetcher = vi.fn<typeof fetch>(async () =>
        Response.json({ code: 'UNAUTHENTICATED', message: 'sensitive upstream text' }, { status }),
      )
      await expect(
        createAccountBackend('http://localhost:8080', fetcher).changePassword('token', {
          oldPassword: 'oldpass1',
          newPassword: 'newpass1',
        }),
      ).rejects.not.toHaveProperty('message', 'sensitive upstream text')
      expect(fetcher).toHaveBeenCalledTimes(1)
    },
  )
})

describe('password mutation session handling', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubGlobal('useRuntimeConfig', () => ({
      apiOrigin: 'http://localhost:8080',
      sessionPassword: secret,
    }))
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })
  async function request(body: unknown, path = '/api/auth/change-password') {
    const { createLoginSession } = await import('../../server/utils/wuu-session')
    const { sealWuuSession } = await import('../../server/utils/wuu-auth')
    const session = createLoginSession({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 900,
      refreshExpiresIn: 3600,
    })
    const incoming = new IncomingMessage(new Socket())
    incoming.url = path
    incoming.method = 'POST'
    incoming.headers = {
      host: 'localhost:3001',
      origin: 'http://localhost:3001',
      'content-type': 'application/json',
      cookie: `wuu_session=${sealWuuSession(session, secret)}`,
    }
    incoming.push(Buffer.from(JSON.stringify(body)))
    incoming.push(null)
    return createEvent(incoming, new ServerResponse(incoming))
  }
  it('clears local session and renewal receipts after confirmed change', async () => {
    const fetcher = vi.fn<typeof fetch>(async (url) =>
      String(url).endsWith('/refresh')
        ? Response.json({
            access_token: 'next-access',
            refresh_token: 'next-refresh',
            expires_in: 900,
            refresh_expires_in: 3600,
            token_type: 'Bearer',
          })
        : String(url).endsWith('/me')
          ? Response.json(user)
          : new Response(null, { status: 204 }),
    )
    vi.stubGlobal('fetch', fetcher)
    const event = await request({ oldPassword: 'eight123', newPassword: 'newpass1' })
    const { currentSession, refreshSession } = await import('../../server/utils/wuu-session')
    await refreshSession(event, currentSession(event))
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    await handleAccountMutation(event, 'password')
    expect(event.node.res.statusCode).toBe(204)
    expect(String(event.node.res.getHeader('set-cookie'))).toContain('Max-Age=0')
    expect(() => currentSession(event)).toThrow()
    expect(fetcher).toHaveBeenCalledTimes(3)
  })
  it('keeps the session for a definite wrong-current-password error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Response.json({ code: 'INVALID_CREDENTIALS' }, { status: 401 })),
    )
    const event = await request({ oldPassword: 'wrong', newPassword: 'newpass1' })
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    await expect(handleAccountMutation(event, 'password')).rejects.toMatchObject({
      statusCode: 400,
    })
    expect(String(event.node.res.getHeader('set-cookie'))).not.toContain('Max-Age=0')
    const { currentSession } = await import('../../server/utils/wuu-session')
    expect(currentSession(event).accessToken).toBe('access')
  })
  it('fails closed on ambiguous password writes without replaying them', async () => {
    const fetcher = vi.fn(async () => {
      throw new Error('synthetic lost reply')
    })
    vi.stubGlobal('fetch', fetcher)
    const event = await request({ oldPassword: 'eight123', newPassword: 'newpass1' })
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    await expect(handleAccountMutation(event, 'password')).rejects.toMatchObject({
      statusCode: 502,
    })
    expect(String(event.node.res.getHeader('set-cookie'))).toContain('Max-Age=0')
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('returns a verified profile without exposing credentials or clearing its session', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(async (_url, options) =>
        Response.json(options?.method === 'PUT' ? { ...user, first_name: 'New' } : user),
      ),
    )
    const event = await request({ firstName: 'New', lastName: 'Last' }, '/api/auth/profile')
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    const result = await handleAccountMutation(event, 'profile')
    expect(result).toMatchObject({ user: { id: 7, first_name: 'New' } })
    expect(JSON.stringify(result)).not.toContain('refresh')
    expect(String(event.node.res.getHeader('set-cookie'))).not.toContain('Max-Age=0')
  })
  it.each([400, 429, 401, 403])(
    'handles definite password status %s without replay',
    async (status) => {
      const fetcher = vi.fn<typeof fetch>(async () =>
        Response.json({ code: 'UNAUTHENTICATED' }, { status }),
      )
      vi.stubGlobal('fetch', fetcher)
      const event = await request({ oldPassword: 'oldpass1', newPassword: 'newpass1' })
      const { handleAccountMutation } = await import('../../server/utils/account-request')
      await expect(handleAccountMutation(event, 'password')).rejects.toMatchObject({
        statusCode: status,
      })
      expect(String(event.node.res.getHeader('set-cookie')).includes('Max-Age=0')).toBe(
        [401, 403].includes(status),
      )
      expect(fetcher).toHaveBeenCalledTimes(1)
    },
  )
  it('rejects CSRF, overrides, wrong content type and oversized bodies before contacting Java', async () => {
    const fetcher = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetcher)
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    const badOrigin = await request({ firstName: '', lastName: '' })
    badOrigin.node.req.headers.origin = 'https://foreign.example'
    await expect(handleAccountMutation(badOrigin, 'profile')).rejects.toMatchObject({
      statusCode: 403,
    })
    await expect(
      handleAccountMutation(
        await request({ firstName: '', lastName: '' }, '/api/auth/profile?id=99'),
        'profile',
      ),
    ).rejects.toMatchObject({ statusCode: 400 })
    const badType = await request({ firstName: '', lastName: '' })
    badType.node.req.headers['content-type'] = 'text/plain'
    await expect(handleAccountMutation(badType, 'profile')).rejects.toMatchObject({
      statusCode: 415,
    })
    await expect(
      handleAccountMutation(
        await request({ firstName: 'x'.repeat(5000), lastName: '' }),
        'profile',
      ),
    ).rejects.toMatchObject({ statusCode: 413 })
    await expect(
      handleAccountMutation(await request({ firstName: '', lastName: '', id: 9 }), 'profile'),
    ).rejects.toMatchObject({ statusCode: 400 })
    await expect(
      handleAccountMutation(
        await request({ oldPassword: 'oldpass1', newPassword: 'newpass1', userId: 9 }),
        'password',
      ),
    ).rejects.toMatchObject({ statusCode: 400 })
    expect(fetcher).not.toHaveBeenCalled()
  })
})

describe('account auth client', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('validates profile sessions and requires 204 for a confirmed password change', async () => {
    const { authRequest } = await import('../../app/features/auth/api/auth-client')
    let status = 200
    vi.stubGlobal(
      '$fetch',
      async (_url: string, options: { onResponse: (context: { response: Response }) => void }) => {
        options.onResponse({ response: new Response(null, { status }) })
        return status === 204 ? undefined : { user, expires_at: Date.now() + 100000 }
      },
    )
    await expect(
      authRequest('profile', { firstName: 'First', lastName: 'Last' }),
    ).resolves.toMatchObject({ user: { id: 7 } })
    await expect(
      authRequest('change-password', { oldPassword: 'oldpass1', newPassword: 'newpass1' }),
    ).rejects.toThrow()
    status = 204
    await expect(
      authRequest('change-password', { oldPassword: 'oldpass1', newPassword: 'newpass1' }),
    ).resolves.toBeNull()
    await expect(authRequest('logout')).resolves.toBeNull()
  })
  it('maps a definite validation failure without exposing submitted passwords', async () => {
    vi.stubGlobal('$fetch', async () => {
      throw { statusCode: 400, data: { message: 'Current password is incorrect.' } }
    })
    const { authRequest } = await import('../../app/features/auth/api/auth-client')
    await expect(
      authRequest('change-password', { oldPassword: 'wrong', newPassword: 'newpass1' }),
    ).rejects.toMatchObject({ status: 400, message: 'Current password is incorrect.' })
  })
})
