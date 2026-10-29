import type { DataMode } from '../domain/data-mode'
import type { WorkspaceServices } from './workspace-services'
import type { GatewayClient } from '../api/gateway-client'
import { createPreviewServices } from './preview-services'
import { createLiveFleetService } from '../../fleet/api/live-fleet-service'
import { createLiveAlertService } from '../../alerts/api/live-alert-service'
import { createLiveTripService } from '../../trips/api/live-trip-service'
import { createLiveGeofenceService } from '../../geofences/api/live-geofence-service'
import { createLivePreferencesService } from '../../settings/api/live-preferences-service'
import { createSubscriptionService } from '../../subscriptions/api/subscription-service'
import { createAccessService } from '../../auth/api/access-service'
import { createRoleService } from '../../roles/api/role-service'

export function createServices(mode: DataMode, client: GatewayClient): WorkspaceServices {
  if (mode === 'preview') return createPreviewServices()
  return {
    mode: 'live',
    fleet: createLiveFleetService(client),
    alerts: createLiveAlertService(client),
    trips: createLiveTripService(client),
    geofences: createLiveGeofenceService(client),
    preferences: createLivePreferencesService(),
    subscriptions: createSubscriptionService(client),
    access: createAccessService(client),
    roles: createRoleService(client),
  }
}
