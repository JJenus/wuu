import type { Trip } from './types'

// Quote every cell and neutralize spreadsheet formulas, including leading whitespace.
export function tripsCsv(trips: readonly Trip[]): string {
  const escape = (value: string | number) => {
    const text = String(value)
    const safe = /^\s*[-=+@\t\r]/.test(text) ? `'${text}` : text
    return `"${safe.replaceAll('"', '""')}"`
  }
  return [
    ['Trip', 'Vehicle', 'Driver', 'From', 'To', 'Distance km', 'Status'],
    ...trips.map((t) => [t.id, t.vehicle, t.driver, t.from, t.to, t.distance, t.status]),
  ]
    .map((row) => row.map(escape).join(','))
    .join('\r\n')
}
