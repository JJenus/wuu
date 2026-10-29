import { describe, expect, it } from 'vitest'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import { createRoleService } from '../../app/features/roles/api/role-service'
import { upstreamPath } from '../../server/utils/wuu-gateway'

const page = {
  content: [
    {
      id: 7,
      name: 'TENANT_ADMIN',
      description: 'Organization administrator',
      permissions: ['roles.read', 'users.read'],
    },
    { id: 8, name: 'VIEWER', description: null, permissions: [] },
  ],
  page_number: 1,
  page_size: 20,
  total_elements: 22,
  total_pages: 2,
  first: false,
  last: true,
  empty: false,
}

describe('tenant role catalog adapter', () => {
  it('maps a bounded tenant page and sends exact paging query parameters', async () => {
    let url = ''
    const service = createRoleService(
      createGatewayClient({
        fetchImpl: async (input) => {
          url = String(input)
          return new Response(JSON.stringify(page), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          })
        },
      }),
    )
    await expect(service.list(1, 20)).resolves.toEqual({
      items: [
        {
          id: 7,
          name: 'TENANT_ADMIN',
          description: 'Organization administrator',
          permissions: ['roles.read', 'users.read'],
        },
        { id: 8, name: 'VIEWER', description: null, permissions: [] },
      ],
      page: 1,
      size: 20,
      total: 22,
      pages: 2,
      first: false,
      last: true,
    })
    expect(url).toBe('/api/_wuu/roles?page=1&size=20')
  })

  it.each([
    { ...page, content: [{ ...page.content[0], id: 0 }] },
    { ...page, content: [{ ...page.content[0], permissions: ['ROLE_ADMIN'] }] },
    { ...page, content: [{ ...page.content[0], description: 'x'.repeat(501) }] },
    { ...page, page_number: -1 },
    { ...page, page_size: 0 },
    { ...page, total_elements: 1.5 },
    { ...page, total_pages: 1 },
    { ...page, content: [], empty: false },
    { ...page, last: false },
  ])('fails closed on an inconsistent role page: %o', async (body) => {
    const service = createRoleService(
      createGatewayClient({
        fetchImpl: async () =>
          new Response(JSON.stringify(body), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }),
      }),
    )
    await expect(service.list(1, 20)).rejects.toMatchObject({ kind: 'contract' })
  })

  it('rejects a backend page that differs from the requested page or size', async () => {
    const service = createRoleService(
      createGatewayClient({
        fetchImpl: async () =>
          new Response(JSON.stringify({ ...page, page_number: 0 }), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          }),
      }),
    )
    await expect(service.list(1, 20)).rejects.toMatchObject({ kind: 'contract' })
  })

  it('exposes exactly the role collection, not tenant-override subpaths', () => {
    expect(upstreamPath('roles')).toBe('/api/v1/roles')
    expect(upstreamPath('roles/1')).toBeNull()
    expect(upstreamPath('roles/999999')).toBeNull()
  })
})
