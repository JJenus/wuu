import { describe, expect, it } from 'vitest'
import { tripsCsv } from '../../app/features/workspace/domain/trip-export'
import type { Trip } from '../../app/features/workspace/domain/types'

describe('preview trip CSV', () => {
  const trip: Trip = { id: 'TR-1', vehicleId: 'VH-1', vehicle: 'Van "A"', driver: 'Maya', from: 'Depot, SF', to: 'SOMA', started: '10:42 AM', duration: '24 min', distance: 12.8, status: 'completed' }
  it('quotes cells and exports only the supplied records', () => {
    const csv = tripsCsv([trip])
    expect(csv.split('\r\n')).toHaveLength(2)
    expect(csv).toContain('"Van ""A"""')
    expect(csv).toContain('"Depot, SF"')
    expect(csv).toContain('"12.8"')
    expect(tripsCsv([]).split('\r\n')).toHaveLength(1)
  })
  it('neutralizes spreadsheet formulas even after leading whitespace', () => {
    for (const driver of ['=1+1', '+1', '-1', '@SUM(A1)', '\t=1', '  =1', '\r=1']) {
      expect(tripsCsv([{ ...trip, driver }])).toContain(`"'${driver}"`)
    }
  })
})
