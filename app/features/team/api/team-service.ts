import { z } from 'zod'
import {
  teamCreateSchema,
  teamProfileSchema,
  teamRolesSchema,
  type TeamCreate,
  type TeamProfile,
} from '#shared/team'
import type { GatewayClient } from '../../core/api/gateway-client'
import { pagedResponseSchema, nullableIsoInstantSchema } from '../../core/api/contracts'
import type { Page } from '../../core/domain/pagination'
import { ApiError } from '../../core/domain/errors'
import type { TeamMember } from '../domain/team'
const id = z.number().int().positive().max(Number.MAX_SAFE_INTEGER)
const memberSchema = z.object({
  id: id.transform(String),
  email: z.string().email(),
  firstName: z.string().nullable(),
  lastName: z.string().nullable(),
  enabled: z.boolean(),
  locked: z.boolean(),
  organizationId: id.nullable(),
  roles: z.array(z.string()),
  createdAt: nullableIsoInstantSchema,
})
const pageSchema = pagedResponseSchema(memberSchema)
  .extend({
    content: z.array(memberSchema).max(50),
    pageNumber: z.number().int().nonnegative(),
    pageSize: z.literal(50),
    totalElements: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
    totalPages: z.number().int().nonnegative(),
    first: z.boolean(),
    last: z.boolean(),
    empty: z.boolean(),
  })
  .refine(
    (result) =>
      result.totalPages === Math.ceil(result.totalElements / result.pageSize) &&
      result.empty === (result.content.length === 0) &&
      result.first === (result.pageNumber === 0) &&
      result.last === result.pageNumber >= Math.max(0, result.totalPages - 1) &&
      result.content.length <= result.totalElements,
    'Inconsistent team pagination',
  )
export interface TeamService {
  list(page: number, signal?: AbortSignal): Promise<Page<TeamMember>>
  get(id: string, signal?: AbortSignal): Promise<TeamMember>
  create(input: TeamCreate): Promise<TeamMember>
  update(id: string, input: TeamProfile): Promise<TeamMember>
  assignRoles(id: string, roleIds: number[]): Promise<TeamMember>
  remove(id: string): Promise<void>
}
function path(id: string) {
  if (!/^[1-9][0-9]{0,15}$/.test(id) || !Number.isSafeInteger(Number(id)))
    throw new ApiError({ kind: 'validation', message: 'Invalid member ID.' })
  return `/team/${id}`
}
export function createTeamService(client: GatewayClient): TeamService {
  return {
    async list(page, signal) {
      if (!Number.isInteger(page) || page < 0)
        throw new ApiError({ kind: 'validation', message: 'Invalid team page.' })
      const result = await client.request('GET', '/team', pageSchema, {
        query: { page, size: 50 },
        signal,
      })
      if (result.pageNumber !== page)
        throw new ApiError({
          kind: 'contract',
          message: 'The service returned a different team page.',
        })
      return {
        items: result.content,
        page: result.pageNumber ?? page,
        size: 50,
        total: result.totalElements ?? result.content.length,
        pages: result.totalPages ?? 1,
        first: result.first ?? page === 0,
        last: result.last,
      }
    },
    get(id, signal) {
      return client.request('GET', path(id), memberSchema, { signal })
    },
    create(input) {
      return client.request('POST', '/team', memberSchema, { body: teamCreateSchema.parse(input) })
    },
    update(id, input) {
      return client.request('PUT', path(id), memberSchema, { body: teamProfileSchema.parse(input) })
    },
    assignRoles(id, roleIds) {
      return client.request('PUT', `${path(id)}/roles`, memberSchema, {
        body: teamRolesSchema.parse({ roleIds }),
      })
    },
    async remove(id) {
      await client.request('DELETE', path(id), z.undefined(), { body: {} })
    },
  }
}
