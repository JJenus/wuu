import { z } from 'zod'
import type { GatewayClient } from '../../core/api/gateway-client'

const uuid = z.string().uuid()
const positiveInteger = z.number().int().positive().max(Number.MAX_SAFE_INTEGER)
const balance = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER)
export const planSchema = z
  .object({
    planId: uuid,
    version: positiveInteger,
    model: z.enum(['USAGE_TOKEN', 'PERIODIC']),
    cadence: z.enum(['MONTHLY', 'YEARLY']).nullable(),
    meter: z.string().min(1).max(100),
    usageLimit: positiveInteger,
    price: z.object({
      amount: z.number().nonnegative().finite(),
      currency: z.string().regex(/^[A-Z]{3}$/),
    }),
  })
  .refine((plan) => (plan.model === 'PERIODIC') === (plan.cadence !== null), 'Inconsistent cadence')
export const enrollmentSchema = z
  .object({
    id: uuid,
    planId: uuid,
    version: positiveInteger,
    status: z.enum(['PENDING', 'TRIALING', 'ADMIN_GRANTED', 'CANCELED']),
    remainingUnits: balance,
  })
  .refine(
    (item) => !['PENDING', 'CANCELED'].includes(item.status) || item.remainingUnits === 0,
    'Inactive subscriptions cannot expose spendable units',
  )
export type SubscriptionPlan = z.infer<typeof planSchema>
export type Enrollment = z.infer<typeof enrollmentSchema>
export interface SubscriptionService {
  catalog(signal?: AbortSignal): Promise<SubscriptionPlan[]>
  list(signal?: AbortSignal): Promise<Enrollment[]>
  enroll(planId: string, version: number, operationKey: string): Promise<Enrollment>
  cancel(id: string): Promise<Enrollment>
}
export function createSubscriptionService(client: GatewayClient): SubscriptionService {
  return {
    catalog: (signal) =>
      client.request('GET', '/subscriptions/catalog', z.array(planSchema), { signal }),
    list: (signal) =>
      client.request('GET', '/subscriptions', z.array(enrollmentSchema), { signal }),
    async enroll(planId, version, operationKey) {
      const body = z
        .object({
          plan_id: uuid,
          version: positiveInteger,
          operation_key: z.string().trim().min(1).max(200),
        })
        .parse({ plan_id: planId, version, operation_key: operationKey })
      return client.request('POST', '/subscriptions', enrollmentSchema, { body })
    },
    async cancel(id) {
      return client.request('POST', `/subscriptions/${uuid.parse(id)}/cancel`, enrollmentSchema, {
        body: {},
      })
    },
  }
}
