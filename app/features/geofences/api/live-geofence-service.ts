import type { GeofenceService } from '../services/geofence-service'
import type { GatewayClient } from '../../core/api/gateway-client'
import { finalListPage } from '../../core/api/list-completeness'
import { ApiError, isApiError } from '../../core/domain/errors'
import { geometrySummary, type GeofenceDraft, type Geometry } from '../domain/geofence'
import type { Geofence } from '../../workspace/domain/types'
import { geofenceDtoSchema, geofencePageSchema, type GeofenceDto } from './contracts'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'

const PAGE_SIZE = 100
const MAX_PAGES = 50

function geometryFrom(dto: GeofenceDto): Geometry | null {
  const shape = dto.shapeType.toUpperCase()
  if (shape === 'CIRCLE') {
    if (
      typeof dto.centerLatitude !== 'number' ||
      typeof dto.centerLongitude !== 'number' ||
      typeof dto.radiusMeters !== 'number'
    )
      return null
    return {
      type: 'circle',
      center: { lat: dto.centerLatitude, lng: dto.centerLongitude },
      radiusMeters: dto.radiusMeters,
    }
  }
  const points = dto.points ?? []
  if (points.length < 3) return null
  return {
    type: 'polygon',
    vertices: points.map((point) => ({ lat: point.latitude, lng: point.longitude })),
  }
}

export function toGeofence(dto: GeofenceDto): Geofence {
  const geometry = geometryFrom(dto)
  return {
    id: String(dto.geofenceId),
    name: dto.name,
    description: dto.description ?? '',
    icon: 'i-lucide-map-pinned',
    radius: geometrySummary(geometry),
    location: 'Location label not provided',
    events: 'Monitoring configuration not provided',
    active: dto.active,
    vehicleIds: dto.vehicleIds ? [...dto.vehicleIds] : dto.vehicleId ? [dto.vehicleId] : [],
    geometry,
  }
}

function writeRequest(input: GeofenceDraft) {
  if (!input.geometry)
    throw new ApiError({ kind: 'validation', message: 'A boundary is required.' })
  const geometry = input.geometry
  return {
    name: input.name.trim(),
    description: input.description,
    vehicleIds: [...input.vehicleIds],
    active: input.active,
    ...(geometry.type === 'circle'
      ? {
          shapeType: 'CIRCLE',
          centerLatitude: geometry.center.lat,
          centerLongitude: geometry.center.lng,
          radiusMeters: geometry.radiusMeters,
        }
      : {
          shapeType: 'POLYGON',
          points: geometry.vertices.map((point) => ({
            latitude: point.lat,
            longitude: point.lng,
          })),
        }),
  }
}

export function createLiveGeofenceService(client: GatewayClient): GeofenceService {
  async function list(options?: ServiceRequestOptions) {
    const geofences: Geofence[] = []
    for (let page = 0; page < MAX_PAGES; page++) {
      const response = await client.request('GET', '/geofences', geofencePageSchema, {
        query: { page, size: PAGE_SIZE, sortBy: 'createdAt', sortDirection: 'DESC' },
        signal: options?.signal,
      })
      geofences.push(...response.content.map(toGeofence))
      if (finalListPage(response, page, MAX_PAGES)) break
    }
    return geofences
  }

  return {
    list,
    async get(id: string, options?: ServiceRequestOptions) {
      if (!/^\d+$/.test(id)) return null
      try {
        const dto = await client.request('GET', `/geofences/${id}`, geofenceDtoSchema, {
          signal: options?.signal,
        })
        return toGeofence(dto)
      } catch (error) {
        if (isApiError(error) && error.kind === 'not-found') return null
        throw error
      }
    },
    async create(input) {
      const dto = await client.request('POST', '/geofences', geofenceDtoSchema, {
        body: writeRequest(input),
      })
      return String(dto.geofenceId)
    },
    async update(id, input) {
      if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n)
        throw new ApiError({ kind: 'validation', message: 'Invalid geofence identifier.' })
      await client.request('PUT', `/geofences/${id}`, geofenceDtoSchema, {
        body: writeRequest(input),
      })
    },
  }
}
