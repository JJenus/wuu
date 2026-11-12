import { describe, expect, it, vi } from 'vitest'
import { ruleRequestAllowed, ruleRequestBody } from '../../server/utils/geofence-rule'
import { createGeofenceRuleService } from '../../app/features/geofences/api/geofence-rule-service'
import { createGatewayClient } from '../../app/features/core/api/gateway-client'
import type { GeofenceRuleInput } from '../../app/features/geofences/domain/geofence-rule'

const input: GeofenceRuleInput = {
  ruleKey: 'depot_rule',
  ruleName: 'Depot entry',
  geofenceId: '9',
  action: 'BOTH',
  vehicleIds: ['VH-1'],
  priority: 2,
  enabled: true,
}
const dto = {
  rule_key: 'depot_rule',
  rule_name: 'Depot entry',
  rule_type: 'GEOFENCE',
  priority: 2,
  enabled: true,
  parameters: { geofenceId: '9', action: 'BOTH', vehicleIds: ['VH-1'] },
}
const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { 'content-type': 'application/json' } })

describe('geofence rule boundary', () => {
  it('admits only collection reads, geofence template creation and exact transitions', () => {
    expect(ruleRequestAllowed('GET', '/api/v1/alerts/rules')).toBe(true)
    expect(ruleRequestAllowed('POST', '/api/v1/alerts/rules/templates/geofence')).toBe(true)
    expect(ruleRequestAllowed('PATCH', '/api/v1/alerts/rules/depot_rule/disable')).toBe(true)
    for (const path of [
      '/api/v1/alerts/rules',
      '/api/v1/alerts/rules/batch',
      '/api/v1/alerts/rules/templates/overspeed',
    ])
      expect(ruleRequestAllowed('POST', path)).toBe(false)
    for (const path of [
      '/api/v1/alerts/rules/key',
      '/api/v1/alerts/rules/key/enable/extra',
      '/api/v1/alerts/rules/key/reset',
    ])
      expect(ruleRequestAllowed('PATCH', path)).toBe(false)
    expect(ruleRequestAllowed('DELETE', '/api/v1/alerts/rules/key')).toBe(false)
  })
  it('maps strict fields without accepting actors, arbitrary parameters or tenant overrides', () => {
    expect(ruleRequestBody('/api/v1/alerts/rules/templates/geofence', input)).toEqual({
      rule_key: 'depot_rule',
      rule_name: 'Depot entry',
      geofence_id: '9',
      action: 'BOTH',
      vehicle_ids: ['VH-1'],
      priority: 2,
      enabled: true,
    })
    for (const extra of [
      { organizationId: 99 },
      { createdBy: 'spoofed' },
      { parameters: {} },
      { geofence_id: '8' },
    ])
      expect(() =>
        ruleRequestBody('/api/v1/alerts/rules/templates/geofence', { ...input, ...extra }),
      ).toThrow()
    expect(ruleRequestBody('/api/v1/alerts/rules/depot_rule/disable', {})).toEqual({})
    expect(() =>
      ruleRequestBody('/api/v1/alerts/rules/depot_rule/enable', { enabled: true }),
    ).toThrow()
  })
  it.each([
    { geofenceId: '0' },
    { geofenceId: '9223372036854775808' },
    { vehicleIds: [] },
    { vehicleIds: [''] },
    { action: 'COMMAND' },
    { ruleKey: 'a/b' },
    { priority: 0 },
  ])('rejects unsupported input %j', (fields) => {
    expect(() =>
      ruleRequestBody('/api/v1/alerts/rules/templates/geofence', { ...input, ...fields }),
    ).toThrow()
  })
})

describe('geofence rule adapter', () => {
  it('creates and validates the persisted rule without retrying', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () => json(dto, 201))
    const service = createGeofenceRuleService(createGatewayClient({ fetchImpl }))
    await expect(service.create(input)).resolves.toMatchObject({
      ruleKey: 'depot_rule',
      enabled: true,
    })
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(JSON.parse(fetchImpl.mock.calls[0]![1]!.body as string)).toEqual(input)
  })
  it('lists only the selected boundary and preserves read failures', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () =>
      json({
        content: [
          dto,
          { ...dto, rule_key: 'other', parameters: { ...dto.parameters, geofenceId: '10' } },
        ],
        last: true,
        empty: false,
      }),
    )
    const service = createGeofenceRuleService(createGatewayClient({ fetchImpl }))
    await expect(service.list('9')).resolves.toHaveLength(1)
    fetchImpl.mockResolvedValue(json({ message: 'Denied' }, 403))
    await expect(service.list('9')).rejects.toMatchObject({ kind: 'forbidden' })
  })
  it('uses empty PATCH bodies and never sends arbitrary updates', async () => {
    const fetchImpl = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    await createGeofenceRuleService(createGatewayClient({ fetchImpl })).setEnabled(
      'depot_rule',
      false,
    )
    expect(fetchImpl.mock.calls[0]![0]).toBe('/api/_wuu/geofence-rules/depot_rule/disable')
    expect(fetchImpl.mock.calls[0]![1]!.method).toBe('PATCH')
    expect(fetchImpl.mock.calls[0]![1]!.body).toBe('{}')
  })
})
