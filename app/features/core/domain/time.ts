export type IsoInstant = string

export function parseInstant(value: string | null | undefined): Date | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatRelativeTime(
  value: string | null | undefined,
  now: number = Date.now(),
): string {
  const date = parseInstant(value)
  if (!date) return 'No update recorded'
  const seconds = Math.round((now - date.getTime()) / 1000)
  if (seconds < -5) return 'Clock skew detected'
  if (Math.abs(seconds) < 10) return 'Just now'
  if (seconds < 60) return `${Math.max(1, seconds)} sec ago`
  const minutes = Math.round(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} hr ago`
  const days = Math.round(hours / 24)
  return days === 1 ? 'Yesterday' : `${days} days ago`
}

export function formatDateTime(
  value: string | null | undefined,
  options: Intl.DateTimeFormatOptions = {},
): string {
  const date = parseInstant(value)
  if (!date) return 'Not recorded'
  // API instants are displayed in UTC with a fixed locale so SSR and the browser render
  // exactly the same text. A deployment can override both through `options`.
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZone: 'UTC',
    timeZoneName: 'short',
    ...options,
  }).format(date)
}

export function formatDuration(
  start: string,
  end?: string | null,
  now: number = Date.now(),
): string {
  const started = parseInstant(start)
  if (!started) return 'Not recorded'
  const finished = parseInstant(end)
  const seconds = Math.max(0, Math.round(((finished?.getTime() ?? now) - started.getTime()) / 1000))
  if (seconds < 60) return `${seconds} sec`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  return `${hours} hr ${minutes % 60} min`
}

export const DEFAULT_STALE_AFTER_MS = 5 * 60 * 1000

export function isStale(
  value: string | null | undefined,
  now: number = Date.now(),
  thresholdMs: number = DEFAULT_STALE_AFTER_MS,
): boolean {
  const date = parseInstant(value)
  return !date || now - date.getTime() > thresholdMs
}
