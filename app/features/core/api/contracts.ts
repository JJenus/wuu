import { z } from 'zod'

export const isoInstantSchema = z.string().datetime({ offset: true })

export const nullableIsoInstantSchema = isoInstantSchema.nullable()

export const pagedResponseSchema = <T extends z.ZodType>(item: T) =>
  z.object({
    content: z.array(item),
    pageNumber: z.number().int().nonnegative().catch(0),
    pageSize: z.number().int().positive().catch(20),
    totalElements: z.number().int().nonnegative().catch(0),
    totalPages: z.number().int().nonnegative().catch(0),
    first: z.boolean().catch(true),
    last: z.boolean(),
    empty: z.boolean().catch(false),
  })

export const springPageSchema = <T extends z.ZodType>(item: T) =>
  z.object({
    content: z.array(item),
    number: z.number().int().nonnegative().catch(0),
    size: z.number().int().positive().catch(20),
    totalElements: z.number().int().nonnegative().catch(0),
    totalPages: z.number().int().nonnegative().catch(0),
    first: z.boolean().catch(true),
    last: z.boolean().catch(true),
    empty: z.boolean().catch(false),
  })

export const apiIssueSchema = z.object({
  path: z.string().optional(),
  field: z.string().optional(),
  message: z.string(),
})

export const apiErrorSchema = z.object({
  message: z.string().optional(),
  detail: z.string().optional(),
  error: z.string().optional(),
  errors: z
    .union([
      z.record(z.string(), z.union([z.string(), z.array(z.string())])),
      z.array(apiIssueSchema),
    ])
    .optional(),
  fieldErrors: z.record(z.string(), z.union([z.string(), z.array(z.string())])).optional(),
})

export type PagedDto<T> = z.infer<ReturnType<typeof pagedResponseSchema<z.ZodType<T>>>>
export type SpringPageDto<T> = z.infer<ReturnType<typeof springPageSchema<z.ZodType<T>>>>

export function toPage<T>(
  dto:
    | {
        content: T[]
        pageNumber: number
        pageSize: number
        totalElements: number
        totalPages: number
        first: boolean
        last: boolean
      }
    | {
        content: T[]
        number: number
        size: number
        totalElements: number
        totalPages: number
        first: boolean
        last: boolean
      },
): import('../domain/pagination').Page<T> {
  const spring = 'number' in dto
  return {
    items: dto.content,
    page: spring ? dto.number : dto.pageNumber,
    size: spring ? dto.size : dto.pageSize,
    total: dto.totalElements,
    pages: dto.totalPages,
    first: dto.first,
    last: dto.last,
  }
}
