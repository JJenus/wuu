import { describe, expect, it } from 'vitest'
import { alertTransitionAllowed, alertTransitionBody } from '../../server/utils/alert-transition'

describe('alert mutation boundary', () => {
  it('allows only positive signed-long IDs and exact POST transition paths', () => {
    for (const action of ['acknowledge', 'resolve']) {
      expect(alertTransitionAllowed('POST', `/alerts/1/${action}`)).toBe(true)
      expect(alertTransitionAllowed('POST', `/alerts/9223372036854775807/${action}`)).toBe(true)
      for (const id of ['0', '-1', '01', '1e2', '9223372036854775808', 'bulk', 'geofences'])
        expect(alertTransitionAllowed('POST', `/alerts/${id}/${action}`)).toBe(false)
      for (const method of ['GET', 'PUT', 'PATCH', 'DELETE'])
        expect(alertTransitionAllowed(method, `/alerts/1/${action}`)).toBe(false)
    }
    expect(alertTransitionAllowed('POST', '/alerts/1/resolve/extra')).toBe(false)
  })

  it('accepts no caller-selected actor, organization or unrelated body fields', () => {
    expect(alertTransitionBody('/alerts/1/acknowledge', {})).toEqual({})
    for (const body of [{ acknowledgedBy: 'forged' }, { orgId: 1 }, [], null])
      expect(() => alertTransitionBody('/alerts/1/acknowledge', body)).toThrow()
    expect(() =>
      alertTransitionBody('/alerts/1/resolve', {
        resolvedBy: 'forged',
        resolutionNotes: 'Reviewed and confirmed',
      }),
    ).toThrow()
  })

  it('trims notes and enforces the backend 10–1000 character contract', () => {
    expect(
      alertTransitionBody('/alerts/1/resolve', { resolutionNotes: '  Reviewed and confirmed  ' }),
    ).toEqual({ resolution_notes: 'Reviewed and confirmed' })
    for (const value of ['', 'short', ' '.repeat(15), 'x'.repeat(1001), 7, null])
      expect(() => alertTransitionBody('/alerts/1/resolve', { resolutionNotes: value })).toThrow()
    expect(() => alertTransitionBody('/alerts/bulk/resolve', {})).toThrow()
  })
})
