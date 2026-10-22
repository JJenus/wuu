<script setup lang="ts">
import GeographicMap from '../../maps/components/GeographicMap.vue'
import MapSearch from '../../maps/components/MapSearch.vue'
import { useMapSearchProvider } from '../../maps/composables/useMapSearchProvider'
import { normalizeLocation } from '../../maps/domain/geographic'
import type { MapSearchResult } from '../../maps/domain/types'
import type { Geometry } from '../domain/geofence'
const props = withDefaults(
  defineProps<{
    modelValue: Geometry | null
    editable?: boolean
    tool?: 'pan' | 'circle' | 'polygon' | 'edit'
  }>(),
  { editable: false, tool: 'pan' },
)
const emit = defineEmits<{
  'update:modelValue': [Geometry]
  drawn: []
  'edit-start': []
  'edit-end': []
}>()
const map = ref<InstanceType<typeof GeographicMap>>()
const searchService = useMapSearchProvider(() => []),
  notice = ref('')
const scene = computed(() => ({
  markers: [],
  geometry: props.modelValue,
  editable: props.editable,
  tool: props.tool,
}))
function searchSelect(result: MapSearchResult) {
  const p = normalizeLocation(result.coordinate)
  if (p) {
    map.value?.focus(p)
    notice.value = `Map focused on ${result.label}`
  }
}
</script>
<template>
  <div class="geofence-map-wrap">
    <div class="geofence-map-search">
      <MapSearch :service="searchService" label="Search geofence map" @select="searchSelect" />
      <p v-if="notice" class="sr-only" role="status">{{ notice }}</p>
    </div>
    <div class="geofence-map-toolbar">
      <span><UIcon name="i-lucide-map-pin" />Geographic boundary · Sample data</span>
      <div class="flex gap-1">
        <UButton
          aria-label="Zoom in"
          icon="i-lucide-plus"
          color="neutral"
          variant="ghost"
          size="sm"
          @click="map?.zoom(1)"
        /><UButton
          aria-label="Zoom out"
          icon="i-lucide-minus"
          color="neutral"
          variant="ghost"
          size="sm"
          @click="map?.zoom(-1)"
        /><UButton
          aria-label="Fit boundary"
          icon="i-lucide-scan"
          color="neutral"
          variant="ghost"
          size="sm"
          @click="map?.fitGeometry()"
        /><UButton
          aria-label="Reset map"
          icon="i-lucide-rotate-ccw"
          color="neutral"
          variant="ghost"
          size="sm"
          @click="map?.reset()"
        />
      </div>
    </div>
    <div class="geographic-geofence-map">
      <GeographicMap
        ref="map"
        :scene="scene"
        test-id="geofence-canvas"
        label="Geofence geographic map. Arrow keys pan; plus and minus zoom. Use numeric fields to draw without a pointer."
        @geometry="emit('update:modelValue', $event)"
        @drawn="emit('drawn')"
        @edit-start="emit('edit-start')"
        @edit-end="emit('edit-end')"
      />
    </div>
    <div class="geofence-map-caption">
      <span>Real map · Boundary changes are session-only</span
      ><span>Drag to pan · + / − to zoom</span>
    </div>
  </div>
</template>
