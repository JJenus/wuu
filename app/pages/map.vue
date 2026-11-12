<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import FleetMap from '~/features/fleet/components/FleetMap.vue'
import { useMapTracking } from '~/features/tracking/composables/useMapTracking'
import { locationFreshness } from '~/features/tracking/domain/live-tracking'
import { formatDateTime } from '~/features/core/domain/time'
import { formatElapsed } from '~/features/tracking/domain/playback'
const source = useDataSource()
const {
  positions,
  summary,
  isPending,
  isError,
  isFetching,
  error,
  refetch,
  refreshedAt,
  paused,
  toggleUpdates,
  seconds,
  playing,
  speed,
  play,
  pause,
  restart,
  trail,
} = useMapTracking()
const mapDataState = computed(() => ({
  source: source.mode,
  status: isPending.value
    ? ('loading' as const)
    : isError.value
      ? ('error' as const)
      : ('ready' as const),
  message: isError.value ? error.value : undefined,
}))
const selectedId = ref<string | null>(null)
const follow = ref(false)
const fleetMap = ref<InstanceType<typeof FleetMap>>()
const status = ref('all')
const visible = computed(() =>
  positions.value.filter((v) => status.value === 'all' || v.status === status.value),
)
const selected = computed(() => visible.value.find((v) => v.id === selectedId.value))
watch(
  visible,
  (vehicles) => {
    if (!vehicles.some((v) => v.id === selectedId.value)) selectedId.value = vehicles[0]?.id ?? null
  },
  { immediate: true },
)
useHead({ title: 'Fleet map — Wuu' })
</script>

<template>
  <div>
    <UiPageHeading
      title="The whole picture."
      description="Find a vehicle, check its status, and see where the day takes it."
      eyebrow="FLEET MAP"
      ><select v-model="status" class="native-select" aria-label="Map vehicle status">
        <option value="all">All {{ summary.total }} vehicles</option>
        <option value="moving">Moving</option>
        <option value="idle">Idle</option>
        <option value="parked">Parked</option>
        <option value="offline">Offline</option>
      </select></UiPageHeading
    >
    <div class="info-banner">
      <UIcon name="i-lucide-info" />
      <p v-if="source.isPreview">
        Real geographic map with sample vehicle positions—not live GPS. Only sample moving vehicles
        animate; idle, parked, and offline vehicles stay still. Nothing is written to fleet records.
      </p>
      <p v-else>
        Saved GPS positions from the backend, refreshed every 10 seconds while this tab is visible.
        No simulated movement or inferred paths. A refreshed snapshot does not prove a device is
        connected.
      </p>
    </div>
    <section class="panel tracking-panel">
      <div class="panel-heading">
        <div>
          <h2>{{ source.isPreview ? 'Simulated tracking' : 'Live fleet positions' }}</h2>
          <p class="form-hint">
            {{
              source.isPreview
                ? 'Opt-in movement preview · 2-minute loop'
                : 'Latest saved positions · not a streaming GPS feed'
            }}
          </p>
        </div>
        <span v-if="source.isPreview" class="tracking-state"
          ><span role="status">{{ playing ? 'Simulation running' : 'Simulation paused' }}</span> ·
          {{ formatElapsed(seconds) }}</span
        >
        <span v-else class="tracking-state" role="status">
          {{
            isError
              ? 'Position refresh failed'
              : paused
                ? 'Updates paused'
                : isFetching
                  ? 'Refreshing positions…'
                  : 'Automatic updates enabled'
          }}
        </span>
      </div>
      <div class="tracking-toolbar playback-controls">
        <UButton
          v-if="source.isPreview"
          :icon="playing ? 'i-lucide-pause' : 'i-lucide-play'"
          @click="playing ? pause() : play()"
          >{{ playing ? 'Pause simulation' : 'Start simulation' }}</UButton
        >
        <UButton
          v-if="source.isPreview"
          icon="i-lucide-rotate-ccw"
          color="neutral"
          variant="outline"
          @click="restart"
          >Reset simulation</UButton
        >
        <label v-if="source.isPreview" class="playback-speed"
          >Speed<select v-model.number="speed" class="native-select" aria-label="Simulation speed">
            <option v-for="rate in [1, 2, 4, 8]" :key="rate" :value="rate">{{ rate }}×</option>
          </select></label
        >
        <template v-if="!source.isPreview">
          <UButton color="neutral" variant="outline" :disabled="isFetching" @click="toggleUpdates">
            {{ paused ? 'Resume updates' : 'Pause updates' }}
          </UButton>
          <UButton icon="i-lucide-refresh-cw" :loading="isFetching" @click="refetch()"
            >Refresh positions</UButton
          >
        </template>
        <UButton
          icon="i-lucide-locate-fixed"
          color="neutral"
          :variant="follow ? 'soft' : 'outline'"
          :aria-pressed="follow"
          :disabled="!selected"
          @click="follow = !follow"
          >Follow selected vehicle</UButton
        >
      </div>
      <p v-if="!source.isPreview && refreshedAt" class="form-hint section-body">
        Snapshot refreshed {{ formatDateTime(refreshedAt, { second: '2-digit' }) }}. Location
        timestamps below describe the GPS fix, not the refresh time. Older than five minutes is
        stale.
      </p>
      <p v-if="!source.isPreview && isError" class="form-error section-body" role="alert">
        {{ error }} Positions are hidden until a successful refresh.
      </p>
      <FleetMap
        ref="fleetMap"
        :vehicles="visible"
        :focus-id="selectedId"
        :follow="follow"
        :route-points="[]"
        :trail-points="source.isPreview && selectedId ? trail(selectedId) : []"
        :data-state="mapDataState"
        full
        @select="selectedId = $event"
        @navigate="follow = false"
        @retry="refetch()"
      />
    </section>
    <section class="panel mt-6">
      <div class="panel-heading">
        <h2>Vehicles in this view</h2>
        <span class="preview-tag">{{ visible.length }} vehicles in view</span>
      </div>
      <div class="tracking-vehicle-grid section-body">
        <button
          v-for="vehicle in visible"
          :key="vehicle.id"
          class="tracking-vehicle-card"
          :class="{ active: selectedId === vehicle.id }"
          :aria-pressed="selectedId === vehicle.id"
          @click="fleetMap?.focusVehicle(vehicle.id)"
        >
          <span
            ><strong>{{ vehicle.name }}</strong
            ><small>{{ vehicle.driver }}</small></span
          ><span
            ><UiStatusBadge :status="vehicle.status" /><small v-if="source.isPreview">{{
              vehicle.status === 'offline'
                ? 'No simulated updates'
                : `${vehicle.speed} km/h · Simulated`
            }}</small></span
          >
          <span v-if="!source.isPreview">
            <small
              >{{ locationFreshness(vehicle) }} ·
              {{ formatDateTime(vehicle.observedAt, { second: '2-digit' }) }}</small
            >
            <small v-if="vehicle.position"
              >{{ vehicle.position.lat.toFixed(5) }}, {{ vehicle.position.lng.toFixed(5) }} · Last
              reported {{ vehicle.speed }} km/h</small
            >
          </span>
        </button>
        <p v-if="!visible.length" class="muted">No vehicles match this filter.</p>
      </div>
    </section>
  </div>
</template>
