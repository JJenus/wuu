<script setup lang="ts">
import FleetMap from '../../fleet/components/FleetMap.vue'
import type { Vehicle } from '../../fleet/domain/fleet'
import { useTripReplay } from '../composables/useTrackingPreview'
import { formatElapsed } from '../domain/playback'
const props = defineProps<{ tripId: string; vehicle?: Vehicle; inProgress: boolean }>()
const { seconds, playing, speed, play, pause, restart, seek, track, duration, current, trail } =
  useTripReplay(() => props.tripId)
const follow = ref(false)
const replayVehicle = computed(() =>
  props.vehicle && current.value
    ? [
        {
          ...props.vehicle,
          position: { lat: current.value.lat, lng: current.value.lng },
          speed: Math.round(current.value.speed),
          status: current.value.speed > 0 ? ('moving' as const) : ('idle' as const),
        },
      ]
    : [],
)
const complete = computed(() => duration.value > 0 && seconds.value >= duration.value)
</script>
<template>
  <section class="panel trip-replay" aria-label="Trip replay">
    <div class="panel-heading">
      <div>
        <h2>Illustrative trip replay</h2>
        <p class="form-hint">Invented demo path · Not recorded GPS history</p>
      </div>
      <span class="preview-tag">{{
        complete ? 'Replay complete' : playing ? 'Playing sample' : 'Paused sample'
      }}</span>
    </div>
    <template v-if="current && vehicle">
      <FleetMap
        :vehicles="replayVehicle"
        :focus-id="vehicle.id"
        :follow="follow"
        :route-points="track"
        :trail-points="trail"
        @navigate="follow = false"
      />
      <div class="section-body detail-stack">
        <div class="playback-timeline-heading">
          <span>{{ inProgress ? 'Demo of elapsed portion only' : 'Sample playback position' }}</span
          ><span
            ><strong data-testid="replay-elapsed">{{ formatElapsed(seconds) }}</strong> /
            {{ formatElapsed(duration) }}</span
          >
        </div>
        <input
          type="range"
          class="playback-slider"
          aria-label="Replay timeline"
          :aria-valuetext="`${formatElapsed(seconds)} of ${formatElapsed(duration)}`"
          min="0"
          :max="duration"
          step="1"
          :value="seconds"
          @input="seek(Number(($event.target as HTMLInputElement).value))"
        />
        <div class="playback-controls">
          <UButton
            :icon="playing ? 'i-lucide-pause' : 'i-lucide-play'"
            @click="playing ? pause() : play()"
            >{{ playing ? 'Pause replay' : 'Play replay' }}</UButton
          >
          <UButton icon="i-lucide-rotate-ccw" color="neutral" variant="outline" @click="restart"
            >Restart replay</UButton
          >
          <label class="playback-speed"
            >Speed<select v-model.number="speed" class="native-select" aria-label="Replay speed">
              <option v-for="rate in [1, 8, 32, 64]" :key="rate" :value="rate">{{ rate }}×</option>
            </select></label
          >
          <UButton
            icon="i-lucide-locate-fixed"
            color="neutral"
            :variant="follow ? 'soft' : 'outline'"
            :aria-pressed="follow"
            @click="follow = !follow"
            >Follow vehicle</UButton
          >
        </div>
        <div class="playback-readings">
          <span
            ><UIcon name="i-lucide-gauge" />{{ Math.round(current.speed) }} km/h · Simulated</span
          ><span
            ><UIcon name="i-lucide-route" />{{ Math.round((seconds / duration) * 100) }}% of
            demo</span
          ><span>Teal: replayed · Dashed: sample path</span>
        </div>
        <p class="form-hint">
          {{
            inProgress
              ? 'This journey is in progress in the fixtures. The demo is not a prediction of its remaining route.'
              : 'This demonstration does not establish where the vehicle actually traveled.'
          }}
          Playback starts paused, pauses in hidden tabs, and does not change fleet records or
          exports.
        </p>
      </div>
    </template>
    <div v-else class="section-body">
      <p class="muted">No illustrative replay is available for this journey.</p>
    </div>
  </section>
</template>
