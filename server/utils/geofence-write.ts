import { z } from 'zod'

import { upstreamApi, upstreamSuffix } from './upstream-api'

const base = upstreamApi.geofences

/** Only atomic collection create and individual edits; no delete/rule side paths. */
export function geofenceWriteAllowed(method: string, target: string): boolean {
  if (method === 'POST') return target === base
  const suffix = upstreamSuffix(target, base)
  const match = suffix === null ? null : /^\/([1-9][0-9]{0,18})$/.exec(suffix)
  return method === 'PUT' && !!match && BigInt(match[1]!) <= 9223372036854775807n
}

const schema = z
  .object({
    name: z.string().trim().min(1).max(100).optional(),
    description: z.string().max(500).optional(),
    active: z.boolean().optional(),
    vehicleIds: z.array(z.string().min(1).max(50)).optional(),
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

const createSchema = schema
  .required({ name: true, active: true, vehicleIds: true, shapeType: true })
  .superRefine((input, context) => {
    const invalid =
      input.shapeType === 'CIRCLE'
        ? input.centerLatitude === undefined ||
          input.centerLongitude === undefined ||
          input.radiusMeters === undefined
        : !input.points || input.points.length < 3
    if (invalid)
      context.addIssue({ code: 'custom', message: 'Complete boundary geometry is required.' })
  })

export function geofenceWriteBody(target: string, body: unknown): Record<string, unknown> {
  const creating = target === base
  if (!geofenceWriteAllowed(creating ? 'POST' : 'PUT', target))
    throw new Error('Unknown geofence write')
  const input = creating
    ? createSchema.parse(body)
    : schema.refine((value) => Object.keys(value).length > 0).parse(body)
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
