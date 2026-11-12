export { geofenceRuleInputSchema } from '#shared/geofence-rule'
export type { GeofenceRuleInput } from '#shared/geofence-rule'

export interface GeofenceAlertRule {
  ruleKey: string
  ruleName: string
  enabled: boolean
  priority: number
  parameters: { geofenceId: string; action: 'ENTRY' | 'EXIT' | 'BOTH'; vehicleIds: string[] }
}
