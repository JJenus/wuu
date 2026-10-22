<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import FleetMap from '~/features/fleet/components/FleetMap.vue'
const route = useRoute()
const source = useDataSource()
const { vehicles, alerts, trips, isPending, isError, error, refetch } = useWorkspace()
const vehicle = computed(() => vehicles.value.find((v) => v.id === route.params.id))
const relatedAlerts = computed(() =>
  alerts.value.filter((a) => a.vehicleId === vehicle.value?.id && a.status !== 'resolved'),
)
const relatedTrips = computed(() => trips.value.filter((t) => t.vehicleId === vehicle.value?.id))
const sourceLabel = computed(() => (source.isPreview ? 'Sample data' : 'Live data'))
</script>

<template>
  <div>
    <NuxtLink to="/vehicles" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to your fleet</NuxtLink
    >
    <UiAsyncState
      :pending="isPending && !vehicle"
      :error="isError && !vehicle ? error : null"
      loading-label="Loading this vehicle…"
      error-title="This vehicle could not load"
      @retry="refetch"
    >
      <template v-if="vehicle">
        <UiPageHeading
          :title="vehicle.name"
          :description="`${vehicle.plate} · ${vehicle.type} · ${vehicle.id}`"
          eyebrow="VEHICLE DETAILS"
          ><UiStatusBadge :status="vehicle.status"
        /></UiPageHeading>
        <div v-if="vehicle.status === 'offline'" class="info-banner warning-banner">
          <UIcon name="i-lucide-wifi-off" />
          <p>
            This vehicle is offline. Its location and readings are the last known values, not live
            telemetry.
          </p>
        </div>
        <section class="panel">
          <div class="detail-stats">
            <div>
              <small>Speed</small><strong>{{ vehicle.speed }} <span>km/h</span></strong>
            </div>
            <div>
              <small>Fuel level</small
              ><strong>{{ vehicle.fuel ?? '—' }}<span v-if="vehicle.fuel !== null">%</span></strong>
            </div>
            <div>
              <small>Distance today</small
              ><strong
                >{{ vehicle.distance ?? '—' }}
                <span v-if="vehicle.distance !== null">km</span></strong
              >
            </div>
            <div>
              <small>Assigned driver</small
              ><strong class="!text-base">{{ vehicle.driver ?? 'Not assigned' }}</strong>
            </div>
          </div>
          <FleetMap :vehicles="[vehicle]" />
        </section>
        <div class="content-grid mt-6">
          <section class="panel">
            <div class="panel-heading">
              <h2>Vehicle information</h2>
              <span class="preview-tag">{{ sourceLabel }}</span>
            </div>
            <div class="section-body">
              <dl class="detail-info">
                <div>
                  <dt>Registration</dt>
                  <dd>{{ vehicle.plate }}</dd>
                </div>
                <div>
                  <dt>Vehicle type</dt>
                  <dd>{{ vehicle.type }}</dd>
                </div>
                <div>
                  <dt>Last known location</dt>
                  <dd>{{ vehicle.location ?? 'Location not provided' }}</dd>
                </div>
                <div>
                  <dt>Last update</dt>
                  <dd>{{ vehicle.updated ?? 'No telemetry recorded' }}</dd>
                </div>
              </dl>
            </div>
          </section>
          <section class="panel">
            <div class="panel-heading">
              <h2>Vehicle alerts</h2>
              <NuxtLink to="/alerts" class="text-link"
                >View alerts<UIcon name="i-lucide-arrow-right"
              /></NuxtLink>
            </div>
            <div class="section-body">
              <p v-if="!relatedAlerts.length" class="section-description">
                Nothing needs your attention for this vehicle.
              </p>
              <NuxtLink
                v-for="alert in relatedAlerts"
                :key="alert.id"
                :to="`/alerts/${alert.id}`"
                class="flex items-center gap-3 mb-3"
              >
                <span class="alert-icon" :class="alert.severity"><UIcon :name="alert.icon" /></span
                ><span>{{ alert.title }}</span>
              </NuxtLink>
            </div>
          </section>
        </div>
        <section class="panel mt-6">
          <div class="panel-heading">
            <h2>Recent journeys</h2>
            <span class="preview-tag">{{
              source.isPreview ? 'Sample records' : 'Loaded trips'
            }}</span>
          </div>
          <div class="section-body detail-stack">
            <NuxtLink
              v-for="trip in relatedTrips"
              :key="trip.id"
              :to="`/trips/${trip.id}`"
              :aria-label="`Journey ${trip.id}`"
              class="related-record"
              ><span class="vehicle-icon"><UIcon name="i-lucide-route" /></span>
              <div>
                <strong
                  >{{ trip.from ?? 'Origin not recorded' }} →
                  {{ trip.to ?? 'Destination not recorded' }}</strong
                >
                <p>{{ trip.id }} · {{ trip.distance }} km · {{ trip.duration }}</p>
              </div>
              <UIcon name="i-lucide-arrow-up-right"
            /></NuxtLink>
            <p v-if="!relatedTrips.length" class="muted">No journeys are linked to this vehicle.</p>
          </div>
        </section>
        <div class="info-banner mt-6">
          <UIcon name="i-lucide-shield-check" />
          <p>
            Remote commands are intentionally unavailable. Fuel-cut and restore require verified
            backend authorization, safety validation, and an audit trail.
          </p>
        </div>
      </template>
      <UiEmptyState
        v-else
        title="Vehicle not found"
        description="This vehicle is not part of the current workspace."
        icon="i-lucide-truck"
        ><UButton to="/vehicles">Back to fleet</UButton></UiEmptyState
      >
    </UiAsyncState>
  </div>
</template>
