<script setup lang="ts">
import type { Vehicle } from '../domain/fleet'
import MapSearch from '../../maps/components/MapSearch.vue'
import GeographicMap from '../../maps/components/GeographicMap.vue'
import { useMapSearchProvider } from '../../maps/composables/useMapSearchProvider'
import { normalizeLocation, validCoordinate, type Coordinate } from '../../maps/domain/geographic'
import type { MapDataState, MapSearchService, MapSearchResult } from '../../maps/domain/types'
const props = defineProps<{
  vehicles: Vehicle[]
  full?: boolean
  focusId?: string | null
  follow?: boolean
  routePoints?: Coordinate[]
  trailPoints?: Coordinate[]
  searchService?: MapSearchService
  dataState?: MapDataState
}>()
const emit = defineEmits<{ select: [string | null]; navigate: []; retry: [] }>()
const map = ref<InstanceType<typeof GeographicMap>>()
const selected = ref<string | null>(props.focusId ?? null),
  notice = ref(''),
  searchPosition = ref<Coordinate | null>(null)
const legend = ref(true),
  layerPanel = ref(false),
  showLabels = ref(false),
  showRoute = ref(true)
const searchService = useMapSearchProvider(
  () => props.vehicles,
  () => props.searchService,
)
const unavailable = computed(
  () => props.dataState?.status === 'loading' || props.dataState?.status === 'error',
)
const plotted = computed(() =>
  props.vehicles.filter((v) => validCoordinate(v.position) && Math.abs(v.position.lat) <= 85.0511),
)
const missing = computed(() => props.vehicles.length - plotted.value.length)
const current = computed(() => props.vehicles.find((v) => v.id === selected.value))
watch(
  () => props.focusId,
  (id) => {
    if (id !== undefined) selected.value = id
  },
  { immediate: true },
)
watch(
  () => props.vehicles,
  (vehicles) => {
    if (!vehicles.length) {
      if (selected.value) select(null)
      return
    }
    if (!selected.value || !vehicles.some((v) => v.id === selected.value))
      select(vehicles[0]?.id ?? null)
  },
  { immediate: true },
)
function select(id: string | null) {
  selected.value = id
  emit('select', id)
}
function reset() {
  map.value?.reset()
  select(null)
  searchPosition.value = null
  emit('navigate')
}
function searchSelect(result: MapSearchResult) {
  const p = normalizeLocation(result.coordinate)
  if (result.kind === 'vehicle') select(result.id)
  if (!p || Math.abs(p.lat) > 85.0511) {
    notice.value = `No valid location for ${result.label}`
    return
  }
  searchPosition.value = result.kind === 'place' ? p : null
  map.value?.focus(p)
  notice.value = `Map focused on ${result.label}`
}
const scene = computed(() => ({
  markers: unavailable.value
    ? []
    : props.vehicles.map((v) => ({
        id: v.id,
        label: v.name,
        status: v.status,
        position: v.position,
      })),
  selectedId: selected.value,
  follow: props.follow,
  route: unavailable.value ? [] : props.routePoints,
  trail: unavailable.value ? [] : props.trailPoints,
  showLabels: showLabels.value,
  showRoute: showRoute.value,
  searchPosition: searchPosition.value,
}))
defineExpose({
  focusVehicle: (id: string) => {
    const vehicle = props.vehicles.find((v) => v.id === id)
    select(id)
    if (vehicle?.position && validCoordinate(vehicle.position)) map.value?.focus(vehicle.position)
  },
})
</script>
<template>
  <section
    class="fleet-map geographic-fleet-map"
    :class="{ 'full-map': full }"
    role="region"
    aria-label="Fleet map viewport"
  >
    <GeographicMap
      ref="map"
      :scene="scene"
      label="Fleet geographic map. Drag to pan, pinch or plus and minus to zoom; arrow keys pan; Home resets."
      @select="select"
      @navigate="emit('navigate')"
    />
    <div v-if="full && !unavailable" class="fleet-map-search" data-map-control>
      <MapSearch :service="searchService" @select="searchSelect" />
    </div>
    <span v-else class="map-location-chip">{{
      dataState?.source === 'live' ? 'Fleet positions' : 'Sample fleet · San Francisco'
    }}</span>
    <div class="map-controls" data-map-control>
      <button aria-label="Zoom in" :disabled="!map" @click="map?.zoom(1)">
        <UIcon name="i-lucide-plus" />
      </button>
      <button aria-label="Zoom out" :disabled="!map" @click="map?.zoom(-1)">
        <UIcon name="i-lucide-minus" />
      </button>
      <button
        aria-label="Fit visible vehicles"
        :disabled="!plotted.length || unavailable"
        @click="map?.fit()"
      >
        <UIcon name="i-lucide-scan" />
      </button>
      <span />
      <button aria-label="Reset map view" @click="reset">
        <UIcon name="i-lucide-locate-fixed" />
      </button>
      <button aria-label="Toggle map legend" :aria-pressed="legend" @click="legend = !legend">
        <UIcon name="i-lucide-layers-2" />
      </button>
      <button aria-label="Map layers" :aria-expanded="layerPanel" @click="layerPanel = !layerPanel">
        <UIcon name="i-lucide-settings-2" />
      </button>
    </div>
    <div v-if="layerPanel" class="map-layer-panel" data-map-control>
      <strong>Map layers</strong
      ><label><input v-model="showLabels" type="checkbox" />Show vehicle labels</label
      ><label
        ><input v-model="showRoute" type="checkbox" />{{
          dataState?.source === 'live' ? 'Show simulated trail' : 'Show sample route'
        }}</label
      ><button type="button" aria-label="Close map layers" @click="layerPanel = false">Done</button>
    </div>
    <div v-if="current && !unavailable" class="map-vehicle-popup" data-map-control>
      <span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
      <div>
        <strong
          >{{ current.name }}<span>{{ current.plate }}</span></strong
        ><small>{{ current.status }} · {{ current.speed }} km/h</small
        ><small v-if="!current.position">No location available</small
        ><small v-else>{{ current.updated }}</small>
      </div>
      <NuxtLink :to="`/vehicles/${current.id}`" :aria-label="`View ${current.name}`"
        ><UIcon name="i-lucide-arrow-up-right"
      /></NuxtLink>
      <button type="button" aria-label="Close vehicle details" @click="select(null)">
        <UIcon name="i-lucide-x" />
      </button>
    </div>
    <div v-if="legend" class="map-legend">
      <span><i class="moving" />Moving</span><span><i class="idle" />Idle</span
      ><span><i class="parked" />Parked</span><span><i class="offline" />Offline</span>
    </div>
    <div
      v-if="unavailable"
      class="map-data-state"
      :role="dataState?.status === 'error' ? 'alert' : 'status'"
    >
      <strong>{{
        dataState?.status === 'loading' ? 'Loading map data…' : 'Map data unavailable'
      }}</strong>
      <p>{{ dataState?.message || 'No sample fallback is used.' }}</p>
      <UButton
        v-if="dataState?.status === 'error'"
        color="neutral"
        variant="outline"
        @click="emit('retry')"
        >Retry map data</UButton
      >
    </div>
    <p v-else-if="!vehicles.length" class="map-empty-message" role="status">
      No vehicles in this view. Change the status filter or search for a vehicle or place.
    </p>
    <p v-else-if="missing" class="geo-missing-message" role="status">
      {{ missing }} vehicle{{ missing === 1 ? '' : 's' }} without a plottable location. No position
      was invented.
    </p>
    <p v-if="notice" class="sr-only" role="status">{{ notice }}</p>
    <span class="map-interaction-hint">Drag to pan · Focus + scroll to zoom · + / − · Home</span>
  </section>
</template>
