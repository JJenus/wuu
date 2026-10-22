<script setup lang="ts">
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { useTripExport } from '~/features/workspace/composables/useTripExport'
import TripReplay from '~/features/tracking/components/TripReplay.vue'
const route = useRoute()
const { trips, vehicles } = useWorkspace()
const { exportTrips } = useTripExport()
const trip = computed(() => trips.value.find((t) => t.id === route.params.id))
const vehicle = computed(() => vehicles.value.find((v) => v.id === trip.value?.vehicleId))
useHead(() => ({
  title: `${trip.value ? `Journey ${trip.value.id}` : 'Trip not found'} — Wuu`,
}))
</script>

<template>
  <div class="detail-page">
    <NuxtLink to="/trips" class="back-link"
      ><UIcon name="i-lucide-arrow-left" />Back to trips</NuxtLink
    >
    <template v-if="trip">
      <UiPageHeading
        :title="`Journey ${trip.id}`"
        :description="`${trip.vehicle} · ${trip.driver} · October 21, 2026`"
        eyebrow="TRIP DETAILS"
        ><UButton
          icon="i-lucide-download"
          color="neutral"
          variant="outline"
          @click="exportTrips([trip])"
          >Export this preview trip</UButton
        ></UiPageHeading
      >
      <div class="info-banner">
        <UIcon name="i-lucide-info" />
        <p>
          Sample journey snapshot. Route, duration, and distance are fictional and do not update in
          real time.
        </p>
      </div>
      <div class="detail-layout">
        <div class="detail-stack">
          <section class="panel journey-panel">
            <div class="panel-heading">
              <h2>A journey at a glance</h2>
              <UiStatusBadge :status="trip.status" />
            </div>
            <div class="detail-stats">
              <div>
                <small>Distance</small><strong>{{ trip.distance }} <span>km</span></strong>
              </div>
              <div>
                <small>{{ trip.status === 'in progress' ? 'Elapsed (sample)' : 'Duration' }}</small
                ><strong>{{ trip.duration }}</strong>
              </div>
              <div>
                <small>Started</small><strong>{{ trip.started }}</strong>
              </div>
            </div>
            <ol class="journey-stops section-body" aria-label="Journey endpoints">
              <li>
                <span class="stop-dot" />
                <div>
                  <small>ORIGIN</small>
                  <h3>{{ trip.from }}</h3>
                  <p>Departure · {{ trip.started }} · Sample time</p>
                </div>
              </li>
              <li>
                <span class="stop-dot destination" />
                <div>
                  <small>{{
                    trip.status === 'in progress' ? 'PLANNED DESTINATION' : 'DESTINATION'
                  }}</small>
                  <h3>{{ trip.to }}</h3>
                  <p>
                    {{
                      trip.status === 'in progress'
                        ? 'Arrival not recorded'
                        : 'Arrival time not provided in this sample'
                    }}
                  </p>
                </div>
              </li>
            </ol>
          </section>
          <TripReplay
            :key="trip.id"
            :trip-id="trip.id"
            :vehicle="vehicle"
            :in-progress="trip.status === 'in progress'"
          />
        </div>
        <div class="detail-stack">
          <section class="panel">
            <div class="panel-heading"><h2>Vehicle &amp; driver</h2></div>
            <div class="section-body detail-stack">
              <div class="detail-record">
                <span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
                <div>
                  <strong>{{ trip.vehicle }}</strong>
                  <p>{{ trip.driver }}</p>
                </div>
              </div>
              <dl class="detail-info">
                <div>
                  <dt>Registration</dt>
                  <dd>{{ vehicle?.plate || 'Not available' }}</dd>
                </div>
                <div>
                  <dt>Trip ID</dt>
                  <dd>{{ trip.id }}</dd>
                </div>
                <div>
                  <dt>Data source</dt>
                  <dd>Preview fixture</dd>
                </div>
              </dl>
              <UButton
                v-if="vehicle"
                :to="`/vehicles/${vehicle.id}`"
                color="neutral"
                variant="outline"
                trailing-icon="i-lucide-arrow-up-right"
                >View {{ vehicle.name }}</UButton
              >
            </div>
          </section>
          <section class="panel">
            <div class="panel-heading"><h2>About this export</h2></div>
            <div class="section-body detail-stack">
              <p class="muted">
                Download only this journey as a CSV. It contains sample records, not a backend
                report or proof of travel.
              </p>
              <NuxtLink to="/trips" class="text-link"
                >Browse all journeys<UIcon name="i-lucide-arrow-right"
              /></NuxtLink>
            </div>
          </section>
        </div>
      </div>
    </template>
    <UiEmptyState
      v-else
      title="Trip not found"
      description="This journey isn’t part of the preview workspace."
      icon="i-lucide-route"
      ><UButton to="/trips">Back to trips</UButton></UiEmptyState
    >
  </div>
</template>
