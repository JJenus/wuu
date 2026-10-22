<script setup lang="ts">
import GeofenceMap from './GeofenceMap.vue'
import {
  geometryArea,
  geometrySummary,
  eventOptions,
  type GeofenceDraft,
  type GeoPoint,
} from '../domain/geofence'
import { useGeofenceEditor } from '../composables/useGeofenceEditor'
const props = defineProps<{ initial: GeofenceDraft; id?: string }>()
const {
  draft,
  dirty,
  errors,
  busy,
  history,
  discardOpen,
  vehicles,
  setGeometry,
  beginMapEdit,
  endMapEdit,
  undo,
  answerDiscard,
  save,
} = useGeofenceEditor(props.initial, props.id)
const tool = ref<'pan' | 'circle' | 'polygon' | 'edit'>('pan')
const back = computed(() => (props.id ? `/geofences/${props.id}` : '/geofences'))
const search = ref('')
const filteredVehicles = computed(() =>
  vehicles.value.filter((v) =>
    `${v.name} ${v.plate}`.toLowerCase().includes(search.value.toLowerCase()),
  ),
)
function draw(type: 'circle' | 'polygon') {
  if (draft.geometry?.type !== type)
    setGeometry(type === 'polygon' ? { type: 'polygon', vertices: [] } : null)
  tool.value = type
}
function setCenter(axis: 'lat' | 'lng', event: Event) {
  if (draft.geometry?.type !== 'circle') return
  setGeometry({
    ...draft.geometry,
    center: { ...draft.geometry.center, [axis]: Number((event.target as HTMLInputElement).value) },
  })
}
function setRadius(event: Event) {
  if (draft.geometry?.type === 'circle')
    setGeometry({
      ...draft.geometry,
      radiusMeters: Number((event.target as HTMLInputElement).value),
    })
}
function setVertex(index: number, axis: 'lat' | 'lng', event: Event) {
  if (draft.geometry?.type !== 'polygon') return
  const n = Number((event.target as HTMLInputElement).value)
  setGeometry({
    ...draft.geometry,
    vertices: draft.geometry.vertices.map((p, i) => (i === index ? { ...p, [axis]: n } : p)),
  })
}
function removeVertex(index: number) {
  if (draft.geometry?.type === 'polygon')
    setGeometry({
      ...draft.geometry,
      vertices: draft.geometry.vertices.filter((_, i) => i !== index),
    })
}
function addVertex() {
  if (draft.geometry?.type === 'polygon' && draft.geometry.vertices.length < 50) {
    const last = draft.geometry.vertices.at(-1)
    const p: GeoPoint = last
      ? { lat: Math.max(37.69, last.lat - 0.001), lng: last.lng }
      : { lat: 37.76, lng: -122.43 }
    setGeometry({ ...draft.geometry, vertices: [...draft.geometry.vertices, p] })
  }
}
function toggleVehicle(id: string, checked: boolean) {
  draft.vehicleIds = checked
    ? [...new Set([...draft.vehicleIds, id])]
    : draft.vehicleIds.filter((v) => v !== id)
}
function useDefaultCircle() {
  setGeometry({ type: 'circle', center: { lat: 37.76, lng: -122.43 }, radiusMeters: 500 })
  tool.value = 'edit'
}
useHead(() => ({ title: `${props.id ? 'Edit' : 'Create'} geofence — Wuu` }))
</script>

