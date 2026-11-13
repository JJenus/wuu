import { vehicleProfileWireSchema, trackerProfileWireSchema, trackerReassignmentWireSchema } from '#shared/fleet-management'
import { vehicleRegistrationSchema } from '#shared/fleet-registration'
import { upstreamApi, upstreamSuffix } from './upstream-api'

function operation(target: string) {
  const vehicle = upstreamSuffix(target, upstreamApi.vehicles)?.slice(1)
  if (vehicle && vehicleRegistrationSchema.shape.vehicleId.safeParse(vehicle).success) return 'vehicle'
  const tracker = upstreamSuffix(target, upstreamApi.trackers)?.slice(1).split('/')
  if (tracker?.length === 2 && vehicleRegistrationSchema.shape.vehicleId.safeParse(tracker[0]).success) {
    if (tracker[1] === 'profile') return 'profile'
    if (tracker[1] === 'reassign') return 'reassign'
  }
  return null
}
export function fleetManagementAllowed(method: string, target: string): boolean {
  const kind = operation(target)
  return (kind === 'vehicle' && method === 'PUT') || (kind === 'profile' && method === 'PATCH') || (kind === 'reassign' && method === 'POST')
}
export function fleetManagementBody(target: string, body: unknown) {
  switch (operation(target)) {
    case 'vehicle': return vehicleProfileWireSchema.parse(body)
    case 'profile': return trackerProfileWireSchema.parse(body)
    case 'reassign': return trackerReassignmentWireSchema.parse(body)
    default: throw new Error('Unsupported fleet operation')
  }
}
