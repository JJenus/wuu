<script setup lang="ts">
import GeographicMap from '../../maps/components/GeographicMap.vue'
import type { MapScene } from '../../maps/domain/scene'
import type { Vehicle } from '../../fleet/domain/fleet'
import { formatDateTime } from '../../core/domain/time'
import { useRecordedReplay } from '../composables/useRecordedReplay'
import { formatElapsed } from '../domain/playback'

const props = defineProps<{ tripId: string; vehicle?: Vehicle }>()
const {
  query,
  history,
  duration,
  at,
  current,
  seconds,
  playing,
  speed,
  play,
  pause,
  restart,
  seek,
} = useRecordedReplay(() => props.tripId)
const follow = ref(false)
const map = ref<InstanceType<typeof GeographicMap>>()
function refresh() {
  pause()
  void query.refetch()
}
const scene = computed<MapScene>(() => ({
  markers:
    history.value && current.value
      ? [
          {
            id: history.value.vehicleId,
            label: `${props.vehicle?.name ?? history.value.vehicleId} · Recorded fix`,
            position: { lat: current.value.latitude, lng: current.value.longitude },
            status:
              current.value.speedKmh === null
                ? 'unknown'
                : current.value.speedKmh > 0
                  ? 'moving'
                  : 'idle',
          },
        ]
      : [],
  selectedId: history.value?.vehicleId,
  follow: follow.value,
}))
</script>

<template>
  <section class="panel trip-replay" aria-label="Recorded GPS replay">
    <div class="panel-heading">
      <div>
        <h2>Recorded GPS replay</h2>
        <p class="form-hint">
          {{
            history?.source === 'TRIP_ASSOCIATED'
              ? 'Recorded fixes associated with this trip · No inferred route'
              : history
                ? 'Vehicle fixes within the trip time window · Not confirmed trip membership'
                : 'Recorded GPS history'
          }}
        </p>
      </div>
      <UButton color="neutral" variant="outline" :loading="query.isFetching.value" @click="refresh"
        >Refresh history</UButton
      >
    </div>
    <div v-if="query.isFetching.value || query.isPending.value" class="section-body" role="status">
      Loading recorded GPS history…
    </div>
    <div v-else-if="query.isError.value" class="section-body" role="alert">
      <p>{{ query.error.value?.message ?? 'Could not read recorded GPS history.' }}</p>
      <p class="form-hint">Recorded positions are hidden until a successful refresh.</p>
    </div>
    <template v-else-if="history">
      <div class="section-body detail-stack">
        <p class="form-hint">
          {{ history.points.length }} recorded fixes · {{ formatDateTime(history.windowStart) }} –
          {{ formatDateTime(history.windowEnd) }}
        </p>
        <p v-if="history.truncated" role="status" class="info-banner">
          Partial history: only the first 10,000 fixes are shown. Missing portions are not a
          complete journey.
        </p>
        <p v-if="!history.points.length" class="muted">
          No recorded GPS fixes in this time window.
        </p>
        <template v-else>
          <div class="playback-timeline-heading">
            <span>{{ formatDateTime(new Date(at).toISOString()) }}</span>
            <span>{{ formatElapsed(seconds) }} / {{ formatElapsed(duration) }}</span>
          </div>
          <input
            type="range"
            class="playback-slider"
            aria-label="Recorded replay timeline"
            min="0"
            :max="duration"
            step="1"
            :value="seconds"
            @input="seek(Number(($event.target as HTMLInputElement).value))"
          />
          <div class="playback-controls">
            <UButton
              :disabled="duration === 0"
              :icon="playing ? 'i-lucide-pause' : 'i-lucide-play'"
              @click="playing ? pause() : play()"
              >{{ playing ? 'Pause replay' : 'Play replay' }}</UButton
            >
            <UButton color="neutral" variant="outline" @click="restart">Restart replay</UButton>
            <label class="playback-speed"
              >Speed<select
                v-model.number="speed"
                class="native-select"
                aria-label="Recorded replay speed"
              >
                <option v-for="rate in [1, 8, 32, 64]" :key="rate" :value="rate">
                  {{ rate }}×
                </option>
              </select></label
            >
            <UButton
              color="neutral"
              variant="outline"
              :aria-pressed="follow"
              @click="follow = !follow"
              >Follow vehicle</UButton
            >
            <UButton color="neutral" variant="outline" @click="map?.fit()"
              >Fit recorded position</UButton
            >
          </div>
          <div v-if="current" class="playback-readings" data-testid="recorded-fix">
            <span>{{ current.latitude.toFixed(5) }}, {{ current.longitude.toFixed(5) }}</span>
            <span>{{
              current.speedKmh === null ? 'Speed not recorded' : `${current.speedKmh} km/h`
            }}</span>
            <span>Fix recorded {{ formatDateTime(current.recordedAt) }}</span>
          </div>
          <p v-else role="status" class="info-banner">
            Recording gap: no fix within the previous two minutes. Position is unknown.
          </p>
          <p class="form-hint">
            Exact recorded samples only. The last fix is held for up to two minutes; no movement or
            road route is inferred between fixes. Playback starts paused and pauses in hidden tabs.
            Refresh to include later arrivals.
          </p>
        </template>
      </div>
      <GeographicMap
        v-if="history.points.length"
        ref="map"
        :scene="scene"
        label="Recorded GPS position map"
        @navigate="follow = false"
      />
    </template>
  </section>
</template>
