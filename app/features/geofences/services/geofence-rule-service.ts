import type { GeofenceAlertRule, GeofenceRuleInput } from '../domain/geofence-rule'
export interface GeofenceRuleService {
  list(geofenceId: string, signal?: AbortSignal): Promise<GeofenceAlertRule[]>
  create(input: GeofenceRuleInput): Promise<GeofenceAlertRule>
  setEnabled(ruleKey: string, enabled: boolean): Promise<void>
}
