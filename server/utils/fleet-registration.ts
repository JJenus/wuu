import {
  trackerRegistrationWireSchema,
  vehicleRegistrationWireSchema,
} from '#shared/fleet-registration'

export function fleetRegistrationAllowed(method: string, target: string): boolean {
  return method === 'POST' && (target === '/api/v1/vehicles' || target === '/api/v1/trackers')
}

export function fleetRegistrationBody(target: string, body: unknown) {
  if (target === '/api/v1/vehicles') return vehicleRegistrationWireSchema.parse(body)
  if (target === '/api/v1/trackers') return trackerRegistrationWireSchema.parse(body)
  throw new Error('Unsupported fleet registration')
}
