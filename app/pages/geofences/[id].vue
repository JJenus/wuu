<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
const route = useRoute()
const { geofences, vehicles } = useWorkspace()
const zone = computed(() => geofences.value.find((z) => z.id === route.params.id))
const members = computed(() => vehicles.value.filter((v) => zone.value?.vehicleIds.includes(v.id)))
useHead(() => ({ title: `${zone.value?.name || 'Geofence not found'} — Wayline` }))
</script>

<template>
  <div class="detail-page">
    <NuxtLink to="/geofences" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to geofences</NuxtLink
    >
    <template v-if="zone">
      <UiPageHeading :title="zone.name" :description="zone.description" eyebrow="GEOFENCE DETAILS"
        ><span class="subtle-badge">Read-only concept preview</span></UiPageHeading
      >
      <div class="info-banner">
        <UIcon name="i-lucide-info" />
        <p>
          This zone is a fictional illustration. Its radius, monitoring settings, and vehicle
          membership are not backed by stored geometry or live position checks.
        </p>
      </div>
      <div class="detail-layout">
        <div class="detail-stack">
          <section class="panel">
            <div class="panel-heading">
              <h2>A little context for this place</h2>
              <span class="preview-tag">{{ zone.id }}</span>
            </div>
            <div class="section-body">
              <div class="zone-illustration zone-detail-art" aria-hidden="true">
                <span><UIcon :name="zone.icon" /></span>
              </div>
              <p class="form-hint mt-3">
                Concept illustration only · No geographic boundary is plotted
              </p>
              <dl class="detail-info mt-6">
                <div>
                  <dt>Location</dt>
                  <dd>{{ zone.location }}</dd>
                </div>
                <div>
                  <dt>Illustrative radius</dt>
                  <dd>{{ zone.radius }}</dd>
                </div>
                <div>
                  <dt>Proposed monitoring</dt>
                  <dd>{{ zone.events }}</dd>
                </div>
                <div>
                  <dt>Data source</dt>
                  <dd>Preview fixture</dd>
                </div>
              </dl>
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
              Zone editing, entry/exit history, and notification delivery are intentionally
              unavailable.
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
