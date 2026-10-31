<script setup lang="ts">
import { useDataSource } from '~/features/core/composables/useDataSource'
import { formatDateTime } from '~/features/core/domain/time'
import { useWorkspace } from '~/features/workspace/composables/useWorkspace'
import { useWorkspaceRecord } from '~/features/workspace/composables/useWorkspaceRecord'
import { useTripExport } from '~/features/workspace/composables/useTripExport'
import TripReplay from '~/features/tracking/components/TripReplay.vue'
const route = useRoute()
const source = useDataSource()
const { vehicles } = useWorkspace('trips')
const { exportTrips } = useTripExport()
const {
  data: trip,
  isPending,
  isError,
  error,
  refetch,
} = useWorkspaceRecord('trips', () => String(route.params.id))
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
    <UiAsyncState
      :pending="isPending && !trip"
      :error="isError && !trip ? error : null"
      loading-label="Loading this journey…"
      error-title="This journey could not load"
      @retry="refetch"
    >
      <template v-if="trip">
        <UiPageHeading
          :title="`Journey ${trip.id}`"
          :description="`${trip.vehicle ?? 'Vehicle not recorded'} · ${trip.driver ?? 'Driver not recorded'} · ${trip.started}`"
          eyebrow="TRIP DETAILS"
          ><UButton
            icon="i-lucide-download"
            color="neutral"
            variant="outline"
            @click="exportTrips([trip])"
            >{{ source.isPreview ? 'Export this preview trip' : 'Export trip CSV' }}</UButton
          ></UiPageHeading
        >
        <div class="info-banner">
          <UIcon name="i-lucide-info" />
          <p v-if="source.isPreview">
            Sample journey snapshot. Route, duration, and distance are fictional and do not update
            in real time.
          </p>
          <p v-else>
            Trip record from the live service. Endpoints and historical GPS points are not part of
            the verified trip contract yet.
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
                  <small>{{ trip.status === 'in progress' ? 'Elapsed' : 'Duration' }}</small
                  ><strong>{{ trip.duration }}</strong>
                </div>
                <div>
                  <small>Started</small><strong>{{ trip.started }}</strong>
                </div>
                <div>
                  <small>Ended</small
                  ><strong>{{
                    trip.endedAt ? formatDateTime(trip.endedAt) : 'Still in progress'
                  }}</strong>
                </div>
              </div>
              <ol
                v-if="trip.from || trip.to"
                class="journey-stops section-body"
                aria-label="Journey endpoints"
              >
                <li>
                  <span class="stop-dot" />
                  <div>
                    <small>ORIGIN</small>
                    <h3>{{ trip.from ?? 'Origin not recorded' }}</h3>
                    <p>Departure · {{ trip.started }}</p>
                  </div>
                </li>
                <li>
                  <span class="stop-dot destination" />
                  <div>
                    <small>{{
                      trip.status === 'in progress' ? 'PLANNED DESTINATION' : 'DESTINATION'
                    }}</small>
                    <h3>{{ trip.to ?? 'Destination not recorded' }}</h3>
                    <p>
                      {{
                        trip.endedAt
                          ? `Arrival · ${formatDateTime(trip.endedAt)}`
                          : 'Arrival not recorded'
                      }}
                    </p>
                  </div>
                </li>
              </ol>
              <div v-else class="section-body">
                <p class="muted">Route endpoints are not available for this trip.</p>
              </div>
            </section>
            <TripReplay
              v-if="source.isPreview && vehicle"
              :key="trip.id"
              :trip-id="trip.id"
              :vehicle="vehicle"
              :in-progress="trip.status === 'in progress'"
            />
            <div v-else class="info-banner">
              <UIcon name="i-lucide-shield-alert" />
              <p>
                Route replay is unavailable for live trips until historical telemetry and origin and
                destination data are verified. No route is generated from the live record.
              </p>
            </div>
          </div>
          <div class="detail-stack">
            <section class="panel">
              <div class="panel-heading"><h2>Vehicle &amp; driver</h2></div>
              <div class="section-body detail-stack">
                <div class="detail-record">
                  <span class="vehicle-icon"><UIcon name="i-lucide-truck" /></span>
                  <div>
                    <strong>{{ trip.vehicle ?? 'Vehicle not recorded' }}</strong>
                    <p>{{ trip.driver ?? 'Driver not recorded' }}</p>
                  </div>
                </div>
                <dl class="detail-info">
                  <div>
                    <dt>Registration</dt>
                    <dd>{{ vehicle?.plate ?? 'Not available' }}</dd>
                  </div>
                  <div>
                    <dt>Trip ID</dt>
                    <dd>{{ trip.id }}</dd>
                  </div>
                  <div>
                    <dt>Data source</dt>
                    <dd>{{ source.isPreview ? 'Preview fixture' : 'Live service' }}</dd>
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
                <p class="muted" v-if="source.isPreview">
                  Download only this journey as a CSV. It contains sample records, not a backend
                  report or proof of travel.
                </p>
                <p class="muted" v-else>
                  Download the currently loaded trip record as a CSV. It is not a server-generated
                  report.
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
        description="This journey is not part of the current workspace."
        icon="i-lucide-route"
        ><UButton to="/trips">Back to trips</UButton></UiEmptyState
      >
    </UiAsyncState>
  </div>
</template>
