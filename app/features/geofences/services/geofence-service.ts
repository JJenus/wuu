import type { Geofence } from '../../workspace/domain/types'
import type { GeofenceDraft } from '../domain/geofence'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'
import type { GeofenceRuleService } from './geofence-rule-service'

export interface GeofenceService {
  rules: GeofenceRuleService
  list(options?: ServiceRequestOptions): Promise<Geofence[]>
  get(id: string, options?: ServiceRequestOptions): Promise<Geofence | null>
  create(input: GeofenceDraft): Promise<string>
  update(id: string, input: GeofenceDraft): Promise<void>
}
