import type { TripService } from '../services/trip-service'
import type { GatewayClient } from '../../core/api/gateway-client'
import { finalListPage } from '../../core/api/list-completeness'
import { isApiError } from '../../core/domain/errors'
import { formatDateTime, formatDuration } from '../../core/domain/time'
import type { Trip } from '../../workspace/domain/types'
import { tripDtoSchema, tripPageSchema, type TripDto } from './contracts'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'

const PAGE_SIZE = 100
const MAX_PAGES = 50

export function toTrip(dto: TripDto, now: number = Date.now()): Trip {
  const active = dto.isActive === true || !dto.endTime
  return {
    id: dto.tripId,
    vehicleId: dto.vehicleId ?? '',
    vehicle: dto.vehicleId ?? null,
    driver: null,
    from: null,
    to: null,
    started: formatDateTime(dto.startTime),
    startedAt: dto.startTime ?? null,
    endedAt: dto.endTime ?? null,
    duration: dto.startTime ? formatDuration(dto.startTime, dto.endTime, now) : 'Not recorded',
    distance: dto.totalDistanceKm ?? 0,
    status: active ? 'in progress' : 'completed',
  }
}

export function createLiveTripService(client: GatewayClient): TripService {
  return {
    async list(options?: ServiceRequestOptions) {
      const trips: Trip[] = []
      for (let page = 0; page < MAX_PAGES; page++) {
        const response = await client.request('GET', '/trips', tripPageSchema, {
          query: { page, size: PAGE_SIZE, sortBy: 'startTime', sortDirection: 'DESC' },
          signal: options?.signal,
        })
        trips.push(...response.content.map((dto) => toTrip(dto)))
        if (finalListPage(response, page, MAX_PAGES)) break
      }
      return trips
    },
    async get(id: string, options?: ServiceRequestOptions) {
      try {
        const dto = await client.request('GET', `/trips/${encodeURIComponent(id)}`, tripDtoSchema, {
          signal: options?.signal,
        })
        return toTrip(dto)
      } catch (error) {
        if (isApiError(error) && error.kind === 'not-found') return null
        throw error
      }
    },
  }
}
