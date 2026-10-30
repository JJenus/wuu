import type { AlertState, FleetAlert } from '../../workspace/domain/types'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'

export interface AlertTransitionContext {
  resolutionNotes?: string
}

export interface AlertService {
  list(options?: ServiceRequestOptions): Promise<FleetAlert[]>
  get(id: string, options?: ServiceRequestOptions): Promise<FleetAlert | null>
  transition(id: string, status: AlertState, context?: AlertTransitionContext): Promise<void>
}
