import type { Preferences } from '../../workspace/domain/types'
import type { ServiceRequestOptions } from '../../fleet/services/fleet-service'

export interface PreferencesService {
  get(options?: ServiceRequestOptions): Promise<Preferences>
  save(input: Preferences): Promise<void>
}
