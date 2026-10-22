import type { FleetService, ServiceRequestOptions } from '../services/fleet-service'
import type { GatewayClient } from '../../core/api/gateway-client'
import { capabilityUnavailable, isApiError } from '../../core/domain/errors'
import { toVehicle, vehicleDtoSchema, vehiclePageSchema } from './contracts'

const PAGE_SIZE = 200
const MAX_PAGES = 50

export function createLiveFleetService(client: GatewayClient): FleetService {
  return {
    async list(options?: ServiceRequestOptions) {
      const vehicles = []
      for (let page = 0; page < MAX_PAGES; page++) {
        const response = await client.request('GET', '/vehicles', vehiclePageSchema, {
          query: { page, size: PAGE_SIZE, sortBy: 'createdAt', sortDirection: 'DESC' },
          signal: options?.signal,
        })
        vehicles.push(...response.content.map((dto) => toVehicle(dto)))
        if (response.last || response.empty || response.content.length === 0) break
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
