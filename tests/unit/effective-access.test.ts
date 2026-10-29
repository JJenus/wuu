import { expect, it } from 'vitest'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import { createAccessService } from '../../app/features/auth/api/access-service'
import { upstreamPath } from '../../server/utils/wuu-gateway'

const current = { user_id: 7, organization_id: 12, permissions: ['vehicles.read'], access_expires_at: '2099-01-01T00:00:00Z' }
const service = (body: unknown, status = 200) => createAccessService(createGatewayClient({ fetchImpl: async () => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } }) }))

it('reads effective current-tenant permissions, not descriptive role templates', async () => {
  expect(await service(current).get()).toEqual({ userId: 7, organizationId: 12, permissions: ['vehicles.read'], accessExpiresAt: '2099-01-01T00:00:00Z' })
  expect((await service({ ...current, organization_id: null, permissions: ['system.admin'] }).get()).organizationId).toBeNull()
})
it('fails closed on an invalid effective-access contract and denied request', async () => {
  for (const body of [{ ...current, permissions: null }, { ...current, user_id: 9007199254740992 }, { ...current, access_expires_at: null }, { ...current, permissions: ['SUPER_ADMIN'] }]) {
    await expect(service(body).get()).rejects.toMatchObject({ kind: 'contract' })
  }
  await expect(service({}, 401).get()).rejects.toMatchObject({ status: 401 })
})
it('exposes exactly the current-user access resource, not a general users proxy', () => {
  expect(upstreamPath('access')).toBe('/api/v1/users/me/access')
  expect(upstreamPath('access/other')).toBeNull()
})
