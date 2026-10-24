import { capabilityUnavailable } from '../../core/domain/errors'
import type { PreferencesService } from '../services/preferences-service'

export function createLivePreferencesService(): PreferencesService {
  return {
    async get() {
      throw capabilityUnavailable(
        'Notification preferences are unavailable until the current-user preferences contract is integrated.',
      )
    },
    async save() {
      throw capabilityUnavailable(
        'Notification preferences are not writable until the preferences contract is verified.',
      )
    },
  }
}
