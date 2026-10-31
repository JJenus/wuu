import type { AlertService, AlertTransitionContext } from '../services/alert-service'
import type { GatewayClient } from '../../core/api/gateway-client'
import { finalListPage } from '../../core/api/list-completeness'
import { capabilityUnavailable, isApiError } from '../../core/domain/errors'
import { formatRelativeTime } from '../../core/domain/time'
import type { AlertState, FleetAlert } from '../../workspace/domain/types'
import { alertPageSchema, alertDtoSchema, type AlertDto } from './contracts'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'

const PAGE_SIZE = 100
const MAX_PAGES = 50

function titleFor(type: string | null | undefined, message: string | null | undefined): string {
  if (type) {
    const words = type.toLowerCase().replaceAll('_', ' ')
    return words.charAt(0).toUpperCase() + words.slice(1)
  }
  return message?.trim().split(/[.!?]/)[0]?.slice(0, 60) || 'Fleet alert'
}

function iconFor(type: string | null | undefined): string {
  const key = type?.toUpperCase() ?? ''
  if (key.includes('FUEL')) return 'i-lucide-fuel'
  if (key.includes('IDLE')) return 'i-lucide-timer'
  if (key.includes('GEOFENCE')) return 'i-lucide-map-pin'
  if (key.includes('SPEED')) return 'i-lucide-gauge'
  if (key.includes('OFFLINE') || key.includes('DISCONNECT')) return 'i-lucide-wifi-off'
  return 'i-lucide-bell-ring'
}

function severity(value: AlertDto['severity']): FleetAlert['severity'] {
  const normalized = value?.toLowerCase()
  return normalized === 'critical' || normalized === 'warning' || normalized === 'info'
    ? normalized
    : 'info'
}

function status(dto: AlertDto): AlertState {
  if (dto.resolved) return 'resolved'
  if (dto.acknowledged) return 'acknowledged'
  return 'active'
}

export function toFleetAlert(dto: AlertDto, now: number = Date.now()): FleetAlert {
  return {
    id: String(dto.alertId),
    title: titleFor(dto.alertType, dto.message),
    description: dto.message?.trim() || 'No further detail was supplied.',
    vehicleId: dto.vehicleId ?? '',
    vehicle: dto.vehicleId || null,
    severity: severity(dto.severity),
    time: formatRelativeTime(dto.triggeredAt, now),
    triggeredAt: dto.triggeredAt ?? null,
    icon: iconFor(dto.alertType),
    status: status(dto),
    acknowledgedBy: dto.acknowledgedBy ?? null,
    acknowledgedAt: dto.acknowledgedAt ?? null,
    resolvedBy: dto.resolvedBy ?? null,
    resolvedAt: dto.resolvedAt ?? null,
    resolutionNotes: dto.resolutionNotes ?? null,
  }
}

export function createLiveAlertService(client: GatewayClient): AlertService {
  return {
    async list(options?: ServiceRequestOptions) {
      const alerts: FleetAlert[] = []
      for (let page = 0; page < MAX_PAGES; page++) {
        const response = await client.request('GET', '/alerts', alertPageSchema, {
          query: { page, size: PAGE_SIZE, sortBy: 'triggeredAt', sortDirection: 'DESC' },
          signal: options?.signal,
        })
        alerts.push(...response.content.map((dto) => toFleetAlert(dto)))
        if (finalListPage(response, page, MAX_PAGES)) break
      }
      return alerts
    },
    async get(id: string, options?: ServiceRequestOptions) {
      if (!/^\d+$/.test(id)) return null
      try {
        const dto = await client.request('GET', `/alerts/${id}`, alertDtoSchema, {
          signal: options?.signal,
        })
        return toFleetAlert(dto)
      } catch (error) {
        if (isApiError(error) && error.kind === 'not-found') return null
        throw error
      }
    },
    async transition(id: string, next: AlertState, context?: AlertTransitionContext) {
      if (next === 'active')
        throw capabilityUnavailable('Alerts cannot be reopened with the current backend contract.')
      if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n)
        throw capabilityUnavailable('This preview alert cannot be changed through the live API.')
      if (next === 'acknowledged') {
        await client.request('POST', `/alerts/${id}/acknowledge`, alertDtoSchema, {
          body: {},
        })
        return
      }
      const notes = context?.resolutionNotes?.trim() ?? ''
      if (notes.length < 10 || notes.length > 1000)
        throw capabilityUnavailable('Resolution notes must contain 10 to 1000 characters.')
      await client.request('POST', `/alerts/${id}/resolve`, alertDtoSchema, {
        body: { resolutionNotes: notes },
      })
    },
  }
}
