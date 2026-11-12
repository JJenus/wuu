import { z } from 'zod'
import { geofenceRuleId, geofenceRuleInputSchema } from '#shared/geofence-rule'
import type { GatewayClient } from '../../core/api/gateway-client'
import { pagedResponseSchema } from '../../core/api/contracts'
import { finalListPage } from '../../core/api/list-completeness'
import { ApiError } from '../../core/domain/errors'
import type { GeofenceRuleService } from '../services/geofence-rule-service'
import type { GeofenceAlertRule } from '../domain/geofence-rule'

const rule = z.object({
  ruleKey: z.string().min(1).max(100),
  ruleName: z.string(),
  ruleType: z.literal('GEOFENCE'),
  enabled: z.boolean(),
  priority: z.number().int().positive(),
  parameters: z.object({
    geofenceId: geofenceRuleId,
    action: z.enum(['ENTRY', 'EXIT', 'BOTH']),
    vehicleIds: z.array(z.string().min(1).max(50)),
  }),
})
const pageSchema = pagedResponseSchema(rule)
export function createGeofenceRuleService(client: GatewayClient): GeofenceRuleService {
  return {
    async list(id, signal) {
      geofenceRuleId.parse(id)
      const result: GeofenceAlertRule[] = []
      for (let page = 0; page < 50; page++) {
        const response = await client.request('GET', '/geofence-rules', pageSchema, {
          signal,
          query: {
            page,
            size: 100,
            ruleType: 'GEOFENCE',
            sortBy: 'createdAt',
            sortDirection: 'DESC',
          },
        })
        result.push(...response.content.filter((item) => item.parameters.geofenceId === id))
        if (finalListPage(response, page, 50)) break
      }
      return result
    },
    async create(input) {
      const body = geofenceRuleInputSchema.parse(input)
      const saved = await client.request('POST', '/geofence-rules/templates/geofence', rule, {
        body,
      })
      if (saved.ruleKey !== body.ruleKey || saved.parameters.geofenceId !== body.geofenceId)
        throw new ApiError({
          kind: 'contract',
          message: 'Saved rule identity could not be verified. Reload before retrying.',
        })
      return saved
    },
    async setEnabled(key, enabled) {
      if (!/^[A-Za-z0-9_-]{1,100}$/.test(key))
        throw new ApiError({ kind: 'validation', message: 'Unsupported rule key.' })
      await client.request(
        'PATCH',
        `/geofence-rules/${encodeURIComponent(key)}/${enabled ? 'enable' : 'disable'}`,
        z.null().optional(),
        { body: {} },
      )
    },
  }
}
