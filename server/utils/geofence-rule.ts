import { z } from 'zod'
import { geofenceRuleInputSchema } from '#shared/geofence-rule'

export function ruleRequestAllowed(method: string, target: string) {
  if (method === 'GET') return target === '/alerts/rules'
  if (method === 'POST') return target === '/alerts/rules/templates/geofence'
  return (
    method === 'PATCH' && /^\/alerts\/rules\/[A-Za-z0-9_-]{1,100}\/(enable|disable)$/.test(target)
  )
}
export function ruleRequestBody(target: string, body: unknown) {
  if (target === '/alerts/rules/templates/geofence') {
    const input = geofenceRuleInputSchema.parse(body)
    return {
      rule_key: input.ruleKey,
      rule_name: input.ruleName,
      geofence_id: input.geofenceId,
      action: input.action,
      vehicle_ids: [...new Set(input.vehicleIds)],
      priority: input.priority,
      enabled: input.enabled,
    }
  }
  if (!ruleRequestAllowed('PATCH', target)) throw new Error('Unsupported rule request')
  return z.object({}).strict().parse(body)
}
