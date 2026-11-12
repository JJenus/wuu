import {
  trackerRegistrationWireSchema,
  vehicleRegistrationWireSchema,
} from '#shared/fleet-registration'

import { upstreamApi } from './upstream-api'

export function fleetRegistrationAllowed(method: string, target: string): boolean {
  return method === 'POST' && (target === upstreamApi.vehicles || target === upstreamApi.trackers)
}

export function fleetRegistrationBody(target: string, body: unknown) {
  if (target === upstreamApi.vehicles) return vehicleRegistrationWireSchema.parse(body)
  if (target === upstreamApi.trackers) return trackerRegistrationWireSchema.parse(body)
  throw new Error('Unsupported fleet registration')
}
