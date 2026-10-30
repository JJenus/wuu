import { describe, expect, it, vi } from 'vitest'
import {
  createLiveAlertService,
  toFleetAlert,
} from '../../app/features/alerts/api/live-alert-service'
import type { GatewayClient } from '../../app/features/core/api/gateway-client'

describe('authenticated alert response adapter', () => {
  it('preserves persisted attribution and notes for operator inspection', () => {
    expect(
      toFleetAlert({
        alertId: 12,
        acknowledgedBy: '7',
        acknowledgedAt: '2026-10-30T12:00:00Z',
        resolvedBy: '8',
        resolvedAt: '2026-10-30T12:01:00Z',
        resolutionNotes: 'Reviewed and confirmed safe',
      }),
    ).toMatchObject({
      acknowledgedBy: '7',
      resolvedBy: '8',
      acknowledgedAt: '2026-10-30T12:00:00Z',
      resolvedAt: '2026-10-30T12:01:00Z',
      resolutionNotes: 'Reviewed and confirmed safe',
    })
  })
  it('uses the sealed session identity and sends no client actor', async () => {
    const request = vi.fn().mockResolvedValue({ alertId: 12, acknowledged: true })
    const service = createLiveAlertService({ request } as unknown as GatewayClient)
    await service.transition('12', 'acknowledged')
    expect(request).toHaveBeenLastCalledWith('POST', '/alerts/12/acknowledge', expect.anything(), {
      body: {},
    })
    await service.transition('12', 'resolved', {
      resolutionNotes: '  Reviewed and confirmed safe  ',
    })
    expect(request).toHaveBeenLastCalledWith('POST', '/alerts/12/resolve', expect.anything(), {
      body: { resolutionNotes: 'Reviewed and confirmed safe' },
    })
  })

  it('rejects invalid IDs, reopening and invalid notes before HTTP', async () => {
    const request = vi.fn()
    const service = createLiveAlertService({ request } as unknown as GatewayClient)
    for (const id of ['0', '-1', '01', '1e2', '9223372036854775808'])
      await expect(service.transition(id, 'acknowledged')).rejects.toMatchObject({
        kind: 'unavailable',
      })
    for (const notes of ['', 'short', ' '.repeat(12), 'x'.repeat(1001)])
      await expect(
        service.transition('12', 'resolved', { resolutionNotes: notes }),
      ).rejects.toMatchObject({ kind: 'unavailable' })
    await expect(service.transition('12', 'active')).rejects.toMatchObject({ kind: 'unavailable' })
    expect(request).not.toHaveBeenCalled()
  })
})
