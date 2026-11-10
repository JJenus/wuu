import type { FleetService, ServiceRequestOptions } from '../services/fleet-service'
import type { GatewayClient } from '../../core/api/gateway-client'
import { finalListPage } from '../../core/api/list-completeness'
import { ApiError, capabilityUnavailable, isApiError } from '../../core/domain/errors'
import {
  toVehicle,
  vehicleDtoSchema,
  vehiclePageSchema,
  trackerDtoSchema,
  trackerPageSchema,
} from './contracts'
import { vehicleRegistrationSchema, trackerRegistrationSchema } from '../domain/registration'
import type { FleetTracker } from '../domain/registration'

const PAGE_SIZE = 200
const MAX_PAGES = 50

export function createLiveFleetService(client: GatewayClient): FleetService {
  return {
    async registerVehicle(input) {
      const parsed = vehicleRegistrationSchema.parse(input)
      const dto = await client.request('POST', '/vehicles', vehicleDtoSchema, {
        body: {
          vehicle_id: parsed.vehicleId,
          model: parsed.model,
          license_plate: parsed.licensePlate,
          vin: parsed.vin,
        },
      })
      if (dto.vehicleId !== parsed.vehicleId)
        throw new ApiError({
          kind: 'contract',
          message: 'The saved vehicle identity could not be verified. Reload before retrying.',
        })
      return toVehicle(dto)
    },
    async registerTracker(input) {
      const parsed = trackerRegistrationSchema.parse(input)
      const dto = await client.request('POST', '/trackers', trackerDtoSchema, {
        body: {
          tracker_id: parsed.trackerId,
          device_id: parsed.deviceId,
          vehicle_id: parsed.vehicleId,
          model: parsed.model,
          protocol: parsed.protocol,
          firmware_version: parsed.firmwareVersion,
          sim_number: parsed.simNumber,
        },
      })
      if (
        dto.trackerId !== parsed.trackerId ||
        dto.deviceId !== parsed.deviceId ||
        dto.vehicleId !== parsed.vehicleId
      )
        throw new ApiError({
          kind: 'contract',
          message: 'The saved tracker assignment could not be verified. Reload before retrying.',
        })
      return dto
    },
    async listTrackers(vehicleId, options) {
      const trackers: FleetTracker[] = []
      for (let page = 0; page < MAX_PAGES; page++) {
        const response = await client.request('GET', '/trackers', trackerPageSchema, {
          query: { page, size: PAGE_SIZE, sortBy: 'createdAt', sortDirection: 'DESC' },
          signal: options?.signal,
        })
        trackers.push(...response.content.filter((tracker) => tracker.vehicleId === vehicleId))
        if (finalListPage(response, page, MAX_PAGES)) break
      }
      return trackers
    },
    async list(options?: ServiceRequestOptions) {
      const vehicles = []
      for (let page = 0; page < MAX_PAGES; page++) {
        const response = await client.request('GET', '/vehicles', vehiclePageSchema, {
          query: { page, size: PAGE_SIZE, sortBy: 'createdAt', sortDirection: 'DESC' },
          signal: options?.signal,
        })
        vehicles.push(...response.content.map((dto) => toVehicle(dto)))
        if (finalListPage(response, page, MAX_PAGES)) break
      }
      return vehicles
    },
    async get(id: string, options?: ServiceRequestOptions) {
      try {
        const dto = await client.request(
          'GET',
          `/vehicles/${encodeURIComponent(id)}`,
          vehicleDtoSchema,
          {
            signal: options?.signal,
          },
        )
        return toVehicle(dto)
      } catch (error) {
        if (isApiError(error) && error.kind === 'not-found') return null
        throw error
      }
    },
    async create() {
      throw capabilityUnavailable(
        'Creating a vehicle needs a backend vehicle ID and tracker contract. Use preview mode until that workflow is verified.',
      )
    },
  }
}
