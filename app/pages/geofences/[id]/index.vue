<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import GeofenceMap from '~/features/geofences/components/GeofenceMap.vue'
import { geometryArea, geometrySummary } from '~/features/geofences/domain/geofence'
const route = useRoute()
const { geofences, vehicles } = useWorkspace()
const zone = computed(() => geofences.value.find((z) => z.id === route.params.id))
const members = computed(() => vehicles.value.filter((v) => zone.value?.vehicleIds.includes(v.id)))
useHead(() => ({ title: `${zone.value?.name || 'Geofence not found'} — Wuu` }))
</script>

<template>
  <div class="detail-page">
    <NuxtLink to="/geofences" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to geofences</NuxtLink
    >
    <template v-if="zone">
      <UiPageHeading :title="zone.name" :description="zone.description" eyebrow="GEOFENCE DETAILS"
        ><UButton :to="`/geofences/${zone.id}/edit`" icon="i-lucide-pencil"
          >Edit geofence</UButton
        ></UiPageHeading
      >
      <div class="info-banner">
        <UIcon name="i-lucide-info" />
        <p>
          Session-only geofence geometry on a real geographic basemap. No server monitoring or
          notification is active. Reloading resets your changes.
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
                  <dt>Proposed monitoring</dt>
                  <dd>{{ zone.events }}</dd>
                </div>
                <div>
                  <dt>Data source</dt>
                  <dd>Preview fixture</dd>
                </div>
                <div>
                  <dt>Approximate area</dt>
                  <dd>{{ (geometryArea(zone.geometry) / 1_000_000).toFixed(3) }} km²</dd>
                </div>
                <div>
                  <dt>Configuration</dt>
                  <dd>{{ zone.active ? 'Enabled in preview' : 'Disabled in preview' }}</dd>
                </div>
              </dl>
              <details class="mt-6">
                <summary class="text-link cursor-pointer">Boundary coordinates</summary>
                <dl v-if="zone.geometry.type === 'circle'" class="detail-info mt-6">
                  <div>
                    <dt>Center latitude</dt>
                    <dd>{{ zone.geometry.center.lat.toFixed(6) }}</dd>
                  </div>
                  <div>
                    <dt>Center longitude</dt>
                    <dd>{{ zone.geometry.center.lng.toFixed(6) }}</dd>
                  </div>
                  <div>
                    <dt>Radius</dt>
                    <dd>{{ zone.geometry.radiusMeters }} meters</dd>
                  </div>
                </dl>
                <div
                  v-else
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
                      <tr v-for="(point, i) in zone.geometry.vertices" :key="i">
                        <td>{{ i + 1 }}</td>
                        <td>{{ point.lat.toFixed(6) }}</td>
                        <td>{{ point.lng.toFixed(6) }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </details>
            </div>
          </section>
          <section class="panel">
            <div class="panel-heading">
              <h2>Sample vehicles</h2>
              <span class="preview-tag">{{ members.length }} records</span>
            </div>
            <div class="section-body detail-stack">
              <p class="muted">Illustrative membership, not live containment.</p>
              <NuxtLink
                v-for="vehicle in members"
                :key="vehicle.id"
                :to="`/vehicles/${vehicle.id}`"
                :aria-label="`View ${vehicle.name}`"
                class="related-record"
                ><span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
                <div>
                  <strong>{{ vehicle.name }}</strong>
                  <p>{{ vehicle.driver }} · {{ vehicle.plate }}</p>
                </div>
                <UIcon name="i-lucide-arrow-up-right"
              /></NuxtLink>
              <p v-if="!members.length" class="muted">
                No sample vehicles are linked to this zone.
              </p>
            </div>
          </section>
        </div>
        <section class="panel self-start">
          <div class="panel-heading"><h2>Before this becomes operational</h2></div>
          <div class="section-body detail-stack">
            <p class="muted">
              You can edit this boundary in the preview. Live entry/exit history and notification
              delivery remain unavailable.
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
      description="This zone isn’t part of the preview workspace."
      icon="i-lucide-map-pin-off"
      ><UButton to="/geofences">Back to geofences</UButton></UiEmptyState
    >
  </div>
</template>
