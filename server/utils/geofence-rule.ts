import { z } from 'zod'
import { upstreamApi, upstreamSuffix } from './upstream-api'
import { geofenceRuleInputSchema } from '#shared/geofence-rule'

export function ruleRequestAllowed(method: string, target: string) {
  const suffix = upstreamSuffix(target, upstreamApi.alertRules)
  if (suffix === null) return false
  if (method === 'GET') return suffix === ''
  if (method === 'POST') return suffix === '/templates/geofence'
  return method === 'PATCH' && /^\/[A-Za-z0-9_-]{1,100}\/(enable|disable)$/.test(suffix)
}
export function ruleRequestBody(target: string, body: unknown) {
  if (target === `${upstreamApi.alertRules}/templates/geofence`) {
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
