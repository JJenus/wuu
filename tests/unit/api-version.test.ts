import { describe, expect, it } from 'vitest'
import { upstreamPath } from '../../server/utils/wuu-gateway'
import { ruleRequestAllowed } from '../../server/utils/geofence-rule'
import { upstreamApi, upstreamSuffix } from '../../server/utils/upstream-api'

describe('canonical upstream API version', () => {
  it('maps alerting and notification resources to v1 only', () => {
    expect(upstreamPath('alerts')).toBe('/api/v1/alerts')
    expect(upstreamPath('geofences')).toBe('/api/v1/alerts/geofences')
    expect(upstreamPath('geofence-rules')).toBe('/api/v1/alerts/rules')
    expect(upstreamPath('notifications')).toBe('/api/v1/notifications')
  })

  it('admits canonical rule reads and rejects the old upstream path', () => {
    expect(ruleRequestAllowed('GET', '/api/v1/alerts/rules')).toBe(true)
    expect(ruleRequestAllowed('GET', '/alerts/rules')).toBe(false)
  })

  it('matches resource boundaries without accepting similarly prefixed resources', () => {
    expect(upstreamSuffix(upstreamApi.subscriptions, upstreamApi.subscriptions)).toBe('')
    expect(upstreamSuffix(`${upstreamApi.subscriptions}/catalog`, upstreamApi.subscriptions)).toBe(
      '/catalog',
    )
    expect(
      upstreamSuffix(`${upstreamApi.subscriptions}-evil`, upstreamApi.subscriptions),
    ).toBeNull()
    expect(upstreamSuffix('/alerts', upstreamApi.alerts)).toBeNull()
  })
})
