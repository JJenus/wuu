import type { Trip } from '../../workspace/domain/types'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'
import type { TripHistory } from '../../tracking/domain/recorded-history'

export interface TripService {
  list(options?: ServiceRequestOptions): Promise<Trip[]>
  get(id: string, options?: ServiceRequestOptions): Promise<Trip | null>
  history(id: string, options?: ServiceRequestOptions): Promise<TripHistory>
}
