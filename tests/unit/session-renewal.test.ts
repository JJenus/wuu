import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IncomingMessage, ServerResponse } from 'node:http'
import { Socket } from 'node:net'
import { createEvent } from 'h3'
import { sealWuuSession, type WuuSession } from '../../server/utils/wuu-auth'

const password = 'test-only-session-password-at-least-32-characters'
let session: WuuSession = {
  accessToken: 'old-access',
  refreshToken: 'old-refresh',
  expiresAt: 0,
  refreshExpiresAt: Date.now() + 3_600_000,
}
const user = {
  id: 7,
  email: 'operator@example.com',
  first_name: 'Fleet',
  last_name: 'Operator',
  organization_id: 12,
  roles: ['VIEWER'],
  enabled: true,
  locked: false,
}
function event(value = session) {
  const request = new IncomingMessage(new Socket())
  request.url = '/api/auth/refresh'
  request.headers = {
    host: 'localhost:3001',
    cookie: `wuu_session=${sealWuuSession(value, password)}`,
  }
  return createEvent(request, new ServerResponse(request))
}
function tokenResponse() {
  return Response.json({
    access_token: 'new-access',
    refresh_token: 'new-refresh',
    expires_in: 900,
    refresh_expires_in: 604800,
    token_type: 'Bearer',
  })
}

