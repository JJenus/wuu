import { describe, expect, it, vi } from 'vitest'
import { loginSchema, safeReturnPath, userSchema } from '../../app/features/auth/domain/auth'
import { createAuthBackend, openSession, sealSession, sameOrigin } from '../../server/utils/wuu-auth'
import { normalizeWireKeys } from '../../app/features/core/api/wire-format'

const password = 'test-only-session-key-with-at-least-32-characters'
const user = { id: 7, email: 'operator@example.com', first_name: 'Fleet', last_name: 'Operator', organization_id: 12, roles: ['VIEWER'], enabled: true, locked: false }

describe('authentication boundaries', () => {
  it('validates credentials without trimming passwords or exposing tokens in identity', () => {
    expect(loginSchema.parse({ email: ' operator@example.com ', password: ' password ' }).password).toBe(' password ')
    expect(loginSchema.safeParse({ email: 'invalid', password: '' }).success).toBe(false)
    expect(userSchema.parse({ ...user, access_token: 'secret' })).not.toHaveProperty('access_token')
  })
  it('allows only local return paths', () => {
    for (const value of ['https://evil.test', '//evil.test', '/\\evil', '/sign-in', '/api/auth/login', '/%2f%2fevil.test']) expect(safeReturnPath(value)).toBe('/')
    expect(safeReturnPath('/vehicles?status=offline')).toBe('/vehicles?status=offline')
  })
  it('seals session tokens, rejects tampering, wrong keys, and expiry', () => {
    const sealed = sealSession({ accessToken: 'private-token', expiresAt: 2000 }, password)
    expect(sealed).not.toContain('private-token')
    expect(openSession(sealed, password, 1000)?.accessToken).toBe('private-token')
    expect(openSession(sealed, password, 2000)).toBeNull()
    expect(openSession(`${sealed}x`, password, 1000)).toBeNull()
    expect(openSession(sealed, `${password}wrong`, 1000)).toBeNull()
    expect(openSession(undefined, password, 1000)).toBeNull()
    expect(() => sealSession({ accessToken: 'x', expiresAt: 2000 }, 'short')).toThrow()
  })
  it('requires exact same-origin HTTPS or loopback for mutations', () => {
    expect(sameOrigin('https://wuu.example', 'wuu.example')).toBe(true)
    expect(sameOrigin('http://localhost:3001', 'localhost:3001')).toBe(true)
    for (const origin of [undefined, 'null', 'https://evil.example', 'http://wuu.example', 'https://wuu.example.evil', 'https://u:p@wuu.example']) expect(sameOrigin(origin, 'wuu.example')).toBe(false)
  })
  it('uses snake_case bearer contracts and verifies current identity after login', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(Response.json({ access_token: 'access', refresh_token: 'discard', expires_in: 900, token_type: 'Bearer' })).mockResolvedValueOnce(Response.json(user))
    const backend = createAuthBackend('http://localhost:8080', fetcher)
    const result = await backend.login({ email: 'operator@example.com', password: 'password' })
    expect(result.user.id).toBe(7)
    expect(result).not.toHaveProperty('refreshToken')
    expect(fetcher.mock.calls[1]?.[1].headers.authorization).toBe('Bearer access')
    expect(fetcher.mock.calls[0]?.[1].redirect).toBe('manual')
  })
  it('keeps invalid credentials, rate limits, malformed identity, and upstream failures as errors', async () => {
    for (const status of [401, 403, 429, 503]) {
      const backend = createAuthBackend('http://localhost:8080', vi.fn().mockResolvedValue(Response.json({ password: 'never expose this' }, { status })))
      await expect(backend.login({ email: 'operator@example.com', password: 'pw' })).rejects.toMatchObject({ status })
    }
    await expect(createAuthBackend('', vi.fn()).me('token')).rejects.toMatchObject({ status: 503 })
    await expect(createAuthBackend('http://remote.example', vi.fn()).me('token')).rejects.toMatchObject({ status: 503 })
    await expect(createAuthBackend('http://localhost', vi.fn().mockRejectedValue(new Error('private network detail'))).me('token')).rejects.toMatchObject({ status: 502 })
    await expect(createAuthBackend('http://localhost', vi.fn().mockResolvedValue(Response.json({ id: 7 }))).me('token')).rejects.toMatchObject({ status: 502 })
    await expect(createAuthBackend('http://localhost', vi.fn().mockResolvedValue(Response.json({ ...user, locked: true }))).me('token')).rejects.toMatchObject({ status: 401 })
    await expect(createAuthBackend('http://localhost', vi.fn().mockResolvedValue(new Response(null, { status: 204 }))).logout('token')).resolves.toBeUndefined()
  })
})

describe('backend JSON compatibility', () => {
  it('normalizes nested snake_case fields without changing values or accepting ambiguous keys', () => {
    expect(normalizeWireKeys({ total_elements: 1, content: [{ vehicle_id: 'VH_1', current_location: { speed_kmh: 3 } }] })).toEqual({ totalElements: 1, content: [{ vehicleId: 'VH_1', currentLocation: { speedKmh: 3 } }] })
    expect(() => normalizeWireKeys({ vehicle_id: 'one', vehicleId: 'two' })).toThrow()
  })
})
