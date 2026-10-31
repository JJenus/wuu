<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { useWorkspaceRecord } from '~/features/workspace/composables/useWorkspaceRecord'
import GeofenceMap from '~/features/geofences/components/GeofenceMap.vue'
import { geometryArea, geometrySummary } from '~/features/geofences/domain/geofence'
const route = useRoute()
const source = useDataSource()
const { vehicles } = useWorkspace('geofences')
const {
  data: zone,
  isPending,
  isError,
  error,
  refetch,
} = useWorkspaceRecord('geofences', () => String(route.params.id))
const circle = computed(() => {
  const geometry = zone.value?.geometry
  return geometry?.type === 'circle' ? geometry : null
})
const polygon = computed(() => {
  const geometry = zone.value?.geometry
  return geometry?.type === 'polygon' ? geometry : null
})
const members = computed(() => vehicles.value.filter((v) => zone.value?.vehicleIds.includes(v.id)))
useHead(() => ({ title: `${zone.value?.name || 'Geofence not found'} — Wuu` }))
</script>

<template>
  <div class="detail-page">
    <NuxtLink to="/geofences" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to geofences</NuxtLink
    >
    <UiAsyncState
      :pending="isPending && !zone"
      :error="isError && !zone ? error : null"
      loading-label="Loading this geofence…"
      error-title="This geofence could not load"
      @retry="refetch"
    >
      <template v-if="zone">
        <UiPageHeading :title="zone.name" :description="zone.description" eyebrow="GEOFENCE DETAILS"
          ><UButton
            :to="`/geofences/${zone.id}/edit`"
            icon="i-lucide-pencil"
            :disabled="!source.isPreview"
            :title="
              source.isPreview
                ? undefined
                : 'Live geofence editing is disabled until the update contract preserves geometry and assignments'
            "
            >Edit geofence</UButton
          ></UiPageHeading
        >
        <div class="info-banner">
          <UIcon name="i-lucide-info" />
          <p v-if="source.isPreview">
            Session-only geofence geometry on a real geographic basemap. No server monitoring or
            notification is active. Reloading resets your changes.
          </p>
          <p v-else>
            This boundary is loaded from the live service. No entry or exit monitoring is claimed
            unless the backend supplies verified event data.
          </p>
        </div>
        <div class="detail-layout">
          <div class="detail-stack">
            <section class="panel">
              <div class="panel-heading">
                <h2>A little context for this place</h2>
                <span class="preview-tag">{{ zone.id }}</span>
              </div>
              <GeofenceMap :key="JSON.stringify(zone.geometry)" :model-value="zone.geometry" />
              <div class="section-body">
                <dl class="detail-info mt-6">
                  <div>
                    <dt>Location</dt>
                    <dd>{{ zone.location }}</dd>
                  </div>
                  <div>
                    <dt>Boundary</dt>
                    <dd>{{ geometrySummary(zone.geometry) }}</dd>
                  </div>
                  <div>
                    <dt>Monitoring configuration</dt>
                    <dd>{{ zone.events }}</dd>
                  </div>
                  <div>
                    <dt>Data source</dt>
                    <dd>{{ source.isPreview ? 'Preview fixture' : 'Live service' }}</dd>
                  </div>
                  <div>
                    <dt>Approximate area</dt>
                    <dd>{{ (geometryArea(zone.geometry) / 1_000_000).toFixed(3) }} km²</dd>
                  </div>
                  <div>
                    <dt>Configuration</dt>
                    <dd>{{ zone.active ? 'Enabled' : 'Disabled' }}</dd>
                  </div>
                </dl>
                <details class="mt-6">
                  <summary class="text-link cursor-pointer">Boundary coordinates</summary>
                  <dl v-if="circle" class="detail-info mt-6">
                    <div>
                      <dt>Center latitude</dt>
                      <dd>{{ circle.center.lat.toFixed(6) }}</dd>
                    </div>
                    <div>
                      <dt>Center longitude</dt>
                      <dd>{{ circle.center.lng.toFixed(6) }}</dd>
                    </div>
                    <div>
                      <dt>Radius</dt>
                      <dd>{{ circle.radiusMeters }} meters</dd>
                    </div>
                  </dl>
                  <div
                    v-else-if="polygon"
                    class="table-scroll mt-6"
                    role="region"
                    aria-label="Boundary coordinate records"
                    tabindex="0"
                  >
                    <table class="data-table">
                      <thead>
                        <tr>
                          <th>Vertex</th>
                          <th>Latitude</th>
                          <th>Longitude</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(point, i) in polygon.vertices" :key="i">
                          <td>{{ i + 1 }}</td>
                          <td>{{ point.lat.toFixed(6) }}</td>
                          <td>{{ point.lng.toFixed(6) }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-else class="muted mt-6">No boundary geometry is available.</p>
                </details>
              </div>
            </section>
            <section class="panel">
              <div class="panel-heading">
                <h2>Linked vehicles</h2>
                <span class="preview-tag">{{ members.length }} records</span>
              </div>
              <div class="section-body detail-stack">
                <p class="muted">
                  {{
                    source.isPreview
                      ? 'Illustrative membership, not live containment.'
                      : 'Assignment data supplied by the live geofence record.'
                  }}
                </p>
                <NuxtLink
                  v-for="vehicle in members"
                  :key="vehicle.id"
                  :to="`/vehicles/${vehicle.id}`"
                  :aria-label="`View ${vehicle.name}`"
                  class="related-record"
                  ><span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
                  <div>
                    <strong>{{ vehicle.name }}</strong>
                    <p>{{ vehicle.driver ?? 'Driver not assigned' }} · {{ vehicle.plate }}</p>
                  </div>
                  <UIcon name="i-lucide-arrow-up-right"
                /></NuxtLink>
                <p v-if="!members.length" class="muted">No vehicles are linked to this zone.</p>
              </div>
            </section>
          </div>
          <section class="panel self-start">
            <div class="panel-heading"><h2>Before this becomes operational</h2></div>
            <div class="section-body detail-stack">
              <p v-if="source.isPreview" class="muted">
                You can edit this boundary in the preview. Live entry/exit history and notification
                delivery remain unavailable.
              </p>
              <p v-else class="muted">
                Live editing, entry/exit history, and notification delivery remain unavailable until
                the backend contracts and authorization model are verified.
              </p>
              <ul class="readiness-list">
                <li>Verify geometry and coordinate format.</li>
                <li>Enforce organization and vehicle scope on the server.</li>
                <li>Confirm entry, exit, and dwell event rules.</li>
                <li>Connect an authorized notification channel.</li>
              </ul>
              <NuxtLink to="/geofences" class="text-link"
                >Explore other places<UIcon name="i-lucide-arrow-right"
              /></NuxtLink>
            </div>
          </section>
        </div>
      </template>
      <UiEmptyState
        v-else
        title="Geofence not found"
        description="This zone is not part of the current workspace."
        icon="i-lucide-map-pin-off"
        ><UButton to="/geofences">Back to geofences</UButton></UiEmptyState
      >
    </UiAsyncState>
  </div>
</template>