describe('single-process session renewal', () => {
  beforeEach(async () => {
    vi.resetModules()
    vi.stubGlobal('useRuntimeConfig', () => ({
      apiOrigin: 'http://localhost:8080',
      sessionPassword: password,
    }))
    const { createLoginSession } = await import('../../server/utils/wuu-session')
    session = {
      ...createLoginSession({
        accessToken: 'old-access',
        refreshToken: 'old-refresh',
        expiresIn: 1,
        refreshExpiresIn: 3600,
      }),
      expiresAt: 0,
    }
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('coalesces independently sealed cookies carrying one refresh credential', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(async (url: string) =>
        url.endsWith('/refresh') ? tokenResponse() : Response.json(user),
      )
    vi.stubGlobal('fetch', fetcher)
    const { currentSession, refreshSession } = await import('../../server/utils/wuu-session')
    const first = event()
    const second = event()
    const results = await Promise.all([
      refreshSession(first, currentSession(first)),
      refreshSession(second, currentSession(second)),
    ])
    expect(fetcher).toHaveBeenCalledTimes(2)
    expect(results[0]).toEqual(results[1])
    expect(results[0]?.refreshExpiresAt).toBe(session.refreshExpiresAt)
    expect(first.node.res.getHeader('set-cookie')).toBeTruthy()
    expect(second.node.res.getHeader('set-cookie')).toBeTruthy()
    const recovered = event()
    expect(currentSession(recovered).refreshToken).toBe('new-refresh')
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('invalidates an in-flight rotation on logout without writing a replacement cookie', async () => {
    let release!: (response: Response) => void
    const fetcher = vi.fn().mockImplementation((url: string) =>
      url.endsWith('/refresh')
        ? new Promise<Response>((resolve) => {
            release = resolve
          })
        : Promise.resolve(Response.json(user)),
    )
    vi.stubGlobal('fetch', fetcher)
    const { currentSession, refreshSession, clearWuuSession } =
      await import('../../server/utils/wuu-session')
    const request = event()
    const renewal = refreshSession(request, currentSession(request))
    const rejected = expect(renewal).rejects.toMatchObject({ statusCode: 401 })
    clearWuuSession(event())
    release(tokenResponse())
    await rejected
    expect(String(request.node.res.getHeader('set-cookie'))).not.toContain('Max-Age=3600')
    expect(() => currentSession(event())).toThrow()
  })

  it('invalidates ancestor receipts when the replacement credential signs out', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(async (url: string) =>
        url.endsWith('/refresh') ? tokenResponse() : Response.json(user),
      )
    vi.stubGlobal('fetch', fetcher)
    const { refreshSession, clearWuuSession, currentSession } =
      await import('../../server/utils/wuu-session')
    const rotated = await refreshSession(event(), session)
    clearWuuSession(event(rotated))
    expect(() => currentSession(event())).toThrow()
  })

  it('clears the local cookie on an ambiguous upstream failure without replaying the credential', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('transport interrupted'))
    vi.stubGlobal('fetch', fetcher)
    const { currentSession, refreshSession } = await import('../../server/utils/wuu-session')
    const request = event()
    await expect(refreshSession(request, currentSession(request))).rejects.toMatchObject({
      statusCode: 502,
    })
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(String(request.node.res.getHeader('set-cookie'))).toContain('Max-Age=0')
    const replay = event()
    await expect(async () => refreshSession(replay, currentSession(replay))).rejects.toMatchObject({
      statusCode: 401,
    })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('rejects invalid and expired cookies before cache lookup', async () => {
    const { currentSession } = await import('../../server/utils/wuu-session')
    expect(() => currentSession(event({ ...session, refreshExpiresAt: 0 }))).toThrow()
    const request = event()
    request.node.req.headers.cookie = 'wuu_session=forged'
    expect(() => currentSession(request)).toThrow()
  })

  it('does not rotate a fresh session and preserves explicit HTTP errors', async () => {
    const { ensureFreshSession, authError, noStore, assertSameOrigin } =
      await import('../../server/utils/wuu-session')
    const fresh = { ...session, expiresAt: Date.now() + 900_000 }
    const request = event(fresh)
    expect(await ensureFreshSession(request, fresh)).toBe(fresh)
    noStore(request)
    expect(request.node.res.getHeader('cache-control')).toBe('no-store')
    expect(request.node.res.getHeader('vary')).toBe('Cookie')
    expect(() => assertSameOrigin(request)).toThrow()
    request.node.req.headers.origin = 'http://localhost:3001'
    expect(() => assertSameOrigin(request)).not.toThrow()
    expect(() => authError(new Error('private upstream detail'))).toThrow(
      'The sign-in request could not be completed.',
    )
  })

  it('bounds receipts without evicting in-window recovery and reclaims expired receipts', async () => {
    const fetcher = vi
      .fn()
      .mockImplementation(async (url: string) =>
        url.endsWith('/refresh') ? tokenResponse() : Response.json(user),
      )
    vi.stubGlobal('fetch', fetcher)
    const { currentSession, refreshSession } = await import('../../server/utils/wuu-session')
    for (let i = 0; i < 256; i++) {
      const value = { ...session, refreshToken: `bounded-${i}` }
      await refreshSession(event(value), value)
    }
    const excess = { ...session, refreshToken: 'excess' }
    await expect(refreshSession(event(excess), excess)).rejects.toMatchObject({ status: 503 })
    expect(currentSession(event({ ...session, refreshToken: 'bounded-0' })).refreshToken).toBe(
      'new-refresh',
    )
    const now = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(now + 31_000)
    expect(currentSession(event({ ...session, refreshToken: 'bounded-0' })).refreshToken).toBe(
      'bounded-0',
    )
    await expect(refreshSession(event(excess), excess)).resolves.toHaveProperty(
      'refreshToken',
      'new-refresh',
    )
  })

  it('rejects oversized replacement cookies and missing session credentials', async () => {
    const { writeSession, refreshSession } = await import('../../server/utils/wuu-session')
    expect(() => writeSession(event(), { ...session, accessToken: 'x'.repeat(4000) })).toThrow(
      'oversized session',
    )
    const request = event()
    delete request.node.req.headers.cookie
    await expect(refreshSession(request, session)).rejects.toMatchObject({ statusCode: 401 })
  })

  it('permits renewal after a fresh sign-in when an outage exhausted rejection tracking', async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error('upstream outage'))
    vi.stubGlobal('fetch', fetcher)
    const { currentSession, refreshSession, createLoginSession } =
      await import('../../server/utils/wuu-session')
    for (let i = 0; i < 256; i++) {
      const value = { ...session, refreshToken: `failed-${i}` }
      await expect(refreshSession(event(value), value)).rejects.toMatchObject({ statusCode: 502 })
    }
    fetcher.mockImplementation(async (url: string) =>
      url.endsWith('/refresh') ? tokenResponse() : Response.json(user),
    )
    const fresh = createLoginSession({
      accessToken: 'healthy-access',
      refreshToken: 'healthy-refresh',
      expiresIn: 1,
      refreshExpiresIn: 3600,
    })
    const request = event(fresh)
    await expect(refreshSession(request, currentSession(request))).resolves.toHaveProperty(
      'refreshToken',
      'new-refresh',
    )
    expect(() => currentSession(event({ ...session, refreshToken: 'failed-0' }))).toThrow()
  })

  it('requires sign-in after process-local coordination is restarted', async () => {
    const { createLoginSession } = await import('../../server/utils/wuu-session')
    const old = createLoginSession({
      accessToken: 'restart-access',
      refreshToken: 'restart-refresh',
      expiresIn: 900,
      refreshExpiresIn: 3600,
    })
    vi.resetModules()
    const restarted = await import('../../server/utils/wuu-session')
    expect(() => restarted.currentSession(event(old))).toThrow()
  })
})
