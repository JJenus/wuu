import { capabilityUnavailable } from '../../core/domain/errors'
import type { PreferencesService } from '../services/preferences-service'

export function createLivePreferencesService(): PreferencesService {
  return {
    async get() {
      throw capabilityUnavailable(
        'Notification preferences need a verified current-user session and preferences contract.',
      )
    },
    async save() {
      throw capabilityUnavailable(
        'Notification preferences are not writable until the current-user session contract is verified.',
      )
    },
  }
}
