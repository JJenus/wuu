import { z } from 'zod'
import { nullableIsoInstantSchema, pagedResponseSchema } from '../../core/api/contracts'

export const tripDtoSchema = z.object({
  tripId: z.string().min(1),
  vehicleId: z.string().nullable().optional(),
  startTime: nullableIsoInstantSchema.optional(),
  endTime: nullableIsoInstantSchema.optional(),
  isActive: z.boolean().nullable().optional(),
  totalDistanceKm: z.number().nullable().optional(),
  averageSpeedKmh: z.number().nullable().optional(),
  maxSpeedKmh: z.number().nullable().optional(),
  fuelConsumedLiters: z.number().nullable().optional(),
  startReason: z.string().nullable().optional(),
  endReason: z.string().nullable().optional(),
})

export const tripPageSchema = pagedResponseSchema(tripDtoSchema)

export type TripDto = z.infer<typeof tripDtoSchema>
