import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IncomingMessage, ServerResponse } from 'node:http'
import { Socket } from 'node:net'
import { createEvent } from 'h3'
import { sealWuuSession, type WuuSession } from '../../server/utils/wuu-auth'

const password = 'test-only-session-password-at-least-32-characters'
const session: WuuSession = {
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
  beforeEach(() => {
    vi.resetModules()
    vi.stubGlobal('useRuntimeConfig', () => ({
      apiOrigin: 'http://localhost:8080',
      sessionPassword: password,
    }))
  })
  afterEach(() => vi.unstubAllGlobals())

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
    expect(currentSession(event()).refreshToken).toBe('old-refresh')
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
    await expect(async () => refreshSession(replay, currentSession(replay))).rejects.toMatchObject({ statusCode: 401 })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('rejects invalid and expired cookies before cache lookup', async () => {
    const { currentSession } = await import('../../server/utils/wuu-session')
    expect(() => currentSession(event({ ...session, refreshExpiresAt: 0 }))).toThrow()
    const request = event()
    request.node.req.headers.cookie = 'wuu_session=forged'
    expect(() => currentSession(request)).toThrow()
  })
})
