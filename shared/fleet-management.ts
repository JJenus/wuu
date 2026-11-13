import { z } from 'zod'
import { vehicleRegistrationSchema } from './fleet-registration'

const text = (max: number) => z.string().max(max).trim().nullable()
export const vehicleProfileSchema = z
  .object({ model: text(100), licensePlate: text(20), vin: text(17) })
  .strict()
export const trackerProfileSchema = z
  .object({ model: text(100), protocol: text(20), firmwareVersion: text(50), simNumber: text(20) })
  .strict()
export const trackerReassignmentSchema = z
  .object({
    sourceVehicleId: vehicleRegistrationSchema.shape.vehicleId,
    targetVehicleId: vehicleRegistrationSchema.shape.vehicleId,
  })
  .strict()
export const vehicleProfileWireSchema = z
  .object({ model: text(100), license_plate: text(20), vin: text(17) })
  .strict()
export const trackerProfileWireSchema = z
  .object({
    model: text(100),
    protocol: text(20),
    firmware_version: text(50),
    sim_number: text(20),
  })
  .strict()
export const trackerReassignmentWireSchema = z
  .object({
    source_vehicle_id: vehicleRegistrationSchema.shape.vehicleId,
    target_vehicle_id: vehicleRegistrationSchema.shape.vehicleId,
  })
  .strict()
export type VehicleProfile = z.infer<typeof vehicleProfileSchema>
export type TrackerProfile = z.infer<typeof trackerProfileSchema>
export type TrackerReassignment = z.infer<typeof trackerReassignmentSchema>
