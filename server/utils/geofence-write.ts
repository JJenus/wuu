import { z } from 'zod'

/** Narrow edit boundary; create/delete/rule paths are not implied by a geofence read. */
export function geofenceWriteAllowed(method: string, target: string): boolean {
  const match = /^\/alerts\/geofences\/([1-9][0-9]{0,18})$/.exec(target)
  return method === 'PUT' && !!match && BigInt(match[1]!) <= 9223372036854775807n
}

const schema = z
  .object({
    name: z.string().trim().min(1).max(100).optional(),
    description: z.string().max(500).optional(),
    active: z.boolean().optional(),
    vehicleIds: z.array(z.string().min(1)).optional(),
    shapeType: z.enum(['CIRCLE', 'POLYGON']).optional(),
    centerLatitude: z.number().min(-90).max(90).optional(),
    centerLongitude: z.number().min(-180).max(180).optional(),
    radiusMeters: z.number().int().positive().max(2147483647).optional(),
    points: z
      .array(
        z
          .object({
            latitude: z.number().min(-90).max(90),
            longitude: z.number().min(-180).max(180),
          })
          .strict(),
      )
      .max(50)
      .optional(),
  })
  .strict()
  .refine((input) => Object.keys(input).length > 0)

export function geofenceWriteBody(target: string, body: unknown): Record<string, unknown> {
  if (!geofenceWriteAllowed('PUT', target)) throw new Error('Unknown geofence edit')
  const input = schema.parse(body)
  const fields: Record<keyof typeof input, string> = {
    name: 'name',
    description: 'description',
    active: 'active',
    vehicleIds: 'vehicle_ids',
    shapeType: 'shape_type',
    centerLatitude: 'center_latitude',
    centerLongitude: 'center_longitude',
    radiusMeters: 'radius_meters',
    points: 'points',
  }
  // Omission is deliberately not defaulted: empty arrays/strings are real edits.
  return Object.fromEntries(
    Object.entries(input).map(([key, value]) => [fields[key as keyof typeof input], value]),
  )
}
