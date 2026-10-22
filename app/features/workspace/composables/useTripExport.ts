import { tripsCsv } from '../domain/trip-export'
import type { Trip } from '../domain/types'

export function useTripExport() {
  const toast = useToast()
  function exportTrips(trips: readonly Trip[]) {
    let url: string | undefined
    try {
      url = URL.createObjectURL(new Blob([tripsCsv(trips)], { type: 'text/csv;charset=utf-8;' }))
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'wuu-preview-trips.csv'
      document.body.append(anchor)
      anchor.click()
      anchor.remove()
    } catch {
      toast.add({
        title: 'Unable to export preview trips',
        description: 'Please try again.',
        color: 'error',
      })
    } finally {
      if (url) URL.revokeObjectURL(url)
    }
  }
  return { exportTrips }
}
