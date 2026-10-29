import { z } from 'zod'
import type { GatewayClient } from '../../core/api/gateway-client'

const identifier = z.number().int().positive().max(Number.MAX_SAFE_INTEGER)
const accessSchema = z.object({
  userId: identifier,
  organizationId: identifier.nullable(),
  permissions: z.array(z.string().regex(/^[a-z][a-z0-9_]*\.[a-z][a-z0-9_]*$/)).max(1000),
  accessExpiresAt: z.iso.datetime(),
})
export type EffectiveAccess = z.infer<typeof accessSchema>
export interface AccessService {
  get(signal?: AbortSignal): Promise<EffectiveAccess>
}
export function createAccessService(client: GatewayClient): AccessService {
  return { get: (signal) => client.request('GET', '/access', accessSchema, { signal }) }
}
