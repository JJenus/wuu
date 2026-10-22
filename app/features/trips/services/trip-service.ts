import type { Trip } from '../../workspace/domain/types'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'

export interface TripService {
  list(options?: ServiceRequestOptions): Promise<Trip[]>
  get(id: string, options?: ServiceRequestOptions): Promise<Trip | null>
}
