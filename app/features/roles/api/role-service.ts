import { z } from 'zod'
import type { GatewayClient } from '../../core/api/gateway-client'
import type { Page } from '../../core/domain/pagination'
import { ApiError } from '../../core/domain/errors'

const identifier = z.number().int().positive().max(Number.MAX_SAFE_INTEGER)
const permissionKey = z
  .string()
  .max(100)
  .regex(/^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$/)
const roleSchema = z.object({
  id: identifier,
  name: z.string().trim().min(1).max(100),
  description: z.string().max(500).nullable(),
  permissions: z.array(permissionKey).max(1000),
})
const responseSchema = z
  .object({
    content: z.array(roleSchema).max(100),
    pageNumber: z.number().int().nonnegative(),
    pageSize: z.number().int().min(1).max(100),
    totalElements: z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER),
    totalPages: z.number().int().nonnegative(),
    first: z.boolean(),
    last: z.boolean(),
    empty: z.boolean(),
  })
  .superRefine((value, context) => {
    const expectedPages =
      value.totalElements === 0 ? 0 : Math.ceil(value.totalElements / value.pageSize)
    if (value.content.length > value.pageSize)
      context.addIssue({ code: 'custom', message: 'Page content exceeds page size' })
    if (value.totalPages !== expectedPages)
      context.addIssue({ code: 'custom', message: 'Page totals are inconsistent' })
    if (value.empty !== (value.content.length === 0))
      context.addIssue({ code: 'custom', message: 'Empty marker is inconsistent' })
    if (value.first !== (value.pageNumber === 0))
      context.addIssue({ code: 'custom', message: 'First-page marker is inconsistent' })
    if (value.last !== value.pageNumber >= Math.max(0, expectedPages - 1))
      context.addIssue({ code: 'custom', message: 'Last-page marker is inconsistent' })
    if (value.content.length > value.totalElements)
      context.addIssue({ code: 'custom', message: 'Page exceeds total elements' })
  })
export type Role = z.infer<typeof roleSchema>
export interface RoleService {
  list(page: number, size: number, signal?: AbortSignal): Promise<Page<Role>>
}
export function createRoleService(client: GatewayClient): RoleService {
  return {
    async list(page, size, signal) {
      if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100)
        throw new ApiError({
          kind: 'validation',
          message: 'Role pages require a nonnegative page and a size between 1 and 100.',
        })
      const response = await client.request('GET', '/roles', responseSchema, {
        query: { page, size },
        signal,
      })
      if (response.pageNumber !== page || response.pageSize !== size)
        throw new ApiError({
          kind: 'contract',
          message: 'The service returned a different role page than requested.',
        })
      return {
        items: response.content,
        page: response.pageNumber,
        size: response.pageSize,
        total: response.totalElements,
        pages: response.totalPages,
        first: response.first,
        last: response.last,
      }
    },
  }
}
