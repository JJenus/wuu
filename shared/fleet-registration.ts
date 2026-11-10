import { z } from 'zod'

// IDs must remain addressable through the gateway's existing traversal/separator guards.
const id = z
  .string()
  .max(50)
  .trim()
  .min(1, 'Enter an ID.')
  .refine(
    (value) => value !== '.' && !value.includes('..') && !/[%\/\\\u0000-\u001f\u007f]/.test(value),
    'Use an ID without percent escapes, path separators, control characters, or consecutive dots.',
  )
const optionalText = (max: number) => z.string().max(max).trim().optional()
export const vehicleRegistrationSchema = z
  .object({
    vehicleId: id,
    model: optionalText(100),
    licensePlate: optionalText(20),
    vin: optionalText(17),
  })
  .strict()
export const trackerRegistrationSchema = z
  .object({
    trackerId: id,
    deviceId: z.string().max(50).trim().min(1, 'Enter a device ID.'),
    vehicleId: id,
    model: optionalText(100),
    protocol: optionalText(20),
    firmwareVersion: optionalText(50),
    simNumber: optionalText(20),
  })
  .strict()
export type VehicleRegistration = z.infer<typeof vehicleRegistrationSchema>
export type TrackerRegistration = z.infer<typeof trackerRegistrationSchema>

export const vehicleRegistrationWireSchema = z
  .object({
    vehicle_id: id,
    model: vehicleRegistrationSchema.shape.model,
    license_plate: vehicleRegistrationSchema.shape.licensePlate,
    vin: vehicleRegistrationSchema.shape.vin,
  })
  .strict()
export const trackerRegistrationWireSchema = z
  .object({
    tracker_id: id,
    device_id: trackerRegistrationSchema.shape.deviceId,
    vehicle_id: id,
    model: trackerRegistrationSchema.shape.model,
    protocol: trackerRegistrationSchema.shape.protocol,
    firmware_version: trackerRegistrationSchema.shape.firmwareVersion,
    sim_number: trackerRegistrationSchema.shape.simNumber,
  })
  .strict()
