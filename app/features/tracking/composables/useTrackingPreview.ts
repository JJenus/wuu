import type { Vehicle } from '../../fleet/domain/fleet'
import { movementTrack, tripTrack } from '../api/preview-tracks'
import { sampleTrack, trackTrail } from '../domain/playback'
import { usePlayback } from './usePlayback'

export function useTrackingPreview(vehicles: MaybeRefOrGetter<Vehicle[]>) {
  const playback = usePlayback(120, true)
  const tracks = computed(() => new Map(toValue(vehicles).map((v) => [v.id, movementTrack(v)])))
  const positions = computed(() =>
    toValue(vehicles).map((v) => {
      const point = sampleTrack(tracks.value.get(v.id) ?? [], playback.seconds.value)
      return point
        ? { ...v, position: { lat: point.lat, lng: point.lng }, speed: Math.round(point.speed) }
        : { ...v }
    }),
  )
  function trail(id: string) {
    return trackTrail(tracks.value.get(id) ?? [], playback.seconds.value)
  }
  return { ...playback, positions, trail }
}
export function useTripReplay(id: MaybeRefOrGetter<string>) {
  const track = computed(() => tripTrack(toValue(id)))
  const duration = computed(() => track.value.at(-1)?.seconds ?? 0)
  const playback = usePlayback(duration)
  const current = computed(() => sampleTrack(track.value, playback.seconds.value))
  const trail = computed(() => trackTrail(track.value, playback.seconds.value))
  return { ...playback, track, duration, current, trail }
}
