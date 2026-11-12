import { z } from 'zod'

export const geofenceRuleId = z
  .string()
  .regex(/^[1-9][0-9]{0,18}$/)
  .refine((id) => /^[1-9][0-9]{0,18}$/.test(id) && BigInt(id) <= 9223372036854775807n)
export const ruleKeySchema = z
  .string()
  .trim()
  .min(3)
  .max(100)
  .regex(/^[A-Za-z0-9_-]+$/)
export const geofenceRuleInputSchema = z
  .object({
    ruleKey: ruleKeySchema,
    ruleName: z.string().max(200).trim().min(3),
    geofenceId: geofenceRuleId,
    action: z.enum(['ENTRY', 'EXIT', 'BOTH']),
    vehicleIds: z.array(z.string().trim().min(1).max(50)).min(1).max(100),
    priority: z.number().int().min(1).max(100),
    enabled: z.boolean(),
  })
  .strict()
export type GeofenceRuleInput = z.infer<typeof geofenceRuleInputSchema>
