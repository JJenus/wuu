import { describe, expect, it, vi } from 'vitest'
import { localPickerAllowed, loadLocalAccounts } from '../../server/utils/wuu-local-accounts'
import { localAccountsRequest } from '../../app/features/auth/api/local-accounts-client'

describe('local password-based account picker', () => {
  it('requires explicit enablement, loopback host, peer, and upstream', () => {
    const input = {
      enabled: 'true',
      host: 'localhost:3001',
      peer: '127.0.0.1',
      origin: 'http://127.0.0.1:8080',
    }
    expect(localPickerAllowed(input)).toBe(true)
    for (const patch of [
      { enabled: 'false' },
      { enabled: '' },
      { host: 'wuu.example' },
      { peer: '192.0.2.1' },
      { peer: undefined },
      { origin: 'https://api.example' },
      { origin: 'http://user:password@localhost:8080' },
    ])
      expect(localPickerAllowed({ ...input, ...patch })).toBe(false)
  })

  it('queries all pages fresh and forwards only email and display name', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({
          accounts: [
            { email: 'admin@wuu.local', display_name: 'Admin', password_hash: 'never-forward' },
          ],
          next_page: 1,
        }),
      )
      .mockResolvedValueOnce(
        Response.json({
          accounts: [{ email: 'new@example.test', display_name: 'New' }],
          next_page: null,
        }),
      )
    const result = await loadLocalAccounts('http://127.0.0.1:8080', fetcher)
    expect(result).toEqual({
      accounts: [
        { email: 'admin@wuu.local', display_name: 'Admin' },
        { email: 'new@example.test', display_name: 'New' },
      ],
    })
    expect(fetcher.mock.calls[0]?.[0]).toBe(
      'http://127.0.0.1:8080/api/v1/auth/local-accounts?page=0',
    )
    expect(fetcher.mock.calls[1]?.[0]).toContain('page=1')
    expect(fetcher.mock.calls[0]?.[1]).toMatchObject({
      method: 'GET',
      cache: 'no-store',
      redirect: 'manual',
    })
  })

  it('does not replace upstream errors or malformed pages with sample accounts', async () => {
    for (const response of [
      Response.json({}, { status: 503 }),
      Response.json({ accounts: [{}], next_page: null }),
      Response.json({ accounts: [], next_page: 0 }),
    ]) {
      await expect(
        loadLocalAccounts('http://localhost:8080', vi.fn().mockResolvedValue(response)),
      ).rejects.toMatchObject({ status: 502 })
    }
    await expect(loadLocalAccounts('https://remote.example', vi.fn())).rejects.toMatchObject({
      status: 404,
    })
    await expect(
      loadLocalAccounts('http://localhost:8080', vi.fn().mockRejectedValue(new Error('secret'))),
    ).rejects.toMatchObject({ status: 502 })
  })

  it('rejects malformed origins and unbounded page sequences', async () => {
    await expect(loadLocalAccounts('invalid', vi.fn())).rejects.toMatchObject({ status: 404 })
    const fetcher = vi.fn(async (url: string) => {
      const page = Number(new URL(url).searchParams.get('page'))
      return Response.json({ accounts: [], next_page: page + 1 })
    })
    await expect(loadLocalAccounts('http://localhost', fetcher)).rejects.toMatchObject({
      status: 502,
    })
    expect(fetcher).toHaveBeenCalledTimes(50)
    expect(
      localPickerAllowed({
        enabled: 'true',
        host: undefined,
        peer: '127.0.0.1',
        origin: 'http://localhost',
      }),
    ).toBe(false)
  })

  it('validates client responses and preserves server errors without credentials', async () => {
    const fetcher = vi.fn().mockResolvedValue({
      enabled: true,
      accounts: [{ email: 'user@wuu.local', display_name: 'User', password: 'discard' }],
    })
    const result = await localAccountsRequest(fetcher)
    expect(result.accounts[0]).not.toHaveProperty('password')
    await expect(
      localAccountsRequest(vi.fn().mockRejectedValue({ statusCode: 502, data: {} })),
    ).rejects.toMatchObject({ status: 502 })
    await expect(
      localAccountsRequest(vi.fn().mockResolvedValue({ accounts: [] })),
    ).rejects.toBeDefined()
  })
})
