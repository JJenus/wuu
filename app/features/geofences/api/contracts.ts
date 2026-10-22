import { z } from 'zod'
import { pagedResponseSchema } from '../../core/api/contracts'

export const geofencePointDtoSchema = z.object({
  latitude: z.number(),
  longitude: z.number(),
})

export const geofenceDtoSchema = z.object({
  geofenceId: z.number().int(),
  vehicleId: z.string().nullable().optional(),
  name: z.string(),
  shapeType: z.enum(['CIRCLE', 'POLYGON', 'circle', 'polygon']),
  centerLatitude: z.number().nullable().optional(),
  centerLongitude: z.number().nullable().optional(),
  radiusMeters: z.number().int().nullable().optional(),
  active: z.boolean(),
  points: z.array(geofencePointDtoSchema).nullable().optional(),
  description: z.string().nullable().optional(),
  createdAt: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
})

export const geofencePageSchema = pagedResponseSchema(geofenceDtoSchema)

export type GeofenceDto = z.infer<typeof geofenceDtoSchema>
