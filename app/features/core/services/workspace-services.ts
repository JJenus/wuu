import type { DataMode } from '../domain/data-mode'
import type { FleetService } from '../../fleet/services/fleet-service'
import type { AlertService } from '../../alerts/services/alert-service'
import type { TripService } from '../../trips/services/trip-service'
import type { GeofenceService } from '../../geofences/services/geofence-service'
import type { PreferencesService } from '../../settings/services/preferences-service'
import type { SubscriptionService } from '../../subscriptions/api/subscription-service'
import type { AccessService } from '../../auth/api/access-service'
import type { RoleService } from '../../roles/api/role-service'

export interface WorkspaceServices {
  mode: DataMode
  fleet: FleetService
  alerts: AlertService
  trips: TripService
  geofences: GeofenceService
  preferences: PreferencesService
  subscriptions: SubscriptionService
  access: AccessService
  roles: RoleService
}
