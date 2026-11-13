import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IncomingMessage, ServerResponse } from 'node:http'
import { Socket } from 'node:net'
import { createEvent } from 'h3'
import { selfProfileSchema, passwordChangeSchema } from '../../shared/self-account'
import { createAccountBackend } from '../../server/utils/account-backend'

const user = { id: 7, email: 'self@example.com', first_name: 'First', last_name: 'Last', organization_id: 12, roles: ['VIEWER'], enabled: true, locked: false }
const secret = 'test-only-session-password-at-least-32-characters'
describe('self account schemas and upstream boundary', () => {
  it('allows names only and never accepts identity or privilege overrides', () => {
    expect(selfProfileSchema.parse({ firstName: ' First ', lastName: '' })).toEqual({ firstName: 'First', lastName: '' })
    for (const extra of [{ id: 8 }, { email: 'other@example.com' }, { organizationId: 99 }, { roles: ['SUPER_ADMIN'] }, { enabled: false }])
      expect(() => selfProfileSchema.parse({ firstName: 'First', lastName: 'Last', ...extra })).toThrow()
    expect(() => selfProfileSchema.parse({ firstName: 'x'.repeat(101), lastName: '' })).toThrow()
  })
  it('preserves password whitespace and enforces eight codepoints and 72 UTF-8 bytes', () => {
    const input = { oldPassword: ' oldpass ', newPassword: ' new123 ' }
    expect(passwordChangeSchema.parse(input)).toEqual(input)
    for (const newPassword of ['seven12', '        ', 'é'.repeat(37), '😀'.repeat(7)])
      expect(() => passwordChangeSchema.parse({ ...input, newPassword })).toThrow()
    expect(passwordChangeSchema.parse({ ...input, newPassword: '😀'.repeat(8) }).newPassword).toBe('😀'.repeat(8))
    expect(() => passwordChangeSchema.parse({ ...input, userId: 9 })).toThrow()
  })
  it('updates only the authenticated identity with snake_case names', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json(user)).mockResolvedValueOnce(Response.json({ ...user, first_name: 'New' }))
    const result = await createAccountBackend('http://localhost:8080', fetcher).updateProfile('private-token', { firstName: 'New', lastName: 'Last' })
    expect(result.first_name).toBe('New')
    expect(fetcher.mock.calls[1]?.[0]).toBe('http://localhost:8080/api/v1/users/me')
    expect(fetcher.mock.calls[1]?.[1].method).toBe('PUT')
    expect(JSON.parse(fetcher.mock.calls[1]?.[1].body)).toEqual({ first_name: 'New', last_name: 'Last' })
    expect(fetcher.mock.calls[1]?.[1].redirect).toBe('manual')
  })
  it('rejects mismatched profile identities and never adopts another account', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json(user)).mockResolvedValueOnce(Response.json({ ...user, id: 8 }))
    await expect(createAccountBackend('http://localhost:8080', fetcher).updateProfile('token', { firstName: '', lastName: '' })).rejects.toMatchObject({ status: 502 })
  })
  it('distinguishes wrong current password from a revoked session and never retries', async () => {
    const fetcher = vi.fn(async () => Response.json({ code: 'INVALID_CREDENTIALS', message: 'do not reflect upstream text' }, { status: 401 }))
    await expect(createAccountBackend('http://localhost:8080', fetcher).changePassword('token', { oldPassword: 'wrong', newPassword: 'newpass1' })).rejects.toMatchObject({ status: 400, message: 'Current password is incorrect.' })
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(JSON.parse(fetcher.mock.calls[0]![1]!.body as string)).toEqual({ old_password: 'wrong', new_password: 'newpass1' })
  })
})

describe('password mutation session handling', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubGlobal('useRuntimeConfig', () => ({ apiOrigin: 'http://localhost:8080', sessionPassword: secret }))
  })
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
  async function request(body: unknown) {
    const { createLoginSession } = await import('../../server/utils/wuu-session')
    const { sealWuuSession } = await import('../../server/utils/wuu-auth')
    const session = createLoginSession({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900, refreshExpiresIn: 3600 })
    const incoming = new IncomingMessage(new Socket()); incoming.url = '/api/auth/change-password'; incoming.method = 'POST'
    incoming.headers = { host: 'localhost:3001', origin: 'http://localhost:3001', 'content-type': 'application/json', cookie: `wuu_session=${sealWuuSession(session, secret)}` }
    incoming.push(Buffer.from(JSON.stringify(body))); incoming.push(null)
    return createEvent(incoming, new ServerResponse(incoming))
  }
  it('clears local session and renewal receipts after confirmed change', async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 })); vi.stubGlobal('fetch', fetcher)
    const event = await request({ oldPassword: 'eight123', newPassword: 'newpass1' })
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    await handleAccountMutation(event, 'password')
    expect(event.node.res.statusCode).toBe(204)
    expect(String(event.node.res.getHeader('set-cookie'))).toContain('Max-Age=0')
    const { currentSession } = await import('../../server/utils/wuu-session')
    expect(() => currentSession(event)).toThrow()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('keeps the session for a definite wrong-current-password error', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Response.json({ code: 'INVALID_CREDENTIALS' }, { status: 401 })))
    const event = await request({ oldPassword: 'wrong', newPassword: 'newpass1' })
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    await expect(handleAccountMutation(event, 'password')).rejects.toMatchObject({ statusCode: 400 })
    expect(String(event.node.res.getHeader('set-cookie'))).not.toContain('Max-Age=0')
    const { currentSession } = await import('../../server/utils/wuu-session')
    expect(currentSession(event).accessToken).toBe('access')
  })
  it('fails closed on ambiguous password writes without replaying them', async () => {
    const fetcher = vi.fn(async () => { throw new Error('synthetic lost reply') }); vi.stubGlobal('fetch', fetcher)
    const event = await request({ oldPassword: 'eight123', newPassword: 'newpass1' })
    const { handleAccountMutation } = await import('../../server/utils/account-request')
    await expect(handleAccountMutation(event, 'password')).rejects.toMatchObject({ statusCode: 502 })
    expect(String(event.node.res.getHeader('set-cookie'))).toContain('Max-Age=0')
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
})
