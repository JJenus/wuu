import { describe, expect, it, vi } from 'vitest'
import { upstreamPath } from '../../server/utils/wuu-gateway'
import { teamRequestAllowed, teamRequestBody, teamQueryAllowed } from '../../server/utils/team-request'
import { createTeamService } from '../../app/features/team/api/team-service'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'

const member = { id: 7, email: 'member@example.test', first_name: 'New', last_name: 'Member', enabled: true, locked: false, organization_id: 10, roles: ['OPERATOR'], created_at: '2026-11-13T00:00:00Z' }
describe('team gateway', () => {
  it('admits only exact tenant-user operations, never a general users proxy', () => {
    expect(upstreamPath('team')).toBe('/api/v1/users')
    expect(upstreamPath('team/7/roles')).toBe('/api/v1/users/7/roles')
    for (const path of ['team/me', 'team/7/sessions', 'team/0', 'team/-1', 'team/7/roles/extra']) expect(upstreamPath(path)).toBeNull()
    for (const [method, path] of [['GET', ''], ['POST', ''], ['GET', '/7'], ['PUT', '/7'], ['DELETE', '/7'], ['PUT', '/7/roles']]) expect(teamRequestAllowed(method!, '/api/v1/users' + path)).toBe(true)
    expect(teamRequestAllowed('POST', '/api/v1/users/7/roles')).toBe(false)
    expect(teamRequestAllowed('GET', '/api/v1/users/me')).toBe(false)
  })
  it('maps only strict known fields and bounds role assignments and query parameters', () => {
    expect(teamRequestBody('POST', '/api/v1/users', { email: ' NEW@example.test ', password: 'eight123', firstName: ' New ', lastName: 'Member' })).toEqual({ email: 'new@example.test', password: 'eight123', first_name: 'New', last_name: 'Member' })
    expect(teamRequestBody('PUT', '/api/v1/users/7/roles', { roleIds: [2, 3] })).toEqual({ role_ids: [2, 3] })
    for (const roleIds of [[], [0], [2, 2], [Number.MAX_SAFE_INTEGER + 1]]) expect(() => teamRequestBody('PUT', '/api/v1/users/7/roles', { roleIds })).toThrow()
    expect(() => teamRequestBody('POST', '/api/v1/users', { email: 'a@b.test', password: 'short', firstName: 'A', lastName: 'B' })).toThrow()
    expect(() => teamRequestBody('PUT', '/api/v1/users/7', { firstName: 'A', lastName: 'B', email: 'a@b.test', organizationId: 99 })).toThrow()
    expect(teamQueryAllowed('/api/v1/users', { page: '0', size: '50' })).toBe(true)
    for (const query of [{ orgId: '99' }, { size: '101' }, { page: ['0', '1'] }]) expect(teamQueryAllowed('/api/v1/users', query)).toBe(false)
    expect(teamQueryAllowed('/api/v1/users/7', { page: '0' })).toBe(false)
  })
})
describe('team adapter', () => {
  it('validates real wire records and maps all mutations without exposing credentials in results', async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify(member), { headers: { 'content-type': 'application/json' } }))
    const service = createTeamService(createGatewayClient({ fetchImpl }))
    expect(await service.create({ email: 'member@example.test', password: 'eight123', firstName: 'New', lastName: 'Member' })).toMatchObject({ id: '7', roles: ['OPERATOR'], organizationId: 10 })
    await service.assignRoles('7', [2])
    expect(fetchImpl).toHaveBeenLastCalledWith('/api/_wuu/team/7/roles', expect.objectContaining({ method: 'PUT', body: '{"roleIds":[2]}' }))
    await service.update('7', { email: 'member@example.test', firstName: 'Updated', lastName: 'Member' })
    expect(fetchImpl).toHaveBeenLastCalledWith('/api/_wuu/team/7', expect.objectContaining({ method: 'PUT' }))
  })
  it('keeps access failures as errors and rejects unsafe records', async () => {
    for (const status of [403, 404, 500]) {
      const fetchImpl = vi.fn(async () => new Response('{}', { status }))
      await expect(createTeamService(createGatewayClient({ fetchImpl })).get('7')).rejects.toMatchObject({ status })
    }
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({ ...member, id: Number.MAX_SAFE_INTEGER + 1 }), { headers: { 'content-type': 'application/json' } }))
    await expect(createTeamService(createGatewayClient({ fetchImpl })).get('7')).rejects.toMatchObject({ kind: 'contract' })
  })
})
