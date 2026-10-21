// Coordinates are percentages on an illustrated canvas, not geographic positions.
export interface TrackPoint {
  seconds: number
  x: number
  y: number
  speed: number
}
export function sampleTrack(track: readonly TrackPoint[], seconds: number): TrackPoint | null {
  if (!track.length) return null
  const first = track[0]!,
    last = track[track.length - 1]!
  const time = Number.isFinite(seconds) ? seconds : 0
  if (time <= first.seconds) return { ...first }
  if (time >= last.seconds) return { ...last }
  const index = track.findIndex((p) => p.seconds >= time)
  const a = track[index - 1]!,
    b = track[index]!
  const ratio = (time - a.seconds) / (b.seconds - a.seconds)
  return {
    seconds: time,
    x: a.x + (b.x - a.x) * ratio,
    y: a.y + (b.y - a.y) * ratio,
    speed: a.speed + (b.speed - a.speed) * ratio,
  }
}
export function trackTrail(track: readonly TrackPoint[], seconds: number): TrackPoint[] {
  const current = sampleTrack(track, seconds)
  if (!current) return []
  return [...track.filter((p) => p.seconds < current.seconds).map((p) => ({ ...p })), current]
}
export function advancePlayback(
  time: number,
  delta: number,
  speed: number,
  duration: number,
  loop: boolean,
): number {
  if (![time, delta, speed, duration].every(Number.isFinite) || duration <= 0) return 0
  const next = Math.max(0, time) + Math.max(0, delta) * Math.max(0, speed)
  return loop ? next % duration : Math.min(duration, next)
}
export function formatElapsed(seconds: number): string {
  const value = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`
}
