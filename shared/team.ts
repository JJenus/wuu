import { z } from 'zod'
const name = z.string().trim().min(1).max(100)
export const teamProfileSchema = z
  .object({
    email: z.string().trim().toLowerCase().email().max(254),
    firstName: name,
    lastName: name,
  })
  .strict()
export const teamCreateSchema = teamProfileSchema
  .extend({ password: z.string().min(8).max(72) })
  .strict()
export const teamRolesSchema = z
  .object({
    roleIds: z
      .array(z.number().int().positive().max(Number.MAX_SAFE_INTEGER))
      .min(1)
      .max(50)
      .refine((ids) => new Set(ids).size === ids.length),
  })
  .strict()
export type TeamProfile = z.infer<typeof teamProfileSchema>
export type TeamCreate = z.infer<typeof teamCreateSchema>
