import { z } from 'zod'
import { nullableIsoInstantSchema, pagedResponseSchema } from '../../core/api/contracts'

export const alertDtoSchema = z.object({
  alertId: z.number().int(),
  trackerId: z.string().nullable().optional(),
  vehicleId: z.string().nullable().optional(),
  alertType: z.string().nullable().optional(),
  severity: z
    .enum(['CRITICAL', 'WARNING', 'INFO', 'critical', 'warning', 'info'])
    .nullable()
    .optional(),
  message: z.string().nullable().optional(),
  latitude: z.number().nullable().optional(),
  longitude: z.number().nullable().optional(),
  speedKmh: z.number().nullable().optional(),
  triggeredAt: nullableIsoInstantSchema.optional(),
  acknowledged: z.boolean().nullable().optional(),
  acknowledgedBy: z.string().nullable().optional(),
  acknowledgedAt: nullableIsoInstantSchema.optional(),
  resolved: z.boolean().nullable().optional(),
  resolvedBy: z.string().nullable().optional(),
  resolvedAt: nullableIsoInstantSchema.optional(),
  resolutionNotes: z.string().nullable().optional(),
  createdAt: nullableIsoInstantSchema.optional(),
  updatedAt: nullableIsoInstantSchema.optional(),
})

export const alertPageSchema = pagedResponseSchema(alertDtoSchema)

export type AlertDto = z.infer<typeof alertDtoSchema>