<template>
  <div class="detail-page">
    <UiPageHeading
      :title="id ? 'Fine-tune your boundary.' : 'Give your places a boundary.'"
      description="Draw a little context around the places that matter."
      :eyebrow="id ? 'EDIT GEOFENCE' : 'CREATE GEOFENCE'"
      ><span class="subtle-badge">Session-only preview</span></UiPageHeading
    >
    <div class="info-banner">
      <UIcon name="i-lucide-flask-conical" />
      <p>
        Changes are saved only in this preview session and reset on reload. The geographic map
        supports geographic boundaries. No server-side geofence, tracking rule, or notification will
        be activated.
      </p>
    </div>
    <form novalidate @submit.prevent="save">
      <div
        v-if="Object.keys(errors).length"
        id="geofence-errors"
        role="alert"
        class="editor-errors"
        tabindex="-1"
      >
        <strong>Check your geofence before saving.</strong>
        <ul>
          <li v-for="(message, key) in errors" :key="key">
            <a v-if="key !== 'save'" :href="`#zone-${key}`">{{ message }}</a
            ><span v-else>{{ message }}</span>
          </li>
        </ul>
      </div>
      <div class="geofence-editor-grid">
        <div class="detail-stack">
          <section class="panel">
            <div class="panel-heading">
              <h2>1. Draw your boundary</h2>
              <span class="preview-tag">{{ geometrySummary(draft.geometry) }}</span>
            </div>
            <div id="zone-geometry" class="geometry-tools" tabindex="-1">
              <UButton
                type="button"
                icon="i-lucide-move"
                color="neutral"
                :variant="tool === 'pan' ? 'soft' : 'ghost'"
                :aria-pressed="tool === 'pan'"
                @click="tool = 'pan'"
                >Pan map</UButton
              >
              <UButton
                type="button"
                icon="i-lucide-circle"
                color="neutral"
                :variant="tool === 'circle' ? 'soft' : 'ghost'"
                :aria-pressed="tool === 'circle'"
                @click="draw('circle')"
                >Draw circle</UButton
              >
              <UButton
                type="button"
                icon="i-lucide-pentagon"
                color="neutral"
                :variant="tool === 'polygon' ? 'soft' : 'ghost'"
                :aria-pressed="tool === 'polygon'"
                @click="draw('polygon')"
                >Draw polygon</UButton
              >
              <UButton
                type="button"
                icon="i-lucide-undo-2"
                aria-label="Undo map change"
                color="neutral"
                variant="ghost"
                :disabled="!history.length"
                @click="undo"
              />
              <UButton
                type="button"
                icon="i-lucide-eraser"
                color="neutral"
                variant="ghost"
                :disabled="!draft.geometry"
                @click="setGeometry(null)"
                >Clear</UButton
              >
            </div>
            <p class="geometry-instructions" role="status">
              {{
                tool === 'circle'
                  ? 'Click or tap the canvas to place a circle. Drag its center or radius handle to adjust.'
                  : tool === 'polygon'
                    ? 'Click or tap to add vertices in order, then finish the polygon. Maximum 50 points.'
                    : tool === 'edit'
                      ? 'Drag the boundary handles to adjust. Focus a handle and use arrow keys for precise changes.'
                      : 'Drag the canvas to pan. Zoom and fit controls do not change your boundary.'
              }}
            </p>
            <GeofenceMap
              :model-value="draft.geometry"
              editable
              :tool="tool"
              @update:model-value="setGeometry"
              @drawn="tool = 'edit'"
              @edit-start="beginMapEdit"
              @edit-end="endMapEdit"
            />
            <div class="section-body detail-stack">
              <div class="geometry-summary">
                <span
                  ><strong>{{
                    draft.geometry?.type === 'polygon'
                      ? 'Polygon'
                      : draft.geometry?.type === 'circle'
                        ? 'Circle'
                        : 'No shape yet'
                  }}</strong
                  ><small
                    >{{ (geometryArea(draft.geometry) / 1_000_000).toFixed(3) }} km² · Approximate
                    area</small
                  ></span
                ><UButton
                  v-if="tool === 'polygon'"
                  type="button"
                  :disabled="
                    draft.geometry?.type !== 'polygon' || draft.geometry.vertices.length < 3
                  "
                  @click="tool = 'edit'"
                  >Finish polygon</UButton
                >
              </div>
              <template v-if="draft.geometry?.type === 'circle'">
                <label class="muted"
                  >Adjust radius<input
                    type="range"
                    class="radius-slider"
                    min="50"
                    max="5000"
                    step="10"
                    :value="draft.geometry.radiusMeters"
                    aria-label="Radius slider"
                    @input="setRadius"
                /></label>
                <div class="geometry-fields">
                  <label
                    >Center latitude<input
                      :value="draft.geometry.center.lat"
                      type="number"
                      step="0.0001"
                      min="37.69"
                      max="37.83"
                      aria-label="Center latitude"
                      @input="setCenter('lat', $event)" /></label
                  ><label
                    >Center longitude<input
                      :value="draft.geometry.center.lng"
                      type="number"
                      step="0.0001"
                      min="-122.53"
                      max="-122.34"
                      aria-label="Center longitude"
                      @input="setCenter('lng', $event)" /></label
                  ><label
                    >Radius (meters)<input
                      :value="draft.geometry.radiusMeters"
                      type="number"
                      min="50"
                      max="5000"
                      step="1"
                      aria-label="Radius (meters)"
                      @input="setRadius"
                  /></label>
                </div>
              </template>
              <template v-else-if="draft.geometry?.type === 'polygon'">
                <div
                  class="vertex-list"
                  role="region"
                  aria-label="Polygon coordinates"
                  tabindex="0"
                >
                  <div v-for="(p, i) in draft.geometry.vertices" :key="i" class="vertex-row">
                    <span class="step-number">{{ i + 1 }}</span
                    ><label
                      >Latitude<input
                        :value="p.lat"
                        type="number"
                        step="0.0001"
                        :aria-label="`Vertex ${i + 1} latitude`"
                        @input="setVertex(i, 'lat', $event)" /></label
                    ><label
                      >Longitude<input
                        :value="p.lng"
                        type="number"
                        step="0.0001"
                        :aria-label="`Vertex ${i + 1} longitude`"
                        @input="setVertex(i, 'lng', $event)" /></label
                    ><UButton
                      type="button"
                      :aria-label="`Remove vertex ${i + 1}`"
                      color="neutral"
                      variant="ghost"
                      icon="i-lucide-x"
                      @click="removeVertex(i)"
                    />
                  </div>
                </div>
                <UButton
                  type="button"
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-plus"
                  :disabled="draft.geometry.vertices.length >= 50"
                  @click="addVertex"
                  >Add vertex using coordinates</UButton
                >
              </template>
              <div v-else class="boundary-empty">
                <p>Choose a drawing tool, or start with a circle using coordinates.</p>
                <UButton type="button" color="neutral" variant="outline" @click="useDefaultCircle"
                  >Set circle using coordinates</UButton
                >
              </div>
              <p v-if="errors.geometry" class="form-error">{{ errors.geometry }}</p>
              <p class="form-hint">
                Preview editing limits: latitude 37.69–37.83, longitude −122.53–−122.34. Circle
                radius: 50–5,000 m. Circles and polygon area use a local metric approximation, not
                surveyed geometry.
              </p>
            </div>
          </section>
        </div>
        <div class="detail-stack">
          <section class="panel">
            <div class="panel-heading"><h2>2. Make it recognizable</h2></div>
            <div class="section-body detail-stack editor-fields">
              <label for="zone-name">Geofence name <span class="muted">(required)</span></label
              ><UInput
                id="zone-name"
                aria-label="Geofence name"
                aria-required="true"
                v-model="draft.name"
                placeholder="e.g. Waterfront depot"
                maxlength="100"
                :aria-invalid="!!errors.name"
              />
              <label for="zone-description">Description</label
              ><textarea
                id="zone-description"
                v-model="draft.description"
                rows="3"
                maxlength="500"
                placeholder="What makes this place important?"
              />
              <label for="zone-location">Location label</label
              ><UInput
                id="zone-location"
                v-model="draft.location"
                placeholder="e.g. Dogpatch, San Francisco"
                maxlength="100"
              />
              <label for="zone-events">Preview event setting</label
              ><select id="zone-events" v-model="draft.events">
                <option v-for="event in eventOptions" :key="event">{{ event }}</option>
              </select>
              <USwitch
                v-model="draft.active"
                label="Enabled in preview"
                description="Stores a sample setting only; no monitoring runs."
              />
            </div>
          </section>
          <section id="zone-vehicleIds" class="panel" tabindex="-1">
            <div class="panel-heading">
              <h2>3. Link sample vehicles</h2>
              <span class="preview-tag">{{ draft.vehicleIds.length }} selected</span>
            </div>
            <div class="section-body detail-stack">
              <p class="muted">
                Choose at least one vehicle. Assignment is not a live containment result.
              </p>
              <UInput
                v-model="search"
                icon="i-lucide-search"
                aria-label="Find vehicles to assign"
                placeholder="Search your fleet…"
              />
              <div class="vehicle-assignment-list">
                <label
                  v-for="vehicle in filteredVehicles"
                  :key="vehicle.id"
                  class="vehicle-assignment"
                  ><input
                    type="checkbox"
                    :checked="draft.vehicleIds.includes(vehicle.id)"
                    :aria-label="`Assign ${vehicle.name}`"
                    @change="toggleVehicle(vehicle.id, ($event.target as HTMLInputElement).checked)"
                  /><span
                    ><strong>{{ vehicle.name }}</strong
                    ><small>{{ vehicle.plate }}</small></span
                  ></label
                >
                <p v-if="!filteredVehicles.length" class="muted">No vehicles match your search.</p>
              </div>
            </div>
          </section>
        </div>
      </div>
      <div class="editor-savebar">
        <span class="muted"
          >{{ dirty ? 'Unsaved preview changes' : 'No unsaved changes' }} · Resets on reload</span
        >
        <div class="flex gap-3">
          <UButton
            type="button"
            color="neutral"
            variant="outline"
            :disabled="busy"
            @click="navigateTo(back)"
            >Cancel editing</UButton
          ><UButton type="submit" icon="i-lucide-check" :loading="busy">{{
            id ? 'Save preview changes' : 'Create preview geofence'
          }}</UButton>
        </div>
      </div>
    </form>
    <UModal
      v-model:open="discardOpen"
      title="Leave your changes behind?"
      description="Your unsaved geofence changes will be discarded. The saved preview record will stay unchanged."
      ><template #body
        ><div class="flex flex-wrap gap-3">
          <UButton color="neutral" variant="outline" @click="answerDiscard(false)"
            >Keep editing</UButton
          ><UButton color="error" @click="answerDiscard(true)">Discard changes</UButton>
        </div></template
      ></UModal
    >
  </div>
</template>
