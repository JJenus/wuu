<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import FleetMap from '~/features/fleet/components/FleetMap.vue'
import { useTrackingPreview } from '~/features/tracking/composables/useTrackingPreview'
import { formatElapsed } from '~/features/tracking/domain/playback'
const source = useDataSource()
const { vehicles, summary, isPending, isError, refetch } = useWorkspace()
const mapDataState = computed(() => ({
  source: source.mode,
  status: isPending.value
    ? ('loading' as const)
    : isError.value
      ? ('error' as const)
      : ('ready' as const),
  message: isError.value ? 'Unable to load map vehicles. Retry the workspace request.' : undefined,
}))
const { positions, seconds, playing, speed, play, pause, restart, trail } =
  useTrackingPreview(vehicles)
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
        A simulated movement overlay on live vehicle starting positions. It is not live GPS and
        never writes to fleet records. Only moving vehicles animate.
      </p>
    </div>
    <section class="panel tracking-panel">
      <div class="panel-heading">
        <div>
          <h2>Simulated tracking</h2>
          <p class="form-hint">Opt-in movement preview · 2-minute loop</p>
        </div>
        <span class="tracking-state"
          ><span role="status">{{ playing ? 'Simulation running' : 'Simulation paused' }}</span> ·
          {{ formatElapsed(seconds) }}</span
        >
      </div>
      <div class="tracking-toolbar playback-controls">
        <UButton
          :icon="playing ? 'i-lucide-pause' : 'i-lucide-play'"
          @click="playing ? pause() : play()"
          >{{ playing ? 'Pause simulation' : 'Start simulation' }}</UButton
        >
        <UButton icon="i-lucide-rotate-ccw" color="neutral" variant="outline" @click="restart"
          >Reset simulation</UButton
        >
        <label class="playback-speed"
          >Speed<select v-model.number="speed" class="native-select" aria-label="Simulation speed">
            <option v-for="rate in [1, 2, 4, 8]" :key="rate" :value="rate">{{ rate }}×</option>
          </select></label
        >
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
      <FleetMap
        ref="fleetMap"
        :vehicles="visible"
        :focus-id="selectedId"
        :follow="follow"
        :route-points="[]"
        :trail-points="selectedId ? trail(selectedId) : []"
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
            ><UiStatusBadge :status="vehicle.status" /><small>{{
              vehicle.status === 'offline'
                ? 'No simulated updates'
                : `${vehicle.speed} km/h · Simulated`
            }}</small></span
          >
        </button>
        <p v-if="!visible.length" class="muted">No vehicles match this filter.</p>
      </div>
    </section>
  </div>
</template>
