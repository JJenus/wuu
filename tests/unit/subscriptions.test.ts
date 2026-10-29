import { describe, expect, it, vi } from 'vitest'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import { createSubscriptionService } from '../../app/features/subscriptions/api/subscription-service'
import { subscriptionRequestAllowed } from '../../server/utils/wuu-gateway'

const id = '00000000-0000-4000-8000-000000000001'
const plan = {
  plan_id: id,
  version: 1,
  model: 'USAGE_TOKEN',
  cadence: null,
  meter: 'locations',
  usage_limit: 100,
  price: { amount: 2.5, currency: 'EUR' },
}
const enrollment = { id, plan_id: id, version: 1, status: 'PENDING', remaining_units: 0 }
function fixture(body: unknown, status = 200) {
  const fetchImpl = vi
    .fn<typeof fetch>()
    .mockResolvedValue(
      new Response(JSON.stringify(body), {
        status,
        headers: { 'content-type': 'application/json' },
      }),
    )
  return { fetchImpl, service: createSubscriptionService(createGatewayClient({ fetchImpl })) }
}
describe('subscription contracts and owner workflows', () => {
  it('normalizes catalog and owner records without inventing active access', async () => {
    const catalog = fixture([plan])
    expect((await catalog.service.catalog())[0]).toMatchObject({ planId: id, usageLimit: 100 })
    const owned = fixture([enrollment])
    expect((await owned.service.list())[0]).toMatchObject({ status: 'PENDING', remainingUnits: 0 })
  })
  it('sends a stable explicit enrollment key and pinned version in snake case', async () => {
    const { service, fetchImpl } = fixture(enrollment)
    await service.enroll(id, 1, 'retry-key')
    expect(fetchImpl).toHaveBeenCalledWith(
      '/api/_wuu/subscriptions',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ plan_id: id, version: 1, operation_key: 'retry-key' }),
      }),
    )
  })
  it('cancels only the selected UUID', async () => {
    const { service, fetchImpl } = fixture({ ...enrollment, status: 'CANCELED' })
    expect((await service.cancel(id)).status).toBe('CANCELED')
    expect(fetchImpl).toHaveBeenCalledWith(
      `/api/_wuu/subscriptions/${id}/cancel`,
      expect.objectContaining({ method: 'POST' }),
    )
    await expect(service.cancel('../catalog')).rejects.toThrow()
    expect(fetchImpl).toHaveBeenCalledTimes(1)
  })
  it.each([401, 403, 409, 503])(
    'retains HTTP %s instead of falling back to samples',
    async (status) => {
      await expect(fixture({ message: 'Denied' }, status).service.catalog()).rejects.toMatchObject({
        status,
      })
    },
  )
  it.each([
    { ...plan, version: 9007199254740992 },
    { ...plan, model: 'PAID' },
    { ...plan, model: 'PERIODIC', cadence: null },
    { ...plan, cadence: 'MONTHLY' },
    { ...plan, usage_limit: -1 },
  ])('rejects unsafe or inconsistent plan contracts', async (invalid) => {
    await expect(fixture([invalid]).service.catalog()).rejects.toMatchObject({ kind: 'contract' })
  })
  it('rejects unknown lifecycle states and unsafe balances', async () => {
    await expect(fixture([{ ...enrollment, status: 'PAID' }]).service.list()).rejects.toMatchObject(
      { kind: 'contract' },
    )
    await expect(
      fixture([{ ...enrollment, remaining_units: 9007199254740992 }]).service.list(),
    ).rejects.toMatchObject({ kind: 'contract' })
  })
})
describe('narrow subscription gateway policy', () => {
  it('allows only owner enrollment/cancel writes, not administration or arbitrary nested routes', () => {
    expect(subscriptionRequestAllowed('GET', '/api/v1/subscriptions/catalog')).toBe(true)
    expect(subscriptionRequestAllowed('POST', '/api/v1/subscriptions')).toBe(true)
    expect(subscriptionRequestAllowed('POST', `/api/v1/subscriptions/${id}/cancel`)).toBe(true)
    for (const suffix of [
      '/catalog',
      `/${id}/grants`,
      `/${id}/consumptions`,
      '/anything/cancel',
      `/${id}/cancel/extra`,
    ]) {
      expect(subscriptionRequestAllowed('POST', `/api/v1/subscriptions${suffix}`)).toBe(false)
    }
    expect(subscriptionRequestAllowed('DELETE', `/api/v1/subscriptions/${id}`)).toBe(false)
    expect(subscriptionRequestAllowed('GET', `/api/v1/subscriptions/${id}/grants`)).toBe(false)
  })
})
