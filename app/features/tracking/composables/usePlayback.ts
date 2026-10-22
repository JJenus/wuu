import { advancePlayback } from '../domain/playback'

// Explicitly opt-in animation. Hidden tabs pause instead of catching up on return.
export function usePlayback(duration: MaybeRefOrGetter<number>, loop = false) {
  const seconds = ref(0),
    playing = ref(false),
    speed = ref(1)
  let frame: number | undefined
  let previous: number | undefined
  function cancelFrame() {
    if (frame !== undefined) cancelAnimationFrame(frame)
    frame = undefined
    previous = undefined
  }
  function pause() {
    playing.value = false
    cancelFrame()
  }
  function tick(now: number) {
    if (!playing.value) return
    if (previous !== undefined)
      seconds.value = advancePlayback(
        seconds.value,
        Math.min((now - previous) / 1000, 0.25),
        speed.value,
        toValue(duration),
        loop,
      )
    previous = now
    if (!loop && seconds.value >= toValue(duration)) {
      pause()
      return
    }
    frame = requestAnimationFrame(tick)
  }
  function play() {
    if (playing.value || toValue(duration) <= 0) return
    if (seconds.value >= toValue(duration)) seconds.value = 0
    playing.value = true
    previous = undefined
    frame = requestAnimationFrame(tick)
  }
  function restart() {
    pause()
    seconds.value = 0
  }
  function seek(value: number) {
    pause()
    seconds.value = Number.isFinite(value) ? Math.max(0, Math.min(toValue(duration), value)) : 0
  }
  function visibility() {
    if (document.hidden) pause()
  }
  watch(() => toValue(duration), restart)
  onMounted(() => document.addEventListener('visibilitychange', visibility))
  onUnmounted(() => {
    pause()
    document.removeEventListener('visibilitychange', visibility)
  })
  return { seconds, playing, speed, play, pause, restart, seek }
}
