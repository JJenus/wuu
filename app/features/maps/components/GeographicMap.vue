<script setup lang="ts">
import type { MapScene } from '../domain/scene'
import type { Coordinate } from '../domain/geographic'
import type { Geometry } from '../../geofences/domain/geofence'
import { useGeographicMap } from '../composables/useGeographicMap'
const props = withDefaults(defineProps<{ scene: MapScene; label?: string; testId?: string }>(), {
  label: 'Geographic map',
})
const emit = defineEmits<{
  select: [string | null]
  navigate: []
  geometry: [Geometry]
  drawn: []
  'edit-start': []
  'edit-end': []
}>()
const host = ref<HTMLElement>()
const config = useRuntimeConfig()
const engine = useGeographicMap(host, () => props.scene, config.public.mapTiles, {
  select: (id) => emit('select', id),
  navigate: () => emit('navigate'),
  geometry: (g) => emit('geometry', g),
  drawn: () => emit('drawn'),
  editStart: () => emit('edit-start'),
  editEnd: () => emit('edit-end'),
})
const { ready, error, tileStatus, view } = engine
defineExpose({
  zoom: engine.zoom,
  reset: engine.reset,
  fit: engine.fitContent,
  fitGeometry: engine.fitGeometry,
  focus: (p: Coordinate, zoom?: number) => engine.focus(p, zoom),
  view,
})
</script>
<template>
  <div class="geographic-map-surface">
    <div
      ref="host"
      class="geographic-map-canvas"
      :data-testid="testId"
      :data-map-ready="ready"
      :data-map-center="`${view.lat},${view.lng}`"
      :data-map-zoom="view.zoom"
      role="group"
      :aria-label="label"
      tabindex="0"
    />
    <div v-if="error" class="geo-engine-error" role="alert">
      <p>{{ error }}</p>
      <UButton color="neutral" variant="outline" @click="engine.initialize"
        >Retry map initialization</UButton
      >
    </div>
    <div v-else-if="tileStatus === 'error'" class="geo-tile-notice" role="alert">
      <span>Map tiles unavailable. Vehicle data is unchanged.</span
      ><UButton color="neutral" variant="outline" size="xs" @click="engine.retryTiles"
        >Retry map tiles</UButton
      >
    </div>
    <span v-else-if="!ready || tileStatus === 'loading'" class="geo-loading" role="status"
      >Loading map…</span
    >
  </div>
</template>
