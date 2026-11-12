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

export const tripHistorySchema = z.object({
  tripId: z.string().min(1),
  vehicleId: z.string().min(1),
  source: z.literal('VEHICLE_TIME_WINDOW'),
  windowStart: z.iso.datetime({ offset: true }),
  windowEnd: z.iso.datetime({ offset: true }),
  truncated: z.boolean(),
  points: z.array(z.object({
    locationId: z.number().int().positive().max(Number.MAX_SAFE_INTEGER),
    recordedAt: z.iso.datetime({ offset: true }),
    latitude: z.number().min(-90).max(90),
    longitude: z.number().min(-180).max(180),
    speedKmh: z.number().nonnegative().nullable(),
  })).max(10000),
}).refine((history) => {
  const start = Date.parse(history.windowStart), end = Date.parse(history.windowEnd)
  if (start > end) return false
  return history.points.every((point, i) => {
    const at = Date.parse(point.recordedAt), previous = history.points[i - 1]
    return at >= start && at <= end && (!previous || at > Date.parse(previous.recordedAt) ||
      (at === Date.parse(previous.recordedAt) && point.locationId > previous.locationId))
  })
}, 'History must be ordered and inside its declared window')
